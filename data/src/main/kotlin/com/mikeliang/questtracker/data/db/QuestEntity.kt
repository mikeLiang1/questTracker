package com.mikeliang.questtracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Flattened Room row for [com.mikeliang.questtracker.core.model.Quest]. The domain model's
 * sealed [com.mikeliang.questtracker.core.model.QuestKind] and
 * [com.mikeliang.questtracker.core.model.ReminderSchedule] hierarchies are stored as nullable
 * columns rather than a discriminator column: [cadence] null means [QuestKind.SideQuest];
 * [reminderTime] vs [reminderOneShotAt] distinguish the two reminder variants. All conversion
 * lives in Mappers.kt — this class carries no domain logic.
 */
@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val cadence: String?,
    val questType: String?,
    val attribute: String?,
    val progressionAmount: Double?,
    val progressionUnit: String?,
    val progressionEscalationLevel: Int?,
    val createdAtEpochMillis: Long,
    val status: String,
    val reminderTime: String?,
    val reminderDays: String?,
    val reminderOneShotAt: String?,
    val autoTrackingMetric: String?,
    val autoTrackingDailyTarget: Double?,
    val cadenceChangedOnEpochDay: Long?,
    @ColumnInfo(defaultValue = "0") val journalLinked: Boolean = false,
)
