package com.sunfeld.smsgateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Centralized pre-scan check utility for Bluetooth runtime permissions.
 *
 * Handles the API-level split between Android 12+ (S / API 31) which requires
 * [Manifest.permission.BLUETOOTH_SCAN], [Manifest.permission.BLUETOOTH_CONNECT],
 * and [Manifest.permission.BLUETOOTH_ADVERTISE], versus older APIs that require
 * [Manifest.permission.ACCESS_FINE_LOCATION] for discovery.
 *
 * **Static helpers** ([hasScanPermissions], [getMissingScanPermissions], etc.) can be
 * called from any component (e.g. [BluetoothScanner]) to guard operations.
 *
 * **Instance methods** ([requestScanPermissions], [requestAllBluetoothPermissions])
 * use the AndroidX Activity Result API to prompt the user and execute a deferred
 * action once permissions are granted.
 *
 * Must be instantiated before the activity reaches the STARTED state
 * (i.e. during or before [ComponentActivity.onCreate]).
 */
class BluetoothPermissionManager(
    activity: ComponentActivity,
    private val onResult: (allGranted: Boolean, results: Map<String, Boolean>) -> Unit = { _, _ -> }
) {

    companion object {
        private const val TAG = "BtPermissionManager"

        /**
         * Returns the permissions required to perform Bluetooth scanning.
         * On API 31+ this is BLUETOOTH_SCAN + BLUETOOTH_CONNECT.
         * On older APIs this is ACCESS_FINE_LOCATION.
         */
        fun getScanPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        /**
         * Returns all permissions required for full Bluetooth HID operation
         * (scanning + connecting + advertising).
         */
        fun getAllBluetoothPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        /** Returns `true` if all scan-related permissions are granted. */
        fun hasScanPermissions(context: Context): Boolean =
            getScanPermissions().all { isGranted(context, it) }

        /** Returns `true` if all Bluetooth permissions (scan + HID) are granted. */
        fun hasAllBluetoothPermissions(context: Context): Boolean =
            getAllBluetoothPermissions().all { isGranted(context, it) }

        /** Returns the subset of scan permissions that have not yet been granted. */
        fun getMissingScanPermissions(context: Context): List<String> =
            getScanPermissions().filter { !isGranted(context, it) }

        /** Returns the subset of all BT permissions that have not yet been granted. */
        fun getMissingBluetoothPermissions(context: Context): List<String> =
            getAllBluetoothPermissions().filter { !isGranted(context, it) }

        private fun isGranted(context: Context, permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private var pendingAction: (() -> Unit)? = null

    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            val allGranted = grants.values.all { it }
            if (allGranted) {
                Log.d(TAG, "All requested BT permissions granted")
                pendingAction?.invoke()
            } else {
                val denied = grants.filterValues { !it }.keys
                Log.w(TAG, "BT permissions denied: $denied")
                Toast.makeText(
                    activity,
                    "Bluetooth permissions required",
                    Toast.LENGTH_LONG
                ).show()
            }
            pendingAction = null
            onResult(allGranted, grants)
        }

    private val context: Context = activity.applicationContext

    /**
     * Checks scan permissions and either executes [action] immediately if all
     * are granted, or requests the missing ones and defers [action] until granted.
     */
    fun requestScanPermissions(action: () -> Unit) {
        requestAndDo(getScanPermissions(), action)
    }

    /**
     * Checks all BT permissions (scan + HID) and either executes [action]
     * immediately or requests the missing ones first.
     */
    fun requestAllBluetoothPermissions(action: () -> Unit) {
        requestAndDo(getAllBluetoothPermissions(), action)
    }

    private fun requestAndDo(permissions: Array<String>, action: () -> Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            launcher.launch(missing.toTypedArray())
        }
    }
}
