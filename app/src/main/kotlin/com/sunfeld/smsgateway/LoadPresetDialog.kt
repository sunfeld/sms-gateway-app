package com.sunfeld.smsgateway

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object LoadPresetDialog {

    fun show(activity: BluetoothHidActivity) {
        val repo = PresetRepository(activity)
        val presets = repo.getAll().toMutableList()

        if (presets.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.no_presets), Toast.LENGTH_SHORT).show()
            return
        }

        val recyclerView = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            setPadding(0, 16, 0, 0)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.load_preset))
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .create()

        val adapter = PresetListAdapter(
            presets = presets,
            onSelect = { preset ->
                activity.applyPreset(preset)
                dialog.dismiss()
            },
            onDelete = { preset ->
                repo.delete(preset.id)
                presets.removeAll { it.id == preset.id }
                recyclerView.adapter?.notifyDataSetChanged()
                Toast.makeText(activity, activity.getString(R.string.preset_deleted), Toast.LENGTH_SHORT).show()
                if (presets.isEmpty()) dialog.dismiss()
            }
        )

        recyclerView.adapter = adapter
        dialog.show()
    }

    private class PresetListAdapter(
        private val presets: MutableList<HidPreset>,
        private val onSelect: (HidPreset) -> Unit,
        private val onDelete: (HidPreset) -> Unit
    ) : RecyclerView.Adapter<PresetListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtPresetName)
            val txtProfile: TextView = view.findViewById(R.id.txtPresetProfile)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePreset)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_preset, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val preset = presets[position]
            holder.txtName.text = preset.name
            val profileName = DeviceProfiles.findById(preset.profileId)?.displayName ?: preset.profileId
            holder.txtProfile.text = "${profileName} • ${preset.customDeviceName}"
            holder.itemView.setOnClickListener { onSelect(preset) }
            holder.btnDelete.setOnClickListener { onDelete(preset) }
        }

        override fun getItemCount() = presets.size
    }
}
