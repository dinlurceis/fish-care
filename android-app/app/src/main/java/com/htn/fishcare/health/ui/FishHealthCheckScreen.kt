package com.htn.fishcare.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FishHealthCheckRoute(
    modifier: Modifier = Modifier,
    viewModel: FishHealthCheckViewModel = viewModel(),
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
        onFishNameChange = viewModel::updateFishName,
        onFishTypeChange = viewModel::updateFishType,
        onAquariumConditionChange = viewModel::updateAquariumCondition,
        onBehaviorDescriptionChange = viewModel::updateBehaviorDescription,
        onSymptomToggle = viewModel::toggleSymptom,
        onAnalyzeClick = viewModel::analyzeFishHealth,
        onResetClick = viewModel::resetForm,
        onBackClick = onBackClick
    )
}

@Composable
fun FishHealthCheckScreen(
    modifier: Modifier = Modifier,
    uiState: FishHealthUiState,
    onFishNameChange: (String) -> Unit,
    onFishTypeChange: (String) -> Unit,
    onAquariumConditionChange: (String) -> Unit,
    onBehaviorDescriptionChange: (String) -> Unit,
    onSymptomToggle: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onResetClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Khám bệnh cho cá",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Fish Information Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Thông tin cá",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = uiState.fishName,
                        onValueChange = onFishNameChange,
                        label = { Text("Tên cá") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isAnalyzing,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.fishType,
                        onValueChange = onFishTypeChange,
                        label = { Text("Loại cá (VD: Cá chép, Cá vàng, ...)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isAnalyzing,
                        singleLine = true
                    )
                }
            }

            // Aquarium Condition Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Điều kiện bể nước",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = uiState.aquariumCondition,
                        onValueChange = onAquariumConditionChange,
                        label = { Text("VD: Nhiệt độ 27°C, pH 6.8, TDS 200") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isAnalyzing,
                        minLines = 2
                    )
                }
            }

            // Symptoms Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Các triệu chứng (chọn những cái thích hợp)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    uiState.symptoms.forEach { symptom ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = symptom.isSelected,
                                onCheckedChange = { onSymptomToggle(symptom.name) },
                                enabled = !uiState.isAnalyzing
                            )
                            Text(
                                text = symptom.name,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // Behavior Description Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Mô tả hành vi bất thường (nếu có)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = uiState.behaviorDescription,
                        onValueChange = onBehaviorDescriptionChange,
                        label = { Text("Mô tả chi tiết hành vi ...") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isAnalyzing,
                        minLines = 3
                    )
                }
            }

            // Error message
            if (uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFCDD2)
                    )
                ) {
                    Text(
                        text = uiState.error,
                        color = Color.Red,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // Analyze button
            if (uiState.isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = onAnalyzeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Phân tích bằng AI")
                }
            }

            // AI Diagnosis Result
            if (uiState.aiDiagnosis != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFC8E6C9)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Kết quả phân tích",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = uiState.aiDiagnosis,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(
                            onClick = onResetClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Khám cá khác")
                        }
                    }
                }
            }

            // Spacer
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
