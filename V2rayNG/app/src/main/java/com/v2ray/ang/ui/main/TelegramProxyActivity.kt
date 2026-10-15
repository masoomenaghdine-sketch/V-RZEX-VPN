package com.v2ray.ang.ui.main

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelegramProxyActivity : BaseComponentActivity() {
    private var proxies by mutableStateOf<List<String>>(emptyList())
    private var latencyByProxy by mutableStateOf<Map<String, Long>>(emptyMap())
    private var query by mutableStateOf("")
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
        val filteredProxies = proxies.filter { proxy ->
            if (query.isBlank()) true else {
                val uri = Uri.parse(proxy)
                uri.getQueryParameter("server").orEmpty().contains(query.trim(), ignoreCase = true) ||
                    uri.getQueryParameter("port").orEmpty().contains(query.trim())
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B14)) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF11172A), Color(0xFF090C16), Color(0xFF06080F))))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(17.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF22D3EE)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("پروکسی تلگرام", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("VØRZEX  /  LIVE", color = Color(0xFF8B9BB9), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp)
                    }
                    Box(
                        modifier = Modifier.clip(CircleShape)
                            .background(if (loading) Color(0xFF493B1D) else Color(0xFF123C36))
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(if (loading) "بررسی…" else "زنده",
                            color = if (loading) Color(0xFFFFD78A) else Color(0xFF70F0C1),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF1B2844), Color(0xFF151A2B), Color(0xFF17152C))))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("سرورهای قابل‌دسترسی", color = Color(0xFFB9C5DC), style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(proxies.size.toString(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("پروکسی", color = Color(0xFF9AA9C5), modifier = Modifier.padding(bottom = 5.dp))
                                }
                            }
                            Box(
                                modifier = Modifier.size(58.dp).clip(CircleShape)
                                    .background(Color(0x2222D3EE)).border(1.dp, Color(0x6638DDF0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↗", color = Color(0xFF67E8F9), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (lastRefresh > 0L) "بررسی ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastRefresh))}" else "هنوز بررسی نشده",
                                color = Color(0xFF8F9DB8), style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { refreshProxies() }, enabled = !loading, shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7658F6), contentColor = Color.White)
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(Modifier.width(7.dp))
                                } else {
                                    Text("↻", fontSize = 18.sp)
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text("تازه‌سازی", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF121827),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263149))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⌕", color = Color(0xFF8B9BB9), fontSize = 23.sp)
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("جست‌وجوی سرور یا پورت", color = Color(0xFF65738D))
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            Text(
                                "پاک", color = Color(0xFF9B8BFF),
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { query = "" }
                                    .background(Color(0x227658F6)).padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (query.isBlank()) "بهترین پاسخ‌ها" else "نتایج جست‌وجو",
                        color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("${filteredProxies.size} نتیجه", color = Color(0xFF7D8BA5), style = MaterialTheme.typography.labelMedium)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(filteredProxies, key = { it }) { proxy ->
                        val uri = Uri.parse(proxy)
                        val server = uri.getQueryParameter("server").orEmpty().removeSuffix(".")
                        val port = uri.getQueryParameter("port").orEmpty()
                        val latency = latencyByProxy[proxy]
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121827)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263149))
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B3040)),
                                    contentAlignment = Alignment.Center
                                ) { Text("↗", color = Color(0xFF67E8F9), fontSize = 22.sp) }
                                Spacer(Modifier.width(11.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(server.ifBlank { "MTProto" }, color = Color.White, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("PORT $port", color = Color(0xFF8493AD), style = MaterialTheme.typography.labelSmall)
                                        Spacer(Modifier.width(8.dp))
                                        Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF123C36)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                            Text("قابل‌دسترسی", color = Color(0xFF70F0C1), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(latency?.let { "${it} ms" } ?: "آنلاین", color = Color(0xFF67E8F9),
                                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.height(6.dp))
                                    Button(
                                        onClick = { openProxy(proxy) }, shape = RoundedCornerShape(13.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7658F6), contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)
                                    ) { Text("اتصال", fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                    }
                    if (filteredProxies.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121827))) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(if (loading) "در حال بررسی سرورها…" else "پروکسی پاسخ‌گو پیدا نشد",
                                        color = Color.White, fontWeight = FontWeight.SemiBold)
                                    if (!loading) OutlinedButton(onClick = { refreshProxies() }) { Text("تلاش دوباره") }
                                    else CircularProgressIndicator(color = Color(0xFF67E8F9))
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { finish() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB7C2D8))
                ) { Text("بازگشت") }
            }
        }
    }

    private fun refreshProxies() {
        if (loading) return
        loading = true
        lifecycleScope.launch {
            val result = runCatching { TelegramProxyRefreshWorker.refreshNow(this@TelegramProxyActivity) }
            loading = false
            result.onSuccess { active ->
                latencyByProxy = active
                proxies = active.keys.filter(TelegramProxyFeed::isValidProxyLink).distinct()
                lastRefresh = TelegramProxyRefreshWorker.lastRefreshMillis(this@TelegramProxyActivity)
                if (proxies.isEmpty()) Toast.makeText(this@TelegramProxyActivity, "پروکسی پاسخ‌گو پیدا نشد", Toast.LENGTH_SHORT).show()
            }.onFailure {
                proxies = TelegramProxyRefreshWorker.cachedProxies(this@TelegramProxyActivity)
                latencyByProxy = emptyMap()
                Toast.makeText(this@TelegramProxyActivity,
                    if (proxies.isEmpty()) "دریافت پروکسی ناموفق بود" else "فهرست ذخیره‌شده نمایش داده شد",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openProxy(value: String) {
        val original = Uri.parse(value)
        val telegramUri = if (
            original.scheme.equals("https", ignoreCase = true) &&
            original.host.equals("t.me", ignoreCase = true) &&
            original.path.equals("/proxy", ignoreCase = true)
        ) {
            Uri.Builder().scheme("tg").authority("proxy").encodedQuery(original.encodedQuery).build()
        } else original

        try {
            val intent = Intent(Intent.ACTION_VIEW, telegramUri)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MTProto", original.toString()))
        Toast.makeText(this, "لینک پروکسی کپی شد", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2 * 60 * 60 * 1000L
    }
}
