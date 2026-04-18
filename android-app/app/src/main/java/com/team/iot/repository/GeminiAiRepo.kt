package com.team.iot.repository

import android.util.Log
import com.example.fishcare.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiAiRepo {
    private val TAG = "GeminiAiRepo"

    suspend fun analyzeWaterChart(dataList: List<SensorData>): String = withContext(Dispatchers.IO) {
        try {
            // 1. Format dataList thành chuỗi text dạng bảng
            val formattedData = formatWaterDataAsTable(dataList)

            // 2. Build prompt tiếng Việt
            val prompt = buildWaterAnalysisPrompt(formattedData, dataList.size)

            // 3. Gọi Gemini API (model gemini-1.5-flash)
            val apiKey = BuildConfig.GEMINI_API_KEY
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )

            val response = generativeModel.generateContent(prompt)
            val result = response.text ?: "Không thể phân tích dữ liệu."

            Log.d(TAG, "Gemini API response: $result")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing water chart: ${e.message}", e)
            "Lỗi khi phân tích dữ liệu. Vui lòng thử lại."
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
     * Build prompt cho Gemini API
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
