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

    /**
     * Sends a POST report to the UnknownPhone API for a given phone number, marking it with a comment and metadata.
     *
     * This method constructs and sends a form-encoded POST request that includes the phone number, a comment,
     * call type, language, and optional metadata. It's used to report marketing or spam calls to the platform.
     *
     * @param phone The phone number being reported.
     * @param comment The user comment about the phone number.
     * @param lang The language code (e.g., "ES" for Spanish).
     * @param username Optional: The username of the person submitting the report.
     * @param phoneOwner Optional: A string indicating ownership or recipient identity.
     * @return `true` if the report was submitted successfully; `false` otherwise.
     */
    fun reportToUnknownPhone(
        phone: String,
        comment: String,
        isSpam: Boolean,
        lang: String,
    ): Boolean {
        val optRating = if (isSpam) "1" else "5"

        val formBuilder = FormBody.Builder()
            .add("api_key", "d58d5bdaba8a80b2311957e9e4af885c")
            .add("phone", phone)
            .add("_action", "_submit_comment")
            .add("comment", comment)
            .add("lang", lang)
            .add("_opt_rating", optRating)

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .post(formBuilder.build())
            .header("Connection", "Keep-Alive")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Host", "secure.unknownphone.com")
            .header("User-Agent", "okhttp/3.14.9")
            .build()

        return try {
            val response = client.newCall(request).execute()
            Log.d("com.addev.listaspam", response.toString())
            response.isSuccessful
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

    /**
     * Sends a report to Tellows about a phone number, submitting a comment and associated metadata.
     *
     * This method builds a POST request to the Tellows API with form data including the phone number,
     * comment, complaint type, user type, and score.
     *
     * @param phone The phone number to report (without country code prefix, if already localized).
     * @param comment A description of the issue or behavior associated with the number.
     * @param complainTypeId Type of complaint, e.g. 5 = "estafa" (scam).
     * @param userScore The danger score (1 = safe, 9 = dangerous).
     * @param lang Language and country code, e.g. "es".
     * @return `true` if the report was accepted; `false` otherwise.
     */
    fun reportToTellows(
        phone: String,
        comment: String,
        isSpam: Boolean,
        lang: String = "es",
        complainTypeId: Int = 5
    ): Boolean {
        val userScore = if (isSpam) 9 else 1

        val url = "https://www.tellows.de/basic/num/$phone" +
                "?xml=1&partner=androidapp&apikey=koE5hjkOwbHnmcADqZuqqq2" +
                "&createcomment=1&country=$lang&lang=$lang&user_auth=&user_email="

        val formBody = FormBody.Builder()
            .add("caller", phone)
            .add("comment", comment)
            .add("complain_type_id", complainTypeId.toString())
            .add("user", "Android")
            .add("userscore", userScore.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("Accept-Encoding", "gzip")
            .header("Connection", "Keep-Alive")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Host", "www.tellows.de")
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 6.0; I14 Pro Max Build/MRA58K)")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return false

            // Check for success in JSON response
            val json = JSONObject(responseBody)
            json.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }

}