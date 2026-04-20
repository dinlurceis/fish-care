package com.htn.fishcare.notification.monitor

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.htn.fishcare.notification.data.NotificationRepository
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lắng nghe /aquarium realtime và tự viết vào:
 *  - /notifications  khi có chỉ số vượt ngưỡng
 *  - /tds_logs       mỗi khi sensor cập nhật
 *
 * Ngưỡng (theo PROJECT_CONTEXT.md):
 *  TDS:         > 400 ppm  → cảnh báo cao
 *  Nhiệt độ:   > 33°C     → cảnh báo quá nóng
 *              < 20°C     → cảnh báo quá lạnh
 *  Độ đục:     > 8 NTU    → cảnh báo đục
 */
@Singleton
class SensorThresholdMonitor @Inject constructor(
    private val notifRepo: NotificationRepository
) {
    private val TAG = "ThresholdMonitor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Debounce: tránh spam cùng 1 loại alert trùng nhau trong 10 phút
    private val lastAlertTime = mutableMapOf<String, Long>()
    private val DEBOUNCE_MS = 10 * 60 * 1000L  // 10 phút

    fun startListening() {
        val ref = FirebaseDatabase.getInstance().getReference("aquarium")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = snapshot.child("temperature").getValue(Double::class.java)?.toFloat() ?: return
                val tds  = snapshot.child("water_quality").getValue(Double::class.java)?.toFloat() ?: return
                val turb = snapshot.child("ts300b").getValue(Double::class.java)?.toFloat() ?: return

                checkThresholds(temp, tds, turb)
                writeTdsLog(temp, tds, turb)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Sensor listen cancelled: ${error.message}")
            }
        })
    }

    private fun checkThresholds(temp: Float, tds: Float, turbidity: Float) {
        // TDS cao
        if (tds > 400f) {
            pushAlertIfDebounced(
                key     = "tds_high",
                type    = "water_quality",
                title   = "⚠️ Chất lượng nước không tốt",
                message = "TDS ở mức ${tds.toInt()} ppm (ngưỡng an toàn ≤ 400 ppm). Cần thay nước hoặc kiểm tra bộ lọc."
            )
        }

        // Nhiệt độ quá cao
        if (temp > 33f) {
            pushAlertIfDebounced(
                key     = "temp_high",
                type    = "temperature",
                title   = "🌡️ Nhiệt độ nước quá cao",
                message = "Nhiệt độ hiện tại ${String.format("%.1f", temp)}°C (ngưỡng an toàn ≤ 33°C). Kiểm tra hệ thống làm mát."
            )
        }

        // Nhiệt độ quá thấp
        if (temp < 20f) {
            pushAlertIfDebounced(
                key     = "temp_low",
                type    = "temperature",
                title   = "🌡️ Nhiệt độ nước quá thấp",
                message = "Nhiệt độ hiện tại ${String.format("%.1f", temp)}°C (ngưỡng an toàn ≥ 20°C). Cá có thể bị stress."
            )
        }

        // Độ đục cao
        if (turbidity > 8f) {
            pushAlertIfDebounced(
                key     = "turb_high",
                type    = "water_quality",
                title   = "🌫️ Độ đục nước quá cao",
                message = "Độ đục ở mức ${String.format("%.1f", turbidity)} NTU (ngưỡng an toàn ≤ 8). Cần vệ sinh bộ lọc."
            )
        }
    }

    private fun pushAlertIfDebounced(key: String, type: String, title: String, message: String) {
        val now  = System.currentTimeMillis()
        val last = lastAlertTime[key] ?: 0L
        if (now - last < DEBOUNCE_MS) return   // chưa đủ thời gian debounce

        lastAlertTime[key] = now
        scope.launch {
            runCatching {
                notifRepo.pushNotification(type, title, message)
                Log.d(TAG, "Alert pushed: [$key] $title")
            }
        }
    }

    /** Ghi snapshot cảm biến vào /tds_logs để có lịch sử cảnh báo */
    private fun writeTdsLog(temp: Float, tds: Float, turbidity: Float) {
        // Chỉ ghi khi có ít nhất 1 chỉ số vượt ngưỡng
        val hasAlert = tds > 400f || temp > 33f || temp < 20f || turbidity > 8f
        if (!hasAlert) return

        val db  = FirebaseDatabase.getInstance()
        val ref = db.getReference("tds_logs")
        val id  = ref.push().key ?: return

        val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN"))
        ref.child(id).setValue(
            mapOf(
                "tds"       to tds,
                "temp"      to temp,
                "turbidity" to turbidity,
                "timestamp" to System.currentTimeMillis(),
                "time"      to sdf.format(Date()),
                "alert"     to buildAlertTag(temp, tds, turbidity)
            )
        ).addOnFailureListener {
            Log.e(TAG, "writeTdsLog failed: ${it.message}")
        }
    }

    private fun buildAlertTag(temp: Float, tds: Float, turb: Float): String {
        val tags = mutableListOf<String>()
        if (tds > 400f)   tags.add("tds_high")
        if (temp > 33f)   tags.add("temp_high")
        if (temp < 20f)   tags.add("temp_low")
        if (turb > 8f)    tags.add("turbidity_high")
        return tags.joinToString(",")
    }
}
