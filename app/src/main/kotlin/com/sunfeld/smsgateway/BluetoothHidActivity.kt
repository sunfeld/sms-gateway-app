package com.sunfeld.smsgateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView

class BluetoothHidActivity : AppCompatActivity() {

    private lateinit var spinnerProfile: MaterialAutoCompleteTextView
    private lateinit var editDeviceName: TextInputEditText
    private lateinit var editPayload: TextInputEditText
    private lateinit var btnStartStop: MaterialButton
    private lateinit var btnSavePreset: MaterialButton
    private lateinit var btnLoadPreset: MaterialButton
    private lateinit var txtStatus: MaterialTextView
    private lateinit var txtPacketsSentCount: MaterialTextView
    private lateinit var txtDevicesTargetedCount: MaterialTextView
    private lateinit var txtDevicesHeader: MaterialTextView
    private lateinit var recyclerDevices: RecyclerView

    private val deviceAdapter = BtDeviceAdapter()
    private val viewModel: BluetoothHidViewModel by viewModels()
    private var isRunning = false

    private val btPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startAttack(this)
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_stress_test)

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
        btnStartStop = findViewById(R.id.btnStartStop)
        btnSavePreset = findViewById(R.id.btnSavePreset)
        btnLoadPreset = findViewById(R.id.btnLoadPreset)
        txtStatus = findViewById(R.id.txtStatus)
        txtPacketsSentCount = findViewById(R.id.txtPacketsSentCount)
        txtDevicesTargetedCount = findViewById(R.id.txtDevicesTargetedCount)
        txtDevicesHeader = findViewById(R.id.txtDevicesHeader)
        recyclerDevices = findViewById(R.id.recyclerDevices)
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
        btnStartStop.setOnClickListener {
            if (isRunning) {
                viewModel.stopAttack(this)
            } else {
                val targets = viewModel.selectedTargets.value ?: emptySet()
                if (targets.isEmpty()) {
                    Toast.makeText(this, getString(R.string.no_devices_selected), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                requestPermissionsAndStart()
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

        viewModel.keystrokesSent.observe(this) { count ->
            txtPacketsSentCount.text = formatCount(count)
        }

        viewModel.connectedCount.observe(this) { count ->
            txtDevicesTargetedCount.text = count.toString()
        }

        viewModel.discoveredDevices.observe(this) { devices ->
            deviceAdapter.updateDevices(devices)
            val visible = devices.isNotEmpty()
            txtDevicesHeader.visibility = if (visible) View.VISIBLE else View.GONE
            recyclerDevices.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun requestPermissionsAndStart() {
        val missing = btPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.startAttack(this)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
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
