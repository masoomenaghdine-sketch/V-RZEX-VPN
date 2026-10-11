package com.v2ray.ang.ui

import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AboutActivity : BaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        AboutScreen(onBackClick = { finish() })
    }
}

@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var showInfo by remember { mutableStateOf(false) }
    var publicIp by remember { mutableStateOf("در حال دریافت…") }
    var usageDuration by remember { mutableStateOf(formatVpnUsage()) }

    val deviceName = (Build.MANUFACTURER + " " + Build.MODEL).trim().ifBlank { "نامشخص" }
    val userId = MmkvManager.decodeSettingsString(AppConfig.PREF_USER_ID) ?: "در حال ساخت…"

    LaunchedEffect(showInfo) {
        if (showInfo) {
            usageDuration = formatVpnUsage()
            publicIp = withContext(Dispatchers.IO) { fetchPublicIp() }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_about),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_feedback_24dp),
                title = stringResource(R.string.title_pref_feedback),
                onClick = { Utils.openUri(context, AppConfig.APP_ISSUES_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_telegram_24dp),
                title = stringResource(R.string.title_tg_channel),
                onClick = { Utils.openUri(context, AppConfig.TG_CHANNEL_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_privacy_24dp),
                title = "اطلاعات",
                onClick = { showInfo = true }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_privacy_24dp),
                title = stringResource(R.string.title_privacy_policy),
                onClick = { Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY) }
            )
            NavigationBarsSpacer()
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("اطلاعات دستگاه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نوع گوشی: " + deviceName)
                    Text("IP عمومی: " + publicIp)
                    Text("مدت استفاده از فیلترشکن: " + usageDuration)
                    Text("شناسه کاربر: " + userId)
                    Text(
                        "برای نمایش IP عمومی، درخواست فقط برای دریافت IP به سرویس بیرونی ارسال می‌شود."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("بستن")
                }
            }
        )
    }
}

private fun formatVpnUsage(): String {
    val savedTotal = MmkvManager.decodeSettingsLong(AppConfig.PREF_VPN_USAGE_TOTAL_MS, 0L)
    val startedAt = MmkvManager.decodeSettingsLong(AppConfig.PREF_VPN_USAGE_STARTED_AT, 0L)
    val activeSession = if (startedAt > 0L) {
        (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    } else {
        0L
    }
    val totalMinutes = (savedTotal + activeSession) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return hours.toString() + " ساعت و " + minutes + " دقیقه"
}

private fun fetchPublicIp(): String {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(AppConfig.IP_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            useCaches = false
        }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(response).optString("ip").ifBlank { "نامشخص" }
    } catch (_: Exception) {
        "دریافت نشد"
    } finally {
        connection?.disconnect()
    }
}
