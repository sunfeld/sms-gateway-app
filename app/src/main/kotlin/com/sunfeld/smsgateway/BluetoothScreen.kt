package com.sunfeld.smsgateway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BluetoothScreen(
    viewModel: BluetoothHidViewModel,
    hidState: HidState,
    onScanClick: () -> Unit,
    onStartStopClick: () -> Unit,
    onSavePreset: () -> Unit,
    onLoadPreset: () -> Unit,
    onPickImage: () -> Unit,
    onSavePayload: () -> Unit,
    onLoadPayload: () -> Unit
) {
    val isScanning by viewModel.isScanningFlow.collectAsStateWithLifecycle()
    val keystrokesSent by viewModel.keystrokesSent.observeAsState(0)
    val connectedCount by viewModel.connectedCount.observeAsState(0)
    val isRunning = hidState is HidState.Attacking || hidState is HidState.Scanning

    val selectedTab by viewModel.activeTab.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Title
        Text(
            text = stringResource(R.string.bluetooth_hid_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.bluetooth_hid_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { if (!isRunning) viewModel.activeTab.value = 0 },
                text = { Text(stringResource(R.string.tab_ble_spam)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { if (!isRunning) viewModel.activeTab.value = 1 },
                text = { Text(stringResource(R.string.tab_data_send)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content
        when (selectedTab) {
            0 -> BleSpamTab(
                viewModel = viewModel,
                enabled = !isRunning,
                onSavePreset = onSavePreset,
                onLoadPreset = onLoadPreset
            )
            1 -> DataSendTab(
                viewModel = viewModel,
                enabled = !isRunning,
                onPickImage = onPickImage,
                onSavePayload = onSavePayload,
                onLoadPayload = onLoadPayload
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shared: Device List
        DeviceListScreen(
            devicesFlow = viewModel.discoveredDevicesFlow,
            isScanningFlow = viewModel.isScanningFlow,
            selectedTargetsFlow = viewModel.selectedTargetsFlow,
            onSelectionChanged = { viewModel.updateSelectedTargets(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // SCAN Button
        OutlinedButton(
            onClick = onScanClick,
            enabled = !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = stringResource(
                    if (isScanning) R.string.scan_btn_stop_scan else R.string.scan_btn_scan
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // START/STOP Button
        Button(
            onClick = onStartStopClick,
            enabled = hidState !is HidState.Stopping,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = if (isRunning) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ) else ButtonDefaults.buttonColors()
        ) {
            Text(
                text = stringResource(
                    if (isRunning) R.string.hid_btn_stop else R.string.hid_btn_start
                ),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status
        Text(
            text = formatStatus(hidState),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Counter Card
        CounterCard(
            broadcastsSent = keystrokesSent,
            targetsCount = connectedCount
        )
    }
}

@Composable
private fun CounterCard(broadcastsSent: Int, targetsCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatCount(broadcastsSent),
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = stringResource(R.string.keystrokes_sent_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = targetsCount.toString(),
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = stringResource(R.string.connected_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun formatStatus(state: HidState): String {
    return when (state) {
        is HidState.Idle -> stringResource(R.string.attack_state_idle)
        is HidState.Scanning -> stringResource(R.string.attack_state_scanning)
        is HidState.Attacking -> stringResource(R.string.attack_state_attacking, state.connectedCount)
        is HidState.Stopping -> stringResource(R.string.attack_state_stopping)
        is HidState.Error -> stringResource(R.string.attack_state_error)
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
