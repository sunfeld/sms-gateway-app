package com.sunfeld.smsgateway

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView

class ProjectDetailActivity : AppCompatActivity() {

    private lateinit var txtProjectTitle: MaterialTextView
    private lateinit var txtGatewayStatus: MaterialTextView
    private lateinit var btnInstallGateway: GatewayInstallButton

    companion object {
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_GATEWAY_INSTALLED = "sms_gateway_installed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_detail)

        txtProjectTitle = findViewById(R.id.txtProjectTitle)
        txtGatewayStatus = findViewById(R.id.txtGatewayStatus)
        btnInstallGateway = findViewById(R.id.btnInstallGateway)

        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: getString(R.string.app_name)
        val gatewayInstalled = intent.getBooleanExtra(EXTRA_GATEWAY_INSTALLED, false)

        txtProjectTitle.text = projectName
        updateGatewayUI(gatewayInstalled)

        btnInstallGateway.onInstallClick = {
            btnInstallGateway.state = GatewayInstallButton.State.INSTALLING
            Toast.makeText(this, getString(R.string.installing_gateway), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGatewayUI(installed: Boolean) {
        if (installed) {
            txtGatewayStatus.text = getString(R.string.gateway_status_installed)
            btnInstallGateway.visibility = View.GONE
        } else {
            txtGatewayStatus.text = getString(R.string.gateway_status_not_installed)
            btnInstallGateway.visibility = View.VISIBLE
            btnInstallGateway.state = GatewayInstallButton.State.IDLE
        }
    }
}
