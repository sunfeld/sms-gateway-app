package com.sunfeld.smsgateway

/**
 * Sealed class representing the UI lifecycle states for gateway installation.
 */
sealed class InstallResult {
    object Idle : InstallResult()
    object Installing : InstallResult()
    object Success : InstallResult() {
        val message: String = "Gateway installed successfully"
    }
    data class Error(val message: String) : InstallResult()
}
