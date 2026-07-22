package com.mikeliang.questtracker.di

import com.mikeliang.questtracker.reminders.AlarmScheduler
import com.mikeliang.questtracker.reminders.AndroidAlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the reminder OS-scheduling seam to its AlarmManager implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler
}
