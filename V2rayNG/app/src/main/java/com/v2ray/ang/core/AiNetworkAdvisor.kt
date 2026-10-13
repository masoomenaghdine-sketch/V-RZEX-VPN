package com.v2ray.ang.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.v2ray.ang.enums.AetherTransport

/**
 * A local, privacy-preserving snapshot used by AI_CON to pick a conservative transport.
 *
 * Android exposes the access-network class and estimated link capacity, but not a reliable,
 * universal SIM/operator firewall profile. We deliberately do not read phone numbers, SIM IDs,
 * or carrier identifiers, and never upload these observations.
 */
internal data class AiNetworkSnapshot(
    val cellular: Boolean,
    val metered: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val signalDbm: Int?,
) {
    /**
     * Prefer HTTP/3 only on a clearly strong, unmetered Wi-Fi link with known signal quality.
     * Cellular, metered, unknown, and constrained links use HTTP/2/TCP because UDP/QUIC can be
     * unreliable on filtered or degraded networks. This is an adaptive heuristic, not a guarantee.
     */
    val strongUnmeteredWifi: Boolean
        get() = !cellular &&
            !metered &&
            downstreamKbps >= MIN_HTTP3_DOWNSTREAM_KBPS &&
            upstreamKbps >= MIN_HTTP3_UPSTREAM_KBPS &&
            signalDbm != null &&
            signalDbm >= MIN_HTTP3_SIGNAL_DBM

    val constrained: Boolean
        get() = !strongUnmeteredWifi

    val preferredTransport: AetherTransport
        get() = if (strongUnmeteredWifi) AetherTransport.HTTP3 else AetherTransport.HTTP2

    companion object {
        internal const val MIN_HTTP3_DOWNSTREAM_KBPS = 15_000
        internal const val MIN_HTTP3_UPSTREAM_KBPS = 3_000
        internal const val MIN_HTTP3_SIGNAL_DBM = -85
    }
}

internal object AiNetworkAdvisor {
    fun assess(context: Context): AiNetworkSnapshot {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val capabilities = if (manager != null && network != null) {
            manager.getNetworkCapabilities(network)
        } else {
            null
        }
        val cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val signal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities?.signalStrength?.takeIf { it != Int.MIN_VALUE && it < 0 }
        } else {
            null
        }
        return AiNetworkSnapshot(
            cellular = cellular,
            metered = manager?.isActiveNetworkMetered ?: true,
            downstreamKbps = capabilities?.linkDownstreamBandwidthKbps ?: 0,
            upstreamKbps = capabilities?.linkUpstreamBandwidthKbps ?: 0,
            signalDbm = signal,
        )
    }
}
