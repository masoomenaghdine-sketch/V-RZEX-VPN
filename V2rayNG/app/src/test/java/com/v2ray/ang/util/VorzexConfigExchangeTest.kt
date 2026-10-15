package com.v2ray.ang.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VorzexConfigExchangeTest {
    @Test
    fun roundTripPreservesMultipleProfiles() {
        val expected = listOf(
            VorzexConfigExchange.ProfilePayload("Europe", "xray", """{"outbounds":[]}"""),
            VorzexConfigExchange.ProfilePayload("Backup", "xray", "vless://example")
        )

        val decoded = VorzexConfigExchange.decode(VorzexConfigExchange.encode(expected))

        assertTrue(decoded is VorzexConfigExchange.DecodeResult.Success)
        assertEquals(expected, (decoded as VorzexConfigExchange.DecodeResult.Success).profiles)
    }

    @Test
    fun rejectsUnknownFormat() {
        val decoded = VorzexConfigExchange.decode("""{"format":"other","version":1,"profiles":[]}""")
        assertEquals(
            VorzexConfigExchange.DecodeResult.Failure(VorzexConfigExchange.Reason.WRONG_FORMAT),
            decoded
        )
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val decoded = VorzexConfigExchange.decode(
            """{"format":"VORZEX_CONFIG_EXCHANGE","version":99,"profiles":[]}"""
        )
        assertEquals(
            VorzexConfigExchange.DecodeResult.Failure(VorzexConfigExchange.Reason.UNSUPPORTED_VERSION),
            decoded
        )
    }

    @Test
    fun identifiesExtensionsWithoutPretendingToDecodeNpv() {
        assertTrue(VorzexConfigExchange.isRecognizedExchangeExtension("share.VZV"))
        assertTrue(VorzexConfigExchange.isNpvExtension("legacy.NPV"))
        assertTrue(VorzexConfigExchange.isNpvExtension("legacy.npvs"))
    }
}
