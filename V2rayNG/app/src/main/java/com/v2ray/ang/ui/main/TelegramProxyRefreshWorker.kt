package com.v2ray.ang.ui.main

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class TelegramProxyRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val fetched = TelegramProxyFeed.fetch()
            TelegramProxyFeed.checkReachable(fetched).keys.toList()
        }.fold(
            onSuccess = { active ->
                saveProxies(applicationContext, active)
                if (active.isNotEmpty()) Result.success() else Result.retry()
            },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        private const val PREFS = "telegram_proxy_feed"
        private const val KEY_PROXY_LIST = "proxy_list"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val UNIQUE_WORK = "vorzex_telegram_proxy_refresh"
        private const val REFRESH_INTERVAL_HOURS = 2L

        private fun saveProxies(context: Context, proxies: List<String>) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROXY_LIST, proxies.joinToString("\n"))
                .putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                .apply()
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<TelegramProxyRefreshWorker>(
                REFRESH_INTERVAL_HOURS, TimeUnit.HOURS
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cachedProxies(context: Context): List<String> =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PROXY_LIST, "")
                .orEmpty()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filter { TelegramProxyFeed.isValidProxyLink(it) }
                .distinct()
                .toList()

        fun lastRefreshMillis(context: Context): Long =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_REFRESH, 0L)

        suspend fun refreshNow(context: Context): Map<String, Long> = withContext(Dispatchers.IO) {
            val fetched = TelegramProxyFeed.fetch()
            val active = TelegramProxyFeed.checkReachable(fetched)
            saveProxies(context.applicationContext, active.keys.toList())
            active
        }
    }
}

internal object TelegramProxyFeed {
    private const val PROXY_FEED_URL =
        "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt"
    private const val PROXY_FEED_CDN_URL =
        "https://cdn.jsdelivr.net/gh/SoliSpirit/mtproto@master/all_proxies.txt"
    private val PROXY_LINK = Regex(
        """(?:tg://proxy\?[^"'<>\s]+|https?://t\.me/proxy\?[^"'<>\s]+)""",
        RegexOption.IGNORE_CASE
    )

    suspend fun fetch(): List<String> = withContext(Dispatchers.IO) {
        suspend fun fetchSources(sources: List<String>): List<String> = coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) {
                    runCatching { readSource(source) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinct().take(150)
        }

        val repositoryFeeds = fetchSources(
            listOf(
                PROXY_FEED_URL,
                PROXY_FEED_CDN_URL,
                "https://raw.githubusercontent.com/Grim1313/mtproto-for-telegram/master/all_proxies.txt",
                "https://cdn.jsdelivr.net/gh/Grim1313/mtproto-for-telegram@master/all_proxies.txt"
            )
        )
        if (repositoryFeeds.isNotEmpty()) return@withContext repositoryFeeds

        fetchSources(listOf("https://t.me/s/PeopleProxy", "https://t.me/s/iRoProxy"))
    }

    /**
     * Checks TCP reachability from the device's current network and orders reachable proxies
     * by measured connection time. A successful TCP handshake is not a guarantee that Telegram
     * will accept the MTProto secret, so the UI reports reachability rather than protocol success.
     */
    suspend fun checkReachable(proxies: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Long>>()
        proxies.distinct().take(100).chunked(20).forEach { batch ->
            val checked = coroutineScope {
                batch.map { proxy ->
                    async(Dispatchers.IO) {
                        runCatching { probeTcp(proxy)?.let { proxy to it } }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
            results += checked
        }
        results.sortedBy { it.second }.toMap(LinkedHashMap())
    }

    private fun probeTcp(proxy: String): Long? {
        val uri = URI(proxy)
        val params = (uri.rawQuery ?: return null).split("&").mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else {
                val key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(
                    part.substring(separator + 1).replace("+", "%2B"),
                    StandardCharsets.UTF_8.name()
                )
                key to value
            }
        }.toMap()
        val server = params["server"].orEmpty().removeSuffix(".")
        val port = params["port"]?.toIntOrNull() ?: return null
        if (server.isBlank() || port !in 1..65535) return null

        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(server, port), 1_800)
            }
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseProxyLinks(body: String): List<String> {
        val normalized = body
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return PROXY_LINK.findAll(normalized)
            .map { it.value.trimEnd('.', ',', ')', ']', ';') }
            .map { link ->
                Regex("""(?i)([?&]server=[^&]+)\.(?=&)""").replace(link, "$1")
            }
            .filter(::isValidProxyLink)
            .distinct()
            .toList()
    }

    internal fun isValidProxyLink(value: String): Boolean {
        return try {
            val uri = URI(value)
            val validSchemeAndHost = when (uri.scheme?.lowercase()) {
                "tg" -> uri.host.equals("proxy", ignoreCase = true)
                "https", "http" -> uri.host.equals("t.me", ignoreCase = true) &&
                    uri.path.equals("/proxy", ignoreCase = true)
                else -> false
            }
            if (!validSchemeAndHost) return false
            val query = uri.rawQuery ?: return false
            val params = query.split("&").mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else {
                    URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8.name()) to
                        URLDecoder.decode(
                            part.substring(separator + 1).replace("+", "%2B"),
                            StandardCharsets.UTF_8.name()
                        )
                }
            }.toMap()
            val server = params["server"].orEmpty().removeSuffix(".")
            val port = params["port"]?.toIntOrNull() ?: return false
            val secret = params["secret"].orEmpty()
            server.isNotBlank() && port in 1..65535 &&
                secret.length in 8..256 && secret.matches(Regex("[A-Za-z0-9_+=/-]+"))
        } catch (_: Exception) {
            false
        }
    }

    private fun readSource(source: String): List<String> {
        val connection = URL(source).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 VORZEX")
        connection.setRequestProperty("Accept", "text/plain,text/html,*/*")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return emptyList()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val body = StringBuilder()
                val buffer = CharArray(8192)
                var total = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_SOURCE_CHARS) break
                    body.append(buffer, 0, count)
                }
                parseProxyLinks(body.toString())
            }
        } finally {
            connection.disconnect()
        }
    }

    private const val MAX_SOURCE_CHARS = 2_000_000
}
