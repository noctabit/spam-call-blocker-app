package com.addev.listaspam

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService

class MyCallScreeningService : CallScreeningService() {

    companion object {
        private const val SPAM_PREFS = "SPAM_PREFS"
        private const val BLOCK_NUMBERS_KEY = "BLOCK_NUMBERS"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val sharedPreferences = getSharedPreferences(SPAM_PREFS, Context.MODE_PRIVATE)
        val blockedNumbers = sharedPreferences.getStringSet(BLOCK_NUMBERS_KEY, null)
        val incomingNumber = callDetails.handle.schemeSpecificPart

        if (blockedNumbers?.contains(incomingNumber) == true) {
            respondToCall(callDetails, CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .build())
        }
    }

    private fun blockCall(callDetails: Call.Details) {
        val callResponse = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .build()
        respondToCall(callDetails, callResponse)
    }
}
