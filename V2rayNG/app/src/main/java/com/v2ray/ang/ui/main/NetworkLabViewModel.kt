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

data class NetworkLabState(
    val resolvers: List<DnsResolverResult> = NetworkLabViewModel.defaultResolvers,
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
            val results = withContext(Dispatchers.IO) {
                // Keep the three well-known choices, then sample across the community DoH list.
                // A remote list is untrusted input: accept HTTPS URLs only and cap the scan size.
                val discovered = fetchPublicDohResolvers()
                val candidates = (defaultResolvers + discovered)
                    .distinctBy { it.coreAddress }
                    .take(MAX_RESOLVERS_PER_SCAN)
                coroutineScope {
                    candidates.map { resolver ->
                        async {
                            // Keep the UI's "3/3 responses" claim accurate for every resolver.
                            // Three samples also make the median less sensitive to one transient spike.
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
                }.sortedWith(compareBy<DnsResolverResult> { it.successful != true }
                    .thenBy { it.latencyMs ?: Long.MAX_VALUE })
            }

            var selectedDns = _state.value.selectedDns
            var error: String? = null
            if (autoApplyBest) {
                val best = results.firstOrNull {
                    it.successful == true && it.latencyMs != null &&
                        it.coreAddress.startsWith("https://", ignoreCase = true)
                }
                if (best == null) {
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
                isScanningDns = false,
                selectedDns = selectedDns,
                error = error,
            )
        }
    }

    fun applyDns(coreAddress: String) {
        val resolver = _state.value.resolvers.firstOrNull {
            it.coreAddress == coreAddress && it.coreAddress.startsWith("https://")
        } ?: return
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
            val timings = withContext(Dispatchers.IO) {
                (1..10).map { probeHttp(AppConfig.DELAY_TEST_URL, timeoutMs = 2500) }
            }
            val good = timings.filterNotNull()
            val average = good.takeIf { it.isNotEmpty() }?.average()?.toLong()
            val jitter = good.zipWithNext { a, b -> abs(a - b) }
                .takeIf { it.isNotEmpty() }?.average()?.toLong()
            _state.value = _state.value.copy(
                isTestingGaming = false,
                gamingAverageMs = average,
                gamingJitterMs = jitter,
                gamingLossPercent = ((timings.size - good.size) * 100) / timings.size,
            )
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
                val valid = lines.map { it.trim() }
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
                if (uri.scheme != "https" || host.equals("localhost", ignoreCase = true)) {
                    return@mapNotNull null
                }
                DnsResolverResult(
                    name = host,
                    description = "Community DoH · tested on this network",
                    coreAddress = endpoint,
                    testUrl = uri.buildUpon()
                        .appendQueryParameter("name", "example.com")
                        .appendQueryParameter("type", "A")
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

    private fun probeHttp(url: String, timeoutMs: Int): Long? {
        val start = SystemClock.elapsedRealtime()
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/dns-json")
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
