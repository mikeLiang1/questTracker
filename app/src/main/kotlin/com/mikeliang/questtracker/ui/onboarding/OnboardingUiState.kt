package com.mikeliang.questtracker.ui.onboarding

import com.mikeliang.questtracker.core.onboarding.StartingClass

/** The two onboarding steps. Health Connect only appears when there is something to ask. */
enum class OnboardingStep { ChooseClass, HealthConnect }

/**
 * Single immutable state for the onboarding flow.
 *
 * @property healthPermissions permission strings for the Health Connect request
 * launcher; populated when entering [OnboardingStep.HealthConnect].
 * @property applying true while the chosen loadout is being written — disables
 * buttons against double-taps.
 * @property finished one-shot: the screen calls its `onFinished` callback when this
 * flips true.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ChooseClass,
    val healthPermissions: Set<String> = emptySet(),
    val applying: Boolean = false,
    val finished: Boolean = false,
)

/** Everything the user can do during onboarding. Every step has a skip. */
sealed interface OnboardingEvent {

    /** Tap on a starting-class card: seed its loadout, then maybe ask about health. */
    data class ClassChosen(val startingClass: StartingClass) : OnboardingEvent

    /** "Later" on step 1: straight to the (empty) quest list — quick-add is the recovery. */
    data object SkipPresets : OnboardingEvent

    /** The Health Connect system dialog returned. Grant and deny both proceed. */
    data object HealthPermissionResult : OnboardingEvent

    /** "Later" on step 2: manual mode, which always works. */
    data object SkipHealth : OnboardingEvent
}
