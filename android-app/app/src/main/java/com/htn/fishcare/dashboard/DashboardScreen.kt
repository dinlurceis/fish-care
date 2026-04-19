package com.htn.fishcare.dashboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.htn.fishcare.sensor.ui.LottieAnimationViewModel
import com.htn.fishcare.sensor.ui.SensorAnimationGrid
import kotlinx.coroutines.delay

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
    animViewModel: LottieAnimationViewModel = hiltViewModel(),
    onNotificationClick: () -> Unit = {},
    unreadCount: Int = 0
) {
    val sensorState by viewModel.sensorState.collectAsState()
    DashboardScreen(
        sensorState         = sensorState,
        animViewModel       = animViewModel,
        onNotificationClick = onNotificationClick,
        unreadCount         = unreadCount,
        modifier            = modifier
    )
}

@Composable
fun DashboardScreen(
    sensorState: SensorData,
    animViewModel: LottieAnimationViewModel,
    onNotificationClick: () -> Unit = {},
    unreadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pagerState = rememberPagerState(pageCount = { 4 })

    // Auto-scroll
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % 4
            pagerState.animateScrollToPage(nextPage)
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF4FBF7), Color(0xFFE8F1FF))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Trang chủ",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2C3E50)
                )
                Text("Hệ thống giám sát hồ cá", fontSize = 14.sp, color = Color.Gray)
            }
            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier.size(48.dp).background(Color.White, CircleShape)
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Thông báo",
                        tint = Color(0xFF0E5A2A),
                        modifier = Modifier.size(26.dp)
                    )
                    // Badge đỏ số chưa đọc
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Red, CircleShape)
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 9) "9+" else "$unreadCount",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Pager – 4 cảm biến ──────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(190.dp)
        ) { page ->
            when (page) {
                0 -> DashboardPagerItem(
                    title = "Nhiệt độ",
                    value = String.format("%.1f", sensorState.temperature),
                    unit = "°C",
                    bgColor = Color(0xFFE8F5E9),
                    accentColor = Color(0xFF0E5A2A),
                    emoji = "🌡️"
                )
                1 -> DashboardPagerItem(
                    title = "Chất lượng nước (TDS)",
                    value = String.format("%.0f", sensorState.tds),
                    unit = "ppm",
                    bgColor = Color(0xFFE3F2FD),
                    accentColor = Color(0xFF1565C0),
                    emoji = "💧"
                )
                2 -> DashboardPagerItem(
                    title = "Độ đục",
                    value = String.format("%.0f", sensorState.turbidity),
                    unit = "NTU",
                    bgColor = Color(0xFFFFF3E0),
                    accentColor = Color(0xFFE65100),
                    emoji = "🌫️"
                )
                3 -> DashboardPagerItem(
                    title = "Cân nặng cám còn lại",
                    value = String.format("%.0f", sensorState.feedWeight),
                    unit = "g",
                    bgColor = Color(0xFFF3E5F5),
                    accentColor = Color(0xFF6A1B9A),
                    emoji = "⚖️"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pager dots
        Row(
            modifier = Modifier.wrapContentHeight().fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                val width by animateDpAsState(if (isSelected) 24.dp else 8.dp, label = "dot_w")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF0E5A2A) else Color.LightGray)
                        .height(8.dp)
                        .width(width)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Trạng thái trực quan (Lottie) ───────────────────────────
        Text(
            text = "Trạng thái trực quan",
            fontSize = 18.sp,
            color = Color(0xFF2C3E50),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))

        SensorAnimationGrid(
            temperature = sensorState.temperature,
            tds = sensorState.tds,
            turbidity = sensorState.turbidity,
            feedWeight = sensorState.feedWeight,
            viewModel = animViewModel
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DashboardPagerItem(
    title: String,
    value: String,
    unit: String,
    bgColor: Color,
    accentColor: Color,
    emoji: String
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 46.sp)
            }

            Column(
                modifier = Modifier.weight(1f).padding(start = 20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = accentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        fontSize = 32.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = unit,
                        fontSize = 14.sp,
                        color = accentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}
