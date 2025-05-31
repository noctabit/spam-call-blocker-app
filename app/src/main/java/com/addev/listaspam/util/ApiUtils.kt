package com.addev.listaspam.util

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Utility object for interacting with the UnknownPhone API to check if a phone number is marked as spam.
 */
object ApiUtils {
    private const val API_URL = "https://secure.unknownphone.com/api/"
    private const val API_KEY = "d58d5bdaba8a80b2311957e9e4af885c"
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
    fun checkListaSpamApi(number: String): Boolean {
        val formBody = FormBody.Builder()
            .add("user_type", "free")
            .add("api_key", API_KEY)
            .add("phone", number)
            .add("_action", "_get_info_for_phone")
            .add("lang", "ES")
            .build()

        val request = Request.Builder()
            .url(API_URL)
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
}