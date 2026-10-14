package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLConnection

class TelegramProxyActivity : BaseComponentActivity() {
    private var proxies by mutableStateOf<List<String>>(emptyList())
    private var loading by mutableStateOf(false)
    private var status by mutableStateOf("")

    override fun onStart() {
        super.onStart()
        if (proxies.isEmpty() && !loading) refreshProxies()
    }

    @Composable
    override fun ScreenContent() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PT · Telegram Proxy", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "پروکسی‌های عمومی MTProto برای خود تلگرام هستند؛ به‌تنهایی VPN کل دستگاه را فعال نمی‌کنند.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { refreshProxies() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator()
                    else Text("جست‌وجو و به‌روزرسانی پروکسی‌ها")
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { openUrl("https://t.me/PeopleProxy") }, modifier = Modifier.weight(1f)) {
                        Text("PeopleProxy")
                    }
                    Button(onClick = { openUrl("https://t.me/iRoProxy") }, modifier = Modifier.weight(1f)) {
                        Text("iRoProxy")
                    }
                }
                Button(
                    onClick = { openUrl("https://github.com/SoliSpirit/mtproto") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("منبع فهرست MTProto") }
                Text(
                    "برای استفاده، یک پروکسی را انتخاب کنید تا تلگرام آن را باز کند. پروکسی‌های عمومی ممکن است آفلاین یا غیرقابل‌اعتماد باشند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(proxies, key = { it }) { proxy ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(proxy.take(100), style = MaterialTheme.typography.bodySmall)
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
        status = "در حال دریافت فهرست عمومی…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val connection: URLConnection = URL(PROXY_FEED_URL).openConnection()
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.startsWith("tg://proxy?", true) || it.startsWith("https://t.me/proxy?", true) || it.startsWith("http://t.me/proxy?", true) }
                        .distinct()
                        .take(100)
                        .toList()
                }
            }
            loading = false
            result.onSuccess { found ->
                proxies = found
                status = if (found.isEmpty()) {
                    "در فهرست منبع فعلاً پروکسی قابل‌خواندن پیدا نشد؛ کانال‌های بالا را هم می‌توانی بررسی کنی."
                } else {
                    "تعداد ${found.size} لینک پروکسی دریافت شد؛ فعال‌بودن آن‌ها هنوز تضمین نشده است."
                }
            }.onFailure {
                status = "دریافت فهرست ناموفق بود. اتصال اینترنت را بررسی کن یا از کانال‌های بالا استفاده کن."
            }
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
        private const val PROXY_FEED_URL =
            "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt"
    }
}
