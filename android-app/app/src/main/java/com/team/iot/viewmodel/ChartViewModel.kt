package com.team.iot.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.iot.repository.FirebaseRepo
import com.team.iot.repository.GeminiAiRepo
import com.team.iot.repository.SensorData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class ChartViewModel @Inject constructor(
    private val firebaseRepo: FirebaseRepo,
    private val geminiAiRepo: GeminiAiRepo
) : ViewModel() {

    private val TAG = "ChartViewModel"

    // Chart data
    private val _chartDataState = MutableStateFlow<List<SensorData>>(emptyList())
    val chartDataState: StateFlow<List<SensorData>> = _chartDataState.asStateFlow()

    // Average values
    private val _avgTdsState = MutableStateFlow(0.0)
    val avgTdsState: StateFlow<Double> = _avgTdsState.asStateFlow()

    private val _avgTempState = MutableStateFlow(0.0)
    val avgTempState: StateFlow<Double> = _avgTempState.asStateFlow()

    private val _avgTurbidityState = MutableStateFlow(0.0)
    val avgTurbidityState: StateFlow<Double> = _avgTurbidityState.asStateFlow()

    // Loading state
    private val _isLoadingState = MutableStateFlow(false)
    val isLoadingState: StateFlow<Boolean> = _isLoadingState.asStateFlow()

    // AI result
    private val _aiResultState = MutableStateFlow("")
    val aiResultState: StateFlow<String> = _aiResultState.asStateFlow()

    fun loadChartData(days: Int) {
        viewModelScope.launch {
            try {
                val sensorData = firebaseRepo.getSensorHistory(days)
                _chartDataState.value = sensorData

                // Calculate average values
                if (sensorData.isNotEmpty()) {
                    _avgTdsState.value = sensorData.map { it.tds }.average()
                    _avgTempState.value = sensorData.map { it.temperature }.average()
                    _avgTurbidityState.value = sensorData.map { it.turbidity }.average()
                } else {
                    _avgTdsState.value = 0.0
                    _avgTempState.value = 0.0
                    _avgTurbidityState.value = 0.0
                }

                Log.d(TAG, "Chart data loaded: ${sensorData.size} items")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chart data: ${e.message}", e)
                _chartDataState.value = emptyList()
            }
        }
    }

    fun analyzeWithAI() {
        viewModelScope.launch {
            try {
                _isLoadingState.value = true
                _aiResultState.value = ""

                val currentData = _chartDataState.value
                if (currentData.isEmpty()) {
                    _aiResultState.value = "Không có dữ liệu để phân tích"
                    return@launch
                }

                val result = geminiAiRepo.analyzeWaterChart(currentData)
                _aiResultState.value = result

                Log.d(TAG, "AI analysis completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing with AI: ${e.message}", e)
                _aiResultState.value = "Lỗi khi phân tích: ${e.message}"
            } finally {
                _isLoadingState.value = false
            }
        }
    }

    fun seedFakeData() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val fakeDataList = mutableListOf<SensorData>()

                // Generate fake data for 30 days
                repeat(30) { dayOffset ->
                    val timestamp = now - (dayOffset * 86400000L)
                    val tds = Random.nextDouble(300.0, 450.0)
                    val temperature = Random.nextDouble(25.0, 32.0)
                    val turbidity = Random.nextDouble(2.0, 6.0)
                    val ph = Random.nextDouble(6.5, 7.5)

                    fakeDataList.add(
                        SensorData(
                            timestamp = timestamp,
                            tds = tds,
                            temperature = temperature,
                            turbidity = turbidity,
                            ph = ph
                        )
                    )
                }

                _chartDataState.value = fakeDataList.sortedBy { it.timestamp }

                // Update average values
                if (fakeDataList.isNotEmpty()) {
                    _avgTdsState.value = fakeDataList.map { it.tds }.average()
                    _avgTempState.value = fakeDataList.map { it.temperature }.average()
                    _avgTurbidityState.value = fakeDataList.map { it.turbidity }.average()
                }

                Log.d(TAG, "Fake data seeded: ${fakeDataList.size} items")
            } catch (e: Exception) {
                Log.e(TAG, "Error seeding fake data: ${e.message}", e)
            }
        }
    }
}
