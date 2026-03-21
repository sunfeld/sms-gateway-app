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

/**
 * Represents the current state of the gateway installation process.
 */
sealed class InstallState {
    data object Idle : InstallState()
    data object Installing : InstallState()
    data class Success(val message: String) : InstallState()
    data class Error(val message: String) : InstallState()
}

/**
 * ViewModel that manages the project detail state including
 * the SMS Gateway installation lifecycle.
 *
 * Handles the API call to trigger installation, polls for job
 * status, and exposes observable state for the UI to react to.
 */
class ProjectViewModel : ViewModel() {

    private val _installState = MutableLiveData<InstallState>(InstallState.Idle)
    val installState: LiveData<InstallState> = _installState

    private val _gatewayInstalled = MutableLiveData<Boolean>(false)
    val gatewayInstalled: LiveData<Boolean> = _gatewayInstalled

    var apiClient: GatewayApiClient = GatewayApiClient()

    private var statusPollingJob: Job? = null

    companion object {
        private const val POLL_INTERVAL_MS = 3000L
        private const val MAX_POLL_ATTEMPTS = 60  // 3 minutes max
        private const val STATUS_POLL_INTERVAL_MS = 5000L
        private const val MAX_STATUS_POLL_ATTEMPTS = 36  // 3 minutes max
        const val ACTION_GATEWAY_STATUS_CHANGED = "com.sunfeld.smsgateway.GATEWAY_STATUS_CHANGED"
        const val EXTRA_GATEWAY_ACTIVE = "gateway_active"
    }

    fun setGatewayInstalled(installed: Boolean) {
        _gatewayInstalled.value = installed
    }

    /**
     * Triggers the gateway installation API call.
     * Transitions through IDLE -> INSTALLING -> SUCCESS/ERROR states.
     * On success with a statusUrl, polls the job status until completion.
     */
    fun installGateway() {
        if (_installState.value is InstallState.Installing) return

        _installState.value = InstallState.Installing

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.installGateway()
                }

                if (result.ok && result.statusUrl.isNotEmpty()) {
                    pollJobStatus(result.statusUrl)
                    // After job polling completes, start status polling to
                    // confirm the backend has marked the gateway as ACTIVE
                    if (_gatewayInstalled.value != true) {
                        startStatusPolling()
                    }
                } else if (result.ok) {
                    _installState.value = InstallState.Success(result.message)
                    _gatewayInstalled.value = true
                } else {
                    _installState.value = InstallState.Error("Installation request was not accepted")
                }
            } catch (e: Exception) {
                _installState.value = InstallState.Error(
                    e.message ?: "Failed to start installation"
                )
            }
        }
    }

    private suspend fun pollJobStatus(statusUrl: String) {
        var attempts = 0
        while (attempts < MAX_POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            attempts++

            try {
                val jobStatus = withContext(Dispatchers.IO) {
                    apiClient.getJobStatus(statusUrl)
                }

                when (jobStatus.status) {
                    "completed", "success" -> {
                        _installState.value = InstallState.Success("Gateway installed successfully")
                        _gatewayInstalled.value = true
                        return
                    }
                    "failed", "error" -> {
                        _installState.value = InstallState.Error(
                            jobStatus.error ?: "Installation failed"
                        )
                        return
                    }
                    // "queued", "building" — keep polling
                }
            } catch (e: Exception) {
                _installState.value = InstallState.Error(
                    "Failed to check installation status: ${e.message}"
                )
                return
            }
        }

        _installState.value = InstallState.Error("Installation timed out")
    }

    /**
     * Starts periodic polling of the project status from the backend.
     * Polls every 5 seconds until the gateway is confirmed ACTIVE or
     * the maximum number of attempts is reached.
     * Automatically stops when gateway becomes active.
     */
    fun startStatusPolling() {
        if (statusPollingJob?.isActive == true) return
        if (_gatewayInstalled.value == true) return

        statusPollingJob = viewModelScope.launch {
            var attempts = 0
            while (attempts < MAX_STATUS_POLL_ATTEMPTS) {
                delay(STATUS_POLL_INTERVAL_MS)
                attempts++

                try {
                    val projectStatus = withContext(Dispatchers.IO) {
                        apiClient.getProjectStatus()
                    }

                    if (projectStatus.gatewayActive) {
                        _gatewayInstalled.value = true
                        if (_installState.value is InstallState.Installing) {
                            _installState.value = InstallState.Success("Gateway is now active")
                        }
                        return@launch
                    }
                } catch (_: Exception) {
                    // Silently continue polling on transient errors
                }
            }
        }
    }

    /**
     * Stops the project status polling coroutine.
     */
    fun stopStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = null
    }

    /**
     * Performs a single project status check against the backend.
     * Updates gatewayInstalled if the backend confirms ACTIVE.
     */
    fun refreshProjectStatus() {
        viewModelScope.launch {
            try {
                val projectStatus = withContext(Dispatchers.IO) {
                    apiClient.getProjectStatus()
                }
                if (projectStatus.gatewayActive) {
                    _gatewayInstalled.value = true
                    stopStatusPolling()
                }
            } catch (_: Exception) {
                // Ignore transient errors on single refresh
            }
        }
    }

    /**
     * Resets install state back to Idle (e.g. after dismissing an error).
     */
    fun resetState() {
        _installState.value = InstallState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusPolling()
    }
}
