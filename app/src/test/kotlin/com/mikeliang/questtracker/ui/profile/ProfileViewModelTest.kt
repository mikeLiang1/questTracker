package com.mikeliang.questtracker.ui.profile

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.ui.questlist.FakeQuestRepository
import com.mikeliang.questtracker.ui.questlist.FixedClock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val clock = FixedClock() // 2026-07-17, UTC
    private val repository = FakeQuestRepository()

    private fun viewModel() = ProfileViewModel(
        repository = repository,
        engine = QuestEngine(clock),
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
        attribute: Attribute = Attribute.Body,
        status: QuestStatus = QuestStatus.Active,
    ) = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.Recurring(Cadence.Daily, QuestType.Maintenance, attribute),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        status = status,
    )

    private fun completionOn(id: String, date: LocalDate) = CompletionRecord(
        questId = QuestId(id),
        completedAt = Instant.parse("2026-07-01T12:00:00Z"),
        periodStart = date,
        source = CompletionSource.Manual,
    )

    private suspend fun ReceiveTurbine<ProfileUiState>.awaitUntil(
        predicate: (ProfileUiState) -> Boolean,
    ): ProfileUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    @Test
    fun `starts loading then shows all four attribute cards even with no data`() = runTest {
        val vm = viewModel()

        assertTrue(vm.uiState.value.loading)

        vm.uiState.test {
            val loaded = awaitUntil { !it.loading }
            assertEquals(Attribute.entries.toList(), loaded.attributes.map { it.attribute })
            assertTrue(loaded.attributes.all { it.rank == 0 && it.title == "Unwritten" })
            assertEquals(0, loaded.lifetimeCompletions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `profile reflects engine output for banked completions`() = runTest {
        repository.seed(recurringQuest())
        repository.recordCompletion(completionOn("quest-1", LocalDate.parse("2026-07-15")))
        repository.recordCompletion(completionOn("quest-1", LocalDate.parse("2026-07-16")))
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitUntil { !it.loading && it.lifetimeCompletions > 0 }
            val body = loaded.attributes.first { it.attribute == Attribute.Body }
            assertEquals(2.0, body.points)
            assertEquals(2, body.completions)
            assertEquals(2, loaded.lifetimeCompletions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new completions update the profile live`() = runTest {
        repository.seed(recurringQuest())
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }

            repository.recordCompletion(completionOn("quest-1", LocalDate.parse("2026-07-17")))

            val updated = awaitUntil { it.lifetimeCompletions == 1 }
            assertEquals(1.0, updated.attributes.first { it.attribute == Attribute.Body }.points)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retired quests appear as completed chapters with their banked count`() = runTest {
        repository.seed(recurringQuest(id = "old", title = "Couch to 5k", status = QuestStatus.Retired))
        repository.recordCompletion(completionOn("old", LocalDate.parse("2026-07-10")))
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitUntil { !it.loading && it.chapters.isNotEmpty() }
            val chapter = loaded.chapters.single()
            assertEquals("Couch to 5k", chapter.quest.title)
            assertEquals(1, chapter.completions)
            // Its point is still on the attribute card — retirement never removes gains.
            assertEquals(1.0, loaded.attributes.first { it.attribute == Attribute.Body }.points)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
