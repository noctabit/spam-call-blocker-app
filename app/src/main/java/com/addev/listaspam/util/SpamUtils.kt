package com.addev.listaspam.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.addev.listaspam.R
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale
import java.util.logging.Logger

/**
 * Utility class for handling spam number checks and notifications.
 */
class SpamUtils {

    companion object {
        // URLs
        const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        const val LISTA_SPAM_CSS_SELECTOR =
            ".rate-and-owner .phone_rating:not(.result-4):not(.result-5)"
        private const val RESPONDERONO_URL_TEMPLATE =
            "https://www.responderono.es/numero-de-telefono/%s"
        private const val RESPONDERONO_CSS_SELECTOR = ".scoreContainer .score.negative"
        private const val CLEVER_DIALER_URL_TEMPLATE = "https://www.cleverdialer.es/numero/%s"
        private const val CLEVER_DIALER_CSS_SELECTOR =
            "body:has(#comments):has(.front-stars:not(.star-rating .stars-4, .star-rating .stars-5)), .circle-spam"

        private const val SPAM_PREFS = "SPAM_PREFS"
        private const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.6533.103 Mobile Safari/537.36"

        object VerificationStatus {
            const val FAILED = 2
        }
    }

    private val client = OkHttpClient()

    /**
     * Extracts the raw phone number from the call details.
     * @param details Details of the incoming call.
     * @return Raw phone number as a String.
     */
    private fun getRawPhoneNumber(details: Call.Details): String? {
        return when {
            details.handle != null -> details.handle.schemeSpecificPart
            details.gatewayInfo?.originalAddress != null -> details.gatewayInfo.originalAddress.schemeSpecificPart
            details.intentExtras != null -> {
                var uri =
                    details.intentExtras.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
                if (uri == null) {
                    uri =
                        details.intentExtras.getParcelable<Uri>(TelephonyManager.EXTRA_INCOMING_NUMBER)
                }
                uri?.schemeSpecificPart
            }

            else -> null
        }
    }

    /**
     * Checks if a given phone number is spam by checking local blocklist and online databases.
     *
     * @param context The application context.
     * @param phoneNumber The phone number to check.
     * @param details Call details
     * @param callback A function to be called with the result (true if spam, false otherwise).
     */
    fun checkSpamNumber(
        context: Context,
        phoneNumber: String?,
        details: Call.Details?,
        callback: (isSpam: Boolean) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val number = if (details != null) getRawPhoneNumber(details) else phoneNumber;

            if (!isBlockingEnabled(context)) {
                showToast(context, context.getString(R.string.blocking_disabled), Toast.LENGTH_LONG)
                return@launch
            }

            val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
            val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, null)

            if (number.isNullOrBlank()) {
                if (shouldBlockHiddenNumbers(context)) {
                    handleSpamNumber(
                        context,
                        "",
                        false,
                        context.getString(R.string.block_hidden_number),
                        callback
                    )
                    return@launch
                } else {
                    return@launch
                }
            }

            // End call if the number is already blocked
            if (blockedNumbers?.contains(number) == true) {
                handleSpamNumber(
                    context,
                    number,
                    false,
                    context.getString(R.string.block_already_blocked_number),
                    callback
                )
                return@launch
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                shouldFilterWithStirShaken(context) &&
                details?.callerNumberVerificationStatus == VerificationStatus.FAILED
            ) {
                handleSpamNumber(
                    context,
                    number,
                    false,
                    context.getString(R.string.block_stir_shaken_risk),
                    callback
                )
                return@launch
            }

            if (isNumberWhitelisted(context, number) ||
                isNumberBlocked(context, number) ||
                isContactOrShouldBlockNonContacts(context, number)
            ) {
                return@launch
            }

            if (isInternationalCall(number) && shouldBlockInternationalNumbers(context)) {
                handleSpamNumber(
                    context,
                    number,
                    false,
                    context.getString(R.string.block_international_call),
                    callback
                )
                return@launch
            }

            val spamCheckers: List<suspend (String) -> Boolean> = buildSpamCheckers(context)
            val isSpam = runBlocking {
                isSpamRace(spamCheckers, number)
            }

