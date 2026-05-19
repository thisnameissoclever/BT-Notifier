package com.btnotifier.ui.watchlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btnotifier.data.WatchedDevice
import com.btnotifier.databinding.ItemWatchedDeviceBinding
import java.text.SimpleDateFormat
import java.util.*

class WatchedDeviceAdapter(
    private val onDelete: (WatchedDevice) -> Unit,
    private val onAlertToggle: (WatchedDevice, Boolean) -> Unit
) : ListAdapter<WatchedDevice, WatchedDeviceAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WatchedDevice>() {
            override fun areItemsTheSame(a: WatchedDevice, b: WatchedDevice) = a.address == b.address
            override fun areContentsTheSame(a: WatchedDevice, b: WatchedDevice) = a == b
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    }

    inner class ViewHolder(private val binding: ItemWatchedDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: WatchedDevice) {
            binding.tvName.text = device.name.ifBlank { "Unknown Device" }
            binding.tvAddress.text = device.address
            binding.tvLastSeen.text = device.lastSeenAt
                ?.let { "Last seen: ${DATE_FORMAT.format(Date(it))}" }
                ?: "Not seen yet"
            binding.switchAlert.isChecked = device.alertEnabled
            binding.switchAlert.setOnCheckedChangeListener(null)
            binding.switchAlert.setOnCheckedChangeListener { _, checked ->
                onAlertToggle(device, checked)
            }
            binding.btnDelete.setOnClickListener { onDelete(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWatchedDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
