package com.htn.fishcare.chatbot.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.htn.fishcare.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class ChatbotMessage(
    val role: String,  // "user" hoặc "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatbotUiState(
    val messages: List<ChatbotMessage> = emptyList(),
    val userInput: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isWaitingForResponse: Boolean = false
)

@HiltViewModel
class RagChatbotViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatbotUiState>(ChatbotUiState())
    val uiState: StateFlow<ChatbotUiState> = _uiState

    private val gson = Gson()
    private val okHttpClient = OkHttpClient()
    private var diseaseDatabaseJson: String? = null

    init {
        loadDiseaseDatabase()
    }

    /**
     * Đọc file benh_ca.json từ res/raw
     */
    private fun loadDiseaseDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resourceId = context.resources.getIdentifier("benh_ca", "raw", context.packageName)
                if (resourceId != 0) {
                    diseaseDatabaseJson = context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Không thể tải dữ liệu bệnh cá: ${e.message}")
            }
        }
    }

    fun updateUserInput(input: String) {
        _uiState.value = _uiState.value.copy(userInput = input)
    }

    /**
     * Gửi tin nhắn và gọi Groq API với RAG
     */
    fun sendMessage(userSymptoms: String) {
        if (userSymptoms.trim().isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Vui lòng nhập triệu chứng")
            return
        }

        if (diseaseDatabaseJson == null) {
            _uiState.value = _uiState.value.copy(error = "Dữ liệu bệnh cá chưa sẵn sàng")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                isWaitingForResponse = true,
                messages = _uiState.value.messages + ChatbotMessage("user", userSymptoms)
            )

            try {
                val diagnosis = callGroqApiWithRag(userSymptoms)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatbotMessage("assistant", diagnosis),
                    isLoading = false,
                    isWaitingForResponse = false,
                    userInput = ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Lỗi khi gọi API: ${e.message}",
                    isLoading = false,
                    isWaitingForResponse = false
                )
            }
        }
    }

    /**
     * Gọi Groq API với RAG (Retrieval Augmented Generation)
     * Kết hợp dữ liệu local benh_ca.json + triệu chứng người dùng
     */
    private suspend fun callGroqApiWithRag(userSymptoms: String): String {
        return withIOContext {
            val apiKey = BuildConfig.GROQ_API_KEY
            if (apiKey.isEmpty()) {
                throw Exception("GROQ_API_KEY not configured in BuildConfig")
            }

            // Xây dựng system message
            val systemMessage = """
                Bạn là chuyên gia bệnh học thủy sản chuyên về bệnh cá. 
                Nhiệm vụ của bạn là chẩn đoán bệnh cá dựa hoàn toàn vào dữ liệu cơ sở dữ liệu được cấp dưới đây.
                TUYỆT ĐỐI KHÔNG được thêm kiến thức ngoài. Chỉ sử dụng dữ liệu được cung cấp.
                
                Định dạng trả lời:
                1. **Tên bệnh**: [Tên bệnh]
                2. **Triệu chứng phù hợp**: [Liệt kê triệu chứng khớp với dữ liệu]
                3. **Nguyên nhân**: [Nguyên nhân từ dữ liệu]
                4. **Cách xử lý**: [Cách xử lý từ dữ liệu]
                5. **Mức độ nghiêm trọng**: [Nhẹ/Trung bình/Nghiêm trọng]
            """.trimIndent()

            // Xây dựng messages array
            val messages = listOf(
                mapOf(
                    "role" to "system",
                    "content" to systemMessage
                ),
                mapOf(
                    "role" to "user",
                    "content" to "Dữ liệu cơ sở dữ liệu bệnh cá:\n\n$diseaseDatabaseJson"
                ),
                mapOf(
                    "role" to "user",
                    "content" to "Triệu chứng cá của tôi: $userSymptoms\n\nVui lòng chẩn đoán và đề xuất cách xử lý."
                )
            )

            // Xây dựng request body
            val requestBody = mapOf(
                "model" to "llama3-70b-8192",
                "messages" to messages,
                "temperature" to 0.5,
                "max_tokens" to 1024
            )

            val jsonBody = gson.toJson(requestBody).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("API Error: ${response.code} - ${response.message}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val responseJson = gson.fromJson(responseBody, JsonObject::class.java)

            val choices = responseJson.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                throw Exception("No choices in response")
            }

            val content = choices.get(0).asJsonObject
                .get("message").asJsonObject
                .get("content").asString

            // Loại bỏ markdown formatting nếu có
            content.replace(Regex("\\*\\*"), "")
                .replace(Regex("\\*"), "")
                .replace(Regex("__"), "")
                .replace(Regex("_"), "")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            userInput = "",
            error = null
        )
    }

    /**
     * Helper function để chuyển vào IO coroutine context
     */
    private suspend fun <T> withIOContext(block: suspend () -> T): T {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            block()
        }
    }
}
