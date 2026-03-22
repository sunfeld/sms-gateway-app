package com.sunfeld.smsgateway

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch

class ProjectDetailActivity : AppCompatActivity() {

    private lateinit var txtProjectTitle: MaterialTextView
    private lateinit var txtGatewayStatus: MaterialTextView
    private lateinit var btnInstallGateway: GatewayInstallButton
    private lateinit var progressInstalling: CircularProgressIndicator

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
        progressInstalling = findViewById(R.id.progressInstalling)

        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: getString(R.string.app_name)
        val gatewayInstalled = intent.getBooleanExtra(EXTRA_GATEWAY_INSTALLED, false)

        txtProjectTitle.text = projectName
        viewModel.setGatewayInstalled(gatewayInstalled)

        btnInstallGateway.onInstallClick = {
            viewModel.installGateway()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.installState.collect { state ->
                        when (state) {
                            is InstallResult.Idle -> {
                                btnInstallGateway.state = GatewayInstallButton.State.IDLE
                                progressInstalling.visibility = View.GONE
                            }
                            is InstallResult.Installing -> {
                                btnInstallGateway.state = GatewayInstallButton.State.INSTALLING
                                progressInstalling.visibility = View.VISIBLE
                            }
                            is InstallResult.Success -> {
                                progressInstalling.visibility = View.GONE
                                Toast.makeText(this@ProjectDetailActivity, "Gateway installed successfully", Toast.LENGTH_SHORT).show()
                            }
                            is InstallResult.Error -> {
                                btnInstallGateway.state = GatewayInstallButton.State.ERROR
                                progressInstalling.visibility = View.GONE
                                Toast.makeText(this@ProjectDetailActivity, state.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.gatewayInstalled.collect { installed ->
                        updateGatewayUI(installed)
                    }
                }
            }
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
