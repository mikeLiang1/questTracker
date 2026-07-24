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

    /** The moment the item happened — the within-day newest-first sort key. */
    val at: Instant

    /**
     * @property linkedQuestTitles the still-existing quests this entry counted toward,
     * for the card's "counted toward …" line. Empty for free-form writing (and for a
     * scoped entry whose quests were all deleted — the writing outlives the link).
     */
    data class Entry(
        val entry: JournalEntry,
        val linkedQuestTitles: List<String> = emptyList(),
    ) : QuestLogItem {
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
 * The Quest Log: journal entries interleaved with quest completions, grouped by day,
 * days newest-first. Within a day the timeline is newest-first, except that the
 * completion a journal entry banked is lifted to sit directly above that entry (see
 * [orderWithinDay]) so the pair reads together. Entries group by their frozen
 * [JournalEntry.entryDate]; completions by the local date of
 * [CompletionRecord.completedAt] in [zone] — the day it happened, not `periodStart`
 * (a weekly banked on Wednesday reads on Wednesday).
 *
 * Every entry appears here, quest-scoped or not — a quest-linked entry sits on its day
 * beside the completion it banked, tagged with the quest(s) it counted toward
 * ([QuestLogItem.Entry.linkedQuestTitles]). The same entry, day-scoped, also shows on
 * that quest's detail screen (see [journalEntriesFor]); the day is the shared index.
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
        entries.map { entry ->
            val titles = entry.questIds.mapNotNull { questsById[it]?.title }
            entry.entryDate to QuestLogItem.Entry(entry, titles)
        } +
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
        .map { (date, items) -> QuestLogDay(date, orderWithinDay(items)) }
}

/**
 * Orders a single day. The list is newest-first, with one exception: the completion a
 * journal entry banked is lifted to sit directly above that entry, so a linked
 * quest/journal pair reads together (quest on top) instead of being split by their
 * near-identical timestamps. Every other item — manual ticks, free-form entries —
 * keeps its plain newest-first slot; this is a pairing rule, not "all quests first".
 *
 * A completion is paired to an entry when that entry lists its quest
 * ([JournalEntry.questIds]) — the entry's frozen record of exactly what it banked. Per
 * period dedupe, at most one entry per day banks a given quest, so the pairing is
 * unambiguous; a completion no same-day entry claims stays standalone.
 */
private fun orderWithinDay(items: List<QuestLogItem>): List<QuestLogItem> {
    val entries = items.filterIsInstance<QuestLogItem.Entry>()
    val completions = items.filterIsInstance<QuestLogItem.Completion>()

    // Quest id -> the entry that banked it today (last write wins, though dedupe means
    // there is only ever one).
    val bankingEntryId: Map<QuestId, String> = buildMap {
        for (e in entries) for (qid in e.entry.questIds) put(qid, e.entry.id.value)
    }

    val bankedFor = HashMap<String, MutableList<QuestLogItem.Completion>>()
    val standaloneCompletions = ArrayList<QuestLogItem.Completion>()
    for (c in completions) {
        val entryId = bankingEntryId[c.record.questId]
        if (entryId != null) bankedFor.getOrPut(entryId) { mutableListOf() }.add(c)
        else standaloneCompletions.add(c)
    }

    // Everything that owns a slot in the timeline (standalone completions + all
    // entries), newest-first; each entry then pulls its banked completions above it.
    val slots: List<QuestLogItem> = (standaloneCompletions + entries).sortedByDescending { it.at }
    return buildList {
        for (item in slots) {
            if (item is QuestLogItem.Entry) {
                bankedFor[item.entry.id.value]?.sortedByDescending { it.at }?.let(::addAll)
            }
            add(item)
        }
    }
}

/**
 * The journal entries that counted toward [questId], newest first — the quest detail
 * screen's journal section. The complement of the main timeline's entry set: an entry
 * scoped to several quests appears on each of their detail screens.
 *
 * [on] narrows to the entries written that day, keyed on the frozen
 * [JournalEntry.entryDate]. A completion row on the Quest Log opens its quest scoped
 * to the day it was tapped on, so the writing shown belongs to that day and no other —
 * a completion banked before anything was ever written shows no journal at all. Null
 * (opening a quest from the board or profile, where no day is in play) is the whole
 * archive.
 */
fun journalEntriesFor(
    questId: QuestId,
    entries: List<JournalEntry>,
    on: LocalDate? = null,
): List<JournalEntry> = entries
    .filter { questId in it.questIds && (on == null || it.entryDate == on) }
    .sortedByDescending { it.createdAt }
