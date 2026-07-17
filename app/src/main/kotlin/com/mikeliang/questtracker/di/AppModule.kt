package com.mikeliang.questtracker.di

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.QuestEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

/**
 * The real [Clock]: wall-clock time in the user's current zone. The only place in the
 * app that reads system time — everything downstream takes it via injection.
 */
private object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock

    @Provides
    @Singleton
    fun provideQuestEngine(clock: Clock): QuestEngine = QuestEngine(clock)
}
