package com.mikeliang.questtracker.core.repository

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Persistence seam, implemented by :data (Room). Completions are append-only —
 * gains are permanent — with one narrow exception: [deleteCompletion] backs the
 * same-day mis-tap undo (see `unclearQuest` in the engine).
 */
interface QuestRepository {

    /** All quests, active and retired. */
    fun observeQuests(): Flow<List<Quest>>

    /** Every completion ever recorded. */
    fun observeCompletions(): Flow<List<CompletionRecord>>

    suspend fun getQuest(id: QuestId): Quest?

    /** Creates or updates a quest (edits, escalation, retirement). */
    suspend fun upsertQuest(quest: Quest)

    /**
     * Removes a quest outright. Only legal for a mis-creation with zero completions —
     * the guard is `canDeleteQuest` in :core, which callers must pass first; the
     * repository is deliberately dumb. Quests with history retire instead.
     */
    suspend fun deleteQuest(id: QuestId)

    suspend fun recordCompletion(record: CompletionRecord)

    /**
     * Removes one banked completion. Only legal for the same-day mis-tap undo — the
     * guard is `QuestEngine.unclear` in :core, which callers must pass first; the
     * repository is deliberately dumb, mirroring [deleteQuest]/`canDeleteQuest`.
     */
    suspend fun deleteCompletion(record: CompletionRecord)

    suspend fun completionsFor(questId: QuestId): List<CompletionRecord>

    /** Completions whose credited period starts within [from]..[to] (inclusive). */
    suspend fun completionsInRange(from: LocalDate, to: LocalDate): List<CompletionRecord>
}
