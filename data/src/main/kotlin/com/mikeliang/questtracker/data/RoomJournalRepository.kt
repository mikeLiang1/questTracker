package com.mikeliang.questtracker.data

import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.core.repository.JournalRepository
import com.mikeliang.questtracker.data.db.JournalDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomJournalRepository(
    private val journalDao: JournalDao,
) : JournalRepository {

    override fun observeEntries(): Flow<List<JournalEntry>> =
        journalDao.observeEntries().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEntry(id: JournalEntryId): JournalEntry? =
        journalDao.getEntry(id.value)?.toDomain()

    override suspend fun upsertEntry(entry: JournalEntry) =
        journalDao.upsert(entry.toEntity())

    override suspend fun deleteEntry(id: JournalEntryId) =
        journalDao.delete(id.value)
}
