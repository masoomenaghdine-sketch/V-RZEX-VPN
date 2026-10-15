package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
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
    private val aiAutoConnectInProgress = AtomicBoolean(false)
    @Volatile private var suppressAiFailoverOnce = false
    @Volatile private var lastAiFailoverAtMs = 0L
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
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
            try {
                // A TCP handshake only narrows the list; success requires real HTTPS traffic through the tunnel.
                val snapshot = com.v2ray.ang.core.AiNetworkAdvisor.assess(this@MainActivity)
                val candidates = com.v2ray.ang.core.AiConfigGenerator
                    .rankedReachableLocalXrayProfiles(snapshot)
                    .take(MAX_AI_AUTO_CONNECT_ATTEMPTS)

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

                // Preserve the independent Aether quick-setup fallback when no local Xray profile
                // passes the real traffic check.
                pendingAetherQuickConnect = true
                profileEditorLauncher.launch(
                    Intent(this@MainActivity, ServerAetherActivity::class.java)
                        .putExtra(ServerAetherActivity.EXTRA_AI_QUICK_SETUP, true)
                )
            } finally {
                aiAutoConnectInProgress.set(false)
            }
        }
    }

    private companion object {
        const val MAX_AI_AUTO_CONNECT_ATTEMPTS = 3
        const val AI_CONNECTION_WAIT_MS = 18_000L
        const val SERVICE_STOP_WAIT_MS = 4_000L
        const val AI_FAILOVER_RETRY_DELAY_MS = 500L
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
        if (destination == MainDestination.Device) {
            startActivity(Intent(this, DeviceActivity::class.java))
            return
        }
        if (destination == MainDestination.Cnr) {
            startActivity(Intent(this, CnrActivity::class.java))
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
            MainDestination.Device -> error("Device is handled before building the settings intent")
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
                                // Reuse the existing profile import pipeline for each supported payload.
                                // Payloads remain subject to the app's own parser and validation.
                                decoded.profiles.forEach { profile ->
                                    mainViewModel.onAction(MainAction.ImportBatchConfig(profile.content))
                                }
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
                        toastError(R.string.toast_failure)
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
