package com.sunfeld.smsgateway

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

/**
 * Manages Bluetooth HID Device profile — makes this phone act as a Bluetooth keyboard.
 *
 * Flow:
 *  1. [register] — acquires HID proxy and registers keyboard SDP record.
 *  2. [connect] — initiates HID connection to a target [BluetoothDevice].
 *  3. [sendText] — sends a string as HID keyboard reports to a connected device.
 *  4. [disconnectAll] / [unregister] — teardown.
 *
 * All operations are safe to call from any thread.
 */
@RequiresApi(Build.VERSION_CODES.P)
class BluetoothHidManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectedDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val connectedDevices: StateFlow<Set<BluetoothDevice>> = _connectedDevices.asStateFlow()

    private val _keystrokesSent = MutableStateFlow(0)
    val keystrokesSent: StateFlow<Int> = _keystrokesSent.asStateFlow()

    private var hidDevice: BluetoothHidDevice? = null
    private var registered = false

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "BT Keyboard",
        "HID Keyboard",
        "Sunfeld",
        BluetoothHidDevice.SUBCLASS1_KEYBOARD,
        HidKeyReport.KEYBOARD_DESCRIPTOR
    )

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
                hidDevice?.registerApp(
                    sdpSettings,
                    null,
                    null,
                    { it.run() },
                    hidCallback
                )
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                registered = false
            }
        }
    }

    /**
     * Acquires the HID Device profile proxy. Must be called before [connect].
     */
    fun register(context: Context) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        btManager.adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    /**
     * Initiates a HID connection to [device], making us appear as a keyboard to it.
     */
    fun connect(device: BluetoothDevice) {
        val hid = hidDevice ?: return
        if (!registered) return
        hid.connect(device)
    }

    /**
     * Sends [text] as HID keyboard reports to [device].
     * Each character is sent as a key-press + key-release pair with [delayMs] between them.
     */
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

    /**
     * Disconnects from all currently connected devices.
     */
    fun disconnectAll() {
        val hid = hidDevice ?: return
        _connectedDevices.value.forEach { device ->
            hid.disconnect(device)
        }
    }

    /**
     * Releases the HID profile proxy. Call when done.
     */
    fun unregister(context: Context) {
        disconnectAll()
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        hidDevice?.let { btManager.adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
        registered = false
    }
}
