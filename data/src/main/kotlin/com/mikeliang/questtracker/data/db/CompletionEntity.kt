package com.mikeliang.questtracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row for one banked [com.mikeliang.questtracker.core.model.CompletionRecord]. Append-only —
 * gains are permanent — except for [CompletionDao.delete], which backs the same-day mis-tap
 * undo. There is deliberately no update method. [periodStartEpochDay] is indexed for range
 * queries, [questId] for per-quest lookup.
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
