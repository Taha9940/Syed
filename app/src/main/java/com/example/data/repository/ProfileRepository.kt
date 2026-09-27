package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "syed_profile")

class ProfileRepository(private val context: Context) {
    companion object {
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_PHOTO_URI = stringPreferencesKey("photo_uri")
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            displayName = prefs[KEY_DISPLAY_NAME] ?: "Taha User",
            photoUri = prefs[KEY_PHOTO_URI],
            deviceName = prefs[KEY_DEVICE_NAME] ?: (android.os.Build.MODEL ?: "Android Device")
        )
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun saveProfile(name: String, photoUri: String?, deviceName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = name
            if (photoUri != null) {
                prefs[KEY_PHOTO_URI] = photoUri
            } else {
                prefs.remove(KEY_PHOTO_URI)
            }
            prefs[KEY_DEVICE_NAME] = deviceName
        }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
    }
}
