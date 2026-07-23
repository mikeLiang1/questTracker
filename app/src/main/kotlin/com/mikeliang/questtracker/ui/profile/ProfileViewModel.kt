package com.mikeliang.questtracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.repository.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Presents the profile summary. Deliberately thin: every number and every line of
 * copy comes from [QuestEngine.profile] — this class only snapshots repository
 * state and reshapes the result for the screen.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    repository: QuestRepository,
    engine: QuestEngine,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.observeQuests(),
        repository.observeCompletions(),
    ) { quests, completions ->
        val summary = engine.profile(quests, completions)
        ProfileUiState(
            attributes = summary.attributes,
            lifetimeCompletions = summary.lifetimeCompletions,
            chapters = summary.chapters,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(loading = true),
    )
}
