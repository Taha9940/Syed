package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "syed_settings")

class SettingsRepository(private val context: Context) {
    companion object {
        private val KEY_DOWNLOAD_FOLDER = stringPreferencesKey("download_folder")
        private val KEY_AUTO_ACCEPT = booleanPreferencesKey("auto_accept")
        private val KEY_TIMEOUT = intPreferencesKey("timeout_sec")
        private val KEY_CONFIRMATION = booleanPreferencesKey("transfer_confirm")
        private val KEY_KEEP_HISTORY = booleanPreferencesKey("keep_history")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val themeStr = prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
        AppSettings(
            defaultDownloadFolder = prefs[KEY_DOWNLOAD_FOLDER] ?: "Download/SYED",
            autoAccept = prefs[KEY_AUTO_ACCEPT] ?: false,
            connectionTimeoutSeconds = prefs[KEY_TIMEOUT] ?: 30,
            transferConfirmation = prefs[KEY_CONFIRMATION] ?: true,
            keepTransferHistory = prefs[KEY_KEEP_HISTORY] ?: true,
            themeMode = themeMode
        )
    }

    suspend fun updateSettings(newSettings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_FOLDER] = newSettings.defaultDownloadFolder
            prefs[KEY_AUTO_ACCEPT] = newSettings.autoAccept
            prefs[KEY_TIMEOUT] = newSettings.connectionTimeoutSeconds
            prefs[KEY_CONFIRMATION] = newSettings.transferConfirmation
            prefs[KEY_KEEP_HISTORY] = newSettings.keepTransferHistory
            prefs[KEY_THEME] = newSettings.themeMode.name
        }
    }
}
