package com.sunfeld.smsgateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var editRecipient: TextInputEditText
    private lateinit var editMessage: TextInputEditText
    private lateinit var btnSend: MaterialButton

    private val requiredPermissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val smsSendGranted = grants[Manifest.permission.SEND_SMS] == true
        updateSendButtonState(smsSendGranted)

        if (!smsSendGranted) {
            Toast.makeText(this, "SMS permission is required to send messages", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editRecipient = findViewById(R.id.editRecipient)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)

        // Disable send button until permissions are confirmed
        btnSend.isEnabled = false

        if (hasRequiredPermissions()) {
            updateSendButtonState(true)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateSendButtonState(enabled: Boolean) {
        btnSend.isEnabled = enabled
        btnSend.alpha = if (enabled) 1.0f else 0.5f
    }
}
