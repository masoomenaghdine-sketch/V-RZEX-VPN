package com.v2ray.ang.ui.main

import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherPsiphonMode
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * VXV runs the bundled Psiphon client through the app's existing Aether/Psiphon path.
 * The main profile is saved, the main tunnel is stopped before this temporary profile starts,
 * and the original selection/service state is restored on exit.
 */
class VXVActivity : BaseComponentActivity() {

    private var restoreMainOnExit = false
    private var mainProfileGuid: String? = null
    private var temporaryProfileGuid: String? = null
    private var startRequested = false
    private var exitHandled = false
    private var localError by mutableStateOf<String?>(null)

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVXVService()
            } else {
                localError = getString(R.string.vxv_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        restoreMainOnExit = intent.getBooleanExtra(EXTRA_RESTORE_MAIN, false)
        mainProfileGuid = intent.getStringExtra(EXTRA_MAIN_GUID)
        temporaryProfileGuid = savedInstanceState?.getString(STATE_TEMP_PROFILE)
            ?: createTemporaryPsiphonProfile()
        super.onCreate(savedInstanceState)

        mainViewModel.onAction(MainAction.Initialize)
        mainViewModel.onAction(MainAction.RefreshServiceState)

        if (temporaryProfileGuid == null && localError == null) {
            localError = getString(R.string.vxv_engine_unavailable)
        } else if (savedInstanceState == null && temporaryProfileGuid != null) {
            requestPermissionOrStart()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        temporaryProfileGuid?.let { outState.putString(STATE_TEMP_PROFILE, it) }
        super.onSaveInstanceState(outState)
    }

    private fun createTemporaryPsiphonProfile(): String? {
        if (!AetherCoreManager.isSupported(this) || !AetherCoreManager.isPsiphonSupported(this)) {
            localError = getString(R.string.vxv_engine_unavailable)
            return null
        }

        return runCatching {
            val profile = ProfileItem.create(EConfigType.AETHER).apply {
                remarks = "VXV Psiphon (temporary)"
                // Psiphon-only mode does not need a WARP endpoint or user-supplied server profile.
                aetherPsiphon = AetherPsiphon.ONLY.type
                aetherPsiphonMode = AetherPsiphonMode.AUTO.type
                aetherPsiphonBundledList = true
            }
            val guid = UUID.randomUUID().toString()
            MmkvManager.encodeServerConfig(guid, profile)
            MmkvManager.setSelectServer(guid)
            guid
        }.getOrElse {
            localError = getString(R.string.vxv_profile_create_failed)
            null
        }
    }

    private fun requestPermissionOrStart() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            vpnPermissionLauncher.launch(permissionIntent)
        } else {
            startVXVService()
        }
    }

    private fun startVXVService() {
        val guid = temporaryProfileGuid ?: run {
            localError = getString(R.string.vxv_engine_unavailable)
            return
        }
        if (!AetherCoreManager.isSupported(this) || !AetherCoreManager.isPsiphonSupported(this)) {
            localError = getString(R.string.vxv_engine_unavailable)
            return
        }
        localError = null
        startRequested = true
        try {
            LauncherManager.startService(this, guid)
            mainViewModel.onAction(MainAction.RefreshServiceState)
        } catch (_: Exception) {
            localError = getString(R.string.vxv_start_failed)
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by mainViewModel.uiState.collectAsState()
        BackHandler { returnToMain() }

        val serviceStatus = state.status
        val statusText = when {
            localError != null -> localError!!
            serviceStatus is MainStatus.Connected && state.isRunning -> getString(R.string.vxv_connected)
            serviceStatus is MainStatus.Connecting -> serviceStatus.message
            state.isRunning -> getString(R.string.vxv_connecting)
            startRequested -> getString(R.string.vxv_start_failed)
            else -> getString(R.string.vxv_disconnected)
        }

        Scaffold { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "VXV",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(20.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.vxv_screen_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.vxv_switch_explanation),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (!state.isRunning && temporaryProfileGuid != null && !exitHandled) {
                    Button(onClick = { requestPermissionOrStart() }) {
                        Text(stringResource(R.string.vxv_start))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = { returnToMain() }, enabled = !exitHandled) {
                    Text(
                        if (exitHandled) stringResource(R.string.vxv_stopping)
                        else stringResource(R.string.vxv_return_main)
                    )
                }
            }
        }
    }

    private fun returnToMain() {
        if (exitHandled) return
        exitHandled = true
        lifecycleScope.launch {
            // Stop first and verify that the service no longer reports running before restoring Xray.
            if (startRequested && !stopVXVAndWait()) {
                exitHandled = false
                localError = getString(R.string.vxv_stop_failed)
                return@launch
            }

            val tempGuid = temporaryProfileGuid
            if (!tempGuid.isNullOrBlank()) {
                MmkvManager.tryRemoveServer(tempGuid)
                temporaryProfileGuid = null
            }

            val originalGuid = mainProfileGuid
            if (!originalGuid.isNullOrBlank() && MmkvManager.decodeServerConfig(originalGuid) != null) {
                MmkvManager.setSelectServer(originalGuid)
            }

            if (restoreMainOnExit && !originalGuid.isNullOrBlank()) {
                LauncherManager.startService(this@VXVActivity, originalGuid)
            }
            finish()
        }
    }

    private suspend fun stopVXVAndWait(): Boolean {
        LauncherManager.stopService(this)
        repeat(20) {
            delay(500)
            mainViewModel.onAction(MainAction.RefreshServiceState)
            delay(250)
            if (!mainViewModel.uiState.value.isRunning) {
                // Give the daemon time to close the TUN descriptor before any main profile is restarted.
                delay(750)
                mainViewModel.onAction(MainAction.RefreshServiceState)
                delay(300)
                if (!mainViewModel.uiState.value.isRunning) return true
            } else {
                LauncherManager.stopService(this)
            }
        }
        return false
    }

    companion object {
        const val EXTRA_RESTORE_MAIN = "com.v2ray.ang.vxv.RESTORE_MAIN"
        const val EXTRA_MAIN_GUID = "com.v2ray.ang.vxv.MAIN_PROFILE_GUID"
        private const val STATE_TEMP_PROFILE = "com.v2ray.ang.vxv.TEMP_PROFILE"
    }
}
