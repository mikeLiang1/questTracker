package com.mikeliang.questtracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row for one banked [com.mikeliang.questtracker.core.model.CompletionRecord]. Append-only:
 * there is deliberately no update/delete DAO method, mirroring the domain rule that gains are
 * permanent. [periodStartEpochDay] is indexed for range queries, [questId] for per-quest lookup.
 */
@Entity(
    tableName = "completions",
    indices = [Index("questId"), Index("periodStartEpochDay")],
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: String,
    val completedAtEpochMillis: Long,
    val periodStartEpochDay: Long,
    val source: String,
    val escalationLevel: Int?,
    val attribute: String?,
    val basePoints: Double?,
)
