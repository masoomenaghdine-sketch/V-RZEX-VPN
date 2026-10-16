package com.v2ray.ang.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.base.BaseComponentActivity

/** Notification hub links to Android's real notification controls rather than showing fabricated history. */
class NotificationsActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("اعلان‌های VØRZEX", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "کنترل اعلان‌ها در اختیار خودت است. این صفحه تاریخچهٔ ساختگی نشان نمی‌دهد و تنظیمات واقعی اعلان اندروید را باز می‌کند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("اعلان اتصال VPN", style = MaterialTheme.typography.titleMedium)
                        Text("وقتی سرویس VPN فعال باشد، اعلان پایدار اندروید و کنترل‌های سرویس در پنل اعلان‌ها نمایش داده می‌شوند.")
                        Text("می‌توانی مجوز و نمایش اعلان‌ها را از تنظیمات سیستم تغییر بدهی.")
                    }
                }
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تنظیمات اعلان‌های اندروید") }
                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@NotificationsActivity, AiDpiShieldActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("بازکردن AI NET") }
                OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("بازگشت")
                }
            }
        }
    }
}
