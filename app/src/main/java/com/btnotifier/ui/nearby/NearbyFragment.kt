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
import com.btnotifier.databinding.FragmentNearbyBinding

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
            onAddClick = { device ->
                viewModel.addToWatchList(device)
                Toast.makeText(requireContext(), "Added ${device.name.ifBlank { device.address }} to watch list", Toast.LENGTH_SHORT).show()
            },
            onRemoveClick = { device ->
                viewModel.removeFromWatchList(device.address)
                Toast.makeText(requireContext(), "Removed from watch list", Toast.LENGTH_SHORT).show()
            }
        )
        binding.recyclerView.adapter = adapter

        binding.btnScan.setOnClickListener {
            if (viewModel.isScanning.value == true) {
                viewModel.stopScan()
            } else {
                requestPermissionsAndScan()
            }
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.btnScan.text = if (scanning) "Stop Scan" else "Scan Now"
            binding.progressBar.visibility = if (scanning) View.VISIBLE else View.GONE
        }

        viewModel.devices.observe(viewLifecycleOwner) { devices ->
            val watchedAddresses = viewModel.watchedDevices.value?.map { it.address }?.toSet() ?: emptySet()
            val items = devices.map { DeviceAdapter.DeviceItem(it, it.address in watchedAddresses) }
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.watchedDevices.observe(viewLifecycleOwner) { _ ->
            // Re-bind to update watch status badges
            val devices = viewModel.devices.value ?: return@observe
            val watchedAddresses = viewModel.watchedDevices.value?.map { it.address }?.toSet() ?: emptySet()
            val items = devices.map { DeviceAdapter.DeviceItem(it, it.address in watchedAddresses) }
            adapter.submitList(items)
        }
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
