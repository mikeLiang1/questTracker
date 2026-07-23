package com.mikeliang.questtracker.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mikeliang.questtracker.core.onboarding.StartingClass
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * First-run persistence for onboarding. App-shell state, not domain state, so it lives
 * in :app (DataStore) rather than the quest repository. Interface so ViewModel tests
 * can use an in-memory fake.
 */
interface OnboardingStateStore {

    /** True once onboarding has been completed (or skipped) on this install. */
    suspend fun isOnboardingComplete(): Boolean

    /**
     * Marks onboarding done. Idempotent. [chosenClass] is null when the user skipped
     * the presets; the choice is stored for later phases, not read back today.
     */
    suspend fun markOnboardingComplete(chosenClass: StartingClass?)
}

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

private val KEY_COMPLETE = booleanPreferencesKey("onboarding_complete")
private val KEY_STARTING_CLASS = stringPreferencesKey("starting_class")

class DataStoreOnboardingStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnboardingStateStore {

    override suspend fun isOnboardingComplete(): Boolean =
        context.onboardingDataStore.data.first()[KEY_COMPLETE] ?: false

    override suspend fun markOnboardingComplete(chosenClass: StartingClass?) {
        context.onboardingDataStore.edit { prefs ->
            prefs[KEY_COMPLETE] = true
            if (chosenClass != null) prefs[KEY_STARTING_CLASS] = chosenClass.name
        }
    }
}
