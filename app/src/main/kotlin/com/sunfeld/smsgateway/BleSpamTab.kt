package com.sunfeld.smsgateway

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleSpamTab(
    viewModel: BluetoothHidViewModel,
    enabled: Boolean,
    onSavePreset: () -> Unit,
    onLoadPreset: () -> Unit
) {
    val selectedProfile by viewModel.selectedProfileFlow.collectAsStateWithLifecycle()
    val customDeviceName by viewModel.customDeviceNameFlow.collectAsStateWithLifecycle()
    val payloadText by viewModel.payloadTextFlow.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_section_title),
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Profile Dropdown
            ProfileDropdown(
                profiles = DeviceProfiles.ALL,
                selectedProfile = selectedProfile,
                enabled = enabled,
                onProfileSelected = { profile ->
                    viewModel.selectedProfileFlow.value = profile
                    viewModel.selectedProfile.value = profile
                    viewModel.customDeviceNameFlow.value = profile.sdpName
                    viewModel.customDeviceName.value = profile.sdpName
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Device Name
            OutlinedTextField(
                value = customDeviceName,
                onValueChange = {
                    viewModel.customDeviceNameFlow.value = it
                    viewModel.customDeviceName.value = it
                },
                label = { Text(stringResource(R.string.device_name_hint)) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Payload Message
            OutlinedTextField(
                value = payloadText,
                onValueChange = {
                    viewModel.payloadTextFlow.value = it
                    viewModel.payload.value = it
                },
                label = { Text(stringResource(R.string.payload_hint)) },
                enabled = enabled,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Save / Load Preset buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSavePreset,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save_preset))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onLoadPreset,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.load_preset))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(
    profiles: List<DeviceProfile>,
    selectedProfile: DeviceProfile,
    enabled: Boolean,
    onProfileSelected: (DeviceProfile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedProfile.displayName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.select_profile_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.displayName) },
                    onClick = {
                        onProfileSelected(profile)
                        expanded = false
                    }
                )
            }
        }
    }
}
