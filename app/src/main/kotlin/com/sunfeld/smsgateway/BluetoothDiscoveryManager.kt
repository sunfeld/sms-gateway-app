package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.util.Log
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

    companion object {
        private const val TAG = "BtDiscovery"
    }

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    internal val filter = DeviceFilter()

    private var adapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleScanActive = false
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

    // BLE scan callback — catches devices that Classic discovery misses
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { addDevice(it, result.rssi.toShort()) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                result.device?.let { addDevice(it, result.rssi.toShort()) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed with error code: $errorCode")
            // Don't set error — Classic scan may still work
        }
    }

    private fun addDevice(device: BluetoothDevice, rssi: Short = 0) {
        val name = try { device.name } catch (_: SecurityException) { null }
        Log.d(TAG, "ACTION_FOUND: ${device.address} name=$name rssi=$rssi")
        val isNew = filter.addOrUpdate(device.address, name, rssi)
        if (isNew) {
            devicesByAddress[device.address] = device
            _devices.value = filter.getAll().mapNotNull { devicesByAddress[it.address] }
            Log.d(TAG, "New device added. Total: ${_devices.value.size}")
        }
    }

    /** Last error message for UI display. Null when no error. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun startDiscovery(context: Context) {
        _lastError.value = null

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter unavailable — no BT hardware?")
            _lastError.value = "Bluetooth not available on this device"
            return
        }

        if (adapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is OFF")
            _lastError.value = "Turn on Bluetooth to scan for devices"
            return
        }

        if (!hasRequiredPermissions(context)) {
            Log.e(TAG, "Missing required BT permissions")
            _lastError.value = "Bluetooth permissions not granted"
            return
        }

        // On API < 31, Bluetooth Classic discovery requires Location to be ON
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null && !locationManager.isLocationEnabled) {
                Log.e(TAG, "Location services disabled — BT discovery requires location on API < 31")
                _lastError.value = "Enable Location in Settings for Bluetooth scanning"
                return
            }
        }

        _isDiscovering.value = true
        filter.clear()
        devicesByAddress.clear()
        _devices.value = emptyList()

        if (!receiverRegistered) {
            val intentFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            // API 26+ (O): RECEIVER_EXPORTED required for BT intents from
            // "highly privileged apps" (Bluetooth stack is NOT a system broadcast)
            // Using ContextCompat for backward compatibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter)
            }
            receiverRegistered = true
            Log.d(TAG, "BroadcastReceiver registered for ACTION_FOUND + DISCOVERY_FINISHED")
        }

        // Start Classic Bluetooth discovery
        try {
            adapter?.cancelDiscovery()
            val started = adapter?.startDiscovery() ?: false
            Log.d(TAG, "Classic startDiscovery() returned: $started")
            if (!started) {
                Log.w(TAG, "Classic discovery failed — will rely on BLE scan only")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting classic discovery", e)
        }

        // Start BLE scan in parallel — catches modern devices that don't
        // respond to Classic inquiry (most phones, BLE peripherals)
        startBleScan()
    }

    private fun startBleScan() {
        try {
            bleScanner = adapter?.bluetoothLeScanner
            if (bleScanner == null) {
                Log.w(TAG, "BLE scanner unavailable")
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0) // Immediate results
                .build()
            bleScanner?.startScan(null, settings, bleScanCallback)
            bleScanActive = true
            Log.d(TAG, "BLE scan started (LOW_LATENCY mode)")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting BLE scan", e)
        } catch (e: Exception) {
            Log.w(TAG, "BLE scan start failed: ${e.message}")
        }
    }

    private fun stopBleScan() {
        if (bleScanActive) {
            try {
                bleScanner?.stopScan(bleScanCallback)
            } catch (_: Exception) { }
            bleScanActive = false
            Log.d(TAG, "BLE scan stopped")
        }
    }

    fun stopDiscovery(context: Context) {
        _isDiscovering.value = false
        adapter?.cancelDiscovery()
        stopBleScan()

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
