package com.btnotifier.ui.nearby

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.databinding.BottomSheetHiddenDevicesBinding
import com.btnotifier.databinding.ItemHiddenDeviceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HiddenDevicesBottomSheet(
    private val hiddenDevices: List<DeviceMetadata>,
    private val onUnhide: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetHiddenDevicesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetHiddenDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerHidden.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = hiddenDevices.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val b = ItemHiddenDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return object : RecyclerView.ViewHolder(b.root) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val meta = hiddenDevices[position]
                val b = ItemHiddenDeviceBinding.bind(holder.itemView)
                b.tvHiddenName.text = meta.customName?.ifBlank { null } ?: meta.address
                b.tvHiddenAddress.text = meta.address
                b.btnUnhide.setOnClickListener {
                    onUnhide(meta.address)
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
