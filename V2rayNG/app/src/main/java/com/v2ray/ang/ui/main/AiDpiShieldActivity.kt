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
                            Text("سپر هوشمند VØRZEX", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "تحلیل محلی شبکه و مقاومت در برابر DPI",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { finish() }) { Text("بازگشت") }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("پیشنهاد هوشمند مسیر اتصال", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "دسترسی HTTPS و تخمین‌های شبکهٔ اندروید بررسی می‌شود تا مسیر مناسب‌تری پیشنهاد شود. تحلیل روی همین دستگاه انجام می‌شود و شناسهٔ سیم‌کارت یا گزارش عیب‌یابی ارسال نمی‌شود.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = ::analyze,
                                enabled = !running,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (running) CircularProgressIndicator()
                                else Text("تحلیل این اتصال")
                            }
                        }
                    }
                }
                if (running) item { Text("در حال بررسی دسترسی HTTPS…", color = MaterialTheme.colorScheme.primary) }
                report?.let { result ->
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("نتیجهٔ تحلیل", style = MaterialTheme.typography.titleLarge)
                                Text("شبکه: ${result.access}")
                                Text("آزمون‌های موفق HTTPS: ${result.passed} از ${result.total}")
                                Text("تخمین لینک: ${result.link}")
                                Text("مسیر پیشنهادی برای شروع", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text(result.recommendation, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("راهنمای پایداری اتصال", style = MaterialTheme.typography.titleMedium)
                                Text("۱. اگر پروفایل‌های Xray وصل نشدند، حالت خودکار یا CDN موجود در Aether/Psiphon را امتحان کنید.")
                                Text("۲. در صورت نیاز از پل‌های Tor استفاده کنید؛ ممکن است دسترسی بهتر شود، اما تضمینی نیست.")
                                Text("۳. یک پروفایل سالم را به‌عنوان مسیر جایگزین نگه دارید و پس از تغییر شبکه دوباره بررسی کنید.")
                                Text("۴. تغییر DNS به‌تنهایی نمی‌تواند DPIای را که خودِ پروتکل را مسدود کرده دور بزند.")
                            }
                        }
                    }
                }
                if (error) item {
                    Text("تحلیل کامل نشد؛ شبکه را بررسی کنید و دوباره تلاش کنید.", color = MaterialTheme.colorScheme.error)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "نکته: رفتار DPI به اپراتور و شبکه بستگی دارد. این صفحه دسترسی را بررسی می‌کند و از میان مسیرهای موجود پیشنهاد می‌دهد؛ دور زدن قطعی همهٔ محدودیت‌ها تضمین نمی‌شود و پروفایل‌ها بی‌اجازه تغییر نمی‌کنند.",
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
                    snapshot.cellular -> "اینترنت همراه"
                    snapshot.wifi -> "وای‌فای"
                    else -> "سایر / نامشخص"
                }
                val link = if (snapshot.downstreamKbps > 0) {
                    "دریافت ${snapshot.downstreamKbps} کیلوبیت · ارسال ${snapshot.upstreamKbps} کیلوبیت"
                } else {
                    "در دسترس نیست"
                }
                val recommendation = when {
                    outcomes.count { it } == 0 ->
                        "ابتدا حالت خودکار یا CDN موجود در Aether/Psiphon را امتحان کنید و در صورت نیاز سراغ پل‌های Tor بروید. آزمون HTTPS به‌تنهایی DPI را از اختلال عمومی شبکه تشخیص نمی‌دهد."
                    snapshot.constrained ->
                        "ابتدا مسیر محافظه‌کارانهٔ TCP/HTTP2 را ترجیح دهید. پروفایل‌های Xray را بررسی کنید و اگر شکست خوردند حالت خودکار Aether/Psiphon را امتحان کنید."
                    else ->
                        "پروفایل فعلی را معیار مقایسه نگه دارید و آن را با حالت خودکار Aether/Psiphon بسنجید. موفقیت HTTPS به معنی دسترسی UDP یا تمام مقصدها نیست."
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
