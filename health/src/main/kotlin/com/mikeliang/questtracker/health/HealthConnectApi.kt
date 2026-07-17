package com.mikeliang.questtracker.health

import com.mikeliang.questtracker.core.health.HealthMetric
import java.time.Instant

/** Whether a usable Health Connect provider exists on this device. */
enum class HealthConnectAvailability { Installed, NotInstalled, UpdateRequired }

/**
 * The thinnest possible seam over [androidx.health.connect.client.HealthConnectClient],
 * so everything above it — day-bounds math, failure collapsing, permission status,
 * reconciliation — is plain JVM-testable logic. Only [RealHealthConnectApi] touches
 * the actual client; it stays a dumb translator with nothing worth unit testing.
 */
interface HealthConnectApi {

    /** The permission strings this app needs, for status checks and the UI's request launcher. */
    val readPermissions: Set<String>

    fun availability(): HealthConnectAvailability

    /** Permission strings currently granted. May throw — callers collapse failures. */
    suspend fun grantedPermissions(): Set<String>

    /**
     * The total of [metric] over [[start], [end]), or null when the provider has no
     * data for the range — null is "absent", never zero. May throw — callers collapse
     * failures to [com.mikeliang.questtracker.core.health.HealthReading.Unavailable].
     */
    suspend fun aggregate(metric: HealthMetric, start: Instant, end: Instant): Double?
}
