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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val sensorState by viewModel.sensorState.collectAsState()
    DashboardScreen(sensorState = sensorState, modifier = modifier)
}

@Composable
fun DashboardScreen(
    sensorState: SensorData,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pagerState = rememberPagerState(pageCount = { 4 })
    
    // Auto-scroll logic
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % 4
            pagerState.animateScrollToPage(nextPage)
        }
    }

    // Aesthetic Gradient Background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFDFBF7),
            Color(0xFFF3F8F9)
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Top Bar Area
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Trang chủ", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C3E50))
                Text("Hệ thống giám sát hồ cá", fontSize = 14.sp, color = Color.Gray)
            }
            IconButton(
                onClick = { },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color(0xFF0F3B73), // Deep blue from original picture
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Auto-sliding Carousel
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            contentPadding = PaddingValues(horizontal = 0.dp) // Removed side peek to look cleaner
        ) { page ->
            when (page) {
                0 -> DashboardPagerItem("Nhiệt độ", "${String.format("%.1f", sensorState.temperature)} °C", "☀️")
                1 -> DashboardPagerItem("Chất lượng nước", "${String.format("%.1f", sensorState.tds)} ppm", "💧")
                2 -> DashboardPagerItem("Độ đục", "${String.format("%.0f", sensorState.turbidity)}", "🌫️")
                3 -> DashboardPagerItem("Cân nặng cám cá", "${String.format("%.1f", sensorState.feedWeight)} g", "🪣")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pager indicators
        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                val width by animateDpAsState(if (isSelected) 24.dp else 8.dp, label = "indicator_width")
                val color = if (isSelected) Color(0xFF29D3D3) else Color.LightGray
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .height(8.dp)
                        .width(width)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Feeding Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8FAFA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tự động cho ăn",
                        fontSize = 18.sp,
                        color = Color(0xFF2C3E50),
                        fontWeight = FontWeight.Bold
                    )
                    var isAuto by remember { mutableStateOf(false) }
                    Switch(
                        checked = isAuto, 
                        onCheckedChange = { isAuto = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF29D3D3),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCFD8DC),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                var gramInput by remember { mutableStateOf("") }
                
                TextField(
                    value = gramInput,
                    onValueChange = { gramInput = it },
                    placeholder = { 
                        Text("Nhập số gram (g)", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp)),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29D3D3)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text("Cho Ăn Ngay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { },
                modifier = Modifier
                    .weight(1f)
                    .height(65.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29D3D3)), // Uniform Cyan
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Bật Guồng", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Button(
                onClick = { },
                modifier = Modifier
                    .weight(1f)
                    .height(65.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29D3D3)), // Uniform Cyan
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Chẩn đoán", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DashboardPagerItem(title: String, value: String, emoji: String) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFFF5F9FA), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 56.sp)
            }
            
            Column(
                modifier = Modifier.weight(1f).padding(start = 24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.sp, 
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value, 
                    fontSize = 28.sp, 
                    color = Color(0xFF2C3E50),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
