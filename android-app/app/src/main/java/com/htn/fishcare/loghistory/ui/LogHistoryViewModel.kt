package com.htn.fishcare.loghistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.htn.fishcare.loghistory.data.LogHistoryRepository
import com.htn.fishcare.loghistory.model.FeedLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class LogHistoryUiState(
    val isLoading: Boolean = true,
    val logs: List<FeedLogEntry> = emptyList(),
    val errorMessage: String? = null
)

class LogHistoryViewModel(
    private val repository: LogHistoryRepository = LogHistoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogHistoryUiState())
    val uiState: StateFlow<LogHistoryUiState> = _uiState.asStateFlow()

    init {
        observeLogs()
    }

    private fun observeLogs() {
        viewModelScope.launch {
            repository.observeLogs()
                .catch {
                    _uiState.value = LogHistoryUiState(
                        isLoading = false,
                        logs = emptyList(),
                        errorMessage = "Loi tai du lieu"
                    )
                }
                .collect { logs ->
                    _uiState.value = LogHistoryUiState(
                        isLoading = false,
                        logs = logs,
                        errorMessage = null
                    )
                }
        }
    }
}
