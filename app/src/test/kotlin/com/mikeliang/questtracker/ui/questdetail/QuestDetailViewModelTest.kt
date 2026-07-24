package com.mikeliang.questtracker.ui.questdetail

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.mikeliang.questtracker.core.engine.QuestEdit
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.engine.TargetEdit
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
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.ui.questlist.FakeQuestRepository
import com.mikeliang.questtracker.ui.questlist.FixedClock
import com.mikeliang.questtracker.ui.questlog.FakeJournalRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
class QuestDetailViewModelTest {

    private val clock = FixedClock() // 2026-07-17 (a Friday), UTC
    private val repository = FakeQuestRepository()
    private val journalRepository = FakeJournalRepository()

    private fun viewModel(id: String = "quest-1", journalDay: LocalDate? = null) = QuestDetailViewModel(
        questIdValue = id,
        journalDayEpochDay = journalDay?.toEpochDay(),
        repository = repository,
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
        cadence: Cadence = Cadence.Daily,
        type: QuestType = QuestType.Maintenance,
        attribute: Attribute = Attribute.Body,
        progression: ProgressionTarget? = null,
        status: QuestStatus = QuestStatus.Active,
    ) = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.Recurring(cadence, type, attribute, progression),
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
        status = status,
    )

    private fun sideQuest(id: String = "quest-1", title: String = "Call plumber") = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.SideQuest,
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
    )

    private fun completion(quest: Quest, date: LocalDate) = CompletionRecord(
        questId = quest.id,
        completedAt = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
        periodStart = date,
        source = CompletionSource.Manual,
    )

    private suspend fun ReceiveTurbine<QuestDetailUiState>.awaitUntil(
        predicate: (QuestDetailUiState) -> Boolean,
    ): QuestDetailUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    @Test
    fun `renders the quest with its evidence`() = runTest {
        val quest = recurringQuest()
        repository.seed(quest)
        repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-15")))
        repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-16")))

        viewModel().uiState.test {
            val state = awaitUntil { it.quest != null }

            assertEquals(quest, state.quest)
            assertEquals(2, state.lifetimeCompletions)
            assertEquals(2, state.consistency?.completedPeriods)
            assertFalse(state.canDelete)
        }
    }

    @Test
    fun `a quest with no completions can be deleted - one with any cannot`() = runTest {
        repository.seed(recurringQuest())

        viewModel().uiState.test {
            assertTrue(awaitUntil { it.quest != null }.canDelete)
        }

        val done = recurringQuest(id = "quest-2")
        repository.seed(done)
        repository.recordCompletion(completion(done, LocalDate.parse("2026-07-16")))

        viewModel("quest-2").uiState.test {
            assertFalse(awaitUntil { it.quest != null }.canDelete)
        }
    }

    @Test
    fun `side quests have no consistency`() = runTest {
        repository.seed(sideQuest())

        viewModel().uiState.test {
            val state = awaitUntil { it.quest != null }

            assertNull(state.consistency)
            assertTrue(state.canDelete)
        }
    }

    @Test
    fun `saving a recurring edit persists it and leaves completions untouched`() = runTest {
        val quest = recurringQuest()
        repository.seed(quest)
        repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-16")))
        val before = repository.recordedCompletions

        viewModel().onEvent(
            QuestDetailEvent.SaveRecurringEdit(
                QuestEdit.EditRecurring(
                    title = "Morning gym",
                    cadence = Cadence.Daily,
                    attribute = Attribute.Discipline,
                    reminder = null,
                )
            )
        )

        val stored = repository.storedQuests.single()
        assertEquals("Morning gym", stored.title)
        assertEquals(Attribute.Discipline, (stored.kind as QuestKind.Recurring).attribute)
        assertEquals(before, repository.recordedCompletions)
    }

    @Test
    fun `a recurring edit can toggle the journal link`() = runTest {
        val quest = recurringQuest()
        repository.seed(quest)

        viewModel().onEvent(
            QuestDetailEvent.SaveRecurringEdit(
                QuestEdit.EditRecurring(
                    title = quest.title,
                    cadence = Cadence.Daily,
                    attribute = Attribute.Body,
                    reminder = null,
                    journalLinked = true,
                )
            )
        )

        assertTrue((repository.storedQuests.single().kind as QuestKind.Recurring).journalLinked)
    }

    @Test
    fun `journal entries scoped to this quest surface newest first`() = runTest {
        repository.seed(recurringQuest())
        journalRepository.upsertEntry(entry(id = "older", text = "First line", at = "2026-07-15T21:00:00Z"))
        journalRepository.upsertEntry(entry(id = "newer", text = "Second line", at = "2026-07-16T21:00:00Z"))
        journalRepository.upsertEntry(
            entry(id = "other", text = "Different quest", at = "2026-07-16T22:00:00Z", questId = "someone-else")
        )

        viewModel().uiState.test {
            val state = awaitUntil { it.journalEntries.isNotEmpty() }
            assertEquals(listOf("newer", "older"), state.journalEntries.map { it.id.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening from a Quest Log day shows only that day's entries`() = runTest {
        repository.seed(recurringQuest())
        journalRepository.upsertEntry(entry(id = "older", text = "First line", at = "2026-07-15T21:00:00Z"))
        journalRepository.upsertEntry(entry(id = "newer", text = "Second line", at = "2026-07-16T21:00:00Z"))

        viewModel(journalDay = LocalDate.parse("2026-07-16")).uiState.test {
            val state = awaitUntil { it.journalEntries.isNotEmpty() }
            assertEquals(listOf("newer"), state.journalEntries.map { it.id.value })
            assertEquals(LocalDate.parse("2026-07-16"), state.journalDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day cleared before anything was written shows no journal`() = runTest {
        val quest = recurringQuest()
        repository.seed(quest)
        repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-15")))
        journalRepository.upsertEntry(entry(id = "newer", text = "Written a day later"))

        viewModel(journalDay = LocalDate.parse("2026-07-15")).uiState.test {
            val state = awaitUntil { it.quest != null }
            assertTrue(state.journalEntries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a journal entry from the quest leaves its completion banked`() = runTest {
        val quest = recurringQuest()
        repository.seed(quest)
        repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-16")))
        journalRepository.upsertEntry(entry(id = "e1", text = "Written, then regretted."))

        viewModel().onEvent(QuestDetailEvent.DeleteJournalEntry(JournalEntryId("e1")))

        assertTrue(journalRepository.storedEntries.isEmpty())
        assertEquals(1, repository.recordedCompletions.size)
    }

    @Test
    fun `editing a journal entry rewrites text and stamps editedAt`() = runTest {
        repository.seed(recurringQuest())
        journalRepository.upsertEntry(entry(id = "e1", text = "First draft"))

        viewModel().onEvent(QuestDetailEvent.EditJournalEntry(JournalEntryId("e1"), " Second draft "))

        val edited = journalRepository.storedEntries.single()
        assertEquals("Second draft", edited.text)
        assertEquals(clock.now(), edited.editedAt)
    }

    private fun entry(
        id: String,
        text: String,
        at: String = "2026-07-16T21:00:00Z",
        questId: String = "quest-1",
    ) = JournalEntry(
        id = JournalEntryId(id),
        text = text,
        createdAt = Instant.parse(at),
        entryDate = LocalDate.parse(at.substring(0, 10)),
        questIds = setOf(QuestId(questId)),
    )

    @Test
    fun `a cadence edit stamps cadenceChangedOn with the clock's today`() = runTest {
        val quest = recurringQuest(cadence = Cadence.Daily)
        repository.seed(quest)

        viewModel().onEvent(
            QuestDetailEvent.SaveRecurringEdit(
                QuestEdit.EditRecurring(
                    title = quest.title,
                    cadence = Cadence.Weekly,
                    attribute = Attribute.Body,
                    reminder = null,
                )
            )
        )

        assertEquals(LocalDate.parse("2026-07-17"), repository.storedQuests.single().cadenceChangedOn)
    }

    @Test
    fun `adding a target makes the quest progression`() = runTest {
        repository.seed(recurringQuest())

        viewModel().onEvent(
            QuestDetailEvent.SaveRecurringEdit(
                QuestEdit.EditRecurring(
                    title = "Gym hour",
                    cadence = Cadence.Daily,
                    attribute = Attribute.Body,
                    reminder = null,
                    target = TargetEdit.Add(8000.0, "steps"),
                )
            )
        )

        val kind = repository.storedQuests.single().kind as QuestKind.Recurring
        assertEquals(QuestType.Progression, kind.type)
        assertEquals(ProgressionTarget(8000.0, "steps", 0), kind.progression)
    }

    @Test
    fun `a side-quest edit maps a new time to its next occurrence`() = runTest {
        repository.seed(sideQuest())

        // FixedClock is 10:00 UTC — 09:00 has passed, so the one-shot lands tomorrow.
        viewModel().onEvent(QuestDetailEvent.SaveSideQuestEdit("Call electrician", LocalTime.of(9, 0)))

        val stored = repository.storedQuests.single()
        assertEquals("Call electrician", stored.title)
        assertEquals(
            ReminderSchedule.OneShot(java.time.LocalDateTime.parse("2026-07-18T09:00:00")),
            stored.reminder,
        )
    }

    @Test
    fun `a side-quest edit with an unchanged time keeps the pending one-shot`() = runTest {
        val pending = ReminderSchedule.OneShot(java.time.LocalDateTime.parse("2026-07-17T15:00:00"))
        repository.seed(sideQuest().copy(reminder = pending))

        viewModel().onEvent(QuestDetailEvent.SaveSideQuestEdit("Call plumber", LocalTime.of(15, 0)))

        assertEquals(pending, repository.storedQuests.single().reminder)
    }

    @Test
    fun `escalating bumps the level and keeps completions`() = runTest {
        val quest = recurringQuest(
            type = QuestType.Progression,
            progression = ProgressionTarget(8000.0, "steps", 1),
        )
        repository.seed(quest)
        repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-16")))

        viewModel().onEvent(QuestDetailEvent.Escalate(10000.0))

        val kind = repository.storedQuests.single().kind as QuestKind.Recurring
        assertEquals(ProgressionTarget(10000.0, "steps", 2), kind.progression)
        assertEquals(1, repository.recordedCompletions.size)
    }

    @Test
    fun `retiring archives the quest, keeps completions, and closes the screen`() = runTest {
        val quest = recurringQuest()
        repository.seed(quest)
        repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-16")))

        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitUntil { it.quest != null }

            viewModel.onEvent(QuestDetailEvent.Retire)

            assertTrue(awaitUntil { it.closed }.closed)
        }
        assertEquals(QuestStatus.Retired, repository.storedQuests.single().status)
        assertEquals(1, repository.recordedCompletions.size)
    }

    @Test
    fun `deleting removes the quest and closes the screen`() = runTest {
        repository.seed(recurringQuest())

        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitUntil { it.canDelete }

            viewModel.onEvent(QuestDetailEvent.Delete)

            assertTrue(awaitUntil { it.closed }.closed)
        }
        assertTrue(repository.storedQuests.isEmpty())
    }

    @Test
    fun `delete is refused when a completion raced in`() = runTest {
        val quest = recurringQuest()
        repository.seed(quest)

        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitUntil { it.canDelete } // the button was rendered…

            // …then a completion lands (e.g. from the notification action)…
            repository.recordCompletion(completion(quest, LocalDate.parse("2026-07-17")))
            viewModel.onEvent(QuestDetailEvent.Delete)

            // …so the delete re-check refuses: history always wins.
            assertFalse(awaitUntil { !it.canDelete }.closed)
        }
        assertEquals(quest, repository.storedQuests.single())
    }
}
