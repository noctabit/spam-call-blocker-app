package com.addev.listaspam

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.*
import org.jsoup.Jsoup
import java.io.IOException

class MyCallReceiver : BroadcastReceiver() {

    companion object {
        private const val SPAM_PREFS = "SPAM_PREFS"
        private const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"
        private const val SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        private const val NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            incomingNumber?.let {
                checkSpamNumber(context, it)
                showToast(context, it)
            }
        } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            // Do nothing (remove missed call notification)
        }
    }

    private fun checkSpamNumber(context: Context, number: String) {
        val url = SPAM_URL_TEMPLATE.format(number)
        val request = Request.Builder().url(url).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error gracefully
                showToast(context, "Failed to check spam number", Toast.LENGTH_LONG)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val spamData = parseHtmlForSpamReports(body)
                    if (spamData.reports > 1) {
                        saveSpamNumber(context, number)
                        sendNotification(context, number)
                    } else {
                        removeSpamNumber(context, number)
                    }
                }
            }
        })
    }

    private fun saveSpamNumber(context: Context, number: String) {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
        blockedNumbers?.add(number)
        with(sharedPreferences.edit()) {
            putStringSet(BLOCK_NUMBERS_KEY, blockedNumbers)
            apply()
        }
    }

    private fun removeSpamNumber(context: Context, number: String) {
        val sharedPreferences = context.getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, mutableSetOf())?.toMutableSet()
        blockedNumbers?.remove(number)
        with(sharedPreferences.edit()) {
            putStringSet(BLOCK_NUMBERS_KEY, blockedNumbers)
            apply()
        }
    }

    private fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    private fun sendNotification(context: Context, number: String) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
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

    private fun parseHtmlForSpamReports(html: String): SpamData {
        val document = Jsoup.parse(html)
        val elementReports = document.select(".n_reports .result").first()
        val elementSearches = document.select(".n_search .result").first()

        val reports = elementReports?.text()?.toIntOrNull() ?: 0
        val searches = elementSearches?.text()?.toIntOrNull() ?: 0

        return SpamData(reports, searches)
    }
}
