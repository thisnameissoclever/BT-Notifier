package com.btnotifier.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val SCAN_INTERVAL_MINUTES = intPreferencesKey("scan_interval_minutes")
        val SCAN_DURATION_SECONDS = intPreferencesKey("scan_duration_seconds")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val NOTIFY_ON_FIND = booleanPreferencesKey("notify_on_find")
        val WIFI_SCAN_GATE_ENABLED = booleanPreferencesKey("wifi_scan_gate_enabled")
        val SELECTED_WIFI_SSID = stringPreferencesKey("selected_wifi_ssid")

        private val LAST_BACKGROUND_SCAN_AT = longPreferencesKey("last_background_scan_at")
        private val LAST_BACKGROUND_DEVICE_COUNT = intPreferencesKey("last_background_device_count")
        private val MONITORING_RESUME_REQUIRED = booleanPreferencesKey("monitoring_resume_required")
        private val MONITORING_MESSAGE = stringPreferencesKey("monitoring_message")

        const val DEFAULT_SCAN_INTERVAL = 5
        const val DEFAULT_SCAN_DURATION = 15
    }

    val scanIntervalMinutes: Flow<Int> = context.dataStore.data
        .map { it[SCAN_INTERVAL_MINUTES] ?: DEFAULT_SCAN_INTERVAL }.distinctUntilChanged()

    val scanDurationSeconds: Flow<Int> = context.dataStore.data
        .map { it[SCAN_DURATION_SECONDS] ?: DEFAULT_SCAN_DURATION }.distinctUntilChanged()

    val serviceEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[SERVICE_ENABLED] ?: true }.distinctUntilChanged()

    val notifyOnFind: Flow<Boolean> = context.dataStore.data
        .map { it[NOTIFY_ON_FIND] ?: true }.distinctUntilChanged()

    val wifiScanGateEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[WIFI_SCAN_GATE_ENABLED] ?: false }.distinctUntilChanged()

    val selectedWifiSsid: Flow<String> = context.dataStore.data
        .map { it[SELECTED_WIFI_SSID] ?: "" }.distinctUntilChanged()

    val lastBackgroundScanAt: Flow<Long> = context.dataStore.data
        .map { it[LAST_BACKGROUND_SCAN_AT] ?: 0L }.distinctUntilChanged()
    val lastBackgroundDeviceCount: Flow<Int> = context.dataStore.data
        .map { it[LAST_BACKGROUND_DEVICE_COUNT] ?: 0 }.distinctUntilChanged()
    val monitoringResumeRequired: Flow<Boolean> = context.dataStore.data
        .map { it[MONITORING_RESUME_REQUIRED] ?: false }.distinctUntilChanged()
    val monitoringMessage: Flow<String> = context.dataStore.data
        .map { it[MONITORING_MESSAGE] ?: "" }.distinctUntilChanged()

    suspend fun recordBackgroundScan(at: Long, count: Int) {
        context.dataStore.edit {
            it[LAST_BACKGROUND_SCAN_AT] = at
            it[LAST_BACKGROUND_DEVICE_COUNT] = count
        }
    }

    suspend fun setMonitoringResumeRequired(required: Boolean, message: String) {
        context.dataStore.edit {
            it[MONITORING_RESUME_REQUIRED] = required
            it[MONITORING_MESSAGE] = message
        }
    }

    suspend fun setScanIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[SCAN_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setScanDurationSeconds(seconds: Int) {
        context.dataStore.edit { it[SCAN_DURATION_SECONDS] = seconds }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SERVICE_ENABLED] = enabled }
    }

    suspend fun setNotifyOnFind(notify: Boolean) {
        context.dataStore.edit { it[NOTIFY_ON_FIND] = notify }
    }

    suspend fun setWifiScanGateEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WIFI_SCAN_GATE_ENABLED] = enabled }
    }

    suspend fun setSelectedWifiSsid(ssid: String) {
        context.dataStore.edit { it[SELECTED_WIFI_SSID] = ssid.trim() }
    }
}
