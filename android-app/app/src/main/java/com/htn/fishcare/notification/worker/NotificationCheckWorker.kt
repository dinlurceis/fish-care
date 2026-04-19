package com.htn.fishcare.notification.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.htn.fishcare.R
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker: chạy ngầm mỗi 15 phút (minimum của WorkManager),
 * kể cả khi app đóng hoàn toàn.
 *
 * Kiểm tra /notifications xem có entry mới chưa đọc không.
 * Nếu có → hiện Android local notification.
 *
 * SharedPreferences lưu timestamp lần check cuối để không hiện lại
 * notification cũ mỗi lần chạy.
 */
class NotificationCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "NotifCheckWorker"
    private val CHANNEL_ID = "fishcare_alerts"
    private val PREFS_KEY   = "fishcare_notif_prefs"
    private val KEY_LAST_TS = "last_notif_timestamp"

    override suspend fun doWork(): Result {
        return try {
            createNotificationChannel()

            val prefs   = applicationContext.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            val lastTs  = prefs.getLong(KEY_LAST_TS, 0L)
            var newestTs = lastTs

            val snapshot = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .get()
                .await()

            var shown = 0
            snapshot.children.forEach { child ->
                val ts   = child.child("timestamp").getValue(Long::class.java) ?: 0L
                val read = child.child("read").getValue(Boolean::class.java) ?: false

                if (ts > lastTs && !read) {
                    val title   = child.child("title").getValue(String::class.java) ?: ""
                    val message = child.child("message").getValue(String::class.java) ?: ""
                    showNotification(title, message, ts.toInt())
                    if (ts > newestTs) newestTs = ts
                    shown++
                }
            }

            // Lưu timestamp mới nhất đã thấy
            if (newestTs > lastTs) {
                prefs.edit().putLong(KEY_LAST_TS, newestTs).apply()
            }

            Log.d(TAG, "Worker ran: $shown new notifications found")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}")
            Result.retry()
        }
    }

    private fun showNotification(title: String, body: String, id: Int) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!manager.areNotificationsEnabled()) return
        }
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try { manager.notify(id, notif) } catch (_: SecurityException) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cảnh báo hồ cá",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Thông báo nhiệt độ, chất lượng nước" }

            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val WORK_NAME = "fishcare_notif_check"

        /** Gọi 1 lần khi app khởi động để đăng ký periodic job */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(
                15, TimeUnit.MINUTES    // Minimum cho phép của WorkManager
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Giữ job cũ nếu đã schedule
                request
            )
            Log.d("NotifCheckWorker", "Scheduled periodic notification check (15 min)")
        }
    }
}
