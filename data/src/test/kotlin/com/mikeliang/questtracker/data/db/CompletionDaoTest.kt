package com.mikeliang.questtracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.data.completion
import com.mikeliang.questtracker.data.recurringQuest
import com.mikeliang.questtracker.data.toDomain
import com.mikeliang.questtracker.data.toEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompletionDaoTest {

    private lateinit var db: QuestTrackerDatabase
    private lateinit var dao: CompletionDao

    private val quest = recurringQuest(id = "quest-1")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuestTrackerDatabase::class.java).build()
        dao = db.completionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert then completionsFor returns every record for that quest`() = runTest {
        val other = recurringQuest(id = "quest-2")
        val record1 = completion(quest, LocalDate.parse("2026-01-01"))
        val record2 = completion(quest, LocalDate.parse("2026-01-02"))
        val otherRecord = completion(other, LocalDate.parse("2026-01-01"))

        dao.insert(record1.toEntity())
        dao.insert(record2.toEntity())
        dao.insert(otherRecord.toEntity())

        val stored = dao.completionsFor("quest-1").map { it.toDomain() }
        assertEquals(setOf(record1, record2), stored.toSet())
    }

    @Test
    fun `completions are append-only - duplicates are all stored, not deduped`() = runTest {
        val record = completion(quest, LocalDate.parse("2026-01-01"))

        dao.insert(record.toEntity())
        dao.insert(record.toEntity())

        assertEquals(2, dao.completionsFor("quest-1").size)
    }

    @Test
    fun `completionsInRange is inclusive of both endpoints and excludes outside dates`() = runTest {
        val before = completion(quest, LocalDate.parse("2025-12-31"))
        val start = completion(quest, LocalDate.parse("2026-01-01"))
        val middle = completion(quest, LocalDate.parse("2026-01-15"))
        val end = completion(quest, LocalDate.parse("2026-01-31"))
        val after = completion(quest, LocalDate.parse("2026-02-01"))

        listOf(before, start, middle, end, after).forEach { dao.insert(it.toEntity()) }

        val inRange = dao.completionsInRange(
            LocalDate.parse("2026-01-01").toEpochDay(),
            LocalDate.parse("2026-01-31").toEpochDay(),
        ).map { it.toDomain() }

        assertEquals(setOf(start, middle, end), inRange.toSet())
    }

    @Test
    fun `escalation level and source round-trip through the entity`() = runTest {
        val record = completion(
            quest,
            LocalDate.parse("2026-01-01"),
            source = CompletionSource.AutoTracked,
            escalationLevel = 3,
        )

        dao.insert(record.toEntity())

        val stored = dao.completionsFor("quest-1").single().toDomain()
        assertEquals(CompletionSource.AutoTracked, stored.source)
        assertEquals(3, stored.escalationLevel)
    }

    @Test
    fun `observeCompletions emits inserted records`() = runTest {
        val record = completion(quest, LocalDate.parse("2026-01-01"))

        dao.insert(record.toEntity())

        val observed = dao.observeCompletions().first().map { it.toDomain() }
        assertTrue(observed.contains(record))
    }
}
