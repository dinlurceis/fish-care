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
import com.htn.fishcare.control.ui.FanControlRoute
import com.htn.fishcare.feeding.ui.FeedingControlRoute
import com.htn.fishcare.loghistory.ui.LogHistoryRoute
import com.htn.fishcare.dashboard.DashboardRoute

private enum class FishCareTab {
    DASHBOARD,
    FEEDING,
    CONTROL,
    HISTORY
}

@Composable
fun FishCareApp() {
    var selectedTab by rememberSaveable { mutableStateOf(FishCareTab.DASHBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.DASHBOARD,
                    onClick = { selectedTab = FishCareTab.DASHBOARD },
                    icon = { Text("D") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.FEEDING,
                    onClick = { selectedTab = FishCareTab.FEEDING },
                    icon = { Text("F") },
                    label = { Text("Feeding") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.CONTROL,
                    onClick = { selectedTab = FishCareTab.CONTROL },
                    icon = { Text("C") },
                    label = { Text("Control") }
                )
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.HISTORY,
                    onClick = { selectedTab = FishCareTab.HISTORY },
                    icon = { Text("H") },
                    label = { Text("History") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            FishCareTab.DASHBOARD -> DashboardRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.FEEDING -> FeedingControlRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.CONTROL -> FanControlRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.HISTORY -> LogHistoryRoute(modifier = Modifier.padding(innerPadding))
        }
    }
}
