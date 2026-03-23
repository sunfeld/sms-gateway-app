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

    internal val filter = DeviceFilter()

    private var adapter: BluetoothAdapter? = null
    private var receiverRegistered = false
    private val devicesByAddress = mutableMapOf<String, BluetoothDevice>()

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
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)
                    device?.let { addDevice(it, rssi) }
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

    private fun addDevice(device: BluetoothDevice, rssi: Short = 0) {
        val name = try { device.name } catch (_: SecurityException) { null }
        val isNew = filter.addOrUpdate(device.address, name, rssi)
        if (isNew) {
            devicesByAddress[device.address] = device
            _devices.value = filter.getAll().mapNotNull { devicesByAddress[it.address] }
        }
    }

    fun startDiscovery(context: Context) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter ?: return

        if (!hasRequiredPermissions(context)) return

        _isDiscovering.value = true
        filter.clear()
        devicesByAddress.clear()
        _devices.value = emptyList()

        if (!receiverRegistered) {
            val intentFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(receiver, intentFilter)
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
