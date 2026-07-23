package com.mikeliang.questtracker.ui.questdetail

import com.mikeliang.questtracker.core.engine.ConsistencyScore
import com.mikeliang.questtracker.core.engine.QuestEdit
import com.mikeliang.questtracker.core.model.Quest
import java.time.LocalTime

/**
 * One quest's detail: identity, the evidence behind it (lifetime completions first —
 * the number that can't break), and what the user may do to it. [canDelete] is true
 * only for zero-completion mis-creations; everything with history retires instead.
 * [closed] asks the host to pop back (after retire/delete, or if the quest vanished).
 */
data class QuestDetailUiState(
    val loading: Boolean = false,
    val quest: Quest? = null,
    val lifetimeCompletions: Int = 0,
    val consistency: ConsistencyScore? = null,
    val canDelete: Boolean = false,
    val closed: Boolean = false,
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
}
