package com.btnotifier.ui.nearby

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.btnotifier.R
import com.btnotifier.databinding.FragmentNearbyBinding
import com.btnotifier.ui.DeviceAliasDialog

class NearbyFragment : Fragment() {

    private var _binding: FragmentNearbyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NearbyViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            startScan()
        } else {
            Toast.makeText(requireContext(), "Bluetooth permissions required to scan", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isBluetoothEnabled()) startScan()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNearbyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DeviceAdapter(
            onAddClick = { item ->
                viewModel.addToWatchList(item.device)
                Toast.makeText(requireContext(), "Added ${item.displayName} to watch list", Toast.LENGTH_SHORT).show()
            },
            onRemoveClick = { item ->
                viewModel.removeFromWatchList(item.device.address)
                Toast.makeText(requireContext(), "Removed from watch list", Toast.LENGTH_SHORT).show()
            },
            onHideClick = { item ->
                viewModel.hideDevice(item.device)
                Toast.makeText(requireContext(), R.string.device_hidden, Toast.LENGTH_SHORT).show()
            },
            onRenameClick = { item -> showRenameDialog(item) }
        )
        binding.recyclerView.adapter = adapter

        binding.btnScan.setOnClickListener {
            if (viewModel.isScanning.value == true) {
                viewModel.stopScan()
            } else {
                requestPermissionsAndScan()
            }
        }

        binding.btnShowHidden.setOnClickListener {
            HiddenDevicesBottomSheet().show(childFragmentManager, "hidden_devices")
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.btnScan.text = if (scanning) "Stop Scan" else "Scan Now"
            binding.progressBar.visibility = if (scanning) View.VISIBLE else View.GONE
        }

        viewModel.devices.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.hiddenDevices.observe(viewLifecycleOwner) { hidden ->
            val count = hidden.size
            binding.btnShowHidden.visibility = if (count > 0) View.VISIBLE else View.GONE
            binding.btnShowHidden.text = getString(R.string.show_hidden, count)
        }
    }

    private fun showRenameDialog(item: NearbyViewModel.DeviceItem) {
        DeviceAliasDialog.show(
            context = requireContext(),
            currentAlias = item.customName,
            onSave = { name -> viewModel.renameDevice(item.device.address, name, item.device.name) },
            onClear = { viewModel.renameDevice(item.device.address, "", item.device.name) }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun requestPermissionsAndScan() {
        val needed = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else if (!isBluetoothEnabled()) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableIntent)
        } else {
            startScan()
        }
    }

    private fun startScan() {
        viewModel.startScan()
    }

    private fun isBluetoothEnabled(): Boolean {
        val bm = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.isEnabled == true
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
