package com.mikeliang.questtracker.reflection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remembers which month's reflection was last handled. App-shell state, not domain
 * state (the reflection's *edits* all live in the quest repository), so it follows
 * [com.mikeliang.questtracker.onboarding.OnboardingStateStore] into :app DataStore.
 * Interface so ViewModel tests can use an in-memory fake.
 */
interface ReflectionStateStore {

    /**
     * The most recent month the user completed *or* skipped a reflection for; null
     * before the first one. A Flow so the Today banner drops the moment either
     * happens — skipping is handling, and handling is never re-prompted.
     */
    fun lastHandledMonth(): Flow<YearMonth?>

    /** Marks [month]'s reflection handled. Idempotent; months only move forward. */
    suspend fun markHandled(month: YearMonth)
}

private val Context.reflectionDataStore by preferencesDataStore(name = "reflection")

private val KEY_LAST_HANDLED_MONTH = stringPreferencesKey("last_handled_month")

class DataStoreReflectionStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReflectionStateStore {

    override fun lastHandledMonth(): Flow<YearMonth?> =
        context.reflectionDataStore.data.map { prefs ->
            prefs[KEY_LAST_HANDLED_MONTH]?.let(YearMonth::parse)
        }

    override suspend fun markHandled(month: YearMonth) {
        context.reflectionDataStore.edit { prefs ->
            prefs[KEY_LAST_HANDLED_MONTH] = month.toString()
        }
    }
}
