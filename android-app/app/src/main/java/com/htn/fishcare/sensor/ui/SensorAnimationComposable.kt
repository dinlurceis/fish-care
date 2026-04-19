package com.htn.fishcare.sensor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.htn.fishcare.R

// ─────────────────────────────────────────────────────────────────────────────
// SensorAnimationCard – single card with Lottie + label + value
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SensorAnimationCard(
    label: String,
    value: String,
    unit: String,
    animationRawRes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lottie Animation
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationRawRes))
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(100.dp)
            )

            // Thông tin cảm biến
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = value,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SensorAnimationGrid – 4-sensor grid using LottieAnimationViewModel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SensorAnimationGrid(
    temperature: Float,
    tds: Float,
    turbidity: Float,
    feedWeight: Float = 0f,
    viewModel: LottieAnimationViewModel,
    modifier: Modifier = Modifier
) {
    // Cập nhật animations khi giá trị thay đổi
    LaunchedEffect(temperature, tds, turbidity) {
        viewModel.updateAllAnimations(
            temperature = temperature,
            tds = tds.toInt(),
            turbidity = turbidity
        )
    }

    val animationState by viewModel.animationState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hàng 1: Nhiệt độ + TDS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SensorAnimationCard(
                label = "Nhiệt độ",
                value = String.format("%.1f", temperature),
                unit = "°C",
                animationRawRes = animationState.temperatureAnimation,
                modifier = Modifier.weight(1f)
            )
            SensorAnimationCard(
                label = "TDS",
                value = tds.toInt().toString(),
                unit = "ppm",
                animationRawRes = animationState.tdsAnimation,
                modifier = Modifier.weight(1f)
            )
        }

        // Hàng 2: Độ đục + Cân nặng cám
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SensorAnimationCard(
                label = "Độ đục",
                value = String.format("%.0f", turbidity),
                unit = "NTU",
                animationRawRes = animationState.turbidityAnimation,
                modifier = Modifier.weight(1f)
            )
            SensorAnimationCard(
                label = "Cám còn lại",
                value = String.format("%.0f", feedWeight),
                unit = "g",
                animationRawRes = R.raw.cannang,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
