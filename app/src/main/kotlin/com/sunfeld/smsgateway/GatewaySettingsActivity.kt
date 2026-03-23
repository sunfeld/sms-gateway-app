package com.sunfeld.smsgateway

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GatewaySettingsActivity : AppCompatActivity() {

    private lateinit var txtConnectionStatus: MaterialTextView
    private lateinit var txtPairingStatus: MaterialTextView
    private lateinit var editServerUrl: TextInputEditText
    private lateinit var btnPair: MaterialButton
    private lateinit var btnConnect: MaterialButton
    private lateinit var txtPhoneFingerprint: MaterialTextView
    private lateinit var txtServerFingerprint: MaterialTextView
    private lateinit var btnRotateKeys: MaterialButton
    private lateinit var txtLog: MaterialTextView

    private var relayClient: RelayClient? = null
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gateway_settings)
        bindViews()
        setupButtons()
        ensureKeyPair()
        updateKeyDisplay()
        updatePairingStatus()
    }

    private fun bindViews() {
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus)
        txtPairingStatus = findViewById(R.id.txtPairingStatus)
        editServerUrl = findViewById(R.id.editServerUrl)
        btnPair = findViewById(R.id.btnPair)
        btnConnect = findViewById(R.id.btnConnect)
        txtPhoneFingerprint = findViewById(R.id.txtPhoneFingerprint)
        txtServerFingerprint = findViewById(R.id.txtServerFingerprint)
        btnRotateKeys = findViewById(R.id.btnRotateKeys)
        txtLog = findViewById(R.id.txtLog)

        // Load saved relay URL
        val savedUrl = CryptoManager.getRelayUrl(this)
        editServerUrl.setText(savedUrl.replace("wss://", "https://").replace("/ws", ""))
    }

    private fun setupButtons() {
        btnPair.setOnClickListener { pairWithServer() }
        btnConnect.setOnClickListener { toggleConnection() }
        btnRotateKeys.setOnClickListener { confirmRotateKeys() }
    }

    private fun ensureKeyPair() {
        if (!CryptoManager.hasKeyPair(this)) {
            CryptoManager.generateKeyPair(this)
        }
    }

    private fun updateKeyDisplay() {
        val pubKey = CryptoManager.getPublicKeyBase64(this)
        if (pubKey != null) {
            txtPhoneFingerprint.text = getString(R.string.phone_fingerprint, CryptoManager.getFingerprint(pubKey))
        } else {
            txtPhoneFingerprint.text = getString(R.string.no_keys)
        }

        val serverPubKey = CryptoManager.getServerPublicKey(this)
        if (serverPubKey != null) {
            txtServerFingerprint.text = getString(R.string.server_fingerprint, CryptoManager.getFingerprint(serverPubKey))
        } else {
            txtServerFingerprint.text = getString(R.string.server_fingerprint, "—")
        }
    }

    private fun updatePairingStatus() {
        val paired = CryptoManager.isPaired(this)
        txtPairingStatus.text = getString(
            if (paired) R.string.pairing_status_paired else R.string.pairing_status_not_paired
        )
        btnConnect.isEnabled = paired
    }

    private fun pairWithServer() {
        val baseUrl = editServerUrl.text?.toString()?.trim() ?: return
        if (baseUrl.isEmpty()) return

        // Derive WebSocket URL and save
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws"
        CryptoManager.setRelayUrl(this, wsUrl)

        // Derive HTTP URL for API calls
        val httpUrl = baseUrl
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .trimEnd('/')

        ensureKeyPair()
        val phonePubKey = CryptoManager.getPublicKeyBase64(this) ?: return

        btnPair.isEnabled = false
        btnPair.text = "Pairing…"

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val body = JSONObject().put("publicKey", phonePubKey).toString()
                    val request = Request.Builder()
                        .url("$httpUrl/api/register-phone")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw Exception("Server returned ${response.code}: ${response.body?.string()}")
                    }
                    JSONObject(response.body!!.string())
                }

                // Store server public key
                val serverPubKey = result.getString("serverPublicKey")
                CryptoManager.setServerPublicKey(this@GatewaySettingsActivity, serverPubKey)

                updateKeyDisplay()
                updatePairingStatus()
                Toast.makeText(this@GatewaySettingsActivity, getString(R.string.pairing_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@GatewaySettingsActivity,
                    getString(R.string.pairing_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnPair.isEnabled = true
                btnPair.text = getString(R.string.pair_with_server)
            }
        }
    }

    private fun toggleConnection() {
        if (relayClient != null) {
            relayClient?.stop()
            relayClient = null
            btnConnect.text = getString(R.string.connect_to_relay)
            txtConnectionStatus.text = getString(R.string.status_disconnected)
            return
        }

        relayClient = RelayClient(this).also { client ->
            // Observe connection state
            lifecycleScope.launch {
                client.state.collect { state ->
                    runOnUiThread {
                        when (state) {
                            is RelayClient.ConnectionState.Disconnected -> {
                                txtConnectionStatus.text = getString(R.string.status_disconnected)
                                btnConnect.text = getString(R.string.connect_to_relay)
                            }
                            is RelayClient.ConnectionState.Connecting -> {
                                txtConnectionStatus.text = getString(R.string.status_connecting)
                                btnConnect.text = getString(R.string.disconnect_from_relay)
                            }
                            is RelayClient.ConnectionState.Authenticating -> {
                                txtConnectionStatus.text = getString(R.string.status_authenticating)
                            }
                            is RelayClient.ConnectionState.Connected -> {
                                txtConnectionStatus.text = getString(R.string.status_connected)
                                btnConnect.text = getString(R.string.disconnect_from_relay)
                            }
                            is RelayClient.ConnectionState.Error -> {
                                txtConnectionStatus.text = getString(R.string.status_error, state.message)
                                btnConnect.text = getString(R.string.disconnect_from_relay)
                            }
                        }
                    }
                }
            }

            // Observe message log
            lifecycleScope.launch {
                client.messageLog.collect { logs ->
                    runOnUiThread {
                        txtLog.text = if (logs.isEmpty()) {
                            getString(R.string.no_log_entries)
                        } else {
                            logs.joinToString("\n")
                        }
                    }
                }
            }

            client.start()
            btnConnect.text = getString(R.string.disconnect_from_relay)
        }
    }

    private fun confirmRotateKeys() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Rotate Keys?")
            .setMessage("This will generate a new Ed25519 keypair and require re-pairing with the server. Continue?")
            .setPositiveButton("Rotate") { _, _ ->
                CryptoManager.rotateKeys(this)
                updateKeyDisplay()
                updatePairingStatus()
                relayClient?.stop()
                relayClient = null
                btnConnect.text = getString(R.string.connect_to_relay)
                txtConnectionStatus.text = getString(R.string.status_disconnected)
                Toast.makeText(this, getString(R.string.keys_rotated), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        relayClient?.stop()
        super.onDestroy()
    }
}
