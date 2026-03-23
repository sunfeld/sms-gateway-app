package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.P)
class BluetoothHidManager {

    companion object {
        private const val TAG = "BtHidManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectedDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val connectedDevices: StateFlow<Set<BluetoothDevice>> = _connectedDevices.asStateFlow()

    private val _keystrokesSent = MutableStateFlow(0)
    val keystrokesSent: StateFlow<Int> = _keystrokesSent.asStateFlow()

    private var hidDevice: BluetoothHidDevice? = null
    private var registered = false
    private var originalAdapterName: String? = null
    private var currentSdpSettings: BluetoothHidDeviceAppSdpSettings? = null

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "HID app status changed: registered=$registered device=${pluggedDevice?.address}")
            this@BluetoothHidManager.registered = registered
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val stateName = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "UNKNOWN($state)"
            }
            Log.d(TAG, "HID connection state: ${device.address} -> $stateName")
            val current = _connectedDevices.value.toMutableSet()
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> current.add(device)
                BluetoothProfile.STATE_DISCONNECTED -> current.remove(device)
            }
            _connectedDevices.value = current
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport from ${device.address} type=$type id=$id")
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID_DEVICE profile proxy connected")
                hidDevice = proxy as BluetoothHidDevice
                currentSdpSettings?.let { settings ->
                    Log.d(TAG, "Registering HID app: ${settings.name}")
                    try {
                        hidDevice?.registerApp(settings, null, null, { it.run() }, hidCallback)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException registering HID app", e)
                    }
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.w(TAG, "HID_DEVICE profile proxy disconnected")
                hidDevice = null
                registered = false
            }
        }
    }

    fun register(context: Context, profile: DeviceProfile, customName: String? = null) {
        Log.d(TAG, "register() profile=${profile.displayName} customName=$customName")
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        if (btManager == null) {
            Log.e(TAG, "BluetoothManager unavailable")
            return
        }
        val adapter = btManager.adapter
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter unavailable")
            return
        }

        // Save original name and set impersonated name
        try {
            originalAdapterName = adapter.name
            val advertisedName = customName?.takeIf { it.isNotBlank() } ?: profile.sdpName
            adapter.setName(advertisedName)
            Log.d(TAG, "Adapter name set to: $advertisedName (was: $originalAdapterName)")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException setting adapter name", e)
        }

        // Build SDP settings from profile
        val sdpName = customName?.takeIf { it.isNotBlank() } ?: profile.sdpName
        currentSdpSettings = BluetoothHidDeviceAppSdpSettings(
            sdpName,
            profile.sdpDescription,
            profile.sdpProvider,
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidKeyReport.KEYBOARD_DESCRIPTOR
        )

        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    fun connect(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            Log.w(TAG, "connect(${device.address}) — HID proxy not ready")
            return
        }
        if (!registered) {
            Log.w(TAG, "connect(${device.address}) — HID app not registered yet")
            return
        }
        try {
            Log.d(TAG, "Connecting HID to ${device.address}")
            hid.connect(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to ${device.address}", e)
        }
    }

    fun sendText(device: BluetoothDevice, text: String, delayMs: Long = 30L) {
        val hid = hidDevice ?: return
        scope.launch {
            for (report in HidKeyReport.buildSequence(text)) {
                hid.sendReport(device, 0, report)
                _keystrokesSent.value++
                delay(delayMs)
            }
        }
    }

    fun disconnectAll() {
        val hid = hidDevice ?: return
        _connectedDevices.value.forEach { device ->
            hid.disconnect(device)
        }
    }

    fun unregister(context: Context) {
        disconnectAll()
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            ?: return
        val adapter = btManager.adapter ?: return

        // Restore original adapter name
        originalAdapterName?.let { name ->
            try { adapter.setName(name) } catch (_: SecurityException) { }
        }

        hidDevice?.let { btManager.adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
        registered = false
        currentSdpSettings = null
    }
}
