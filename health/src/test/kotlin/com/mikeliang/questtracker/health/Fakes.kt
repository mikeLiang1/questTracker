package com.mikeliang.questtracker.health

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.health.HealthDataSource
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.health.HealthReading
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeClock(
    var instant: Instant,
    var zoneId: ZoneId = ZoneOffset.UTC,
) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

/** Scripted [HealthConnectApi]; records the ranges it was asked to aggregate. */
class FakeHealthConnectApi : HealthConnectApi {

    override val readPermissions: Set<String> =
        setOf("android.permission.health.READ_STEPS", "android.permission.health.READ_SLEEP")

    var availability: HealthConnectAvailability = HealthConnectAvailability.Installed
    var granted: Set<String> = readPermissions
    var grantedPermissionsError: Exception? = null

    /** Scripted aggregate result per metric; null means "provider has no data". */
    val totals = mutableMapOf<HealthMetric, Double?>()
    var aggregateError: Exception? = null

    data class AggregateCall(val metric: HealthMetric, val start: Instant, val end: Instant)
    val aggregateCalls = mutableListOf<AggregateCall>()

    override fun availability(): HealthConnectAvailability = availability

    override suspend fun grantedPermissions(): Set<String> {
        grantedPermissionsError?.let { throw it }
        return granted
    }

    override suspend fun aggregate(metric: HealthMetric, start: Instant, end: Instant): Double? {
        aggregateCalls += AggregateCall(metric, start, end)
        aggregateError?.let { throw it }
        return totals[metric]
    }
}

/** Scripted [HealthDataSource] keyed by (metric, date); absent entries are Unavailable. */
class FakeHealthDataSource : HealthDataSource {

    val readings = mutableMapOf<Pair<HealthMetric, LocalDate>, HealthReading>()
    val readDayCalls = mutableListOf<Pair<HealthMetric, LocalDate>>()

    fun script(metric: HealthMetric, date: LocalDate, value: Double) {
        readings[metric to date] = HealthReading.Available(value)
    }

    override suspend fun readDay(metric: HealthMetric, date: LocalDate): HealthReading {
        readDayCalls += metric to date
        return readings[metric to date] ?: HealthReading.Unavailable
    }

    override fun observeToday(metric: HealthMetric): Flow<HealthReading> =
        flowOf(HealthReading.Unavailable)
}

/** In-memory [QuestRepository]. Completions append-only bar the mis-tap undo, like the real one. */
class FakeQuestRepository : com.mikeliang.questtracker.core.repository.QuestRepository {

    private val quests = MutableStateFlow<List<Quest>>(emptyList())
    private val completions = MutableStateFlow<List<CompletionRecord>>(emptyList())

    val recordedCompletions: List<CompletionRecord> get() = completions.value

    fun seedQuests(vararg seed: Quest) {
        quests.value = seed.toList()
    }

    fun seedCompletions(vararg seed: CompletionRecord) {
        completions.value = seed.toList()
    }

    override fun observeQuests(): Flow<List<Quest>> = quests

    override fun observeCompletions(): Flow<List<CompletionRecord>> = completions

    override suspend fun getQuest(id: QuestId): Quest? = quests.value.firstOrNull { it.id == id }

    override suspend fun upsertQuest(quest: Quest) {
        quests.value = quests.value.filterNot { it.id == quest.id } + quest
    }

    override suspend fun deleteQuest(id: QuestId) {
        quests.value = quests.value.filterNot { it.id == id }
    }

    override suspend fun recordCompletion(record: CompletionRecord) {
        completions.value = completions.value + record
    }

    override suspend fun deleteCompletion(record: CompletionRecord) {
        completions.value = completions.value.filterNot {
            it.questId == record.questId && it.completedAt == record.completedAt
        }
    }

    override suspend fun completionsFor(questId: QuestId): List<CompletionRecord> =
        completions.value.filter { it.questId == questId }

    override suspend fun completionsInRange(from: LocalDate, to: LocalDate): List<CompletionRecord> =
        completions.value.filter { it.periodStart in from..to }
}
