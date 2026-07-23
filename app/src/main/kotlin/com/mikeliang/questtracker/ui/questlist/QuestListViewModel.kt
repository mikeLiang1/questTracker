package com.mikeliang.questtracker.ui.questlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.CompletionFeedback
import com.mikeliang.questtracker.core.engine.CompletionOutcome
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.engine.TodayBoard
import com.mikeliang.questtracker.core.health.HealthDataSource
import com.mikeliang.questtracker.core.health.HealthReading
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.core.repository.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Presents [TodayBoard] and routes user intents to the :core engine. Deliberately
 * thin: board building, completion crediting, dedupe, and feedback copy all live in
 * [QuestEngine] — this class only snapshots repository state, calls the engine, and
 * persists what comes back.
 */
@HiltViewModel
class QuestListViewModel @Inject constructor(
    private val repository: QuestRepository,
    private val engine: QuestEngine,
    private val healthSource: HealthDataSource,
    private val clock: Clock,
) : ViewModel() {

    private val feedback = MutableStateFlow<CompletionFeedback?>(null)

    private val board: Flow<TodayBoard> = combine(
        repository.observeQuests(),
        repository.observeCompletions(),
    ) { quests, completions -> engine.todayBoard(quests, completions) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val boardWithReadings: Flow<Pair<TodayBoard, Map<QuestId, HealthReading>>> =
        board.flatMapLatest { current ->
            val tracked = current.recurring.mapNotNull { due ->
                due.quest.autoTracking?.let { auto -> due.quest.id to auto.metric }
            }
            if (tracked.isEmpty()) {
                flowOf(current to emptyMap())
            } else {
                combine(
                    tracked.map { (id, metric) ->
                        healthSource.observeToday(metric).map { reading -> id to reading }
                    }
                ) { readings -> current to readings.toMap() }
            }
        }

    val uiState: StateFlow<QuestListUiState> =
        combine(boardWithReadings, feedback) { (current, readings), pendingFeedback ->
            QuestListUiState(
                recurring = current.recurring.map { due ->
                    QuestListUiState.RecurringItem(
                        quest = due.quest,
                        completed = due.completed,
                        progress = due.quest.autoTracking?.let { auto ->
                            QuestListUiState.AutoProgress(
                                metric = auto.metric,
                                current = (readings[due.quest.id] as? HealthReading.Available)?.value,
                                target = auto.dailyTarget,
                            )
                        },
                    )
                },
                sideQuests = current.sideQuests.map {
                    QuestListUiState.SideQuestItem(it.quest, it.completed)
                },
                doneForToday = current.doneForToday,
                feedback = pendingFeedback,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QuestListUiState(loading = true),
        )

    fun onEvent(event: QuestListEvent) {
        when (event) {
            is QuestListEvent.CompleteQuest -> completeQuest(event.id)
            is QuestListEvent.AddSideQuest -> addSideQuest(event)
            is QuestListEvent.AddRecurringQuest -> addRecurringQuest(event)
            QuestListEvent.FeedbackShown -> feedback.value = null
        }
    }

    private fun addSideQuest(event: QuestListEvent.AddSideQuest) {
        val title = event.title.trim()
        if (title.isEmpty()) return
        viewModelScope.launch {
            repository.upsertQuest(
                Quest(
                    id = newQuestId(),
                    title = title,
                    kind = QuestKind.SideQuest,
                    createdAt = clock.now(),
                    reminder = event.reminderTime?.let {
                        ReminderSchedule.OneShot(at = nextOccurrenceOf(it))
                    },
                )
            )
        }
    }

    private fun addRecurringQuest(event: QuestListEvent.AddRecurringQuest) {
        val title = event.title.trim()
        if (title.isEmpty()) return
        viewModelScope.launch {
            repository.upsertQuest(
                Quest(
                    id = newQuestId(),
                    title = title,
                    // Quick-add captures Maintenance quests only; progression targets
                    // are added from the quest detail screen, not a 5-second sheet.
                    kind = QuestKind.Recurring(
                        cadence = event.cadence,
                        type = QuestType.Maintenance,
                        attribute = event.attribute,
                        journalLinked = event.journalLinked,
                    ),
                    createdAt = clock.now(),
                    reminder = event.reminderTime?.let { reminderScheduleFor(event.cadence, it) },
                )
            )
        }
    }

    /**
     * Quick-added recurring reminders: dailies nudge every day; weeklies and monthlies
     * nudge once a week on the day the quest was created — the sheet has no day picker,
     * and a weekly nudge is the least-surprising default. Editable from the quest's
     * detail screen.
     */
    private fun reminderScheduleFor(cadence: Cadence, time: LocalTime): ReminderSchedule =
        ReminderSchedule.Recurring(
            time = time,
            days = when (cadence) {
                Cadence.Daily -> DayOfWeek.entries.toSet()
                Cadence.Weekly, Cadence.Monthly -> setOf(clock.today().dayOfWeek)
            },
        )

    private fun newQuestId(): QuestId = QuestId(UUID.randomUUID().toString())

    private fun completeQuest(id: QuestId) {
        viewModelScope.launch {
            val quests = repository.observeQuests().first()
            val completions = repository.observeCompletions().first()
            val quest = quests.firstOrNull { it.id == id } ?: return@launch
            when (val outcome = engine.complete(quest, quests, completions, CompletionSource.Manual)) {
                is CompletionOutcome.Completed -> {
                    repository.recordCompletion(outcome.record)
                    feedback.value = outcome.feedback
                }

                CompletionOutcome.AlreadyCompleted -> Unit
            }
        }
    }

    /**
     * A quick-added reminder means "next time it's this o'clock" — today if the time
     * is still ahead, tomorrow otherwise. Input shaping, not engine logic.
     */
    private fun nextOccurrenceOf(time: LocalTime): LocalDateTime {
        val now = LocalDateTime.ofInstant(clock.now(), clock.zone())
        val todayAt = now.toLocalDate().atTime(time)
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }
}
