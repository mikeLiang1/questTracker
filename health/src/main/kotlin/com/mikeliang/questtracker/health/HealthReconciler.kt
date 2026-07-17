package com.mikeliang.questtracker.health

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.autoCompletionFor
import com.mikeliang.questtracker.core.engine.periodStartFor
import com.mikeliang.questtracker.core.health.HealthDataSource
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.repository.QuestRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The one sync pass, shared by the periodic worker and the on-app-open refresh
 * (Phase 4 policy: one code path, run twice as often, is safer than two).
 *
 * For each active auto-tracked quest and each local date in the recent window,
 * re-read the day's total and bank an auto-tracked completion if the target was hit
 * and the period isn't already credited. Idempotent (period dedupe), strictly
 * additive (below-target and Unavailable days write nothing, and nothing is ever
 * revoked), and the completion decision itself is :core's [autoCompletionFor] —
 * this class only reads and persists.
 */
class HealthReconciler @Inject constructor(
    private val healthSource: HealthDataSource,
    private val repository: QuestRepository,
    private val clock: Clock,
) {

    suspend fun reconcile() {
        val autoTracked = repository.observeQuests().first().filter {
            it.status == QuestStatus.Active && it.autoTracking != null
        }
        if (autoTracked.isEmpty()) return

        // Today plus the two previous local dates: covers at least the last 48h so
        // late-arriving provider data still lands. Anything older is deliberately
        // ignored — rest-day absorption already treats those days as neutral.
        val today = clock.today()
        val window = (WINDOW_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }

        for (quest in autoTracked) {
            val tracking = quest.autoTracking ?: continue
            val cadence = (quest.kind as? QuestKind.Recurring)?.cadence ?: continue
            var records = repository.completionsFor(quest.id)

            // Oldest first, so a completion banked for (say) Tuesday suppresses the
            // rest of that week for a weekly quest without re-reading.
            for (date in window) {
                val periodStart = periodStartFor(date, cadence)
                val banked = records.any { periodStartFor(it.periodStart, cadence) == periodStart }
                if (banked) continue // skip the read entirely — nothing could change

                val reading = healthSource.readDay(tracking.metric, date)
                val record = autoCompletionFor(quest, date, reading, records, clock.now())
                if (record != null) {
                    repository.recordCompletion(record)
                    records = records + record
                }
            }
        }
    }

    private companion object {
        const val WINDOW_DAYS = 3
    }
}
