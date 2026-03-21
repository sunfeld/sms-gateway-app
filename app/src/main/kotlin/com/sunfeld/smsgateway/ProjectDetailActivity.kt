package com.sunfeld.smsgateway

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView

class ProjectDetailActivity : AppCompatActivity() {

    private lateinit var txtProjectTitle: MaterialTextView
    private lateinit var txtGatewayStatus: MaterialTextView
    private lateinit var btnInstallGateway: GatewayInstallButton

    private val viewModel: ProjectViewModel by viewModels()

    private val gatewayStatusReceiver = GatewayStatusReceiver { isActive ->
        if (isActive) {
            viewModel.setGatewayInstalled(true)
            viewModel.stopStatusPolling()
        }
    }

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
        viewModel.setGatewayInstalled(gatewayInstalled)

        btnInstallGateway.onInstallClick = {
            viewModel.installGateway()
        }

        viewModel.installState.observe(this) { state ->
            when (state) {
                is InstallState.Idle -> {
                    btnInstallGateway.state = GatewayInstallButton.State.IDLE
                }
                is InstallState.Installing -> {
                    btnInstallGateway.state = GatewayInstallButton.State.INSTALLING
                }
                is InstallState.Success -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                is InstallState.Error -> {
                    btnInstallGateway.state = GatewayInstallButton.State.ERROR
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.gatewayInstalled.observe(this) { installed ->
            updateGatewayUI(installed)
        }
    }

    override fun onStart() {
        super.onStart()
        gatewayStatusReceiver.register(this)
    }

    override fun onResume() {
        super.onResume()
        // Refresh status on resume and start polling if gateway is not yet active
        viewModel.refreshProjectStatus()
        if (viewModel.gatewayInstalled.value != true) {
            viewModel.startStatusPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopStatusPolling()
    }

    override fun onStop() {
        super.onStop()
        gatewayStatusReceiver.unregister(this)
    }

    private fun updateGatewayUI(installed: Boolean) {
        if (installed) {
            txtGatewayStatus.text = getString(R.string.gateway_status_installed)
            btnInstallGateway.visibility = View.GONE
            viewModel.stopStatusPolling()
            // Broadcast the status change so other components can react
            GatewayStatusReceiver.sendStatusBroadcast(this, gatewayActive = true)
        } else {
            txtGatewayStatus.text = getString(R.string.gateway_status_not_installed)
            btnInstallGateway.visibility = View.VISIBLE
            btnInstallGateway.state = GatewayInstallButton.State.IDLE
        }
    }
}
