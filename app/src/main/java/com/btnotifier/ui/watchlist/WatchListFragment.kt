package com.btnotifier.ui.watchlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.btnotifier.databinding.FragmentWatchListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WatchListFragment : Fragment() {

    private var _binding: FragmentWatchListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WatchListViewModel by viewModels()
    private lateinit var adapter: WatchedDeviceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWatchListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = WatchedDeviceAdapter(
            onDelete = { device ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Remove device")
                    .setMessage("Stop watching ${device.name.ifBlank { device.address }}?")
                    .setPositiveButton("Remove") { _, _ -> viewModel.removeDevice(device) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onAlertToggle = { device, enabled ->
                viewModel.setAlertEnabled(device, enabled)
            }
        )
        binding.recyclerView.adapter = adapter

        viewModel.watchedDevices.observe(viewLifecycleOwner) { devices ->
            adapter.submitList(devices)
            binding.tvEmpty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
