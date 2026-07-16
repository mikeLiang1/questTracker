package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TodayBoardTest {

    private val today = date("2026-07-16") // Thursday

    @Test
    fun `recurring quests are ordered daily then weekly then monthly`() {
        val monthly = recurringQuest(id = "m", title = "Review budget", cadence = Cadence.Monthly)
        val weekly = recurringQuest(id = "w", title = "Long run", cadence = Cadence.Weekly)
        val daily = recurringQuest(id = "d", title = "Gym hour", cadence = Cadence.Daily)

        val board = buildTodayBoard(listOf(monthly, weekly, daily), emptyList(), today)

        assertEquals(listOf("d", "w", "m"), board.recurring.map { it.quest.id.value })
    }

    @Test
    fun `completion state reflects the current period per cadence`() {
        val daily = recurringQuest(id = "d", cadence = Cadence.Daily)
        val weekly = recurringQuest(id = "w", title = "Long run", cadence = Cadence.Weekly)
        // Daily done yesterday (stale), weekly done Monday (current week counts).
        val history = completions(daily, today.minusDays(1)) + completions(weekly, date("2026-07-13"))

        val board = buildTodayBoard(listOf(daily, weekly), history, today)

        assertFalse(board.recurring.first { it.quest.id.value == "d" }.completed)
        assertTrue(board.recurring.first { it.quest.id.value == "w" }.completed)
    }

    @Test
    fun `doneForToday when every recurring quest is completed for its period`() {
        val daily = recurringQuest(id = "d")
        val weekly = recurringQuest(id = "w", title = "Long run", cadence = Cadence.Weekly)
        val history = completions(daily, today) + completions(weekly, date("2026-07-14"))

        val board = buildTodayBoard(listOf(daily, weekly), history, today)

        assertTrue(board.doneForToday)
    }

    @Test
    fun `open side quests never block doneForToday`() {
        val daily = recurringQuest(id = "d")
        val side = sideQuest()
        val history = completions(daily, today)

        val board = buildTodayBoard(listOf(daily, side), history, today)

        assertTrue(board.doneForToday)
        assertEquals(1, board.sideQuests.size)
        assertFalse(board.sideQuests.single().completed)
    }

    @Test
    fun `an empty board is an empty state - not done`() {
        val board = buildTodayBoard(emptyList(), emptyList(), today)

        assertFalse(board.doneForToday)
        assertTrue(board.recurring.isEmpty())
    }

    @Test
    fun `side quests completed today stay visible - completed earlier disappear`() {
        val doneToday = sideQuest(id = "s1", title = "Call plumber")
        val doneLastWeek = sideQuest(id = "s2", title = "Renew rego")
        val history = completions(doneToday, today) + completions(doneLastWeek, today.minusDays(7))

        val board = buildTodayBoard(listOf(doneToday, doneLastWeek), history, today)

        assertEquals(listOf("s1"), board.sideQuests.map { it.quest.id.value })
        assertTrue(board.sideQuests.single().completed)
    }

    @Test
    fun `retired quests are off the board`() {
        val retired = recurringQuest(id = "r", status = QuestStatus.Retired)
        val retiredSide = sideQuest(id = "rs", status = QuestStatus.Retired)

        val board = buildTodayBoard(listOf(retired, retiredSide), emptyList(), today)

        assertTrue(board.recurring.isEmpty())
        assertTrue(board.sideQuests.isEmpty())
    }
}
