package com.mikeliang.questtracker.health

import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.AutoTracking
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthReconcilerTest {

    private val now = Instant.parse("2026-07-16T10:00:00Z")
    private val today = LocalDate.parse("2026-07-16") // Thursday
    private val yesterday = today.minusDays(1)
    private val twoDaysAgo = today.minusDays(2)

    private val healthSource = FakeHealthDataSource()
    private val repository = FakeQuestRepository()
    private val clock = FakeClock(now)
    private val reconciler = HealthReconciler(healthSource, repository, clock)

    private fun autoQuest(
        id: String = "steps",
        cadence: Cadence = Cadence.Daily,
        metric: HealthMetric = HealthMetric.Steps,
        target: Double = 8_000.0,
        status: QuestStatus = QuestStatus.Active,
    ): Quest = Quest(
        id = QuestId(id),
        title = "Walk it off",
        kind = QuestKind.Recurring(cadence, QuestType.Maintenance, Attribute.Body),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        status = status,
        autoTracking = AutoTracking(metric, target),
    )

    private fun manualCompletion(quest: Quest, date: LocalDate) = CompletionRecord(
        questId = quest.id,
        completedAt = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
        periodStart = date,
        source = CompletionSource.Manual,
    )

    @Test
    fun `banks an auto-tracked completion when today hits target`() = runTest {
        repository.seedQuests(autoQuest())
        healthSource.script(HealthMetric.Steps, today, 9_100.0)

        reconciler.reconcile()

        val record = repository.recordedCompletions.single()
        assertEquals(today, record.periodStart)
        assertEquals(CompletionSource.AutoTracked, record.source)
        assertEquals(now, record.completedAt)
    }

    @Test
    fun `backfills late-arriving data inside the 48h window`() = runTest {
        repository.seedQuests(autoQuest())
        healthSource.script(HealthMetric.Steps, twoDaysAgo, 8_500.0)

        reconciler.reconcile()

        assertEquals(twoDaysAgo, repository.recordedCompletions.single().periodStart)
    }

    @Test
    fun `below-target and unavailable days record nothing`() = runTest {
        repository.seedQuests(autoQuest())
        healthSource.script(HealthMetric.Steps, today, 4_000.0)
        // yesterday and twoDaysAgo have no scripted reading: Unavailable.

        reconciler.reconcile()

        assertTrue(repository.recordedCompletions.isEmpty())
    }

    @Test
    fun `reconciling twice is idempotent`() = runTest {
        repository.seedQuests(autoQuest())
        healthSource.script(HealthMetric.Steps, today, 9_100.0)

        reconciler.reconcile()
        reconciler.reconcile()

        assertEquals(1, repository.recordedCompletions.size)
    }

    @Test
    fun `an already banked period is not even read again`() = runTest {
        val quest = autoQuest()
        repository.seedQuests(quest)
        repository.seedCompletions(manualCompletion(quest, today))
        healthSource.script(HealthMetric.Steps, today, 9_100.0)

        reconciler.reconcile()

        assertTrue(healthSource.readDayCalls.none { it.second == today })
        assertEquals(1, repository.recordedCompletions.size) // just the seeded manual one
    }

    @Test
    fun `a weekly quest credits its period once across the window`() = runTest {
        repository.seedQuests(autoQuest(cadence = Cadence.Weekly))
        // Tue, Wed, Thu of the same ISO week all hit target.
        healthSource.script(HealthMetric.Steps, twoDaysAgo, 9_000.0)
        healthSource.script(HealthMetric.Steps, yesterday, 9_000.0)
        healthSource.script(HealthMetric.Steps, today, 9_000.0)

        reconciler.reconcile()

        val record = repository.recordedCompletions.single()
        assertEquals(LocalDate.parse("2026-07-13"), record.periodStart) // that week's Monday
    }

    @Test
    fun `retired and manual-only quests are never read`() = runTest {
        repository.seedQuests(
            autoQuest(id = "retired", status = QuestStatus.Retired),
            Quest(
                id = QuestId("manual"),
                title = "Gym hour",
                kind = QuestKind.Recurring(Cadence.Daily, QuestType.Maintenance, Attribute.Body),
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )
        healthSource.script(HealthMetric.Steps, today, 99_999.0)

        reconciler.reconcile()

        assertTrue(healthSource.readDayCalls.isEmpty())
        assertTrue(repository.recordedCompletions.isEmpty())
    }

    @Test
    fun `each quest reads its own metric`() = runTest {
        repository.seedQuests(
            autoQuest(id = "steps", metric = HealthMetric.Steps, target = 8_000.0),
            autoQuest(id = "sleep", metric = HealthMetric.SleepMinutes, target = 420.0),
        )
        healthSource.script(HealthMetric.Steps, today, 9_000.0)
        healthSource.script(HealthMetric.SleepMinutes, today, 450.0)

        reconciler.reconcile()

        assertEquals(
            setOf("steps", "sleep"),
            repository.recordedCompletions.map { it.questId.value }.toSet(),
        )
    }

    @Test
    fun `existing completions are never removed or altered`() = runTest {
        val quest = autoQuest()
        val seeded = manualCompletion(quest, LocalDate.parse("2026-07-01"))
        repository.seedQuests(quest)
        repository.seedCompletions(seeded)
        healthSource.script(HealthMetric.Steps, today, 9_100.0)

        reconciler.reconcile()

        assertTrue(seeded in repository.recordedCompletions)
        assertEquals(2, repository.recordedCompletions.size)
    }
}
