package com.v2ray.ang.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.v2ray.ang.enums.AetherTorBridges
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.server.ServerAetherActivity

/**
 * A Tor-focused entry point. It reuses the app's existing Aether/Tor engine and profile storage;
 * it deliberately does not claim a transport is available unless the bundled engine reports it.
 */
class TorCenterActivity : HelperBaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("VØRZEX • Tor Center", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "مدیریت اتصال Tor با موتور داخلی برنامه. پروفایل‌های Xray موجود حذف یا تغییر داده نمی‌شوند.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    TorModeCard(
                        title = "اتصال هوشمند",
                        summary = "ابتدا اتصال عادی را بررسی می‌کند و در صورت نیاز از پل‌های در دسترس استفاده می‌کند.",
                        action = "ساخت پروفایل هوشمند",
                        onClick = { openEditor(AetherTorBridges.AUTO.type) }
                    )
                }
                item {
                    TorModeCard(
                        title = "استفاده از پل از ابتدا",
                        summary = "از ابتدا تلاش می‌کند با پل‌های Tor متصل شود.",
                        action = "انتخاب این حالت",
                        onClick = { openEditor(AetherTorBridges.FIRST.type) }
                    )
                }
                item {
                    TorModeCard(
                        title = "اتصال مستقیم",
                        summary = "بدون پل تلاش می‌کند؛ ممکن است در بعضی شبکه‌ها قابل اتصال نباشد.",
                        action = "انتخاب این حالت",
                        onClick = { openEditor(AetherTorBridges.NEVER.type) }
                    )
                }
                item {
                    TorModeCard(
                        title = "پل سفارشی",
                        summary = "برای وارد کردن خطوط پل خودت، ویرایشگر پروفایل را باز کن.",
                        action = "وارد کردن پل",
                        onClick = { openEditor(AetherTorBridges.OWN.type) }
                    )
                }
                item {
                    Text(
                        "نکته: انتخاب حالت به‌تنهایی اتصال را تضمین نمی‌کند. در صورت نبودن موتور یا پل قابل دسترس، گزارش خطا را در صفحهٔ پروفایل بررسی کن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("بازگشت") }
                }
            }
        }
    }

    private fun openEditor(bridgeMode: String) {
        // Return the selected preset to MainActivity. MainActivity then opens the profile editor
        // through its existing result launcher, preserving the established save-and-connect flow.
        setResult(
            RESULT_OK,
            Intent().putExtra(ServerAetherActivity.EXTRA_TOR_BRIDGE_MODE, bridgeMode)
        )
        finish()
    }
}

@Composable
private fun TorModeCard(
    title: String,
    summary: String,
    action: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}
