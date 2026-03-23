package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers nearby Bluetooth Classic devices via [BluetoothAdapter.startDiscovery].
 *
 * Starts a scan, collects all [BluetoothDevice] objects seen during the 12-second
 * discovery window, and auto-restarts while [isScanning] is true.
 *
 * Usage:
 *   scanner.startScan(context)
 *   scanner.devices.collect { list -> ... }
 *   scanner.stopScan(context)
 */
class BluetoothScanner {

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var adapter: BluetoothAdapter? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Auto-restart scan while active
                    if (_isActive.value) {
                        adapter?.startDiscovery()
                    }
                }
            }
        }
    }

    private fun addDevice(device: BluetoothDevice) {
        val current = _devices.value.toMutableList()
        if (current.none { it.address == device.address }) {
            current.add(device)
            _devices.value = current
        }
    }

    fun startScan(context: Context) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter ?: return

        if (!hasRequiredPermissions(context)) return

        _isActive.value = true
        _devices.value = emptyList()

        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(receiver, filter)
            receiverRegistered = true
        }

        adapter?.cancelDiscovery()
        adapter?.startDiscovery()
    }

    fun stopScan(context: Context) {
        _isActive.value = false
        adapter?.cancelDiscovery()

        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
            receiverRegistered = false
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean =
        BluetoothPermissionManager.hasScanPermissions(context)
}
