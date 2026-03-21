package com.sunfeld.smsgateway

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textview.MaterialTextView

class BluetoothStressTestActivity : AppCompatActivity() {

    private lateinit var switchStressTest: MaterialSwitch
    private lateinit var txtStatus: MaterialTextView
    private lateinit var txtPacketsSentCount: MaterialTextView
    private lateinit var txtDevicesTargetedCount: MaterialTextView
    private lateinit var txtRemainingTime: MaterialTextView

    private val viewModel: BluetoothStressTestViewModel by viewModels()

    private var isUpdatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_stress_test)

        switchStressTest = findViewById(R.id.switchStressTest)
        txtStatus = findViewById(R.id.txtStatus)
        txtPacketsSentCount = findViewById(R.id.txtPacketsSentCount)
        txtDevicesTargetedCount = findViewById(R.id.txtDevicesTargetedCount)
        txtRemainingTime = findViewById(R.id.txtRemainingTime)

        switchStressTest.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                viewModel.startStressTest()
            } else {
                viewModel.stopStressTest()
            }
        }

        viewModel.state.observe(this) { state ->
            updateUI(state)
        }

        viewModel.packetsSent.observe(this) { count ->
            txtPacketsSentCount.text = formatCount(count)
        }

        viewModel.devicesTargeted.observe(this) { count ->
            txtDevicesTargetedCount.text = count.toString()
        }
    }

    private fun updateUI(state: StressTestState) {
        when (state) {
            is StressTestState.Idle -> {
                setSwitchChecked(false)
                switchStressTest.isEnabled = true
                txtStatus.text = getString(R.string.stress_test_status_idle)
                txtRemainingTime.visibility = View.GONE
            }
            is StressTestState.Starting -> {
                setSwitchChecked(true)
                switchStressTest.isEnabled = false
                txtStatus.text = getString(R.string.stress_test_status_starting)
                txtRemainingTime.visibility = View.GONE
            }
            is StressTestState.Running -> {
                setSwitchChecked(true)
                switchStressTest.isEnabled = true
                txtStatus.text = getString(R.string.stress_test_status_running)
                txtRemainingTime.visibility = View.VISIBLE
                txtRemainingTime.text = getString(
                    R.string.stress_test_remaining_time,
                    state.remainingSeconds
                )
            }
            is StressTestState.Stopping -> {
                setSwitchChecked(true)
                switchStressTest.isEnabled = false
                txtStatus.text = getString(R.string.stress_test_status_stopping)
            }
            is StressTestState.Error -> {
                setSwitchChecked(false)
                switchStressTest.isEnabled = true
                txtStatus.text = getString(R.string.stress_test_status_error)
                txtRemainingTime.visibility = View.GONE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                viewModel.dismissError()
            }
        }
    }

    private fun setSwitchChecked(checked: Boolean) {
        isUpdatingSwitch = true
        switchStressTest.isChecked = checked
        isUpdatingSwitch = false
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
