package com.mikeliang.questtracker.ui.questlist

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.health.HealthDataSource
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.health.HealthReading
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.reflection.ReflectionStateStore
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FixedClock(
    private val instant: Instant = Instant.parse("2026-07-17T10:00:00Z"),
    private val zoneId: ZoneId = ZoneId.of("UTC"),
) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

class FakeQuestRepository : QuestRepository {

    private val quests = MutableStateFlow<List<Quest>>(emptyList())
    private val completions = MutableStateFlow<List<CompletionRecord>>(emptyList())

    override fun observeQuests(): Flow<List<Quest>> = quests.asStateFlow()

    override fun observeCompletions(): Flow<List<CompletionRecord>> = completions.asStateFlow()

    override suspend fun getQuest(id: QuestId): Quest? = quests.value.firstOrNull { it.id == id }

    override suspend fun upsertQuest(quest: Quest) {
        quests.update { list -> list.filterNot { it.id == quest.id } + quest }
    }

    override suspend fun deleteQuest(id: QuestId) {
        quests.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun recordCompletion(record: CompletionRecord) {
        completions.update { it + record }
    }

    override suspend fun deleteCompletion(record: CompletionRecord) {
        completions.update { list ->
            list.filterNot { it.questId == record.questId && it.completedAt == record.completedAt }
        }
    }

    override suspend fun completionsFor(questId: QuestId): List<CompletionRecord> =
        completions.value.filter { it.questId == questId }

    override suspend fun completionsInRange(from: LocalDate, to: LocalDate): List<CompletionRecord> =
        completions.value.filter { it.periodStart in from..to }

    val recordedCompletions: List<CompletionRecord> get() = completions.value
    val storedQuests: List<Quest> get() = quests.value

    suspend fun seed(vararg seeded: Quest) = seeded.forEach { upsertQuest(it) }
}

/** In-memory reflection store; also used by the reflection ViewModel's own tests. */
class FakeReflectionStateStore : ReflectionStateStore {

    private val handled = MutableStateFlow<YearMonth?>(null)

    override fun lastHandledMonth(): Flow<YearMonth?> = handled.asStateFlow()

    override suspend fun markHandled(month: YearMonth) {
        handled.value = month
    }

    val handledMonth: YearMonth? get() = handled.value

    fun seed(month: YearMonth?) {
        handled.value = month
    }
}

/** Health source whose "today" reading the test scripts directly. */
class ScriptedHealthSource : HealthDataSource {

    val today = MutableStateFlow<HealthReading>(HealthReading.Unavailable)

    override suspend fun readDay(metric: HealthMetric, date: LocalDate): HealthReading =
        HealthReading.Unavailable

    override fun observeToday(metric: HealthMetric): Flow<HealthReading> = today.asStateFlow()
}
