package com.sunfeld.smsgateway

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class StressTestState {
    data object Idle : StressTestState()
    data object Starting : StressTestState()
    data class Running(
        val sessionId: String,
        val packetsSent: Int,
        val targetsActive: Int,
        val remainingSeconds: Int
    ) : StressTestState()
    data object Stopping : StressTestState()
    data class Error(val message: String) : StressTestState()
}

class BluetoothStressTestViewModel : ViewModel() {

    private val _state = MutableLiveData<StressTestState>(StressTestState.Idle)
    val state: LiveData<StressTestState> = _state

    private val _packetsSent = MutableLiveData(0)
    val packetsSent: LiveData<Int> = _packetsSent

    private val _devicesTargeted = MutableLiveData(0)
    val devicesTargeted: LiveData<Int> = _devicesTargeted

    var apiClient: GatewayApiClient = GatewayApiClient()

    private var pollingJob: Job? = null
    private var currentSessionId: String? = null

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
        private const val DEFAULT_DURATION = 60
        private const val DEFAULT_INTENSITY = 3
    }

    fun startStressTest(duration: Int = DEFAULT_DURATION, intensity: Int = DEFAULT_INTENSITY) {
        if (_state.value is StressTestState.Running || _state.value is StressTestState.Starting) return

        _state.value = StressTestState.Starting
        _packetsSent.value = 0
        _devicesTargeted.value = 0

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.startBluetoothDos(duration, intensity)
                }

                currentSessionId = result.sessionId
                _devicesTargeted.value = result.targetsDiscovered
                _state.value = StressTestState.Running(
                    sessionId = result.sessionId,
                    packetsSent = 0,
                    targetsActive = result.targetsDiscovered,
                    remainingSeconds = result.duration
                )

                startPolling(result.sessionId)
            } catch (e: Exception) {
                _state.value = StressTestState.Error(
                    e.message ?: "Failed to start stress test"
                )
            }
        }
    }

    fun stopStressTest() {
        val sessionId = currentSessionId ?: return
        if (_state.value !is StressTestState.Running) return

        _state.value = StressTestState.Stopping

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiClient.stopBluetoothDos(sessionId)
                }
                stopPolling()
                _state.value = StressTestState.Idle
            } catch (e: Exception) {
                _state.value = StressTestState.Error(
                    e.message ?: "Failed to stop stress test"
                )
            }
        }
    }

    fun dismissError() {
        _state.value = StressTestState.Idle
    }

    private fun startPolling(sessionId: String) {
        stopPolling()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                try {
                    val status = withContext(Dispatchers.IO) {
                        apiClient.getBluetoothDosStatus(sessionId)
                    }

                    _packetsSent.value = status.packetsSent
                    _devicesTargeted.value = status.targetsActive

                    when (status.status) {
                        "running" -> {
                            _state.value = StressTestState.Running(
                                sessionId = sessionId,
                                packetsSent = status.packetsSent,
                                targetsActive = status.targetsActive,
                                remainingSeconds = status.remainingSeconds
                            )
                        }
                        "completed", "stopped", "idle" -> {
                            _state.value = StressTestState.Idle
                            currentSessionId = null
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    _state.value = StressTestState.Error(
                        "Lost connection: ${e.message}"
                    )
                    return@launch
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
