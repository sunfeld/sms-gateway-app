package com.sunfeld.smsgateway

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject

/**
 * WebSocket client for the SMS relay server.
 * Connects to wss://sms.sunfeld.nl/ws, authenticates with Ed25519,
 * receives signed SMS commands, sends them via SmsService, and
 * returns signed delivery receipts.
 */
class RelayClient(private val context: Context) {

    companion object {
        private const val TAG = "RelayClient"
        private const val RECONNECT_BASE_MS = 2000L
        private const val RECONNECT_MAX_MS = 60000L
        private const val PING_INTERVAL_MS = 30000L
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Authenticating : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _messageLog = MutableStateFlow<List<String>>(emptyList())
    val messageLog: StateFlow<List<String>> = _messageLog.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(30))
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectAttempt = 0
    private var running = false

    private val smsService = SmsService(context)

    fun start() {
        if (running) return
        running = true
        reconnectAttempt = 0
        connect()
    }

    fun stop() {
        running = false
        reconnectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "User stopped")
        webSocket = null
        _state.value = ConnectionState.Disconnected
    }

    private fun connect() {
        if (!running) return
        if (!CryptoManager.isPaired(context)) {
            _state.value = ConnectionState.Error("Not paired — pair with server first")
            return
        }

        val url = CryptoManager.getRelayUrl(context)
        _state.value = ConnectionState.Connecting
        addLog("Connecting to $url...")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnectionState.Authenticating
                addLog("Connected, authenticating...")
                sendAuthHandshake(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                addLog("Disconnected: $reason")
                _state.value = ConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                addLog("Connection failed: ${t.message}")
                _state.value = ConnectionState.Error(t.message ?: "Connection failed")
                scheduleReconnect()
            }
        })
    }

    private fun sendAuthHandshake(ws: WebSocket) {
        val nonce = CryptoManager.generateNonce()
        val challenge = JSONObject().apply {
            put("type", "auth_challenge")
            put("nonce", nonce)
            put("timestamp", System.currentTimeMillis())
        }
        val challengeStr = challenge.toString()
        val signature = CryptoManager.sign(context, challengeStr) ?: return

        val authMsg = JSONObject().apply {
            put("type", "auth")
            put("challenge", challenge)
            put("signature", signature)
        }
        ws.send(authMsg.toString())
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        try {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "auth_ok" -> {
                    _state.value = ConnectionState.Connected
                    reconnectAttempt = 0
                    addLog("Authenticated with server")
                    startPingLoop(ws)
                }
                "send_sms" -> handleSmsCommand(ws, msg)
                "pong" -> { /* heartbeat response */ }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun handleSmsCommand(ws: WebSocket, msg: JSONObject) {
        val commandId = msg.getString("commandId")
        val payload = msg.getJSONObject("payload")
        val signature = msg.getString("signature")

        // Verify server signature
        val serverPubKey = CryptoManager.getServerPublicKey(context) ?: return
        val payloadStr = payload.toString()
        if (!CryptoManager.verify(payloadStr, signature, serverPubKey)) {
            addLog("Rejected SMS command $commandId — invalid signature")
            sendReceipt(ws, commandId, "rejected", "Invalid server signature")
            return
        }

        val to = payload.getString("to")
        val message = payload.getString("message")
        addLog("Sending SMS to $to: ${message.take(30)}...")

        // Send the SMS
        val result = smsService.sendDirectSms(to, message)
        val status = when (result) {
            is SmsResult.Success -> "sent"
            is SmsResult.InvalidNumber -> "failed"
            is SmsResult.EmptyMessage -> "failed"
            is SmsResult.PermissionDenied -> "failed"
            is SmsResult.SendFailed -> "failed"
        }
        val detail = when (result) {
            is SmsResult.Success -> "SMS queued to ${result.phoneNumber}"
            is SmsResult.InvalidNumber -> "Invalid number: ${result.phoneNumber}"
            is SmsResult.EmptyMessage -> "Empty message"
            is SmsResult.PermissionDenied -> "SMS permission denied"
            is SmsResult.SendFailed -> "Send failed: ${result.error.message}"
        }

        addLog("SMS result: $status — $detail")
        sendReceipt(ws, commandId, status, detail)
    }

    private fun sendReceipt(ws: WebSocket, commandId: String, status: String, detail: String) {
        val receiptPayload = JSONObject().apply {
            put("commandId", commandId)
            put("status", status)
            put("detail", detail)
        }
        val receiptStr = receiptPayload.toString()
        val signature = CryptoManager.sign(context, receiptStr) ?: return

        val receipt = JSONObject().apply {
            put("type", "sms_receipt")
            put("commandId", commandId)
            put("status", status)
            put("detail", detail)
            put("signature", signature)
        }
        ws.send(receipt.toString())
    }

    private fun startPingLoop(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                ws.send(JSONObject().put("type", "ping").toString())
            }
        }
    }

    private fun scheduleReconnect() {
        if (!running) return
        reconnectJob?.cancel()
        val delay = (RECONNECT_BASE_MS * (1 shl reconnectAttempt.coerceAtMost(5)))
            .coerceAtMost(RECONNECT_MAX_MS)
        reconnectAttempt++
        addLog("Reconnecting in ${delay / 1000}s...")
        reconnectJob = scope.launch {
            delay(delay)
            connect()
        }
    }

    private fun addLog(message: String) {
        Log.d(TAG, message)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val current = _messageLog.value.toMutableList()
        current.add(0, "[$timestamp] $message")
        if (current.size > 50) current.removeAt(current.lastIndex)
        _messageLog.value = current
    }
}
