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
import java.net.URL
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.net.URLConnection
import java.util.concurrent.TimeUnit

class TelegramProxyRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { TelegramProxyFeed.fetch() }.fold(
            onSuccess = { proxies ->
                if (proxies.isNotEmpty()) saveProxies(applicationContext, proxies)
                if (proxies.isNotEmpty()) Result.success() else Result.retry()
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
                .distinct()
                .toList()

        fun lastRefreshMillis(context: Context): Long =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_REFRESH, 0L)

        suspend fun refreshNow(context: Context): List<String> = withContext(Dispatchers.IO) {
            TelegramProxyFeed.fetch().also { proxies ->
                if (proxies.isNotEmpty()) saveProxies(context.applicationContext, proxies)
            }
        }
    }
}

internal object TelegramProxyFeed {
    private const val PROXY_FEED_URL =
        "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt"
    private val PROXY_LINK = Regex(
        """(?:tg://proxy\?[^"'<>\s]+|https?://t\.me/proxy\?[^"'<>\s]+)""",
        RegexOption.IGNORE_CASE
    )

    suspend fun fetch(): List<String> = withContext(Dispatchers.IO) {
        val sources = listOf(
            PROXY_FEED_URL,
            "https://t.me/s/PeopleProxy",
            "https://t.me/s/iRoProxy"
        )
        coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) {
                    runCatching { readSource(source) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinct().take(150)
        }
    }

    internal fun parseProxyLinks(body: String): List<String> {
        val normalized = body.replace("&amp;", "&").replace("&#38;", "&")
        return PROXY_LINK.findAll(normalized)
            .map { it.value.trimEnd('.', ',', ')', ']', ';') }
            .filter(::isValidProxyLink)
            .distinct()
            .toList()
    }

    private fun isValidProxyLink(value: String): Boolean {
        return try {
            val query = URI(value).rawQuery ?: return false
            val params = query.split("&").mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else {
                    URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8.name()) to
                        URLDecoder.decode(part.substring(separator + 1).replace("+", "%2B"), StandardCharsets.UTF_8.name())
                }
            }.toMap()
            val server = params["server"].orEmpty()
            val port = params["port"]?.toIntOrNull() ?: return false
            val secret = params["secret"].orEmpty()
            server.isNotBlank() && port in 1..65535 &&
                secret.length in 8..256 && secret.matches(Regex("[A-Za-z0-9_+=/-]+"))
        } catch (_: Exception) {
            false
        }
    }

    private fun readSource(source: String): List<String> {
        val connection: URLConnection = URL(source).openConnection()
        connection.connectTimeout = 7000
        connection.readTimeout = 7000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 VORZEX-PT")
        val body = connection.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseProxyLinks(body)
    }
}
