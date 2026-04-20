package com.htn.fishcare.loghistory.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.htn.fishcare.loghistory.model.FeedLogEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LogHistoryRepository {
    private val logsRef = FirebaseDatabase.getInstance().getReference("logs")

    fun observeLogs(): Flow<List<FeedLogEntry>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sortedLogs = snapshot.children
                    .mapNotNull { child ->
                        val key = child.key.orEmpty()
                        if (!key.startsWith("log")) {
                            return@mapNotNull null
                        }

                        FeedLogEntry(
                            id = key,
                            gram = child.child("gram").asFloat(),
                            mode = child.child("mode").getValue(String::class.java).orEmpty(),
                            time = child.child("time").asText()
                        )
                    }
                    .sortedByDescending { entry ->
                        entry.id.removePrefix("log").toIntOrNull() ?: 0
                    }

                trySend(sortedLogs)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        logsRef.addValueEventListener(listener)
        awaitClose { logsRef.removeEventListener(listener) }
    }

    private fun DataSnapshot.asFloat(): Float {
        val value = this.value
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

    private fun DataSnapshot.asText(): String {
        val value = this.value
        return when (value) {
            is String -> value
            is Number -> value.toString()
            else -> ""
        }
    }
}
