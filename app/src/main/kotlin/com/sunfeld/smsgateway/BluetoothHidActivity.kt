package com.sunfeld.smsgateway

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState

class BluetoothHidActivity : AppCompatActivity() {

    private val viewModel: BluetoothHidViewModel by viewModels()
    private lateinit var btPermissionManager: BluetoothPermissionManager

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        handleImagePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        btPermissionManager = BluetoothPermissionManager(this)

        // Ensure default payloads exist
        PayloadRepository.ensureDefaults(this)

        setContent {
            MaterialTheme {
                val hidState by viewModel.state.observeAsState(HidState.Idle)

                BluetoothScreen(
                    viewModel = viewModel,
                    hidState = hidState,
                    onScanClick = { handleScanClick() },
                    onStartStopClick = { handleStartStopClick(hidState) },
                    onCrayModeClick = { handleCrayModeClick() },
                    onSavePreset = { SavePresetDialog.show(this@BluetoothHidActivity) },
                    onLoadPreset = { LoadPresetDialog.show(this@BluetoothHidActivity) },
                    onPickImage = { imagePicker.launch("image/*") },
                    onSavePayload = { saveCurrentPayload() },
                    onLoadPayload = { showLoadPayloadDialog() }
                )
            }
        }

        // Observe errors for Toast
        viewModel.state.observe(this) { state ->
            if (state is HidState.Error) {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                viewModel.dismissError()
            }
        }
    }

    private fun handleScanClick() {
        val scanning = viewModel.isScanning.value == true
        if (scanning) {
            viewModel.stopScan(this)
        } else {
            btPermissionManager.requestScanPermissions { viewModel.startScan(this) }
        }
    }

    private fun handleCrayModeClick() {
        if (viewModel.isCrayMode.value) {
            viewModel.stopCrayMode(this)
        } else {
            btPermissionManager.requestAllBluetoothPermissions {
                viewModel.startCrayMode(this)
            }
        }
    }

    private fun handleStartStopClick(hidState: HidState) {
        val isRunning = hidState is HidState.Attacking || hidState is HidState.Scanning
        if (isRunning) {
            viewModel.stopAttack(this)
        } else {
            val targets = viewModel.selectedTargets.value ?: emptySet()
            if (targets.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_devices_selected), Toast.LENGTH_SHORT).show()
                return
            }
            btPermissionManager.requestAllBluetoothPermissions { viewModel.startAttack(this) }
        }
    }

    private fun handleImagePicked(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bytes = inputStream.readBytes()
            inputStream.close()
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val sizeKb = bytes.size / 1024
            viewModel.setImageData(bytes, mimeType, "Image selected (${sizeKb}KB)")
            Toast.makeText(this, "Image loaded (${sizeKb}KB)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCurrentPayload() {
        val payload = viewModel.buildPayloadFromForm()
        if (payload.name.isBlank()) {
            Toast.makeText(this, "Payload name is required", Toast.LENGTH_SHORT).show()
            return
        }
        PayloadRepository.save(this, payload)
        Toast.makeText(this, getString(R.string.payload_saved), Toast.LENGTH_SHORT).show()
    }

    private fun showLoadPayloadDialog() {
        val payloads = PayloadRepository.getAll(this)
        if (payloads.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_payloads), Toast.LENGTH_SHORT).show()
            return
        }

        val names = payloads.map { "${it.name} (${it.type.name})" }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.load_payload))
            .setItems(names) { _, which ->
                val selected = payloads[which]
                viewModel.loadPayloadIntoForm(selected)
                Toast.makeText(this, getString(R.string.payload_loaded), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- Preset compatibility (used by SavePresetDialog / LoadPresetDialog) ----

    fun applyPreset(preset: HidPreset) {
        val profile = DeviceProfiles.findById(preset.profileId) ?: DeviceProfiles.DEFAULT
        viewModel.selectedProfile.value = profile
        viewModel.selectedProfileFlow.value = profile
        viewModel.customDeviceName.value = preset.customDeviceName
        viewModel.customDeviceNameFlow.value = preset.customDeviceName
        viewModel.payload.value = preset.payload
        viewModel.payloadTextFlow.value = preset.payload
        viewModel.updateSelectedTargets(preset.targetAddresses.toSet())
        Toast.makeText(this, getString(R.string.preset_loaded), Toast.LENGTH_SHORT).show()
    }

    fun getCurrentPresetState(): PresetState {
        return PresetState(
            profileId = viewModel.selectedProfile.value?.id ?: DeviceProfiles.DEFAULT.id,
            customDeviceName = viewModel.customDeviceName.value ?: "",
            targetAddresses = viewModel.selectedTargets.value?.toList() ?: emptyList(),
            payload = viewModel.payload.value ?: ""
        )
    }

    data class PresetState(
        val profileId: String,
        val customDeviceName: String,
        val targetAddresses: List<String>,
        val payload: String
    )
}
