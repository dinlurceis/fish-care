package com.htn.fishcare

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.htn.fishcare.notification.monitor.SensorThresholdMonitor
import com.htn.fishcare.notification.worker.NotificationCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FishCareApplication : Application() {

    @Inject
    lateinit var sensorThresholdMonitor: SensorThresholdMonitor

    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)

        // Monitor ngưỡng cảm biến khi app đang mở (realtime)
        sensorThresholdMonitor.startListening()

        // WorkManager: kiểm tra /notifications mỗi 15 phút kể cả khi app tắt (FREE)
        NotificationCheckWorker.schedule(this)
    }
}
