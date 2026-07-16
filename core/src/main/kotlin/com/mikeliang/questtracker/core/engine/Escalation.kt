package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType

/**
 * Changes a progression quest's target — the honest "harder boss", and always the
 * user's choice (the app never forces escalation; lowering the target is equally
 * valid). Any change bumps the escalation level, which restores the full accrual
 * rate for diminishing returns.
 */
fun escalate(quest: Quest, newAmount: Double): Quest {
    val kind = quest.kind
    require(kind is QuestKind.Recurring && kind.type == QuestType.Progression) {
        "Only progression quests can be escalated"
    }
    val target = checkNotNull(kind.progression)
    return quest.copy(
        kind = kind.copy(
            progression = target.copy(
                amount = newAmount,
                escalationLevel = target.escalationLevel + 1,
            )
        )
    )
}
