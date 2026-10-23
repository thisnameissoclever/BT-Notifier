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
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.btnotifier.data.PreferencesManager
import com.btnotifier.databinding.ActivityMainBinding
import com.btnotifier.service.BluetoothScanService
import com.btnotifier.service.MonitoringServiceStarter
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var permissionRequestInFlight = false

    // Request all required permissions at launch; start the service only after the
    // Bluetooth permissions are decided (granted or denied - we handle both cases).
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequestInFlight = false
        startBackgroundServiceIfPermitted(requestMissingPermissions = false)
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

        showRequestedDestination(intent)
        requestPermissionsAndStartService()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!permissionRequestInFlight) {
            startBackgroundServiceIfPermitted(requestMissingPermissions = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showRequestedDestination(intent)
    }

    private fun showRequestedDestination(intent: Intent?) {
        if (intent?.action != NotificationHelper.ACTION_RESUME_MONITORING) return
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        if (navHost.navController.currentDestination?.id != R.id.healthFragment) {
            navHost.navController.navigate(R.id.healthFragment)
        }
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
            startBackgroundServiceIfPermitted(requestMissingPermissions = false)
        } else {
            permissionRequestInFlight = true
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    /**
     * Starts the foreground service only when:
     * 1. The required Bluetooth permissions are granted.
     * 2. The user hasn't toggled the service off in Settings.
     */
    fun startBackgroundServiceIfPermitted(requestMissingPermissions: Boolean = true) {
        if (!BluetoothScanner.getInstance(this).hasPermissions) {
            if (requestMissingPermissions && !permissionRequestInFlight) {
                requestPermissionsAndStartService()
            }
            return
        }

        lifecycleScope.launch {
            val enabled = PreferencesManager(this@MainActivity).serviceEnabled.first()
            if (!enabled || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            MonitoringServiceStarter.start(this@MainActivity, userInitiated = true)
        }
    }

    fun setBackgroundMonitoringEnabled(enabled: Boolean) {
        lifecycleScope.launch {
            val prefs = PreferencesManager(applicationContext)
            prefs.setServiceEnabled(enabled)
            if (enabled) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    startBackgroundServiceIfPermitted()
                } else {
                    prefs.setMonitoringResumeRequired(true, "Open the app to start monitoring.")
                }
            } else {
                prefs.setMonitoringResumeRequired(false, "")
                NotificationHelper.cancelResumeNotification(applicationContext)
                applicationContext.stopService(Intent(applicationContext, BluetoothScanService::class.java))
            }
        }
    }
}
