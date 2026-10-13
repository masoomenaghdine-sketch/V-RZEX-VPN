package com.v2ray.ang.core

import com.v2ray.ang.enums.AetherTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiNetworkAdvisorTest {
    @Test
    fun cellularNetworkPrefersTcpEvenWithGoodBandwidth() {
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
    fun mediumWifiUsesConservativeTcpTransport() {
        val snapshot = AiNetworkSnapshot(
            cellular = false,
            metered = false,
            downstreamKbps = 9_000,
            upstreamKbps = 2_000,
            signalDbm = -75,
        )
        assertTrue(snapshot.constrained)
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
        assertTrue(snapshot.strongUnmeteredWifi)
        assertFalse(snapshot.constrained)
        assertEquals(AetherTransport.HTTP3, snapshot.preferredTransport)
    }

    @Test
    fun unknownSignalChoosesConservativeTcp() {
        val snapshot = AiNetworkSnapshot(
            cellular = false,
            metered = false,
            downstreamKbps = 80_000,
            upstreamKbps = 20_000,
            signalDbm = null,
        )
        assertEquals(AetherTransport.HTTP2, snapshot.preferredTransport)
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
