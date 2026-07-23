package com.mikeliang.questtracker.data

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.data.db.CompletionDao
import com.mikeliang.questtracker.data.db.QuestDao
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomQuestRepository(
    private val questDao: QuestDao,
    private val completionDao: CompletionDao,
) : QuestRepository {

    override fun observeQuests(): Flow<List<Quest>> =
        questDao.observeQuests().map { entities -> entities.map { it.toDomain() } }

    override fun observeCompletions(): Flow<List<CompletionRecord>> =
        completionDao.observeCompletions().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getQuest(id: QuestId): Quest? =
        questDao.getQuest(id.value)?.toDomain()

    override suspend fun upsertQuest(quest: Quest) =
        questDao.upsert(quest.toEntity())

    override suspend fun deleteQuest(id: QuestId) =
        questDao.delete(id.value)

    override suspend fun recordCompletion(record: CompletionRecord) =
        completionDao.insert(record.toEntity())

    override suspend fun deleteCompletion(record: CompletionRecord) =
        completionDao.delete(record.questId.value, record.completedAt.toEpochMilli())

    override suspend fun completionsFor(questId: QuestId): List<CompletionRecord> =
        completionDao.completionsFor(questId.value).map { it.toDomain() }

    override suspend fun completionsInRange(from: LocalDate, to: LocalDate): List<CompletionRecord> =
        completionDao.completionsInRange(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }
}
