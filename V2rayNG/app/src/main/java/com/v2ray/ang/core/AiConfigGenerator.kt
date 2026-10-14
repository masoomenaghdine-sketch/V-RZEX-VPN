package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.InetSocketAddress
import java.net.Socket
import android.os.SystemClock

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

    /**
     * Probes locally stored Xray endpoints over TCP before choosing one. This is a reachability
     * and handshake-latency signal, not a guarantee that authentication or the full VPN tunnel works.
     * No profiles are downloaded and no credentials are sent to any new endpoint beyond a TCP connect.
     */
    suspend fun bestReachableLocalXrayProfile(snapshot: AiNetworkSnapshot): Candidate? = coroutineScope {
        val stored = MmkvManager.decodeAllServerList().distinct().mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            if (!isUsableXrayProfile(profile)) return@mapNotNull null
            Candidate(
                guid = guid,
                profile = profile,
                pingMillis = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L,
            )
        }
        if (stored.isEmpty()) return@coroutineScope null

        // Probe a bounded shortlist concurrently so a large local list cannot stall AI setup.
        val shortlist = stored.sortedWith(
            compareByDescending<Candidate> { isVlessReality(it.profile) }
                .thenBy { it.pingMillis.takeIf { ping -> ping > 0L } ?: Long.MAX_VALUE }
        ).take(MAX_LIVE_PROBES)

        val reachable = shortlist.map { candidate ->
            async(Dispatchers.IO) {
                val latency = tcpHandshakeLatency(candidate.profile.server.orEmpty(), candidate.profile.serverPort.orEmpty())
                if (latency == null) null else candidate.copy(pingMillis = latency)
            }
        }.awaitAll().filterNotNull()

        rankCandidates(reachable, snapshot)
    }

    private fun tcpHandshakeLatency(host: String, portText: String): Long? {
        val port = portText.toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return try {
            Socket().use { socket ->
                val start = SystemClock.elapsedRealtime()
                socket.connect(InetSocketAddress(host.trim(), port), TCP_PROBE_TIMEOUT_MS)
                SystemClock.elapsedRealtime() - start
            }
        } catch (_: Exception) {
            null
        }
    }

    private const val TCP_PROBE_TIMEOUT_MS = 1_600
    private const val MAX_LIVE_PROBES = 32

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

        val measured = stored.filter { it.pingMillis > 0L }
        val pool = if (measured.isNotEmpty()) measured else stored
        return rankCandidates(pool, snapshot)
    }

    internal fun rankCandidates(candidates: List<Candidate>, snapshot: AiNetworkSnapshot): Candidate? {
        // Do not trust signal strength or advertised bandwidth as proof that UDP is available.
        // Prefer TCP on constrained links, or until a real UDP endpoint probe succeeds.
        val preferTcp = snapshot.constrained || !snapshot.udpReachable
        return candidates.sortedWith(
            compareBy<Candidate> { if (preferTcp && usesDatagramTransport(it.profile)) 1 else 0 }
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
