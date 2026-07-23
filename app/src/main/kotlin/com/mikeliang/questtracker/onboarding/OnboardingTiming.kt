package com.mikeliang.questtracker.onboarding

import android.os.SystemClock
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only metric behind the build plan's "<60s to a working quest list" claim:
 * time from onboarding first shown to landing on the quest list. Interface seam
 * because :app unit tests cannot call [android.util.Log].
 */
interface OnboardingTiming {

    /** Called when onboarding first appears. Only the first call counts. */
    fun onboardingShown()

    /** Called when onboarding hands off to the quest list. Logs elapsed time once. */
    fun questListReached()
}

/**
 * Logs `time_to_first_quest_list_ms` to logcat. Uses [SystemClock.elapsedRealtime]
 * (monotonic) rather than the domain `Clock` — this is a duration diagnostic, and
 * wall-clock jumps would corrupt it. Singleton so the start mark survives the
 * onboarding → home recomposition.
 */
@Singleton
class LogcatOnboardingTiming @Inject constructor() : OnboardingTiming {

    private var shownAt: Long? = null
    private var logged = false

    override fun onboardingShown() {
        if (shownAt == null) shownAt = SystemClock.elapsedRealtime()
    }

    override fun questListReached() {
        val start = shownAt ?: return
        if (logged) return
        logged = true
        Log.i(TAG, "time_to_first_quest_list_ms=${SystemClock.elapsedRealtime() - start}")
    }

    private companion object {
        const val TAG = "Onboarding"
    }
}
