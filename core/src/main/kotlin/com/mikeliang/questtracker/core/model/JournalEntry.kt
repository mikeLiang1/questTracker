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
 */
data class JournalEntry(
    val id: JournalEntryId,
    val text: String,
    val createdAt: Instant,
    val entryDate: LocalDate,
    val editedAt: Instant? = null,
) {
    init {
        require(text.isNotBlank()) { "Journal entry text must not be blank" }
    }
}
