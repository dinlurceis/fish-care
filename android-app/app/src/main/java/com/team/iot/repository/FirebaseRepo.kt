package com.team.iot.repository

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.inject.Inject

data class SensorData(
    val timestamp: Long = 0,
    val tds: Double = 0.0,
    val turbidity: Double = 0.0,
    val temperature: Double = 0.0,
    val ph: Double = 0.0
)

class FirebaseRepo @Inject constructor() {
    private val database = FirebaseDatabase.getInstance()
    private val TAG = "FirebaseRepo"

    suspend fun getSensorHistory(days: Int): List<SensorData> = suspendCancellableCoroutine { continuation ->
        try {
            // Tính timestamp từ (hiện tại - days)
            val currentTimeMillis = System.currentTimeMillis()
            val daysInMillis = days * 86400000L
            val startTimestamp = currentTimeMillis - daysInMillis

            // Query Firebase Realtime DB node "sensor_logs" orderByChild "timestamp" startAt [timestamp đó]
            val sensorLogsRef = database.getReference("sensor_logs")
            
            sensorLogsRef.orderByChild("timestamp")
                .startAt(startTimestamp.toDouble())
                .get()
                .addOnSuccessListener { snapshot ->
                    val sensorDataList = mutableListOf<SensorData>()
                    
                    snapshot.children.forEach { dataSnapshot ->
                        try {
                            val timestamp = dataSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            val tds = dataSnapshot.child("tds").getValue(Double::class.java) ?: 0.0
                            val turbidity = dataSnapshot.child("turbidity").getValue(Double::class.java) ?: 0.0
                            val temperature = dataSnapshot.child("temperature").getValue(Double::class.java) ?: 0.0
                            val ph = dataSnapshot.child("ph").getValue(Double::class.java) ?: 0.0
                            
                            sensorDataList.add(
                                SensorData(
                                    timestamp = timestamp,
                                    tds = tds,
                                    turbidity = turbidity,
                                    temperature = temperature,
                                    ph = ph
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing sensor data: ${e.message}", e)
                        }
                    }
                    
                    // Sắp xếp theo timestamp tăng dần
                    val sortedList = sensorDataList.sortedBy { it.timestamp }
                    continuation.resume(sortedList)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error fetching sensor history: ${exception.message}", exception)
                    // Trả về list rỗng nếu lỗi (không throw exception)
                    continuation.resume(emptyList())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in getSensorHistory: ${e.message}", e)
            continuation.resume(emptyList())
        }
    }
}
