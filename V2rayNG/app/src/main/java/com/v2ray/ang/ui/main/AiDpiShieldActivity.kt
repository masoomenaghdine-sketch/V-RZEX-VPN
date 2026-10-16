package com.v2ray.ang.ui.main

import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2ray.ang.core.AiNetworkAdvisor
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A local network-resilience advisor. It measures ordinary HTTPS reachability and reports
 * conservative options; it does not claim that a probe can identify or defeat every DPI system.
 */
class AiDpiShieldActivity : BaseComponentActivity() {
    private var running by mutableStateOf(false)
    private var report by mutableStateOf<ShieldReport?>(null)
    private var error by mutableStateOf(false)

    @Composable
    override fun ScreenContent() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Shield", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "DPI resilience · local network analysis",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { finish() }) { Text("Back") }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Network-aware recommendations", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Checks HTTPS reachability and Android's network estimates, then suggests a conservative path. Analysis runs on this device; no SIM identifiers or diagnostic report are uploaded.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = ::analyze,
                                enabled = !running,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (running) CircularProgressIndicator()
                                else Text("Analyze this connection")
                            }
                        }
                    }
                }
                if (running) item { Text("Testing HTTPS endpoints…", color = MaterialTheme.colorScheme.primary) }
                report?.let { result ->
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Analysis", style = MaterialTheme.typography.titleLarge)
                                Text("Access: ${result.access}")
                                Text("HTTPS checks passed: ${result.passed}/${result.total}")
                                Text("Link estimate: ${result.link}")
                                Text("Suggested starting point", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text(result.recommendation, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Resilience playbook", style = MaterialTheme.typography.titleMedium)
                                Text("1. If ordinary Xray profiles fail, test the existing Aether/Psiphon automatic or CDN mode.")
                                Text("2. If Tor is needed, use the existing Tor bridge options; bridges may improve reachability but are not guaranteed.")
                                Text("3. Keep a known-working profile as fallback and compare results after network changes.")
                                Text("4. A DNS change alone cannot defeat DPI that blocks the transport itself.")
                            }
                        }
                    }
                }
                if (error) item {
                    Text("Analysis could not finish. Check your network and try again.", color = MaterialTheme.colorScheme.error)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Important: DPI behaviour depends on the operator and network. This screen measures reachability and recommends existing transports; it does not promise universal censorship bypass or silently rewrite your profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    private fun analyze() {
        if (running) return
        running = true
        error = false
        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { AiNetworkAdvisor.assess(this@AiDpiShieldActivity) }
                val outcomes = withContext(Dispatchers.IO) {
                    ENDPOINTS.map { endpoint -> probe(endpoint) }
                }
                val access = when {
                    snapshot.cellular -> "Mobile data"
                    snapshot.wifi -> "Wi-Fi"
                    else -> "Other / unknown"
                }
                val link = if (snapshot.downstreamKbps > 0) {
                    "${snapshot.downstreamKbps} kbps down · ${snapshot.upstreamKbps} kbps up"
                } else {
                    "Unavailable"
                }
                val recommendation = when {
                    outcomes.count { it } == 0 ->
                        "Start with the existing Aether/Psiphon automatic or CDN mode, then consider Tor bridges if required. HTTPS tests alone cannot distinguish DPI from general connectivity failure."
                    snapshot.constrained ->
                        "Prefer a conservative TCP/HTTP2-based path first. Test existing Xray profiles, then Aether/Psiphon automatic mode if they fail."
                    else ->
                        "Keep the current profile as baseline and compare it with Aether/Psiphon automatic mode. A successful HTTPS check does not prove UDP or every destination is reachable."
                }
                report = ShieldReport(access, outcomes.count { it }, outcomes.size, link, recommendation)
            } catch (_: Exception) {
                error = true
            } finally {
                running = false
            }
        }
    }

    private fun probe(endpoint: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                instanceFollowRedirects = false
                useCaches = false
            }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private data class ShieldReport(
        val access: String,
        val passed: Int,
        val total: Int,
        val link: String,
        val recommendation: String
    )

    private companion object {
        val ENDPOINTS = listOf(
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
            "https://www.google.com/generate_204"
        )
    }
}
