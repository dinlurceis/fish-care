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

class FanControlRepository {
    private val fanRef = FirebaseDatabase.getInstance().getReference("aquarium/control/quat")

    fun observeFanState(): Flow<Boolean> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(Boolean::class.java) ?: false
                trySend(state)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        fanRef.addValueEventListener(listener)
        awaitClose { fanRef.removeEventListener(listener) }
    }

    suspend fun setFanState(enabled: Boolean) {
        fanRef.awaitUpdate(enabled)
    }

    private suspend fun DatabaseReference.awaitUpdate(value: Any) {
        suspendCancellableCoroutine<Unit> { continuation ->
            setValue(value)
                .addOnSuccessListener {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                .addOnFailureListener { throwable ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(throwable)
                    }
                }
        }
    }
}
