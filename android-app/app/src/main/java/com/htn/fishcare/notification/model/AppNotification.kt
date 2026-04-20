package com.htn.fishcare.notification.model

/**
 * Firebase schema (cùng cấp với /aquarium):
 *
 * /notifications
 *   /{pushId}
 *     type:      "water_quality" | "temperature" | "feed" | "alert"
 *     title:     "⚠️ Chất lượng nước thấp"
 *     message:   "TDS đang ở mức 650 ppm - ngưỡng nguy hiểm"
 *     timestamp: 1713500000000
 *     read:      false
 *
 * /logs          (lịch sử cho ăn – do ESP32 ghi)
 *   counter: 5
 *   log1: { gram, mode, time }
 *
 * /tds_logs      (log cảnh báo chất lượng nước/nhiệt độ)
 *   /{pushId}
 *     tds:       450
 *     temp:      35.2
 *     turbidity: 12
 *     timestamp: 1713500000000
 *     alert:     "tds_high" | "temp_high" | "temp_low" | "turbidity_high"
 */
data class AppNotification(
    val id: String = "",
    val type: String = "",      // "water_quality", "temperature", "feed", "alert"
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false
) {
    val icon: String get() = when (type) {
        "water_quality" -> "💧"
        "temperature"   -> "🌡️"
        "feed"          -> "🐟"
        else            -> "⚠️"
    }
}
