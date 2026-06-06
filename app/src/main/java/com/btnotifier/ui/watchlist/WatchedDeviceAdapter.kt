package com.btnotifier.ui.watchlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btnotifier.databinding.ItemWatchedDeviceBinding
import java.text.SimpleDateFormat
import java.util.*

class WatchedDeviceAdapter(
    private val onDelete: (WatchListViewModel.WatchedDeviceItem) -> Unit,
    private val onAlertToggle: (WatchListViewModel.WatchedDeviceItem, Boolean) -> Unit,
    private val onRename: (WatchListViewModel.WatchedDeviceItem) -> Unit
) : ListAdapter<WatchListViewModel.WatchedDeviceItem, WatchedDeviceAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WatchListViewModel.WatchedDeviceItem>() {
            override fun areItemsTheSame(
                a: WatchListViewModel.WatchedDeviceItem,
                b: WatchListViewModel.WatchedDeviceItem
            ) = a.device.address == b.device.address

            override fun areContentsTheSame(
                a: WatchListViewModel.WatchedDeviceItem,
                b: WatchListViewModel.WatchedDeviceItem
            ) = a == b
        }
        private val DATE_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    }

    inner class ViewHolder(private val binding: ItemWatchedDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WatchListViewModel.WatchedDeviceItem) {
            val device = item.device
            binding.tvName.text = item.displayName
            binding.tvAddress.text = device.address
            binding.tvLastSeen.text = device.lastSeenAt
                ?.let { "Last seen: ${DATE_FORMAT.format(Date(it))}" }
                ?: "Not seen yet"
            binding.switchAlert.setOnCheckedChangeListener(null)
            binding.switchAlert.isChecked = device.alertEnabled
            binding.switchAlert.setOnCheckedChangeListener { _, checked ->
                onAlertToggle(item, checked)
            }
            binding.btnDelete.setOnClickListener { onDelete(item) }
            binding.root.setOnLongClickListener {
                onRename(item)
                true
            }
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
