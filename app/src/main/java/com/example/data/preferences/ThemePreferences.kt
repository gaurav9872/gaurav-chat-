package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AccentPalette
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chat_gaurav_settings")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPalette: AccentPalette = AccentPalette.EMERALD,
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true
)

class ThemePreferences(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeMode = prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.SYSTEM

        val accentPalette = prefs[Keys.ACCENT_PALETTE]?.let { name ->
            AccentPalette.entries.firstOrNull { it.name == name }
        } ?: AccentPalette.EMERALD

        UserSettings(
            themeMode = themeMode,
            accentPalette = accentPalette,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPalette(palette: AccentPalette) {
        context.dataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }
}
