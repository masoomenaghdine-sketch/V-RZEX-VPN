package com.v2ray.ang.ui.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.api.vorzex.VorzexInstallationStore
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class DeviceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceCode = VorzexInstallationStore(this).installationId()
        val localIps = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { it.hostAddress.orEmpty() }
                .distinct()
                .joinToString(", ")
                .ifBlank { "در دسترس نیست" }
        }.getOrDefault("در دسترس نیست")

        setContent {
            MaterialTheme {
                DeviceScreen(
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    androidVersion = Build.VERSION.RELEASE ?: "نامشخص",
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceCode = deviceCode,
                    localIps = localIps,
                    onBack = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceScreen(
    deviceName: String,
    androidVersion: String,
    appVersion: String,
    deviceCode: String,
    localIps: String,
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090D1A)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("VØRZEX · Device", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF70F0FF))
            Text(
                "اطلاعات زیر فقط روی همین صفحه نمایش داده می‌شوند. این صفحه آن‌ها را به API ارسال نمی‌کند؛ کانفیگ‌ها و تاریخچهٔ ترافیک هم ارسال نمی‌شوند.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )
            DeviceInfoCard("مدل دستگاه", deviceName)
            DeviceInfoCard("نسخهٔ اندروید", androidVersion)
            DeviceInfoCard("نسخهٔ برنامه", appVersion)
            DeviceInfoCard("شناسهٔ محلی ۱۲ رقمی", deviceCode)
            DeviceInfoCard("IP محلی دستگاه", localIps)
            Text(
                "این صفحه IP عمومی را از سرویس ثالث دریافت نمی‌کند. وضعیت اتصال و قطع VPN از صفحهٔ اصلی کنترل می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CAAC4)
            )
            Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
                Text("بازگشت به صفحهٔ اصلی")
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D32))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color(0xFF9DF6D5))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}
