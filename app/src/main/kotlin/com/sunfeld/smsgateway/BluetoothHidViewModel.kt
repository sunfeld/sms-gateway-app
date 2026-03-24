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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HidState {
    data object Idle : HidState()
    data object Scanning : HidState()
    data class Attacking(val connectedCount: Int) : HidState()
    data object Stopping : HidState()
    data class Error(val message: String) : HidState()
}

class BluetoothHidViewModel : ViewModel() {

    internal val discoveryManager = BluetoothDiscoveryManager()
    internal val pairingSpammer = BluetoothPairingSpammer()
    internal val bleAdvertiser = BleAdvertiser()
    internal val obexPusher = ObexPusher()
    internal val hidManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        BluetoothHidManager()
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

    // Image data for IMAGE / VCARD_PHOTO types
    private var pendingImageBytes: ByteArray? = null
    private var pendingImageMimeType: String = "image/jpeg"

    private var scanObserverJob: Job? = null
    private var connectedObserverJob: Job? = null
    private var keystrokeObserverJob: Job? = null
    private var attackLoopJob: Job? = null
    private var errorObserverJob: Job? = null

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
                // Pairing spam mode: BLE ads + createBond()
                val name = customDeviceName.value ?: payload.value ?: "Hello"
                val message = currentPayload?.data?.get("name") ?: name
                bleAdvertiser.start(context, message)
                pairingSpammer.start(context, message, targets, discoveredList)

                // Observe pairing + BLE counters
                connectedObserverJob = viewModelScope.launch {
                    pairingSpammer.connectionAttempts.collect { count ->
                        val bleCount = bleAdvertiser.broadcastCount.value
                        _keystrokesSent.postValue(count + bleCount)
                        _state.postValue(HidState.Attacking(targets.size))
                    }
                }
                keystrokeObserverJob = viewModelScope.launch {
                    bleAdvertiser.broadcastCount.collect { bleCount ->
                        val pairingCount = pairingSpammer.connectionAttempts.value
                        _keystrokesSent.postValue(pairingCount + bleCount)
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

    fun stopAttack(context: Context) {
        if (_state.value is HidState.Idle || _state.value is HidState.Stopping) return

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
        _isScanning.value = false
    }
}
