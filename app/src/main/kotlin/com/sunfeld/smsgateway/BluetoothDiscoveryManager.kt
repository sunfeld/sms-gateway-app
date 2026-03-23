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
 * Manages Bluetooth Classic device discovery via [BluetoothAdapter.startDiscovery].
 *
 * Registers a [BroadcastReceiver] that listens for [BluetoothDevice.ACTION_FOUND] events
 * and emits each newly discovered device into a [StateFlow] in real-time, de-duplicating
 * by MAC address. Auto-restarts the 12-second discovery window while [isDiscovering] is true.
 *
 * Usage:
 *   discoveryManager.startDiscovery(context)
 *   discoveryManager.devices.collect { list -> ... }
 *   discoveryManager.stopDiscovery(context)
 */
class BluetoothDiscoveryManager {

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

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
                    // Auto-restart discovery while active
                    if (_isDiscovering.value) {
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

    fun startDiscovery(context: Context) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter ?: return

        if (!hasRequiredPermissions(context)) return

        _isDiscovering.value = true
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

    fun stopDiscovery(context: Context) {
        _isDiscovering.value = false
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
