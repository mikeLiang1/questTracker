package com.mikeliang.questtracker.core.model

import java.time.Instant
import java.time.LocalDate

/** Stable identifier for a journal entry. */
@JvmInline
value class JournalEntryId(val value: String)

/**
 * A free-text entry the user wrote — gratitude, a log line, anything. Entries are the
 * user's words, not banked gains: unlike completions they may be edited and deleted.
 * A [CompletionRecord] banked because an entry was saved is an ordinary append-only
 * completion with no reference back to the entry — editing or deleting the entry
 * never removes, downgrades, or re-links it (gains are permanent).
 *
 * @property entryDate the local date the entry was written, frozen at write time via
 * the injected clock (like `CompletionRecord.periodStart`) — the timeline grouping
 * key; travelling later never moves an entry to a different day.
 * @property editedAt set on every edit; null while the entry is untouched.
 * @property questIds the quests this entry counted toward, frozen at save time.
 * Presentation scoping only: a quest-scoped entry lives on its quest's detail screen
 * instead of the main Quest Log; an empty set means free-form writing, shown on the
 * timeline. This is one-directional bookkeeping on the entry — completions still
 * carry no reference back, and deleting the entry still un-completes nothing.
 */
data class JournalEntry(
    val id: JournalEntryId,
    val text: String,
    val createdAt: Instant,
    val entryDate: LocalDate,
    val editedAt: Instant? = null,
    val questIds: Set<QuestId> = emptySet(),
) {
    init {
        require(text.isNotBlank()) { "Journal entry text must not be blank" }
    }
}
