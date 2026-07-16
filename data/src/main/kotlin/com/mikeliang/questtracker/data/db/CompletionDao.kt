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

    @Query("SELECT * FROM completions WHERE questId = :questId")
    suspend fun completionsFor(questId: String): List<CompletionEntity>

    /** Inclusive on both ends, matching [com.mikeliang.questtracker.core.repository.QuestRepository]. */
    @Query("SELECT * FROM completions WHERE periodStartEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    suspend fun completionsInRange(fromEpochDay: Long, toEpochDay: Long): List<CompletionEntity>
}
