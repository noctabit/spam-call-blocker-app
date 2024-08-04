package com.addev.listaspam

import android.Manifest
import android.app.Activity
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

class SpamUtils {

    companion object {
        private const val SPAM_PREFS = "SPAM_PREFS"
        private const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"
        private const val SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        private const val RESPONDERONO_URL_TEMPLATE = "https://www.responderono.es/numero-de-telefono/%s"
        private const val NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    fun checkSpamNumber(context: Context, number: String, callback: (isSpam: Boolean) -> Unit) {
        val url = SPAM_URL_TEMPLATE.format(number)
        val request = Request.Builder().url(url).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error gracefully
                Handler(Looper.getMainLooper()).post {
                    showToast(context, "Failed to check number in www.listaspam.com", Toast.LENGTH_LONG)
                    callback(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val spamData = parseHtmlForSpamReports(body)
                    if (spamData.reports > 1) {
                        saveSpamNumber(context, number)
                        sendNotification(context, number)
                        callback(true)
                    } else {
                        checkResponderono(context, number) { isResponderONoNegative ->
                            if (isResponderONoNegative) {
                                saveSpamNumber(context, number)
                                sendNotification(context, number)
                                callback(true)
                            } else {
                                Handler(Looper.getMainLooper()).post {
                                    showToast(context, "Incoming call is not spam", Toast.LENGTH_LONG)
                                }
                                 removeSpamNumber(context, number)
                                 callback(false)
                            }
                        }
                    }
                }
            }
        })
    }

    fun checkResponderono(context: Context, number: String, callback: (isNegative: Boolean) -> Unit) {
        val url = RESPONDERONO_URL_TEMPLATE.format(number)
        val request = Request.Builder().url(url).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error gracefully
                Handler(Looper.getMainLooper()).post {
                    showToast(context, "Failed to check number in www.responderono.es", Toast.LENGTH_LONG)
                    callback(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val isResponderONoNegative = body.contains(".scoreContainer .score.negative")
                    callback(isResponderONoNegative)
                }
            }
        })
    }

    fun saveSpamNumber(context: Context, number: String) {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
        blockedNumbers?.add(number)
        with(sharedPreferences.edit()) {
            putStringSet(BLOCK_NUMBERS_KEY, blockedNumbers)
            apply()
        }
    }

    fun removeSpamNumber(context: Context, number: String) {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
        blockedNumbers?.remove(number)
        with(sharedPreferences.edit()) {
            putStringSet(BLOCK_NUMBERS_KEY, blockedNumbers)
            apply()
        }
    }

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun sendNotification(context: Context, number: String) {
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

    fun parseHtmlForSpamReports(html: String): SpamData {
        val document = Jsoup.parse(html)
        val elementReports = document.select(".n_reports .result").first()
        val elementSearches = document.select(".n_search .result").first()

        val reports = elementReports?.text()?.toIntOrNull() ?: 0
        val searches = elementSearches?.text()?.toIntOrNull() ?: 0

        return SpamData(reports, searches, false)
    }
}