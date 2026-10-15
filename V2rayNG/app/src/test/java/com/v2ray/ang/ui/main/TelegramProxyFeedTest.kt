package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelegramProxyFeedTest {
    @Test
    fun parsesValidTelegramProxyLinksAndHtmlEscapedSeparators() {
        val first = "https://t.me/proxy?server=proxy.example.com&port=443&secret=dd10400103324995"
        val second = "tg://proxy?server=proxy2.example.com&amp;port=8443&amp;secret=eeNEgYdJvXrFGRMC"
        val third = "https://t.me/proxy?server=proxy3.example.com&port=443&secret=abcd+efghijkl"
        val body = """
            $first
            $second
            $third
            $first
            https://t.me/proxy?server=bad.example.com&port=99999&secret=dd10400103324995
            https://example.com/not-a-proxy?server=bad.example.com&port=443&secret=dd10400103324995
        """.trimIndent()

        val parsed = TelegramProxyFeed.parseProxyLinks(body)

        assertEquals(3, parsed.size)
        assertTrue(parsed.contains(first))
        assertTrue(parsed.contains(third))
        assertTrue(parsed.contains("tg://proxy?server=proxy2.example.com&port=8443&secret=eeNEgYdJvXrFGRMC"))
    }

    @Test
    fun rejectsLinksWithoutRequiredParameters() {
        val body = """
            https://t.me/proxy?server=proxy.example.com&port=443
            https://t.me/proxy?server=proxy.example.com&port=abc&secret=dd10400103324995
            https://t.me/proxy?server=proxy.example.com&port=443&secret=short
        """.trimIndent()

        assertTrue(TelegramProxyFeed.parseProxyLinks(body).isEmpty())
    }
    @Test
    fun normalizesTrailingDotInPublicProxyHostnames() {
        val link = "https://t.me/proxy?server=proxy.example.com.&port=443&secret=dd10400103324995"
        val parsed = TelegramProxyFeed.parseProxyLinks(link)
        assertEquals(listOf("https://t.me/proxy?server=proxy.example.com&port=443&secret=dd10400103324995"), parsed)
    }

}
