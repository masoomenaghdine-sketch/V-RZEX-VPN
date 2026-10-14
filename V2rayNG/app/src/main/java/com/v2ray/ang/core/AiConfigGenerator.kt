package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager

/**
 * Selects only endpoints already stored on this device. It never downloads configs or invents
 * server addresses/credentials. AI_CON is a locally generated profile based on a usable Xray endpoint.
 */
internal object AiConfigGenerator {
    internal data class Candidate(
        val guid: String,
        val profile: ProfileItem,
        val pingMillis: Long = 0L,
    )

    fun bestLocalXrayProfile(snapshot: AiNetworkSnapshot): Candidate? {
        val stored = MmkvManager.decodeAllServerList().distinct().mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            if (!isUsableXrayProfile(profile)) return@mapNotNull null
            Candidate(
                guid = guid,
                profile = profile,
                pingMillis = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L,
            )
        }
        if (stored.isEmpty()) return null

        // Prefer a known-good latency result when one exists. Otherwise choose a conservative
        // TCP-friendly profile on restricted/metered links, then prefer VLESS+Reality.
        val measured = stored.filter { it.pingMillis > 0L }
        val pool = if (measured.isNotEmpty()) measured else stored
        return pool.sortedWith(
            compareBy<Candidate> { if (snapshot.constrained && usesDatagramTransport(it.profile)) 1 else 0 }
                .thenBy { it.pingMillis.takeIf { ping -> ping > 0L } ?: Long.MAX_VALUE }
                .thenByDescending { isVlessReality(it.profile) }
                .thenByDescending { protocolPriority(it.profile) }
        ).firstOrNull()
    }

    internal fun isUsableXrayProfile(profile: ProfileItem): Boolean {
        val host = profile.server?.trim().orEmpty()
        val port = profile.serverPort?.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) return false

        return when (profile.configType) {
            EConfigType.VLESS -> !profile.password.isNullOrBlank() &&
                (!profile.security.equals(AppConfig.REALITY, ignoreCase = true) || !profile.publicKey.isNullOrBlank())
            EConfigType.VMESS -> !profile.password.isNullOrBlank()
            EConfigType.TROJAN -> !profile.password.isNullOrBlank()
            EConfigType.SHADOWSOCKS -> !profile.password.isNullOrBlank() && !profile.method.isNullOrBlank()
            EConfigType.HYSTERIA2 -> !profile.password.isNullOrBlank()
            EConfigType.WIREGUARD -> !profile.publicKey.isNullOrBlank() && !profile.secretKey.isNullOrBlank()
            else -> false
        }
    }

    internal fun isVlessReality(profile: ProfileItem): Boolean =
        profile.configType == EConfigType.VLESS &&
            profile.security.equals(AppConfig.REALITY, ignoreCase = true) &&
            !profile.publicKey.isNullOrBlank() &&
            !profile.password.isNullOrBlank()

    private fun usesDatagramTransport(profile: ProfileItem): Boolean =
        profile.configType == EConfigType.HYSTERIA2 ||
            profile.network.equals("kcp", ignoreCase = true) ||
            profile.network.equals("quic", ignoreCase = true)

    private fun protocolPriority(profile: ProfileItem): Int = when {
        isVlessReality(profile) -> 500
        profile.configType == EConfigType.VLESS -> 400
        profile.configType == EConfigType.TROJAN -> 300
        profile.configType == EConfigType.VMESS -> 200
        profile.configType == EConfigType.SHADOWSOCKS -> 100
        else -> 0
    }
}
