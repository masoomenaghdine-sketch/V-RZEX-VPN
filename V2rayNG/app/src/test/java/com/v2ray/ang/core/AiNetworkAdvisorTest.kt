package com.v2ray.ang.core

import com.v2ray.ang.enums.AetherTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiNetworkAdvisorTest {
    @Test
    fun cellularNetworkPrefersTcpTransport() {
        val snapshot = AiNetworkSnapshot(
            cellular = true,
            metered = true,
            downstreamKbps = 50_000,
            upstreamKbps = 10_000,
            signalDbm = -75,
        )
        assertTrue(snapshot.constrained)
        assertEquals(AetherTransport.HTTP2, snapshot.preferredTransport)
    }

    @Test
    fun weakWifiPrefersTcpTransport() {
        val snapshot = AiNetworkSnapshot(
            cellular = false,
            metered = false,
            downstreamKbps = 2_000,
            upstreamKbps = 500,
            signalDbm = -105,
        )
        assertEquals(AetherTransport.HTTP2, snapshot.preferredTransport)
    }

    @Test
    fun strongUnmeteredWifiPrefersHttp3() {
        val snapshot = AiNetworkSnapshot(
            cellular = false,
            metered = false,
            downstreamKbps = 80_000,
            upstreamKbps = 20_000,
            signalDbm = -55,
        )
        assertEquals(AetherTransport.HTTP3, snapshot.preferredTransport)
    }

    @Test
    fun unknownNetworkChoosesConservativeTcp() {
        val snapshot = AiNetworkSnapshot(
            cellular = false,
            metered = true,
            downstreamKbps = 0,
            upstreamKbps = 0,
            signalDbm = null,
        )
        assertEquals(AetherTransport.HTTP2, snapshot.preferredTransport)
    }
}
