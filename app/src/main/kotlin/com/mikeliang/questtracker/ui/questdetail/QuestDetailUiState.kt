package com.mikeliang.questtracker.ui.questdetail

import com.mikeliang.questtracker.core.engine.ConsistencyScore
import com.mikeliang.questtracker.core.engine.QuestEdit
import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.core.model.Quest
import java.time.LocalDate
import java.time.LocalTime

/**
 * One quest's detail: identity, the evidence behind it (lifetime completions first —
 * the number that can't break), and what the user may do to it. [canDelete] is true
 * only for zero-completion mis-creations; everything with history retires instead.
 * [closed] asks the host to pop back (after retire/delete, or if the quest vanished).
 * [journalEntries] are the entries that counted toward this quest — they live here
 * rather than on the main Quest Log timeline, newest first.
 *
 * [journalDay] is the Quest Log day this was opened from, and [journalEntries] is
 * already narrowed to it: a day's completion shows that day's writing and nothing
 * else. Null means no day is in play (opened from the board or profile) and the list
 * is the quest's whole journal.
 */
data class QuestDetailUiState(
    val loading: Boolean = false,
    val quest: Quest? = null,
    val lifetimeCompletions: Int = 0,
    val consistency: ConsistencyScore? = null,
    val canDelete: Boolean = false,
    val closed: Boolean = false,
    val journalEntries: List<JournalEntry> = emptyList(),
    val journalDay: LocalDate? = null,
)

sealed interface QuestDetailEvent {

    /** A validated recurring edit from the edit sheet, applied through :core. */
    data class SaveRecurringEdit(val edit: QuestEdit.EditRecurring) : QuestDetailEvent

    /**
     * A side-quest edit. Carries the raw time — the ViewModel owns the clock and maps
     * it to the next occurrence (the same rule quick-add uses), keeping an unchanged
     * time from resetting a pending one-shot.
     */
    data class SaveSideQuestEdit(val title: String, val reminderTime: LocalTime?) : QuestDetailEvent

    /** User-chosen target change — the one path that bumps the escalation level. */
    data class Escalate(val newAmount: Double) : QuestDetailEvent

    data object Retire : QuestDetailEvent

    data object Delete : QuestDetailEvent

    /** Rewrite one of this quest's journal entries. Never touches completions. */
    data class EditJournalEntry(val id: JournalEntryId, val text: String) : QuestDetailEvent

    /** Remove one of this quest's journal entries. Never touches completions. */
    data class DeleteJournalEntry(val id: JournalEntryId) : QuestDetailEvent
}
