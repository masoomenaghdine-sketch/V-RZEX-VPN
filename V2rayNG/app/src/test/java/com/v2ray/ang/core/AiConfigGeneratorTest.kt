package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiConfigGeneratorTest {
    private fun reality(guid: String, ping: Long = 0L) = AiConfigGenerator.Candidate(
        guid = guid,
        pingMillis = ping,
        profile = ProfileItem.create(EConfigType.VLESS).apply {
            remarks = guid
            server = "$guid.example.net"
            serverPort = "443"
            password = "00000000-0000-0000-0000-000000000000"
            security = AppConfig.REALITY
            publicKey = "valid-public-key"
            shortId = "abcd1234"
            network = "tcp"
        }
    )

    @Test
    fun lowestMeasuredPingWinsAmongKnownWorkingCandidates() {
        val snapshot = AiNetworkSnapshot(
            cellular = true, metered = true, downstreamKbps = 8000, upstreamKbps = 1000, signalDbm = -95
        )
        val result = AiConfigGenerator.rankCandidates(
            listOf(reality("slow", 180), reality("fast", 42), reality("medium", 91)),
            snapshot
        )
        assertEquals("fast", result?.guid)
    }

    @Test
    fun realityIsPreferredWhenNoPingHasBeenMeasured() {
        val snapshot = AiNetworkSnapshot(
            cellular = true, metered = true, downstreamKbps = 8000, upstreamKbps = 1000, signalDbm = -95
        )
        val ordinaryTls = reality("tls").profile.apply {
            security = "tls"
            publicKey = null
        }
        val result = AiConfigGenerator.rankCandidates(
            listOf(
                AiConfigGenerator.Candidate("tls", ordinaryTls),
                reality("reality")
            ),
            snapshot
        )
        assertEquals("reality", result?.guid)
    }

    @Test
    fun realityWithoutPublicKeyIsNotUsable() {
        val profile = reality("broken").profile.apply { publicKey = null }
        assertFalse(AiConfigGenerator.isUsableXrayProfile(profile))
    }

    @Test
    fun usableRealityProfileRequiresServerAndValidPort() {
        val profile = reality("valid").profile
        assertTrue(AiConfigGenerator.isUsableXrayProfile(profile))
        profile.serverPort = "70000"
        assertFalse(AiConfigGenerator.isUsableXrayProfile(profile))
    }
}
