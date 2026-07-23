package com.mikeliang.questtracker.ui.reflection

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.ProgressionTarget
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.ui.questlist.FakeQuestRepository
import com.mikeliang.questtracker.ui.questlist.FakeReflectionStateStore
import com.mikeliang.questtracker.ui.questlist.FixedClock
import com.mikeliang.questtracker.ui.questlist.QuestListEvent
import java.time.Instant
import java.time.LocalDate
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReflectionViewModelTest {

    private val clock = FixedClock() // 2026-07-17, UTC → reviewed month is June
    private val repository = FakeQuestRepository()
    private val stateStore = FakeReflectionStateStore()

    private fun viewModel() = ReflectionViewModel(
        repository = repository,
        engine = QuestEngine(clock),
        stateStore = stateStore,
        clock = clock,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun maintenanceQuest(id: String = "quest-1", title: String = "Gym hour") = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.Recurring(Cadence.Daily, QuestType.Maintenance, Attribute.Body),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun progressionQuest(id: String = "steps", amount: Double = 8000.0) = Quest(
        id = QuestId(id),
        title = "Daily steps",
        kind = QuestKind.Recurring(
            Cadence.Daily, QuestType.Progression, Attribute.Body,
            progression = ProgressionTarget(amount, "steps", escalationLevel = 1),
        ),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private suspend fun bankJuneCompletion(id: String, day: Int = 10) {
        repository.recordCompletion(
            CompletionRecord(
                questId = QuestId(id),
                completedAt = Instant.parse("2026-06-%02dT09:00:00Z".format(day)),
                periodStart = LocalDate.parse("2026-06-%02d".format(day)),
                source = CompletionSource.Manual,
            )
        )
    }

    private suspend fun ReceiveTurbine<ReflectionUiState>.awaitUntil(
        predicate: (ReflectionUiState) -> Boolean,
    ): ReflectionUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    @Test
    fun `snapshots last month's trajectory for active recurring quests`() = runTest {
        repository.seed(maintenanceQuest())
        bankJuneCompletion("quest-1")
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitUntil { !it.loading }
            assertEquals(YearMonth.of(2026, 6), state.month)
            val row = state.rows.single()
            assertEquals("Gym hour", row.quest.title)
            assertEquals(1, row.completionsInMonth)
            assertEquals(ReflectionChoice.Keep, row.choice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing with a retire choice retires the quest and marks the month handled`() = runTest {
        repository.seed(maintenanceQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(ReflectionEvent.Choose(QuestId("quest-1"), ReflectionChoice.Retire))
            vm.onEvent(ReflectionEvent.Complete)

            awaitUntil { it.closed }
            assertEquals(QuestStatus.Retired, repository.storedQuests.single().status)
            assertEquals(YearMonth.of(2026, 7), stateStore.handledMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing with an escalate choice raises the target through core's rule`() = runTest {
        repository.seed(progressionQuest(amount = 8000.0))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(ReflectionEvent.Choose(QuestId("steps"), ReflectionChoice.Escalate(10_000.0)))
            vm.onEvent(ReflectionEvent.Complete)

            awaitUntil { it.closed }
            val kind = repository.storedQuests.single().kind as QuestKind.Recurring
            assertEquals(10_000.0, kind.progression?.amount)
            // escalate() bumps the level — the diminishing-returns reset.
            assertEquals(2, kind.progression?.escalationLevel)
            assertEquals(YearMonth.of(2026, 7), stateStore.handledMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a quest through the reflection stores it immediately`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(
                ReflectionEvent.AddQuest(
                    QuestListEvent.AddRecurringQuest("Morning pages", Cadence.Daily, Attribute.Mind, null)
                )
            )

            val state = awaitUntil { it.addedQuestTitles.isNotEmpty() }
            assertEquals(listOf("Morning pages"), state.addedQuestTitles)
            val kind = repository.storedQuests.single().kind as QuestKind.Recurring
            assertEquals(Attribute.Mind, kind.attribute)
            assertFalse(state.closed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `skipping changes no quest state but marks the month handled`() = runTest {
        val quest = maintenanceQuest()
        repository.seed(quest)
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            // Even a pending retire choice is discarded on skip — closing without
            // Done must never edit the system.
            vm.onEvent(ReflectionEvent.Choose(QuestId("quest-1"), ReflectionChoice.Retire))
            vm.onEvent(ReflectionEvent.Skip)

            awaitUntil { it.closed }
            assertEquals(listOf(quest), repository.storedQuests)
            assertEquals(YearMonth.of(2026, 7), stateStore.handledMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keep choices leave quests untouched on complete`() = runTest {
        val quest = maintenanceQuest()
        repository.seed(quest)
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            vm.onEvent(ReflectionEvent.Complete)

            awaitUntil { it.closed }
            assertEquals(listOf(quest), repository.storedQuests)
            assertTrue(stateStore.handledMonth != null)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
