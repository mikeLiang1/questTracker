package com.mikeliang.questtracker.core.model

import com.mikeliang.questtracker.core.health.HealthMetric
import java.time.Instant
import java.time.LocalDate

/** Stable identifier for a quest. */
@JvmInline
value class QuestId(val value: String)

/**
 * The four life-area attributes. Recurring-quest completions accrue points toward
 * exactly one attribute; side quests never touch them.
 */
enum class Attribute { Body, Mind, Social, Discipline }

/** The progression track a recurring quest runs on. Each cadence is its own clock. */
enum class Cadence { Daily, Weekly, Monthly }

/**
 * How a recurring quest grows:
 * - [Maintenance]: consistency is the win; the quest never escalates.
 * - [Progression]: a rising target (e.g. step count) that escalates with diminishing
 *   returns on completions farmed at the same level.
 * - [Outcome]: the quest itself is fixed; a separately measured result climbs.
 */
enum class QuestType { Maintenance, Progression, Outcome }

/**
 * Active quests appear on the board; retired quests move to the "completed chapters"
 * archive. Retiring is the only exit for a quest with history — quests are never
 * failed, and deletion exists solely for mis-creations with zero lifetime completions
 * (nothing banked, so nothing is taken away; see `canDeleteQuest`).
 */
enum class QuestStatus { Active, Retired }

/**
 * The rising target of a [QuestType.Progression] quest.
 *
 * @property escalationLevel bumped on every target change; diminishing returns are
 * keyed to it, so escalating restores the full accrual rate.
 */
data class ProgressionTarget(
    val amount: Double,
    val unit: String,
    val escalationLevel: Int = 0,
) {
    init {
        require(amount > 0) { "Progression target amount must be positive, was $amount" }
        require(escalationLevel >= 0) { "Escalation level cannot be negative, was $escalationLevel" }
    }
}

/**
 * The identity firewall: recurring quests build attributes; side quests are one-off
 * life admin that tick boxes and count toward lifetime completions but never feed
 * attributes.
 */
sealed interface QuestKind {

    data class Recurring(
        val cadence: Cadence,
        val type: QuestType,
        val attribute: Attribute,
        val progression: ProgressionTarget? = null,
    ) : QuestKind {
        init {
            require((type == QuestType.Progression) == (progression != null)) {
                "Progression quests require a ProgressionTarget; other types must not have one"
            }
        }
    }

    data object SideQuest : QuestKind
}

/**
 * Auto-completion source for a recurring quest: the quest completes for the day when
 * the metric reaches [dailyTarget]. Absence of data never fails the quest — it just
 * leaves manual completion available.
 */
data class AutoTracking(
    val metric: HealthMetric,
    val dailyTarget: Double,
) {
    init {
        require(dailyTarget > 0) { "Auto-tracking target must be positive, was $dailyTarget" }
    }
}

/**
 * A quest. Immutable; edits (escalation, retirement, reminder changes) produce copies.
 *
 * @property cadenceChangedOn the date of the most recent cadence edit, if any.
 * Consistency scoring restarts its evaluation window here so a cadence change never
 * retroactively re-reads old periods under the new clock (no-take-away rule).
 */
data class Quest(
    val id: QuestId,
    val title: String,
    val kind: QuestKind,
    val createdAt: Instant,
    val status: QuestStatus = QuestStatus.Active,
    val reminder: ReminderSchedule? = null,
    val autoTracking: AutoTracking? = null,
    val cadenceChangedOn: LocalDate? = null,
) {
    init {
        require(title.isNotBlank()) { "Quest title must not be blank" }
        require(autoTracking == null || kind is QuestKind.Recurring) {
            "Only recurring quests can be auto-tracked"
        }
    }
}
