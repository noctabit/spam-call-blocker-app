package com.addev.listaspam.util

import android.content.Context
import java.util.Locale
import android.telephony.TelephonyManager
import com.addev.listaspam.R

object CountryLanguageUtils {
    private val COUNTRY_TO_LANG = mapOf(
        "US" to "EN", "GB" to "EN", "ES" to "ES", "FR" to "FR", "DE" to "DE", "IT" to "IT",
        "RU" to "RU", "SE" to "SV", "PL" to "PL", "PT" to "PT", "NL" to "NL", "NO" to "NO",
        "CZ" to "CZ", "ID" to "ID", "CN" to "ZH", "TW" to "ZH", "HK" to "ZH", "JP" to "JA",
        "IL" to "HE", "TR" to "TR", "HU" to "HU", "FI" to "FI", "DK" to "DA", "TH" to "TH",
        "GR" to "GK", "SK" to "SK", "RO" to "RO"
    )

    private fun getSimCountry(context: Context): String? {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return telephonyManager?.simCountryIso?.takeIf { it.isNotEmpty() }
    }

    fun setListaSpamLanguage(context: Context) {
        if (getListaSpamApiLang(context) != null) return

        val simCountry = getSimCountry(context)?.uppercase()
        val supportedLanguages = context.resources.getStringArray(R.array.language_values).toSet()

        val langFromSim = simCountry?.let { COUNTRY_TO_LANG[it] }
        val systemLanguage = Locale.getDefault().language.uppercase()

        val finalLang = when {
            langFromSim != null && supportedLanguages.contains(langFromSim) -> langFromSim
            supportedLanguages.contains(systemLanguage) -> systemLanguage
            else -> "EN"
        }
        setListaSpamApiLang(context, finalLang)
    }

    fun setTellowsCountry(context: Context) {
        if (getTellowsApiCountry(context) != null) return

        val simCountry = getSimCountry(context)?.lowercase()
        val systemCountry = Locale.getDefault().country.lowercase()
        val supportedCountries =
            context.resources.getStringArray(R.array.entryvalues_region_preference).toSet()

        val finalCountry = when {
            simCountry != null && supportedCountries.contains(simCountry) -> simCountry
            supportedCountries.contains(systemCountry) -> systemCountry
            else -> "us"
        }
        setTellowsApiCountry(context, finalCountry)
    }

    fun setTruecallerCountry(context: Context) {
        if (getTruecallerApiCountry(context) != null) return

        val simCountry = getSimCountry(context)?.uppercase()
        val systemCountry = Locale.getDefault().country.uppercase()
        val supportedCountries =
            context.resources.getStringArray(R.array.truecaller_region_code).toSet()

        val finalCountry = when {
            simCountry != null && supportedCountries.contains(simCountry) -> simCountry
            supportedCountries.contains(systemCountry) -> systemCountry
            else -> "US"
        }
        setTruecallerApiCountry(context, finalCountry)
    }
}
