package com.addev.listaspam.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.addev.listaspam.R
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale

/**
 * Utility class for handling spam number checks and notifications.
 */
class SpamUtils {

    companion object {
        // URLs
        const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        const val LISTA_SPAM_CSS_SELECTOR = ".rate-and-owner .phone_rating:not(.result-4):not(.result-5)"
        private const val RESPONDERONO_URL_TEMPLATE =
            "https://www.responderono.es/numero-de-telefono/%s"
        private const val RESPONDERONO_CSS_SELECTOR = ".scoreContainer .score.negative"
        private const val CLEVER_DIALER_URL_TEMPLATE = "https://www.cleverdialer.es/numero/%s"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.6533.103 Mobile Safari/537.36"
    }

    private val client = OkHttpClient()

    /**
     * Checks if a given phone number is spam by checking local blocklist and online databases.
     *
     * @param context The application context.
     * @param number The phone number to check.
     * @param callback A function to be called with the result (true if spam, false otherwise).
     */
    fun checkSpamNumber(context: Context, number: String, callback: (isSpam: Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isBlockingEnabled(context)) {
                showToast(context, context.getString(R.string.blocking_disabled), Toast.LENGTH_LONG)
                return@launch
            }

            if (number.isNullOrBlank() && shouldBlockHiddenNumbers(context)) {
                handleSpamNumber(context, number, false, context.getString(R.string.block_hidden_number), callback)
                return@launch
            }

            if (isNumberWhitelisted(context, number)) {
                return@launch
            }

            if (isNumberBlocked(context, number)) {
                handleSpamNumber(context, number, context.getString(R.string.block_already_blocked_number), callback)
                return@launch
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                if (isNumberInAgenda(context, number)) {
                    return@launch
                } else if (shouldBlockNonContacts(context)) {
                    handleSpamNumber(context, number, false, context.getString(R.string.block_non_contact), callback)
                    return@launch
                }
            }

            if (isInternationalCall(number) && shouldBlockInternationalNumbers(context)) {
                handleSpamNumber(context, number, false, context.getString(R.string.block_international_call), callback)
                return@launch
            }

            // List to hold the functions that should be used
            val spamCheckers = mutableListOf<suspend (String) -> Boolean>()

            // Add functions based on preferences
            if (shouldFilterWithListaSpam(context)) {
                spamCheckers.add(::checkListaSpam)
            }

            if (shouldFilterWithResponderONo(context)) {
                spamCheckers.add(::checkResponderono)
            }

            val isSpam = spamCheckers.any { checker ->
                runCatching { checker(number) }.getOrDefault(false)
            }

            if (isSpam) {
                handleSpamNumber(context, number, context.getString(R.string.block_spam_number), callback)
            } else {
                handleNonSpamNumber(context, number, callback)
            }
        }
    }


    fun isInternationalCall(phoneNumber: String): Boolean {
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
        val normalizedNumber = normalizePhoneNumber(number)

        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val contactNumber = normalizePhoneNumber(cursor.getString(numberIndex))
                    if (normalizedNumber == contactNumber || normalizedNumber.endsWith(contactNumber) || contactNumber.endsWith(normalizedNumber)) {
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
     * Checks a URL for spam indicators using a CSS selector.
     *
     * @param url The URL to check.
     * @param cssSelector The CSS selector to use for finding spam indicators.
     * @return True if spam indicators are found, false otherwise.
     */
    private suspend fun checkUrlForSpam(url: String, cssSelector: String): Boolean {
        val request = Request.Builder()
            .header("User-Agent", USER_AGENT)
            .url(url).build()
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                body?.let {
                    val doc = Jsoup.parse(it)
                    val found = doc.select(cssSelector).first() != null
                    found
                } ?: false
            } catch (e: IOException) {
                e.printStackTrace()
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
        showToast(context, context.getString(R.string.block_reason_long) + " " + reason, Toast.LENGTH_LONG)

        if (saveNumber) {
            saveSpamNumber(context, number)
        }
        sendNotification(context, number, reason)
        callback(true)
    }

    /**
     * Handles the scenario when a phone number is not identified as spam.
     * @param context Context for accessing resources.
     * @param number Phone number identified as not spam.
     * @param callback Callback function to handle the result.
     */
    private fun handleNonSpamNumber(
        context: Context,
        number: String,
        callback: (isSpam: Boolean) -> Unit
    ) {
        Handler(Looper.getMainLooper()).post {
            showToast(context, context.getString(R.string.incoming_call_not_spam), Toast.LENGTH_LONG)
        }
        removeSpamNumber(context, number)
        callback(false)
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