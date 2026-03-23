package com.sunfeld.smsgateway

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
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
            this@BluetoothHidManager.registered = registered
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val current = _connectedDevices.value.toMutableSet()
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> current.add(device)
                BluetoothProfile.STATE_DISCONNECTED -> current.remove(device)
            }
            _connectedDevices.value = current
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                currentSdpSettings?.let { settings ->
                    hidDevice?.registerApp(settings, null, null, { it.run() }, hidCallback)
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                registered = false
            }
        }
    }

    fun register(context: Context, profile: DeviceProfile, customName: String? = null) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = btManager.adapter

        // Save original name and set impersonated name
        try {
            originalAdapterName = adapter.name
            val advertisedName = customName?.takeIf { it.isNotBlank() } ?: profile.sdpName
            adapter.setName(advertisedName)
        } catch (_: SecurityException) { }

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
        val hid = hidDevice ?: return
        if (!registered) return
        hid.connect(device)
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
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = btManager.adapter

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
