package com.sunfeld.smsgateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel that manages the project detail state including
 * the SMS Gateway installation lifecycle.
 *
 * Handles the API call to trigger installation, polls for job
 * status, and exposes observable state for the UI to react to.
 */
class ProjectViewModel : ViewModel() {

    private val _installState = MutableStateFlow<InstallResult>(InstallResult.Idle)
    val installState: StateFlow<InstallResult> = _installState.asStateFlow()

    private val _gatewayInstalled = MutableStateFlow(false)
    val gatewayInstalled: StateFlow<Boolean> = _gatewayInstalled.asStateFlow()

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
        if (_installState.value is InstallResult.Installing) return

        _installState.value = InstallResult.Installing

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
                    _installState.value = InstallResult.Success
                    _gatewayInstalled.value = true
                } else {
                    _installState.value = InstallResult.Error("Installation request was not accepted")
                }
            } catch (e: Exception) {
                _installState.value = InstallResult.Error(
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
                        _installState.value = InstallResult.Success
                        _gatewayInstalled.value = true
                        return
                    }
                    "failed", "error" -> {
                        _installState.value = InstallResult.Error(
                            jobStatus.error ?: "Installation failed"
                        )
                        return
                    }
                    // "queued", "building" — keep polling
                }
            } catch (e: Exception) {
                _installState.value = InstallResult.Error(
                    "Failed to check installation status: ${e.message}"
                )
                return
            }
        }

        _installState.value = InstallResult.Error("Installation timed out")
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
                        if (_installState.value is InstallResult.Installing) {
                            _installState.value = InstallResult.Success
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
        _installState.value = InstallResult.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusPolling()
    }
}
