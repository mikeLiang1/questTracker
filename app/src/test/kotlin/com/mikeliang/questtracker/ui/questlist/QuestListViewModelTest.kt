package com.mikeliang.questtracker.ui.questlist

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.mikeliang.questtracker.core.engine.CompletionFeedback
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.health.HealthReading
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.AutoTracking
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestListViewModelTest {

    private val clock = FixedClock() // 2026-07-17 (a Friday), UTC
    private val repository = FakeQuestRepository()
    private val healthSource = ScriptedHealthSource()
    private val reflectionStore = FakeReflectionStateStore()

    private fun viewModel() = QuestListViewModel(
        repository = repository,
        engine = QuestEngine(clock),
        healthSource = healthSource,
        clock = clock,
        reflectionStateStore = reflectionStore,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun recurringQuest(
        id: String = "quest-1",
        title: String = "Gym hour",
        cadence: Cadence = Cadence.Daily,
        autoTracking: AutoTracking? = null,
    ) = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.Recurring(cadence, QuestType.Maintenance, Attribute.Body),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        autoTracking = autoTracking,
    )

    private fun sideQuest(id: String = "side-1", title: String = "Call plumber") = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.SideQuest,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private suspend fun ReceiveTurbine<QuestListUiState>.awaitUntil(
        predicate: (QuestListUiState) -> Boolean,
    ): QuestListUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    @Test
    fun `starts loading then shows the board`() = runTest {
        repository.seed(recurringQuest(), sideQuest())
        val vm = viewModel()

        // Before anyone collects, the state is the loading placeholder.
        assertTrue(vm.uiState.value.loading)

        vm.uiState.test {
            val loaded = awaitUntil { !it.loading }
            assertEquals(listOf("Gym hour"), loaded.recurring.map { it.quest.title })
            assertEquals(listOf("Call plumber"), loaded.sideQuests.map { it.quest.title })
            assertFalse(loaded.doneForToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty board reports isEmpty`() = runTest {
        viewModel().uiState.test {
            val loaded = awaitUntil { !it.loading }
            assertTrue(loaded.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CompleteQuest records the completion and surfaces engine feedback`() = runTest {
        repository.seed(recurringQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading && it.recurring.isNotEmpty() }

            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("quest-1")))

            val completed = awaitUntil { it.recurring.firstOrNull()?.completed == true && it.feedback != null }
            assertEquals(1, repository.recordedCompletions.size)
            assertTrue(completed.feedback is CompletionFeedback.EvidenceBanked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing an already-completed quest is a no-op`() = runTest {
        repository.seed(recurringQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading && it.recurring.isNotEmpty() }

            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("quest-1")))
            awaitUntil { it.recurring.firstOrNull()?.completed == true }
            vm.onEvent(QuestListEvent.FeedbackShown)
            awaitUntil { it.feedback == null }

            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("quest-1")))
            expectNoEvents()
            assertEquals(1, repository.recordedCompletions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `UnclearQuest undoes a same-day tick and re-opens the quest`() = runTest {
        repository.seed(recurringQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading && it.recurring.isNotEmpty() }

            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("quest-1")))
            val completed = awaitUntil { it.recurring.firstOrNull()?.completed == true }
            assertTrue(completed.recurring.single().undoable)

            vm.onEvent(QuestListEvent.UnclearQuest(QuestId("quest-1")))

            awaitUntil { it.recurring.firstOrNull()?.completed == false }
            assertTrue(repository.recordedCompletions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `UnclearQuest is a no-op for a completion banked before today`() = runTest {
        // Weekly cleared on Monday the 13th; clock is Friday the 17th — banked forever.
        val weekly = recurringQuest(id = "w", title = "Long run", cadence = Cadence.Weekly)
        repository.seed(weekly)
        repository.recordCompletion(
            CompletionRecord(
                questId = QuestId("w"),
                completedAt = Instant.parse("2026-07-13T09:00:00Z"),
                periodStart = LocalDate.parse("2026-07-13"),
                source = CompletionSource.Manual,
            )
        )
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitUntil { it.recurring.firstOrNull()?.completed == true }
            assertFalse(state.recurring.single().undoable)

            vm.onEvent(QuestListEvent.UnclearQuest(QuestId("w")))
            expectNoEvents()
            assertEquals(1, repository.recordedCompletions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `FeedbackShown clears feedback from state`() = runTest {
        repository.seed(recurringQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading && it.recurring.isNotEmpty() }
            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("quest-1")))
            awaitUntil { it.feedback != null }

            vm.onEvent(QuestListEvent.FeedbackShown)

            val cleared = awaitUntil { it.feedback == null }
            assertNull(cleared.feedback)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing every recurring quest flips doneForToday`() = runTest {
        repository.seed(recurringQuest(id = "q1", title = "A"), recurringQuest(id = "q2", title = "B"))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { it.recurring.size == 2 }

            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("q1")))
            val oneDone = awaitUntil { it.recurring.count { r -> r.completed } == 1 }
            assertFalse(oneDone.doneForToday)
            // Mid-day the board already splits: cleared quests leave the active list.
            assertEquals(listOf(QuestId("q2")), oneDone.activeRecurring.map { it.quest.id })
            assertEquals(listOf(QuestId("q1")), oneDone.clearedRecurring.map { it.quest.id })

            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("q2")))
            val done = awaitUntil { it.doneForToday }
            assertTrue(done.recurring.all { it.completed })
            assertTrue(done.activeRecurring.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing a side quest gives side-quest feedback, never attribute framing`() = runTest {
        repository.seed(sideQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { it.sideQuests.isNotEmpty() }

            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("side-1")))

            val state = awaitUntil { it.feedback != null }
            assertTrue(state.feedback is CompletionFeedback.SideQuestCleared)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AddSideQuest stores a side quest with a one-shot reminder at the next occurrence`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            // Clock is 10:00 UTC; 09:00 has passed today, so the reminder lands tomorrow.
            vm.onEvent(QuestListEvent.AddSideQuest("Buy stamps", LocalTime.of(9, 0)))

            val state = awaitUntil { it.sideQuests.isNotEmpty() }
            val quest = state.sideQuests.single().quest
            assertEquals(QuestKind.SideQuest, quest.kind)
            val reminder = quest.reminder as ReminderSchedule.OneShot
            assertEquals("2026-07-18T09:00", reminder.at.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank titles are ignored`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(QuestListEvent.AddSideQuest("   ", null))
            expectNoEvents()
            assertTrue(repository.storedQuests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AddRecurringQuest quick-adds a Maintenance quest with the chosen cadence and attribute`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(
                QuestListEvent.AddRecurringQuest("Read 20 pages", Cadence.Weekly, Attribute.Mind, LocalTime.of(19, 0))
            )

            val state = awaitUntil { it.recurring.isNotEmpty() }
            val kind = state.recurring.single().quest.kind as QuestKind.Recurring
            assertEquals(Cadence.Weekly, kind.cadence)
            assertEquals(QuestType.Maintenance, kind.type)
            assertEquals(Attribute.Mind, kind.attribute)

            // Weekly quest → weekly nudge on the day it was created (2026-07-17 is a Friday).
            val reminder = state.recurring.single().quest.reminder as ReminderSchedule.Recurring
            assertEquals(setOf(DayOfWeek.FRIDAY), reminder.days)
            assertEquals(LocalTime.of(19, 0), reminder.time)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AddRecurringQuest carries the journal link into the stored kind`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(
                QuestListEvent.AddRecurringQuest(
                    "One line of journal", Cadence.Daily, Attribute.Mind, null, journalLinked = true,
                )
            )

            val state = awaitUntil { it.recurring.isNotEmpty() }
            val kind = state.recurring.single().quest.kind as QuestKind.Recurring
            assertTrue(kind.journalLinked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the tick on a journal-linked quest still completes with no writing required`() = runTest {
        repository.seed(
            recurringQuest(id = "journal", title = "One line of journal").let {
                it.copy(kind = (it.kind as QuestKind.Recurring).copy(journalLinked = true))
            }
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }
            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("journal")))
            awaitUntil { it.recurring.singleOrNull()?.completed == true }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.recordedCompletions.size)
    }

    @Test
    fun `daily quick-add reminder nudges every day`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(
                QuestListEvent.AddRecurringQuest("Stretch", Cadence.Daily, Attribute.Body, LocalTime.of(7, 0))
            )

            val state = awaitUntil { it.recurring.isNotEmpty() }
            val reminder = state.recurring.single().quest.reminder as ReminderSchedule.Recurring
            assertEquals(DayOfWeek.entries.toSet(), reminder.days)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto-tracked quest exposes live progress from the health source`() = runTest {
        repository.seed(
            recurringQuest(id = "steps", title = "10k steps", autoTracking = AutoTracking(HealthMetric.Steps, 8000.0))
        )
        val vm = viewModel()

        vm.uiState.test {
            val unavailable = awaitUntil { it.recurring.isNotEmpty() }
            val progressBefore = unavailable.recurring.single().progress
            assertEquals(8000.0, progressBefore?.target)
            assertNull(progressBefore?.current)

            healthSource.today.value = HealthReading.Available(5200.0)

            val updated = awaitUntil { it.recurring.single().progress?.current != null }
            assertEquals(5200.0, updated.recurring.single().progress?.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `quests without auto-tracking expose no progress`() = runTest {
        repository.seed(recurringQuest())
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitUntil { it.recurring.isNotEmpty() }
            assertNull(state.recurring.single().progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- reflection banner --------------------------------------------------

    /** A completion credited to June — history from before the current (July) month. */
    private suspend fun bankJuneCompletion() {
        repository.recordCompletion(
            CompletionRecord(
                questId = QuestId("quest-1"),
                completedAt = Instant.parse("2026-06-10T09:00:00Z"),
                periodStart = LocalDate.parse("2026-06-10"),
                source = CompletionSource.Manual,
            )
        )
    }

    @Test
    fun `reflection surfaces once history predates the current month`() = runTest {
        repository.seed(recurringQuest())
        bankJuneCompletion()
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitUntil { !it.loading }
            assertTrue(state.reflectionDue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reflection stays quiet with only current-month history`() = runTest {
        repository.seed(recurringQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }
            vm.onEvent(QuestListEvent.CompleteQuest(QuestId("quest-1")))
            val state = awaitUntil { it.recurring.firstOrNull()?.completed == true }
            assertFalse(state.reflectionDue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `banner drops the moment the month is marked handled — skip and complete alike`() = runTest {
        repository.seed(recurringQuest())
        bankJuneCompletion()
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading && it.reflectionDue }

            reflectionStore.markHandled(YearMonth.of(2026, 7))

            awaitUntil { !it.reflectionDue }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a month handled last month re-arms the banner`() = runTest {
        repository.seed(recurringQuest())
        bankJuneCompletion()
        reflectionStore.seed(YearMonth.of(2026, 6))
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitUntil { !it.loading }
            assertTrue(state.reflectionDue)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
