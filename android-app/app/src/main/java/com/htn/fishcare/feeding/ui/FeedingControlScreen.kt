package com.htn.fishcare.feeding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.htn.fishcare.feeding.data.FeedMode

@Composable
fun FeedingControlRoute(
    modifier: Modifier = Modifier,
    viewModel: FeedingControlViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.message, uiState.error) {
        if (uiState.message != null || uiState.error != null) {
            // Keep banner visible for a short time, then clear.
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    FeedingControlScreen(
        modifier = modifier,
        uiState = uiState,
        onGramInputChange = viewModel::onGramInputChange,
        onSetAuto = viewModel::setAuto,
        onSetManual = viewModel::setManual,
        onStartGram = viewModel::startGramMode,
        onStopGram = viewModel::stopGramMode
    )
}

@Composable
fun FeedingControlScreen(
    modifier: Modifier = Modifier,
    uiState: FeedingControlUiState,
    onGramInputChange: (String) -> Unit,
    onSetAuto: (Boolean) -> Unit,
    onSetManual: (Boolean) -> Unit,
    onStartGram: () -> Unit,
    onStopGram: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF4FBF7), Color(0xFFE8F1FF))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                        Text(
                            text = "Feeding Control",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tuy chinh che do cho an: Auto, Manual, Dinh luong",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    StatusBanner(
                        message = uiState.message,
                        error = uiState.error
                    )
                }

                item {
                    ControlCard(
                        title = "Auto",
                        subtitle = "He thong tu cho an theo lich 06:00 va 17:00",
                        isActive = uiState.controlState.mode == FeedMode.AUTO,
                        action = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Bat/Tat")
                                Switch(
                                    checked = uiState.controlState.mode == FeedMode.AUTO && uiState.controlState.isEnabled,
                                    onCheckedChange = onSetAuto,
                                    enabled = !uiState.isSaving
                                )
                            }
                        }
                    )
                }

                item {
                    ControlCard(
                        title = "Manual",
                        subtitle = "Dieu khien dong co cho an bang tay",
                        isActive = uiState.controlState.mode == FeedMode.MANUAL,
                        action = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onSetManual(true) },
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Bat")
                                }
                                TextButton(
                                    onClick = { onSetManual(false) },
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Tat")
                                }
                            }
                        }
                    )
                }

                item {
                    ControlCard(
                        title = "Dinh luong",
                        subtitle = "Nhap so gram va gui lenh cho an",
                        isActive = uiState.controlState.mode == FeedMode.GRAM,
                        action = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = uiState.gramInput,
                                    onValueChange = onGramInputChange,
                                    label = { Text("So gram") },
                                    singleLine = true,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onStartGram,
                                        enabled = !uiState.isSaving,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cho an")
                                    }
                                    TextButton(
                                        onClick = onStopGram,
                                        enabled = !uiState.isSaving,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Dung")
                                    }
                                }
                            }
                        }
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Trang thai hien tai",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = "Mode: ${uiState.controlState.mode.name}")
                            Text(text = "State: ${if (uiState.controlState.isEnabled) "ON" else "OFF"}")
                            Text(text = "Target gram: ${"%.1f".format(uiState.controlState.targetGram)}")
                        }
                    }
                }
            }
        }

        if (uiState.isSaving) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Dang gui lenh...")
                }
            }
        }
    }
}

@Composable
private fun ControlCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    action: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action()
        }
    }
}

@Composable
private fun StatusBanner(message: String?, error: String?) {
    when {
        error != null -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE3E3)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF7A1111)
                )
            }
        }

        message != null -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F6EC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF0E5A2A)
                )
            }
        }
    }
}
