package com.v2ray.ang.core

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Conservative preflight for public config feeds. It removes endpoints that fail a TCP connect
 * test before import. TCP reachability is not proof that credentials, TLS, Reality, or the full
 * tunnel works; AI_CON still performs end-to-end tunnel verification after import.
 */
internal object PublicConfigHealthFilter {
    data class Result(
        val text: String,
        val candidates: Int,
        val reachable: Int,
        val unsupported: Int
    )

    suspend fun filter(raw: String): Result = coroutineScope {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()
        val parsed = lines.mapNotNull { line -> endpoint(line)?.let { line to it } }
        val checked = parsed.map { (line, target) ->
            async(Dispatchers.IO) { if (isTcpReachable(target.first, target.second)) line else null }
        }.awaitAll().filterNotNull()
        Result(
            text = checked.joinToString("\n"),
            candidates = parsed.size,
            reachable = checked.size,
            unsupported = (lines.size - parsed.size).coerceAtLeast(0)
        )
    }

    private fun endpoint(line: String): Pair<String, Int>? {
        return try {
            val scheme = line.substringBefore("://", "").lowercase()
            when (scheme) {
                "vless", "trojan", "ss", "socks", "http" -> {
                    val uri = URI(line)
                    if (scheme == "ss" && uri.host.isNullOrBlank()) {
                        parseShadowsocks(line)
                    } else {
                        val host = uri.host?.trim('[', ']')?.takeIf { it.isNotBlank() } ?: return null
                        val port = uri.port.takeIf { it in 1..65535 } ?: return null
                        host to port
                    }
                }
                "vmess" -> {
                    val payload = line.substringAfter("://", "").substringBefore('#')
                    val decoded = runCatching {
                        String(Base64.decode(payload, Base64.DEFAULT or Base64.NO_WRAP), Charsets.UTF_8)
                    }.getOrNull() ?: return null
                    val json = JSONObject(decoded)
                    val host = json.optString("add").takeIf { it.isNotBlank() } ?: return null
                    val port = json.optString("port").toIntOrNull() ?: json.optInt("port")
                    if (port !in 1..65535) return null
                    host to port
                }
                else -> null // UDP-only and unknown formats are not falsely marked as TCP-healthy.
            }
        } catch (_: Exception) {
            parseShadowsocks(line)
        }
    }

    private fun parseShadowsocks(line: String): Pair<String, Int>? {
        return runCatching {
            val payload = line.substringAfter("://").substringBefore('#').substringBefore('?')
            val decoded = String(Base64.decode(payload, Base64.DEFAULT or Base64.NO_WRAP), Charsets.UTF_8)
            val authority = decoded.substringAfterLast('@')
            val hostPort = URI("tcp://$authority")
            val host = hostPort.host ?: return null
            val port = hostPort.port
            if (port !in 1..65535) null else host to port
        }.getOrNull()
    }

    private fun isTcpReachable(host: String, port: Int): Boolean {
        if (host.isBlank() || host.length > 253 || port !in 1..65535) return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private const val CONNECT_TIMEOUT_MS = 1_300
    private const val MAX_CANDIDATES = 160
}
