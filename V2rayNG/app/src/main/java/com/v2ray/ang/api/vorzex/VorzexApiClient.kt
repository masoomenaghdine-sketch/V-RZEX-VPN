package com.v2ray.ang.api.vorzex

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Read-only VØRZEX API client.
 *
 * The Android app may fetch public configuration listings and health status.
 * It intentionally has no registration, telemetry, device-reporting, command,
 * authorization-status, or other write methods.
 */
class VorzexApiClient(
    baseUrl: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = Gson()
) {
    private val baseUrl: HttpUrl = parseBaseUrl(baseUrl)

    suspend fun checkHealth(): Unit = get("health", Unit::class.java)

    /** Public configs only; this request does not include device identifiers or user data. */
    suspend fun getPublishedConfigs(): PublishedConfigsResponse =
        get("v1/public/configs", PublishedConfigsResponse::class.java)

    private suspend fun <T : Any> get(path: String, responseType: Class<T>): T =
        withContext(Dispatchers.IO) {
            val safePath = path.removePrefix("/")
            val request = Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments(safePath).build())
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "VorzexVPN-Android")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        gson.fromJson(bodyText, ApiErrorResponse::class.java)
                    }.getOrNull()
                    throw IOException(
                        "VØRZEX API returned HTTP ${response.code}" +
                            (detail?.error?.let { ": $it" } ?: "")
                    )
                }
                if (responseType == Unit::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext Unit as T
                }
                if (bodyText.isBlank()) throw IOException("VØRZEX API returned an empty response")
                try {
                    gson.fromJson(bodyText, responseType)
                        ?: throw IOException("VØRZEX API returned an invalid response")
                } catch (e: JsonParseException) {
                    throw IOException("VØRZEX API returned malformed JSON", e)
                }
            }
        }

    companion object {
        private fun parseBaseUrl(value: String): HttpUrl {
            val parsed = runCatching { value.trim().toHttpUrl() }.getOrNull()
                ?: throw IllegalArgumentException("A valid VØRZEX API base URL is required")
            require(parsed.scheme == "https") { "VØRZEX API must use HTTPS" }
            require(parsed.query == null && parsed.fragment == null) {
                "API base URL must not contain a query or fragment"
            }
            require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
                "Credentials must not be embedded in the API URL"
            }
            val normalizedPath = if (parsed.encodedPath.endsWith("/")) {
                parsed.encodedPath
            } else {
                parsed.encodedPath + "/"
            }
            return parsed.newBuilder().encodedPath(normalizedPath).build()
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
