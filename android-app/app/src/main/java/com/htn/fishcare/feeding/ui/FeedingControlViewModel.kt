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
    val selectedMode: FeedMode = FeedMode.GRAM,   // mode đang được mở rộng trên UI
    val message: String? = null,
    val error: String? = null,
    val isListeningVoice: Boolean = false,
    val voiceHint: String = ""
)

class FeedingControlViewModel(
    private val repository: FeedingControlRepository = FeedingControlRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedingControlUiState())
    val uiState: StateFlow<FeedingControlUiState> = _uiState.asStateFlow()

    init {
        observeControlState()
    }

    fun selectMode(mode: FeedMode) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun onGramInputChange(value: String) {
        val normalized = value.filter { it.isDigit() || it == '.' }
        _uiState.value = _uiState.value.copy(gramInput = normalized)
    }

    fun setAuto(enabled: Boolean) = executeAction("Đã cập nhật chế độ Tự động") {
        repository.setAutoMode(enabled)
    }

    fun setManual(enabled: Boolean) = executeAction(
        if (enabled) "Đã bật máy cho ăn thủ công" else "Đã tắt máy cho ăn"
    ) {
        repository.setManualMode(enabled)
    }

    fun startGramMode() {
        val target = _uiState.value.gramInput.toFloatOrNull()
        if (target == null || target <= 0f) {
            _uiState.value = _uiState.value.copy(error = "Nhập số gram hợp lệ (> 0)")
            return
        }
        executeAction("Đã gửi lệnh định lượng ${String.format("%.1f", target)}g") {
            repository.startGramMode(target)
        }
    }

    fun stopGramMode() = executeAction("Đã dừng chế độ định lượng") {
        repository.stopGramMode()
    }

    // ── Voice command parsing ────────────────────────────────────────────────
    /**
     * Gọi khi nhận được text từ speech recognition.
     * Ví dụ: "cho ăn 10 gram" → set gramInput = "10" rồi gọi startGramMode
     */
    fun processVoiceCommand(text: String) {
        _uiState.value = _uiState.value.copy(voiceHint = "Bạn nói: \"$text\"", isListeningVoice = false)
        val lower = text.lowercase()
        // Extract number from text like "cho ăn 10 gram", "10g", "feed 15"
        val gram = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:g|gram|gam)?").find(lower)?.groupValues?.getOrNull(1)
        if (gram != null && gram.toFloatOrNull() != null) {
            _uiState.value = _uiState.value.copy(gramInput = gram, selectedMode = FeedMode.GRAM)
            startGramMode()
        } else {
            when {
                lower.contains("tự động") || lower.contains("auto") -> {
                    _uiState.value = _uiState.value.copy(selectedMode = FeedMode.AUTO)
                    setAuto(true)
                }
                lower.contains("thủ công") || lower.contains("manual") -> {
                    _uiState.value = _uiState.value.copy(selectedMode = FeedMode.MANUAL)
                    setManual(true)
                }
                else -> _uiState.value = _uiState.value.copy(
                    error = "Không nhận ra lệnh. Thử: \"Cho ăn 10 gram\""
                )
            }
        }
    }

    fun setListeningVoice(listening: Boolean) {
        _uiState.value = _uiState.value.copy(isListeningVoice = listening, voiceHint = if (listening) "" else _uiState.value.voiceHint)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null, voiceHint = "")
    }

    private fun observeControlState() {
        viewModelScope.launch {
            repository.observeControlState()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không đọc được trạng thái cho ăn"
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
                    _uiState.value = _uiState.value.copy(isSaving = false, message = successMessage, error = null)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isSaving = false, message = null, error = "Gửi lệnh thất bại")
                }
        }
    }
}
