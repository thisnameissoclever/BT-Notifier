package com.btnotifier.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.btnotifier.data.PreferencesManager
import com.btnotifier.databinding.FragmentSettingsBinding
import com.btnotifier.network.WifiNetworkGate
import com.btnotifier.service.BluetoothScanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferencesManager
    private lateinit var wifiGate: WifiNetworkGate
    private lateinit var wifiAdapter: ArrayAdapter<String>
    private var isLoadingSettings = false
    private var ignoreWifiSelection = false
    private var wifiOptionCount = 0

    private companion object {
        const val WIFI_PLACEHOLDER = "Select Wi-Fi network"
    }

    private val wifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (_binding == null) return@registerForActivityResult
        if (granted) {
            refreshWifiNetworks(requestScan = true)
            persistWifiGateEnabledIfReady()
        } else {
            setWifiGateCheckedSilently(false)
            setWifiControlsEnabled(false)
            lifecycleScope.launch { prefs.setWifiScanGateEnabled(false) }
            binding.tvWifiGateStatus.text = "Location permission is needed to detect Wi-Fi networks"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PreferencesManager(requireContext())
        wifiGate = WifiNetworkGate(requireContext())
        setupWifiSelector()
        setupListeners()
        loadCurrentSettings()
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            isLoadingSettings = true
            val interval = prefs.scanIntervalMinutes.first()
            val duration = prefs.scanDurationSeconds.first()
            val serviceEnabled = prefs.serviceEnabled.first()
            val notifyEnabled = prefs.notifyOnFind.first()
            val wifiGateEnabled = prefs.wifiScanGateEnabled.first()
            val selectedWifiSsid = prefs.selectedWifiSsid.first()
            val hasSelectedWifiSsid = WifiNetworkGate.normalizeSsid(selectedWifiSsid) != null
            val effectiveWifiGateEnabled = wifiGateEnabled && hasSelectedWifiSsid

            binding.seekInterval.progress = interval - 1  // 1-60 min, 0-indexed
            binding.tvIntervalValue.text = "${interval} min"
            binding.seekDuration.progress = (duration / 5) - 1  // 5,10,15..60s
            binding.tvDurationValue.text = "${duration} sec"
            binding.switchService.isChecked = serviceEnabled
            binding.switchNotify.isChecked = notifyEnabled
            binding.switchWifiGate.isChecked = effectiveWifiGateEnabled
            setWifiControlsEnabled(effectiveWifiGateEnabled)
            if (wifiGateEnabled && !hasSelectedWifiSsid) {
                prefs.setWifiScanGateEnabled(false)
            }
            isLoadingSettings = false
            refreshWifiNetworks(requestScan = false, selectedWifiSsid = selectedWifiSsid)
        }
    }

    private fun setupWifiSelector() {
        wifiAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf())
        wifiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerWifiNetwork.adapter = wifiAdapter
        binding.spinnerWifiNetwork.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreWifiSelection || position < 0 || position >= wifiAdapter.count) return
                val ssid = wifiAdapter.getItem(position) ?: return
                if (ssid == WIFI_PLACEHOLDER) return
                lifecycleScope.launch {
                    prefs.setSelectedWifiSsid(ssid)
                    if (_binding != null && binding.switchWifiGate.isChecked) {
                        prefs.setWifiScanGateEnabled(true)
                    }
                    if (_binding != null) {
                        binding.tvWifiGateStatus.text = "Background scans will run near $ssid"
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupListeners() {
        binding.seekInterval.max = 59  // 1 to 60 minutes
        binding.seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val minutes = progress + 1
                binding.tvIntervalValue.text = "${minutes} min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val minutes = seekBar.progress + 1
                lifecycleScope.launch { prefs.setScanIntervalMinutes(minutes) }
            }
        })

        binding.seekDuration.max = 11  // 5,10,15,20,25,30,35,40,45,50,55,60 seconds
        binding.seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val seconds = (progress + 1) * 5
                binding.tvDurationValue.text = "${seconds} sec"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val seconds = (seekBar.progress + 1) * 5
                lifecycleScope.launch { prefs.setScanDurationSeconds(seconds) }
            }
        })

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                prefs.setServiceEnabled(isChecked)
                val serviceIntent = Intent(requireContext(), BluetoothScanService::class.java).apply {
                    action = if (isChecked) BluetoothScanService.ACTION_START else BluetoothScanService.ACTION_STOP
                }
                if (isChecked) {
                    ContextCompat.startForegroundService(requireContext(), serviceIntent)
                } else {
                    requireContext().startService(serviceIntent)
                }
            }
        }

        binding.switchNotify.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { prefs.setNotifyOnFind(isChecked) }
        }

        binding.switchWifiGate.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            setWifiControlsEnabled(isChecked)
            if (isChecked) {
                requestWifiPermissionAndRefresh()
            } else {
                lifecycleScope.launch { prefs.setWifiScanGateEnabled(false) }
                binding.tvWifiGateStatus.text = "Background scans are not limited by Wi-Fi"
            }
        }

        binding.btnRefreshWifi.setOnClickListener {
            requestWifiPermissionAndRefresh()
        }
    }

    private fun requestWifiPermissionAndRefresh() {
        if (hasWifiLocationPermission()) {
            refreshWifiNetworks(requestScan = true)
            persistWifiGateEnabledIfReady()
        } else {
            wifiPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun persistWifiGateEnabledIfReady() {
        lifecycleScope.launch {
            if (_binding == null || !binding.switchWifiGate.isChecked) {
                prefs.setWifiScanGateEnabled(false)
                return@launch
            }
            val selected = WifiNetworkGate.normalizeSsid(prefs.selectedWifiSsid.first())
            prefs.setWifiScanGateEnabled(selected != null)
            if (selected == null && _binding != null) {
                binding.tvWifiGateStatus.text = "Choose a Wi-Fi network for background scans"
            }
        }
    }

    private fun refreshWifiNetworks(requestScan: Boolean, selectedWifiSsid: String? = null) {
        lifecycleScope.launch {
            val selected = selectedWifiSsid ?: prefs.selectedWifiSsid.first()
            val result = withContext(Dispatchers.IO) {
                wifiGate.availableSsids(requestScan = requestScan)
            }
            if (_binding == null) return@launch
            updateWifiOptions(result.ssids, selected, result.blockedReason)
        }
    }

    private fun updateWifiOptions(
        ssids: List<String>,
        selectedWifiSsid: String,
        blockedReason: WifiNetworkGate.BlockedReason?
    ) {
        val selected = WifiNetworkGate.normalizeSsid(selectedWifiSsid)
        val options = (listOfNotNull(selected) + ssids)
            .mapNotNull { WifiNetworkGate.normalizeSsid(it) }
            .distinct()
            .sorted()
        val displayOptions = listOf(WIFI_PLACEHOLDER) + options

        ignoreWifiSelection = true
        wifiAdapter.clear()
        wifiAdapter.addAll(displayOptions)
        wifiAdapter.notifyDataSetChanged()
        val selectedIndex = selected?.let {
            val index = options.indexOf(it)
            if (index >= 0) index + 1 else 0
        } ?: 0
        binding.spinnerWifiNetwork.setSelection(selectedIndex, false)
        ignoreWifiSelection = false
        wifiOptionCount = options.size

        setWifiControlsEnabled(binding.switchWifiGate.isChecked)
        binding.tvWifiGateStatus.text = wifiStatusText(
            enabled = binding.switchWifiGate.isChecked,
            selectedWifiSsid = selected,
            hasOptions = options.isNotEmpty(),
            blockedReason = blockedReason
        )
    }

    private fun setWifiControlsEnabled(enabled: Boolean) {
        binding.btnRefreshWifi.isEnabled = enabled
        binding.spinnerWifiNetwork.isEnabled = enabled && wifiOptionCount > 0
    }

    private fun setWifiGateCheckedSilently(checked: Boolean) {
        isLoadingSettings = true
        binding.switchWifiGate.isChecked = checked
        isLoadingSettings = false
    }

    private fun wifiStatusText(
        enabled: Boolean,
        selectedWifiSsid: String?,
        hasOptions: Boolean,
        blockedReason: WifiNetworkGate.BlockedReason?
    ): String {
        if (!enabled) return "Background scans are not limited by Wi-Fi"
        return when (blockedReason) {
            WifiNetworkGate.BlockedReason.MISSING_LOCATION_PERMISSION ->
                "Location permission is needed to detect Wi-Fi networks"
            WifiNetworkGate.BlockedReason.LOCATION_DISABLED ->
                "Location is off. Turn on Location to detect Wi-Fi networks"
            WifiNetworkGate.BlockedReason.WIFI_DISABLED ->
                "Wi-Fi is off. Turn on Wi-Fi to select a network"
            WifiNetworkGate.BlockedReason.NO_SELECTED_NETWORK ->
                "Choose a Wi-Fi network for background scans"
            WifiNetworkGate.BlockedReason.TARGET_NOT_FOUND ->
                "Selected Wi-Fi is not nearby"
            null -> when {
                selectedWifiSsid != null -> "Background scans will run near $selectedWifiSsid"
                hasOptions -> "Choose a Wi-Fi network for background scans"
                else -> "No Wi-Fi networks found. Tap Refresh"
            }
        }
    }

    private fun hasWifiLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
