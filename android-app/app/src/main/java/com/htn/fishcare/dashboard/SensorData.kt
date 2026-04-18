package com.htn.fishcare.dashboard

data class SensorData(
    val temperature: Float = 0.0f,
    val tds: Float = 0.0f,
    val turbidity: Float = 0.0f,
    val feedWeight: Float = 0.0f,
    val lastUpdated: Long = System.currentTimeMillis()
)
