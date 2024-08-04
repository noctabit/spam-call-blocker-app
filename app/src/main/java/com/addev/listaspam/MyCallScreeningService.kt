package com.addev.listaspam

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.annotation.RequiresApi

class MyCallScreeningService : CallScreeningService() {

    val spamUtils = SpamUtils()

    companion object {
        private const val SPAM_PREFS = "SPAM_PREFS"
        private const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING)
            return

        var rawNumber = ""
        if (details.handle != null) {
            rawNumber = details.handle.schemeSpecificPart
        } else if (details.gatewayInfo?.originalAddress != null){
            rawNumber = details.gatewayInfo?.originalAddress?.schemeSpecificPart!!
        } else if (details.intentExtras != null) {
            var uri = details.intentExtras.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
            if (uri == null) {
                uri = details.intentExtras.getParcelable<Uri>(TelephonyManager.EXTRA_INCOMING_NUMBER)
            }
            if (uri != null) {
                rawNumber = uri.schemeSpecificPart
            }
        }

        rawNumber?.let {
            val sharedPreferences = getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
            val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, null)

            if (blockedNumbers?.contains(rawNumber) == true) {
                endCall(details)
                return
            }

            spamUtils.checkSpamNumber(this, it) { isSpam ->
                if (isSpam) {
                    endCall(details)
                }
            }
        }
    }

    private fun endCall(details: Call.Details) {
        respondToCall(
            details, CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .build()
        )
    }
}
