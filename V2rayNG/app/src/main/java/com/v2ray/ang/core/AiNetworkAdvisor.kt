package com.v2ray.ang.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.v2ray.ang.enums.AetherTransport

/**
 * A local, privacy-preserving snapshot used by AI_CON to pick a conservative transport.
 * It does not read the SIM phone number, carrier identifiers, or send network data anywhere.
 */
internal data class AiNetworkSnapshot(
    val cellular: Boolean,
    val metered: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val signalDbm: Int?,
) {
    /**
     * HTTP/2 over TCP is the conservative choice for metered/cellular, weak-signal, or
     * bandwidth-constrained links. HTTP/3 is preferred only on a seemingly strong unmetered link.
     * This is a heuristic, not a promise that a protocol is reachable on a filtered network.
     */
    val constrained: Boolean
        get() = cellular || metered ||
            (downstreamKbps <= 5_000) ||
            (upstreamKbps <= 1_000) ||
            (signalDbm != null && signalDbm < -100)

    val preferredTransport: AetherTransport
        get() = if (constrained) AetherTransport.HTTP2 else AetherTransport.HTTP3
}

internal object AiNetworkAdvisor {
    fun assess(context: Context): AiNetworkSnapshot {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val capabilities = if (manager != null && network != null) manager.getNetworkCapabilities(network) else null
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
