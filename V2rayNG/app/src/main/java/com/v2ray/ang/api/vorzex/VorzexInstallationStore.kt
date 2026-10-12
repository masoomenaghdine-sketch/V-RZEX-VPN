package com.v2ray.ang.api.vorzex

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores a stable device code, consent choice, and API token. The code is derived from Android ID so it survives app reinstall under the same Android signing identity.
 * The bearer token is encrypted with an Android Keystore AES-GCM key.
 * VPN profiles and traffic data are never stored here.
 */
class VorzexInstallationStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        "vorzex_installation_v1",
        Context.MODE_PRIVATE
    )

    fun hasConsentDecision(): Boolean = prefs.contains(KEY_CONSENT_DECIDED)

    fun isConsentGranted(): Boolean = prefs.getBoolean(KEY_CONSENT_GRANTED, false)

    fun consentedAt(): String? = prefs.getString(KEY_CONSENTED_AT, null)

    fun setConsent(granted: Boolean, timestamp: String?) {
        prefs.edit()
            .putBoolean(KEY_CONSENT_DECIDED, true)
            .putBoolean(KEY_CONSENT_GRANTED, granted)
            .apply {
                if (granted && !timestamp.isNullOrBlank()) putString(KEY_CONSENTED_AT, timestamp)
                else remove(KEY_CONSENTED_AT)
            }
            .apply()
        if (!granted) clearToken()
    }

    /**
     * A 12-digit device code: eight pseudorandom-looking digits derived from SHA-256 and
     * four trailing digits derived from Android ID. Android ID remains stable across reinstall
     * for the same signing key, Android user, and device. Factory reset or signing-key changes
     * may change it.
     */
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

    /**
     * High-entropy API identity, separate from the 12-digit code shown on the Device screen.
     * It is only registered after the user explicitly chooses to send Device-screen information.
     */
    fun apiInstallationId(): String {
        val existing = prefs.getString(KEY_API_INSTALLATION_ID, null)
        if (existing != null && existing.matches(Regex("[0-9a-f]{64}"))) return existing
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val created = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_API_INSTALLATION_ID, created).apply()
        return created
    }

    fun saveToken(token: String) {
        require(token.isNotBlank()) { "Installation token must not be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString(KEY_TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun token(): String? = runCatching {
        val packed = Base64.decode(prefs.getString(KEY_TOKEN, null), Base64.NO_WRAP)
        require(packed.size > IV_LENGTH) { "Invalid encrypted token" }
        val iv = packed.copyOfRange(0, IV_LENGTH)
        val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vorzex.installation-token.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_TOKEN = "installation_token"
        const val KEY_API_INSTALLATION_ID = "api_installation_id"
        const val KEY_CONSENT_DECIDED = "consent_decided"
        const val KEY_CONSENT_GRANTED = "consent_granted"
        const val KEY_CONSENTED_AT = "consented_at"
    }
}
