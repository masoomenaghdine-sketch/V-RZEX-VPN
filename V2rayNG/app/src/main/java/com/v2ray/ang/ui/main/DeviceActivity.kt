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

class DeviceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceCode = VorzexInstallationStore(this).installationId()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val androidVersion = Build.VERSION.RELEASE ?: "نامشخص"
        setContent {
            MaterialTheme {
                DeviceScreen(
                    deviceName = deviceName,
                    androidVersion = androidVersion,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceCode = deviceCode,
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
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090D1A)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("دستگاه", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF70F0FF))
            DeviceInfoCard("دستگاه", deviceName)
            DeviceInfoCard("اندروید", androidVersion)
            DeviceInfoCard("نسخهٔ برنامه", appVersion)
            DeviceInfoCard("شناسه", deviceCode)
            Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
                Text("بازگشت")
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
