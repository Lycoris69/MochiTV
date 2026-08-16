package com.lycoris.mochitv.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.data.settings.AppSettingsRepository
import com.lycoris.mochitv.domain.model.AccentColor
import com.lycoris.mochitv.domain.model.AppFont
import com.lycoris.mochitv.domain.model.AppSettings
import com.lycoris.mochitv.domain.model.FontScale
import com.lycoris.mochitv.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: AppSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) =
        viewModelScope.launch { repo.setThemeMode(mode) }

    fun setAccentColor(color: AccentColor) =
        viewModelScope.launch { repo.setAccentColor(color) }

    fun setAppFont(font: AppFont) =
        viewModelScope.launch { repo.setAppFont(font) }

    fun setFontScale(scale: FontScale) =
        viewModelScope.launch { repo.setFontScale(scale) }
}
