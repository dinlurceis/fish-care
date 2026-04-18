package com.htn.fishcare.control.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
        onToggleFan = viewModel::toggleFan,
        onStartVoiceCommand = viewModel::startVoiceCommand,
        onUpdateTempThreshold = viewModel::updateTempThreshold,
        onUpdateWaterThreshold = viewModel::updateWaterQualityThreshold
    )
}

@Composable
fun FanControlScreen(
    modifier: Modifier = Modifier,
    uiState: FanControlUiState,
    onToggleFan: (Boolean) -> Unit,
    onStartVoiceCommand: () -> Unit,
    onUpdateTempThreshold: (Float) -> Unit,
    onUpdateWaterThreshold: (Int) -> Unit
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
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FanStatusCard(
                            isFanOn = uiState.isFanOn,
                            isUpdating = uiState.isUpdating,
                            onToggle = { onToggleFan(!uiState.isFanOn) }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThresholdBox(
                            modifier = Modifier.weight(1f),
                            title = "Nhiệt độ",
                            currentValue = "${uiState.currentTemp}°C",
                            thresholdValue = uiState.tempThreshold,
                            unit = "°C",
                            onThresholdChange = { onUpdateTempThreshold(it) }
                        )
                        ThresholdBox(
                            modifier = Modifier.weight(1f),
                            title = "Chất lượng nước",
                            currentValue = "${uiState.currentWaterQuality}%",
                            thresholdValue = uiState.waterQualityThreshold.toFloat(),
                            unit = "%",
                            onThresholdChange = { onUpdateWaterThreshold(it.toInt()) }
                        )
                    }
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
            onClick = onStartVoiceCommand,
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun FanControlScreenPreview() {
    MaterialTheme {
        FanControlScreen(
            uiState = FanControlUiState(
                isLoading = false,
                isFanOn = true,
                isAutoActive = false,
                message = "Đã bật quạt (Chế độ xem trước)",
                currentTemp = 29.5f,
                tempThreshold = 30.0f,
                currentWaterQuality = 82,
                waterQualityThreshold = 75
            ),
            onToggleFan = {},
            onStartVoiceCommand = {},
            onUpdateTempThreshold = {},
            onUpdateWaterThreshold = {}
        )
    }
}

@Composable
private fun ThresholdBox(
    modifier: Modifier = Modifier,
    title: String,
    currentValue: String,
    thresholdValue: Float,
    unit: String,
    onThresholdChange: (Float) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(
                text = currentValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            AnimatedVisibility(visible = isExpanded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(text = "Ngưỡng kích hoạt", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { onThresholdChange(thresholdValue - 0.5f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Giảm",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Text(
                            text = "${if (thresholdValue % 1 == 0f) thresholdValue.toInt() else thresholdValue}$unit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        
                        IconButton(
                            onClick = { onThresholdChange(thresholdValue + 0.5f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tăng",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FanStatusCard(
    isFanOn: Boolean,
    isUpdating: Boolean,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FanRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .aspectRatio(1f)
            .clickable(enabled = !isUpdating) { onToggle() },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isFanOn) Color(0xFFE6F6EC) else Color(0xFFF0F0F0)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFanOn) 12.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = if (isFanOn) Color(0xFF0E5A2A).copy(alpha = 0.1f) else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Air,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .rotate(if (isFanOn) rotation else 0f),
                    tint = if (isFanOn) Color(0xFF0E5A2A) else Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isFanOn) "ĐANG BẬT" else "ĐANG TẮT",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFanOn) Color(0xFF0E5A2A) else Color.Gray
            )
            
            Text(
                text = if (isFanOn) "Chạm để tắt" else "Chạm để bật",
                style = MaterialTheme.typography.labelMedium,
                color = if (isFanOn) Color(0xFF0E5A2A).copy(alpha = 0.6f) else Color.Gray
            )
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
