package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.progressionQuest
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EscalationTest {

    @Test
    fun `escalating raises the target and bumps the level`() {
        val quest = progressionQuest(amount = 8000.0)

        val escalated = escalate(quest, 10_000.0)

        val target = (escalated.kind as QuestKind.Recurring).progression!!
        assertEquals(10_000.0, target.amount)
        assertEquals(1, target.escalationLevel)
        assertEquals(quest.id, escalated.id)
    }

    @Test
    fun `lowering the target is a valid choice and still bumps the level`() {
        val quest = progressionQuest(amount = 8000.0)

        val eased = escalate(quest, 6000.0)

        val target = (eased.kind as QuestKind.Recurring).progression!!
        assertEquals(6000.0, target.amount)
        assertEquals(1, target.escalationLevel)
    }

    @Test
    fun `only progression quests escalate`() {
        assertThrows<IllegalArgumentException> { escalate(recurringQuest(), 10.0) }
        assertThrows<IllegalArgumentException> { escalate(sideQuest(), 10.0) }
    }
}
