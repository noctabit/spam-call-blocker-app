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

    private fun buildUnknownPhoneRequest(formBody: FormBody): Request {
        return Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .post(formBody)
            .apply {
                header("Connection", "Keep-Alive")
                header("Content-Type", "application/x-www-form-urlencoded")
                header("Host", "secure.unknownphone.com")
                header("User-Agent", "okhttp/3.14.9")
            }
            .build()
    }

    private fun buildTellowsRequest(url: HttpUrl): Request {
        return Request.Builder()
            .url(url)
            .get()
            .apply {
                header("Connection", "Keep-Alive")
                header("Host", TELLOWS_API_URL)
                header(
                    "User-Agent",
                    "Dalvik/2.1.0 (Linux; U; Android 6.0; I14 Pro Max Build/MRA58K)"
                )
            }
            .build()
    }

    fun checkListaSpamApi(number: String, lang: String): Boolean {
        val formBody = FormBody.Builder()
            .add("user_type", "free")
            .add("api_key", UNKNOWN_PHONE_API_KEY)
            .add("phone", number)
            .add("_action", "_get_info_for_phone")
            .add("lang", lang)
            .build()

        val request = buildUnknownPhoneRequest(formBody)

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val avgRating = JSONObject(response.body?.string() ?: return false)
                    .optString("avg_ratings").toIntOrNull() ?: return false
                avgRating < 3
            }
        } catch (e: Exception) {
            false
        }
    }

    fun reportToUnknownPhone(
        phone: String,
        comment: String,
        isSpam: Boolean,
        lang: String,
    ): Boolean {
        val formBody = FormBody.Builder()
            .add("api_key", UNKNOWN_PHONE_API_KEY)
            .add("phone", phone)
            .add("_action", "_submit_comment")
            .add("comment", comment)
            .add("lang", lang)
            .add("_opt_rating", if (isSpam) "1" else "5")
            .build()

        val request = buildUnknownPhoneRequest(formBody)

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
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

        val request = buildTellowsRequest(url)

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(response.body?.byteStream() ?: return false)
                val score = xml.getElementsByTagName("score").item(0)
                    ?.textContent?.toIntOrNull() ?: return false
                score >= 7
            }
        } catch (e: Exception) {
            false
        }
    }

    fun reportToTellows(
        phone: String,
        comment: String,
        isSpam: Boolean,
        lang: String = "es",
        complainTypeId: Int = 5
    ): Boolean {
        val userScore = if (isSpam) 9 else 1

        val url = "https://$TELLOWS_API_URL/basic/num/$phone" +
                "?xml=1&partner=androidapp&apikey=$TELLOWS_API_KEY" +
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
            .header("Host", TELLOWS_API_URL)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 6.0; I14 Pro Max Build/MRA58K)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: return false)
                json.optBoolean("success", false)
            }
        } catch (e: Exception) {
            false
        }
    }
}
