package com.htn.fishcare.notification.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.htn.fishcare.notification.model.AppNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class NotificationRepository @Inject constructor() {

    private val TAG = "NotifRepo"

    /** Đọc toàn bộ /notifications, sort mới nhất lên đầu */
    fun observeNotifications(): Flow<List<AppNotification>> = callbackFlow {
        val ref = FirebaseDatabase.getInstance().getReference("notifications")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    runCatching {
                        AppNotification(
                            id        = child.key ?: return@mapNotNull null,
                            type      = child.child("type").getValue(String::class.java) ?: "alert",
                            title     = child.child("title").getValue(String::class.java) ?: "",
                            message   = child.child("message").getValue(String::class.java) ?: "",
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            read      = child.child("read").getValue(Boolean::class.java) ?: false
                        )
                    }.getOrNull()
                }.sortedByDescending { it.timestamp }

                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeNotifications cancelled: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Đếm số thông báo chưa đọc */
    fun observeUnreadCount(): Flow<Int> = callbackFlow {
        val ref = FirebaseDatabase.getInstance().getReference("notifications")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count {
                    it.child("read").getValue(Boolean::class.java) == false
                }
                trySend(count)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Đánh dấu 1 thông báo đã đọc */
    suspend fun markAsRead(id: String) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("notifications/$id/read")
        suspendCancellableCoroutine<Unit> { cont ->
            ref.setValue(true)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    /** Đánh dấu tất cả đã đọc */
    suspend fun markAllAsRead(ids: List<String>) {
        val db = FirebaseDatabase.getInstance()
        val updates = ids.associate { "notifications/$it/read" to true }
        suspendCancellableCoroutine<Unit> { cont ->
            db.reference.updateChildren(updates)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    /**
     * Ghi 1 notification vào Firebase (dùng khi App tự phát hiện ngưỡng sensor).
     * Thông thường ESP32 hoặc Cloud Function sẽ ghi, nhưng App cũng có thể ghi.
     */
    fun pushNotification(type: String, title: String, message: String) {
        val ref = FirebaseDatabase.getInstance().getReference("notifications")
        val id  = ref.push().key ?: return
        ref.child(id).setValue(
            mapOf(
                "type"      to type,
                "title"     to title,
                "message"   to message,
                "timestamp" to System.currentTimeMillis(),
                "read"      to false
            )
        )
        Log.d(TAG, "Pushed notification [$type]: $title")
    }
}
