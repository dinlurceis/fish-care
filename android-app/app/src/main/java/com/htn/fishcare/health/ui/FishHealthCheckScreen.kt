package com.htn.fishcare.health.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val GREEN = Color(0xFF0E5A2A)
private val GREEN_LIGHT = Color(0xFFE8F5E9)
private val GREEN_BG = Brush.verticalGradient(listOf(Color(0xFFF4FBF7), Color(0xFFE8F1FF)))

@Composable
fun FishHealthCheckRoute(
    modifier: Modifier = Modifier,
    viewModel: FishHealthCheckViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearError()
        }
    }
    FishHealthCheckScreen(
        modifier = modifier,
        uiState = uiState,
        onSelectFishType = viewModel::selectFishType,
        onToggleDropdown = viewModel::toggleFishTypeDropdown,
        onSymptomSearchChange = viewModel::onSymptomSearchChange,
        onAddSymptom = viewModel::addSymptom,
        onRemoveSymptom = viewModel::removeSymptom,
        onBehaviorChange = viewModel::updateBehaviorDescription,
        onAnalyzeClick = viewModel::analyzeFishHealth,
        onResetClick = viewModel::resetForm,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FishHealthCheckScreen(
    modifier: Modifier = Modifier,
    uiState: FishHealthUiState,
    onSelectFishType: (String) -> Unit,
    onToggleDropdown: () -> Unit,
    onSymptomSearchChange: (String) -> Unit,
    onAddSymptom: (String) -> Unit,
    onRemoveSymptom: (String) -> Unit,
    onBehaviorChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onResetClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GREEN_BG)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GREEN)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🐟 Khám bệnh cho cá", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Phân tích sức khỏe bằng AI", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── 1. Loại cá (Dropdown) ───────────────────────────────────────
            SectionCard(title = "Loại cá") {
                Box {
                    OutlinedTextField(
                        value = uiState.selectedFishType.ifBlank { "Chọn loại cá..." },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDropdown() },
                        enabled = false,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null, tint = GREEN)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = if (uiState.selectedFishType.isBlank()) Color.Gray else Color(0xFF1B2A1B),
                            disabledBorderColor = GREEN.copy(alpha = 0.5f),
                            disabledContainerColor = Color.White
                        )
                    )
                    // Invisible overlay to capture click since field is disabled
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { onToggleDropdown() }
                    )
                    DropdownMenu(
                        expanded = uiState.fishTypeExpanded,
                        onDismissRequest = { onToggleDropdown() }
                    ) {
                        FISH_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = { onSelectFishType(type) }
                            )
                        }
                    }
                }
            }

            // ── 2. Điều kiện bể (auto from sensor) ─────────────────────────
            SectionCard(title = "Điều kiện bể nước") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GREEN_LIGHT)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🌡️", fontSize = 20.sp)
                        Column {
                            Text(uiState.aquariumSummary, fontWeight = FontWeight.SemiBold, color = GREEN)
                            Text("Đọc tự động từ cảm biến", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // ── 3. Triệu chứng (search + chips) ────────────────────────────
            SectionCard(title = "Triệu chứng") {
                // Selected symptom chips
                if (uiState.selectedSymptoms.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.selectedSymptoms.forEach { symptom ->
                            SymptomChip(text = symptom, onRemove = { onRemoveSymptom(symptom) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Search box
                OutlinedTextField(
                    value = uiState.symptomSearch,
                    onValueChange = onSymptomSearchChange,
                    placeholder = { Text("Tìm triệu chứng...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = GREEN) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GREEN,
                        unfocusedBorderColor = GREEN.copy(alpha = 0.4f),
                        cursorColor = GREEN
                    ),
                    enabled = !uiState.isAnalyzing
                )

                // Popup results
                AnimatedVisibility(visible = uiState.filteredSymptoms.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column {
                            uiState.filteredSymptoms.take(6).forEach { symptom ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAddSymptom(symptom) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("+ ", color = GREEN, fontWeight = FontWeight.Bold)
                                    Text(symptom, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. Mô tả thêm ───────────────────────────────────────────────
            SectionCard(title = "Mô tả hành vi bất thường (tuỳ chọn)") {
                OutlinedTextField(
                    value = uiState.behaviorDescription,
                    onValueChange = onBehaviorChange,
                    placeholder = { Text("Mô tả chi tiết hành vi...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isAnalyzing,
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GREEN,
                        unfocusedBorderColor = GREEN.copy(alpha = 0.4f),
                        cursorColor = GREEN
                    )
                )
            }

            // ── Error ────────────────────────────────────────────────────────
            if (uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE3E3))
                ) {
                    Text(
                        text = uiState.error,
                        color = Color(0xFF7A1111),
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Analyze button ───────────────────────────────────────────────
            if (uiState.isAnalyzing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = GREEN, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Đang phân tích...", color = GREEN, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onAnalyzeClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GREEN)
                ) {
                    Text("🤖  Phân tích bằng AI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // ── AI Result ────────────────────────────────────────────────────
            if (uiState.aiDiagnosis != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = GREEN_LIGHT),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "📋 Kết quả phân tích",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = GREEN
                        )
                        Text(
                            text = uiState.aiDiagnosis,
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            color = Color(0xFF1B2A1B)
                        )
                        Button(
                            onClick = onResetClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GREEN)
                        ) {
                            Text("Khám cá khác", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GREEN)
            content()
        }
    }
}

@Composable
private fun SymptomChip(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF0E5A2A))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp)
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Xóa", tint = Color.White, modifier = Modifier.size(10.dp))
        }
    }
}
