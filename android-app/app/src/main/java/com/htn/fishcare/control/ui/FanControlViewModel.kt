package com.htn.fishcare.control.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.htn.fishcare.control.data.FanControlRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class FanControlUiState(
    val isLoading: Boolean = true,
    val isFanOn: Boolean = false,
    val isAutoActive: Boolean = false,
    val isUpdating: Boolean = false,
    val isListening: Boolean = false,
    val voiceText: String = "",
    val message: String? = null,
    val error: String? = null,
    val currentTemp: Float = 28.5f,
    val tempThreshold: Float = 30.0f,
    val currentWaterQuality: Int = 85,
    val waterQualityThreshold: Int = 70
)

@HiltViewModel
class FanControlViewModel @Inject constructor(
    private val repository: FanControlRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FanControlUiState())
    val uiState: StateFlow<FanControlUiState> = _uiState.asStateFlow()

    // TODO: Refactor VoiceRecognizer to work with dependency injection
    // private val voiceRecognizer = VoiceRecognizer(application)

    init {
        observeFanState()
        observeAutoActiveState()
        // observeVoiceResults() // TODO: Re-enable when VoiceRecognizer is refactored
    }

    private fun observeVoiceResults() {
        // TODO: Implement voice recognition with proper DI
        /*
        viewModelScope.launch {
            voiceRecognizer.isListening.collect { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }
        viewModelScope.launch {
            voiceRecognizer.recognizedText.collectLatest { text ->
                if (text.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(voiceText = text)
                    processVoiceCommand(text)
                }
            }
        }
        viewModelScope.launch {
            voiceRecognizer.error.collect { err ->
                if (err != null) {
                    _uiState.value = _uiState.value.copy(error = err)
                }
            }
        }
        */
    }

    fun startVoiceCommand() {
        // voiceRecognizer.startListening() // TODO: Re-enable when VoiceRecognizer is refactored
    }

    private fun processVoiceCommand(command: String) {
        val cmd = command.lowercase()
        when {
            cmd.contains("bật") && (cmd.contains("quạt") || cmd.contains("oxy")) -> {
                toggleFan(true)
            }
            cmd.contains("tắt") && (cmd.contains("quạt") || cmd.contains("oxy")) -> {
                toggleFan(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // voiceRecognizer.destroy()
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

    fun updateTempThreshold(newThreshold: Float) {
        val updatedThreshold = String.format("%.1f", newThreshold).toFloat()
        _uiState.value = _uiState.value.copy(tempThreshold = updatedThreshold)
        
        // AUTO-SAVE: Gọi repository để cập nhật lên Firebase/ESP32 ngay lập tức
        viewModelScope.launch {
            runCatching { repository.updateTempThreshold(updatedThreshold) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Không thể lưu ngưỡng nhiệt độ") }
        }
    }

    fun updateWaterQualityThreshold(newThreshold: Int) {
        val clampedThreshold = newThreshold.coerceIn(0, 100)
        _uiState.value = _uiState.value.copy(waterQualityThreshold = clampedThreshold)
        
        // AUTO-SAVE: Gọi repository ngay lập tức
        viewModelScope.launch {
            runCatching { repository.updateWaterThreshold(clampedThreshold) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Không thể lưu ngưỡng chất lượng nước") }
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
