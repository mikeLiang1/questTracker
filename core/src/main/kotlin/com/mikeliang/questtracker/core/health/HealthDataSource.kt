package com.mikeliang.questtracker.core.health

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The phone-only metrics v1 designs around (design doc §10). */
enum class HealthMetric {
    Steps,
    DistanceMeters,
    Floors,
    ActiveEnergyKcal,
    ExerciseMinutes,
    SleepMinutes,
}

/**
 * A health read result. There is deliberately no error type: permission loss, a
 * missing provider, a failed sync, and genuinely absent data all collapse into
 * [Unavailable], because the product treats them identically — offer manual
 * completion, never fail a quest, never dent consistency. Wrong data is worse than
 * no data, so absence is never represented as zero.
 */
sealed interface HealthReading {
    data class Available(val value: Double) : HealthReading
    data object Unavailable : HealthReading
}

/**
 * Read-side seam to Health Connect (implemented in :health). Implementations must
 * never let an exception cross this boundary — every failure path is [HealthReading.Unavailable].
 */
interface HealthDataSource {

    /** The metric's total for [date] in the user's zone. */
    suspend fun readDay(metric: HealthMetric, date: LocalDate): HealthReading

    /** Live progress for today, for on-screen auto-tracked quests. */
    fun observeToday(metric: HealthMetric): Flow<HealthReading>
}
