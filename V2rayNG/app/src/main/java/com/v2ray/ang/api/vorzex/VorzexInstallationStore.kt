package com.v2ray.ang.api.vorzex

import android.content.Context
import android.provider.Settings
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale

/**
 * Creates a local-only display code for the Device screen.
 * No API tokens, consent state, or remote installation identity are stored.
 */
class VorzexInstallationStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        "vorzex_installation_v1",
        Context.MODE_PRIVATE
    )

    init {
        // One-time privacy cleanup for values stored by older API-enabled builds.
        prefs.edit()
            .remove("installation_token")
            .remove("refresh_token")
            .remove("api_installation_id")
            .remove("consent_granted")
            .remove("consent_decided")
            .remove("consented_at")
            .apply()
    }

    fun installationId(): String {
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
        if (existing != null && existing.matches(Regex("\\d{12}"))) return existing
        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty().ifBlank { "unknown-device" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("VORZEX_DEVICE_CODE_V1:$androidId".toByteArray(Charsets.UTF_8))
        val firstEight = BigInteger(1, digest.copyOfRange(0, 8))
            .mod(BigInteger.valueOf(100_000_000L)).toLong()
        val suffixSource = androidId.takeLast(8).toLongOrNull(16)
            ?: androidId.hashCode().toLong().and(0xffffL)
        val lastFour = suffixSource % 10_000L
        val created = String.format(Locale.US, "%08d%04d", firstEight, lastFour)
        prefs.edit().putString(KEY_INSTALLATION_ID, created).apply()
        return created
    }

    private companion object {
        const val KEY_INSTALLATION_ID = "installation_id"
    }
}
