package com.htn.fishcare.control.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.htn.fishcare.control.data.AutomationRepository
import com.htn.fishcare.control.data.AutomationThresholds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AutomationUiState(
    val isLoading: Boolean = true,
    val thresholds: AutomationThresholds = AutomationThresholds(),
    val isUpdating: Boolean = false,
    val tempInput: String = "32.0",
    val turbidityInput: String = "2600.0",
    val message: String? = null,
    val error: String? = null
)

class AutomationViewModel(
    private val repository: AutomationRepository = AutomationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        observeThresholds()
    }

    private fun observeThresholds() {
        viewModelScope.launch {
            repository.observeThresholds()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không thể tải cấu hình ngưỡng"
                    )
                }
                .collect { thresholds ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        thresholds = thresholds,
                        tempInput = thresholds.tempHigh.toString(),
                        turbidityInput = thresholds.turbidityHigh.toString(),
                        error = null
                    )
                }
        }
    }

    fun onTempInputChange(value: String) {
        _uiState.value = _uiState.value.copy(tempInput = value)
    }

    fun onTurbidityInputChange(value: String) {
        _uiState.value = _uiState.value.copy(turbidityInput = value)
    }

    fun saveThresholds() {
        val temp = _uiState.value.tempInput.toFloatOrNull()
        val turbidity = _uiState.value.turbidityInput.toFloatOrNull()

        if (temp == null || turbidity == null) {
            _uiState.value = _uiState.value.copy(error = "Vui lòng nhập số hợp lệ")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, message = null, error = null)
            runCatching {
                repository.updateThresholds(AutomationThresholds(temp, turbidity))
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    message = "Đã cập nhật ngưỡng tự động"
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    error = "Cập nhật thất bại"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
