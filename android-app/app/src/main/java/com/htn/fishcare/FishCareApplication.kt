package com.htn.fishcare

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FishCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase with context
        Firebase.initialize(this)
    }
}
