package com.mikeliang.questtracker.data

import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.AutoTracking
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
import com.mikeliang.questtracker.data.db.CompletionEntity
import com.mikeliang.questtracker.data.db.QuestEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun Quest.toEntity(): QuestEntity {
    val recurring = kind as? QuestKind.Recurring
    val recurringReminder = reminder as? ReminderSchedule.Recurring
    val oneShotReminder = reminder as? ReminderSchedule.OneShot

    return QuestEntity(
        id = id.value,
        title = title,
        cadence = recurring?.cadence?.name,
        questType = recurring?.type?.name,
        attribute = recurring?.attribute?.name,
        progressionAmount = recurring?.progression?.amount,
        progressionUnit = recurring?.progression?.unit,
        progressionEscalationLevel = recurring?.progression?.escalationLevel,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        status = status.name,
        reminderTime = recurringReminder?.time?.toString(),
        reminderDays = recurringReminder?.days?.joinToString(",") { it.name },
        reminderOneShotAt = oneShotReminder?.at?.toString(),
        autoTrackingMetric = autoTracking?.metric?.name,
        autoTrackingDailyTarget = autoTracking?.dailyTarget,
    )
}

fun QuestEntity.toDomain(): Quest {
    val kind: QuestKind = if (cadence != null) {
        QuestKind.Recurring(
            cadence = Cadence.valueOf(cadence),
            type = QuestType.valueOf(requireNotNull(questType) { "Recurring quest $id missing questType" }),
            attribute = Attribute.valueOf(requireNotNull(attribute) { "Recurring quest $id missing attribute" }),
            progression = progressionAmount?.let {
                ProgressionTarget(
                    amount = it,
                    unit = requireNotNull(progressionUnit) { "Progression quest $id missing progressionUnit" },
                    escalationLevel = progressionEscalationLevel ?: 0,
                )
            },
        )
    } else {
        QuestKind.SideQuest
    }

    val reminder: ReminderSchedule? = when {
        reminderTime != null -> ReminderSchedule.Recurring(
            time = LocalTime.parse(reminderTime),
            days = reminderDays.orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .map { DayOfWeek.valueOf(it) }
                .toSet(),
        )
        reminderOneShotAt != null -> ReminderSchedule.OneShot(at = LocalDateTime.parse(reminderOneShotAt))
        else -> null
    }

    val autoTracking = autoTrackingMetric?.let {
        AutoTracking(
            metric = HealthMetric.valueOf(it),
            dailyTarget = requireNotNull(autoTrackingDailyTarget) { "Auto-tracked quest $id missing dailyTarget" },
        )
    }

    return Quest(
        id = QuestId(id),
        title = title,
        kind = kind,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        status = QuestStatus.valueOf(status),
        reminder = reminder,
        autoTracking = autoTracking,
    )
}

fun CompletionRecord.toEntity(): CompletionEntity = CompletionEntity(
    questId = questId.value,
    completedAtEpochMillis = completedAt.toEpochMilli(),
    periodStartEpochDay = periodStart.toEpochDay(),
    source = source.name,
    escalationLevel = escalationLevel,
)

fun CompletionEntity.toDomain(): CompletionRecord = CompletionRecord(
    questId = QuestId(questId),
    completedAt = Instant.ofEpochMilli(completedAtEpochMillis),
    periodStart = LocalDate.ofEpochDay(periodStartEpochDay),
    source = CompletionSource.valueOf(source),
    escalationLevel = escalationLevel,
)
