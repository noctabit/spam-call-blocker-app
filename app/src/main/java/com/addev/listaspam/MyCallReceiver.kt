package com.addev.listaspam

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.*
import org.jsoup.Jsoup
import java.io.IOException

class MyCallReceiver : BroadcastReceiver() {

    val spamUtils = SpamUtils()

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.PHONE_STATE") {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber =
                intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return

            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                incomingNumber?.let {
                    spamUtils.checkSpamNumber(context, it) { isSpam ->
                        if (isSpam) {
                            endCall(context)
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    private fun endCall(context: Context) {
        val telMgr = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telMgr.endCall()
    }

}
