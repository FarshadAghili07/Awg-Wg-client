package com.network.awg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ServerLocation(val country: String, val flagEmoji: String, val ip: String)

object LocationHelper {
    suspend fun fetchConnectedCountry(): ServerLocation? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://ip-api.com/json/?fields=status,country,countryCode,query")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            if (json.optString("status") == "success") {
                val country = json.getString("country")
                val countryCode = json.getString("countryCode")
                val ip = json.getString("query")

                // تبدیل کد دو حرفی کشور به ایموجی پرچم (مثال: DE -> 🇩🇪)
                val flagEmoji = countryCode.uppercase().map { char ->
                    Character.toChars(0x1F1E6 + (char.code - 'A'.code))
                }.joinToString("") { String(it) }

                ServerLocation(country, flagEmoji, ip)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
