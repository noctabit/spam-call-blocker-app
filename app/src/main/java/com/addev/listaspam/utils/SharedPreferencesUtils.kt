package com.addev.listaspam.utils

import android.content.Context
import androidx.preference.PreferenceManager

fun isBlockingEnabled(context: Context): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    return sharedPreferences.getBoolean("pref_enable_blocking", true)
}

fun shouldFilterWithListaSpam(context: Context): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    return sharedPreferences.getBoolean("pref_filter_lista_spam", true)
}

fun shouldFilterWithResponderONo(context: Context): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    return sharedPreferences.getBoolean("pref_filter_responder_o_no", true)
}

fun shouldBlockNonContacts(context: Context): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    return sharedPreferences.getBoolean("pref_block_non_contacts", false)
}

fun shouldShowNotification(context: Context): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    return sharedPreferences.getBoolean("pref_show_notification", true)
}
