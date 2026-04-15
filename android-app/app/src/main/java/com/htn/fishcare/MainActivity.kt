package com.htn.fishcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.htn.fishcare.loghistory.ui.LogHistoryRoute
import com.htn.fishcare.ui.theme.FishcareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FishcareTheme {
                LogHistoryRoute()
            }
        }
    }
}