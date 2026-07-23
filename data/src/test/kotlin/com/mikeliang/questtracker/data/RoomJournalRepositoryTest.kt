package com.mikeliang.questtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.data.db.QuestTrackerDatabase
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
class RoomJournalRepositoryTest {

    private lateinit var db: QuestTrackerDatabase
    private lateinit var repository: RoomJournalRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuestTrackerDatabase::class.java).build()
        repository = RoomJournalRepository(db.journalDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `an untouched entry round-trips with a null editedAt`() = runTest {
        val entry = journalEntry(
            id = "e1",
            text = "Grateful for the quiet morning.",
            entryDate = LocalDate.parse("2026-07-16"),
            createdAt = Instant.parse("2026-07-16T21:00:00Z"),
        )

        repository.upsertEntry(entry)

        assertEquals(entry, repository.getEntry(JournalEntryId("e1")))
    }

    @Test
    fun `an edited entry round-trips its editedAt`() = runTest {
        val entry = journalEntry(id = "e1", editedAt = Instant.parse("2026-07-17T08:00:00Z"))

        repository.upsertEntry(entry)

        assertEquals(entry, repository.getEntry(JournalEntryId("e1")))
    }

    @Test
    fun `deleteEntry removes it from the observed list`() = runTest {
        repository.upsertEntry(journalEntry(id = "e1"))

        repository.deleteEntry(JournalEntryId("e1"))

        assertNull(repository.getEntry(JournalEntryId("e1")))
        assertEquals(emptyList<Any>(), repository.observeEntries().first())
    }
}
