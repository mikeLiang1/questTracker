package com.mikeliang.questtracker.core.repository

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Persistence seam, implemented by :data (Room). Completions are append-only —
 * there is deliberately no way to delete or edit a completion, because gains are
 * permanent.
 */
interface QuestRepository {

    /** All quests, active and retired. */
    fun observeQuests(): Flow<List<Quest>>

    /** Every completion ever recorded. */
    fun observeCompletions(): Flow<List<CompletionRecord>>

    suspend fun getQuest(id: QuestId): Quest?

    /** Creates or updates a quest (edits, escalation, retirement). */
    suspend fun upsertQuest(quest: Quest)

    suspend fun recordCompletion(record: CompletionRecord)

    suspend fun completionsFor(questId: QuestId): List<CompletionRecord>

    /** Completions whose credited period starts within [from]..[to] (inclusive). */
    suspend fun completionsInRange(from: LocalDate, to: LocalDate): List<CompletionRecord>
}
