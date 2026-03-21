package com.sunfeld.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * BroadcastReceiver that listens for gateway status change broadcasts.
 * When the backend confirms the gateway is ACTIVE, this receiver
 * notifies the registered callback so the UI can refresh.
 *
 * Usage:
 *   val receiver = GatewayStatusReceiver { isActive ->
 *       if (isActive) viewModel.setGatewayInstalled(true)
 *   }
 *   receiver.register(context)
 *   // ... later ...
 *   receiver.unregister(context)
 *
 * To trigger:
 *   GatewayStatusReceiver.sendStatusBroadcast(context, gatewayActive = true)
 */
class GatewayStatusReceiver(
    private val onStatusChanged: (Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ProjectViewModel.ACTION_GATEWAY_STATUS_CHANGED) {
            val isActive = intent.getBooleanExtra(
                ProjectViewModel.EXTRA_GATEWAY_ACTIVE, false
            )
            onStatusChanged(isActive)
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter(ProjectViewModel.ACTION_GATEWAY_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(this, filter)
        }
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
    }

    companion object {
        /**
         * Sends a gateway status changed broadcast.
         * Call this when the backend confirms the gateway is ACTIVE.
         */
        fun sendStatusBroadcast(context: Context, gatewayActive: Boolean) {
            val intent = Intent(ProjectViewModel.ACTION_GATEWAY_STATUS_CHANGED).apply {
                setPackage(context.packageName)
                putExtra(ProjectViewModel.EXTRA_GATEWAY_ACTIVE, gatewayActive)
            }
            context.sendBroadcast(intent)
        }
    }
}
