package com.mikeliang.questtracker.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.engine.escalate
import com.mikeliang.questtracker.core.engine.retireQuest
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.reflection.ReflectionStateStore
import com.mikeliang.questtracker.ui.questlist.questFromQuickAdd
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The 90-second monthly reflection. Thin like every ViewModel here: the trajectory
 * summary comes from :core ([QuestEngine.reflection]) and every outcome goes through
 * a :core use-case (`retireQuest`, `escalate`, or the shared quick-add shaping) —
 * the reflection UI never mutates state directly.
 *
 * The summary is a snapshot taken when the flow opens, not a live stream: rows
 * reshuffling mid-ritual (because an add landed, say) would fight the one-question
 * shape of the flow.
 */
@HiltViewModel
class ReflectionViewModel @Inject constructor(
    private val repository: QuestRepository,
    private val engine: QuestEngine,
    private val stateStore: ReflectionStateStore,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReflectionUiState(loading = true))
    val uiState: StateFlow<ReflectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val quests = repository.observeQuests().first()
            val completions = repository.observeCompletions().first()
            val summary = engine.reflection(quests, completions)
            _uiState.value = ReflectionUiState(
                month = YearMonth.from(summary.month.start),
                rows = summary.quests.map { trajectory ->
                    ReflectionUiState.Row(
                        quest = trajectory.quest,
                        completionsInMonth = trajectory.completionsInMonth,
                        consistencyRate = trajectory.consistency.rate,
                        escalatedInMonth = trajectory.escalatedInMonth,
                        barelyMoved = trajectory.quest.id == summary.barelyMovedQuestId,
                    )
                },
                attributeGains = summary.attributeGains,
            )
        }
    }

    fun onEvent(event: ReflectionEvent) {
        when (event) {
            is ReflectionEvent.Choose -> _uiState.update { state ->
                state.copy(
                    rows = state.rows.map { row ->
                        if (row.quest.id == event.id) row.copy(choice = event.choice) else row
                    }
                )
            }

            is ReflectionEvent.AddQuest -> addQuest(event)
            ReflectionEvent.Complete -> finish(applyChoices = true)
            ReflectionEvent.Skip -> finish(applyChoices = false)
        }
    }

    /**
     * Adds apply immediately — a new quest is a commitment made, not a pending edit,
     * and it should survive even if the user then backs out of the rest.
     */
    private fun addQuest(event: ReflectionEvent.AddQuest) {
        val quest = questFromQuickAdd(event.event, clock) ?: return
        viewModelScope.launch {
            repository.upsertQuest(quest)
            _uiState.update { it.copy(addedQuestTitles = it.addedQuestTitles + quest.title) }
        }
    }

    private fun finish(applyChoices: Boolean) {
        viewModelScope.launch {
            if (applyChoices) {
                for (row in _uiState.value.rows) {
                    // Re-fetch each quest: a completion or edit may have landed since
                    // the snapshot, and the :core functions validate against current
                    // state (escalate requires an active progression target).
                    val quest = repository.getQuest(row.quest.id) ?: continue
                    when (val choice = row.choice) {
                        ReflectionChoice.Keep -> Unit
                        ReflectionChoice.Retire ->
                            repository.upsertQuest(retireQuest(quest))
                        is ReflectionChoice.Escalate ->
                            if (choice.newAmount > 0 && row.canEscalate) {
                                repository.upsertQuest(escalate(quest, choice.newAmount))
                            }
                    }
                }
            }
            // Skipped or completed, the month is handled either way — the banner
            // drops and re-arms next month. Skipping costs nothing, ever.
            stateStore.markHandled(YearMonth.from(clock.today()))
            _uiState.update { it.copy(closed = true) }
        }
    }
}
