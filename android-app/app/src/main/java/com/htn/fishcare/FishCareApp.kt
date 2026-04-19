package com.htn.fishcare

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.htn.fishcare.auth.ui.LoginRoute
import com.htn.fishcare.chart.ui.ChartRoute
import com.htn.fishcare.control.ui.FanControlRoute
import com.htn.fishcare.feeding.ui.FeedingControlRoute
import com.htn.fishcare.health.ui.FishHealthCheckRoute
import com.htn.fishcare.loghistory.ui.LogHistoryRoute
import com.htn.fishcare.dashboard.DashboardRoute
import com.htn.fishcare.profile.ui.ProfileRoute

private enum class FishCareTab {
    DASHBOARD,
    FEEDING,
    CONTROL,
    CHART,
    PROFILE
}

@Composable
fun FishCareApp() {
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(FishCareTab.DASHBOARD) }
    var showFishHealthCheck by rememberSaveable { mutableStateOf(false) }
    var showFeedingHistory by rememberSaveable { mutableStateOf(false) }

    if (!isLoggedIn) {
        LoginRoute(
            onLoginSuccess = { isLoggedIn = true }
        )
        return
    }

    // Handle nested navigation for modal screens
    if (showFishHealthCheck) {
        FishHealthCheckRoute(
            onBackClick = { showFishHealthCheck = false }
        )
        return
    }

    if (showFeedingHistory) {
        LogHistoryRoute(
            modifier = Modifier,
            onBackClick = { showFeedingHistory = false }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.DASHBOARD,
                    onClick = { selectedTab = FishCareTab.DASHBOARD },
                    icon = { Text("🏠") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.FEEDING,
                    onClick = { selectedTab = FishCareTab.FEEDING },
                    icon = { Text("🍖") },
                    label = { Text("Cho ăn") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.CONTROL,
                    onClick = { selectedTab = FishCareTab.CONTROL },
                    icon = { Text("🎛️") },
                    label = { Text("Điều khiển") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.CHART,
                    onClick = { selectedTab = FishCareTab.CHART },
                    icon = { Text("📊") },
                    label = { Text("Biểu đồ") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.PROFILE,
                    onClick = { selectedTab = FishCareTab.PROFILE },
                    icon = { Text("👤") },
                    label = { Text("Tài khoản") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            FishCareTab.DASHBOARD -> DashboardRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.FEEDING -> FeedingControlRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.CONTROL -> FanControlRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.CHART -> ChartRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.PROFILE -> ProfileRoute(
                modifier = Modifier.padding(innerPadding),
                onNavigateToFeedingHistory = { showFeedingHistory = true },
                onNavigateToFishHealthCheck = { showFishHealthCheck = true }
            )
        }
    }
}
