package com.btnotifier.ui.finder

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.btnotifier.R
import com.btnotifier.data.DetectionSource
import com.btnotifier.data.history.ObservationRepository
import com.btnotifier.databinding.FragmentFinderBinding
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.scanner.ScanLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FinderFragment : Fragment() {
    private var _binding: FragmentFinderBinding? = null
    private val binding get() = _binding!!
    private val scanner by lazy { BluetoothScanner.getInstance(requireContext()) }
    private var lease: ScanLease? = null
    private var signal = FinderSignalModel()
    private var observationsJob: Job? = null
    private var timerJob: Job? = null
    private var statusJob: Job? = null
    private var lastObservationAt = Long.MIN_VALUE
    private val address get() = arguments?.getString("address").orEmpty().trim()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (isScreenStarted()) {
            if (scanner.hasPermissions) requestSearch()
            else binding.tvStatus.setText(R.string.finder_permissions_required)
        }
    }
    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (isScreenStarted()) {
            if (scanner.isBluetoothEnabled) requestSearch()
            else binding.tvStatus.setText(R.string.finder_bluetooth_required)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFinderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvDeviceName.text = arguments?.getString("displayName").orEmpty().ifBlank { address }
        binding.tvAddress.text = address
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnSearch.isEnabled = address.isNotBlank()
        if (address.isBlank()) binding.tvStatus.setText(R.string.finder_no_target)
        binding.btnSearch.setOnClickListener {
            if (lease != null) stopSearch(getString(R.string.finder_stopped)) else requestSearch()
        }
        binding.btnAppSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.feature_settings_unavailable, Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Toast.makeText(requireContext(), R.string.feature_settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isScreenStarted(): Boolean = _binding != null &&
        viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun requestSearch() {
        if (!isScreenStarted() || address.isBlank()) return
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissions.launch(missing.toTypedArray())
        } else if (!scanner.isBluetoothEnabled) {
            try {
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: ActivityNotFoundException) {
                binding.tvStatus.setText(R.string.finder_bluetooth_required)
            } catch (_: SecurityException) {
                binding.tvStatus.setText(R.string.finder_permissions_required)
            }
        } else startSearch()
    }

    private fun startSearch() {
        if (lease != null) return
        val acquired = scanner.acquire("finder", setOf(address))
        if (acquired == null) {
            binding.tvStatus.text = getString(R.string.finder_failed,
                scanner.status.value.error ?: getString(R.string.finder_bluetooth_required))
            return
        }
        lease = acquired
        signal = FinderSignalModel()
        lastObservationAt = Long.MIN_VALUE
        binding.btnSearch.setText(R.string.finder_stop)
        renderSignal()
        val repository = ObservationRepository.get(requireContext())
        observationsJob = viewLifecycleOwner.lifecycleScope.launch {
            acquired.devices.collect { devices ->
                val device = devices.values.firstOrNull { it.address.equals(address, ignoreCase = true) }
                    ?: return@collect
                if (device.timestamp <= lastObservationAt) return@collect
                lastObservationAt = device.timestamp
                signal.add(device.rssi, SystemClock.elapsedRealtime())
                renderSignal()
                try {
                    repository.record(listOf(device), DetectionSource.FINDER)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    binding.tvStatus.text = getString(R.string.finder_failed, error.localizedMessage ?: error.javaClass.simpleName)
                }
            }
        }
        statusJob = viewLifecycleOwner.lifecycleScope.launch {
            scanner.status.collect { status ->
                if (!scanner.hasPermissions || !scanner.isBluetoothEnabled || !status.isScanning) {
                    stopSearch(getString(R.string.finder_failed,
                        status.error ?: getString(R.string.finder_bluetooth_required)))
                } else if (status.error != null) {
                    binding.tvStatus.text = getString(R.string.finder_failed, status.error)
                }
            }
        }
        val until = SystemClock.elapsedRealtime() + 60_000L
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (lease === acquired) {
                val remaining = until - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    stopSearch(getString(R.string.finder_finished))
                    break
                }
                if (scanner.status.value.error == null) {
                    binding.tvStatus.text = getString(R.string.finder_searching, (remaining + 999L) / 1_000L)
                }
                renderSignal()
                delay(1_000L)
            }
        }
    }

    private fun renderSignal() {
        val current = signal.snapshot(SystemClock.elapsedRealtime())
        val reading = current.smoothedRssi
        val text = if (reading == null) getString(R.string.finder_signal_none) else {
            val proximity = when (current.proximity) {
                SignalProximity.STRONG -> R.string.finder_strong
                SignalProximity.MODERATE -> R.string.finder_moderate
                else -> R.string.finder_weak
            }
            val movement = when (current.direction) {
                SignalDirection.STRONGER -> R.string.finder_stronger
                SignalDirection.WEAKER -> R.string.finder_weaker
                SignalDirection.STEADY -> R.string.finder_steady
                else -> R.string.finder_direction_unknown
            }
            getString(R.string.finder_signal, reading.roundToInt(), getString(proximity), getString(movement))
        }
        if (binding.tvSignal.text.toString() != text) binding.tvSignal.text = text
        binding.signalGraph.setSignal(current, text)
    }

    private fun stopSearch(message: String) {
        observationsJob?.cancel()
        observationsJob = null
        timerJob?.cancel()
        timerJob = null
        statusJob?.cancel()
        statusJob = null
        val oldLease = lease
        lease = null
        oldLease?.let { scanner.release(it) }
        _binding?.let {
            it.btnSearch.setText(R.string.finder_start)
            it.tvStatus.text = message
            it.tvSignal.setText(R.string.finder_signal_none)
            it.signalGraph.setSignal(signal.snapshot(Long.MAX_VALUE), getString(R.string.finder_signal_none))
        }
    }

    override fun onStop() {
        stopSearch(getString(R.string.finder_stopped))
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
