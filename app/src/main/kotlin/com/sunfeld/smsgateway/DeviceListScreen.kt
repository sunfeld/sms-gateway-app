package com.sunfeld.smsgateway

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * Composable screen that collects the discovery [StateFlow] and renders
 * device cards with unique MAC address filtering.
 *
 * Uses Column (not LazyColumn) because this is embedded inside a
 * NestedScrollView which provides infinite height constraints —
 * LazyColumn crashes with infinite height.
 */
@Composable
fun DeviceListScreen(
    devicesFlow: StateFlow<List<BluetoothDevice>>,
    isScanningFlow: StateFlow<Boolean>,
    selectedTargetsFlow: StateFlow<Set<String>>,
    onSelectionChanged: (Set<String>) -> Unit
) {
    val devices by devicesFlow.collectAsStateWithLifecycle()
    val isScanning by isScanningFlow.collectAsStateWithLifecycle()
    val selectedTargets by selectedTargetsFlow.collectAsStateWithLifecycle()

    val uniqueDevices = devices.distinctBy { it.address }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isScanning && uniqueDevices.isEmpty()) {
            ScanningIndicator()
        } else if (!isScanning && uniqueDevices.isEmpty()) {
            EmptyStateNoDevices()
        } else {
            uniqueDevices.forEach { device ->
                DeviceCard(
                    device = device,
                    isSelected = selectedTargets.contains(device.address),
                    onToggleSelection = { selected ->
                        val updated = if (selected) {
                            selectedTargets + device.address
                        } else {
                            selectedTargets - device.address
                        }
                        onSelectionChanged(updated)
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BluetoothDevice,
    isSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit
) {
    val deviceName = try {
        device.name?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
    } ?: "Unknown device"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggleSelection
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScanningIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.scanning_indicator_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateNoDevices() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(android.R.drawable.stat_sys_data_bluetooth),
                contentDescription = stringResource(R.string.no_devices_found),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_devices_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
