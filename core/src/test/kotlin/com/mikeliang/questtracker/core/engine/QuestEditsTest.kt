package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.AutoTracking
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.core.progressionQuest
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QuestEditsTest {

    private val today = date("2026-07-16")
    private val reminder = ReminderSchedule.Recurring(LocalTime.of(7, 30), setOf(DayOfWeek.MONDAY))

    private fun recurringEdit(
        quest: Quest,
        title: String = quest.title,
        cadence: Cadence = (quest.kind as QuestKind.Recurring).cadence,
        attribute: Attribute = (quest.kind as QuestKind.Recurring).attribute,
        reminder: ReminderSchedule? = quest.reminder,
        target: TargetEdit = TargetEdit.Keep,
        journalLinked: Boolean = (quest.kind as QuestKind.Recurring).journalLinked,
    ) = QuestEdit.EditRecurring(title, cadence, attribute, reminder, target, journalLinked)

    @Test
    fun `a recurring edit applies title, attribute, cadence, and reminder`() {
        val quest = recurringQuest()

        val edited = editQuest(
            quest,
            recurringEdit(
                quest,
                title = "Morning gym hour",
                cadence = Cadence.Weekly,
                attribute = Attribute.Discipline,
                reminder = reminder,
            ),
            emptyList(),
            today,
        )

        assertEquals("Morning gym hour", edited.title)
        val kind = edited.kind as QuestKind.Recurring
        assertEquals(Cadence.Weekly, kind.cadence)
        assertEquals(Attribute.Discipline, kind.attribute)
        assertEquals(reminder, edited.reminder)
    }

    @Test
    fun `a side-quest edit applies title and reminder`() {
        val quest = sideQuest()
        val oneShot = ReminderSchedule.OneShot(today.plusDays(1).atTime(9, 0))

        val edited = editQuest(quest, QuestEdit.EditSideQuest("Call the electrician", oneShot), emptyList(), today)

        assertEquals("Call the electrician", edited.title)
        assertEquals(oneShot, edited.reminder)
        assertEquals(QuestKind.SideQuest, edited.kind)
    }

    @Test
    fun `kind is immutable in both directions`() {
        assertThrows<IllegalArgumentException> {
            editQuest(sideQuest(), recurringEdit(recurringQuest()), emptyList(), today)
        }
        assertThrows<IllegalArgumentException> {
            editQuest(recurringQuest(), QuestEdit.EditSideQuest("New title", null), emptyList(), today)
        }
    }

    @Test
    fun `retired quests are read-only`() {
        val quest = recurringQuest(status = QuestStatus.Retired)

        assertThrows<IllegalArgumentException> {
            editQuest(quest, recurringEdit(quest, title = "New title"), emptyList(), today)
        }
    }

    @Test
    fun `edits never touch id, createdAt, status, or autoTracking`() {
        val quest = recurringQuest(autoTracking = AutoTracking(HealthMetric.Steps, 8000.0))

        val edited = editQuest(quest, recurringEdit(quest, title = "Renamed"), emptyList(), today)

        assertEquals(quest.id, edited.id)
        assertEquals(quest.createdAt, edited.createdAt)
        assertEquals(quest.status, edited.status)
        assertEquals(quest.autoTracking, edited.autoTracking)
    }

    @Test
    fun `a cadence change stamps cadenceChangedOn with today`() {
        val quest = recurringQuest(cadence = Cadence.Daily)

        val edited = editQuest(quest, recurringEdit(quest, cadence = Cadence.Weekly), emptyList(), today)

        assertEquals(today, edited.cadenceChangedOn)
    }

    @Test
    fun `an edit that keeps the cadence preserves the previous stamp`() {
        val previousChange = date("2026-03-01")
        val quest = recurringQuest(cadenceChangedOn = previousChange)

        val edited = editQuest(quest, recurringEdit(quest, title = "Renamed"), emptyList(), today)

        assertEquals(previousChange, edited.cadenceChangedOn)
    }

    @Test
    fun `adding a target with no history starts at level zero`() {
        val quest = recurringQuest(type = QuestType.Maintenance)

        val edited = editQuest(
            quest,
            recurringEdit(quest, target = TargetEdit.Add(8000.0, "steps")),
            emptyList(),
            today,
        )

        val kind = edited.kind as QuestKind.Recurring
        assertEquals(QuestType.Progression, kind.type)
        assertEquals(8000.0, kind.progression?.amount)
        assertEquals("steps", kind.progression?.unit)
        assertEquals(0, kind.progression?.escalationLevel)
    }

    @Test
    fun `a re-added target starts above the highest recorded level - never resumes a farmed one`() {
        // A quest that was Progression up to level 2, then had its target removed.
        val past = progressionQuest(escalationLevel = 2)
        val quest = recurringQuest(type = QuestType.Maintenance)
        val history = completions(past, date("2026-07-01"), date("2026-07-02"))

        val edited = editQuest(
            quest,
            recurringEdit(quest, target = TargetEdit.Add(9000.0, "steps")),
            history,
            today,
        )

        assertEquals(3, (edited.kind as QuestKind.Recurring).progression?.escalationLevel)
    }

    @Test
    fun `adding a target to a progression quest throws - amounts change via escalate`() {
        val quest = progressionQuest()

        assertThrows<IllegalArgumentException> {
            editQuest(quest, recurringEdit(quest, target = TargetEdit.Add(9000.0, "steps")), emptyList(), today)
        }
    }

    @Test
    fun `removing the target makes the quest maintenance - gains stay banked`() {
        val quest = progressionQuest()

        val edited = editQuest(quest, recurringEdit(quest, target = TargetEdit.Remove), emptyList(), today)

        val kind = edited.kind as QuestKind.Recurring
        assertEquals(QuestType.Maintenance, kind.type)
        assertNull(kind.progression)
    }

    @Test
    fun `removing a target a maintenance quest doesn't have throws`() {
        val quest = recurringQuest(type = QuestType.Maintenance)

        assertThrows<IllegalArgumentException> {
            editQuest(quest, recurringEdit(quest, target = TargetEdit.Remove), emptyList(), today)
        }
    }

    @Test
    fun `retiring flips status and nothing else`() {
        val quest = recurringQuest(reminder = reminder)

        val retired = retireQuest(quest)

        assertEquals(QuestStatus.Retired, retired.status)
        assertEquals(quest.copy(status = QuestStatus.Retired), retired)
    }

    @Test
    fun `delete is allowed only with zero completions`() {
        val quest = recurringQuest()
        val other = sideQuest()

        assertTrue(canDeleteQuest(quest, emptyList()))
        assertTrue(canDeleteQuest(quest, listOf(completion(other, date("2026-07-10")))))
        assertFalse(canDeleteQuest(quest, listOf(completion(quest, date("2026-07-10")))))
    }

    @Test
    fun `an edit can link and unlink the journal`() {
        val quest = recurringQuest()

        val linked = editQuest(quest, recurringEdit(quest, journalLinked = true), emptyList(), today)
        assertTrue((linked.kind as QuestKind.Recurring).journalLinked)

        val unlinked = editQuest(linked, recurringEdit(linked, journalLinked = false), emptyList(), today)
        assertFalse((unlinked.kind as QuestKind.Recurring).journalLinked)
    }

    @Test
    fun `an unrelated edit preserves the journal link`() {
        val quest = recurringQuest(journalLinked = true)

        val edited = editQuest(quest, recurringEdit(quest, title = "Renamed"), emptyList(), today)

        assertTrue((edited.kind as QuestKind.Recurring).journalLinked)
    }

    @Test
    fun `lifetime count survives a cadence edit`() {
        val daily = recurringQuest(cadence = Cadence.Daily)
        // Three daily credits inside one calendar week.
        val history = completions(daily, date("2026-07-13"), date("2026-07-14"), date("2026-07-15"))

        val edited = editQuest(daily, recurringEdit(daily, cadence = Cadence.Weekly), history, today)

        assertEquals(3, lifetimeCompletionCount(listOf(edited), history))
    }
}
