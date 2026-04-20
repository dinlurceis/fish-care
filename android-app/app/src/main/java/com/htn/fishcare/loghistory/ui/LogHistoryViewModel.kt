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
    val filteredLogs: List<FeedLogEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedDate: String? = null,
    val availableDates: List<String> = emptyList(),
    val errorMessage: String? = null
)

class LogHistoryViewModel(
    private val repository: LogHistoryRepository = LogHistoryRepository()
) : ViewModel() {

    private var allLogs: List<FeedLogEntry> = emptyList()

    private val _uiState = MutableStateFlow(LogHistoryUiState())
    val uiState: StateFlow<LogHistoryUiState> = _uiState.asStateFlow()

    init {
        observeLogs()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun onDateFilterChange(date: String?) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        applyFilters()
    }

    private fun observeLogs() {
        viewModelScope.launch {
            repository.observeLogs()
                .catch {
                    _uiState.value = LogHistoryUiState(
                        isLoading = false,
                        logs = emptyList(),
                        filteredLogs = emptyList(),
                        errorMessage = "Loi tai du lieu"
                    )
                }
                .collect { logs ->
                    allLogs = logs
                    val currentState = _uiState.value
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        logs = logs,
                        availableDates = extractAvailableDates(logs),
                        errorMessage = null
                    )
                    applyFilters()
                }
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase()
        val selectedDate = state.selectedDate

        val filtered = allLogs.filter { entry ->
            val matchesDate = selectedDate == null || extractDate(entry.time) == selectedDate
            val matchesSearch = query.isBlank() ||
                entry.id.lowercase().contains(query) ||
                entry.mode.lowercase().contains(query) ||
                entry.time.lowercase().contains(query) ||
                entry.gram.toString().contains(query)

            matchesDate && matchesSearch
        }

        _uiState.value = state.copy(filteredLogs = filtered)
    }

    private fun extractAvailableDates(logs: List<FeedLogEntry>): List<String> {
        return logs.mapNotNull { extractDate(it.time) }
            .distinct()
    }

    private fun extractDate(timeText: String): String? {
        return timeText.substringAfter(' ', missingDelimiterValue = "").ifBlank { null }
    }
}
