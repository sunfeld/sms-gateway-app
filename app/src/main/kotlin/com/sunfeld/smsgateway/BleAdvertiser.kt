package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE Advertisement Spam — broadcasts custom device name via BLE advertisements.
 *
 * How it works:
 * 1. Sets BluetoothAdapter name to the custom message
 * 2. Starts BLE advertising with setIncludeDeviceName(true)
 * 3. Every nearby phone doing BLE scanning sees the custom name
 * 4. Cycles the advertiser on/off to ensure fresh broadcasts
 *
 * This is similar to Flipper Zero BLE spam but from an Android phone.
 * The device name IS the message — it appears in Bluetooth settings
 * and pairing notifications on nearby devices.
 */
class BleAdvertiser {

    companion object {
        private const val TAG = "BleAdvertiser"
        // Random HID service UUID for keyboard appearance
        private val HID_SERVICE_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"))
        private const val CYCLE_INTERVAL_MS = 3000L
        private const val CRAY_CYCLE_INTERVAL_MS = 500L
    }

    private val _broadcastCount = MutableStateFlow(0)
    val broadcastCount: StateFlow<Int> = _broadcastCount.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var originalAdapterName: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cycleJob: Job? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE advertising started successfully")
            _broadcastCount.value++
        }

        override fun onStartFailure(errorCode: Int) {
            val errorName = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                else -> "UNKNOWN($errorCode)"
            }
            Log.e(TAG, "BLE advertising failed: $errorName")
        }
    }

    /**
     * Start BLE advertisement spam with custom device name.
     *
     * @param context Android context
     * @param customName The message to broadcast as the BLE device name
     * @param crayMode If true, rapidly rotates through random device profile names
     */
    fun start(context: Context, customName: String, crayMode: Boolean = false) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter

        if (adapter == null || adapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth not available")
            return
        }

        // Set adapter name FIRST — this becomes the BLE advertised name
        try {
            originalAdapterName = adapter?.name
            adapter?.setName(customName)
            Log.d(TAG, "Adapter name set to: '$customName' (was: '$originalAdapterName')")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set adapter name", e)
            return
        }

        advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE advertiser not available on this device")
            return
        }

        _isAdvertising.value = true
        _broadcastCount.value = 0

        val interval = if (crayMode) CRAY_CYCLE_INTERVAL_MS else CYCLE_INTERVAL_MS
        val crayNames = if (crayMode) {
            DeviceProfiles.ALL.map { it.sdpName } + listOf(
                customName, "Free WiFi", "AirDrop - Open Me",
                "Galaxy Buds Pro", "AirPods Pro", "Beats Studio"
            )
        } else null

        // Start cycling advertisements to keep them fresh
        cycleJob = scope.launch {
            while (isActive) {
                val name = if (crayNames != null) {
                    crayNames.random().also {
                        try { adapter?.setName(it) } catch (_: SecurityException) { }
                    }
                } else customName
                startAdvertising(name)
                delay(interval)
                stopAdvertisingInternal()
                delay(if (crayMode) 50L else 200L)
            }
        }

        val mode = if (crayMode) "CRAY" else "normal"
        CrashLogger.log(context, TAG, "BLE spam started ($mode): name='$customName'")
    }

    private fun startAdvertising(customName: String) {
        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true) // Must be connectable for pairing dialogs
                .setTimeout(0) // Advertise indefinitely
                .build()

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(true) // Include the custom adapter name
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(HID_SERVICE_UUID) // Appear as HID device
                .build()

            // Scan response with additional data
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            advertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
            Log.d(TAG, "BLE advertisement cycle started")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE advertising", e)
        } catch (e: Exception) {
            Log.e(TAG, "BLE advertising failed", e)
        }
    }

    private fun stopAdvertisingInternal() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) { }
    }

    fun stop(context: Context) {
        _isAdvertising.value = false
        cycleJob?.cancel()
        stopAdvertisingInternal()

        // Restore original name
        originalAdapterName?.let { name ->
            try { adapter?.setName(name) } catch (_: SecurityException) { }
        }

        Log.d(TAG, "BLE spam stopped, name restored")
    }
}
