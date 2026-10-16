package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private data class CnrSource(val title: String, val url: String)

class CnrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CnrScreen(
                    onImport = { url ->
                        val deepLink = Uri.parse("v2rayng://install-config?url=" + Uri.encode(url))
                        startActivity(Intent(Intent.ACTION_VIEW, deepLink))
                    },
                    onCountries = { startActivity(Intent(this, CountryConfigsActivity::class.java)) },
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
private fun CnrScreen(
    onImport: (String) -> Unit,
    onCountries: () -> Unit,
    onBackToConnect: () -> Unit
) {
    val glow = rememberInfiniteTransition(label = "cnrGlow")
    val pulse by glow.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Reverse),
        label = "cnrPulse"
    )
    val sources = listOf(
        CnrSource("همهٔ کانفیگ‌ها", "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/all_servers.txt"),
        CnrSource("VLESS", "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/vless_servers.txt"),
        CnrSource("VMess", "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/vmess_servers.txt"),
        CnrSource("Trojan", "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/trojan_servers.txt"),
        CnrSource("Hysteria 2", "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/hysteria2_servers.txt"),
        CnrSource("Shadowsocks", "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/servers/ss_servers.txt")
    )
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080A14)) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VØRZEX · کانفیگ رایگان", modifier = Modifier.graphicsLayer(alpha = pulse), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF72F5E8))
            Button(modifier = Modifier.fillMaxWidth(), onClick = onCountries) {
                Text("🌍  کشورها · کانفیگ بر اساس موقعیت")
            }
            Text("فهرست کشورها از مخزن‌های عمومی دریافت می‌شود. پایداری و امنیت کانفیگ‌های عمومی تضمین‌شده نیست.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC7D1EF))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sources) { source ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF151D32))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(source.title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF9DF6D5))
                            Button(modifier = Modifier.fillMaxWidth(), onClick = { onImport(source.url) }) { Text("افزودن به کانفیگ‌ها") }
                        }
                    }
                }
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onBackToConnect) { Text("بازگشت") }
        }
    }
}
