@file:OptIn(ExperimentalMaterial3Api::class)
package com.htn.fishcare.loghistory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.htn.fishcare.loghistory.model.FeedLogEntry
import java.util.Locale

@Composable
fun LogHistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: LogHistoryViewModel = viewModel(),
    onBackClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    LogHistoryScreen(
        modifier = modifier,
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onDateFilterChange = viewModel::onDateFilterChange,
        onBackClick = onBackClick
    )
}

@Composable
fun LogHistoryScreen(
    modifier: Modifier = Modifier,
    uiState: LogHistoryUiState,
    onSearchQueryChange: (String) -> Unit,
    onDateFilterChange: (String?) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Lịch sử cho ăn", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Trở về")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0E5A2A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Đang tải lịch sử...",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Không tải được dữ liệu lịch sử",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            uiState.filteredLogs.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (uiState.logs.isEmpty()) {
                            "Chưa có dữ liệu lịch sử nào"
                        } else {
                            "Không có kết quả phù hợp"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Tìm kiếm & Lọc lịch sử",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            label = { Text("Tìm theo chế độ, thời gian...") },
                            singleLine = true
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                AssistChip(
                                    onClick = { onDateFilterChange(null) },
                                    label = {
                                        Text(
                                            text = if (uiState.selectedDate == null) "Tất cả ngày *" else "Tất cả ngày"
                                        )
                                    }
                                )
                            }

                            items(uiState.availableDates) { date ->
                                val selected = uiState.selectedDate == date
                                AssistChip(
                                    onClick = { onDateFilterChange(date) },
                                    label = { Text(if (selected) "$date *" else date) }
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.filteredLogs, key = { it.id }) { log ->
                            LogHistoryItem(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogHistoryItem(log: FeedLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.id.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", log.gram)} gam",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0E5A2A)
                )
            }

            Text(
                text = "Chế độ: ${log.mode}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Thời gian: ${log.time}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
