package com.mikeliang.questtracker.core.repository

import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.JournalEntryId
import kotlinx.coroutines.flow.Flow

/**
 * Persistence seam for journal entries, implemented by :data (Room). Deliberately
 * separate from [QuestRepository]: that interface's contract is append-only
 * permanence, while entries are the one mutable, deletable thing in the app — they
 * are the user's words, not banked gains. The law both sides share: a completion
 * recorded because an entry was saved lives in [QuestRepository] as an ordinary
 * append-only record, and nothing here can touch it.
 */
interface JournalRepository {

    /** Every entry, newest first. */
    fun observeEntries(): Flow<List<JournalEntry>>

    suspend fun getEntry(id: JournalEntryId): JournalEntry?

    /** Creates or updates an entry (edits set [JournalEntry.editedAt]). */
    suspend fun upsertEntry(entry: JournalEntry)

    /** Removes the entry only — never a completion it may have triggered. */
    suspend fun deleteEntry(id: JournalEntryId)
}
