package com.mikeliang.questtracker.ui.questlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.CompletionFeedback
import com.mikeliang.questtracker.core.engine.CompletionOutcome
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.engine.TodayBoard
import com.mikeliang.questtracker.core.engine.UnclearOutcome
import com.mikeliang.questtracker.core.health.HealthDataSource
import com.mikeliang.questtracker.core.health.HealthReading
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.reflection.ReflectionStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
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
    reflectionStateStore: ReflectionStateStore,
) : ViewModel() {

    private val feedback = MutableStateFlow<CompletionFeedback?>(null)

    /**
     * Whether to surface the monthly-reflection banner. Reactive on both inputs:
     * completions crossing a month boundary arm it, marking the month handled
     * (finishing or skipping the flow) drops it immediately.
     */
    private val reflectionDue: Flow<Boolean> = combine(
        repository.observeCompletions(),
        reflectionStateStore.lastHandledMonth(),
    ) { completions, lastHandled -> engine.reflectionDue(completions, lastHandled) }

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
        combine(boardWithReadings, feedback, reflectionDue) { (current, readings), pendingFeedback, dueForReflection ->
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
                        undoable = due.undoable,
                    )
                },
                sideQuests = current.sideQuests.map {
                    QuestListUiState.SideQuestItem(it.quest, it.completed, undoable = it.undoable)
                },
                doneForToday = current.doneForToday,
                feedback = pendingFeedback,
                reflectionDue = dueForReflection,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QuestListUiState(loading = true),
        )

    fun onEvent(event: QuestListEvent) {
        when (event) {
            is QuestListEvent.CompleteQuest -> completeQuest(event.id)
            is QuestListEvent.UnclearQuest -> unclearQuest(event.id)
            is QuestListEvent.AddSideQuest,
            is QuestListEvent.AddRecurringQuest -> addQuest(event)
            QuestListEvent.FeedbackShown -> feedback.value = null
        }
    }

    private fun addQuest(event: QuestListEvent) {
        val quest = questFromQuickAdd(event, clock) ?: return
        viewModelScope.launch { repository.upsertQuest(quest) }
    }

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

    private fun unclearQuest(id: QuestId) {
        viewModelScope.launch {
            val quests = repository.observeQuests().first()
            val completions = repository.observeCompletions().first()
            val quest = quests.firstOrNull { it.id == id } ?: return@launch
            when (val outcome = engine.unclear(quest, completions)) {
                // Silent revert: the row re-opening is the feedback. No copy — undoing
                // a mis-tap is not an achievement and not a failure.
                is UnclearOutcome.Uncleared -> repository.deleteCompletion(outcome.record)
                UnclearOutcome.NotUndoable -> Unit
            }
        }
    }

}
