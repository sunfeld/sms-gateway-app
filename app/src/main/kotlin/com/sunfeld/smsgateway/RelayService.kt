package com.sunfeld.smsgateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Foreground service that keeps the SMS relay WebSocket connection alive
 * in the background. Shows a persistent notification with connection status.
 *
 * Auto-starts on boot via [BootReceiver] and on app install via [MainActivity].
 * Auto-pairs with the relay server if keys haven't been exchanged yet.
 */
class RelayService : Service() {

    companion object {
        private const val TAG = "RelayService"
        private const val CHANNEL_ID = "sms_relay_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RelayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RelayService::class.java))
        }
    }

    private var relayClient: RelayClient? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statusObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RelayService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RelayService onStartCommand")

        // Auto-pair if not yet paired, then connect
        serviceScope.launch {
            autoPairIfNeeded()
            startRelayConnection()
        }

        return START_STICKY // Restart if killed
    }

    private suspend fun autoPairIfNeeded() {
        if (CryptoManager.isPaired(this)) {
            Log.d(TAG, "Already paired with relay server")
            return
        }

        // Generate keypair if needed
        if (!CryptoManager.hasKeyPair(this)) {
            CryptoManager.generateKeyPair(this)
            Log.d(TAG, "Generated new Ed25519 keypair")
        }

        val phonePubKey = CryptoManager.getPublicKeyBase64(this) ?: return
        val baseUrl = Config.RELAY_BASE_URL

        Log.d(TAG, "Auto-pairing with $baseUrl...")
        updateNotification("Pairing with server...")

        try {
            val client = OkHttpClient()
            val body = JSONObject().put("publicKey", phonePubKey).toString()
            val request = Request.Builder()
                .url("$baseUrl/api/register-phone")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val result = JSONObject(response.body!!.string())
                    val serverPubKey = result.getString("serverPublicKey")
                    CryptoManager.setServerPublicKey(this@RelayService, serverPubKey)
                    Log.d(TAG, "Auto-paired successfully. Server fingerprint: ${CryptoManager.getFingerprint(serverPubKey)}")
                } else {
                    Log.e(TAG, "Auto-pair failed: ${response.code} ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-pair error: ${e.message}", e)
        }
    }

    private fun startRelayConnection() {
        if (!CryptoManager.isPaired(this)) {
            Log.w(TAG, "Not paired — cannot connect. Will retry on next start.")
            updateNotification("Not paired with server")
            return
        }

        relayClient?.stop()
        relayClient = RelayClient(this).also { client ->
            // Observe connection state and update notification
            statusObserverJob?.cancel()
            statusObserverJob = serviceScope.launch {
                client.state.collectLatest { state ->
                    val text = when (state) {
                        is RelayClient.ConnectionState.Disconnected -> "Disconnected"
                        is RelayClient.ConnectionState.Connecting -> "Connecting..."
                        is RelayClient.ConnectionState.Authenticating -> "Authenticating..."
                        is RelayClient.ConnectionState.Connected -> "Connected — ready to relay SMS"
                        is RelayClient.ConnectionState.Error -> "Error: ${state.message}"
                    }
                    Log.d(TAG, "Relay state: $text")
                    updateNotification(text)
                }
            }
            client.start()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Gateway Relay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows SMS relay connection status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = Intent(this, GatewaySettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        Log.d(TAG, "RelayService destroyed")
        statusObserverJob?.cancel()
        relayClient?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
