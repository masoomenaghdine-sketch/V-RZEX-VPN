package com.v2ray.ang.ui.main

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.settings.SettingsActivity

/** Local-only VØRZEX profile hub. No account or remote identity is required. */
class ProfileActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.vorzex_icon_foreground),
                        contentDescription = "VØRZEX VPN",
                        modifier = Modifier.size(76.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("VØRZEX VPN", style = MaterialTheme.typography.headlineSmall)
                        Text("Freedom Beyond Borders", color = MaterialTheme.colorScheme.primary)
                        Text("پروفایل و حریم خصوصی", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("حساب کاربری محلی", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "برای استفاده از VPN نیازی به ساخت حساب یا ارسال شناسهٔ دستگاه نیست. تنظیمات و پروفایل‌های اتصال روی دستگاه مدیریت می‌شوند.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("وضعیت: بدون نیاز به ورود", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Button(
                    onClick = { startActivity(Intent(this@ProfileActivity, DeviceActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اطلاعات دستگاه") }
                OutlinedButton(
                    onClick = { startActivity(Intent(this@ProfileActivity, SettingsActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تنظیمات برنامه") }
                OutlinedButton(
                    onClick = { startActivity(Intent(this@ProfileActivity, AboutActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("دربارهٔ VØRZEX و مجوزها") }
                OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("بازگشت")
                }
            }
        }
    }
}
