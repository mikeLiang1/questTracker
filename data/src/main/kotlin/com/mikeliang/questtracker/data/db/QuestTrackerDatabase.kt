package com.mikeliang.questtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QuestEntity::class, CompletionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class QuestTrackerDatabase : RoomDatabase() {
    abstract fun questDao(): QuestDao
    abstract fun completionDao(): CompletionDao

    companion object {
        const val NAME = "quest_tracker.db"
    }
}
