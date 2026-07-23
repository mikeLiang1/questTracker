package com.mikeliang.questtracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionDao {

    @Query("SELECT * FROM completions")
    fun observeCompletions(): Flow<List<CompletionEntity>>

    @Insert
    suspend fun insert(completion: CompletionEntity)

    /**
     * Backs the same-day mis-tap undo — the one exception to append-only completions.
     * Keyed on (questId, completedAt): the engine never banks the same quest twice in
     * the same instant, so this deletes at most the one record the undo targeted.
     */
    @Query("DELETE FROM completions WHERE questId = :questId AND completedAtEpochMillis = :completedAtEpochMillis")
    suspend fun delete(questId: String, completedAtEpochMillis: Long)

    @Query("SELECT * FROM completions WHERE questId = :questId")
    suspend fun completionsFor(questId: String): List<CompletionEntity>

    /** Inclusive on both ends, matching [com.mikeliang.questtracker.core.repository.QuestRepository]. */
    @Query("SELECT * FROM completions WHERE periodStartEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    suspend fun completionsInRange(fromEpochDay: Long, toEpochDay: Long): List<CompletionEntity>
}
