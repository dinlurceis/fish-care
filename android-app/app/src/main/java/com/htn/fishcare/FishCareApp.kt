package com.htn.fishcare

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.htn.fishcare.auth.ui.LoginRoute
import com.htn.fishcare.chart.ui.ChartRoute
import com.htn.fishcare.control.ui.FanControlRoute
import com.htn.fishcare.feeding.ui.FeedingControlRoute
import com.htn.fishcare.health.ui.FishHealthCheckRoute
import com.htn.fishcare.loghistory.ui.LogHistoryRoute
import com.htn.fishcare.dashboard.DashboardRoute
import com.htn.fishcare.notification.ui.NotificationRoute
import com.htn.fishcare.notification.ui.NotificationViewModel
import com.htn.fishcare.profile.ui.ProfileRoute

private enum class FishCareTab {
    DASHBOARD, FEEDING, CONTROL, CHART, PROFILE
}

@Composable
fun FishCareApp() {
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(FishCareTab.DASHBOARD) }
    var showFishHealthCheck by rememberSaveable { mutableStateOf(false) }
    var showFeedingHistory by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }

    // Notification ViewModel (singleton across the app)
    val notifViewModel: NotificationViewModel = hiltViewModel()
    val notifState by notifViewModel.uiState.collectAsState()

    // Runtime permission request for Android 13+
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied – handle silently */ }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ── Auth gate ────────────────────────────────────────────────────────────
    if (!isLoggedIn) {
        LoginRoute(onLoginSuccess = { isLoggedIn = true })
        return
    }

    // ── Modal screens (full-screen overlays) ────────────────────────────────
    if (showFishHealthCheck) {
        FishHealthCheckRoute(onBackClick = { showFishHealthCheck = false })
        return
    }
    if (showFeedingHistory) {
        LogHistoryRoute(modifier = Modifier, onBackClick = { showFeedingHistory = false })
        return
    }
    if (showNotifications) {
        NotificationRoute(onBackClick = { showNotifications = false })
        return
    }

    // ── Main scaffold with bottom nav ────────────────────────────────────────
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.DASHBOARD,
                    onClick  = { selectedTab = FishCareTab.DASHBOARD },
                    icon     = { Text("🏠") },
                    label    = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.FEEDING,
                    onClick  = { selectedTab = FishCareTab.FEEDING },
                    icon     = { Text("🍖") },
                    label    = { Text("Cho ăn") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.CONTROL,
                    onClick  = { selectedTab = FishCareTab.CONTROL },
                    icon     = { Text("🎛️") },
                    label    = { Text("Điều khiển") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.CHART,
                    onClick  = { selectedTab = FishCareTab.CHART },
                    icon     = { Text("📊") },
                    label    = { Text("Biểu đồ") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.PROFILE,
                    onClick  = { selectedTab = FishCareTab.PROFILE },
                    icon     = { Text("👤") },
                    label    = { Text("Tài khoản") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            FishCareTab.DASHBOARD -> DashboardRoute(
                modifier             = Modifier.padding(innerPadding),
                onNotificationClick  = { showNotifications = true },
                unreadCount          = notifState.unreadCount
            )
            FishCareTab.FEEDING  -> FeedingControlRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.CONTROL  -> FanControlRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.CHART    -> ChartRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.PROFILE  -> ProfileRoute(
                modifier                    = Modifier.padding(innerPadding),
                onNavigateToFeedingHistory  = { showFeedingHistory = true },
                onNavigateToFishHealthCheck = { showFishHealthCheck = true }
            )
        }
    }
}
