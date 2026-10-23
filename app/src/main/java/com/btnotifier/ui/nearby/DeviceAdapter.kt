package com.btnotifier.ui.nearby

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btnotifier.R
import com.btnotifier.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onAddClick: (NearbyViewModel.DeviceItem) -> Unit,
    private val onRemoveClick: (NearbyViewModel.DeviceItem) -> Unit,
    private val onHideClick: (NearbyViewModel.DeviceItem) -> Unit,
    private val onRenameClick: (NearbyViewModel.DeviceItem) -> Unit,
    private val onFindClick: (NearbyViewModel.DeviceItem) -> Unit
) : ListAdapter<NearbyViewModel.DeviceItem, DeviceAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NearbyViewModel.DeviceItem>() {
            override fun areItemsTheSame(a: NearbyViewModel.DeviceItem, b: NearbyViewModel.DeviceItem) =
                a.device.address == b.device.address

            override fun areContentsTheSame(a: NearbyViewModel.DeviceItem, b: NearbyViewModel.DeviceItem) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NearbyViewModel.DeviceItem) {
            val device = item.device
            binding.tvDeviceName.text = item.displayName
            binding.tvDeviceAddress.text = device.address
            binding.tvRssi.text = "${device.rssi} dBm"
            binding.tvType.text = if (device.isBle) "BLE" else "Classic"

            val signalIcon = when {
                device.rssi >= -60 -> R.drawable.ic_signal_strong
                device.rssi >= -75 -> R.drawable.ic_signal_medium
                else -> R.drawable.ic_signal_weak
            }
            binding.ivSignal.setImageResource(signalIcon)

            binding.root.setOnClickListener(null)
            binding.root.setOnLongClickListener {
                onRenameClick(item)
                true
            }
            binding.btnHide.setOnClickListener { onHideClick(item) }
            binding.btnFind.setOnClickListener { onFindClick(item) }

            if (item.isWatched) {
                binding.btnWatch.text = "Watching"
                binding.btnWatch.isSelected = true
                binding.btnWatch.setOnClickListener { onRemoveClick(item) }
            } else {
                binding.btnWatch.text = "Watch"
                binding.btnWatch.isSelected = false
                binding.btnWatch.setOnClickListener { onAddClick(item) }
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
