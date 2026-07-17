package com.mikeliang.questtracker.health

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.health.HealthDataSource
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.health.HealthReading
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * [HealthDataSource] backed by Health Connect. The interface's contract does the
 * heavy lifting: every failure path — missing provider, revoked permission, a read
 * blowing up, genuinely no data — collapses to [HealthReading.Unavailable], so no
 * exception and no fake zero ever crosses the module boundary.
 */
class HealthConnectDataSource @Inject constructor(
    private val api: HealthConnectApi,
    private val clock: Clock,
) : HealthDataSource {

    override suspend fun readDay(metric: HealthMetric, date: LocalDate): HealthReading {
        if (api.availability() != HealthConnectAvailability.Installed) {
            return HealthReading.Unavailable
        }
        // Day bounds in the user's current zone, [midnight, next midnight) — java.time
        // resolves DST transitions (a 23h or 25h "day" aggregates exactly what the
        // user lived through).
        val zone = clock.zone()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return try {
            when (val total = api.aggregate(metric, start, end)) {
                null -> HealthReading.Unavailable
                else -> HealthReading.Available(total)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthReading.Unavailable
        }
    }

    /**
     * Health Connect has no push API for aggregates, so live progress is a poll:
     * re-read today's total every [POLL_INTERVAL_MILLIS] while collected. "Today"
     * is re-resolved on every tick, so a collection running across midnight (or a
     * timezone change) follows the user's current day.
     */
    override fun observeToday(metric: HealthMetric): Flow<HealthReading> = flow {
        while (currentCoroutineContext().isActive) {
            emit(readDay(metric, clock.today()))
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 60_000L
    }
}
