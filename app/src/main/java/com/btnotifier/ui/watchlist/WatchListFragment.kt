package com.btnotifier.ui.watchlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.btnotifier.R
import com.btnotifier.databinding.FragmentWatchListBinding
import com.btnotifier.device.DeviceDisplayName
import com.btnotifier.ui.DeviceAliasDialog
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
            onDelete = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Remove device")
                    .setMessage("Stop watching ${item.displayName}?")
                    .setPositiveButton("Remove") { _, _ -> viewModel.removeDevice(item.device) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onAlertToggle = { item, enabled ->
                viewModel.setAlertEnabled(item.device, enabled)
            },
            onRename = { item -> showRenameDialog(item) },
            onFind = { item ->
                findNavController().navigate(R.id.finderFragment, bundleOf(
                    "address" to item.device.address,
                    "displayName" to item.displayName
                ))
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

    private fun showRenameDialog(item: WatchListViewModel.WatchedDeviceItem) {
        DeviceAliasDialog.show(
            context = requireContext(),
            currentAlias = item.customName,
            onSave = { name -> viewModel.renameDevice(item.device.address, name, actualName(item)) },
            onClear = { viewModel.renameDevice(item.device.address, "", actualName(item)) }
        )
    }

    private fun actualName(item: WatchListViewModel.WatchedDeviceItem): String? {
        return DeviceDisplayName.safeActualNameForAliasEdit(
            alias = item.customName,
            lastSeenName = item.lastSeenName,
            storedWatchedName = item.device.name
        )
    }
}
