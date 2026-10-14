package com.v2ray.ang.ui.main

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

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

    fun scanDns() {
        if (_state.value.isScanningDns) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanningDns = true, error = null)
            val results = withContext(Dispatchers.IO) {
                defaultResolvers.map { resolver ->
                    val samples = mutableListOf<Long>()
                    repeat(3) {
                        val elapsed = probeHttp(resolver.testUrl, timeoutMs = 2200)
                        if (elapsed != null) samples += elapsed
                    }
                    resolver.copy(
                        latencyMs = samples.sorted().getOrNull(samples.size / 2),
                        successful = samples.size == 3,
                    )
                }.sortedWith(compareBy<DnsResolverResult> { it.successful != true }
                    .thenBy { it.latencyMs ?: Long.MAX_VALUE })
            }
            _state.value = _state.value.copy(resolvers = results, isScanningDns = false)
        }
    }

    fun applyDns(coreAddress: String) {
        val resolver = defaultResolvers.firstOrNull { it.coreAddress == coreAddress } ?: return
        val stored = MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, resolver.coreAddress)
        if (!stored) {
            _state.value = _state.value.copy(error = "Could not save DNS setting.")
            return
        }
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_REMOTE_DNS)
        _state.value = _state.value.copy(selectedDns = resolver.coreAddress, error = null)
    }

    fun setGamingEnabled(enabled: Boolean) {
        val stored = MmkvManager.encodeSettings(AppConfig.PREF_GAMING_MODE, enabled)
        if (stored) {
            _state.value = _state.value.copy(gamingEnabled = enabled, error = null)
        } else {
            _state.value = _state.value.copy(error = "Could not save Gaming Mode.")
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
                    connection.inputStream.use { input -> input.readNBytes(512) }
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
