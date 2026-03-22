package com.sunfeld.smsgateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textview.MaterialTextView

class BluetoothStressTestActivity : AppCompatActivity() {

    private lateinit var switchStressTest: MaterialSwitch
    private lateinit var txtStatus: MaterialTextView
    private lateinit var txtPacketsSentCount: MaterialTextView
    private lateinit var txtDevicesTargetedCount: MaterialTextView
    private lateinit var txtDevicesHeader: MaterialTextView
    private lateinit var recyclerDevices: RecyclerView

    private val deviceAdapter = BtDeviceAdapter()
    private val viewModel: BluetoothStressTestViewModel by viewModels()
    private var isUpdatingSwitch = false

    private val btPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            viewModel.startAttack(this)
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
            setSwitchChecked(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_stress_test)

        switchStressTest = findViewById(R.id.switchStressTest)
        txtStatus = findViewById(R.id.txtStatus)
        txtPacketsSentCount = findViewById(R.id.txtPacketsSentCount)
        txtDevicesTargetedCount = findViewById(R.id.txtDevicesTargetedCount)
        txtDevicesHeader = findViewById(R.id.txtDevicesHeader)
        recyclerDevices = findViewById(R.id.recyclerDevices)

        recyclerDevices.layoutManager = LinearLayoutManager(this)
        recyclerDevices.adapter = deviceAdapter

        switchStressTest.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                requestPermissionsAndStart()
            } else {
                viewModel.stopAttack(this)
            }
        }

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

    private fun updateUI(state: AttackState) {
        when (state) {
            is AttackState.Idle -> {
                setSwitchChecked(false)
                switchStressTest.isEnabled = true
                txtStatus.text = getString(R.string.attack_state_idle)
            }
            is AttackState.Scanning -> {
                setSwitchChecked(true)
                switchStressTest.isEnabled = true
                txtStatus.text = getString(R.string.attack_state_scanning)
            }
            is AttackState.Attacking -> {
                setSwitchChecked(true)
                switchStressTest.isEnabled = true
                txtStatus.text = getString(R.string.attack_state_attacking, state.connectedCount)
            }
            is AttackState.Stopping -> {
                setSwitchChecked(false)
                switchStressTest.isEnabled = false
                txtStatus.text = getString(R.string.attack_state_stopping)
            }
            is AttackState.Error -> {
                setSwitchChecked(false)
                switchStressTest.isEnabled = true
                txtStatus.text = getString(R.string.attack_state_error)
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                viewModel.dismissError()
            }
        }
    }

    private fun setSwitchChecked(checked: Boolean) {
        isUpdatingSwitch = true
        switchStressTest.isChecked = checked
        isUpdatingSwitch = false
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
