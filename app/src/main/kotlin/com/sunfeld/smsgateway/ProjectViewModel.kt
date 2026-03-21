package com.sunfeld.smsgateway

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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

    companion object {
        private const val POLL_INTERVAL_MS = 3000L
        private const val MAX_POLL_ATTEMPTS = 60  // 3 minutes max
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
     * Resets install state back to Idle (e.g. after dismissing an error).
     */
    fun resetState() {
        _installState.value = InstallState.Idle
    }
}
