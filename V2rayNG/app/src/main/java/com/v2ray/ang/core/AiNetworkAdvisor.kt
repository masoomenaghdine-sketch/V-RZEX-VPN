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
    val wifi: Boolean = !cellular,
    val validated: Boolean = true,
    val notCongested: Boolean = true,
    val notSuspended: Boolean = true,
    /** True only after a real endpoint probe proves UDP/QUIC is reachable on this network. */
    val udpReachable: Boolean = false,
) {
    /**
     * Strong Wi-Fi alone does not prove UDP/QUIC works: Android's bandwidth and signal estimates
     * cannot detect carrier filtering or a blocked UDP path. Keep the robust TCP/HTTP/2 default
     * unless a real endpoint probe explicitly confirms UDP reachability. This is especially important
     * on metered/cellular networks and during severe connectivity restrictions.
     */
    val strongUnmeteredWifi: Boolean
        get() = wifi &&
            !cellular &&
            !metered &&
            validated &&
            notCongested &&
            notSuspended &&
            downstreamKbps >= MIN_HTTP3_DOWNSTREAM_KBPS &&
            upstreamKbps >= MIN_HTTP3_UPSTREAM_KBPS &&
            signalDbm != null &&
            signalDbm >= MIN_HTTP3_SIGNAL_DBM

    val constrained: Boolean
        get() = !strongUnmeteredWifi

    val preferredTransport: AetherTransport
        get() = if (strongUnmeteredWifi && udpReachable) AetherTransport.HTTP3 else AetherTransport.HTTP2

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
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
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
            wifi = wifi,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            notCongested = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) != false,
            notSuspended = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) != false,
        )
    }
}
