package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.ZoneOffset
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

        val board = buildTodayBoard(listOf(monthly, weekly, daily), emptyList(), today, ZoneOffset.UTC)

        assertEquals(listOf("d", "w", "m"), board.recurring.map { it.quest.id.value })
    }

    @Test
    fun `completion state reflects the current period per cadence`() {
        val daily = recurringQuest(id = "d", cadence = Cadence.Daily)
        val weekly = recurringQuest(id = "w", title = "Long run", cadence = Cadence.Weekly)
        // Daily done yesterday (stale), weekly done Monday (current week counts).
        val history = completions(daily, today.minusDays(1)) + completions(weekly, date("2026-07-13"))

        val board = buildTodayBoard(listOf(daily, weekly), history, today, ZoneOffset.UTC)

        assertFalse(board.recurring.first { it.quest.id.value == "d" }.completed)
        assertTrue(board.recurring.first { it.quest.id.value == "w" }.completed)
    }

    @Test
    fun `doneForToday when every recurring quest is completed for its period`() {
        val daily = recurringQuest(id = "d")
        val weekly = recurringQuest(id = "w", title = "Long run", cadence = Cadence.Weekly)
        val history = completions(daily, today) + completions(weekly, date("2026-07-14"))

        val board = buildTodayBoard(listOf(daily, weekly), history, today, ZoneOffset.UTC)

        assertTrue(board.doneForToday)
    }

    @Test
    fun `open side quests never block doneForToday`() {
        val daily = recurringQuest(id = "d")
        val side = sideQuest()
        val history = completions(daily, today)

        val board = buildTodayBoard(listOf(daily, side), history, today, ZoneOffset.UTC)

        assertTrue(board.doneForToday)
        assertEquals(1, board.sideQuests.size)
        assertFalse(board.sideQuests.single().completed)
    }

    @Test
    fun `an empty board is an empty state - not done`() {
        val board = buildTodayBoard(emptyList(), emptyList(), today, ZoneOffset.UTC)

        assertFalse(board.doneForToday)
        assertTrue(board.recurring.isEmpty())
    }

    @Test
    fun `side quests completed today stay visible - completed earlier disappear`() {
        val doneToday = sideQuest(id = "s1", title = "Call plumber")
        val doneLastWeek = sideQuest(id = "s2", title = "Renew rego")
        val history = completions(doneToday, today) + completions(doneLastWeek, today.minusDays(7))

        val board = buildTodayBoard(listOf(doneToday, doneLastWeek), history, today, ZoneOffset.UTC)

        assertEquals(listOf("s1"), board.sideQuests.map { it.quest.id.value })
        assertTrue(board.sideQuests.single().completed)
    }

    @Test
    fun `completions banked today are undoable - earlier in the period are locked`() {
        val daily = recurringQuest(id = "d")
        val weekly = recurringQuest(id = "w", title = "Long run", cadence = Cadence.Weekly)
        // Daily ticked today (mis-tap window open); weekly banked Monday (locked).
        val history = completions(daily, today) + completions(weekly, date("2026-07-13"))

        val board = buildTodayBoard(listOf(daily, weekly), history, today, ZoneOffset.UTC)

        assertTrue(board.recurring.first { it.quest.id.value == "d" }.undoable)
        val lockedWeekly = board.recurring.first { it.quest.id.value == "w" }
        assertTrue(lockedWeekly.completed)
        assertFalse(lockedWeekly.undoable)
    }

    @Test
    fun `auto-tracked completions are never undoable`() {
        val daily = recurringQuest(id = "d")
        val history = listOf(
            completion(daily, today, source = CompletionSource.AutoTracked),
        )

        val board = buildTodayBoard(listOf(daily), history, today, ZoneOffset.UTC)

        assertTrue(board.recurring.single().completed)
        assertFalse(board.recurring.single().undoable)
    }

    @Test
    fun `a side quest ticked today is undoable`() {
        val side = sideQuest()
        val history = completions(side, today)

        val board = buildTodayBoard(listOf(side), history, today, ZoneOffset.UTC)

        assertTrue(board.sideQuests.single().undoable)
    }

    @Test
    fun `retired quests are off the board`() {
        val retired = recurringQuest(id = "r", status = QuestStatus.Retired)
        val retiredSide = sideQuest(id = "rs", status = QuestStatus.Retired)

        val board = buildTodayBoard(listOf(retired, retiredSide), emptyList(), today, ZoneOffset.UTC)

        assertTrue(board.recurring.isEmpty())
        assertTrue(board.sideQuests.isEmpty())
    }
}
