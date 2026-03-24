package com.sunfeld.smsgateway

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
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
    data object Stopping : HidState()
    data class Error(val message: String) : HidState()
}

class BluetoothHidViewModel : ViewModel() {

    internal val discoveryManager = BluetoothDiscoveryManager()
    internal val pairingSpammer = BluetoothPairingSpammer()
    internal val bleAdvertiser = BleAdvertiser()
    internal val hidManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        BluetoothHidManager()
    } else null

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

    // User-configurable fields
    val selectedProfile = MutableLiveData<DeviceProfile>(DeviceProfiles.DEFAULT)
    val customDeviceName = MutableLiveData<String>(DeviceProfiles.DEFAULT.sdpName)
    val selectedTargets = MutableLiveData<Set<String>>(emptySet())
    val payload = MutableLiveData("Hello from your keyboard!\n")

    private var scanObserverJob: Job? = null
    private var connectedObserverJob: Job? = null
    private var keystrokeObserverJob: Job? = null
    private var attackLoopJob: Job? = null

    private val cycleDelayMs = 5_000L

    // ---- Independent scan control (decoupled from attack) ----

    private var errorObserverJob: Job? = null

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

    // ---- Attack control: dual-mode BLE advertisement + pairing request spam ----
    // 1. BLE: Broadcasts custom device name via BLE advertisements (Flipper Zero style)
    // 2. Classic: createBond() sends pairing requests showing custom name in dialog

    fun startAttack(context: Context) {
        if (_state.value is HidState.Scanning || _state.value is HidState.Attacking) return

        val name = customDeviceName.value ?: payload.value ?: "Hello"
        val targets = selectedTargets.value ?: emptySet()

        if (targets.isEmpty()) return

        // Stop scanning if active
        if (_isScanning.value == true) {
            stopScan(context)
        }

        _state.value = HidState.Attacking(0)
        _connectedCount.value = targets.size
        _keystrokesSent.value = 0

        try {
            // Start BLE advertisement spam (broadcasts custom name to ALL nearby devices)
            bleAdvertiser.start(context, name)

            // Start Classic pairing request spam to selected targets
            val discoveredList = _discoveredDevices.value ?: emptyList()
            pairingSpammer.start(context, name, targets, discoveredList)

            // Observe pairing attempt counter
            connectedObserverJob = viewModelScope.launch {
                pairingSpammer.connectionAttempts.collect { count ->
                    val bleCount = bleAdvertiser.broadcastCount.value
                    _keystrokesSent.postValue(count + bleCount)
                    _state.postValue(HidState.Attacking(targets.size))
                }
            }

            // Observe BLE broadcast counter
            keystrokeObserverJob = viewModelScope.launch {
                bleAdvertiser.broadcastCount.collect { bleCount ->
                    val pairingCount = pairingSpammer.connectionAttempts.value
                    _keystrokesSent.postValue(pairingCount + bleCount)
                }
            }

            // Observe errors from pairing spammer
            errorObserverJob = viewModelScope.launch {
                pairingSpammer.lastError.collect { error ->
                    if (error != null) {
                        _state.postValue(HidState.Error(error))
                    }
                }
            }

            CrashLogger.log(context, "BtAttack", "Dual-mode attack started: BLE ads + pairing spam, name='$name' targets=${targets.size}")
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
