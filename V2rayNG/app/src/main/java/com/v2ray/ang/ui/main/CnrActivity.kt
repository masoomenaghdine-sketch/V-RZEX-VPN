package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent

private data class CnrSource(val title: String, val detail: String, val url: String)

class CnrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CnrScreen(
                    onImport = { url ->
                        val deepLink = Uri.parse("v2rayng://install-config?url=${Uri.encode(url)}")
                        startActivity(Intent(Intent.ACTION_VIEW, deepLink))
                    },
                    onBackToConnect = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun CnrScreen(onImport: (String) -> Unit, onBackToConnect: () -> Unit) {
    val sources = listOf(
        CnrSource(
            "همهٔ کانفیگ‌ها",
            "فهرست عمومی تجمیع‌شده؛ ممکن است بعضی سرورها از دسترس خارج شده باشند",
            "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/all_servers.txt"
        ),
        CnrSource(
            "VLESS",
            "کانفیگ‌های VLESS",
            "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/vless_servers.txt"
        ),
        CnrSource(
            "VMess",
            "کانفیگ‌های VMess",
            "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/vmess_servers.txt"
        ),
        CnrSource(
            "Trojan",
            "کانفیگ‌های Trojan",
            "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/trojan_servers.txt"
        ),
        CnrSource(
            "Hysteria 2",
            "کانفیگ‌های Hysteria2",
            "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/hysteria2_servers.txt"
        ),
        CnrSource(
            "Shadowsocks",
            "کانفیگ‌های Shadowsocks",
            "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/ss_servers.txt"
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090D1A)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("CNR · VØRZEX VPN", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF70F0FF))
            Text(
                "مرکز کانفیگ‌های عمومی",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                "یک منبع را انتخاب کن تا کانفیگ‌ها از مخزن عمومی وارد فهرست برنامه شوند. کانفیگ‌های عمومی ممکن است ناپایدار یا نامعتبر باشند؛ قبل از اتصال، آن‌ها را بررسی کن.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sources) { source ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D32))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(source.title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF9DF6D5))
                            Text(source.detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD5DDF0))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onImport(source.url) }
                            ) {
                                Text("افزودن به کانفیگ‌ها")
                            }
                        }
                    }
                }
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onBackToConnect) {
                Text("بازگشت به صفحهٔ اتصال / قطع اتصال")
            }
        }
    }
}
