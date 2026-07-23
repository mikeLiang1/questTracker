package com.mikeliang.questtracker.ui.questlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.core.repository.JournalRepository
import com.mikeliang.questtracker.core.repository.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Presents the Quest Log timeline and routes journal intents. Deliberately thin:
 * interleaving/grouping and journal-linked completion crediting live in
 * [QuestEngine] — this class snapshots repository state, calls the engine, and
 * persists what comes back. The one law it owns operationally: deleting or editing
 * an entry only ever touches [JournalRepository]; completions are never revisited.
 */
@HiltViewModel
class QuestLogViewModel @Inject constructor(
    private val questRepository: QuestRepository,
    private val journalRepository: JournalRepository,
    private val engine: QuestEngine,
    private val clock: Clock,
) : ViewModel() {

    private val feedback = MutableStateFlow<QuestLogFeedback?>(null)

    val uiState: StateFlow<QuestLogUiState> =
        combine(
            questRepository.observeQuests(),
            questRepository.observeCompletions(),
            journalRepository.observeEntries(),
            feedback,
        ) { quests, completions, entries, pendingFeedback ->
            QuestLogUiState(
                days = engine.questLog(quests, completions, entries),
                today = clock.today(),
                feedback = pendingFeedback,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QuestLogUiState(loading = true),
        )

    fun onEvent(event: QuestLogEvent) {
        when (event) {
            is QuestLogEvent.SaveEntry -> saveEntry(event.text)
            is QuestLogEvent.EditEntry -> editEntry(event.id, event.text)
            is QuestLogEvent.DeleteEntry -> deleteEntry(event.id)
            QuestLogEvent.FeedbackShown -> feedback.value = null
        }
    }

    private fun saveEntry(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            journalRepository.upsertEntry(
                JournalEntry(
                    id = JournalEntryId(UUID.randomUUID().toString()),
                    text = trimmed,
                    createdAt = clock.now(),
                    entryDate = clock.today(),
                )
            )
            val quests = questRepository.observeQuests().first()
            val completions = questRepository.observeCompletions().first()
            val result = engine.completeFromJournalEntry(quests, completions)
            result.records.forEach { questRepository.recordCompletion(it) }
            feedback.value = result.feedback
                ?.let { QuestLogFeedback.QuestCompleted(it) }
                ?: QuestLogFeedback.EntrySaved
        }
    }

    private fun editEntry(id: JournalEntryId, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = journalRepository.getEntry(id) ?: return@launch
            journalRepository.upsertEntry(existing.copy(text = trimmed, editedAt = clock.now()))
            feedback.value = QuestLogFeedback.EntrySaved
        }
    }

    private fun deleteEntry(id: JournalEntryId) {
        viewModelScope.launch {
            journalRepository.deleteEntry(id)
        }
    }
}
