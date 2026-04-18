package com.htn.fishcare.control.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
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
import kotlinx.coroutines.delay

@Composable
fun FanControlRoute(
    modifier: Modifier = Modifier,
    viewModel: FanControlViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.message, uiState.error) {
        if (uiState.message != null || uiState.error != null) {
            delay(2500)
            viewModel.clearMessage()
        }
    }

    FanControlScreen(
        modifier = modifier,
        uiState = uiState,
        onToggleFan = viewModel::toggleFan
    )
}

@Composable
fun FanControlScreen(
    modifier: Modifier = Modifier,
    uiState: FanControlUiState,
    onToggleFan: (Boolean) -> Unit
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
                            text = "Oxy & Quạt Nước",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Điều khiển thiết bị sục khí và làm mát nước",
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

                if (uiState.isAutoActive) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "⚠️ HỆ THỐNG TỰ KÍCH HOẠT: Quạt đang chạy do cảm biến vượt ngưỡng!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    ControlCard(
                        title = "Trạng thái Quạt",
                        subtitle = if (uiState.isFanOn) "Quạt đang chạy" else "Quạt đang tắt",
                        isActive = uiState.isFanOn,
                        action = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (uiState.isFanOn) "ĐANG BẬT" else "ĐANG TẮT",
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isFanOn) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                                Switch(
                                    checked = uiState.isFanOn,
                                    onCheckedChange = onToggleFan,
                                    enabled = !uiState.isUpdating
                                )
                            }
                        }
                    )
                }

                item {
                    AutomationSettingsSection()
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Lưu ý",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Hệ thống sẽ tự động bật quạt nếu nhiệt độ vượt ngưỡng hoặc chất lượng nước kém khi ESP32 ở chế độ Offline.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isUpdating) {
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
                    Text("Đang xử lý...")
                }
            }
        }

        FloatingActionButton(
            onClick = viewModel::startVoiceCommand,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            containerColor = if (uiState.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = if (uiState.isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Giọng nói",
                tint = Color.White
            )
        }

        if (uiState.isListening || uiState.voiceText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .fillMaxWidth(0.8f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = if (uiState.isListening) "Đang lắng nghe..." else "Bạn nói: ${uiState.voiceText}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
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
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
