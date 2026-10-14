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
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.TimeUnit

/**
 * Refreshes the public Telegram MTProto feed in the background.
 * Public proxies are untrusted and cannot be guaranteed to stay online.
 */
class TelegramProxyRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val proxies = TelegramProxyFeed.fetch()
            if (proxies.isNotEmpty()) {
                applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PROXY_LIST, proxies.joinToString("\n"))
                    .putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                    .apply()
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        private const val PREFS = "telegram_proxy_feed"
        private const val KEY_PROXY_LIST = "proxy_list"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val UNIQUE_WORK = "vorzex_telegram_proxy_refresh"
        private const val REFRESH_INTERVAL_HOURS = 2L

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
            val proxies = TelegramProxyFeed.fetch()
            if (proxies.isNotEmpty()) {
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PROXY_LIST, proxies.joinToString("\n"))
                    .putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                    .apply()
            }
            proxies
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
        sources.map { source ->
            kotlinx.coroutines.coroutineScope {
                async(Dispatchers.IO) { readSource(source) }
            }
        }.awaitAll().flatten().distinct().take(150)
    }

    private fun readSource(source: String): List<String> {
        val connection: URLConnection = URL(source).openConnection()
        connection.connectTimeout = 7000
        connection.readTimeout = 7000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 VORZEX-PT")
        val body = connection.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            .replace("&amp;", "&")
            .replace("&#38;", "&")
        return PROXY_LINK.findAll(body)
            .map { it.value.trimEnd('.', ',', ')', ']', ';') }
            .filter { it.contains("server=") && it.contains("port=") && it.contains("secret=") }
            .distinct()
            .toList()
    }
}
