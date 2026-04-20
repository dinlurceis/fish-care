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

            // Query Firebase Realtime DB node "tds_logs" theo epoch timestamp
            val sensorLogsRef = database.getReference("tds_logs")
            
            sensorLogsRef.orderByKey()
                .startAt(startTimestamp.toString())
                .get()
                .addOnSuccessListener { snapshot ->
                    val sensorDataList = mutableListOf<SensorData>()
                    
                    snapshot.children.forEach { dataSnapshot ->
                        try {
                            val timestampStr = dataSnapshot.key ?: ""
                            val timestamp = timestampStr.toLongOrNull() ?: 0L
                            
                            val tdsValue = dataSnapshot.getValue(Double::class.java) ?: 0.0
                            
                            // Vì Firebase query key dạng text nên cẩn thận lọc thêm cho chắc
                            if (timestamp >= startTimestamp) {
                                // Gán temperature = tds để vẽ biểu đồ TDS trên LineChart tái sử dụng mã
                                sensorDataList.add(
                                    SensorData(
                                        timestamp = timestamp,
                                        tds = tdsValue,
                                        turbidity = 0.0,
                                        temperature = tdsValue, // Trick để vẽ biểu đồ TDS chung hàm ChartScreen
                                        ph = 0.0
                                    )
                                )
                            }
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
