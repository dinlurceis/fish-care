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
import javax.inject.Inject

class FanControlRepository @Inject constructor() {
    private val fanRef = FirebaseDatabase.getInstance().getReference("aquarium/control/guong")
    private val autoActiveRef = FirebaseDatabase.getInstance().getReference("aquarium/control/guong_auto_active")
    private val tempThresholdRef = FirebaseDatabase.getInstance().getReference("aquarium/settings/temp_threshold")
    private val waterThresholdRef = FirebaseDatabase.getInstance().getReference("aquarium/settings/water_threshold")

    fun observeFanState(): Flow<Boolean> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(Boolean::class.java) ?: false
                trySend(state)
            }

            override fun onCancelled(error: DatabaseError) {
                // Log lỗi thay vì throw exception gây crash app
                android.util.Log.e("FanControlRepo", "Permission Denied: ${error.message}")
                close()
            }
        }
        fanRef.addValueEventListener(listener)
        awaitClose { fanRef.removeEventListener(listener) }
    }

    fun observeAutoActiveState(): Flow<Boolean> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(Boolean::class.java) ?: false
                trySend(state)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FanControlRepo", "Permission Denied: ${error.message}")
                close()
            }
        }
        autoActiveRef.addValueEventListener(listener)
        awaitClose { autoActiveRef.removeEventListener(listener) }
    }

    suspend fun setFanState(enabled: Boolean) {
        fanRef.awaitUpdate(enabled)
    }

    suspend fun updateTempThreshold(value: Float) {
        tempThresholdRef.awaitUpdate(value)
    }

    suspend fun updateWaterThreshold(value: Int) {
        waterThresholdRef.awaitUpdate(value)
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
