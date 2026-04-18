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
import com.htn.fishcare.feeding.ui.FeedingControlRoute
import com.htn.fishcare.loghistory.ui.LogHistoryRoute

private enum class FishCareTab {
    FEEDING,
    HISTORY
}

@Composable
fun FishCareApp() {
    var selectedTab by rememberSaveable { mutableStateOf(FishCareTab.FEEDING) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == FishCareTab.FEEDING,
                    onClick = { selectedTab = FishCareTab.FEEDING },
                    icon = { Text("F") },
                    label = { Text("Feeding") }
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
            FishCareTab.FEEDING -> FeedingControlRoute(modifier = Modifier.padding(innerPadding))
            FishCareTab.HISTORY -> LogHistoryRoute(modifier = Modifier.padding(innerPadding))
        }
    }
}
