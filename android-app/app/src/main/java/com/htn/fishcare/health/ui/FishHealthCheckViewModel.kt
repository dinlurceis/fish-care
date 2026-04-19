package com.htn.fishcare.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.iot.repository.GroqAiRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FishSymptom(
    val name: String,
    val isSelected: Boolean = false
)

data class FishHealthUiState(
    val fishName: String = "",
    val fishType: String = "",
    val aquariumCondition: String = "", // pH, temperature, etc.
    val behaviorDescription: String = "",
    val symptoms: List<FishSymptom> = listOf(
        FishSymptom("Thiếu ăn"),
        FishSymptom("Lên mặt nước bắt hơi"),
        FishSymptom("Nằm dưới đáy bể"),
        FishSymptom("Lơ lửng ở giữa bể"),
        FishSymptom("Vây bị rách"),
        FishSymptom("Người bị xù/phồng"),
        FishSymptom("Lươn lẹo bất thường"),
        FishSymptom("Lớp phủ trắng trên người"),
        FishSymptom("Chảy máu"),
        FishSymptom("Mắt bị trắng")
    ),
    val isLoading: Boolean = false,
    val error: String? = null,
    val aiDiagnosis: String? = null,
    val isAnalyzing: Boolean = false
)

@HiltViewModel
class FishHealthCheckViewModel @Inject constructor(
    private val groqAiRepo: GroqAiRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(FishHealthUiState())
    val uiState: StateFlow<FishHealthUiState> = _uiState.asStateFlow()

    fun updateFishName(name: String) {
        _uiState.value = _uiState.value.copy(fishName = name)
    }

    fun updateFishType(type: String) {
        _uiState.value = _uiState.value.copy(fishType = type)
    }

    fun updateAquariumCondition(condition: String) {
        _uiState.value = _uiState.value.copy(aquariumCondition = condition)
    }

    fun updateBehaviorDescription(description: String) {
        _uiState.value = _uiState.value.copy(behaviorDescription = description)
    }

    fun toggleSymptom(symptomName: String) {
        val updatedSymptoms = _uiState.value.symptoms.map { symptom ->
            if (symptom.name == symptomName) {
                symptom.copy(isSelected = !symptom.isSelected)
            } else {
                symptom
            }
        }
        _uiState.value = _uiState.value.copy(symptoms = updatedSymptoms)
    }

    fun analyzeFishHealth() {
        val currentState = _uiState.value

        if (currentState.fishName.isBlank() || currentState.fishType.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Vui lòng nhập tên cá và loại cá"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)

            try {
                // Build the analysis prompt for AI
                val selectedSymptoms = currentState.symptoms
                    .filter { it.isSelected }
                    .map { it.name }
                    .joinToString(", ")

                val prompt = buildFishHealthPrompt(
                    fishName = currentState.fishName,
                    fishType = currentState.fishType,
                    aquariumCondition = currentState.aquariumCondition,
                    symptoms = selectedSymptoms,
                    behavior = currentState.behaviorDescription
                )

                // Call Groq AI with the analysis prompt
                val diagnosis = groqAiRepo.callGroqApiForFishHealth(prompt)

                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    aiDiagnosis = diagnosis
                )
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
        _uiState.value = FishHealthUiState()
    }

    private fun buildFishHealthPrompt(
        fishName: String,
        fishType: String,
        aquariumCondition: String,
        symptoms: String,
        behavior: String
    ): String {
        return """
            Vui lòng phân tích sức khỏe của cá dựa trên thông tin sau:
            
            Tên cá: $fishName
            Loại cá: $fishType
            Điều kiện bể nước: $aquariumCondition
            Các triệu chứng: ${if (symptoms.isNotEmpty()) symptoms else "Không có triệu chứng đặc biệt"}
            Mô tả hành vi: ${if (behavior.isNotEmpty()) behavior else "Hành vi bình thường"}
            
            Dựa trên những thông tin này, hãy:
            1. Đưa ra chẩn đoán sơ bộ về tình trạng sức khỏe của cá
            2. Nêu các nguyên nhân có thể gây ra những triệu chứng này
            3. Đề xuất các biện pháp xử lý và phòng ngừa
            4. Khi nào cần gọi thú y
            
            Vui lòng trả lời bằng tiếng Việt.
        """.trimIndent()
    }
}
