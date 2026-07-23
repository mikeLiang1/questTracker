package com.mikeliang.questtracker.core.onboarding

import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class StartingClassesTest {

    private val createdAt = Instant.parse("2026-07-17T10:00:00Z")

    /** Deterministic id generator: q0, q1, q2… */
    private fun countingIds(): () -> QuestId {
        var next = 0
        return { QuestId("q${next++}") }
    }

    @ParameterizedTest
    @EnumSource(StartingClass::class)
    fun `every class yields four active Maintenance recurring quests`(startingClass: StartingClass) {
        val loadout = startingClass.questLoadout(createdAt, countingIds())

        assertEquals(4, loadout.size)
        loadout.forEach { quest ->
            val kind = quest.kind
            assertTrue(kind is QuestKind.Recurring) { "${quest.title} must be recurring" }
            kind as QuestKind.Recurring
            assertEquals(QuestType.Maintenance, kind.type) { "${quest.title} must be Maintenance" }
            assertNull(kind.progression) { "${quest.title} must not carry a progression target" }
            assertEquals(QuestStatus.Active, quest.status)
        }
    }

    @ParameterizedTest
    @EnumSource(StartingClass::class)
    fun `no preset quest carries a reminder — reminder times are always user-chosen`(startingClass: StartingClass) {
        startingClass.questLoadout(createdAt, countingIds()).forEach { quest ->
            assertNull(quest.reminder) { "${quest.title} must not invent a reminder time" }
        }
    }

    @ParameterizedTest
    @EnumSource(StartingClass::class)
    fun `every class has exactly one auto-tracked steps quest`(startingClass: StartingClass) {
        val loadout = startingClass.questLoadout(createdAt, countingIds())

        val stepsQuests = loadout.filter { it.autoTracking?.metric == HealthMetric.Steps }
        assertEquals(1, stepsQuests.size)
        val expectedTarget = when (startingClass) {
            StartingClass.Warrior -> 8_000.0
            StartingClass.Sage -> 6_000.0
            StartingClass.Adventurer -> 7_000.0
        }
        assertEquals(expectedTarget, stepsQuests.single().autoTracking?.dailyTarget)
    }

    @ParameterizedTest
    @EnumSource(StartingClass::class)
    fun `createdAt is propagated and ids come from the generator, unique`(startingClass: StartingClass) {
        val loadout = startingClass.questLoadout(createdAt, countingIds())

        loadout.forEach { assertEquals(createdAt, it.createdAt) }
        assertEquals(listOf("q0", "q1", "q2", "q3"), loadout.map { it.id.value })
    }

    @Test
    fun `Warrior loadout is Body-focused with an auto-tracked sleep quest`() {
        val loadout = StartingClass.Warrior.questLoadout(createdAt, countingIds())

        assertEquals(
            listOf("Walk 8,000 steps", "Train or stretch", "Sleep 7+ hours", "Plan tomorrow"),
            loadout.map { it.title },
        )
        assertEquals(
            listOf(Attribute.Body, Attribute.Body, Attribute.Body, Attribute.Discipline),
            loadout.map { (it.kind as QuestKind.Recurring).attribute },
        )
        assertTrue(loadout.all { (it.kind as QuestKind.Recurring).cadence == Cadence.Daily })
        val sleep = loadout.single { it.title == "Sleep 7+ hours" }
        assertEquals(HealthMetric.SleepMinutes, sleep.autoTracking?.metric)
        assertEquals(420.0, sleep.autoTracking?.dailyTarget)
    }

    @Test
    fun `Sage loadout is Mind-focused with a weekly deep-work block`() {
        val loadout = StartingClass.Sage.questLoadout(createdAt, countingIds())

        assertEquals(
            listOf("Read 20 minutes", "One line of journal", "Walk 6,000 steps", "One deep-work block"),
            loadout.map { it.title },
        )
        assertEquals(
            listOf(Attribute.Mind, Attribute.Mind, Attribute.Body, Attribute.Discipline),
            loadout.map { (it.kind as QuestKind.Recurring).attribute },
        )
        val deepWork = loadout.single { it.title == "One deep-work block" }
        assertEquals(Cadence.Weekly, (deepWork.kind as QuestKind.Recurring).cadence)
    }

    @Test
    fun `Adventurer loadout touches four attributes with a weekly social quest`() {
        val loadout = StartingClass.Adventurer.questLoadout(createdAt, countingIds())

        assertEquals(
            listOf("Walk 7,000 steps", "Read 15 minutes", "Reach out to someone", "Plan tomorrow"),
            loadout.map { it.title },
        )
        assertEquals(
            listOf(Attribute.Body, Attribute.Mind, Attribute.Social, Attribute.Discipline),
            loadout.map { (it.kind as QuestKind.Recurring).attribute },
        )
        val reachOut = loadout.single { it.title == "Reach out to someone" }
        assertEquals(Cadence.Weekly, (reachOut.kind as QuestKind.Recurring).cadence)
    }
}
