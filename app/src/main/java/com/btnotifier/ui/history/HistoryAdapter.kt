package com.btnotifier.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btnotifier.R
import com.btnotifier.data.history.DetectionObservation
import com.btnotifier.databinding.ItemHistoryBinding
import java.text.DateFormat
import java.util.Date

class HistoryAdapter : ListAdapter<DetectionObservation, HistoryAdapter.Holder>(object : DiffUtil.ItemCallback<DetectionObservation>() {
    override fun areItemsTheSame(old: DetectionObservation, new: DetectionObservation) =
        old.address == new.address && old.minuteBucket == new.minuteBucket
    override fun areContentsTheSame(old: DetectionObservation, new: DetectionObservation) = old == new
}) {
    class Holder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            tvHistoryName.text = row.observedName.takeIf { it.isNotBlank() } ?: context.getString(R.string.history_unnamed)
            tvHistoryAddress.text = row.address
            val source = when (row.source) {
                "BACKGROUND" -> context.getString(R.string.history_background)
                "FINDER" -> context.getString(R.string.history_finder)
                else -> context.getString(R.string.history_nearby)
            }
            tvHistoryDetails.text = context.getString(
                R.string.history_details, row.rssi, if (row.isBle) "BLE" else "Classic", source
            )
            tvHistoryTime.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(row.observedAt))
        }
    }
}
