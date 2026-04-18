package com.htn.fishcare.control.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.htn.fishcare.control.data.FanControlRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class FanControlUiState(
    val isLoading: Boolean = true,
    val isFanOn: Boolean = false,
    val isAutoActive: Boolean = false,
    val isUpdating: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class FanControlViewModel(
    private val repository: FanControlRepository = FanControlRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FanControlUiState())
    val uiState: StateFlow<FanControlUiState> = _uiState.asStateFlow()

    init {
        observeFanState()
        observeAutoActiveState()
    }

    fun toggleFan(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, message = null, error = null)
            runCatching { repository.setFanState(enabled) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        message = if (enabled) "Đã bật quạt" else "Đã tắt quạt"
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        error = "Lỗi khi điều khiển quạt"
                    )
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    private fun observeAutoActiveState() {
        viewModelScope.launch {
            repository.observeAutoActiveState()
                .collect { isAutoActive ->
                    _uiState.value = _uiState.value.copy(isAutoActive = isAutoActive)
                }
        }
    }

    private fun observeFanState() {
        viewModelScope.launch {
            repository.observeFanState()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không thể đọc trạng thái quạt"
                    )
                }
                .collect { isFanOn ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isFanOn = isFanOn,
                        error = null
                    )
                }
        }
    }
}
