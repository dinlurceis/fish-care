package com.htn.fishcare.dashboard

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel : ViewModel() {
    // MutableStateFlow giữ trạng thái nội bộ, có thể thay đổi
    private val _sensorState = MutableStateFlow(SensorData())
    
    // StateFlow chỉ đọc dành cho UI (Compose) lắng nghe
    val sensorState: StateFlow<SensorData> = _sensorState.asStateFlow()

    // Khởi tạo Firebase Database instance
    private val database = FirebaseDatabase.getInstance()
    
    // Lấy reference theo đúng Schema trong PROJECT_CONTEXT.md
    private val sensorRef = database.getReference("aquarium")

    init {
        listenToSensorData()
    }

    private fun listenToSensorData() {
        // Lắng nghe sự thay đổi dữ liệu từ Firebase Realtime Database
        sensorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Đọc đúng các key được quy định ("temperature", "water_quality", "ts300b")
                // Dùng Double::class.java rồi cast toFloat() để tránh lỗi parse number của Firebase SDK
                val temp = snapshot.child("temperature").getValue(Number::class.java)?.toFloat() ?: _sensorState.value.temperature
                val tds = snapshot.child("water_quality").getValue(Number::class.java)?.toFloat() ?: _sensorState.value.tds
                val turbidity = snapshot.child("ts300b").getValue(Number::class.java)?.toFloat() ?: _sensorState.value.turbidity
                val feedWeight = snapshot.child("weight").getValue(Number::class.java)?.toFloat() ?: _sensorState.value.feedWeight
                
                // Cập nhật giá trị mới vào StateFlow, UI tự động vẽ lại
                _sensorState.value = SensorData(
                    temperature = temp,
                    tds = tds,
                    turbidity = turbidity,
                    feedWeight = feedWeight,
                    lastUpdated = System.currentTimeMillis()
                )
            }

            override fun onCancelled(error: DatabaseError) {
                // Có thể bổ sung log lỗi ở đây
            }
        })
    }
}
