package com.mikeliang.questtracker.core.onboarding

import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.AutoTracking
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import java.time.Instant
import java.util.UUID

/**
 * The three onboarding presets. Names are identity flavor only — they carry no
 * mechanics; the loadout is what matters.
 */
enum class StartingClass { Warrior, Sage, Adventurer }

/**
 * The quest loadout a starting class pre-populates the board with.
 *
 * Every loadout is four active [QuestType.Maintenance] recurring quests (showing up is
 * the win — progression targets need more thought than onboarding allows) with no
 * reminders (reminder times are always user-chosen, never invented by a preset) and
 * exactly one auto-tracked steps quest, so auto-tracking is on by default for every
 * class.
 *
 * @param createdAt creation instant stamped on every quest (callers get it from the
 * injected [com.mikeliang.questtracker.core.Clock]).
 * @param newId id generator, injectable for deterministic tests.
 */
fun StartingClass.questLoadout(
    createdAt: Instant,
    newId: () -> QuestId = { QuestId(UUID.randomUUID().toString()) },
): List<Quest> {
    fun quest(
        title: String,
        cadence: Cadence,
        attribute: Attribute,
        autoTracking: AutoTracking? = null,
        journalLinked: Boolean = false,
    ) = Quest(
        id = newId(),
        title = title,
        kind = QuestKind.Recurring(
            cadence = cadence,
            type = QuestType.Maintenance,
            attribute = attribute,
            journalLinked = journalLinked,
        ),
        createdAt = createdAt,
        autoTracking = autoTracking,
    )

    return when (this) {
        StartingClass.Warrior -> listOf(
            quest("Walk 8,000 steps", Cadence.Daily, Attribute.Body, AutoTracking(HealthMetric.Steps, 8_000.0)),
            quest("Train or stretch", Cadence.Daily, Attribute.Body),
            quest("Sleep 7+ hours", Cadence.Daily, Attribute.Body, AutoTracking(HealthMetric.SleepMinutes, 420.0)),
            quest("Plan tomorrow", Cadence.Daily, Attribute.Discipline),
        )
        StartingClass.Sage -> listOf(
            quest("Read 20 minutes", Cadence.Daily, Attribute.Mind),
            quest("One line of journal", Cadence.Daily, Attribute.Mind, journalLinked = true),
            quest("Walk 6,000 steps", Cadence.Daily, Attribute.Body, AutoTracking(HealthMetric.Steps, 6_000.0)),
            quest("One deep-work block", Cadence.Weekly, Attribute.Discipline),
        )
        StartingClass.Adventurer -> listOf(
            quest("Walk 7,000 steps", Cadence.Daily, Attribute.Body, AutoTracking(HealthMetric.Steps, 7_000.0)),
            quest("Read 15 minutes", Cadence.Daily, Attribute.Mind),
            quest("Reach out to someone", Cadence.Weekly, Attribute.Social),
            quest("Plan tomorrow", Cadence.Daily, Attribute.Discipline),
        )
    }
}
