package com.mikeliang.questtracker.ui.reflection

import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.ProgressionTarget
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.ui.questlist.QuestListEvent
import java.time.YearMonth

/**
 * The reflection flow's state: last month's evidence plus the user's pending answer.
 * Choices stay pending until [ReflectionEvent.Complete] — nothing is retired or
 * escalated while the user is still thinking.
 */
data class ReflectionUiState(
    val loading: Boolean = false,
    /** The reviewed month (always the previous calendar month). */
    val month: YearMonth? = null,
    val rows: List<Row> = emptyList(),
    /** Attribute points banked inside the month, all four attributes present. */
    val attributeGains: Map<Attribute, Double> = emptyMap(),
    /** Titles of quests already added during this reflection (adds apply immediately). */
    val addedQuestTitles: List<String> = emptyList(),
    /** Set once the reflection is completed or skipped; the host closes the overlay. */
    val closed: Boolean = false,
) {

    /** One quest's month in review, paired with the user's pending choice for it. */
    data class Row(
        val quest: Quest,
        val completionsInMonth: Int,
        val consistencyRate: Double,
        val escalatedInMonth: Boolean,
        /** The quietest quest this month — surfaced gently, never as a failure. */
        val barelyMoved: Boolean,
        val choice: ReflectionChoice = ReflectionChoice.Keep,
    ) {
        val progression: ProgressionTarget?
            get() = (quest.kind as? QuestKind.Recurring)?.progression

        /** Escalation is only meaningful for progression quests. */
        val canEscalate: Boolean get() = progression != null
    }
}

/** The user's answer for one quest. Keep is the default — doing nothing is fine. */
sealed interface ReflectionChoice {
    data object Keep : ReflectionChoice
    data object Retire : ReflectionChoice
    data class Escalate(val newAmount: Double) : ReflectionChoice
}

/** Every user intent the reflection screen can produce. */
sealed interface ReflectionEvent {

    /** Sets (or reverts) the pending choice for one quest. */
    data class Choose(val id: QuestId, val choice: ReflectionChoice) : ReflectionEvent

    /** Adds a quest right now via the shared quick-add sheet event. */
    data class AddQuest(val event: QuestListEvent) : ReflectionEvent

    /** Applies every pending choice and marks the month handled. */
    data object Complete : ReflectionEvent

    /** Marks the month handled without touching a single quest. Never guilt-tripped. */
    data object Skip : ReflectionEvent
}
