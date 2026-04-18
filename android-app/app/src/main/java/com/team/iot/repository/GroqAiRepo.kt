package com.team.iot.repository

import android.util.Log
import com.htn.fishcare.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data classes cho Groq API (OpenAI-compatible format)
 */
@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val max_tokens: Int = 500,
    val temperature: Double = 0.7
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>?
)

@Serializable
data class GroqChoice(
    val message: GroqMessage?
)

class GroqAiRepo {
    private val TAG = "GroqAiRepo"
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeWaterChart(dataList: List<SensorData>): String = withContext(Dispatchers.IO) {
        try {
            // Check if API key is configured
            val apiKey = BuildConfig.GROQ_API_KEY
            if (apiKey.isEmpty() || apiKey == "your_groq_api_key_here") {
                return@withContext "Vui lòng cấu hình GROQ_API_KEY trong local.properties"
            }

            // 1. Format dataList thành chuỗi text dạng bảng
            val formattedData = formatWaterDataAsTable(dataList)

            // 2. Build prompt tiếng Việt
            val prompt = buildWaterAnalysisPrompt(formattedData, dataList.size)

            // 3. Gọi Groq API
            val result = callGroqApi(prompt, apiKey)

            Log.d(TAG, "Groq API response: $result")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing water chart: ${e.message}", e)
            "Lỗi khi phân tích dữ liệu. Vui lòng thử lại."
        }
    }

    private fun callGroqApi(prompt: String, apiKey: String): String {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        
        val request = GroqRequest(
            model = "mixtral-8x7b-32768",  // hoặc "llama2-70b-4096"
            messages = listOf(
                GroqMessage(role = "user", content = prompt)
            ),
            max_tokens = 500,
            temperature = 0.7
        )

        val requestBody = json.encodeToString(GroqRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        
        return if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: return "Không thể phân tích dữ liệu."
            try {
                val groqResponse = json.decodeFromString(GroqResponse.serializer(), responseBody)
                groqResponse.choices?.firstOrNull()?.message?.content 
                    ?: "Không thể phân tích dữ liệu."
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Groq response: ${e.message}")
                "Không thể phân tích dữ liệu."
            }
        } else {
            Log.e(TAG, "Groq API error: ${response.code} - ${response.message}")
            "Lỗi khi gọi API phân tích."
        }
    }

    /**
     * Format dataList thành chuỗi text dạng bảng
     * Định dạng: "Ngày dd/MM: TDS=xxx, Nhiệt=xx.x°C, Độục=x.x, pH=x.x"
     */
    private fun formatWaterDataAsTable(dataList: List<SensorData>): String {
        if (dataList.isEmpty()) {
            return "Không có dữ liệu."
        }

        val dateFormat = SimpleDateFormat("dd/MM", Locale("vi", "VN"))
        val stringBuilder = StringBuilder()

        dataList.forEach { data ->
            val dateStr = dateFormat.format(Date(data.timestamp))
            val line = "Ngày $dateStr: TDS=${data.tds.toInt()}, Nhiệt=${String.format("%.1f", data.temperature)}°C, Độục=${String.format("%.1f", data.turbidity)}, pH=${String.format("%.1f", data.ph)}"
            stringBuilder.append(line).append("\n")
        }

        return stringBuilder.toString().trim()
    }

    /**
     * Build prompt cho Groq API
     */
    private fun buildWaterAnalysisPrompt(formattedData: String, dayCount: Int): String {
        return """Đây là dữ liệu chất lượng nước hồ cá $dayCount ngày qua:
$formattedData

Hãy:
(1) Nhận xét xu hướng từng chỉ số.
(2) Cảnh báo nếu có chỉ số vượt ngưỡng (TDS>400, nhiệt>33°C, độ đục>8, pH<6.5 hoặc >8).
(3) Đề xuất hành động cụ thể.

Trả lời tiếng Việt, dưới 200 từ, dùng bullet points."""
    }
}
