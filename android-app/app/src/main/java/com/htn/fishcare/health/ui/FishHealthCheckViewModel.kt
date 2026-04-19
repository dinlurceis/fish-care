package com.htn.fishcare.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.team.iot.repository.GroqAiRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

val FISH_TYPES = listOf(
    "Cá chép", "Cá vàng", "Cá koi", "Cá rô phi", "Cá basa",
    "Cá trê", "Cá lóc", "Cá tai tượng", "Cá thát lát", "Cá trắm",
    "Cá mè", "Cá diêu hồng", "Cá chim trắng", "Cá thần tiên", "Khác"
)

val ALL_SYMPTOMS = listOf(
    "Thiếu ăn", "Lên mặt nước bắt hơi", "Nằm dưới đáy bể",
    "Lơ lửng ở giữa bể", "Vây bị rách", "Người bị xù/phồng",
    "Lươn lẹo bất thường", "Lớp phủ trắng trên người",
    "Chảy máu", "Mắt bị trắng", "Bơi nghiêng", "Thân bị đốm",
    "Cá bị rụng vảy", "Bụng phình to", "Hô hấp nhanh bất thường",
    "Màu sắc nhợt nhạt", "Cọ thân vào thành bể", "Không phản ứng với thức ăn"
)

data class FishHealthUiState(
    val selectedFishType: String = "",
    val fishTypeExpanded: Boolean = false,
    val symptomSearch: String = "",
    val selectedSymptoms: List<String> = emptyList(),
    val behaviorDescription: String = "",
    val currentTemp: Float = 0f,
    val currentTds: Float = 0f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val aiDiagnosis: String? = null,
    val isAnalyzing: Boolean = false
) {
    val filteredSymptoms: List<String> get() =
        if (symptomSearch.isBlank()) emptyList()
        else ALL_SYMPTOMS.filter {
            it.contains(symptomSearch, ignoreCase = true) && it !in selectedSymptoms
        }

    val aquariumSummary: String get() =
        if (currentTemp > 0f || currentTds > 0f)
            "Nhiệt độ: ${String.format("%.1f", currentTemp)}°C | TDS: ${currentTds.toInt()} ppm"
        else "Chưa có dữ liệu cảm biến"
}

@HiltViewModel
class FishHealthCheckViewModel @Inject constructor(
    private val groqAiRepo: GroqAiRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(FishHealthUiState())
    val uiState: StateFlow<FishHealthUiState> = _uiState.asStateFlow()

    init {
        listenToSensorData()
    }

    private fun listenToSensorData() {
        FirebaseDatabase.getInstance().getReference("aquarium")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val temp = snapshot.child("temperature").getValue(Double::class.java)?.toFloat() ?: 0f
                    val tds = snapshot.child("water_quality").getValue(Double::class.java)?.toFloat() ?: 0f
                    _uiState.value = _uiState.value.copy(currentTemp = temp, currentTds = tds)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun selectFishType(type: String) {
        _uiState.value = _uiState.value.copy(selectedFishType = type, fishTypeExpanded = false)
    }

    fun toggleFishTypeDropdown() {
        _uiState.value = _uiState.value.copy(fishTypeExpanded = !_uiState.value.fishTypeExpanded)
    }

    fun onSymptomSearchChange(q: String) {
        _uiState.value = _uiState.value.copy(symptomSearch = q)
    }

    fun addSymptom(symptom: String) {
        val updated = _uiState.value.selectedSymptoms + symptom
        _uiState.value = _uiState.value.copy(
            selectedSymptoms = updated,
            symptomSearch = ""
        )
    }

    fun removeSymptom(symptom: String) {
        _uiState.value = _uiState.value.copy(
            selectedSymptoms = _uiState.value.selectedSymptoms - symptom
        )
    }

    fun updateBehaviorDescription(desc: String) {
        _uiState.value = _uiState.value.copy(behaviorDescription = desc)
    }

    fun analyzeFishHealth() {
        val state = _uiState.value
        if (state.selectedFishType.isBlank()) {
            _uiState.value = state.copy(error = "Vui lòng chọn loại cá")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)
            try {
                val symptoms = state.selectedSymptoms.joinToString(", ")
                val prompt = """
                    Vui lòng phân tích sức khỏe của cá dựa trên thông tin sau:
                    
                    Loại cá: ${state.selectedFishType}
                    Điều kiện bể nước: ${state.aquariumSummary}
                    Các triệu chứng: ${if (symptoms.isNotEmpty()) symptoms else "Không có triệu chứng đặc biệt"}
                    Mô tả hành vi: ${if (state.behaviorDescription.isNotEmpty()) state.behaviorDescription else "Hành vi bình thường"}
                    
                    Dựa trên những thông tin này, hãy:
                    1. Đưa ra chẩn đoán sơ bộ về tình trạng sức khỏe của cá
                    2. Nêu các nguyên nhân có thể gây ra những triệu chứng này
                    3. Đề xuất các biện pháp xử lý và phòng ngừa
                    4. Khi nào cần gọi thú y
                    
                    Vui lòng trả lời bằng tiếng Việt, ngắn gọn, dùng bullet points.
                """.trimIndent()
                val diagnosis = groqAiRepo.callGroqApiForFishHealth(prompt)
                _uiState.value = _uiState.value.copy(isAnalyzing = false, aiDiagnosis = diagnosis)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = "Lỗi phân tích: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetForm() {
        _uiState.value = FishHealthUiState(
            currentTemp = _uiState.value.currentTemp,
            currentTds = _uiState.value.currentTds
        )
    }
}
