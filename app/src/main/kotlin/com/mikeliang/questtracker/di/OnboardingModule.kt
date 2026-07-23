package com.mikeliang.questtracker.di

import com.mikeliang.questtracker.onboarding.DataStoreOnboardingStateStore
import com.mikeliang.questtracker.onboarding.LogcatOnboardingTiming
import com.mikeliang.questtracker.onboarding.OnboardingStateStore
import com.mikeliang.questtracker.onboarding.OnboardingTiming
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {

    @Binds
    @Singleton
    abstract fun bindOnboardingStateStore(impl: DataStoreOnboardingStateStore): OnboardingStateStore

    @Binds
    @Singleton
    abstract fun bindOnboardingTiming(impl: LogcatOnboardingTiming): OnboardingTiming
}
