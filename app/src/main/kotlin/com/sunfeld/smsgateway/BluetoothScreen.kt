package com.sunfeld.smsgateway

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    onCrayModeClick: () -> Unit,
    onSavePreset: () -> Unit,
    onLoadPreset: () -> Unit,
    onPickImage: () -> Unit,
    onSavePayload: () -> Unit,
    onLoadPayload: () -> Unit
) {
    val isScanning by viewModel.isScanningFlow.collectAsStateWithLifecycle()
    val connectedCount by viewModel.connectedCount.observeAsState(0)
    val isCray by viewModel.isCrayMode.collectAsStateWithLifecycle()
    val craySeconds by viewModel.craySecondsRemaining.collectAsStateWithLifecycle()
    val crayDuration by viewModel.crayDuration.collectAsStateWithLifecycle()
    val confirmed by viewModel.confirmedHits.collectAsStateWithLifecycle()
    val skipped by viewModel.skippedTargets.collectAsStateWithLifecycle()
    val isRunning = hidState is HidState.Attacking || hidState is HidState.Scanning || hidState is HidState.CrayMode

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

        // ---- CRAY MODE SECTION ----
        CrayModeCard(
            isCrayActive = isCray,
            secondsRemaining = craySeconds,
            duration = crayDuration,
            targetsCount = connectedCount,
            confirmedCount = confirmed,
            enabled = !isRunning || isCray,
            onDurationSelected = { viewModel.crayDuration.value = it },
            onCrayClick = onCrayModeClick
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
            enabled = hidState !is HidState.Stopping && !isCray,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = if (isRunning && !isCray) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ) else ButtonDefaults.buttonColors()
        ) {
            Text(
                text = stringResource(
                    if (isRunning && !isCray) R.string.hid_btn_stop else R.string.hid_btn_start
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
            confirmed = confirmed,
            skipped = skipped,
            targetsCount = connectedCount
        )
    }
}

@Composable
private fun CrayModeCard(
    isCrayActive: Boolean,
    secondsRemaining: Int,
    duration: Int,
    targetsCount: Int,
    confirmedCount: Int,
    enabled: Boolean,
    onDurationSelected: (Int) -> Unit,
    onCrayClick: () -> Unit
) {
    val durations = listOf(30, 60, 120, 300)
    val durationLabels = listOf("30s", "1m", "2m", "5m")

    // Pulsing animation when active
    val pulseAlpha = if (isCrayActive) {
        val transition = rememberInfiniteTransition(label = "cray_pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cray_alpha"
        )
        alpha
    } else 1f

    val cardColor = if (isCrayActive) {
        Color(0xFFB71C1C) // Deep red when active
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    val textColor = if (isCrayActive) Color.White else MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(pulseAlpha),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCrayActive) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (isCrayActive) "CRAY MODE ACTIVE" else "CRAY MODE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = textColor
            )

            if (!isCrayActive) {
                Text(
                    text = "Auto-scan + precision pair ALL nearby devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isCrayActive) {
                // Active: show countdown + stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatCrayTimer(secondsRemaining),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                        Text("REMAINING", style = MaterialTheme.typography.labelSmall, color = textColor)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = targetsCount.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text("TARGETS", style = MaterialTheme.typography.labelSmall, color = textColor)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatCount(confirmedCount),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text("CONFIRMED", style = MaterialTheme.typography.labelSmall, color = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stop button
                Button(
                    onClick = onCrayClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFB71C1C)
                    )
                ) {
                    Text("STOP CRAY MODE", fontWeight = FontWeight.Black)
                }
            } else {
                // Inactive: show duration picker + launch button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    durations.forEachIndexed { index, dur ->
                        OutlinedButton(
                            onClick = { onDurationSelected(dur) },
                            modifier = Modifier.weight(1f),
                            colors = if (duration == dur) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                )
                            } else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(
                                durationLabels[index],
                                fontWeight = if (duration == dur) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onCrayClick,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        "GO CRAY",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterCard(confirmed: Int, skipped: Int, targetsCount: Int) {
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
                    text = formatCount(confirmed),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Confirmed",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatCount(skipped),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Skipped",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
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
        is HidState.Attacking -> "Status: Pairing with ${state.connectedCount} target(s)"
        is HidState.CrayMode -> "CRAY MODE: ${state.connectedCount} targets, ${state.secondsRemaining}s left"
        is HidState.Stopping -> stringResource(R.string.attack_state_stopping)
        is HidState.Error -> stringResource(R.string.attack_state_error)
    }
}

private fun formatCrayTimer(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return if (min > 0) String.format("%d:%02d", min, sec) else "${sec}s"
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
