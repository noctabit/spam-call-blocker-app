package com.addev.listaspam

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Utility class for handling spam number checks and notifications.
 */
class SpamUtils {

    companion object {
        private const val SPAM_PREFS = "SPAM_PREFS"
        private const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"
        private const val SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        private const val RESPONDERONO_URL_TEMPLATE = "https://www.responderono.es/numero-de-telefono/%s"
        private const val NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 1
        private const val SPAM_REPORT_THRESHOLD = 1
    }

    /**
     * Checks if a given phone number is considered spam by querying spam databases.
     * @param context Context for accessing resources.
     * @param number Phone number to check.
     * @param callback Callback function to handle the result.
     */
    fun checkSpamNumber(context: Context, number: String, callback: (isSpam: Boolean) -> Unit) {
        val url = SPAM_URL_TEMPLATE.format(number)
        val request = Request.Builder().url(url).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleNetworkFailure(context, "Failed to check number in www.listaspam.com", e, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val spamData = parseHtmlForSpamReports(body)
                    if (spamData.reports > SPAM_REPORT_THRESHOLD) {
                        handleSpamNumber(context, number, callback)
                    } else {
                        checkResponderono(context, number) { isResponderONoNegative ->
                            if (isResponderONoNegative) {
                                handleSpamNumber(context, number, callback)
                            } else {
                                handleNonSpamNumber(context, number, callback)
                            }
                        }
                    }
                } ?: handleNetworkFailure(context, "Empty response from www.listaspam.com", null, callback)
            }
        })
    }

    /**
     * Checks if a given phone number is considered negative by the ResponderONo database.
     * @param context Context for accessing resources.
     * @param number Phone number to check.
     * @param callback Callback function to handle the result.
     */
    private fun checkResponderono(context: Context, number: String, callback: (isNegative: Boolean) -> Unit) {
        val url = RESPONDERONO_URL_TEMPLATE.format(number)
        val request = Request.Builder().url(url).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleNetworkFailure(context, "Failed to check number in www.responderono.es", e, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val isResponderONoNegative = body.contains(".scoreContainer .score.negative")
                    callback(isResponderONoNegative)
                } ?: handleNetworkFailure(context, "Empty response from www.responderono.es", null, callback)
            }
        })
    }

    /**
     * Handles the scenario when a phone number is identified as spam.
     * @param context Context for accessing resources.
     * @param number Phone number identified as spam.
     * @param callback Callback function to handle the result.
     */
    private fun handleSpamNumber(context: Context, number: String, callback: (isSpam: Boolean) -> Unit) {
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
    private fun handleNonSpamNumber(context: Context, number: String, callback: (isSpam: Boolean) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            showToast(context, "Incoming call is not spam", Toast.LENGTH_LONG)
        }
        removeSpamNumber(context, number)
        callback(false)
    }

    /**
     * Handles network failures by showing a toast message and logging the error.
     * @param context Context for accessing resources.
     * @param message Error message to display.
     * @param e Exception that occurred (optional).
     * @param callback Callback function to handle the result.
     */
    private fun handleNetworkFailure(context: Context, message: String, e: IOException?, callback: (Boolean) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            showToast(context, message, Toast.LENGTH_LONG)
        }
        e?.printStackTrace()
        callback(false)
    }

    /**
     * Saves a phone number as spam in SharedPreferences.
     * @param context Context for accessing resources.
     * @param number Phone number to save as spam.
     */
    private fun saveSpamNumber(context: Context, number: String) {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
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
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
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
    private fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
    }

    /**
     * Sends a notification indicating that a spam number has been blocked.
     * @param context Context for sending the notification.
     * @param number Phone number that was blocked.
     */
    private fun sendNotification(context: Context, number: String) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Spam Number Blocked")
            .setContentText("Blocked spam number: $number")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Creates a notification channel for spam notifications.
     * @param context Context for creating the notification channel.
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Spam Blocker Channel"
            val descriptionText = "Notifications for blocked spam numbers"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Parses HTML content to extract spam report data.
     * @param html HTML content to parse.
     * @return [SpamData] containing the number of reports and searches.
     */
    private fun parseHtmlForSpamReports(html: String): SpamData {
        val document = Jsoup.parse(html)
        val elementReports = document.select(".n_reports .result").first()
        val elementSearches = document.select(".n_search .result").first()

        val reports = elementReports?.text()?.toIntOrNull() ?: 0
        val searches = elementSearches?.text()?.toIntOrNull() ?: 0

        return SpamData(reports, searches, false)
    }
}