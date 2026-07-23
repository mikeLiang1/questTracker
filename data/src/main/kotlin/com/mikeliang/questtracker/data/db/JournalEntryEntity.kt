package com.mikeliang.questtracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for [com.mikeliang.questtracker.core.model.JournalEntry]. Unlike
 * completions, entries are mutable: upsert doubles as edit and delete exists —
 * they are the user's words, not banked gains. There is deliberately no reference
 * to any completion an entry may have triggered.
 */
@Entity(
    tableName = "journal_entries",
    indices = [Index("entryDateEpochDay")],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val createdAtEpochMillis: Long,
    val entryDateEpochDay: Long,
    val editedAtEpochMillis: Long?,
)
