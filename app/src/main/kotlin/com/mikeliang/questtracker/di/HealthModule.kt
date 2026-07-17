package com.mikeliang.questtracker.di

import com.mikeliang.questtracker.core.health.HealthDataSource
import com.mikeliang.questtracker.health.HealthConnectApi
import com.mikeliang.questtracker.health.HealthConnectDataSource
import com.mikeliang.questtracker.health.RealHealthConnectApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {

    @Binds
    @Singleton
    abstract fun bindHealthDataSource(impl: HealthConnectDataSource): HealthDataSource

    @Binds
    @Singleton
    abstract fun bindHealthConnectApi(impl: RealHealthConnectApi): HealthConnectApi
}
