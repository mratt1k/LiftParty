package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.liftPartyDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftparty_prefs")

class UserPreferencesRepository(private val context: Context) {

    val preferredLiftUrl: Flow<String> = context.liftPartyDataStore.data.map { prefs ->
        prefs[KEY_LIFT_URL] ?: UserProfile.DEFAULT_LIFT_URL
    }

    suspend fun setPreferredLiftUrl(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        context.liftPartyDataStore.edit { prefs ->
            prefs[KEY_LIFT_URL] = clean
        }
    }

    private companion object {
        val KEY_LIFT_URL = stringPreferencesKey("preferred_lift_url")
    }
}
