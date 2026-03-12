package com.sunfeld.smsgateway

import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Helper class that manages runtime permission requests using the
 * AndroidX Activity Result API. Registers an [ActivityResultLauncher]
 * for requesting multiple permissions at once and reports grant results
 * through a callback.
 *
 * Must be instantiated before the activity reaches the STARTED state
 * (i.e. during or before [ComponentActivity.onCreate]).
 */
class PermissionManager(
    activity: ComponentActivity,
    private val onResult: (allGranted: Boolean, results: Map<String, Boolean>) -> Unit
) {

    companion object {
        private const val TAG = "PermissionManager"

        /** The set of runtime permissions this app requires. */
        val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val context = activity.applicationContext

    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            val allGranted = grants.values.all { it }
            if (allGranted) {
                Log.d(TAG, "All permissions granted")
            } else {
                val denied = grants.filterValues { !it }.keys
                Log.w(TAG, "Permissions denied: $denied")
            }
            onResult(allGranted, grants)
        }

    /**
     * Returns `true` if every permission in [REQUIRED_PERMISSIONS] has
     * already been granted.
     */
    fun hasAllPermissions(): Boolean =
        REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * Launches the system permission dialog for any permissions that
     * have not yet been granted. If all permissions are already granted
     * the callback fires immediately with `allGranted = true`.
     */
    fun requestPermissions() {
        if (hasAllPermissions()) {
            Log.d(TAG, "All permissions already granted")
            val results = REQUIRED_PERMISSIONS.associateWith { true }
            onResult(true, results)
            return
        }
        launcher.launch(REQUIRED_PERMISSIONS)
    }
}
