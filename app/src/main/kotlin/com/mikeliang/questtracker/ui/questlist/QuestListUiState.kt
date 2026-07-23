package com.mikeliang.questtracker.ui.questlist

import com.mikeliang.questtracker.core.engine.CompletionFeedback
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import java.time.LocalTime

/**
 * Everything the main screen renders, as one immutable value. Derived from
 * [com.mikeliang.questtracker.core.engine.TodayBoard] plus live health readings;
 * the ViewModel never invents board or reward logic of its own.
 */
data class QuestListUiState(
    val loading: Boolean = false,
    val recurring: List<RecurringItem> = emptyList(),
    val sideQuests: List<SideQuestItem> = emptyList(),
    val doneForToday: Boolean = false,
    /** Transient identity-framed copy from :core; cleared via [QuestListEvent.FeedbackShown]. */
    val feedback: CompletionFeedback? = null,
    /** True while the monthly reflection is surfaced (never forced) as a banner. */
    val reflectionDue: Boolean = false,
) {

    /** A recurring quest due this period, with live progress when auto-tracked. */
    data class RecurringItem(
        val quest: Quest,
        val completed: Boolean,
        val progress: AutoProgress?,
        /** True while today's manual tick can still be un-cleared (same-day mis-tap window). */
        val undoable: Boolean = false,
    )

    /** An open (or just-ticked-today) side quest. */
    data class SideQuestItem(
        val quest: Quest,
        val completed: Boolean,
        /** Same-day mis-tap window, as on [RecurringItem.undoable]. */
        val undoable: Boolean = false,
    )

    /** Live auto-tracking readout. [current] is null while the source has no data. */
    data class AutoProgress(
        val metric: HealthMetric,
        val current: Double?,
        val target: Double,
    )

    val isEmpty: Boolean get() = !loading && recurring.isEmpty() && sideQuests.isEmpty()

    /** Recurring quests still open this period — the top of the board. */
    val activeRecurring: List<RecurringItem> get() = recurring.filter { !it.completed }

    /**
     * Recurring quests already banked this period. Always rendered as their own
     * "Cleared today" section, not just under the done-for-today banner — a cleared
     * quest visibly moves out of the way while its gain stays on screen.
     */
    val clearedRecurring: List<RecurringItem> get() = recurring.filter { it.completed }
}

/** Every user intent the screen can produce. */
sealed interface QuestListEvent {

    /** One-tap manual completion (recurring or side quest). */
    data class CompleteQuest(val id: QuestId) : QuestListEvent

    /** Tap-again undo of today's manual completion; a no-op once the gain is banked overnight. */
    data class UnclearQuest(val id: QuestId) : QuestListEvent

    /** Quick-add default path: capture a side quest, optionally with a reminder. */
    data class AddSideQuest(
        val title: String,
        val reminderTime: LocalTime?,
    ) : QuestListEvent

    /** Quick-add secondary path ("make this recurring…"). */
    data class AddRecurringQuest(
        val title: String,
        val cadence: Cadence,
        val attribute: Attribute,
        val reminderTime: LocalTime?,
        /** Opt-in: saving a journal entry auto-completes this quest for its period. */
        val journalLinked: Boolean = false,
    ) : QuestListEvent

    /** The completion feedback has been displayed; clear it from state. */
    data object FeedbackShown : QuestListEvent
}
