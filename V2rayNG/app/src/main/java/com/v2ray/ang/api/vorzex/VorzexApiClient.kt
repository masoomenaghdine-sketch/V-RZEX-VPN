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
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Android-side client for the independently hosted VØRZEX backend.
 *
 * This class does not create infrastructure and performs no requests until a caller
 * explicitly invokes a method. The app must collect clear user consent before
 * registration or telemetry calls. The base URL must be supplied by the host app
 * configuration; no production endpoint is embedded in the APK.
 */
class VorzexApiClient(
    baseUrl: String,
    private val installationTokenProvider: () -> String? = { null },
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = Gson()
) {
    private val baseUrl: HttpUrl = parseBaseUrl(baseUrl)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Lightweight connectivity probe; sends no installation or VPN profile data. */
    suspend fun checkHealth(): Unit = get(
        path = "health",
        responseType = Unit::class.java,
        requiresToken = false
    )

    /** Public feed maintained by the VØRZEX Telegram admin panel. */
    suspend fun getPublishedConfigs(): PublishedConfigsResponse = get(
        path = "v1/public/configs",
        responseType = PublishedConfigsResponse::class.java,
        requiresToken = false
    )

    suspend fun registerInstallation(
        request: InstallationRegisterRequest
    ): InstallationRegisterResponse = post(
        path = "v1/installations/register",
        payload = request,
        responseType = InstallationRegisterResponse::class.java,
        requiresToken = false
    )

    suspend fun sendEvent(request: ClientEventRequest): Unit = post(
        path = "v1/telemetry/events",
        payload = request,
        responseType = Unit::class.java,
        requiresToken = true
    )

    suspend fun getAccessStatus(): ClientAccessStatusResponse = get(
        path = "v1/access/status",
        responseType = ClientAccessStatusResponse::class.java,
        requiresToken = true
    )

    private suspend fun <T : Any> post(
        path: String,
        payload: Any,
        responseType: Class<T>,
        requiresToken: Boolean
    ): T = withContext(Dispatchers.IO) {
        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        val builder = requestBuilder(path)
            .post(body)
            .header("Accept", "application/json")
        applyToken(builder, requiresToken)
        execute(builder.build(), responseType)
    }

    private suspend fun <T : Any> get(
        path: String,
        responseType: Class<T>,
        requiresToken: Boolean
    ): T = withContext(Dispatchers.IO) {
        val builder = requestBuilder(path)
            .get()
            .header("Accept", "application/json")
        applyToken(builder, requiresToken)
        execute(builder.build(), responseType)
    }

    private fun requestBuilder(path: String): Request.Builder {
        val safePath = path.removePrefix("/")
        return Request.Builder().url(baseUrl.newBuilder().addPathSegments(safePath).build())
            .header("User-Agent", "VorzexVPN-Android")
    }

    private fun applyToken(builder: Request.Builder, required: Boolean) {
        val token = installationTokenProvider()?.trim().orEmpty()
        if (required && token.isBlank()) {
            throw IllegalStateException("VØRZEX installation token is not available")
        }
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
    }

    private fun <T : Any> execute(request: Request, responseType: Class<T>): T {
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
                return Unit as T
            }
            if (bodyText.isBlank()) throw IOException("VØRZEX API returned an empty response")
            return try {
                gson.fromJson(bodyText, responseType)
                    ?: throw IOException("VØRZEX API returned an invalid response")
            } catch (e: JsonParseException) {
                throw IOException("VØRZEX API returned malformed JSON", e)
            }
        }
    }

    companion object {
        private fun parseBaseUrl(value: String): HttpUrl {
            val parsed = value.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("A valid VØRZEX API base URL is required")
            if (parsed.scheme != "https") {
                throw IllegalArgumentException("VØRZEX API must use HTTPS")
            }
            if (parsed.query != null || parsed.fragment != null) {
                throw IllegalArgumentException("API base URL must not contain a query or fragment")
            }
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
                throw IllegalArgumentException("Credentials must not be embedded in the API URL")
            }
            val normalizedPath = if (parsed.encodedPath.endsWith("/")) {
                parsed.encodedPath
            } else {
                parsed.encodedPath + "/"
            }
            return parsed.newBuilder()
                .encodedPath(normalizedPath)
                .build()
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        private fun String.toHttpUrlOrNull(): HttpUrl? =
            runCatching { toHttpUrl() }.getOrNull()
    }
}
