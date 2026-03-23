package com.sunfeld.smsgateway

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView

class BluetoothHidActivity : AppCompatActivity() {

    private lateinit var spinnerProfile: MaterialAutoCompleteTextView
    private lateinit var editDeviceName: TextInputEditText
    private lateinit var editPayload: TextInputEditText
    private lateinit var btnScan: MaterialButton
    private lateinit var btnStartStop: MaterialButton
    private lateinit var btnSavePreset: MaterialButton
    private lateinit var btnLoadPreset: MaterialButton
    private lateinit var txtStatus: MaterialTextView
    private lateinit var txtScanStatus: MaterialTextView
    private lateinit var txtPacketsSentCount: MaterialTextView
    private lateinit var txtDevicesTargetedCount: MaterialTextView
    private lateinit var txtDevicesHeader: MaterialTextView
    private lateinit var recyclerDevices: RecyclerView
    private lateinit var scanningIndicator: LinearLayout
    private lateinit var emptyStateNoDevices: LinearLayout

    private val deviceAdapter = BtDeviceAdapter()
    private val viewModel: BluetoothHidViewModel by viewModels()
    private var isRunning = false

    private lateinit var btPermissionManager: BluetoothPermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_stress_test)

        btPermissionManager = BluetoothPermissionManager(this)

        bindViews()
        setupProfileDropdown()
        setupDeviceList()
        setupButtons()
        observeViewModel()
    }

    private fun bindViews() {
        spinnerProfile = findViewById(R.id.spinnerProfile)
        editDeviceName = findViewById(R.id.editDeviceName)
        editPayload = findViewById(R.id.editPayload)
        btnScan = findViewById(R.id.btnScan)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnSavePreset = findViewById(R.id.btnSavePreset)
        btnLoadPreset = findViewById(R.id.btnLoadPreset)
        txtStatus = findViewById(R.id.txtStatus)
        txtScanStatus = findViewById(R.id.txtScanStatus)
        txtPacketsSentCount = findViewById(R.id.txtPacketsSentCount)
        txtDevicesTargetedCount = findViewById(R.id.txtDevicesTargetedCount)
        txtDevicesHeader = findViewById(R.id.txtDevicesHeader)
        recyclerDevices = findViewById(R.id.recyclerDevices)
        scanningIndicator = findViewById(R.id.scanningIndicator)
        emptyStateNoDevices = findViewById(R.id.emptyStateNoDevices)
    }

    private fun setupProfileDropdown() {
        val profiles = DeviceProfiles.ALL
        val names = profiles.map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        spinnerProfile.setAdapter(adapter)

        // Set default
        spinnerProfile.setText(DeviceProfiles.DEFAULT.displayName, false)
        editDeviceName.setText(DeviceProfiles.DEFAULT.sdpName)

        spinnerProfile.setOnItemClickListener { _, _, position, _ ->
            val profile = profiles[position]
            viewModel.selectedProfile.value = profile
            editDeviceName.setText(profile.sdpName)
            viewModel.customDeviceName.value = profile.sdpName
        }

        editDeviceName.doAfterTextChanged { text ->
            viewModel.customDeviceName.value = text?.toString() ?: ""
        }

        editPayload.doAfterTextChanged { text ->
            viewModel.payload.value = text?.toString() ?: ""
        }
    }

    private fun setupDeviceList() {
        recyclerDevices.layoutManager = LinearLayoutManager(this)
        recyclerDevices.adapter = deviceAdapter

        deviceAdapter.onSelectionChanged = { selected ->
            viewModel.selectedTargets.value = selected
        }
    }

    private fun setupButtons() {
        btnScan.setOnClickListener {
            val scanning = viewModel.isScanning.value == true
            if (scanning) {
                viewModel.stopScan(this)
            } else {
                btPermissionManager.requestScanPermissions { viewModel.startScan(this) }
            }
        }

        btnStartStop.setOnClickListener {
            if (isRunning) {
                viewModel.stopAttack(this)
            } else {
                val targets = viewModel.selectedTargets.value ?: emptySet()
                if (targets.isEmpty()) {
                    Toast.makeText(this, getString(R.string.no_devices_selected), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                btPermissionManager.requestAllBluetoothPermissions { viewModel.startAttack(this) }
            }
        }

        btnSavePreset.setOnClickListener {
            SavePresetDialog.show(this)
        }

        btnLoadPreset.setOnClickListener {
            LoadPresetDialog.show(this)
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { updateUI(it) }

        viewModel.isScanning.observe(this) { scanning ->
            btnScan.text = getString(
                if (scanning) R.string.scan_btn_stop_scan else R.string.scan_btn_scan
            )
            val hasDevices = (viewModel.discoveredDevices.value?.size ?: 0) > 0
            if (scanning) {
                // Show scanning indicator, hide empty state
                txtDevicesHeader.visibility = View.VISIBLE
                txtScanStatus.visibility = View.VISIBLE
                scanningIndicator.visibility = View.VISIBLE
                emptyStateNoDevices.visibility = View.GONE
                recyclerDevices.visibility = if (hasDevices) View.VISIBLE else View.GONE
                txtScanStatus.text = getString(R.string.scan_status_scanning)
            } else {
                // Scanning stopped — hide indicator, show empty state if no devices
                scanningIndicator.visibility = View.GONE
                if (!hasDevices) {
                    // Only show empty state if a scan was actually attempted (header visible)
                    val scanWasActive = txtDevicesHeader.visibility == View.VISIBLE
                    emptyStateNoDevices.visibility = if (scanWasActive) View.VISIBLE else View.GONE
                }
            }
            // Disable scan button during active HID impersonation
            btnScan.isEnabled = !isRunning
        }

        viewModel.keystrokesSent.observe(this) { count ->
            txtPacketsSentCount.text = formatCount(count)
        }

        viewModel.connectedCount.observe(this) { count ->
            txtDevicesTargetedCount.text = count.toString()
        }

        viewModel.discoveredDevices.observe(this) { devices ->
            deviceAdapter.updateDevices(devices)
            val hasDevices = devices.isNotEmpty()
            val scanning = viewModel.isScanning.value == true
            txtDevicesHeader.visibility = if (hasDevices || scanning) View.VISIBLE else View.GONE
            recyclerDevices.visibility = if (hasDevices) View.VISIBLE else View.GONE
            if (hasDevices) {
                // Devices found — hide scanning indicator and empty state, show count
                scanningIndicator.visibility = if (scanning) View.VISIBLE else View.GONE
                emptyStateNoDevices.visibility = View.GONE
                txtScanStatus.text = getString(R.string.scan_status_found, devices.size)
            }
        }
    }

    private fun updateUI(state: HidState) {
        when (state) {
            is HidState.Idle -> {
                isRunning = false
                btnStartStop.text = getString(R.string.hid_btn_start)
                btnStartStop.isEnabled = true
                setConfigFieldsEnabled(true)
                txtStatus.text = getString(R.string.attack_state_idle)
            }
            is HidState.Scanning -> {
                isRunning = true
                btnStartStop.text = getString(R.string.hid_btn_stop)
                btnStartStop.isEnabled = true
                setConfigFieldsEnabled(false)
                txtStatus.text = getString(R.string.attack_state_scanning)
            }
            is HidState.Attacking -> {
                isRunning = true
                btnStartStop.text = getString(R.string.hid_btn_stop)
                btnStartStop.isEnabled = true
                setConfigFieldsEnabled(false)
                txtStatus.text = getString(R.string.attack_state_attacking, state.connectedCount)
            }
            is HidState.Stopping -> {
                isRunning = false
                btnStartStop.text = getString(R.string.hid_btn_start)
                btnStartStop.isEnabled = false
                setConfigFieldsEnabled(false)
                txtStatus.text = getString(R.string.attack_state_stopping)
            }
            is HidState.Error -> {
                isRunning = false
                btnStartStop.text = getString(R.string.hid_btn_start)
                btnStartStop.isEnabled = true
                setConfigFieldsEnabled(true)
                txtStatus.text = getString(R.string.attack_state_error)
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                viewModel.dismissError()
            }
        }
    }

    private fun setConfigFieldsEnabled(enabled: Boolean) {
        spinnerProfile.isEnabled = enabled
        editDeviceName.isEnabled = enabled
        editPayload.isEnabled = enabled
        btnSavePreset.isEnabled = enabled
        btnLoadPreset.isEnabled = enabled
        btnScan.isEnabled = enabled
    }

    fun applyPreset(preset: HidPreset) {
        val profile = DeviceProfiles.findById(preset.profileId) ?: DeviceProfiles.DEFAULT
        viewModel.selectedProfile.value = profile
        viewModel.customDeviceName.value = preset.customDeviceName
        viewModel.payload.value = preset.payload

        spinnerProfile.setText(profile.displayName, false)
        editDeviceName.setText(preset.customDeviceName)
        editPayload.setText(preset.payload)

        // Pre-select target addresses if they're in the current device list
        deviceAdapter.setSelectedAddresses(preset.targetAddresses.toSet())

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

    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
