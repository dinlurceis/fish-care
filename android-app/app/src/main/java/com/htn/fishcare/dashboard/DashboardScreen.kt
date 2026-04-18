package com.htn.fishcare.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    // Thu thập dữ liệu từ flow
    val sensorState by viewModel.sensorState.collectAsState()
    DashboardScreen(sensorState = sensorState, modifier = modifier)
}

@Composable
fun DashboardScreen(
    sensorState: SensorData,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Hồ cá thông minh",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        SensorCard(
            title = "Nhiệt độ",
            value = String.format("%.1f", sensorState.temperature),
            unit = "°C",
            icon = rememberVectorPainter(Icons.Default.Info),
            isWarning = sensorState.temperature > 32.0f || sensorState.temperature < 20.0f
        )

        SensorCard(
            title = "Chất lượng nước (TDS)",
            value = String.format("%.1f", sensorState.tds),
            unit = "ppm",
            icon = rememberVectorPainter(Icons.Default.Info),
            isWarning = sensorState.tds > 1000.0f
        )

        SensorCard(
            title = "Độ đục",
            value = String.format("%.0f", sensorState.turbidity),
            unit = "Mức thô",
            icon = rememberVectorPainter(Icons.Default.Info),
            // Theo tài liệu ESP32 Project Context: Tín hiệu thô càng nhỏ thì nước càng đục
            isWarning = sensorState.turbidity < 1000.0f && sensorState.turbidity > 0f
        )
    }
}
