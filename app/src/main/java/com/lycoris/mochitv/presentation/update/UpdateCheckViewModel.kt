package com.lycoris.mochitv.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.data.update.UpdateChecker
import com.lycoris.mochitv.domain.model.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateCheckState {
    data object Idle      : UpdateCheckState
    data object Checking  : UpdateCheckState
    data object UpToDate  : UpdateCheckState
    data class  Available (val info: UpdateInfo) : UpdateCheckState
    data class  Error     (val message: String)  : UpdateCheckState
}

@HiltViewModel
class UpdateCheckViewModel @Inject constructor(
    private val checker: UpdateChecker,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    init {
        // Silently check on first launch.  Errors are suppressed so a network
        // issue at startup never surfaces as a visible error to the user.
        checkForUpdate(silent = true)
    }

    /** Triggered explicitly from the Settings screen "Vérifier" button. */
    fun checkForUpdate(silent: Boolean = false) {
        if (_state.value == UpdateCheckState.Checking) return
        viewModelScope.launch {
            _state.value = UpdateCheckState.Checking
            _state.value = runCatching { checker.checkForUpdate() }
                .fold(
                    onSuccess = { info ->
                        if (info != null) UpdateCheckState.Available(info)
                        else UpdateCheckState.UpToDate
                    },
                    onFailure = { e ->
                        if (silent) UpdateCheckState.Idle
                        else UpdateCheckState.Error(e.message ?: "Erreur inconnue")
                    },
                )
        }
    }

    /** Called when the user taps "Plus tard" — hides the dialog. */
    fun dismissUpdate() {
        if (_state.value is UpdateCheckState.Available) {
            _state.value = UpdateCheckState.Idle
        }
    }
}
