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

/**
 * Call screening service to identify and block spam calls.
 * Requires Android Q (API level 29) or higher.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MyCallScreeningService : CallScreeningService() {

    private val spamUtils = SpamUtils()

    companion object {
        private const val SPAM_PREFS = "SPAM_PREFS"
        private const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"
    }

    /**
     * Called when an incoming call is being screened.
     * @param details Details of the incoming call.
     */
    override fun onScreenCall(details: Call.Details) {
        // Only handle incoming calls
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return

        val rawNumber = getRawPhoneNumber(details)

        rawNumber?.let {
            val sharedPreferences = getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
            val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, null)

            // End call if the number is already blocked
            if (blockedNumbers?.contains(rawNumber) == true) {
                endCall(details)
                return
            }

            // Check if the number is spam
            spamUtils.checkSpamNumber(this, it) { isSpam ->
                if (isSpam) {
                    endCall(details)
                }
            }
        }
    }

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
                var uri = details.intentExtras.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
                if (uri == null) {
                    uri = details.intentExtras.getParcelable<Uri>(TelephonyManager.EXTRA_INCOMING_NUMBER)
                }
                uri?.schemeSpecificPart
            }
            else -> null
        }
    }

    /**
     * Ends the incoming call by responding to the call with disallow and reject options.
     * @param details Details of the incoming call.
     */
    private fun endCall(details: Call.Details) {
        respondToCall(
            details, CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .build()
        )
    }
}
