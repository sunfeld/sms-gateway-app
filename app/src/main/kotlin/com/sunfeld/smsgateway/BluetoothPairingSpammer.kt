package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Precision Bluetooth pairing — creates real connections that show numeric
 * comparison dialogs on target devices.
 *
 * Flow per target:
 * 1. Set adapter name to custom message (this IS the device name shown on target)
 * 2. Call createBond() to initiate Secure Simple Pairing (SSP)
 * 3. Wait for ACTION_PAIRING_REQUEST — confirms key exchange started and
 *    target is showing a pairing dialog with our name + a numeric code
 * 4. Dwell for configurable time so target reads the message
 * 5. Cancel bond and move to next target
 *
 * Devices that don't respond within timeout are skipped — no wasted cycles.
 */
class BluetoothPairingSpammer {

    companion object {
        private const val TAG = "BtPairingConnect"

        // How long to wait for the target to respond with a pairing dialog
        const val DEFAULT_TARGET_TIMEOUT_MS = 10_000L
        // How long to keep the dialog visible after confirmation
        const val DEFAULT_DWELL_MS = 4_000L
        // Settle time after canceling before moving to next target
        private const val SETTLE_MS = 500L

        // Chaotic names rotated per-target in cray mode
        private val CRAY_NAMES = listOf(
            "Magic Keyboard", "Keyboard K380", "AirPods Pro",
            "Galaxy Buds Pro", "Beats Studio", "JBL Flip 6",
            "Sony WH-1000XM5", "Bose QC Ultra", "Free WiFi",
            "AirDrop - Open Me", "Ring Doorbell", "Tesla Model 3",
            "Fire TV Stick", "Chromecast", "Nintendo Switch",
            "Tile Tracker", "MX Keys", "Samsung Keyboard"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Public state ---
    private val _confirmedCount = MutableStateFlow(0)
    val confirmedCount: StateFlow<Int> = _confirmedCount.asStateFlow()

    private val _skippedCount = MutableStateFlow(0)
    val skippedCount: StateFlow<Int> = _skippedCount.asStateFlow()

    private val _totalAttempts = MutableStateFlow(0)
    val totalAttempts: StateFlow<Int> = _totalAttempts.asStateFlow()

    private val _activeTargets = MutableStateFlow(0)
    val activeTargets: StateFlow<Int> = _activeTargets.asStateFlow()

    private val _currentTarget = MutableStateFlow<String?>(null)
    val currentTarget: StateFlow<String?> = _currentTarget.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // --- Internal state ---
    private var adapter: BluetoothAdapter? = null
    private var originalAdapterName: String? = null
    private var customMessage: String = ""
    private var spamJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var isCrayMode = false

    // Configurable timing
    var targetTimeoutMs = DEFAULT_TARGET_TIMEOUT_MS
    var dwellMs = DEFAULT_DWELL_MS

    // Event-driven: the receiver signals this when ACTION_PAIRING_REQUEST fires
    // for the device we're currently targeting
    @Volatile private var currentTargetAddress: String? = null
    private var pairingDeferred: CompletableDeferred<Int>? = null

    /**
     * Start precision pairing against the given targets.
     * Processes one device at a time, waits for confirmed key exchange.
     */
    fun start(
        context: Context,
        customName: String,
        targets: Set<String>,
        discoveredDevices: List<BluetoothDevice>,
        crayMode: Boolean = false
    ) {
        isCrayMode = crayMode
        appContext = context.applicationContext
        _lastError.value = null
        _confirmedCount.value = 0
        _skippedCount.value = 0
        _totalAttempts.value = 0
        _activeTargets.value = targets.size
        _currentTarget.value = null

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter

        if (adapter == null || adapter?.isEnabled != true) {
            _lastError.value = "Bluetooth not available or disabled"
            return
        }

        customMessage = customName

        try {
            originalAdapterName = adapter?.name
            adapter?.setName(customName)
            Log.d(TAG, "Adapter name set to: '$customName' (was: '$originalAdapterName')")
            Thread.sleep(300)
            CrashLogger.log(context, TAG, "Precision pairing started: name='$customName' targets=${targets.size} dwell=${dwellMs}ms timeout=${targetTimeoutMs}ms cray=$crayMode")
        } catch (e: SecurityException) {
            _lastError.value = "Permission denied: cannot set Bluetooth name"
            return
        }

        registerReceiver(context)

        val targetDevices = discoveredDevices.filter { targets.contains(it.address) }
        if (targetDevices.isEmpty()) {
            _lastError.value = "No target devices found"
            return
        }

        spamJob = scope.launch {
            Log.d(TAG, "Precision loop started for ${targetDevices.size} targets")
            while (isActive) {
                for (device in targetDevices) {
                    if (!isActive) break
                    try {
                        connectToTarget(device)
                    } catch (e: Exception) {
                        Log.w(TAG, "Target ${device.address} error: ${e.message}")
                    }
                    _totalAttempts.value++
                    delay(SETTLE_MS)
                }
            }
        }
    }

    /**
     * Core per-target flow: bond → wait for pairing dialog → dwell → cancel.
     */
    private suspend fun connectToTarget(device: BluetoothDevice) {
        val address = device.address
        _currentTarget.value = address

        try {
            // Set name before each attempt — in cray mode, rotate through known brands
            val nameToUse = if (isCrayMode) CRAY_NAMES.random() else customMessage
            try {
                adapter?.setName(nameToUse)
            } catch (_: SecurityException) { }

            // Clear any existing bond state — must be BOND_NONE for dialog to show
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    Log.d(TAG, "[$address] Removing existing bond")
                    removeBond(device)
                    delay(300)
                }
                BluetoothDevice.BOND_BONDING -> {
                    cancelBond(device)
                    delay(200)
                }
            }

            // Set up the deferred BEFORE calling createBond
            currentTargetAddress = address
            pairingDeferred = CompletableDeferred()

            // Initiate SSP pairing — this sends the pairing request over the air
            Log.d(TAG, "[$address] Initiating pairing as '$nameToUse'")
            val bondStarted = device.createBond()

            if (!bondStarted) {
                Log.w(TAG, "[$address] createBond() returned false — skipping")
                _skippedCount.value++
                return
            }

            // Wait for ACTION_PAIRING_REQUEST — proves the target is showing the dialog
            val pairingVariant = try {
                withTimeout(targetTimeoutMs) {
                    pairingDeferred!!.await()
                }
            } catch (_: TimeoutCancellationException) {
                -1 // timeout
            }

            if (pairingVariant >= 0) {
                // Confirmed — target is showing the pairing dialog with our name
                _confirmedCount.value++
                val variantName = when (pairingVariant) {
                    0 -> "PIN"
                    1 -> "PASSKEY"
                    2 -> "NUMERIC_COMPARISON"
                    3 -> "CONSENT"
                    else -> "VARIANT_$pairingVariant"
                }
                Log.d(TAG, "[$address] CONFIRMED — dialog showing ($variantName), dwelling ${dwellMs}ms")

                // Dwell — let them read the name and see the numeric code
                delay(dwellMs)
            } else {
                // No response within timeout — device didn't engage
                _skippedCount.value++
                Log.d(TAG, "[$address] No pairing response within ${targetTimeoutMs}ms — skipped")
            }

            // Cancel and clean up — we never actually complete the pairing
            cancelBond(device)
            // Brief extra settle to let the cancel propagate
            delay(100)

        } catch (e: SecurityException) {
            Log.w(TAG, "[$address] SecurityException", e)
            _skippedCount.value++
        } catch (e: Exception) {
            Log.w(TAG, "[$address] Error: ${e.message}", e)
            _skippedCount.value++
        } finally {
            currentTargetAddress = null
            pairingDeferred = null
            _currentTarget.value = null
        }
    }

