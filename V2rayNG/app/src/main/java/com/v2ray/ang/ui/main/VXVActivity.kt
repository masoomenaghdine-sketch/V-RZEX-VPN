package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.base.BaseComponentActivity

/**
 * Isolated VXV entry point. The main VPN service is stopped by MainActivity before this screen
 * opens; returning starts the same selected main profile when one was selected.
 *
 * The standalone Shir o Khorshid/Psiphon engine is intentionally not represented as connected
 * until it is actually integrated and can report a real connection state.
 */
class VXVActivity : BaseComponentActivity() {

    private var restoreMainOnExit = false
    private var mainProfileGuid: String? = null
    private var exitHandled = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        restoreMainOnExit = intent.getBooleanExtra(EXTRA_RESTORE_MAIN, false)
        mainProfileGuid = intent.getStringExtra(EXTRA_MAIN_GUID)
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { returnToMain() }

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
                            text = stringResource(R.string.vxv_engine_pending),
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
                Button(onClick = { returnToMain() }) {
                    Text(stringResource(R.string.vxv_return_main))
                }
            }
        }
    }

    private fun returnToMain() {
        if (exitHandled) return
        exitHandled = true
        if (restoreMainOnExit) {
            LauncherManager.startService(this, mainProfileGuid)
        }
        finish()
    }

    companion object {
        const val EXTRA_RESTORE_MAIN = "com.v2ray.ang.vxv.RESTORE_MAIN"
        const val EXTRA_MAIN_GUID = "com.v2ray.ang.vxv.MAIN_PROFILE_GUID"
    }
}
