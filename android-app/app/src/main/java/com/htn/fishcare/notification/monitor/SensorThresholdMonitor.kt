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
 * Ngưỡng cảnh báo cho ao cá nông nghiệp:
 *  Nhiệt độ:   < 15°C       → cảnh báo thấp (cá stress, ăn kém)
 *              > 34°C       → nguy hiểm cao (cần bật guồng oxy)
 *  TDS:        400-600 ppm  → cảnh báo (nước bắt đầu bẩn)
 *              > 600 ppm    → nguy hiểm (nước quá bẩn, xử lý ngay)
 *  Độ đục TS300B ADC: < 1000 → nguy hiểm (nước quá đục, cá khó thở)
 *              < 1500       → cảnh báo (nước hơi đục)
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
        // ════════════════════════════════════════
        // KIỂM TRA NHIỆT ĐỘ
        // ════════════════════════════════════════
        when {
            temp > 34f -> {
                pushAlertIfDebounced(
                    key     = "temp_critical",
                    type    = "temperature",
                    title   = "NGUY HIỂM: Nhiệt độ ao cao",
                    message = "Nhiệt độ hiện tại ${String.format("%.1f", temp)}°C (ngưỡng nguy hiểm > 34°C). Hãy bật guồng ngay để làm mát! Cá sẽ bị chết nếu không xử lý."
                )
            }
            temp < 15f -> {
                pushAlertIfDebounced(
                    key     = "temp_low",
                    type    = "temperature",
                    title   = "Nhiệt độ ao thấp",
                    message = "Nhiệt độ hiện tại ${String.format("%.1f", temp)}°C (ngưỡng cảnh báo < 15°C). Cá bị stress, ăn kém."
                )
            }
        }

        // ════════════════════════════════════════
        // KIỂM TRA CHẤT LƯỢNG NƯỚC (TDS)
        // ════════════════════════════════════════
        when {
            tds > 600f -> {
                pushAlertIfDebounced(
                    key     = "tds_critical",
                    type    = "water_quality",
                    title   = "NGUY HIỂM: Nước quá bẩn",
                    message = "TDS ở mức ${tds.toInt()} ppm (ngưỡng nguy hiểm > 600 ppm). Phải xử lý ngay! Thay nước hoặc vệ sinh bộ lọc khẩn cấp."
                )
            }
            tds > 400f -> {
                pushAlertIfDebounced(
                    key     = "tds_warning",
                    type    = "water_quality",
                    title   = "Chất lượng nước bắt đầu bẩn",
                    message = "TDS ở mức ${tds.toInt()} ppm (ngưỡng cảnh báo 400-600 ppm). Nước bắt đầu bẩn, cần xử lý sớm."
                )
            }
        }

        // ════════════════════════════════════════
        // KIỂM TRA ĐỘ ĐỤC (TS300B - ADC raw: 0-4095, thấp = đục)
        // ════════════════════════════════════════
        when {
            turbidity < 1000f -> {
                pushAlertIfDebounced(
                    key     = "turb_critical",
                    type    = "water_quality",
                    title   = "NGUY HIỂM: Nước quá đục",
                    message = "Độ đục ADC = ${String.format("%.0f", turbidity)} (range 0-4095, ngưỡng nguy hiểm < 1000). ⚡ CÁ KHÓ THỞ! Nước quá đục → cá khó nhìn thức ăn + cạn oxy. Cần vệ sinh ao cá và bật oxy ngay."
                )
            }
            turbidity < 1500f -> {
                pushAlertIfDebounced(
                    key     = "turb_warning",
                    type    = "water_quality",
                    title   = "Nước hơi đục",
                    message = "Độ đục ADC = ${String.format("%.0f", turbidity)} (range 0-4095, ngưỡng cảnh báo < 1500). Nước hơi đục → cá khó nhìn thức ăn. Cần vệ sinh ao cá sớm."
                )
            }
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
        val hasAlert = tds > 400f || temp > 34f || temp < 15f || turbidity < 1500f
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
        
        // Nhiệt độ
        when {
            temp > 34f -> tags.add("temp_critical")
            temp < 15f -> tags.add("temp_low")
        }
        
        // Chất lượng nước
        when {
            tds > 600f -> tags.add("tds_critical")
            tds > 400f -> tags.add("tds_warning")
        }
        
        // Độ đục (TS300B ADC: thấp = đục)
        when {
            turb < 1000f -> tags.add("turb_critical")
            turb < 1500f -> tags.add("turb_warning")
        }
        
        return tags.joinToString(",")
    }
}
