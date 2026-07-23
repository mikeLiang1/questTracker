package com.mikeliang.questtracker.ui.questlog

import com.mikeliang.questtracker.core.engine.CompletionFeedback
import com.mikeliang.questtracker.core.engine.QuestLogDay
import com.mikeliang.questtracker.core.model.JournalEntryId
import java.time.LocalDate

/**
 * Everything the Quest Log screen renders, as one immutable value. The timeline
 * itself comes straight from [com.mikeliang.questtracker.core.engine.QuestEngine.questLog];
 * this state only adds [today] so the screen can label days ("Today", "Yesterday")
 * without reading a clock of its own.
 */
data class QuestLogUiState(
    val loading: Boolean = false,
    val days: List<QuestLogDay> = emptyList(),
    val today: LocalDate? = null,
    /** Transient save feedback; cleared via [QuestLogEvent.FeedbackShown]. */
    val feedback: QuestLogFeedback? = null,
) {
    val isEmpty: Boolean get() = !loading && days.isEmpty()
}

/**
 * What a save banked. There is deliberately no "didn't count" variant — an entry
 * that completes nothing is still an entry, and the copy says only that it saved.
 */
sealed interface QuestLogFeedback {

    /** Ready-to-display copy. */
    val message: String

    /** Saving the entry also completed at least one journal-linked quest. */
    data class QuestCompleted(val completion: CompletionFeedback) : QuestLogFeedback {
        override val message: String get() = completion.message
    }

    /** The entry saved; nothing new banked (which is fine). */
    data object EntrySaved : QuestLogFeedback {
        override val message: String get() = "Entry saved."
    }
}

/** Every user intent the screen can produce. */
sealed interface QuestLogEvent {

    /** Write a new entry; may auto-complete journal-linked quests. */
    data class SaveEntry(val text: String) : QuestLogEvent

    /** Rewrite an existing entry's text. Never touches completions. */
    data class EditEntry(val id: JournalEntryId, val text: String) : QuestLogEvent

    /** Remove an entry. Never touches completions — banked stays banked. */
    data class DeleteEntry(val id: JournalEntryId) : QuestLogEvent

    /** The save feedback has been displayed; clear it from state. */
    data object FeedbackShown : QuestLogEvent
}
