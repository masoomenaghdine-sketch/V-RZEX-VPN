package com.v2ray.ang.ui.main

import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

class NetworkLabActivity : BaseComponentActivity() {
    private val viewModel: NetworkLabViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val tab = intent.getStringExtra(EXTRA_TAB) ?: TAB_DNS
        val state by viewModel.state.collectAsStateWithLifecycle()
        NetworkLabScreen(
            state = state,
            gamingTab = tab == TAB_GAMING,
            onBack = { finish() },
            onScanDns = viewModel::scanDns,
            onApplyDns = viewModel::applyDns,
            onGamingToggle = viewModel::setGamingEnabled,
            onTestGaming = viewModel::testGamingNetwork,
        )
    }

    companion object {
        const val EXTRA_TAB = "network_lab_tab"
        const val TAB_DNS = "dns"
        const val TAB_GAMING = "gaming"
    }
}

@Composable
private fun NetworkLabScreen(
    state: NetworkLabState,
    gamingTab: Boolean,
    onBack: () -> Unit,
    onScanDns: () -> Unit,
    onApplyDns: (String) -> Unit,
    onGamingToggle: (Boolean) -> Unit,
    onTestGaming: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (gamingTab) R.string.gaming_mode_title else R.string.dns_lab_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.network_lab_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }

            if (gamingTab) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.gaming_mode_title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.gaming_mode_description), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = state.gamingEnabled, onCheckedChange = onGamingToggle)
                        }
                        Button(onClick = onTestGaming, enabled = !state.isTestingGaming, modifier = Modifier.fillMaxWidth()) {
                            if (state.isTestingGaming) {
                                CircularProgressIndicator()
                            } else {
                                Text(stringResource(R.string.gaming_test_button))
                            }
                        }
                        state.gamingAverageMs?.let {
                            Text(stringResource(R.string.gaming_average, it))
                        }
                        state.gamingJitterMs?.let {
                            Text(stringResource(R.string.gaming_jitter, it))
                        }
                        state.gamingLossPercent?.let {
                            Text(stringResource(R.string.gaming_loss, it))
                        }
                        Text(
                            stringResource(R.string.gaming_measurement_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(stringResource(R.string.dns_lab_explanation), style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = onScanDns,
                    enabled = !state.isScanningDns,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isScanningDns) CircularProgressIndicator()
                    else Text(stringResource(R.string.dns_scan_button))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(state.resolvers, key = { it.coreAddress }) { resolver ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(resolver.name, style = MaterialTheme.typography.titleMedium)
                                Text(resolver.description, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = when (resolver.successful) {
                                        true -> stringResource(R.string.dns_result_success, resolver.latencyMs ?: 0L)
                                        false -> stringResource(R.string.dns_result_unstable)
                                        null -> stringResource(R.string.dns_result_not_tested)
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { onApplyDns(resolver.coreAddress) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.dns_apply_button))
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
