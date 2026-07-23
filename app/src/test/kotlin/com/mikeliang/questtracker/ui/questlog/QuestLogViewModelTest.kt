package com.mikeliang.questtracker.ui.questlog

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.engine.QuestLogItem
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestLogViewModelTest {

    private val clock = FixedClock() // 2026-07-17 (a Friday), UTC
    private val questRepository = FakeQuestRepository()
    private val journalRepository = FakeJournalRepository()

    private fun viewModel() = QuestLogViewModel(
        questRepository = questRepository,
        journalRepository = journalRepository,
        engine = QuestEngine(clock),
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

    private fun recurringQuest(
        id: String = "quest-1",
        title: String = "Gym hour",
        journalLinked: Boolean = false,
    ) = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.Recurring(
            cadence = com.mikeliang.questtracker.core.model.Cadence.Daily,
            type = QuestType.Maintenance,
            attribute = Attribute.Mind,
            journalLinked = journalLinked,
        ),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private suspend fun ReceiveTurbine<QuestLogUiState>.awaitUntil(
        predicate: (QuestLogUiState) -> Boolean,
    ): QuestLogUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    @Test
    fun `saving an entry persists it stamped with the clock's now and today`() = runTest {
        val vm = viewModel()

        vm.onEvent(QuestLogEvent.SaveEntry("  Grateful for the rain.  "))

        val entry = journalRepository.storedEntries.single()
        assertEquals("Grateful for the rain.", entry.text)
        assertEquals(clock.now(), entry.createdAt)
        assertEquals(LocalDate.parse("2026-07-17"), entry.entryDate)
        assertNull(entry.editedAt)
    }

    @Test
    fun `saving an entry completes the journal-linked quest with Manual source`() = runTest {
        questRepository.seed(
            recurringQuest(id = "journal", title = "One line of journal", journalLinked = true),
            recurringQuest(id = "gym", title = "Gym hour"),
        )
        val vm = viewModel()

        vm.onEvent(QuestLogEvent.SaveEntry("One good line."))

        val record = questRepository.recordedCompletions.single()
        assertEquals("journal", record.questId.value)
        assertEquals(CompletionSource.Manual, record.source)
        assertEquals(Attribute.Mind, record.attribute)
    }

    @Test
    fun `a save that banks a completion surfaces the engine's feedback`() = runTest {
        questRepository.seed(recurringQuest(id = "journal", journalLinked = true))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }
            vm.onEvent(QuestLogEvent.SaveEntry("One good line."))
            val state = awaitUntil { it.feedback != null }
            assertTrue(state.feedback is QuestLogFeedback.QuestCompleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a save with no linked quests still saves - with plain saved copy`() = runTest {
        questRepository.seed(recurringQuest(id = "gym"))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }
            vm.onEvent(QuestLogEvent.SaveEntry("Just a note."))
            val state = awaitUntil { it.feedback != null }
            assertEquals(QuestLogFeedback.EntrySaved, state.feedback)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, journalRepository.storedEntries.size)
        assertTrue(questRepository.recordedCompletions.isEmpty())
    }

    @Test
    fun `a second entry the same day banks no second completion`() = runTest {
        questRepository.seed(recurringQuest(id = "journal", journalLinked = true))
        val vm = viewModel()

        vm.onEvent(QuestLogEvent.SaveEntry("First."))
        vm.onEvent(QuestLogEvent.SaveEntry("Second."))

        assertEquals(2, journalRepository.storedEntries.size)
        assertEquals(1, questRepository.recordedCompletions.size)
    }

    @Test
    fun `blank text is a no-op`() = runTest {
        val vm = viewModel()

        vm.onEvent(QuestLogEvent.SaveEntry("   "))

        assertTrue(journalRepository.storedEntries.isEmpty())
    }

    @Test
    fun `deleting an entry leaves its completion banked - the law`() = runTest {
        questRepository.seed(recurringQuest(id = "journal", journalLinked = true))
        val vm = viewModel()

        vm.onEvent(QuestLogEvent.SaveEntry("Written, then regretted."))
        val entryId = journalRepository.storedEntries.single().id

        vm.onEvent(QuestLogEvent.DeleteEntry(entryId))

        assertTrue(journalRepository.storedEntries.isEmpty())
        assertEquals(1, questRepository.recordedCompletions.size)
    }

    @Test
    fun `editing an entry rewrites text, stamps editedAt, and never re-banks`() = runTest {
        questRepository.seed(recurringQuest(id = "journal", journalLinked = true))
        val vm = viewModel()

        vm.onEvent(QuestLogEvent.SaveEntry("First draft."))
        val entryId = journalRepository.storedEntries.single().id

        vm.onEvent(QuestLogEvent.EditEntry(entryId, "Second draft."))

        val entry = journalRepository.storedEntries.single()
        assertEquals("Second draft.", entry.text)
        assertNotNull(entry.editedAt)
        assertEquals(1, questRepository.recordedCompletions.size)
    }

    @Test
    fun `editing an unknown entry is a no-op`() = runTest {
        val vm = viewModel()

        vm.onEvent(QuestLogEvent.EditEntry(JournalEntryId("ghost"), "Boo."))

        assertTrue(journalRepository.storedEntries.isEmpty())
    }

    @Test
    fun `the timeline interleaves entries and completions with today set`() = runTest {
        questRepository.seed(recurringQuest(id = "journal", journalLinked = true))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.loading }
            vm.onEvent(QuestLogEvent.SaveEntry("One good line."))
            val state = awaitUntil { it.days.isNotEmpty() && it.days.single().items.size == 2 }

            assertEquals(LocalDate.parse("2026-07-17"), state.today)
            val day = state.days.single()
            assertEquals(LocalDate.parse("2026-07-17"), day.date)
            assertTrue(day.items.any { it is QuestLogItem.Entry })
            assertTrue(day.items.any { it is QuestLogItem.Completion })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
