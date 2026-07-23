package com.mikeliang.questtracker.data

import com.mikeliang.questtracker.core.engine.AccrualRules
import com.mikeliang.questtracker.core.model.Attribute
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
    cadenceChangedOn: LocalDate? = null,
    journalLinked: Boolean = false,
): Quest = Quest(
    id = QuestId(id),
    title = title,
    kind = QuestKind.Recurring(cadence, type, attribute, progression, journalLinked),
    createdAt = createdAt,
    status = status,
    reminder = reminder,
    cadenceChangedOn = cadenceChangedOn,
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

fun journalEntry(
    id: String = "entry-1",
    text: String = "Grateful for the rain.",
    entryDate: LocalDate = LocalDate.parse("2026-01-01"),
    createdAt: Instant = entryDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
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

fun completion(
    quest: Quest,
    periodStart: LocalDate,
    completedAt: Instant = periodStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
    source: CompletionSource = CompletionSource.Manual,
    escalationLevel: Int? = (quest.kind as? QuestKind.Recurring)?.progression?.escalationLevel,
): CompletionRecord {
    val kind = quest.kind as? QuestKind.Recurring
    return CompletionRecord(
        questId = quest.id,
        completedAt = completedAt,
        periodStart = periodStart,
        source = source,
        escalationLevel = escalationLevel,
        attribute = kind?.attribute,
        basePoints = kind?.let { AccrualRules.basePoints(it.cadence) },
    )
}
