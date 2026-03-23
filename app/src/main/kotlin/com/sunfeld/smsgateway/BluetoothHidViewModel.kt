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

    fun startScan(context: Context) {
        if (_isScanning.value == true) return

        _isScanning.value = true
        _isScanningFlow.value = true
        discoveryManager.startDiscovery(context)

        // Observe discovered devices and push to LiveData
        scanObserverJob?.cancel()
        scanObserverJob = viewModelScope.launch {
            discoveryManager.devices.collect { devices ->
                _discoveredDevices.postValue(devices)
            }
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

    // ---- Attack control (uses already-discovered + selected devices) ----

    fun startAttack(context: Context) {
        if (_state.value is HidState.Scanning || _state.value is HidState.Attacking) return

        val profile = selectedProfile.value ?: DeviceProfiles.DEFAULT
        val name = customDeviceName.value
        val targets = selectedTargets.value ?: emptySet()

        if (targets.isEmpty()) return

        // Stop scanning if active — we're switching to HID mode
        if (_isScanning.value == true) {
            stopScan(context)
        }

        _state.value = HidState.Scanning
        _connectedCount.value = 0
        _keystrokesSent.value = 0

        // Register HID profile with selected device profile
        hidManager?.register(context, profile, name)

        // Connect HID to selected targets from already-discovered devices
        val discoveredList = _discoveredDevices.value ?: emptyList()
        discoveredList.filter { targets.contains(it.address) }.forEach { device ->
            hidManager?.connect(device)
        }

        // Transition to Attacking once we attempt connections
        if (targets.isNotEmpty()) {
            _state.value = HidState.Attacking(0)
        }

        // Observe HID connected count
        connectedObserverJob = viewModelScope.launch {
            hidManager?.connectedDevices?.collect { connected ->
                _connectedCount.postValue(connected.size)
                if (_state.value is HidState.Attacking || _state.value is HidState.Scanning) {
                    _state.postValue(HidState.Attacking(connected.size))
                }
            }
        }

        // Observe keystroke counter
        keystrokeObserverJob = viewModelScope.launch {
            hidManager?.keystrokesSent?.collect { count ->
                _keystrokesSent.postValue(count)
            }
        }

        // Attack loop: send keystrokes to connected devices periodically
        attackLoopJob = viewModelScope.launch {
            while (true) {
                delay(cycleDelayMs)
                val currentPayload = payload.value ?: "Hello from your keyboard!\n"
                val connected = hidManager?.connectedDevices?.value ?: emptySet()
                connected.forEach { device ->
                    hidManager?.sendText(device, currentPayload)
                }
            }
        }
    }

    fun stopAttack(context: Context) {
        if (_state.value is HidState.Idle || _state.value is HidState.Stopping) return

        _state.value = HidState.Stopping

        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()

        hidManager?.disconnectAll()
        hidManager?.unregister(context)

        _state.value = HidState.Idle
    }

    fun dismissError() {
        _state.value = HidState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        scanObserverJob?.cancel()
        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()
        _isScanning.value = false
    }
}
