package com.sunfeld.smsgateway

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class InstallResult {
    data object Idle : InstallResult()
    data object Installing : InstallResult()
    data object Success : InstallResult()
    data class Error(val message: String) : InstallResult()
}

class ProjectViewModel : ViewModel() {

    private val _installState = MutableLiveData<InstallResult>(InstallResult.Idle)
    val installState: LiveData<InstallResult> = _installState

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun installGateway(baseUrl: String, projectId: Int) {
        _installState.value = InstallResult.Installing

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/api/projects/$projectId/install-gateway")
                        .post("{}".toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            InstallResult.Success
                        } else {
                            InstallResult.Error("Server returned ${response.code}")
                        }
                    }
                } catch (e: IOException) {
                    InstallResult.Error(e.message ?: "Network error")
                }
            }
            _installState.value = result
        }
    }

    fun resetState() {
        _installState.value = InstallResult.Idle
    }
}
