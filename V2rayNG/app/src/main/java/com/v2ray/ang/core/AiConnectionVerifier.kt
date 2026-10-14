package com.v2ray.ang.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifies application traffic through the currently selected Android VPN tunnel.
 * A TCP socket to a server alone is not enough: this requires a real HTTPS response.
 */
internal object AiConnectionVerifier {
    private val endpoints = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://www.google.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
    )

    suspend fun verifyTunnelTraffic(): Boolean = withContext(Dispatchers.IO) {
        endpoints.any(::probe)
    }

    private fun probe(endpoint: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = connection.responseCode
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private const val PROBE_TIMEOUT_MS = 2_500
}
