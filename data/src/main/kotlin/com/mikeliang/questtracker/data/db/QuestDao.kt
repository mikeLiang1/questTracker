package com.mikeliang.questtracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {

    @Query("SELECT * FROM quests")
    fun observeQuests(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun getQuest(id: String): QuestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quest: QuestEntity)

    /**
     * Hard delete — only ever reached through :core's `canDeleteQuest` guard
     * (zero-completion mis-creations). Quests with history retire instead.
     */
    @Query("DELETE FROM quests WHERE id = :id")
    suspend fun delete(id: String)
}
