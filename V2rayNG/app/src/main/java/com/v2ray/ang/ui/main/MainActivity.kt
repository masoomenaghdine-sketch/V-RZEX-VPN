package com.v2ray.ang.ui.main

import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.api.vorzex.VorzexInstallationStore
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.backup.BackupActivity
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.routing.RoutingSettingActivity
import com.v2ray.ang.ui.server.ProfileEditorResult
import com.v2ray.ang.ui.server.ServerAetherActivity
import com.v2ray.ang.ui.server.ServerCustomConfigActivity
import com.v2ray.ang.ui.server.ServerGroupActivity
import com.v2ray.ang.ui.server.ServerHttpActivity
import com.v2ray.ang.ui.server.ServerHysteria2Activity
import com.v2ray.ang.ui.server.ServerProxyChainActivity
import com.v2ray.ang.ui.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.server.ServerSocksActivity
import com.v2ray.ang.ui.server.ServerTrojanActivity
import com.v2ray.ang.ui.server.ServerVlessActivity
import com.v2ray.ang.ui.server.ServerVmessActivity
import com.v2ray.ang.ui.server.ServerWireguardActivity
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.ui.subscription.SubSettingActivity
import com.v2ray.ang.ui.userasset.UserAssetActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : HelperBaseComponentActivity() {
    private var pendingAetherQuickConnect = false
    private var pendingVZVExport: String? = null
    private val aiAutoConnectInProgress = AtomicBoolean(false)
    @Volatile private var lastAiFreeConfigFetchAtMs = 0L
    @Volatile private var preferAiNetFreeCandidates = false
    @Volatile private var suppressAiFailoverOnce = false
    @Volatile private var lastAiFailoverAtMs = 0L
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    private val vzvDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.vorzex.config+json")) { uri ->
            if (uri == null) {
                pendingVZVExport = null
                return@registerForActivityResult
            }
            val payload = pendingVZVExport
            pendingVZVExport = null
            try {
                if (payload.isNullOrBlank()) throw IllegalStateException("No VZV export data")
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(payload) }
                    ?: throw IllegalStateException("Cannot write the selected file")
                toastSuccess(R.string.vzv_export_saved)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to write VZV export", e)
                toastError(R.string.toast_failure)
            }
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                pendingAetherQuickConnect = false
                return@registerForActivityResult
            }
            val data = result.data ?: run {
                pendingAetherQuickConnect = false
                return@registerForActivityResult
            }
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION) ?: run {
                pendingAetherQuickConnect = false
                return@registerForActivityResult
            }
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) {
                pendingAetherQuickConnect = false
                return@registerForActivityResult
            }
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            val savedGuid = data.getStringExtra(ProfileEditorResult.EXTRA_GUID)
            val quickConnect = pendingAetherQuickConnect && action == ProfileEditorResult.ACTION_SAVED
            pendingAetherQuickConnect = false
            if (quickConnect && !savedGuid.isNullOrBlank()) {
                mainViewModel.updateSelectedGuid(savedGuid)
                mainViewModel.onAction(MainAction.RefreshGroups)
                LauncherManager.restartServiceOrStart(this, ::requestServiceStart)
                return@registerForActivityResult
            }
            val selectedProfileSaved = action == ProfileEditorResult.ACTION_SAVED &&
                    savedGuid == mainViewModel.uiState.value.selectedGuid
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService || selectedProfileSaved) LauncherManager.restartService(this)
        }

    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()
            mainViewModel.refreshUiSettings()
            if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService) LauncherManager.restartService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppThemeDayNight_NoActionBar)
        super.onCreate(savedInstanceState)
        // Clear API credentials/consent left by older builds; all VPN controls now work locally.
        VorzexInstallationStore(this)
        mainViewModel.onAction(MainAction.Initialize)
        handleExternalConfigIntent(intent)
        handleAiConLaunchIntent(intent)

        // If an AI_CON tunnel unexpectedly stops, attempt a bounded, verified fallback.
        // Manual disconnects are explicitly suppressed; repeated failures are rate-limited.
        lifecycleScope.launch {
            var hadRunningSession = false
            mainViewModel.uiState.collect { state ->
                if (state.isRunning) {
                    hadRunningSession = true
                    return@collect
                }
                if (!hadRunningSession) return@collect
                hadRunningSession = false
                if (suppressAiFailoverOnce) {
                    suppressAiFailoverOnce = false
                    return@collect
                }
                val selectedProfile = state.selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
                if (selectedProfile?.remarks.equals("AI_CON", ignoreCase = true) &&
                    !aiAutoConnectInProgress.get()
                ) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAiFailoverAtMs >= AI_FAILOVER_COOLDOWN_MS) {
                        lastAiFailoverAtMs = now
                        LogUtil.w(AppConfig.TAG, "AI_CON session stopped unexpectedly; trying bounded failover")
                        startAiConfigQuickSetup()
                    }
                }
            }
        }

        // A tunnel can stay "running" while upstream traffic has silently stopped.
        // Check AI_CON end-to-end at a conservative interval and require two failures before failover.
        lifecycleScope.launch {
            while (true) {
                delay(AI_HEALTH_CHECK_INTERVAL_MS)
                val state = mainViewModel.uiState.value
                val profile = state.selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
                if (!state.isRunning ||
                    state.status !is MainStatus.Connected ||
                    !profile?.remarks.equals("AI_CON", ignoreCase = true) ||
                    aiAutoConnectInProgress.get()
                ) continue

                if (!com.v2ray.ang.core.AiConnectionVerifier.verifyTunnelTraffic()) {
                    delay(AI_HEALTH_RECHECK_DELAY_MS)
                    val latest = mainViewModel.uiState.value
                    val latestProfile = latest.selectedGuid?.let { MmkvManager.decodeServerConfig(it) }
                    if (latest.isRunning &&
                        latest.status is MainStatus.Connected &&
                        latestProfile?.remarks.equals("AI_CON", ignoreCase = true) &&
                        !aiAutoConnectInProgress.get() &&
                        !com.v2ray.ang.core.AiConnectionVerifier.verifyTunnelTraffic()
                    ) {
                        LogUtil.w(AppConfig.TAG, "AI_CON traffic check failed twice; stopping unhealthy tunnel for failover")
                        LauncherManager.stopService(this@MainActivity)
                    }
                }
            }
        }

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalConfigIntent(intent)
        handleAiConLaunchIntent(intent)
    }

    private fun handleAiConLaunchIntent(incoming: Intent?) {
        if (incoming?.getBooleanExtra(EXTRA_OPEN_AI_CON, false) != true) return
        incoming.removeExtra(EXTRA_OPEN_AI_CON)
        lifecycleScope.launch {
            // Let the main view model finish restoring local profiles before ranking them.
            delay(500)
            startAiConfigQuickSetup()
        }
    }

    private fun handleExternalConfigIntent(incoming: Intent?) {
        if (incoming == null) return
        val uri: Uri? = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            Intent.ACTION_SEND -> {
                if (incoming.type != "text/plain") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        incoming.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                } else null
            }
            else -> null
        }
        if (uri != null && uri.scheme.equals("v2rayng", ignoreCase = true) && uri.host == "install-config") {
            val remoteUrl = uri.getQueryParameter("url")
            if (remoteUrl.isNullOrBlank()) {
                toastError(R.string.toast_failure)
                return
            }
            lifecycleScope.launch {
                var healthSummary: String? = null
                try {
                    val configText = withContext(Dispatchers.IO) {
                        val parsedUrl = URL(remoteUrl)
                        require(parsedUrl.protocol.equals("https", ignoreCase = true)) {
                            "Only HTTPS configuration sources are accepted"
                        }
                        require(parsedUrl.host.equals("raw.githubusercontent.com", ignoreCase = true)) {
                            "Configuration source host is not allowed"
                        }
                        val connection = parsedUrl.openConnection() as HttpURLConnection
                        val rawText = try {
                            connection.connectTimeout = 8_000
                            connection.readTimeout = 12_000
                            connection.instanceFollowRedirects = false
                            require(connection.responseCode in 200..299) {
                                "Configuration source returned HTTP " + connection.responseCode
                            }
                            require(connection.contentLength <= 2 * 1024 * 1024) {
                                "Configuration source is too large"
                            }
                            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                .also { require(it.isNotBlank() && it.length <= 2 * 1024 * 1024) { "Configuration source is empty or too large" } }
                        } finally {
                            connection.disconnect()
                        }
                        if (uri.getQueryParameter("check") != "1") {
                            rawText
                        } else {
                            val checked = com.v2ray.ang.core.PublicConfigHealthFilter.filter(rawText)
                            require(checked.text.isNotBlank()) {
                                "No TCP-reachable public config endpoints found (" + checked.candidates + " candidates)"
                            }
                            healthSummary = "بررسی اتصال: " + checked.reachable + " کانفیگ پاسخ‌گو از " +
                                checked.candidates + " مقصد TCP؛ سلامت تونل هنوز باید آزمایش شود."
                            checked.text
                        }
                    }
                    mainViewModel.onAction(MainAction.ImportBatchConfig(configText))
                    if (healthSummary != null) toast(healthSummary!!) else toastSuccess(R.string.toast_success)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import remote public configuration", e)
                    toastError(R.string.toast_failure)
                }
            }
        } else if (uri != null) {
            lifecycleScope.launch {
                delay(350)
                importExchangeUri(uri)
            }
        } else if (incoming.action == Intent.ACTION_SEND && incoming.type == "text/plain") {
            val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (sharedText.contains("VORZEX_CONFIG_EXCHANGE")) {
                lifecycleScope.launch {
                    delay(350)
                    importExchangeText(sharedText, "shared.VXV")
                }
            }
        }
    }

    private fun importExchangeUri(uri: Uri) {
        try {
            val displayName = runCatching {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull().orEmpty()
            val content = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalArgumentException("The selected file could not be read")
            importExchangeText(content, displayName.ifBlank { uri.lastPathSegment.orEmpty() })
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import shared VØRZEX configuration", e)
            toastError(R.string.toast_failure)
        }
    }

    private fun importExchangeText(content: String, displayName: String) {
        when {
            com.v2ray.ang.util.VorzexConfigExchange.isRecognizedExchangeExtension(displayName) ||
                content.contains("VORZEX_CONFIG_EXCHANGE") -> {
                when (val decoded = com.v2ray.ang.util.VorzexConfigExchange.decode(content)) {
                    is com.v2ray.ang.util.VorzexConfigExchange.DecodeResult.Success -> {
                        mainViewModel.onAction(MainAction.ImportVZVProfiles(decoded.profiles.map { it.content }))
                        toastSuccess(R.string.toast_success)
                    }
                    is com.v2ray.ang.util.VorzexConfigExchange.DecodeResult.Failure -> {
                        LogUtil.w(AppConfig.TAG, "Rejected VØRZEX exchange file: ${decoded.reason}")
                        toastError(R.string.toast_failure)
                    }
                }
            }
            com.v2ray.ang.util.VorzexConfigExchange.isNpvExtension(displayName) -> {
                toastError(R.string.npv_import_not_supported)
            }
            else -> mainViewModel.onAction(MainAction.ImportBatchConfig(content))
        }
    }

    override fun onStart() {
        super.onStart()
        // The service can have gone away while the screen was not shown, without a word if its process died.
        mainViewModel.onAction(MainAction.RefreshServiceState)
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        MainScreen(
            mainViewModel = mainViewModel,
            onAction = { action ->
                when (action) {
                    MainAction.ToggleService -> handleFabAction()
                    MainAction.TestCurrentServer -> handleLayoutTestClick()
                    MainAction.ImportQRcode -> importQRcode()
                    MainAction.ImportClipboard -> importClipboard()
                    MainAction.ImportConfigLocal -> importConfigLocal()
                    MainAction.ExportVZV -> exportAllVZV()
                    MainAction.ShareVZV -> shareAllVZV()
                    MainAction.GenerateAiConfig -> startAiConfigQuickSetup()
                    MainAction.OpenDnsLab -> settingsActivityLauncher.launch(
                        Intent(this, NetworkLabActivity::class.java)
                            .putExtra(NetworkLabActivity.EXTRA_TAB, NetworkLabActivity.TAB_DNS)
                    )
                    MainAction.OpenGamingMode -> settingsActivityLauncher.launch(
                        Intent(this, NetworkLabActivity::class.java)
                            .putExtra(NetworkLabActivity.EXTRA_TAB, NetworkLabActivity.TAB_GAMING)
                    )
                    is MainAction.ImportManually -> importManually(action.type)
                    MainAction.RestartService -> LauncherManager.restartServiceOrStart(this, ::requestServiceStart)
                    MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
                    is MainAction.SelectServer -> setSelectServer(action.guid)
                    is MainAction.EditServer -> editServer(action.guid, action.profile)
                    is MainAction.ShareClipboard -> shareToClipboard(action.guid)
                    is MainAction.ShareFullContent -> shareFullContentAsync(action.guid)
                    else -> mainViewModel.onAction(action)
                }
            },
            onNavigate = { route -> navigateTo(route) },
            onPsiphonQuickSetup = {
                pendingAetherQuickConnect = true
                profileEditorLauncher.launch(
                    Intent(this, ServerAetherActivity::class.java)
                        .putExtra(ServerAetherActivity.EXTRA_PSIPHON_QUICK_SETUP, true)
                )
            },
        )
    }

    private fun startAiConfigQuickSetup() {
        if (!aiAutoConnectInProgress.compareAndSet(false, true)) return
        lifecycleScope.launch {
            var retryAfterFreeImport = false
            try {
                // A TCP handshake only narrows the list; success requires real HTTPS traffic through the tunnel.
                val snapshot = com.v2ray.ang.core.AiNetworkAdvisor.assess(this@MainActivity)
                val rankedCandidates = com.v2ray.ang.core.AiConfigGenerator
                    .rankedReachableLocalXrayProfiles(snapshot)
                val candidates = if (preferAiNetFreeCandidates) {
                    preferAiNetFreeCandidates = false
                    rankedCandidates.filter {
                        it.profile.remarks.orEmpty().startsWith("AI_NET_FREE_", ignoreCase = true)
                    }.take(MAX_AI_AUTO_CONNECT_ATTEMPTS)
                } else {
                    rankedCandidates.take(MAX_AI_AUTO_CONNECT_ATTEMPTS)
                }

                if (candidates.isNotEmpty()) {
                    var localAiGuid = MmkvManager.decodeAllServerList().firstOrNull { guid ->
                        val profile = MmkvManager.decodeServerConfig(guid)
                        profile?.remarks.equals("AI_CON", ignoreCase = true) &&
                            profile?.subscriptionId == AppConfig.DEFAULT_SUBSCRIPTION_ID
                    }

                    for ((index, candidate) in candidates.withIndex()) {
                        val generatedResult = runCatching {
                            val aiProfile = candidate.profile.copy(
                                remarks = "AI_CON",
                                subscriptionId = AppConfig.DEFAULT_SUBSCRIPTION_ID,
                                addedTime = System.currentTimeMillis(),
                            )
                            val existingGuid = localAiGuid
                            if (existingGuid != null) {
                                MmkvManager.encodeServerConfig(existingGuid, aiProfile)
                                existingGuid
                            } else {
                                MmkvManager.encodeServerConfig("", aiProfile).also { localAiGuid = it }
                            }
                        }
                        if (generatedResult.isFailure) {
                            val saveError = generatedResult.exceptionOrNull()
                            if (saveError != null) {
                                LogUtil.e(AppConfig.TAG, "AI_CON could not save candidate ${candidate.guid}", saveError)
                            } else {
                                LogUtil.w(AppConfig.TAG, "AI_CON could not save candidate ${candidate.guid}; no exception details available")
                            }
                            continue
                        }
                        val generatedGuid = generatedResult.getOrThrow()
                        mainViewModel.updateSelectedGuid(generatedGuid)
                        mainViewModel.onAction(MainAction.RefreshGroups)

                        if (mainViewModel.uiState.value.isRunning) {
                            LauncherManager.stopService(this@MainActivity)
                            withTimeoutOrNull(SERVICE_STOP_WAIT_MS) {
                                mainViewModel.uiState.first { !it.isRunning }
                            }
                        }
                        suppressAiFailoverOnce = false
                        requestServiceStart()

                        var sawRunning = false
                        val connectionState = withTimeoutOrNull(AI_CONNECTION_WAIT_MS) {
                            mainViewModel.uiState.first { state ->
                                if (state.isRunning) sawRunning = true
                                (state.isRunning && state.status is MainStatus.Connected) ||
                                    (sawRunning && !state.isRunning)
                            }
                        }
                        val connected = connectionState?.let {
                            it.isRunning && it.status is MainStatus.Connected
                        } == true
                        val verified = connected &&
                            com.v2ray.ang.core.AiConnectionVerifier.verifyTunnelTraffic()

                        if (verified) {
                            toastSuccess(R.string.ai_config_xray_selected)
                            return@launch
                        }

                        LogUtil.w(
                            AppConfig.TAG,
                            "AI_CON candidate ${candidate.guid} did not pass end-to-end tunnel verification"
                        )
                        if (mainViewModel.uiState.value.isRunning) {
                            LauncherManager.stopService(this@MainActivity)
                            withTimeoutOrNull(SERVICE_STOP_WAIT_MS) {
                                mainViewModel.uiState.first { !it.isRunning }
                            }
                        }
                        if (index < candidates.lastIndex) delay(AI_FAILOVER_RETRY_DELAY_MS)
                    }
                }

                // If stored profiles fail end-to-end checks, automatically fetch and preflight a
                // public feed once, import only TCP-responsive endpoints, then rerun AI_CON ranking.
                val now = SystemClock.elapsedRealtime()
                if (lastAiFreeConfigFetchAtMs == 0L || now - lastAiFreeConfigFetchAtMs >= AI_FREE_CONFIG_COOLDOWN_MS) {
                    lastAiFreeConfigFetchAtMs = now
                    retryAfterFreeImport = fetchAndImportReachableFreeConfigs()
                }

                // Keep the existing independent Aether quick-setup fallback if no public candidate
                // could be imported. A failed source fetch never blocks the app's existing modes.
                if (!retryAfterFreeImport) {
                    pendingAetherQuickConnect = true
                    profileEditorLauncher.launch(
                        Intent(this@MainActivity, ServerAetherActivity::class.java)
                            .putExtra(ServerAetherActivity.EXTRA_AI_QUICK_SETUP, true)
                    )
                }
            } finally {
                aiAutoConnectInProgress.set(false)
            }
            if (retryAfterFreeImport) {
                delay(500)
                startAiConfigQuickSetup()
            }
        }
    }

    private suspend fun fetchAndImportReachableFreeConfigs(): Boolean {
        return try {
            val result = withContext(Dispatchers.IO) {
                val source = URL(AI_FREE_CONFIG_SOURCE)
                require(source.protocol.equals("https", ignoreCase = true) &&
                    source.host.equals("raw.githubusercontent.com", ignoreCase = true)) {
                    "Untrusted public config source"
                }
                val connection = source.openConnection() as HttpURLConnection
                val raw = try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 12_000
                    connection.instanceFollowRedirects = false
                    require(connection.responseCode in 200..299) {
                        "Public config feed returned HTTP " + connection.responseCode
                    }
                    require(connection.contentLength <= 2 * 1024 * 1024) {
                        "Public config feed is too large"
                    }
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        .also { require(it.isNotBlank() && it.length <= 2 * 1024 * 1024) { "Public config feed is empty or too large" } }
                } finally {
                    connection.disconnect()
                }
                val filtered = com.v2ray.ang.core.PublicConfigHealthFilter.filter(raw)
                require(filtered.text.isNotBlank()) { "No TCP-responsive public configs found" }
                filtered
            }
            val existingGuids = MmkvManager.decodeAllServerList().toSet()
            mainViewModel.onAction(MainAction.ImportBatchConfig(result.text))
            delay(1_000)
            val importedGuids = MmkvManager.decodeAllServerList().filterNot { it in existingGuids }
            importedGuids.forEach { guid ->
                val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
                val sourceName = profile.remarks.orEmpty().removePrefix("AI_NET_FREE_")
                MmkvManager.encodeServerConfig(guid, profile.copy(remarks = "AI_NET_FREE_" + sourceName))
            }
            mainViewModel.onAction(MainAction.RefreshGroups)
            preferAiNetFreeCandidates = importedGuids.isNotEmpty()
            toast("AI_CON: " + result.reachable + " کانفیگ پاسخ‌گوی TCP پیدا شد؛ بررسی تونل در حال انجام است.")
            importedGuids.isNotEmpty()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "AI_CON public config fallback unavailable", e)
            false
        }
    }

    companion object {
        const val EXTRA_OPEN_AI_CON = "com.v2ray.ang.extra.OPEN_AI_CON"
        const val MAX_AI_AUTO_CONNECT_ATTEMPTS = 5
        const val AI_CONNECTION_WAIT_MS = 18_000L
        const val SERVICE_STOP_WAIT_MS = 4_000L
        const val AI_FAILOVER_RETRY_DELAY_MS = 500L
        const val AI_FREE_CONFIG_COOLDOWN_MS = 600_000L
        const val AI_FREE_CONFIG_SOURCE = "https://raw.githubusercontent.com/MohammadBahemmat/V2ray-Collector/main/all_servers.txt"
        const val AI_FAILOVER_COOLDOWN_MS = 30_000L
        const val AI_HEALTH_CHECK_INTERVAL_MS = 30_000L
        const val AI_HEALTH_RECHECK_DELAY_MS = 3_000L
    }

    private fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

    private fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun navigateTo(destination: MainDestination) {
        if (destination == MainDestination.TelegramProxy) {
            startActivity(Intent(this, TelegramProxyActivity::class.java))
            return
        }
        if (destination == MainDestination.Notifications) {
            startActivity(Intent(this, NotificationsActivity::class.java))
            return
        }
        if (destination == MainDestination.Profile) {
            startActivity(Intent(this, ProfileActivity::class.java))
            return
        }
        if (destination == MainDestination.Device) {
            startActivity(Intent(this, DeviceActivity::class.java))
            return
        }
        if (destination == MainDestination.Cnr) {
            startActivity(Intent(this, CnrActivity::class.java))
            return
        }
        if (destination == MainDestination.Countries) {
            startActivity(Intent(this, CountriesActivity::class.java))
            return
        }
        if (destination == MainDestination.AiCon) {
            mainViewModel.onAction(MainAction.GenerateAiConfig)
            return
        }
        if (destination == MainDestination.AiShield) {
            startActivity(Intent(this, AiDpiShieldActivity::class.java))
            return
        }
        if (destination == MainDestination.TorNetwork) {
            // Open a Tor-only starter profile in the existing Aether editor. The existing
            // Tor/lyrebird engine is reused; existing VPN profiles and settings remain intact.
            // Use the result launcher so saving the starter profile selects it and starts the VPN.
            pendingAetherQuickConnect = true
            profileEditorLauncher.launch(
                Intent(this, ServerAetherActivity::class.java)
                    .putExtra(ServerAetherActivity.EXTRA_TOR_QUICK_SETUP, true)
            )
            return
        }
        val intent = when (destination) {
            MainDestination.Subscriptions -> Intent(this, SubSettingActivity::class.java)
            MainDestination.PerAppProxy -> Intent(this, PerAppProxyActivity::class.java)
            MainDestination.Routing -> Intent(this, RoutingSettingActivity::class.java)
            MainDestination.UserAssets -> Intent(this, UserAssetActivity::class.java)
            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)
            MainDestination.TorNetwork -> error("Tor Network is handled before building the settings intent")
            MainDestination.TelegramProxy -> error("Telegram Proxy is handled before building the settings intent")
            MainDestination.Cnr -> error("CNR is handled before building the settings intent")
            MainDestination.Countries -> error("Countries is handled before building the settings intent")
            MainDestination.AiCon -> error("AI_CON is handled before building the settings intent")
            MainDestination.AiShield -> error("AI Shield is handled before building the settings intent")
            MainDestination.Device -> error("Device is handled before building the settings intent")
            MainDestination.Profile -> error("Profile is handled before building the settings intent")
            MainDestination.Notifications -> error("Notifications is handled before building the settings intent")
            MainDestination.Logcat -> Intent(this, LogcatActivity::class.java)
            MainDestination.CheckUpdate -> Intent(this, CheckUpdateActivity::class.java)
            MainDestination.BackupRestore -> Intent(this, BackupActivity::class.java)
            MainDestination.About -> Intent(this, AboutActivity::class.java)
            MainDestination.Promotion -> {
                Utils.openUri(
                    this,
                    "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
                )
                return
            }
        }
        settingsActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        if (mainViewModel.uiState.value.isRunning) {
            suppressAiFailoverOnce = true
            LauncherManager.stopService(this)
            // A service whose process died takes no stop and answers nothing; asking for its state right
            // after lets that silence show the screen as stopped instead of leaving it connected.
            mainViewModel.onAction(MainAction.RefreshServiceState)
        } else {
            suppressAiFailoverOnce = false
            requestServiceStart()
        }
    }

    private fun requestServiceStart() {
        // VPN operation stays local; no device registration, telemetry, or API authorization is required.
        requestVpnPermissionAndStart()
    }

    private fun requestVpnPermissionAndStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.uiState.value.isRunning) {
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.WIREGUARD.value -> Intent(this, ServerWireguardActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            EConfigType.AETHER.value -> Intent(this, ServerAetherActivity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                mainViewModel.onAction(MainAction.ImportBatchConfig(scanResult))
            }
        }
    }

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            mainViewModel.onAction(MainAction.ImportBatchConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }


    /**
     * Exports each stored profile as a portable Xray JSON payload inside the versioned VZV envelope.
     * The runtime builder is used so the exported JSON is a real configuration, not just a renamed file.
     */
    private fun buildVZVExport(): String {
        val payloads = MmkvManager.decodeAllServerList().mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val result = com.v2ray.ang.core.CoreConfigManager.getV2rayConfig(this, guid)
            if (!result.status || result.content.isBlank()) return@mapNotNull null
            com.v2ray.ang.util.VorzexConfigExchange.ProfilePayload(
                name = profile.remarks.ifBlank { "VØRZEX profile" },
                type = "xray-json",
                content = result.content
            )
        }
        if (payloads.isEmpty()) throw IllegalStateException(getString(R.string.vzv_export_no_profiles))
        return com.v2ray.ang.util.VorzexConfigExchange.encode(payloads)
    }

    private fun exportAllVZV() {
        try {
            pendingVZVExport = buildVZVExport()
            vzvDocumentLauncher.launch("Vorzex-configs.VXV")
        } catch (e: Exception) {
            pendingVZVExport = null
            LogUtil.e(AppConfig.TAG, "Failed to prepare VZV export", e)
            toastError(R.string.vzv_export_no_profiles)
        }
    }

    private fun shareAllVZV() {
        try {
            val payload = buildVZVExport()
            val shareDir = File(cacheDir, "shared-configs").apply { mkdirs() }
            val shareFile = File(shareDir, "Vorzex-configs.VXV")
            shareFile.writeText(payload, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "${packageName}.cache", shareFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = com.v2ray.ang.util.VorzexConfigExchange.MIME_TYPE
                putExtra(Intent.EXTRA_SUBJECT, "VØRZEX VPN configuration (.VXV)")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "VØRZEX VPN configuration", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, getString(R.string.vzv_share_title)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to prepare VØRZEX share", e)
            toastError(R.string.vzv_export_no_profiles)
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                val displayName = runCatching {
                    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }.getOrNull().orEmpty()
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("The selected file could not be read")

                when {
                    com.v2ray.ang.util.VorzexConfigExchange.isRecognizedExchangeExtension(displayName) -> {
                        when (val decoded = com.v2ray.ang.util.VorzexConfigExchange.decode(content)) {
                            is com.v2ray.ang.util.VorzexConfigExchange.DecodeResult.Success -> {
                                // Import all payloads in one operation so asynchronous imports cannot race.
                                // The existing parser remains responsible for validating each Xray entry.
                                mainViewModel.onAction(MainAction.ImportVZVProfiles(decoded.profiles.map { it.content }))
                                toastSuccess(R.string.toast_success)
                            }
                            is com.v2ray.ang.util.VorzexConfigExchange.DecodeResult.Failure -> {
                                toastError(R.string.toast_failure)
                                LogUtil.w(AppConfig.TAG, "Rejected VZV exchange file: ${decoded.reason}")
                            }
                        }
                    }
                    com.v2ray.ang.util.VorzexConfigExchange.isNpvExtension(displayName) -> {
                        // NPV/NPVS are proprietary formats. Never silently reinterpret them as Xray text.
                        toastError(R.string.npv_import_not_supported)
                        LogUtil.w(AppConfig.TAG, "NPV/NPVS import needs a verified format-specific decoder")
                    }
                    else -> mainViewModel.onAction(MainAction.ImportBatchConfig(content))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read configuration file", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            EConfigType.AETHER -> ServerAetherActivity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            LauncherManager.restartService(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
