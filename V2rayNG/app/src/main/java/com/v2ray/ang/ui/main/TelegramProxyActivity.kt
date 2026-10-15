package com.v2ray.ang.ui.main

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.launch

class TelegramProxyActivity : BaseComponentActivity() {
    private var proxies by mutableStateOf<List<String>>(emptyList())
    private var loading by mutableStateOf(false)
    private var lastRefresh by mutableStateOf(0L)

    override fun onStart() {
        super.onStart()
        TelegramProxyRefreshWorker.schedule(this)
        proxies = TelegramProxyRefreshWorker.cachedProxies(this)
        lastRefresh = TelegramProxyRefreshWorker.lastRefreshMillis(this)
        if (proxies.isEmpty() || System.currentTimeMillis() - lastRefresh >= REFRESH_INTERVAL_MS) {
            refreshProxies()
        }
    }

    @Composable
    override fun ScreenContent() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { finish() }, modifier = Modifier.weight(1f)) { Text("بازگشت") }
                    Button(onClick = { refreshProxies() }, enabled = !loading, modifier = Modifier.weight(1f)) {
                        if (loading) CircularProgressIndicator()
                        else Text("تازه‌سازی")
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(proxies, key = { it }) { proxy ->
                        val uri = Uri.parse(proxy)
                        val server = uri.getQueryParameter("server").orEmpty().removeSuffix(".")
                        val port = uri.getQueryParameter("port").orEmpty()
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(server.ifBlank { "MTProto" }, style = MaterialTheme.typography.bodyMedium)
                                    Text(port, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(onClick = { openProxy(proxy) }) { Text("افزودن") }
                            }
                        }
                    }
                    if (proxies.isEmpty() && !loading) {
                        item {
                            OutlinedButton(onClick = { refreshProxies() }, modifier = Modifier.fillMaxWidth()) {
                                Text("دریافت پروکسی")
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.padding(2.dp)) }
                }
            }
        }
    }

    private fun refreshProxies() {
        if (loading) return
        loading = true
        lifecycleScope.launch {
            val result = runCatching { TelegramProxyRefreshWorker.refreshNow(this@TelegramProxyActivity) }
            loading = false
            result.onSuccess { found ->
                proxies = (found + TelegramProxyRefreshWorker.cachedProxies(this@TelegramProxyActivity))
                    .filter(TelegramProxyFeed::isValidProxyLink)
                    .distinct()
                lastRefresh = TelegramProxyRefreshWorker.lastRefreshMillis(this@TelegramProxyActivity)
                if (proxies.isEmpty()) {
                    Toast.makeText(this@TelegramProxyActivity, "پروکسی پیدا نشد", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                proxies = TelegramProxyRefreshWorker.cachedProxies(this@TelegramProxyActivity)
                Toast.makeText(
                    this@TelegramProxyActivity,
                    if (proxies.isEmpty()) "خطا در دریافت پروکسی" else "پروکسی ذخیره‌شده نمایش داده شد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openProxy(value: String) {
        val original = Uri.parse(value)
        val telegramUri = if (original.scheme.equals("https", ignoreCase = true) &&
            original.host.equals("t.me", ignoreCase = true) &&
            original.path.equals("/proxy", ignoreCase = true)
        ) {
            Uri.Builder().scheme("tg").authority("proxy").encodedQuery(original.encodedQuery).build()
        } else original

        val candidates = listOf(telegramUri, original).distinct()
        for (candidate in candidates) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, candidate))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the universal link, then offer a copy fallback.
            } catch (_: SecurityException) {
                // Try the next compatible link.
            }
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MTProto", original.toString()))
        Toast.makeText(this, "لینک پروکسی کپی شد", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2 * 60 * 60 * 1000L
    }
}
