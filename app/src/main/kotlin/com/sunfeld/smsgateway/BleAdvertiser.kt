package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
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
 * FIXED approach (v3):
 * - Start ONE continuous advertisement — never stop/start cycle
 * - Change adapter name via setName() — Android updates the ad in-place
 * - Connectable=true so devices see it as a real pairable device
 * - Name changes every 2s (enough for scan cycle to pick up each name)
 */
class BleAdvertiser {

    companion object {
        private const val TAG = "BleAdvertiser"
        private val HID_SERVICE_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"))
        // How long each name stays before rotating (must exceed typical BLE scan interval)
        private const val NAME_ROTATE_MS = 2000L
        private const val CRAY_NAME_ROTATE_MS = 1500L
    }

    private val _broadcastCount = MutableStateFlow(0)
    val broadcastCount: StateFlow<Int> = _broadcastCount.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var originalAdapterName: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nameRotateJob: Job? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE advertising started — continuous mode")
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
     * Starts ONE continuous ad, then rotates the adapter name.
     */
    fun start(context: Context, customName: String, crayMode: Boolean = false) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter

        if (adapter == null || adapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth not available")
            return
        }

        try {
            originalAdapterName = adapter?.name
            adapter?.setName(customName)
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

        // Start ONE continuous advertisement — never stop it
        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0) // Indefinite
                .build()

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(HID_SERVICE_UUID)
                .build()

            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            advertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
            return
        }

        // In cray mode, rotate the adapter name — Android updates the ad in-place
        val crayNames = if (crayMode) {
            DeviceProfiles.ALL.map { it.sdpName } + listOf(
                customName, "Free WiFi", "AirDrop - Open Me",
                "Galaxy Buds Pro", "AirPods Pro", "Beats Studio",
                "JBL Flip 6", "Sony WH-1000XM5", "Bose QC Ultra",
                "Ring Doorbell", "Tesla Model 3", "Tile Tracker",
                "Chromecast", "Fire TV Stick", "Nintendo Switch"
            )
        } else null

        val rotateInterval = if (crayMode) CRAY_NAME_ROTATE_MS else NAME_ROTATE_MS

        nameRotateJob = scope.launch {
            while (isActive) {
                delay(rotateInterval)
                val name = crayNames?.random() ?: customName
                try {
                    adapter?.setName(name)
                    _broadcastCount.value++
                } catch (_: SecurityException) { }
            }
        }

        val mode = if (crayMode) "CRAY" else "normal"
        CrashLogger.log(context, TAG, "BLE spam started ($mode, continuous): name='$customName'")
    }

    fun stop(context: Context) {
        _isAdvertising.value = false
        nameRotateJob?.cancel()

        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) { }

        // Restore original name
        originalAdapterName?.let { name ->
            try { adapter?.setName(name) } catch (_: SecurityException) { }
        }

        Log.d(TAG, "BLE spam stopped, name restored")
    }
}
