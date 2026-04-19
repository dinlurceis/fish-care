package com.htn.fishcare.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
    onNavigateToFeedingHistory: () -> Unit,
    onNavigateToFishHealthCheck: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearError()
        }
    }

    ProfileScreen(
        modifier = modifier,
        uiState = uiState,
        onAquariumNameChange = viewModel::updateAquariumName,
        onSaveChanges = viewModel::saveChanges,
        onNavigateToFeedingHistory = onNavigateToFeedingHistory,
        onNavigateToFishHealthCheck = onNavigateToFishHealthCheck
    )
}

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    uiState: ProfileUiState,
    onAquariumNameChange: (String) -> Unit,
    onSaveChanges: () -> Unit,
    onNavigateToFeedingHistory: () -> Unit,
    onNavigateToFishHealthCheck: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "User Profile",
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .padding(8.dp),
                    tint = Color.White
                )
                Column {
                    Text(
                        text = uiState.userName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.userEmail,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // Profile section
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
                        text = "Thông tin bể cá",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = uiState.aquariumName,
                        onValueChange = onAquariumNameChange,
                        label = { Text("Tên bể cá") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading
                    )
                    Button(
                        onClick = onSaveChanges,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading
                    ) {
                        Text("Lưu thay đổi")
                    }

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Feeding History Section
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
                        text = "Lịch sử cho ăn",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Xem chi tiết lịch sử cho ăn của bể cá",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Button(
                        onClick = onNavigateToFeedingHistory,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Xem lịch sử")
                    }
                }
            }

            // Fish Health Check Section
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
                        text = "Khám bệnh cho cá",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Nhập các thông tin về cá của bạn để AI phân tích sức khỏe",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Button(
                        onClick = onNavigateToFishHealthCheck,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Khám bệnh")
                    }
                }
            }

            // Logout button
            OutlinedButton(
                onClick = { /* TODO: Handle logout */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Đăng xuất", color = Color.Red)
            }
        }
    }
}
