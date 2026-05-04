package com.htn.fishcare.notification.data

import android.util.Log
import com.google.firebase.database.*
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

                // ❌ KHÔNG crash nữa
                trySend(emptyList())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeUnreadCount(): Flow<Int> = callbackFlow {
        val ref = FirebaseDatabase.getInstance().getReference("notifications")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count {
                    it.child("read").getValue(Boolean::class.java) == false
                }
                trySend(count)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeUnreadCount error: ${error.message}")

                // ❌ KHÔNG crash
                trySend(0)
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun markAsRead(id: String) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("notifications/$id/read")

        suspendCancellableCoroutine<Unit> { cont ->
            ref.setValue(true)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener {
                    Log.e(TAG, "markAsRead error: ${it.message}")
                    if (cont.isActive) cont.resume(Unit) // ❌ không crash
                }
        }
    }

    suspend fun markAllAsRead(ids: List<String>) {
        val db = FirebaseDatabase.getInstance()
        val updates = ids.associate { "notifications/$it/read" to true }

        suspendCancellableCoroutine<Unit> { cont ->
            db.reference.updateChildren(updates)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener {
                    Log.e(TAG, "markAllAsRead error: ${it.message}")
                    if (cont.isActive) cont.resume(Unit)
                }
        }
    }

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
        ).addOnFailureListener {
            Log.e(TAG, "pushNotification error: ${it.message}")
        }

        Log.d(TAG, "Pushed notification [$type]: $title")
    }
}