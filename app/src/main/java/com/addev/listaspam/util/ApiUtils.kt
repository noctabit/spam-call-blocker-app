package com.addev.listaspam.util

import android.util.Log
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Utility object for interacting with the UnknownPhone API to check if a phone number is marked as spam.
 */
object ApiUtils {
    private const val UNKNOWN_PHONE_API_URL = "https://secure.unknownphone.com/api/"
    private const val UNKNOWN_PHONE_API_KEY = "d58d5bdaba8a80b2311957e9e4af885c"

    private const val TELLOWS_API_URL = "www.tellows.de"
    private const val TELLOWS_API_KEY = "koE5hjkOwbHnmcADqZuqqq2"

    private val client = OkHttpClient()

    /**
     * Sends a POST request to the UnknownPhone API to retrieve information about the given phone number.
     *
     * The method constructs a form-encoded request with necessary parameters, sends it using OkHttp,
     * and interprets the response to determine if the phone number is likely to be spam.
     *
     * @param number The phone number to check, in international format.
     * @return `true` if the number has an average rating lower than 3 (i.e., bad or dangerous), otherwise `false`.
     */
    fun checkListaSpamApi(number: String, lang: String): Boolean {
        val formBody = FormBody.Builder()
            .add("user_type", "free")
            .add("api_key", UNKNOWN_PHONE_API_KEY)
            .add("phone", number)
            .add("_action", "_get_info_for_phone")
            .add("lang", lang)
            .build()

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .post(formBody)
            .header("Connection", "Keep-Alive")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Host", "secure.unknownphone.com")
            .header("User-Agent", "okhttp/3.14.9")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val bodyString = response.body?.string() ?: return false

            val avgRating =
                JSONObject(bodyString).optString("avg_ratings").toIntOrNull() ?: return false

            // Average ratings:
            // 5 - safe
            // 4 - good
            // 3 - neutral
            // 2 - bad
            // 1 - dangerous
            avgRating < 3
        } catch (e: Exception) {
            false
        }
    }

    fun checkTellowsSpamApi(number: String, country: String): Boolean {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(TELLOWS_API_URL)
            .addPathSegments("basic/num/$number")
            .addQueryParameter("xml", "1")
            .addQueryParameter("partner", "androidapp")
            .addQueryParameter("apikey", TELLOWS_API_KEY)
            .addQueryParameter("overridecountryfilter", "1")
            .addQueryParameter("country", country)
            .addQueryParameter("showcomments", "50")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Connection", "Keep-Alive")
            .header("Host", "www.tellows.de")
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 6.0; I14 Pro Max Build/MRA58K)")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val bodyString = response.body?.string() ?: return false

            val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(bodyString.byteInputStream())
            Log.d("com.addev.listaspam", "parsed")


            val scoreNode = xml.getElementsByTagName("score").item(0)
            val score = scoreNode?.textContent?.toIntOrNull() ?: return false
            Log.d("com.addev.listaspam", score.toString())

            // Tellows scores: 1 (safe) to 9 (very dangerous)
            score >= 7
        } catch (e: Exception) {
            false
        }
    }

}