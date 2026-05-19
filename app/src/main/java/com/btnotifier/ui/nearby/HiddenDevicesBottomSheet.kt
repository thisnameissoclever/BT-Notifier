package com.btnotifier.ui.nearby

import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.databinding.BottomSheetHiddenDevicesBinding
import com.btnotifier.databinding.ItemHiddenDeviceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HiddenDevicesBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetHiddenDevicesBinding? = null
    private val binding get() = _binding!!

    // Share the NearbyFragment's ViewModel so the list stays live
    private val viewModel: NearbyViewModel by lazy {
        ViewModelProvider(requireParentFragment())[NearbyViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetHiddenDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = HiddenAdapter { address -> viewModel.unhideDevice(address) }
        binding.recyclerHidden.adapter = adapter

        viewModel.hiddenDevices.observe(viewLifecycleOwner) { hidden ->
            if (hidden.isEmpty()) dismiss()
            else adapter.submitList(hidden)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class HiddenViewHolder(val binding: ItemHiddenDeviceBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class HiddenAdapter(private val onUnhide: (String) -> Unit) :
        ListAdapter<DeviceMetadata, HiddenViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            HiddenViewHolder(
                ItemHiddenDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

        override fun onBindViewHolder(holder: HiddenViewHolder, position: Int) {
            val meta = getItem(position)
            holder.binding.tvHiddenName.text = meta.customName?.ifBlank { null } ?: meta.address
            holder.binding.tvHiddenAddress.text = meta.address
            holder.binding.btnUnhide.setOnClickListener { onUnhide(meta.address) }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<DeviceMetadata>() {
                override fun areItemsTheSame(a: DeviceMetadata, b: DeviceMetadata) =
                    a.address == b.address
                override fun areContentsTheSame(a: DeviceMetadata, b: DeviceMetadata) = a == b
            }
        }
    }
}
