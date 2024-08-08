package com.addev.listaspam.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.addev.listaspam.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Utility class for handling spam number checks and notifications.
 */
class SpamUtils {

    companion object {
        const val SPAM_PREFS = "SPAM_PREFS"
        const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"
        private const val NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 1

        // URLs
        const val REPORT_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s#denuncia"
        const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        private const val RESPONDERONO_URL_TEMPLATE =
            "https://www.responderono.es/numero-de-telefono/%s"
        private const val CLEVER_DIALER_URL_TEMPLATE = "https://www.cleverdialer.es/numero/%s"
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
                return@launch
            }

            if (isNumberBlockedLocally(context, number)) {
                handleSpamNumber(context, number, callback)
                return@launch
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                if (isNumberInAgenda(context, number)) {
                    handleNonSpamNumber(context, number, callback)
                    return@launch
                } else if (shouldBlockNonContacts(context)) {
                    handleSpamNumber(context, number, callback)
                }


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
                handleSpamNumber(context, number, callback)
            } else {
                handleNonSpamNumber(context, number, callback)
            }
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
     * Checks if a number is blocked locally in shared preferences.
     *
     * @param context The application context.
     * @param number The phone number to check.
     * @return True if the number is blocked locally, false otherwise.
     */
    private fun isNumberBlockedLocally(context: Context, number: String): Boolean {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, emptySet())
        if (blockedNumbers != null) {
            return blockedNumbers.contains(number)
        }
        return false
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
            ".data_top .phone_rating.result-3, .data_top .phone_rating.result-2, .data_top .phone_rating.result-1, .alert-icon-big"
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
        return checkUrlForSpam(url, ".scoreContainer .score.negative")
    }

    /**
     * Checks a URL for spam indicators using a CSS selector.
     *
     * @param url The URL to check.
     * @param cssSelector The CSS selector to use for finding spam indicators.
     * @return True if spam indicators are found, false otherwise.
     */
    private suspend fun checkUrlForSpam(url: String, cssSelector: String): Boolean {
        val request = Request.Builder().url(url).build()
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
        callback: (isSpam: Boolean) -> Unit
    ) {
        saveSpamNumber(context, number)
        sendNotification(context, number)
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
     * Saves a phone number as spam in SharedPreferences.
     * @param context Context for accessing resources.
     * @param number Phone number to save as spam.
     */
    private fun saveSpamNumber(context: Context, number: String) {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers =
            sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
        blockedNumbers?.add(number)
        with(sharedPreferences.edit()) {
            putStringSet(BLOCK_NUMBERS_KEY, blockedNumbers)
            apply()
        }
    }

    /**
     * Removes a phone number from spam list in SharedPreferences.
     * @param context Context for accessing resources.
     * @param number Phone number to remove from spam list.
     */
    private fun removeSpamNumber(context: Context, number: String) {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers =
            sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
        blockedNumbers?.remove(number)
        with(sharedPreferences.edit()) {
            putStringSet(BLOCK_NUMBERS_KEY, blockedNumbers)
            apply()
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

    private fun sendNotification(context: Context, number: String) {
        createNotificationChannel(context)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED ||
            !shouldShowNotification(context)
        ) {
            // Aquí deberías solicitar el permiso si no está concedido.
            return
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_title_spam_blocked))
            .setContentText(context.getString(R.string.notification_text_spam_blocked, number))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        val name = context.getString(R.string.notification_channel_name)
        val descriptionText = context.getString(R.string.notification_channel_name)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

}