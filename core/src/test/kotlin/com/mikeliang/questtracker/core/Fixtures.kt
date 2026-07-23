package com.mikeliang.questtracker.core

import com.mikeliang.questtracker.core.engine.AccrualRules
import com.mikeliang.questtracker.core.engine.periodStartFor
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.AutoTracking
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.core.model.ProgressionTarget
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class FakeClock(
    var instant: Instant,
    var zoneId: ZoneId = ZoneOffset.UTC,
) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

fun recurringQuest(
    id: String = "quest-1",
    title: String = "Gym hour",
    cadence: Cadence = Cadence.Daily,
    type: QuestType = QuestType.Maintenance,
    attribute: Attribute = Attribute.Body,
    progression: ProgressionTarget? = null,
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    status: QuestStatus = QuestStatus.Active,
    reminder: ReminderSchedule? = null,
    autoTracking: AutoTracking? = null,
    cadenceChangedOn: LocalDate? = null,
    journalLinked: Boolean = false,
): Quest = Quest(
    id = QuestId(id),
    title = title,
    kind = QuestKind.Recurring(cadence, type, attribute, progression, journalLinked),
    createdAt = createdAt,
    status = status,
    reminder = reminder,
    autoTracking = autoTracking,
    cadenceChangedOn = cadenceChangedOn,
)

fun progressionQuest(
    id: String = "quest-1",
    title: String = "Walk it off",
    cadence: Cadence = Cadence.Daily,
    attribute: Attribute = Attribute.Body,
    amount: Double = 8000.0,
    unit: String = "steps",
    escalationLevel: Int = 0,
): Quest = recurringQuest(
    id = id,
    title = title,
    cadence = cadence,
    type = QuestType.Progression,
    attribute = attribute,
    progression = ProgressionTarget(amount, unit, escalationLevel),
)

fun sideQuest(
    id: String = "side-1",
    title: String = "Call the plumber",
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    status: QuestStatus = QuestStatus.Active,
    reminder: ReminderSchedule? = null,
): Quest = Quest(
    id = QuestId(id),
    title = title,
    kind = QuestKind.SideQuest,
    createdAt = createdAt,
    status = status,
    reminder = reminder,
)

/** A completion credited to the period containing [date] (in the quest's cadence). */
fun completion(
    quest: Quest,
    date: LocalDate,
    source: CompletionSource = CompletionSource.Manual,
    completedAt: Instant = date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC),
    escalationLevel: Int? = (quest.kind as? QuestKind.Recurring)?.progression?.escalationLevel,
): CompletionRecord {
    val kind = quest.kind as? QuestKind.Recurring
    return CompletionRecord(
        questId = quest.id,
        completedAt = completedAt,
        periodStart = kind?.let { periodStartFor(date, it.cadence) } ?: date,
        source = source,
        escalationLevel = escalationLevel,
        attribute = kind?.attribute,
        basePoints = kind?.let { AccrualRules.basePoints(it.cadence) },
    )
}

/** One completion per date, for building histories tersely. */
fun completions(quest: Quest, vararg dates: LocalDate): List<CompletionRecord> =
    dates.map { completion(quest, it) }

fun journalEntry(
    id: String = "entry-1",
    text: String = "Grateful for the rain.",
    entryDate: LocalDate = date("2026-01-01"),
    createdAt: Instant = entryDate.atTime(LocalTime.of(21, 0)).toInstant(ZoneOffset.UTC),
    editedAt: Instant? = null,
    questIds: Set<QuestId> = emptySet(),
): JournalEntry = JournalEntry(
    id = JournalEntryId(id),
    text = text,
    createdAt = createdAt,
    entryDate = entryDate,
    editedAt = editedAt,
    questIds = questIds,
)

fun date(iso: String): LocalDate = LocalDate.parse(iso)
