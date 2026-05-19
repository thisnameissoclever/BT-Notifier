package com.btnotifier.ui.nearby

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btnotifier.R
import com.btnotifier.databinding.ItemDeviceBinding
import com.btnotifier.scanner.ScannedDevice

class DeviceAdapter(
    private val onAddClick: (ScannedDevice) -> Unit,
    private val onRemoveClick: (ScannedDevice) -> Unit
) : ListAdapter<DeviceAdapter.DeviceItem, DeviceAdapter.ViewHolder>(DIFF) {

    data class DeviceItem(val device: ScannedDevice, val isWatched: Boolean)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DeviceItem>() {
            override fun areItemsTheSame(a: DeviceItem, b: DeviceItem) =
                a.device.address == b.device.address

            override fun areContentsTheSame(a: DeviceItem, b: DeviceItem) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceItem) {
            val device = item.device
            binding.tvDeviceName.text = device.name.ifBlank { "Unknown Device" }
            binding.tvDeviceAddress.text = device.address
            binding.tvRssi.text = "${device.rssi} dBm"
            binding.tvType.text = if (device.isBle) "BLE" else "Classic"

            val signalIcon = when {
                device.rssi >= -60 -> R.drawable.ic_signal_strong
                device.rssi >= -75 -> R.drawable.ic_signal_medium
                else -> R.drawable.ic_signal_weak
            }
            binding.ivSignal.setImageResource(signalIcon)

            if (item.isWatched) {
                binding.btnWatch.text = "Watching"
                binding.btnWatch.isSelected = true
                binding.btnWatch.setOnClickListener { onRemoveClick(device) }
            } else {
                binding.btnWatch.text = "Watch"
                binding.btnWatch.isSelected = false
                binding.btnWatch.setOnClickListener { onAddClick(device) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
