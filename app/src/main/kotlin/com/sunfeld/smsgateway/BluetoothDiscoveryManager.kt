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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Bluetooth device discovery using BOTH Classic and BLE scanning.
 *
 * Every operation is wrapped in try-catch to prevent crashes.
 * Errors are surfaced via [lastError] StateFlow for UI display.
 * Comprehensive logging via [Log] and [CrashLogger] for remote diagnosis.
 */
class BluetoothDiscoveryManager {

    companion object {
        private const val TAG = "BtDiscovery"
    }

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    internal val filter = DeviceFilter()

    private var adapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleScanActive = false
    private var receiverRegistered = false
    private val devicesByAddress = mutableMapOf<String, BluetoothDevice>()

    // Context reference for CrashLogger (set in startDiscovery)
    private var appContext: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                        } catch (e: Exception) {
                            logError("getParcelableExtra failed", e)
                            null
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)
                        device?.let { addDevice(it, rssi) }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Classic discovery finished, active=${_isDiscovering.value}")
                        if (_isDiscovering.value) {
                            try {
                                adapter?.startDiscovery()
                            } catch (e: SecurityException) {
                                logError("SecurityException restarting discovery", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logError("BroadcastReceiver.onReceive crashed", e)
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                result.device?.let { addDevice(it, result.rssi.toShort()) }
            } catch (e: Exception) {
                logError("BLE onScanResult crashed", e)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            try {
                results.forEach { result ->
                    result.device?.let { addDevice(it, result.rssi.toShort()) }
                }
            } catch (e: Exception) {
                logError("BLE onBatchScanResults crashed", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorName = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                else -> "UNKNOWN($errorCode)"
            }
            Log.w(TAG, "BLE scan failed: $errorName")
            logCrash("BLE scan failed: $errorName")
        }
    }

    private fun addDevice(device: BluetoothDevice, rssi: Short = 0) {
        try {
            val address = device.address ?: return
            val name = try { device.name } catch (_: SecurityException) { null }
            Log.d(TAG, "Device found: $address name=$name rssi=$rssi")
            val isNew = filter.addOrUpdate(address, name, rssi)
            if (isNew) {
                devicesByAddress[address] = device
                _devices.value = filter.getAll().mapNotNull { devicesByAddress[it.address] }
                Log.d(TAG, "New device added. Total: ${_devices.value.size}")
            }
        } catch (e: Exception) {
            logError("addDevice crashed", e)
        }
    }

    /**
     * Start device discovery. Every step is individually try-caught.
     * If one method fails, we continue with the others.
     */
    fun startDiscovery(context: Context) {
        appContext = context.applicationContext
        _lastError.value = null
        logCrash("startDiscovery() called — SDK ${Build.VERSION.SDK_INT}, ${Build.MODEL}")

        // Step 1: Get Bluetooth adapter
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            adapter = btManager?.adapter
        } catch (e: Exception) {
            logError("Failed to get BluetoothAdapter", e)
            _lastError.value = "Bluetooth not available: ${e.message}"
            return
        }

        if (adapter == null) {
            logCrash("BluetoothAdapter is null — no BT hardware?")
            _lastError.value = "Bluetooth not available on this device"
            return
        }

        // Step 2: Check Bluetooth is enabled
        try {
            if (adapter?.isEnabled != true) {
                logCrash("Bluetooth is disabled")
                _lastError.value = "Turn on Bluetooth to scan for devices"
                return
            }
        } catch (e: Exception) {
            logError("Exception checking BT enabled", e)
        }

        // Step 3: Check permissions
        try {
            if (!hasRequiredPermissions(context)) {
                logCrash("Missing BT permissions: ${BluetoothPermissionManager.getMissingScanPermissions(context)}")
                _lastError.value = "Bluetooth permissions not granted"
                return
            }
        } catch (e: Exception) {
            logError("Exception checking permissions", e)
        }

        // Step 4: Check location for API < 31
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null && !locationManager.isLocationEnabled) {
                    logCrash("Location disabled on API ${Build.VERSION.SDK_INT}")
                    _lastError.value = "Enable Location in Settings for Bluetooth scanning"
                    return
                }
            } catch (e: Exception) {
                logError("Exception checking location", e)
            }
        }

        _isDiscovering.value = true
        filter.clear()
        devicesByAddress.clear()
        _devices.value = emptyList()

        // Step 5: Register BroadcastReceiver
        if (!receiverRegistered) {
            try {
                val intentFilter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                // ContextCompat safely handles RECEIVER_EXPORTED across all API levels
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                receiverRegistered = true
                logCrash("BroadcastReceiver registered OK")
            } catch (e: Exception) {
                logError("CRITICAL: registerReceiver FAILED", e)
                _lastError.value = "Failed to register Bluetooth receiver: ${e.message}"
                _isDiscovering.value = false
                return
            }
        }

        // Step 6: Start Classic Bluetooth discovery
        try {
            adapter?.cancelDiscovery()
            val started = adapter?.startDiscovery() ?: false
            logCrash("Classic startDiscovery() returned: $started")
            if (!started) {
                logCrash("Classic discovery failed to start — will try BLE only")
            }
        } catch (e: SecurityException) {
            logError("SecurityException on startDiscovery", e)
        } catch (e: Exception) {
            logError("Exception on startDiscovery", e)
        }

        // Step 7: Start BLE scan
        startBleScan()
    }

    private fun startBleScan() {
        try {
            bleScanner = adapter?.bluetoothLeScanner
            if (bleScanner == null) {
                logCrash("BLE scanner is null — adapter?.bluetoothLeScanner returned null")
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()
            bleScanner?.startScan(null, settings, bleScanCallback)
            bleScanActive = true
            logCrash("BLE scan started OK (LOW_LATENCY)")
        } catch (e: SecurityException) {
            logError("SecurityException starting BLE scan", e)
        } catch (e: IllegalStateException) {
            logError("IllegalStateException starting BLE scan (adapter off?)", e)
        } catch (e: Exception) {
            logError("BLE scan start failed", e)
        }
    }

    private fun stopBleScan() {
        if (bleScanActive) {
            try {
                bleScanner?.stopScan(bleScanCallback)
            } catch (_: Exception) { }
            bleScanActive = false
        }
    }

    fun stopDiscovery(context: Context) {
        _isDiscovering.value = false
        try { adapter?.cancelDiscovery() } catch (_: Exception) { }
        stopBleScan()

        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) { }
            receiverRegistered = false
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean =
        BluetoothPermissionManager.hasScanPermissions(context)

    private fun logError(message: String, e: Exception) {
        Log.e(TAG, message, e)
        appContext?.let { CrashLogger.log(it, TAG, "$message: ${e.message}", e) }
    }

    private fun logCrash(message: String) {
        Log.d(TAG, message)
        appContext?.let { CrashLogger.log(it, TAG, message) }
    }
}
