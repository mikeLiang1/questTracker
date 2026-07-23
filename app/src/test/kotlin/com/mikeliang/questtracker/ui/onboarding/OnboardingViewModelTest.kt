package com.mikeliang.questtracker.ui.onboarding

import com.mikeliang.questtracker.core.onboarding.StartingClass
import com.mikeliang.questtracker.core.onboarding.questLoadout
import com.mikeliang.questtracker.health.HealthConnectAvailability
import com.mikeliang.questtracker.health.HealthConnectPermissions
import com.mikeliang.questtracker.ui.questlist.FakeQuestRepository
import com.mikeliang.questtracker.ui.questlist.FixedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val clock = FixedClock() // 2026-07-17, UTC
    private val repository = FakeQuestRepository()
    private val healthApi = FakeHealthConnectApi()

    /** Shared event log so flag-persist vs. quest-list-reached ordering is assertable. */
    private val events = mutableListOf<String>()
    private val stateStore = FakeOnboardingStateStore(events)
    private val timing = RecordingOnboardingTiming(events)

    private fun viewModel() = OnboardingViewModel(
        repository = repository,
        clock = clock,
        healthPermissions = HealthConnectPermissions(healthApi),
        stateStore = stateStore,
        timing = timing,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun healthDenied() {
        healthApi.availability = HealthConnectAvailability.Installed
        healthApi.granted = emptySet()
    }

    private fun healthGranted() {
        healthApi.availability = HealthConnectAvailability.Installed
        healthApi.granted = healthApi.readPermissions
    }

    private fun healthUnavailable() {
        healthApi.availability = HealthConnectAvailability.NotInstalled
    }

    @Test
    fun `choosing a class with health denied seeds the loadout and shows the health step`() = runTest {
        healthDenied()
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Warrior))

        val expectedTitles = StartingClass.Warrior.questLoadout(clock.now()).map { it.title }
        assertEquals(expectedTitles, repository.storedQuests.map { it.title })
        val state = vm.uiState.value
        assertEquals(OnboardingStep.HealthConnect, state.step)
        assertEquals(healthApi.readPermissions, state.healthPermissions)
        assertFalse(state.finished)
        assertTrue(stateStore.markedWith.isEmpty()) { "Flag must not be set before the flow finishes" }
    }

    @Test
    fun `choosing a class with health unavailable finishes without the health step`() = runTest {
        healthUnavailable()
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Sage))

        assertEquals(4, repository.storedQuests.size)
        assertTrue(vm.uiState.value.finished)
        assertEquals(OnboardingStep.ChooseClass, vm.uiState.value.step)
        assertEquals(listOf<StartingClass?>(StartingClass.Sage), stateStore.markedWith)
    }

    @Test
    fun `choosing a class with health already granted finishes without asking again`() = runTest {
        healthGranted()
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Adventurer))

        assertTrue(vm.uiState.value.finished)
        assertEquals(OnboardingStep.ChooseClass, vm.uiState.value.step)
        assertEquals(listOf<StartingClass?>(StartingClass.Adventurer), stateStore.markedWith)
    }

    @Test
    fun `skipping presets finishes with an empty board and a null class`() = runTest {
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.SkipPresets)

        assertTrue(repository.storedQuests.isEmpty())
        assertTrue(vm.uiState.value.finished)
        assertEquals(listOf<StartingClass?>(null), stateStore.markedWith)
    }

    @Test
    fun `skipping the health step finishes with the chosen class recorded`() = runTest {
        healthDenied()
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Warrior))

        vm.onEvent(OnboardingEvent.SkipHealth)

        assertTrue(vm.uiState.value.finished)
        assertEquals(listOf<StartingClass?>(StartingClass.Warrior), stateStore.markedWith)
    }

    @Test
    fun `the health permission result finishes regardless of grant or deny`() = runTest {
        healthDenied()
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Warrior))

        // The system dialog returned — the event carries no answer on purpose:
        // grant and deny both land on the quest list.
        vm.onEvent(OnboardingEvent.HealthPermissionResult)

        assertTrue(vm.uiState.value.finished)
        assertEquals(listOf<StartingClass?>(StartingClass.Warrior), stateStore.markedWith)
    }

    @Test
    fun `timing marks shown at construction and reached exactly once, after the flag persists`() = runTest {
        healthUnavailable()
        val vm = viewModel()
        assertEquals(1, timing.shownCount)
        assertEquals(0, timing.reachedCount)

        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Warrior))

        assertEquals(1, timing.reachedCount)
        // Flag-before-finished ordering: a process death after landing must never
        // re-run onboarding and duplicate the loadout.
        assertEquals(listOf("flag-persisted", "quest-list-reached"), events)
    }

    @Test
    fun `a second class tap while the first is still applying inserts nothing extra`() = runTest {
        // A standard dispatcher keeps the first choice in flight (unlike Unconfined,
        // which would complete it synchronously) so the double-tap window is real.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        healthDenied()
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Warrior))
        vm.onEvent(OnboardingEvent.ClassChosen(StartingClass.Sage))
        advanceUntilIdle()

        // Only the first loadout landed — the second tap hit the `applying` guard.
        assertEquals(4, repository.storedQuests.size)
        val expectedTitles = StartingClass.Warrior.questLoadout(clock.now()).map { it.title }
        assertEquals(expectedTitles, repository.storedQuests.map { it.title })
    }
}
