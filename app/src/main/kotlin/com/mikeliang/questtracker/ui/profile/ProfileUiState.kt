package com.mikeliang.questtracker.ui.profile

import com.mikeliang.questtracker.core.engine.AttributeCard
import com.mikeliang.questtracker.core.engine.CompletedChapter

/**
 * Everything the profile screen renders, as one immutable value. All content comes
 * from [com.mikeliang.questtracker.core.engine.ProfileSummary] — the ViewModel and
 * UI never compute milestone math or invent progression copy. The screen is
 * read-only, so there are no events beyond navigation (owned by MainActivity).
 */
data class ProfileUiState(
    val loading: Boolean = false,
    /** The four attribute cards, always all present, in [Attribute] order. */
    val attributes: List<AttributeCard> = emptyList(),
    /** Total completions ever — the headline number, because it can never shrink. */
    val lifetimeCompletions: Int = 0,
    /** The completed-chapters archive: retired quests and what they banked. */
    val chapters: List<CompletedChapter> = emptyList(),
)
