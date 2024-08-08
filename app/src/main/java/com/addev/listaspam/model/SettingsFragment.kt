package com.addev.listaspam.model

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.addev.listaspam.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}