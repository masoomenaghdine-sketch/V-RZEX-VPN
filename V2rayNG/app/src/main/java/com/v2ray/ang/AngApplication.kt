package com.v2ray.ang

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.ThemeManager
import java.util.Locale
import java.util.UUID

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let(ContextCompat::getContextForLanguage))
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Creates one stable local identifier on first launch. Only the last four Android ID
     * characters are included; the full Android ID is never stored or displayed.
     */
    private fun ensureUserIdentity() {
        if (!MmkvManager.decodeSettingsString(AppConfig.PREF_USER_ID).isNullOrBlank()) return

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val deviceSuffix = androidId.takeLast(4).uppercase(Locale.ROOT).ifBlank { "0000" }
        val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.ROOT)
        MmkvManager.encodeSettings(AppConfig.PREF_USER_ID, "VZ-" + deviceSuffix + "-" + randomSuffix)
    }

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MmkvManager.initialize(this)
        ensureUserIdentity()

        AppLocaleManager.initialize(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)

        // PattNG: the Aether cores of this process listen on the port of the settings.
        AetherCoreManager.listenPortSource = SettingsManager::getAetherListenPort

        // Initialize theme state from MMKV
        ThemeManager.refresh()
    }
}
