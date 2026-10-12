package com.v2ray.ang.ui.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.api.vorzex.VorzexInstallationStore
import com.v2ray.ang.api.vorzex.VorzexApiClient
import com.v2ray.ang.api.vorzex.InstallationRegisterRequest
import com.v2ray.ang.api.vorzex.DeviceReportRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class DeviceActivity : ComponentActivity() {
    private var sendingDeviceInfo by mutableStateOf(false)
    private var apiStatus by mutableStateOf("")
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

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val androidVersion = Build.VERSION.RELEASE ?: "نامشخص"
        setContent {
            MaterialTheme {
                DeviceScreen(
                    deviceName = deviceName,
                    androidVersion = androidVersion,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceCode = deviceCode,
                    localIps = localIps,
                    sendingDeviceInfo = sendingDeviceInfo,
                    apiStatus = apiStatus,
                    onSendDeviceInfo = { sendDeviceInfo(deviceCode, deviceName, androidVersion, localIps) },
                    onBack = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun sendDeviceInfo(deviceCode: String, deviceName: String, androidVersion: String, localIps: String) {
        if (sendingDeviceInfo) return
        sendingDeviceInfo = true
        apiStatus = "در حال ارسال اطلاعات دستگاه به API…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val store = VorzexInstallationStore(this@DeviceActivity)
                    val client = VorzexApiClient(
                        baseUrl = BuildConfig.VORZEX_API_BASE_URL,
                        installationTokenProvider = { store.token() },
                        refreshTokenProvider = { store.refreshToken() },
                        tokenSaver = { access, refresh ->
                            store.saveToken(access)
                            if (!refresh.isNullOrBlank()) store.saveRefreshToken(refresh)
                        }
                    )
                    if (store.token().isNullOrBlank()) {
                        val registration = client.registerInstallation(
                            InstallationRegisterRequest(
                                installationId = store.apiInstallationId(),
                                appVersion = BuildConfig.VERSION_NAME,
                                consentedAt = Instant.now().toString()
                            )
                        )
                        store.saveToken(registration.installationToken)
                        registration.refreshToken?.let(store::saveRefreshToken)
                    }
                    client.submitDeviceReport(
                        DeviceReportRequest(
                            deviceCode = deviceCode,
                            deviceName = deviceName.take(120),
                            androidVersion = androidVersion.take(32),
                            localIps = localIps.take(256)
                        )
                    )
                }
                apiStatus = "اطلاعات همین بخش با موفقیت به API ارسال شد."
            } catch (error: Exception) {
                apiStatus = "ارسال ناموفق بود: ${error.message ?: "خطای اتصال"}"
            } finally {
                sendingDeviceInfo = false
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
    sendingDeviceInfo: Boolean,
    apiStatus: String,
    onSendDeviceInfo: () -> Unit,
    onBack: () -> Unit
) {
    var showSendConfirmation by androidx.compose.runtime.remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090D1A)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("VØRZEX · Device", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF70F0FF))
            Text(
                "اطلاعات این صفحه به‌صورت خودکار ارسال نمی‌شوند. فقط با تأیید خودت، شناسهٔ دستگاه، مدل، نسخهٔ اندروید، نسخهٔ برنامه و IPهای محلی همین صفحه به API فرستاده می‌شوند؛ کانفیگ‌ها، کلیدها و تاریخچهٔ ترافیک ارسال نمی‌شوند.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )
            DeviceInfoCard("مدل دستگاه", deviceName)
            DeviceInfoCard("نسخهٔ اندروید", androidVersion)
            DeviceInfoCard("نسخهٔ برنامه", appVersion)
            DeviceInfoCard("شناسهٔ محلی ۱۲ رقمی", deviceCode)
            DeviceInfoCard("IP محلی دستگاه", localIps)
            Text(
                "IP عمومی از سرویس ثالث دریافت نمی‌شود. برای گزارش دستگاه به پنل مدیریت، دکمهٔ زیر را بزن؛ ارسال کاملاً دستی است.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CAAC4)
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !sendingDeviceInfo,
                onClick = { showSendConfirmation = true }
            ) {
                Text(if (sendingDeviceInfo) "در حال ارسال…" else "ارسال اطلاعات دستگاه به API")
            }
            if (apiStatus.isNotBlank()) {
                Text(apiStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFF70F0FF))
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
                Text("بازگشت به صفحهٔ اصلی")
            }
        }
    }
    if (showSendConfirmation) {
        AlertDialog(
            onDismissRequest = { showSendConfirmation = false },
            title = { Text("اجازهٔ ارسال اطلاعات دستگاه؟") },
            text = {
                Text("فقط اطلاعات نمایش‌داده‌شده در این بخش (مدل، نسخهٔ اندروید، نسخهٔ برنامه، شناسهٔ ۱۲ رقمی و IP محلی) برای پنل مدیریت VØRZEX ارسال می‌شود. کانفیگ‌ها، کلیدهای خصوصی و تاریخچهٔ ترافیک ارسال نمی‌شوند.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSendConfirmation = false
                    onSendDeviceInfo()
                }) { Text("تأیید و ارسال") }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirmation = false }) { Text("انصراف") }
            }
        )
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
