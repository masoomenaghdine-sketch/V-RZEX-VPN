package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private data class CountryEntry(val title: String, val file: String, val directory: Boolean)

class CountryConfigsActivity : ComponentActivity() {
    private var entries by mutableStateOf<List<CountryEntry>>(emptyList())
    private var pageTitle by mutableStateOf("کانفیگ رایگان · کشورها")
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf("یک منبع را انتخاب کنید.")
    private var repo by mutableStateOf("SoliSpirit/v2ray-configs")
    private var path by mutableStateOf("Countries")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF080A14)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(pageTitle, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF72F5E8))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { goBack() }) { Text("بازگشت") }
                            Button(onClick = { openSource("SoliSpirit/v2ray-configs", "Countries", "کشورهای SoliSpirit") }) { Text("SoliSpirit") }
                            Button(onClick = { openSource("ircfspace/location", "", "کشورهای ircfspace") }) { Text("ircfspace") }
                        }
                        Text(message, color = Color(0xFFC7D1EF))
                        if (loading) CircularProgressIndicator()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(entries, key = { it.file + it.directory }) { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D32))) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text((if (item.directory) "🌍 " else "📄 ") + item.title, Modifier.weight(1f), color = Color.White)
                                        Button(onClick = { select(item) }) { Text(if (item.directory) "مشاهده" else "افزودن") }
                                    }
                                }
                            }
                        }
                        Text("کانفیگ‌های عمومی ممکن است ناپایدار یا نامطمئن باشند؛ قبل از استفاده بررسی‌شان کنید.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA9CC))
                    }
                }
            }
        }
    }

    private fun openSource(ownerRepo: String, folder: String, label: String) {
        repo = ownerRepo
        path = folder
        pageTitle = label
        loadFolder()
    }

    private fun select(item: CountryEntry) {
        if (item.directory) {
            path = if (path.isBlank()) item.file else path + "/" + item.file
            pageTitle = item.title
            loadFolder()
        } else {
            val raw = "https://raw.githubusercontent.com/" + repo + "/main/" +
                (if (path.isBlank()) "" else path + "/") + item.file
            val deep = Uri.parse("v2rayng://install-config?url=" + Uri.encode(raw))
            try { startActivity(Intent(Intent.ACTION_VIEW, deep)) }
            catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw))) }
        }
    }

    private fun goBack() {
        if (path.contains('/')) {
            path = path.substringBeforeLast('/')
            pageTitle = path.substringAfterLast('/')
            loadFolder()
        } else finish()
    }

    private fun loadFolder() {
        if (loading) return
        loading = true
        message = "در حال دریافت فهرست…"
        Thread {
            try {
                val encoded = Uri.encode(path, "/")
                val address = "https://api.github.com/repos/" + repo + "/contents/" + encoded + "?ref=main"
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
                connection.setRequestProperty("User-Agent", "VORZEX-VPN")
                val body = try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP error")
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
                val array = JSONArray(body)
                val result = mutableListOf<CountryEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.optString("name")
                    val kind = obj.optString("type")
                    if (kind == "dir") result.add(CountryEntry(name, name, true))
                    else if (kind == "file" && name.endsWith(".txt", true) && obj.optString("download_url").isNotBlank()) {
                        result.add(CountryEntry(name.removeSuffix(".txt"), name, false))
                    }
                }
                val sorted = result.sortedWith(compareByDescending<CountryEntry> { it.directory }.thenBy { it.title.lowercase() })
                runOnUiThread {
                    entries = sorted
                    message = if (sorted.isEmpty()) "موردی پیدا نشد یا ساختار مخزن تغییر کرده است." else "کشور یا فایل موردنظر را انتخاب کنید."
                    loading = false
                }
            } catch (_: Exception) {
                runOnUiThread {
                    entries = emptyList()
                    message = "دریافت فهرست ناموفق بود؛ اینترنت را بررسی و دوباره تلاش کنید."
                    loading = false
                }
            }
        }.start()
    }
}
