package com.mikeliang.questtracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.onboarding.StartingClass
import com.mikeliang.questtracker.core.onboarding.questLoadout
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.health.HealthConnectPermissions
import com.mikeliang.questtracker.health.HealthPermissionStatus
import com.mikeliang.questtracker.onboarding.OnboardingStateStore
import com.mikeliang.questtracker.onboarding.OnboardingTiming
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the two-step onboarding flow. Unlike the list screens this state is
 * event-driven, not repository-derived, so it is a plain [MutableStateFlow] rather
 * than the usual `combine` + `stateIn` — there is nothing to derive from the repo.
 *
 * The Health Connect step only appears when [HealthConnectPermissions.status] is
 * [HealthPermissionStatus.Denied] (installed but not yet granted): never ask for what
 * we already have or cannot use. Either way — grant, deny, skip, unavailable — the
 * flow always lands on the quest list.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: QuestRepository,
    private val clock: Clock,
    private val healthPermissions: HealthConnectPermissions,
    private val stateStore: OnboardingStateStore,
    private val timing: OnboardingTiming,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var chosen: StartingClass? = null

    init {
        // ViewModel creation coincides with the first onboarding composition.
        timing.onboardingShown()
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.ClassChosen -> applyClass(event.startingClass)
            OnboardingEvent.SkipPresets -> viewModelScope.launch { finish() }
            OnboardingEvent.HealthPermissionResult -> viewModelScope.launch { finish() }
            OnboardingEvent.SkipHealth -> viewModelScope.launch { finish() }
        }
    }

    private fun applyClass(startingClass: StartingClass) {
        if (_uiState.value.applying) return // double-tap guard
        _uiState.update { it.copy(applying = true) }
        viewModelScope.launch {
            startingClass.questLoadout(clock.now()).forEach { repository.upsertQuest(it) }
            chosen = startingClass
            when (healthPermissions.status()) {
                // Installed but not granted: the one case where asking has a point.
                HealthPermissionStatus.Denied -> _uiState.update {
                    it.copy(
                        step = OnboardingStep.HealthConnect,
                        healthPermissions = healthPermissions.requiredPermissions,
                        applying = false,
                    )
                }
                // Already granted or no provider: nothing to ask, land directly.
                HealthPermissionStatus.Granted,
                HealthPermissionStatus.Unavailable,
                -> finish()
            }
        }
    }

    /**
     * Persists the flag *before* signalling completion so a process death after
     * landing never re-runs onboarding and duplicates the loadout.
     */
    private suspend fun finish() {
        stateStore.markOnboardingComplete(chosen)
        timing.questListReached()
        _uiState.update { it.copy(finished = true) }
    }
}
