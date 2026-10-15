package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.launch

class TelegramProxyActivity : BaseComponentActivity() {
    private var proxies by mutableStateOf<List<String>>(emptyList())
    private var loading by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var lastRefresh by mutableStateOf(0L)

    override fun onStart() {
        super.onStart()
        TelegramProxyRefreshWorker.schedule(this)
        proxies = TelegramProxyRefreshWorker.cachedProxies(this)
        lastRefresh = TelegramProxyRefreshWorker.lastRefreshMillis(this)
        if (proxies.isEmpty() || System.currentTimeMillis() - lastRefresh >= REFRESH_INTERVAL_MS) {
            refreshProxies()
        } else {
            status = "به‌روزرسانی: ${formatAge(lastRefresh)}"
        }
    }

    @Composable
    override fun ScreenContent() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PT · Telegram Proxy", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { refreshProxies() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator()
                    else Text("به‌روزرسانی پروکسی‌ها")
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(proxies, key = { it }) { proxy ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(Uri.parse(proxy).getQueryParameter("server") ?: "Telegram Proxy", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { openUrl(proxy) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("بازکردن در تلگرام")
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.padding(2.dp)) }
                }
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
            }
        }
    }

    private fun refreshProxies() {
        if (loading) return
        loading = true
        status = "در حال دریافت فهرست پروکسی‌های عمومی…"
        lifecycleScope.launch {
            val result = runCatching { TelegramProxyRefreshWorker.refreshNow(this@TelegramProxyActivity) }
            loading = false
            result.onSuccess { found ->
                proxies = found.ifEmpty { TelegramProxyRefreshWorker.cachedProxies(this@TelegramProxyActivity) }
                lastRefresh = TelegramProxyRefreshWorker.lastRefreshMillis(this@TelegramProxyActivity)
                status = if (proxies.isEmpty()) {
                    "پروکسی پیدا نشد"
                } else {
                    "${proxies.size} پروکسی"
                }
            }.onFailure {
                proxies = TelegramProxyRefreshWorker.cachedProxies(this@TelegramProxyActivity)
                status = if (proxies.isEmpty()) {
                    "دریافت ناموفق"
                } else {
                    "فهرست ذخیره‌شده"
                }
            }
        }
    }

    private fun formatAge(timestamp: Long): String {
        if (timestamp <= 0L) return "نامشخص"
        val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L)
        return when {
            minutes < 1 -> "همین الان"
            minutes < 60 -> "${minutes} دقیقه قبل"
            else -> "${minutes / 60} ساعت قبل"
        }
    }

    private fun openUrl(value: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
        }.onFailure {
            status = "برنامه‌ای برای بازکردن این لینک پیدا نشد."
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2 * 60 * 60 * 1000L
    }
}
