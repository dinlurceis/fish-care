package com.htn.fishcare.control.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AutomationThresholds(
    val tempHigh: Float = 32f,
    val turbidityHigh: Float = 2600f
)

class AutomationRepository {
    private val thresholdRef = FirebaseDatabase.getInstance().getReference("aquarium/config/thresholds")

    fun observeThresholds(): Flow<AutomationThresholds> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = snapshot.child("temp_high").getValue(Float::class.java) ?: 32f
                val turbidity = snapshot.child("turbidity_high").getValue(Float::class.java) ?: 2600f
                trySend(AutomationThresholds(temp, turbidity))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        thresholdRef.addValueEventListener(listener)
        awaitClose { thresholdRef.removeEventListener(listener) }
    }

    suspend fun updateThresholds(thresholds: AutomationThresholds) {
        val updates = mapOf(
            "temp_high" to thresholds.tempHigh,
            "turbidity_high" to thresholds.turbidityHigh
        )
        thresholdRef.awaitUpdate(updates)
    }

    private suspend fun DatabaseReference.awaitUpdate(updates: Map<String, Any>) {
        suspendCancellableCoroutine<Unit> { continuation ->
            updateChildren(updates)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                .addOnFailureListener { throwable ->
                    if (continuation.isActive) continuation.resumeWithException(throwable)
                }
        }
    }
}
