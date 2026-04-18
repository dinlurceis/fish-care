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

data class FanControlUiState(
    val isLoading: Boolean = true,
    val isFanOn: Boolean = false,
    val isAutoActive: Boolean = false,
    val isUpdating: Boolean = false,
    val isListening: Boolean = false,
    val voiceText: String = "",
    val message: String? = null,
    val error: String? = null
)

class FanControlViewModel(
    application: Application,
    private val repository: FanControlRepository = FanControlRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FanControlUiState())
    val uiState: StateFlow<FanControlUiState> = _uiState.asStateFlow()

    private val voiceRecognizer = VoiceRecognizer(application)

    init {
        observeFanState()
        observeAutoActiveState()
        observeVoiceResults()
    }

    private fun observeVoiceResults() {
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
    }

    fun startVoiceCommand() {
        voiceRecognizer.startListening()
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
        voiceRecognizer.destroy()
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
