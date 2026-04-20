package com.htn.fishcare.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.htn.fishcare.R

private val GREEN_DARK = Color(0xFF0E5A2A)
private val GREEN_MID  = Color(0xFF1B7A3E)
private val GREEN_CARD = Color(0xFFF4FBF7)

@Composable
fun LoginRoute(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoginSuccess) {
        if (uiState.isLoginSuccess) onLoginSuccess()
    }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearError()
        }
    }

    LoginScreen(
        modifier = modifier,
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = viewModel::submit,
        onToggleMode = viewModel::toggleMode
    )
}

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit
) {
    val fishComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.ca))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GREEN_DARK, GREEN_MID, Color(0xFF2E7D52))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Lottie Fish Animation ────────────────────────────────────
            LottieAnimation(
                composition = fishComposition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(160.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── App Title ────────────────────────────────────────────────
            Text(
                text = "FishCare",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                text = "Hệ thống nuôi cá thông minh",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // ── Form Card ────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = GREEN_CARD),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (uiState.isRegisterMode) "Tạo tài khoản" else "Đăng nhập",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GREEN_DARK,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    // Email
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = onEmailChange,
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GREEN_DARK,
                            unfocusedBorderColor = GREEN_DARK.copy(alpha = 0.4f),
                            cursorColor = GREEN_DARK,
                            focusedLabelColor = GREEN_DARK
                        )
                    )

                    // Password
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        label = { Text("Mật khẩu") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GREEN_DARK,
                            unfocusedBorderColor = GREEN_DARK.copy(alpha = 0.4f),
                            cursorColor = GREEN_DARK,
                            focusedLabelColor = GREEN_DARK
                        )
                    )

                    // Confirm password (register only)
                    AnimatedVisibility(
                        visible = uiState.isRegisterMode,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = uiState.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            label = { Text("Xác nhận mật khẩu") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GREEN_DARK,
                                unfocusedBorderColor = GREEN_DARK.copy(alpha = 0.4f),
                                cursorColor = GREEN_DARK,
                                focusedLabelColor = GREEN_DARK
                            )
                        )
                    }

                    // Error
                    AnimatedVisibility(visible = uiState.error != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE3E3))
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                color = Color(0xFF7A1111),
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Submit button
                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GREEN_DARK, modifier = Modifier.size(36.dp))
                        }
                    } else {
                        Button(
                            onClick = onSubmit,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GREEN_DARK)
                        ) {
                            Text(
                                text = if (uiState.isRegisterMode) "🐟  Tạo tài khoản" else "🐟  Đăng nhập",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Toggle mode link
                    Text(
                        text = if (uiState.isRegisterMode)
                            "Đã có tài khoản? Đăng nhập ngay"
                        else
                            "Chưa có tài khoản? Đăng ký ngay",
                        color = GREEN_DARK,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleMode() }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
