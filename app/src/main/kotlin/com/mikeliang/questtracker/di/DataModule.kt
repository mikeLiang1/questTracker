package com.mikeliang.questtracker.di

import android.content.Context
import androidx.room.Room
import com.mikeliang.questtracker.core.repository.JournalRepository
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.data.RoomJournalRepository
import com.mikeliang.questtracker.data.RoomQuestRepository
import com.mikeliang.questtracker.data.db.MIGRATION_1_2
import com.mikeliang.questtracker.data.db.MIGRATION_2_3
import com.mikeliang.questtracker.data.db.MIGRATION_3_4
import com.mikeliang.questtracker.data.db.QuestTrackerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuestTrackerDatabase =
        Room.databaseBuilder(context, QuestTrackerDatabase::class.java, QuestTrackerDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    @Singleton
    fun provideQuestRepository(db: QuestTrackerDatabase): QuestRepository =
        RoomQuestRepository(db.questDao(), db.completionDao())

    @Provides
    @Singleton
    fun provideJournalRepository(db: QuestTrackerDatabase): JournalRepository =
        RoomJournalRepository(db.journalDao())
}
