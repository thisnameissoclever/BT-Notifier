package com.btnotifier.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.btnotifier.data.PreferencesManager
import com.btnotifier.databinding.FragmentSettingsBinding
import com.btnotifier.service.BluetoothScanService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferencesManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PreferencesManager(requireContext())
        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            val interval = prefs.scanIntervalMinutes.first()
            val duration = prefs.scanDurationSeconds.first()
            val serviceEnabled = prefs.serviceEnabled.first()
            val notifyEnabled = prefs.notifyOnFind.first()

            binding.seekInterval.progress = interval - 1  // 1-60 min, 0-indexed
            binding.tvIntervalValue.text = "${interval} min"
            binding.seekDuration.progress = (duration / 5) - 1  // 5,10,15..60s
            binding.tvDurationValue.text = "${duration} sec"
            binding.switchService.isChecked = serviceEnabled
            binding.switchNotify.isChecked = notifyEnabled
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
            lifecycleScope.launch { prefs.setNotifyOnFind(isChecked) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
