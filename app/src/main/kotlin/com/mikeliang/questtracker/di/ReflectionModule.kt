package com.mikeliang.questtracker.di

import com.mikeliang.questtracker.reflection.DataStoreReflectionStateStore
import com.mikeliang.questtracker.reflection.ReflectionStateStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReflectionModule {

    @Binds
    @Singleton
    abstract fun bindReflectionStateStore(impl: DataStoreReflectionStateStore): ReflectionStateStore
}
