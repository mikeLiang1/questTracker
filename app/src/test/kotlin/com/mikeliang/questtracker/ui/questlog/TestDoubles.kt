package com.mikeliang.questtracker.ui.questlog

import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.core.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeJournalRepository : JournalRepository {

    private val entries = MutableStateFlow<List<JournalEntry>>(emptyList())

    override fun observeEntries(): Flow<List<JournalEntry>> = entries.asStateFlow()

    override suspend fun getEntry(id: JournalEntryId): JournalEntry? =
        entries.value.firstOrNull { it.id == id }

    override suspend fun upsertEntry(entry: JournalEntry) {
        entries.update { list -> list.filterNot { it.id == entry.id } + entry }
    }

    override suspend fun deleteEntry(id: JournalEntryId) {
        entries.update { list -> list.filterNot { it.id == id } }
    }

    val storedEntries: List<JournalEntry> get() = entries.value
}
