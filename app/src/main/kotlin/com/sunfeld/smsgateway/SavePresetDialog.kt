package com.sunfeld.smsgateway

import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.UUID

object SavePresetDialog {

    fun show(activity: BluetoothHidActivity) {
        val inputLayout = TextInputLayout(activity).apply {
            hint = activity.getString(R.string.preset_name_hint)
            setPadding(48, 16, 48, 0)
        }
        val input = TextInputEditText(activity)
        inputLayout.addView(input)

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.save_preset))
            .setView(inputLayout)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(activity, "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val state = activity.getCurrentPresetState()
                val preset = HidPreset(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    profileId = state.profileId,
                    customDeviceName = state.customDeviceName,
                    targetAddresses = state.targetAddresses,
                    payload = state.payload,
                    createdAt = System.currentTimeMillis()
                )
                PresetRepository(activity).save(preset)
                Toast.makeText(activity, activity.getString(R.string.preset_saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
