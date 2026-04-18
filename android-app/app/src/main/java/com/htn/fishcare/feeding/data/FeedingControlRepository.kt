package com.htn.fishcare.feeding.data

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

enum class FeedMode {
    AUTO,
    MANUAL,
    GRAM,
    UNKNOWN
}

data class FeedingControlState(
    val mode: FeedMode = FeedMode.UNKNOWN,
    val isEnabled: Boolean = false,
    val targetGram: Float = 0f
)

class FeedingControlRepository {
    private val controlRef = FirebaseDatabase.getInstance().getReference("aquarium/control/thucan")

    fun observeControlState(): Flow<FeedingControlState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val mode = snapshot.child("mode").getValue(String::class.java)
                    .orEmpty()
                    .lowercase()
                val state = snapshot.child("state").getValue(Boolean::class.java) ?: false
                val targetGram = snapshot.child("target_gram").asFloat()

                trySend(
                    FeedingControlState(
                        mode = mode.toFeedMode(),
                        isEnabled = state,
                        targetGram = targetGram
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        controlRef.addValueEventListener(listener)
        awaitClose { controlRef.removeEventListener(listener) }
    }

    suspend fun setAutoMode(enabled: Boolean) {
        controlRef.awaitUpdate(
            mapOf(
                "mode" to "auto",
                "state" to enabled,
                "target_gram" to 0f
            )
        )
    }

    suspend fun setManualMode(enabled: Boolean) {
        controlRef.awaitUpdate(
            mapOf(
                "mode" to "manual",
                "state" to enabled,
                "target_gram" to 0f
            )
        )
    }

    suspend fun startGramMode(targetGram: Float) {
        controlRef.awaitUpdate(
            mapOf(
                "mode" to "gram",
                "state" to true,
                "target_gram" to targetGram
            )
        )
    }

    suspend fun stopGramMode() {
        controlRef.awaitUpdate(
            mapOf(
                "mode" to "gram",
                "state" to false,
                "target_gram" to 0f
            )
        )
    }

    private fun String.toFeedMode(): FeedMode = when (this) {
        "auto" -> FeedMode.AUTO
        "manual" -> FeedMode.MANUAL
        "gram" -> FeedMode.GRAM
        else -> FeedMode.UNKNOWN
    }

    private fun DataSnapshot.asFloat(): Float {
        val value = this.value
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

    private suspend fun DatabaseReference.awaitUpdate(update: Map<String, Any>) {
        suspendCancellableCoroutine<Unit> { continuation ->
            updateChildren(update)
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
