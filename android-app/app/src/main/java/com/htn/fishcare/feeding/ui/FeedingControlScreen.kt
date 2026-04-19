package com.htn.fishcare.feeding.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.htn.fishcare.feeding.data.FeedMode
import kotlinx.coroutines.delay

private val GREEN = Color(0xFF0E5A2A)
private val GREEN_LIGHT = Color(0xFFE8F5E9)

@Composable
fun FeedingControlRoute(
    modifier: Modifier = Modifier,
    viewModel: FeedingControlViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // voice launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            viewModel.processVoiceCommand(text)
        } else {
            viewModel.setListeningVoice(false)
        }
    }

    LaunchedEffect(uiState.message, uiState.error) {
        if (uiState.message != null || uiState.error != null) {
            delay(2500)
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
        onStopGram = viewModel::stopGramMode,
        onModeSelect = viewModel::selectMode,
        onVoiceClick = {
            viewModel.setListeningVoice(true)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói: \"Cho ăn 10 gram\"")
            }
            voiceLauncher.launch(intent)
        }
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
    onStopGram: () -> Unit,
    onModeSelect: (FeedMode) -> Unit,
    onVoiceClick: () -> Unit
) {
    val gradient = Brush.verticalGradient(listOf(Color(0xFFF4FBF7), Color(0xFFE8F1FF)))

    Box(modifier = modifier.fillMaxSize().background(gradient)) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = GREEN)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                        Text(
                            text = "Điều khiển Cho ăn",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2C3E50)
                        )
                        Text(
                            text = "Chọn chế độ và cấu hình lịch cho ăn",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }

                item { StatusBanner(message = uiState.message, error = uiState.error) }

                // Voice hint banner
                if (uiState.voiceHint.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                        ) {
                            Text(
                                text = "🎙 ${uiState.voiceHint}",
                                modifier = Modifier.padding(12.dp),
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ── Mode Tab Bar ────────────────────────────────────────────
                item {
                    ModeTabBar(
                        selectedMode = uiState.selectedMode,
                        onSelect = onModeSelect
                    )
                }

                // ── Mode Content (expandable) ──────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = uiState.selectedMode == FeedMode.AUTO,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        ModeCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("Cho ăn tự động", fontWeight = FontWeight.SemiBold, color = GREEN)
                                    Text("Hệ thống tự cho ăn lúc 06:00 và 17:00", fontSize = 12.sp, color = Color.Gray)
                                }
                                Switch(
                                    checked = uiState.controlState.mode == FeedMode.AUTO && uiState.controlState.isEnabled,
                                    onCheckedChange = onSetAuto,
                                    enabled = !uiState.isSaving,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = GREEN,
                                        checkedThumbColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.selectedMode == FeedMode.MANUAL,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        ModeCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Điều khiển tay", fontWeight = FontWeight.SemiBold, color = GREEN)
                                Text("Bật/tắt máy cho ăn ngay lập tức", fontSize = 12.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onSetManual(true) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GREEN),
                                        enabled = !uiState.isSaving
                                    ) { Text("▶ Bật", fontWeight = FontWeight.Bold) }
                                    Button(
                                        onClick = { onSetManual(false) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCFD8DC)),
                                        enabled = !uiState.isSaving
                                    ) { Text("⏹ Tắt", color = Color(0xFF2C3E50), fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.selectedMode == FeedMode.GRAM,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        ModeCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Định lượng gram", fontWeight = FontWeight.SemiBold, color = GREEN)
                                Text("Nhập lượng cám cần nhả, máy tự dừng khi đủ", fontSize = 12.sp, color = Color.Gray)
                                OutlinedTextField(
                                    value = uiState.gramInput,
                                    onValueChange = onGramInputChange,
                                    label = { Text("Số gram") },
                                    singleLine = true,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GREEN,
                                        cursorColor = GREEN
                                    ),
                                    trailingIcon = { Text("g", color = GREEN, fontWeight = FontWeight.Bold) }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onStartGram,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GREEN),
                                        enabled = !uiState.isSaving
                                    ) { Text("🐟 Cho ăn", fontWeight = FontWeight.Bold) }
                                    TextButton(
                                        onClick = onStopGram,
                                        enabled = !uiState.isSaving,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) { Text("⏹ Dừng", color = Color.Gray, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }

                // ── Status card ─────────────────────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = GREEN, modifier = Modifier.size(22.dp))
                            Column {
                                Text(
                                    text = "Trạng thái: ${uiState.controlState.mode.name} | ${if (uiState.controlState.isEnabled) "Đang bật" else "Đang tắt"}",
                                    fontWeight = FontWeight.SemiBold, color = GREEN, fontSize = 13.sp
                                )
                                Text(
                                    text = "Mục tiêu: ${String.format("%.1f", uiState.controlState.targetGram)} g",
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Saving indicator ─────────────────────────────────────────────────
        if (uiState.isSaving) {
            Card(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 90.dp, end = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = GREEN)
                    Text("Đang gửi lệnh...")
                }
            }
        }

        // ── Voice FAB ────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = onVoiceClick,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            containerColor = if (uiState.isListeningVoice) Color(0xFFE53935) else GREEN,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (uiState.isListeningVoice) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Giọng nói",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ModeTabBar(selectedMode: FeedMode, onSelect: (FeedMode) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            data class TabItem(val mode: FeedMode, val label: String, val emoji: String)
            listOf(
                TabItem(FeedMode.AUTO, "Tự động", "⏰"),
                TabItem(FeedMode.MANUAL, "Thủ công", "✋"),
                TabItem(FeedMode.GRAM, "Định lượng", "⚖️")
            ).forEach { tab ->
                val isSelected = selectedMode == tab.mode
                Button(
                    onClick = { onSelect(tab.mode) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) GREEN else Color.Transparent,
                        contentColor = if (isSelected) Color.White else Color.Gray
                    ),
                    elevation = ButtonDefaults.buttonElevation(if (isSelected) 4.dp else 0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.emoji, fontSize = 16.sp)
                        Text(tab.label, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}

@Composable
private fun StatusBanner(message: String?, error: String?) {
    when {
        error != null -> Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE3E3)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = error, modifier = Modifier.padding(12.dp), color = Color(0xFF7A1111))
        }
        message != null -> Card(
            colors = CardDefaults.cardColors(containerColor = GREEN_LIGHT),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = message, modifier = Modifier.padding(12.dp), color = GREEN)
        }
    }
}
