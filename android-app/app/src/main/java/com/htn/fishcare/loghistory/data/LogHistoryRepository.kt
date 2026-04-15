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
                val logs = mutableListOf<FeedLogEntry>()

                snapshot.children.forEach { child ->
                    if (child.key == "counter") {
                        return@forEach
                    }

                    val gram = child.child("gram").getValue(Float::class.java) ?: 0f
                    val mode = child.child("mode").getValue(String::class.java).orEmpty()
                    val time = child.child("time").getValue(String::class.java).orEmpty()

                    logs.add(
                        FeedLogEntry(
                            id = child.key.orEmpty(),
                            gram = gram,
                            mode = mode,
                            time = time
                        )
                    )
                }

                val sortedLogs = logs.sortedByDescending { entry ->
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
}
