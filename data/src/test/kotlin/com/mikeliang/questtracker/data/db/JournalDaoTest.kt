package com.mikeliang.questtracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mikeliang.questtracker.data.journalEntry
import com.mikeliang.questtracker.data.toDomain
import com.mikeliang.questtracker.data.toEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalDaoTest {

    private lateinit var db: QuestTrackerDatabase
    private lateinit var dao: JournalDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuestTrackerDatabase::class.java).build()
        dao = db.journalDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getEntry round-trips an entry`() = runTest {
        val entry = journalEntry(id = "e1", text = "Walked in the rain and liked it.")

        dao.upsert(entry.toEntity())

        assertEquals(entry, dao.getEntry("e1")?.toDomain())
    }

    @Test
    fun `upsert with existing id is an edit`() = runTest {
        val original = journalEntry(id = "e1", text = "First draft")
        val edited = original.copy(text = "Second draft", editedAt = Instant.parse("2026-01-02T10:00:00Z"))

        dao.upsert(original.toEntity())
        dao.upsert(edited.toEntity())

        assertEquals(edited, dao.getEntry("e1")?.toDomain())
        assertEquals(1, dao.observeEntries().first().size)
    }

    @Test
    fun `observeEntries is newest-first by createdAt`() = runTest {
        dao.upsert(journalEntry(id = "old", entryDate = LocalDate.parse("2026-01-01")).toEntity())
        dao.upsert(journalEntry(id = "new", entryDate = LocalDate.parse("2026-02-01")).toEntity())

        assertEquals(listOf("new", "old"), dao.observeEntries().first().map { it.id })
    }

    @Test
    fun `delete removes the entry only`() = runTest {
        dao.upsert(journalEntry(id = "e1").toEntity())
        dao.upsert(journalEntry(id = "e2").toEntity())

        dao.delete("e1")

        assertNull(dao.getEntry("e1"))
        assertEquals(listOf("e2"), dao.observeEntries().first().map { it.id })
    }

    @Test
    fun `delete of an unknown id is a no-op`() = runTest {
        dao.upsert(journalEntry(id = "e1").toEntity())

        dao.delete("does-not-exist")

        assertEquals(1, dao.observeEntries().first().size)
    }
}
