package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private data class CountryConfigSource(val name: String, val downloadUrl: String)

class CountriesActivity : ComponentActivity() {
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countries by mutableStateOf<List<CountryConfigSource>>(emptyList())
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CountriesScreen(
                    countries = countries,
                    loading = loading,
                    error = error,
                    onRefresh = ::loadCountries,
                    onImport = { source ->
                        val deepLink = Uri.parse("v2rayng://install-config?url=" + Uri.encode(source.downloadUrl))
                        startActivity(Intent(Intent.ACTION_VIEW, deepLink))
                    },
                    onOpenIrcf = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ircfspace.github.io/location/")))
                    },
                    onBack = { finish() }
                )
            }
        }
        loadCountries()
    }

    private fun loadCountries() {
        if (loading) return
        loading = true
        error = null
        screenScope.launch {
            try {
                countries = withContext(Dispatchers.IO) {
                    val connection = URL(
                        "https://api.github.com/repos/SoliSpirit/v2ray-configs/contents/Countries"
                    ).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 8000
                        connection.readTimeout = 10000
                        connection.setRequestProperty("Accept", "application/vnd.github+json")
                        if (connection.responseCode !in 200..299) {
                            throw IllegalStateException("HTTP ${connection.responseCode}")
                        }
                        val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val items = JSONArray(json)
                        buildList {
                            for (index in 0 until items.length()) {
                                val item = items.optJSONObject(index) ?: continue
                                if (item.optString("type") != "file") continue
                                val name = item.optString("name")
                                if (!name.endsWith(".txt", ignoreCase = true)) continue
                                if (name.contains("XX", ignoreCase = true)) continue
                                val url = item.optString("download_url").ifBlank {
                                    "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/" +
                                        Uri.encode(name)
                                }
                                add(CountryConfigSource(name.removeSuffix(".txt"), url))
                            }
                        }.sortedBy { it.name.lowercase() }
                    } finally {
                        connection.disconnect()
                    }
                }
                if (countries.isEmpty()) error = "فهرست کشورها خالی است؛ دوباره تلاش کن."
            } catch (_: Exception) {
                error = "دریافت کشورها ناموفق بود. اتصال اینترنت را بررسی کن و دوباره بزن."
            } finally {
                loading = false
            }
        }
    }

    override fun onDestroy() {
        screenScope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun CountriesScreen(
    countries: List<CountryConfigSource>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onImport: (CountryConfigSource) -> Unit,
    onOpenIrcf: () -> Unit,
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("کشورها · VØRZEX", style = MaterialTheme.typography.headlineMedium)
            Text(
                "کانفیگ‌های عمومی بر اساس کشور. فهرست از مخزن SoliSpirit دریافت می‌شود؛ IRCF نیز همین مجموعه را برای دسته‌بندی مکانی نمایش می‌دهد.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenIrcf, modifier = Modifier.weight(1f)) {
                    Text("نمایش در IRCF")
                }
                OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("تازه‌سازی")
                }
            }
            if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("در حال دریافت فهرست کشورها…")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(countries, key = { it.downloadUrl }) { country ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(country.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { onImport(country) }) { Text("افزودن") }
                        }
                    }
                }
            }
            Text(
                "هشدار: کانفیگ‌های عمومی ممکن است از کار افتاده، کند یا نامطمئن باشند. قبل از اتصال، منبع و تنظیمات را بررسی کن.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
        }
    }
}
