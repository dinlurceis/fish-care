package com.htn.fishcare.chart.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.team.iot.repository.SensorData
import com.team.iot.viewmodel.ChartViewModel

// ─────────────────────────────────────────────────────────────────────────────
// ChartRoute – entry point được gọi từ FishCareApp.kt
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChartRoute(
    modifier: Modifier = Modifier,
    viewModel: ChartViewModel = hiltViewModel()
) {
    val chartData  by viewModel.chartDataState.collectAsState()
    val avgTds     by viewModel.avgTdsState.collectAsState()
    val avgTemp    by viewModel.avgTempState.collectAsState()
    val avgTurb    by viewModel.avgTurbidityState.collectAsState()
    val isLoading  by viewModel.isLoadingState.collectAsState()
    val aiResult   by viewModel.aiResultState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadChartData(7) }

    ChartScreen(
        modifier       = modifier,
        chartData      = chartData,
        avgTds         = avgTds,
        avgTemp        = avgTemp,
        avgTurbidity   = avgTurb,
        isLoading      = isLoading,
        aiResult       = aiResult,
        onDaysChange   = { viewModel.loadChartData(it) },
        onAnalyzeClick = { viewModel.analyzeWithAI() },
        onSeedFakeData = { viewModel.seedFakeData() }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ChartScreen – UI chính
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChartScreen(
    modifier: Modifier = Modifier,
    chartData: List<SensorData>,
    avgTds: Double,
    avgTemp: Double,
    avgTurbidity: Double,
    isLoading: Boolean,
    aiResult: String,
    onDaysChange: (Int) -> Unit,
    onAnalyzeClick: () -> Unit,
    onSeedFakeData: () -> Unit = {}
) {
    var selectedDays by remember { mutableIntStateOf(7) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E5A2A))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "📊 Biểu đồ lịch sử",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // Day-range filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(7 to "7 ngày", 14 to "14 ngày", 30 to "30 ngày").forEach { (days, label) ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = {
                                selectedDays = days
                                onDaysChange(days)
                            },
                            label = { Text(label, color = if (selectedDays == days) Color(0xFF1565C0) else Color.White) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        if (isLoading && chartData.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Color(0xFF0E5A2A)) }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF4FBF7), Color(0xFFE8F1FF))
                    )
                )
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ══════════════════════════════════════════════════════════════════
            // SECTION 2: Thống kê trung bình
            // ══════════════════════════════════════════════════════════════════
            Text(
                text = "📈 Số liệu trung bình ($selectedDays ngày qua)",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0E5A2A)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("TDS",     String.format("%.1f", avgTds),      "ppm",  Modifier.weight(1f))
                StatCard("Nhiệt độ", String.format("%.1f", avgTemp),    "°C",   Modifier.weight(1f))
                StatCard("Độ đục",  String.format("%.1f", avgTurbidity),"NTU",  Modifier.weight(1f))
            }

            // ══════════════════════════════════════════════════════════════════
            // SECTION 3: Timeline log
            // ══════════════════════════════════════════════════════════════════
            if (chartData.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📂 ${chartData.size} bản ghi",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF0E5A2A)
                        )
                        Text(
                            text = "Từ ${chartData.firstOrNull()?.timestamp?.let { formatDate(it) } ?: "N/A"}" +
                                    " → ${chartData.lastOrNull()?.timestamp?.let { formatDate(it) } ?: "N/A"}",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Dữ liệu mới nhất: TDS=${
                                String.format("%.0f", chartData.lastOrNull()?.tds ?: 0.0)
                            } ppm | Nhiệt=${
                                String.format("%.1f", chartData.lastOrNull()?.temperature ?: 0.0)
                            }°C",
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠️ Chưa có dữ liệu lịch sử", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Button(
                            onClick = onSeedFakeData,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E5A2A)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tạo dữ liệu mẫu (Demo)", color = Color.White)
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // SECTION 4: AI Phân tích chart (Groq)
            // ══════════════════════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 ", fontSize = 22.sp)
                        Text(
                            text = "AI Phân tích chất lượng nước",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0E5A2A)
                        )
                    }

                    if (aiResult.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text(
                                text = aiResult,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFF263238)
                            )
                        }
                    } else {
                        Text(
                            text = "AI sẽ phân tích xu hướng TDS, nhiệt độ, độ đục và đưa ra khuyến nghị chăm sóc ao cá.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }

                    Button(
                        onClick = onAnalyzeClick,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E5A2A)),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp,
                                modifier = Modifier.padding(end = 8.dp).height(18.dp))
                        }
                        Text(
                            text = if (isLoading) "Đang phân tích..." else "🤖 Phân tích bằng Groq AI",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatCard – hiển thị 1 thống kê trung bình
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0E5A2A)
            )
            Text(
                text = unit,
                fontSize = 11.sp,
                color = Color(0xFF0E5A2A).copy(alpha = 0.7f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Util
// ─────────────────────────────────────────────────────────────────────────────
fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale("vi", "VN"))
    return sdf.format(java.util.Date(timestamp))
}
