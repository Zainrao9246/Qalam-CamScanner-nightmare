package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camscan_prefs")

class ThemePreferences(private val context: Context) {

    companion object {
        private val KEY_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        private val KEY_READER_NIGHT_MODE = booleanPreferencesKey("is_reader_night_mode")
        private val KEY_STARTER_SEEDED = booleanPreferencesKey("has_seeded_starter_docs_v1")
        private val KEY_OFFICE_STARTER_SEEDED = booleanPreferencesKey("has_seeded_office_docs_v2")
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_THEME] ?: false
    }

    val isReaderNightMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_READER_NIGHT_MODE] ?: false
    }

    suspend fun hasSeededStarterDocs(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_STARTER_SEEDED] ?: false
        }.first()
    }

    suspend fun markStarterDocsSeeded() {
        context.dataStore.edit { prefs ->
            prefs[KEY_STARTER_SEEDED] = true
        }
    }

    suspend fun hasSeededOfficeStarters(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_OFFICE_STARTER_SEEDED] ?: false
        }.first()
    }

    suspend fun markOfficeStartersSeeded() {
        context.dataStore.edit { prefs ->
            prefs[KEY_OFFICE_STARTER_SEEDED] = true
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_THEME] = enabled
        }
    }

    suspend fun setReaderNightMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_READER_NIGHT_MODE] = enabled
        }
    }
}
