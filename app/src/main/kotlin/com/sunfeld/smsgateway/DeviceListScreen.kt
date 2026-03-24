package com.sunfeld.smsgateway

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * Composable screen that collects the discovery [StateFlow] and renders
 * device cards with unique MAC address filtering, fuzzy search, and
 * known/unknown device separation.
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

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var unknownExpanded by rememberSaveable { mutableStateOf(false) }

    val uniqueDevices = devices.distinctBy { it.address }

    // Split into known (has name) and unknown (no name)
    val (knownDevices, unknownDevices) = uniqueDevices.partition { device ->
        val name = try { device.name?.takeIf { it.isNotBlank() } } catch (_: SecurityException) { null }
        name != null
    }

    // Fuzzy filter based on search query
    val filteredKnown = if (searchQuery.isBlank()) knownDevices else knownDevices.filter { device ->
        fuzzyMatch(deviceDisplayName(device), searchQuery) ||
            device.address.contains(searchQuery, ignoreCase = true)
    }
    val filteredUnknown = if (searchQuery.isBlank()) unknownDevices else unknownDevices.filter { device ->
        device.address.contains(searchQuery, ignoreCase = true)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_devices_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (isScanning && uniqueDevices.isEmpty()) {
            ScanningIndicator()
        } else if (!isScanning && uniqueDevices.isEmpty()) {
            EmptyStateNoDevices()
        } else {
            // Known devices section
            if (filteredKnown.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.known_devices_header),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                filteredKnown.forEach { device ->
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

            // Unknown devices section (collapsed by default)
            if (filteredUnknown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { unknownExpanded = !unknownExpanded }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (unknownExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.unknown_devices_header, filteredUnknown.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                AnimatedVisibility(
                    visible = unknownExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredUnknown.forEach { device ->
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

            // Show scanning indicator at bottom while still scanning
            if (isScanning) {
                ScanningIndicator()
            }
        }
    }
}

/**
 * Fuzzy match: checks if all characters of the query appear in order
 * within the target string (case-insensitive). Allows gaps between
 * characters for loose matching.
 */
private fun fuzzyMatch(target: String, query: String): Boolean {
    val t = target.lowercase()
    val q = query.lowercase()
    var ti = 0
    for (ch in q) {
        val found = t.indexOf(ch, ti)
        if (found < 0) return false
        ti = found + 1
    }
    return true
}

/** Safe device name extraction. */
private fun deviceDisplayName(device: BluetoothDevice): String {
    return try {
        device.name?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
    } ?: stringResourceFallback()
}

/** Fallback name when composable context isn't available. */
private fun stringResourceFallback(): String = "Unknown device"

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
    } ?: stringResource(R.string.unknown_device_name)

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
