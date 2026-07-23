package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.ProgressionTarget
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import java.time.LocalDate

/**
 * A validated quest edit. The two shapes are sealed per [QuestKind], so kind
 * immutability holds by construction — there is no way to express a SideQuest ↔
 * Recurring conversion (the identity firewall stays intact).
 */
sealed interface QuestEdit {

    data class EditSideQuest(
        val title: String,
        val reminder: ReminderSchedule?,
    ) : QuestEdit

    data class EditRecurring(
        val title: String,
        val cadence: Cadence,
        val attribute: Attribute,
        val reminder: ReminderSchedule?,
        val target: TargetEdit = TargetEdit.Keep,
        val journalLinked: Boolean = false,
    ) : QuestEdit
}

/**
 * What happens to a recurring quest's progression target. Changing the target
 * *amount* is deliberately not expressible here — that is escalation and goes
 * through [escalate], the one path that bumps the level.
 */
sealed interface TargetEdit {

    /** Leave the target (and therefore the quest's type) as it is. */
    data object Keep : TargetEdit

    /** Maintenance → Progression: add a target. */
    data class Add(val amount: Double, val unit: String) : TargetEdit

    /** Progression → Maintenance: drop the target. Banked gains stay. */
    data object Remove : TargetEdit
}

/**
 * Applies [edit] to [quest], producing a copy. Forward-looking only: `id`,
 * `createdAt`, `status`, `autoTracking`, and every banked completion are untouched.
 * A cadence change stamps [Quest.cadenceChangedOn] with [today] so consistency
 * scoring restarts its window instead of re-reading history under the new clock.
 *
 * [completions] must be this quest's records; a re-added target starts above the
 * highest escalation level ever recorded, so returning to Progression never resumes
 * a farmed level (full accrual rate is always restored, never a punishment).
 */
fun editQuest(
    quest: Quest,
    edit: QuestEdit,
    completions: List<CompletionRecord>,
    today: LocalDate,
): Quest {
    require(quest.status == QuestStatus.Active) {
        "Retired quests are read-only chapters: ${quest.id.value}"
    }
    return when (edit) {
        is QuestEdit.EditSideQuest -> {
            require(quest.kind is QuestKind.SideQuest) {
                "Kind is immutable: cannot apply a side-quest edit to a recurring quest"
            }
            quest.copy(title = edit.title, reminder = edit.reminder)
        }

        is QuestEdit.EditRecurring -> {
            val kind = quest.kind
            require(kind is QuestKind.Recurring) {
                "Kind is immutable: cannot apply a recurring edit to a side quest"
            }
            val progression = when (edit.target) {
                TargetEdit.Keep -> kind.progression

                is TargetEdit.Add -> {
                    require(kind.type != QuestType.Progression) {
                        "Quest already has a target — change its amount via escalate()"
                    }
                    val questRecords = completions.filter { it.questId == quest.id }
                    val level = questRecords
                        .mapNotNull { it.escalationLevel }
                        .maxOrNull()
                        ?.plus(1)
                        ?: 0
                    ProgressionTarget(edit.target.amount, edit.target.unit, level)
                }

                TargetEdit.Remove -> {
                    require(kind.type == QuestType.Progression) {
                        "Only progression quests have a target to remove"
                    }
                    null
                }
            }
            val type = when {
                progression != null -> QuestType.Progression
                kind.type == QuestType.Progression -> QuestType.Maintenance
                else -> kind.type
            }
            quest.copy(
                title = edit.title,
                reminder = edit.reminder,
                kind = kind.copy(
                    cadence = edit.cadence,
                    type = type,
                    attribute = edit.attribute,
                    progression = progression,
                    journalLinked = edit.journalLinked,
                ),
                cadenceChangedOn = if (edit.cadence != kind.cadence) today else quest.cadenceChangedOn,
            )
        }
    }
}

/**
 * Retires [quest] — the only exit for a quest with history. Everything banked stays:
 * the copy differs in [Quest.status] alone, and the archive keeps every point.
 */
fun retireQuest(quest: Quest): Quest = quest.copy(status = QuestStatus.Retired)

/**
 * Whether [quest] may be hard-deleted: true only when it has zero completion records.
 * Any record — manual, auto-tracked, even a same-period duplicate — is history the
 * user owns and blocks deletion; a quest with history retires instead. The repository
 * delete is deliberately dumb; this is the guard callers must pass first.
 */
fun canDeleteQuest(quest: Quest, completions: List<CompletionRecord>): Boolean =
    completions.none { it.questId == quest.id }
