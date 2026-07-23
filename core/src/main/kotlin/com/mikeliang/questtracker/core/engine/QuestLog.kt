package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One item on the Quest Log timeline: something written or something completed. */
sealed interface QuestLogItem {

    /** The moment the item happened — the within-day sort key. */
    val at: Instant

    data class Entry(val entry: JournalEntry) : QuestLogItem {
        override val at: Instant get() = entry.createdAt
    }

    /**
     * @property questTitle null when the quest row no longer exists (can't happen
     * through the app — `canDeleteQuest` blocks deleting quests with history — but
     * the log renders a neutral fallback rather than trusting that).
     * @property attribute the record's frozen attribute, falling back to the quest's
     * current one for pre-freeze rows; null for side quests.
     */
    data class Completion(
        val record: CompletionRecord,
        val questTitle: String?,
        val attribute: Attribute?,
    ) : QuestLogItem {
        override val at: Instant get() = record.completedAt
    }
}

/** One calendar day of the timeline. [items] newest-first. */
data class QuestLogDay(
    val date: LocalDate,
    val items: List<QuestLogItem>,
)

/**
 * The Quest Log: free-form journal entries interleaved with quest completions,
 * grouped by day, days and items newest-first. Entries group by their frozen
 * [JournalEntry.entryDate]; completions by the local date of
 * [CompletionRecord.completedAt] in [zone] — the day it happened, not `periodStart`
 * (a weekly banked on Wednesday reads on Wednesday).
 *
 * Quest-scoped entries (non-empty [JournalEntry.questIds]) are excluded: they live on
 * their quest's detail screen (see [journalEntriesFor]), while the quest's completion
 * row still marks the day here.
 *
 * Unpaginated by design: a heavy user is ~10 completions plus a few entries per day
 * (4–5k small rows/year), well within a Flow + LazyColumn's comfort — revisit with
 * paging only if measured jank appears.
 */
fun buildQuestLog(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
    entries: List<JournalEntry>,
    zone: ZoneId,
): List<QuestLogDay> {
    val questsById = quests.associateBy { it.id }
    val dated: List<Pair<LocalDate, QuestLogItem>> =
        entries.filter { it.questIds.isEmpty() }.map { it.entryDate to QuestLogItem.Entry(it) } +
            completions.map { record ->
                val quest = questsById[record.questId]
                val item = QuestLogItem.Completion(
                    record = record,
                    questTitle = quest?.title,
                    attribute = record.attribute
                        ?: (quest?.kind as? QuestKind.Recurring)?.attribute,
                )
                record.completedAt.atZone(zone).toLocalDate() to item
            }
    return dated
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareByDescending { it })
        .map { (date, items) -> QuestLogDay(date, items.sortedByDescending { it.at }) }
}

/**
 * The journal entries that counted toward [questId], newest first — the quest detail
 * screen's journal section. The complement of the main timeline's entry set: an entry
 * scoped to several quests appears on each of their detail screens.
 */
fun journalEntriesFor(questId: QuestId, entries: List<JournalEntry>): List<JournalEntry> =
    entries.filter { questId in it.questIds }.sortedByDescending { it.createdAt }
