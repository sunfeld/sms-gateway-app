package com.sunfeld.smsgateway

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.textview.MaterialTextView

class BtDeviceAdapter : RecyclerView.Adapter<BtDeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()
    private val connectedAddresses = mutableSetOf<String>()
    private val selectedAddresses = mutableSetOf<String>()

    var onSelectionChanged: ((Set<String>) -> Unit)? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: MaterialCheckBox = view.findViewById(R.id.checkboxSelect)
        val txtName: MaterialTextView = view.findViewById(R.id.txtDeviceName)
        val txtAddress: MaterialTextView = view.findViewById(R.id.txtDeviceAddress)
        val chipStatus: Chip = view.findViewById(R.id.chipStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bt_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        val address = device.address
        val name = try { device.name?.takeIf { it.isNotBlank() } } catch (_: SecurityException) { null }

        holder.txtName.text = name ?: "Unknown device"
        holder.txtAddress.text = address

        val isConnected = connectedAddresses.contains(address)
        holder.chipStatus.text = holder.itemView.context.getString(
            if (isConnected) R.string.device_status_connected else R.string.device_status_discovered
        )

        // Set checkbox without triggering listener
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selectedAddresses.contains(address)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedAddresses.add(address)
            } else {
                selectedAddresses.remove(address)
            }
            onSelectionChanged?.invoke(selectedAddresses.toSet())
        }
    }

    override fun getItemCount() = devices.size

    fun getSelectedAddresses(): Set<String> = selectedAddresses.toSet()

    fun setSelectedAddresses(addresses: Set<String>) {
        selectedAddresses.clear()
        selectedAddresses.addAll(addresses)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedAddresses.toSet())
    }

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    fun updateConnected(connectedSet: Set<BluetoothDevice>) {
        connectedAddresses.clear()
        connectedAddresses.addAll(connectedSet.map { it.address })
        notifyDataSetChanged()
    }
}