            if (isSpam) {
                handleSpamNumber(
                    context,
                    number,
                    context.getString(R.string.block_spam_number),
                    callback
                )
            } else {
                handleNonSpamNumber(context, number)
                return@launch
            }
        }
    }

    /**
     * Runs a list of suspend functions in parallel to check if a number is spam.
     *
     * Launches all checks simultaneously and returns `true` as soon as
     * any function returns `true`. At that point, it cancels all other running tasks.
     * If none return `true`, it returns `false`.
     *
     * @param spamCheckers List of suspend functions that take a number (String) and return a Boolean indicating spam status.
     * @param number The number (String) to be evaluated by the spam checkers.
     * @return `true` if at least one function determines the number is spam; `false` otherwise.
     */
    private suspend fun isSpamRace(
        spamCheckers: List<suspend (String) -> Boolean>,
        number: String
    ): Boolean = coroutineScope {
        val resultChannel = Channel<Boolean>()

        val jobs = spamCheckers.map { checker ->
            launch {
                val result = runCatching { checker(number) }.getOrDefault(false)
                if (result) resultChannel.send(true)
            }
        }

        val isSpam = resultChannel.receive()

        // Cancelar todos los jobs restantes
        jobs.forEach { it.cancel() }

        return@coroutineScope isSpam
    }

    private fun isContactOrShouldBlockNonContacts(context: Context, number: String): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (isNumberInAgenda(context, number)) return true
            if (shouldBlockNonContacts(context)) {
                handleSpamNumber(
                    context,
                    number,
                    false,
                    context.getString(R.string.block_non_contact),
                    {})
                return true
            }
        }
        return false
    }

    private fun buildSpamCheckers(context: Context): List<suspend (String) -> Boolean> {
        val spamCheckers = mutableListOf<suspend (String) -> Boolean>()

        // ListaSpam
        val listaSpamApi = shouldFilterWithListaSpamApi(context)
        if (listaSpamApi) {
            spamCheckers.add { number ->
                ApiUtils.checkListaSpamApi(number, getListaSpamApiLang(context) ?: "EN")
            }
        }

        // Tellows
        val tellowsApi = shouldFilterWithTellowsApi(context)
        if (tellowsApi) {
            spamCheckers.add { number ->
                ApiUtils.checkTellowsSpamApi(number, getTellowsApiCountry(context) ?: "us")
            }
        }

        // Truecaller
        val truecallerApi = shouldFilterWithTruecallerApi(context)
        if (truecallerApi) {
            spamCheckers.add { number ->
                ApiUtils.checkTruecallerSpamApi(number, getTruecallerApiCountry(context) ?: "US")
            }
        }

        if (shouldFilterWithListaSpamScraper(context) && !listaSpamApi) spamCheckers.add(::checkListaSpam)

        if (shouldFilterWithResponderONo(context)) spamCheckers.add(::checkResponderono)
        if (shouldFilterWithCleverdialer(context)) spamCheckers.add(::checkCleverdialer)
        return spamCheckers
    }

    private fun isInternationalCall(phoneNumber: String): Boolean {
        // Get an instance of PhoneNumberUtil
        val phoneNumberUtil = PhoneNumberUtil.getInstance()

        try {
            // Parse the phone number
            val parsedNumber: Phonenumber.PhoneNumber = phoneNumberUtil.parse(phoneNumber, null)

            // Get the country code of the parsed number
            val phoneCountryCode = parsedNumber.countryCode

            // Get the device's country code based on locale
            val deviceCountryCode = PhoneNumberUtil.getInstance()
                .getCountryCodeForRegion(Locale.getDefault().country)

            // Check if the country codes are different
            return phoneCountryCode != deviceCountryCode

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Normalizes a phone number by removing all non-digit characters.
     *
     * @param number The phone number to normalize.
     * @return The normalized phone number.
     */
    private fun normalizePhoneNumber(number: String): String {
        return number.replace("\\D".toRegex(), "")
    }

    /**
     * Checks if the given number exists in the user's contact list.
     * Ignores spaces and considers different number prefixes for comparison.
     *
     * @param context The context of the caller.
     * @param number The phone number to check.
     * @return True if the number is in the user's contact list, false otherwise.
     */
    private fun isNumberInAgenda(context: Context, number: String): Boolean {
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection =
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} LIKE ?"
        val normalizedNumber = normalizePhoneNumber(number)
        val selectionArgs = arrayOf("%$normalizedNumber%", "%$normalizedNumber%")

        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val numberIndex =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val contactNumber = normalizePhoneNumber(cursor.getString(numberIndex))
                    if (normalizedNumber == contactNumber || normalizedNumber.endsWith(contactNumber) || contactNumber.endsWith(
                            normalizedNumber
                        )
                    ) {
                        return true
                    }
                }
            }
            false
        } finally {
            cursor?.close()
        }
    }

    /**
     * Checks if a number is marked as spam on ListaSpam.
     *
     * @param number The phone number to check.
     * @return True if the number is marked as spam, false otherwise.
     */
    private suspend fun checkListaSpam(number: String): Boolean {
        val url = LISTA_SPAM_URL_TEMPLATE.format(number)
        return checkUrlForSpam(
            url,
            LISTA_SPAM_CSS_SELECTOR
        )
    }

    /**
     * Checks if a number is marked as spam on Responderono.
     *
     * @param number The phone number to check.
     * @return True if the number is marked as spam, false otherwise.
     */
    private suspend fun checkResponderono(number: String): Boolean {
        val url = RESPONDERONO_URL_TEMPLATE.format(number)
        return checkUrlForSpam(url, RESPONDERONO_CSS_SELECTOR)
    }

    /**
     * Checks if a number is marked as spam on Cleverdialer.
     *
     * @param number The phone number to check.
     * @return True if the number is marked as spam, false otherwise.
     */
    private suspend fun checkCleverdialer(number: String): Boolean {
        val url = CLEVER_DIALER_URL_TEMPLATE.format(number)
        return checkUrlForSpam(url, CLEVER_DIALER_CSS_SELECTOR)
    }

    /**
     * Checks a URL for spam indicators using a CSS selector.
     *
     * @param url The URL to check.
     * @param cssSelector The CSS selector to use for finding spam indicators.
     * @return True if spam indicators are found, false otherwise.
     */
    private suspend fun checkUrlForSpam(url: String, cssSelector: String): Boolean {
        val request = Request.Builder()
            .header("User-Agent", USER_AGENT)
            .url(url)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext false
                    val doc = Jsoup.parse(body)
                    doc.select(cssSelector).isNotEmpty()
                }
            } catch (e: IOException) {
                Logger.getLogger("checkUrlForSpam")
                    .warning("Error checking URL: $url with error ${e.message}")
                false
            }
        }
    }

    /**
     * Handles the scenario when a phone number is identified as spam.
     * @param context Context for accessing resources.
     * @param number Phone number identified as spam.
     * @param callback Callback function to handle the result.
     */
    private fun handleSpamNumber(
        context: Context,
        number: String,
        reason: String,
        callback: (isSpam: Boolean) -> Unit
    ) {
        handleSpamNumber(context, number, true, reason, callback)
    }

    /**
     * Handles the scenario when a phone number is identified as spam.
     * @param context Context for accessing resources.
     * @param number Phone number identified as spam.
     * @param callback Callback function to handle the result.
     */
    private fun handleSpamNumber(
        context: Context,
        number: String,
        saveNumber: Boolean,
        reason: String,
        callback: (isSpam: Boolean) -> Unit
    ) {
        showToast(
            context,
            context.getString(R.string.block_reason_long) + " " + reason,
            Toast.LENGTH_LONG
        )

        if (saveNumber) {
            saveSpamNumber(context, number)
        }
        sendBlockedCallNotification(context, number, reason)
        callback(true)
    }

    /**
     * Handles the scenario when a phone number is not identified as spam.
     * @param context Context for accessing resources.
     * @param number Phone number identified as not spam.
     */
    private fun handleNonSpamNumber(
        context: Context,
        number: String
    ) {
        showToast(context, context.getString(R.string.incoming_call_not_spam))

        CoroutineScope(Dispatchers.Main).launch {
            sendNotification(
                context,
                context.getString(R.string.call_incoming),
                context.getString(R.string.incoming_call_not_spam),
                10000
            )
            removeSpamNumber(context, number)
        }
    }

    /**
     * Displays a toast message.
     * @param context Context for displaying the toast.
     * @param message Message to display.
     * @param duration Duration of the toast display.
     */
    private fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
    }

}