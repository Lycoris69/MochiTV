package com.lycoris.mochitv.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lycoris.mochitv.domain.model.AccentColor
import com.lycoris.mochitv.domain.model.AppFont
import com.lycoris.mochitv.domain.model.AppSettings
import com.lycoris.mochitv.domain.model.FontScale
import com.lycoris.mochitv.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    companion object {
        private val KEY_THEME      = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT     = stringPreferencesKey("accent_color")
        private val KEY_FONT       = stringPreferencesKey("app_font")
        private val KEY_FONT_SCALE = stringPreferencesKey("font_scale")
    }

    /**
     * Live stream of the current [AppSettings].  Emits immediately on
     * collection with whatever is persisted, then again on every change.
     * Falls back to the [AppSettings] defaults when a key is absent.
     */
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode   = prefs[KEY_THEME]?.runCatching { ThemeMode.valueOf(this) }?.getOrNull()
                ?: ThemeMode.SYSTEM,
            accentColor = prefs[KEY_ACCENT]?.runCatching { AccentColor.valueOf(this) }?.getOrNull()
                ?: AccentColor.VIOLET,
            appFont     = prefs[KEY_FONT]?.runCatching { AppFont.valueOf(this) }?.getOrNull()
                ?: AppFont.NUNITO,
            fontScale   = prefs[KEY_FONT_SCALE]?.runCatching { FontScale.valueOf(this) }?.getOrNull()
                ?: FontScale.NORMAL,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { it[KEY_ACCENT] = color.name }
    }

    suspend fun setAppFont(font: AppFont) {
        dataStore.edit { it[KEY_FONT] = font.name }
    }

    suspend fun setFontScale(scale: FontScale) {
        dataStore.edit { it[KEY_FONT_SCALE] = scale.name }
    }
}