    private fun cancelBond(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("cancelBondProcess")
            method.invoke(device)
            Log.d(TAG, "Cancelled bond with ${device.address}")
        } catch (_: Exception) { }
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
        } catch (e: Exception) {
            Log.w(TAG, "removeBond failed for ${device.address}: ${e.message}")
        }
    }

    fun stop(context: Context) {
        Log.d(TAG, "Stopping precision pairing (confirmed=${_confirmedCount.value} skipped=${_skippedCount.value})")
        spamJob?.cancel()
        spamJob = null
        currentTargetAddress = null
        pairingDeferred?.cancel()
        pairingDeferred = null
        _currentTarget.value = null

        originalAdapterName?.let { name ->
            try { adapter?.setName(name) } catch (_: SecurityException) { }
        }

        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { }
        }
        receiver = null
    }

    /**
     * Register for both ACTION_PAIRING_REQUEST and ACTION_BOND_STATE_CHANGED.
     * ACTION_PAIRING_REQUEST is the gold signal — it means both devices are in
     * the key exchange phase and the target is showing a dialog.
     */
    private fun registerReceiver(context: Context) {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                when (intent.action) {
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                        Log.d(TAG, "ACTION_PAIRING_REQUEST from ${device?.address} variant=$variant")

                        // Signal the waiting coroutine if this is our current target
                        if (device?.address == currentTargetAddress) {
                            pairingDeferred?.complete(variant)
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val stateName = when (state) {
                            BluetoothDevice.BOND_NONE -> "NONE"
                            BluetoothDevice.BOND_BONDING -> "BONDING"
                            BluetoothDevice.BOND_BONDED -> "BONDED"
                            else -> "UNKNOWN($state)"
                        }
                        Log.d(TAG, "BOND_STATE: ${device?.address} $prev -> $stateName")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver!!,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register receiver: ${e.message}")
        }
    }

    // Legacy compat: total connection attempts = confirmed + skipped
    val connectionAttempts: StateFlow<Int> = _totalAttempts
}
