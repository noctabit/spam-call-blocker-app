package com.addev.listaspam

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

/**
 * BroadcastReceiver for handling incoming call events and checking for spam numbers.
 * Requires Android P (API level 28) or higher.
 *
 * Note: This class will not work on Android Q (API level 29) or higher due to changes in privacy permissions.
 * For Android Q or higher, use MyCallScreeningService instead.
 */
@RequiresApi(Build.VERSION_CODES.P)
class MyCallReceiver : BroadcastReceiver() {

    private val spamUtils = SpamUtils()

    /**
     * Called when the BroadcastReceiver is receiving an Intent broadcast.
     * @param context Context for accessing resources.
     * @param intent The received Intent.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.PHONE_STATE") {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber =
                intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return

            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                handleIncomingCall(context, incomingNumber)
            }
        }
    }

    /**
     * Handles the incoming call by checking if the number is spam.
     * @param context Context for accessing resources.
     * @param incomingNumber The incoming phone number.
     */
    private fun handleIncomingCall(context: Context, incomingNumber: String) {
        spamUtils.checkSpamNumber(context, incomingNumber) { isSpam ->
            if (isSpam) {
                endCall(context)
            }
        }
    }

    /**
     * Ends the incoming call.
     * @param context Context for accessing resources.
     */
    @SuppressLint("MissingPermission")
    private fun endCall(context: Context) {
        val telMgr = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telMgr.endCall()
    }
}
