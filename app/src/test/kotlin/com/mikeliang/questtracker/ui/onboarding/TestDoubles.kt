package com.mikeliang.questtracker.ui.onboarding

import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.onboarding.StartingClass
import com.mikeliang.questtracker.health.HealthConnectApi
import com.mikeliang.questtracker.health.HealthConnectAvailability
import com.mikeliang.questtracker.onboarding.OnboardingStateStore
import com.mikeliang.questtracker.onboarding.OnboardingTiming
import java.time.Instant

/**
 * Scripted seam under the real [com.mikeliang.questtracker.health.HealthConnectPermissions]
 * (final class, but it only needs this interface).
 */
class FakeHealthConnectApi(
    var availability: HealthConnectAvailability = HealthConnectAvailability.Installed,
    var granted: Set<String> = emptySet(),
) : HealthConnectApi {

    override val readPermissions: Set<String> = setOf("perm.STEPS", "perm.SLEEP")

    override fun availability(): HealthConnectAvailability = availability

    override suspend fun grantedPermissions(): Set<String> = granted

    override suspend fun aggregate(metric: HealthMetric, start: Instant, end: Instant): Double? = null
}

/** Records completion marks; [events] lets tests assert call ordering. */
class FakeOnboardingStateStore(
    private val events: MutableList<String> = mutableListOf(),
) : OnboardingStateStore {

    val markedWith = mutableListOf<StartingClass?>()

    override suspend fun isOnboardingComplete(): Boolean = markedWith.isNotEmpty()

    override suspend fun markOnboardingComplete(chosenClass: StartingClass?) {
        markedWith += chosenClass
        events += "flag-persisted"
    }
}

/** Records timing calls into the shared [events] list for ordering assertions. */
class RecordingOnboardingTiming(
    private val events: MutableList<String> = mutableListOf(),
) : OnboardingTiming {

    var shownCount = 0
        private set
    var reachedCount = 0
        private set

    override fun onboardingShown() {
        shownCount++
    }

    override fun questListReached() {
        reachedCount++
        events += "quest-list-reached"
    }
}
