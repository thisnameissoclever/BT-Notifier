package com.btnotifier.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val SCAN_INTERVAL_MINUTES = intPreferencesKey("scan_interval_minutes")
        val SCAN_DURATION_SECONDS = intPreferencesKey("scan_duration_seconds")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val NOTIFY_ON_FIND = booleanPreferencesKey("notify_on_find")
        val WIFI_SCAN_GATE_ENABLED = booleanPreferencesKey("wifi_scan_gate_enabled")
        val SELECTED_WIFI_SSID = stringPreferencesKey("selected_wifi_ssid")

        const val DEFAULT_SCAN_INTERVAL = 5
        const val DEFAULT_SCAN_DURATION = 15
    }

    val scanIntervalMinutes: Flow<Int> = context.dataStore.data
        .map { it[SCAN_INTERVAL_MINUTES] ?: DEFAULT_SCAN_INTERVAL }

    val scanDurationSeconds: Flow<Int> = context.dataStore.data
        .map { it[SCAN_DURATION_SECONDS] ?: DEFAULT_SCAN_DURATION }

    val serviceEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[SERVICE_ENABLED] ?: true }

    val notifyOnFind: Flow<Boolean> = context.dataStore.data
        .map { it[NOTIFY_ON_FIND] ?: true }

    val wifiScanGateEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[WIFI_SCAN_GATE_ENABLED] ?: false }

    val selectedWifiSsid: Flow<String> = context.dataStore.data
        .map { it[SELECTED_WIFI_SSID] ?: "" }

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
