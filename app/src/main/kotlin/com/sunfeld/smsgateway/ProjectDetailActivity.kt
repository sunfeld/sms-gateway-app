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

    companion object {
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_GATEWAY_INSTALLED = "sms_gateway_installed"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_API_BASE_URL = "api_base_url"
        private const val DEFAULT_API_BASE_URL = "http://localhost:3001"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_detail)

        txtProjectTitle = findViewById(R.id.txtProjectTitle)
        txtGatewayStatus = findViewById(R.id.txtGatewayStatus)
        btnInstallGateway = findViewById(R.id.btnInstallGateway)

        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: getString(R.string.app_name)
        val gatewayInstalled = intent.getBooleanExtra(EXTRA_GATEWAY_INSTALLED, false)
        val projectId = intent.getIntExtra(EXTRA_PROJECT_ID, -1)
        val baseUrl = intent.getStringExtra(EXTRA_API_BASE_URL) ?: DEFAULT_API_BASE_URL

        txtProjectTitle.text = projectName
        updateGatewayUI(gatewayInstalled)

        btnInstallGateway.onInstallClick = {
            if (projectId != -1) {
                viewModel.installGateway(baseUrl, projectId)
            } else {
                Toast.makeText(this, "No project ID provided", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.installState.observe(this) { state ->
            when (state) {
                is InstallResult.Idle -> {
                    btnInstallGateway.state = GatewayInstallButton.State.IDLE
                }
                is InstallResult.Installing -> {
                    btnInstallGateway.state = GatewayInstallButton.State.INSTALLING
                }
                is InstallResult.Success -> {
                    updateGatewayUI(installed = true)
                    Toast.makeText(this, "Gateway installed successfully", Toast.LENGTH_SHORT).show()
                }
                is InstallResult.Error -> {
                    btnInstallGateway.state = GatewayInstallButton.State.ERROR
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
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
