package com.v2ray.ang.ui.main

import android.content.Intent
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
                                "عیب‌یابی محلی شبکه و پایداری اتصال",
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
                            Text("تشخیص هوشمند کیفیت اتصال", style = MaterialTheme.typography.titleMedium)
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
                                Text("۱. اگر اتصال ناپایدار است، وضعیت اینترنت و پیام خطای برنامه را بررسی کنید.")
                                Text("۲. شبکهٔ دیگری را که مجاز به استفاده از آن هستید امتحان کنید تا مشکل اپراتور از مشکل دستگاه جدا شود.")
                                Text("۳. تنظیمات اتصال را به حالت شناخته‌شده و معتبر برگردانید و دوباره آزمایش کنید.")
                                Text("۴. تغییر DNS به‌تنهایی علت همهٔ خطاهای شبکه را برطرف نمی‌کند.")
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("ابزارهای پایداری VØRZEX", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "AI_CON پروفایل‌های موجود روی دستگاه را بررسی می‌کند، اتصال تونل را می‌آزماید و در صورت شکست، گزینه‌های جایگزین موجود را امتحان می‌کند.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    startActivity(
                                        Intent(this@AiDpiShieldActivity, MainActivity::class.java)
                                            .putExtra(MainActivity.EXTRA_OPEN_AI_CON, true)
                                    )
                                    finish()
                                }
                            ) { Text("اجرای AI_CON و اتصال هوشمند") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { startActivity(Intent(this@AiDpiShieldActivity, CnrActivity::class.java)) }
                                ) { Text("کانفیگ رایگان") }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { startActivity(Intent(this@AiDpiShieldActivity, CountriesActivity::class.java)) }
                                ) { Text("کشورها") }
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
                        "نکته: این صفحه فقط دسترسی HTTPS را به‌صورت محلی بررسی می‌کند؛ تشخیص قطعی نوع محدودیت شبکه یا تضمین دسترسی به همهٔ سرویس‌ها ممکن نیست. هیچ پروفایلی بی‌اجازه تغییر نمی‌کند.",
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
                        "کیفیت لینک محدود به نظر می‌رسد. برنامه‌های پس‌زمینه را کاهش دهید، نزدیک‌تر به روتر شوید یا شبکه را دوباره بررسی کنید."
                    else ->
                        "آزمون HTTPS موفق است؛ بااین‌حال این نتیجه فقط دسترسی به چند مقصد آزمایشی را نشان می‌دهد و کیفیت همهٔ سرویس‌ها را تضمین نمی‌کند."
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
