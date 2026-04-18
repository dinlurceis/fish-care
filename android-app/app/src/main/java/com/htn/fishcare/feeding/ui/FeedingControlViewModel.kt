package com.htn.fishcare.feeding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.htn.fishcare.feeding.data.FeedMode
import com.htn.fishcare.feeding.data.FeedingControlRepository
import com.htn.fishcare.feeding.data.FeedingControlState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class FeedingControlUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val controlState: FeedingControlState = FeedingControlState(),
    val gramInput: String = "30",
    val message: String? = null,
    val error: String? = null
)

class FeedingControlViewModel(
    private val repository: FeedingControlRepository = FeedingControlRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedingControlUiState())
    val uiState: StateFlow<FeedingControlUiState> = _uiState.asStateFlow()

    init {
        observeControlState()
    }

    fun onGramInputChange(value: String) {
        val normalized = value.filter { it.isDigit() || it == '.' }
        _uiState.value = _uiState.value.copy(gramInput = normalized)
    }

    fun setAuto(enabled: Boolean) = executeAction("Da cap nhat che do Auto") {
        repository.setAutoMode(enabled)
    }

    fun setManual(enabled: Boolean) = executeAction("Da cap nhat che do Manual") {
        repository.setManualMode(enabled)
    }

    fun startGramMode() {
        val target = _uiState.value.gramInput.toFloatOrNull()
        if (target == null || target <= 0f) {
            _uiState.value = _uiState.value.copy(error = "Nhap so gram hop le (> 0)")
            return
        }

        executeAction("Da gui lenh dinh luong ${"%.1f".format(target)}g") {
            repository.startGramMode(target)
        }
    }

    fun stopGramMode() = executeAction("Da dung che do Dinh luong") {
        repository.stopGramMode()
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    private fun observeControlState() {
        viewModelScope.launch {
            repository.observeControlState()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Khong doc duoc trang thai feeding"
                    )
                }
                .collect { controlState ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        controlState = controlState,
                        error = null
                    )
                }
        }
    }

    private fun executeAction(successMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null, error = null)
            runCatching { action() }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        message = successMessage,
                        error = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        message = null,
                        error = "Gui lenh that bai"
                    )
                }
        }
    }
}
