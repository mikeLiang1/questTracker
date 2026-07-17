package com.mikeliang.questtracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mikeliang.questtracker.core.health.HealthMetric
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * The real Health Connect adapter. Pure translation — availability codes, metric
 * enums, and unit types map 1:1 onto our seam; every decision lives above it.
 */
class RealHealthConnectApi @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectApi {

    override val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    override fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Installed
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UpdateRequired
            else -> HealthConnectAvailability.NotInstalled
        }

    override suspend fun grantedPermissions(): Set<String> =
        client().permissionController.getGrantedPermissions()

    override suspend fun aggregate(metric: HealthMetric, start: Instant, end: Instant): Double? {
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(metric.aggregateMetric()),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return when (metric) {
            HealthMetric.Steps -> result[StepsRecord.COUNT_TOTAL]?.toDouble()
            HealthMetric.DistanceMeters -> result[DistanceRecord.DISTANCE_TOTAL]?.inMeters
            HealthMetric.Floors -> result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]
            HealthMetric.ActiveEnergyKcal ->
                result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            HealthMetric.ExerciseMinutes ->
                result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutesDouble()
            HealthMetric.SleepMinutes ->
                result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutesDouble()
        }
    }

    private fun HealthMetric.aggregateMetric(): AggregateMetric<*> = when (this) {
        HealthMetric.Steps -> StepsRecord.COUNT_TOTAL
        HealthMetric.DistanceMeters -> DistanceRecord.DISTANCE_TOTAL
        HealthMetric.Floors -> FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
        HealthMetric.ActiveEnergyKcal -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
        HealthMetric.ExerciseMinutes -> ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
        HealthMetric.SleepMinutes -> SleepSessionRecord.SLEEP_DURATION_TOTAL
    }

    private fun Duration.toMinutesDouble(): Double = toMillis() / 60_000.0

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}
