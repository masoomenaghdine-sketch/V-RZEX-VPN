package com.v2ray.ang.ui.main

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.math.abs
import kotlin.math.max

data class DnsResolverResult(
    val name: String,
    val description: String,
    val coreAddress: String,
    val testUrl: String,
    val latencyMs: Long? = null,
    val successful: Boolean? = null,
)

data class NetworkEndpointResult(
    val name: String,
    val url: String,
    val latencyMs: Long? = null,
    val successfulSamples: Int = 0,
    val totalSamples: Int = 3,
)

data class NetworkLabState(
    val resolvers: List<DnsResolverResult> = NetworkLabViewModel.defaultResolvers,
    val endpointResults: List<NetworkEndpointResult> = NetworkLabViewModel.defaultEndpoints,
    val isTestingEndpoints: Boolean = false,
    val isScanningDns: Boolean = false,
    val isTestingGaming: Boolean = false,
    val selectedDns: String? = null,
    val error: String? = null,
    val gamingEnabled: Boolean = false,
    val gamingAverageMs: Long? = null,
    val gamingJitterMs: Long? = null,
    val gamingLossPercent: Int? = null,
)

class NetworkLabViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        NetworkLabState(
            // Reflect the DNS already configured for Xray when the user reopens DNS Lab.
            selectedDns = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS),
            gamingEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_GAMING_MODE, false),
        )
    )
    val state = _state.asStateFlow()

    fun scanDns() = scanDns(autoApplyBest = false)

    fun scanAndApplyBestDns() = scanDns(autoApplyBest = true)

    private fun scanDns(autoApplyBest: Boolean) {
        if (_state.value.isScanningDns) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanningDns = true, error = null)
            try {
                val results = withContext(Dispatchers.IO) {
                    // Keep the three well-known choices, then sample a bounded community DoH list.
                    // The remote list is untrusted input: accept HTTPS URLs only and cap scan size.
                    val discovered = fetchPublicDohResolvers()
                    val candidates = (defaultResolvers + discovered)
                        .distinctBy { it.coreAddress }
                        .take(MAX_RESOLVERS_PER_SCAN)
                    coroutineScope {
                        candidates.map { resolver ->
                            async {
                                // Three samples make the median less sensitive to a transient spike.
                                val attempts = 3
                                val samples = mutableListOf<Long>()
                                repeat(attempts) {
                                    val elapsed = probeHttp(resolver.testUrl, timeoutMs = 1800)
                                    if (elapsed != null) samples += elapsed
                                }
                                resolver.copy(
                                    latencyMs = samples.sorted().getOrNull(samples.size / 2),
                                    successful = samples.size == attempts,
                                )
                            }
                        }.awaitAll()
                    }.sortedWith(
                        compareBy<DnsResolverResult> { it.successful != true }
                            .thenBy { it.latencyMs ?: Long.MAX_VALUE }
                    )
                }

                var selectedDns = _state.value.selectedDns
                var error: String? = null
                if (autoApplyBest) {
                    val best = results.firstOrNull {
                        it.successful == true && it.latencyMs != null &&
                            it.coreAddress.startsWith("https://", ignoreCase = true)
                    }
                    if (best == null) {
                        // Never overwrite a working/previous choice with an untested fallback.
                        error = "no_dns"
                    } else if (MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, best.coreAddress)) {
                        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_REMOTE_DNS)
                        selectedDns = best.coreAddress
                    } else {
                        error = "save_error"
                    }
                }
                _state.value = _state.value.copy(
                    resolvers = results,
                    selectedDns = selectedDns,
                    error = error,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "scan_failed")
            } finally {
                // A network exception must not leave the UI spinner permanently active.
                _state.value = _state.value.copy(isScanningDns = false)
            }
        }
    }

    fun applyDns(coreAddress: String) {
        // Never replace the current resolver with an untested or failed community endpoint.
        val resolver = _state.value.resolvers.firstOrNull {
            it.coreAddress == coreAddress &&
                it.coreAddress.startsWith("https://", ignoreCase = true) &&
                it.successful == true &&
                it.latencyMs != null
        }
        if (resolver == null) {
            _state.value = _state.value.copy(error = "dns_not_verified")
            return
        }
        val stored = MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, resolver.coreAddress)
        if (!stored) {
            _state.value = _state.value.copy(error = "save_error")
            return
        }
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_REMOTE_DNS)
        _state.value = _state.value.copy(selectedDns = resolver.coreAddress, error = null)
    }
    fun setGamingEnabled(enabled: Boolean) {
        val stored = MmkvManager.encodeSettings(AppConfig.PREF_GAMING_MODE, enabled)
        if (stored) {
            _state.value = _state.value.copy(gamingEnabled = enabled, error = null)
            // Enabling Gaming Mode is an explicit request to optimize DNS for this network.
            // Use only a resolver that returned successful test responses.
            if (enabled) scanDns(autoApplyBest = true)
        } else {
            _state.value = _state.value.copy(error = "save_error")
        }
    }

    fun testNetworkEndpoints() {
        if (_state.value.isTestingEndpoints) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isTestingEndpoints = true, error = null)
            try {
                val results = withContext(Dispatchers.IO) {
                    coroutineScope {
                        defaultEndpoints.map { endpoint ->
                            async {
                                val samples = (1..endpoint.totalSamples).map {
                                    probeHttp(endpoint.url, timeoutMs = 2500)
                                }
                                endpoint.copy(
                                    latencyMs = samples.filterNotNull().sorted().let { good ->
                                        good.getOrNull(good.size / 2)
                                    },
                                    successfulSamples = samples.count { it != null },
                                )
                            }
                        }.awaitAll()
                    }
                }
                _state.value = _state.value.copy(endpointResults = results)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "scan_failed")
            } finally {
                _state.value = _state.value.copy(isTestingEndpoints = false)
            }
        }
    }

    fun testGamingNetwork() {
        if (_state.value.isTestingGaming) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isTestingGaming = true,
                gamingAverageMs = null,
                gamingJitterMs = null,
                gamingLossPercent = null,
                error = null,
            )
            try {
                val timings = withContext(Dispatchers.IO) {
                    (1..10).map { probeHttp(AppConfig.DELAY_TEST_URL, timeoutMs = 2500) }
                }
                val good = timings.filterNotNull()
                val average = good.takeIf { it.isNotEmpty() }?.average()?.toLong()
                val jitter = good.zipWithNext { a, b -> abs(a - b) }
                    .takeIf { it.isNotEmpty() }?.average()?.toLong()
                _state.value = _state.value.copy(
                    gamingAverageMs = average,
                    gamingJitterMs = jitter,
                    gamingLossPercent = ((timings.size - good.size) * 100) / timings.size,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "scan_failed")
            } finally {
                _state.value = _state.value.copy(isTestingGaming = false)
            }
        }
    }

    private fun fetchPublicDohResolvers(): List<DnsResolverResult> {
        val connection = try {
            (URL(PUBLIC_DOH_LIST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/plain")
                useCaches = false
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val endpoints = connection.inputStream.bufferedReader().useLines { lines ->
                val valid = lines
                    .take(MAX_REMOTE_DOH_LINES)
                    .map { it.trim() }
                    .filter { it.length <= MAX_RESOLVER_URL_LENGTH }
                    .filter { it.startsWith("https://", ignoreCase = true) }
                    .distinct()
                    .toList()
                if (valid.isEmpty()) {
                    emptyList()
                } else {
                    // Spread the sample across the list instead of only testing its alphabetic beginning.
                    val step = max(1, valid.size / COMMUNITY_RESOLVERS_PER_SCAN)
                    valid.filterIndexed { index, _ -> index % step == 0 }
                        .take(COMMUNITY_RESOLVERS_PER_SCAN)
                }
            }
            endpoints.mapNotNull { endpoint ->
                val uri = runCatching { Uri.parse(endpoint) }.getOrNull() ?: return@mapNotNull null
                val host = uri.host?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (
                    uri.scheme != "https" ||
                    uri.userInfo != null ||
                    host.equals("localhost", ignoreCase = true) ||
                    host.equals("127.0.0.1") ||
                    host.equals("::1") ||
                    uri.port !in -1..65535
                ) {
                    return@mapNotNull null
                }
                DnsResolverResult(
                    name = host,
                    description = "Community DoH · tested on this network",
                    coreAddress = endpoint,
                    // Public-DNS-Collector entries are RFC 8484 DoH endpoints, not necessarily
                    // JSON APIs. Probe them with a real DNS wire query encoded as the required
                    // base64url `dns=` parameter instead of assuming every resolver supports
                    // Google's non-standard ?name=...&type=... JSON interface.
                    testUrl = uri.buildUpon()
                        .appendQueryParameter("dns", encodedExampleDotComQuery())
                        .build()
                        .toString(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A minimal RFC 1035 query for example.com A/IN, encoded as the RFC 8484
     * base64url GET parameter used by public DoH endpoints.
     */
    private fun encodedExampleDotComQuery(): String {
        val query = byteArrayOf(
            0x12, 0x34, // transaction ID
            0x01, 0x00, // recursion desired
            0x00, 0x01, // one question
            0x00, 0x00, // answer count
            0x00, 0x00, // authority count
            0x00, 0x00, // additional count
            0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, // example
            0x03, 0x63, 0x6f, 0x6d, // com
            0x00, // root terminator
            0x00, 0x01, // A
            0x00, 0x01, // IN
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(query)
    }

    private fun probeHttp(url: String, timeoutMs: Int): Long? {
        val start = SystemClock.elapsedRealtime()
        return try {
            val isWireFormatDoh = url.contains("dns=", ignoreCase = true)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty(
                    "Accept",
                    if (isWireFormatDoh) "application/dns-message" else "application/dns-json"
                )
                setRequestProperty("Cache-Control", "no-cache")
                useCaches = false
            }
            try {
                val code = connection.responseCode
                if (code in 200..299) {
                    // Read a small response body so the result includes the resolver's actual answer.
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(512)
                        input.read(buffer)
                    }
                    SystemClock.elapsedRealtime() - start
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PUBLIC_DOH_LIST_URL =
            "https://raw.githubusercontent.com/10ium/Public-DNS-Collector/main/lists/doh.txt"
        private const val COMMUNITY_RESOLVERS_PER_SCAN = 18
        private const val MAX_RESOLVERS_PER_SCAN = 21
        private const val MAX_REMOTE_DOH_LINES = 1_000
        private const val MAX_RESOLVER_URL_LENGTH = 2_048

        val defaultEndpoints = listOf(
            NetworkEndpointResult("Google", "https://www.google.com/generate_204"),
            NetworkEndpointResult("Cloudflare", "https://www.cloudflare.com/cdn-cgi/trace"),
            NetworkEndpointResult("YouTube", "https://www.youtube.com/generate_204"),
            NetworkEndpointResult("Instagram", "https://www.instagram.com/"),
        )

        val defaultResolvers = listOf(
            DnsResolverResult(
                name = "Cloudflare",
                description = "DoH · privacy-focused public resolver",
                coreAddress = "https://cloudflare-dns.com/dns-query",
                testUrl = "https://cloudflare-dns.com/dns-query?name=example.com&type=A",
            ),
            DnsResolverResult(
                name = "Google Public DNS",
                description = "DoH · broad anycast coverage",
                coreAddress = "https://dns.google/dns-query",
                testUrl = "https://dns.google/resolve?name=example.com&type=A",
            ),
            DnsResolverResult(
                name = "AdGuard DNS",
                description = "DoH · filtering options depend on endpoint",
                coreAddress = "https://dns.adguard-dns.com/dns-query",
                testUrl = "https://dns.adguard-dns.com/resolve?name=example.com&type=A",
            ),
        )
    }
}
