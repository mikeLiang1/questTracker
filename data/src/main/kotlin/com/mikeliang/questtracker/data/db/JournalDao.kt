package com.mikeliang.questtracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entries ORDER BY createdAtEpochMillis DESC")
    fun observeEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getEntry(id: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntryEntity)

    /** Deletes the entry only — completions it triggered live in their own table, untouched. */
    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: String)
}
