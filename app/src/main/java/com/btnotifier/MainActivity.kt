package com.btnotifier

import android.Manifest
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.btnotifier.data.PreferencesManager
import com.btnotifier.databinding.ActivityMainBinding
import com.btnotifier.service.BluetoothScanService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Request all required permissions at launch; start the service only after the
    // Bluetooth permissions are decided (granted or denied - we handle both cases).
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Always attempt - startBackgroundService() guards internally.
        startBackgroundServiceIfPermitted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars()

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNavigation.setupWithNavController(navHost.navController)

        requestPermissionsAndStartService()
    }

    private fun configureSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            windowInsets
        }

        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    private fun requestPermissionsAndStartService() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            startBackgroundServiceIfPermitted()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    /**
     * Starts the foreground service only when:
     * 1. The Bluetooth permission required by connectedDevice foreground-service type is granted.
     * 2. The user hasn't toggled the service off in Settings.
     */
    fun startBackgroundServiceIfPermitted() {
        val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
        if (!hasBluetoothPermission) return

        lifecycleScope.launch {
            val enabled = PreferencesManager(this@MainActivity).serviceEnabled.first()
            if (!enabled) return@launch
            ContextCompat.startForegroundService(
                this@MainActivity,
                Intent(this@MainActivity, BluetoothScanService::class.java).apply {
                    action = BluetoothScanService.ACTION_START
                }
            )
        }
    }
}
