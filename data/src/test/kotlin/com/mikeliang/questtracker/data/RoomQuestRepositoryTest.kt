package com.mikeliang.questtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.data.db.QuestTrackerDatabase
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

/**
 * Exercises [RoomQuestRepository] through the [QuestRepository] interface it implements, rather
 * than through the DAOs directly — this is the contract :app and :core use-cases will actually
 * see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomQuestRepositoryTest {

    private lateinit var db: QuestTrackerDatabase
    private lateinit var repository: QuestRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuestTrackerDatabase::class.java).build()
        repository = RoomQuestRepository(db.questDao(), db.completionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getQuest returns null before any upsert`() = runTest {
        assertNull(repository.getQuest(QuestId("quest-1")))
    }

    @Test
    fun `upsertQuest then getQuest round-trips`() = runTest {
        val quest = recurringQuest(id = "quest-1")

        repository.upsertQuest(quest)

        assertEquals(quest, repository.getQuest(QuestId("quest-1")))
    }

    @Test
    fun `upsertQuest twice with the same id edits rather than duplicates`() = runTest {
        val quest = sideQuest(id = "side-1")

        repository.upsertQuest(quest)
        repository.upsertQuest(quest.copy(status = QuestStatus.Retired))

        assertEquals(1, repository.observeQuests().first().size)
        assertEquals(QuestStatus.Retired, repository.getQuest(QuestId("side-1"))?.status)
    }

    @Test
    fun `observeQuests reflects every upserted quest`() = runTest {
        repository.upsertQuest(recurringQuest(id = "quest-1"))
        repository.upsertQuest(sideQuest(id = "side-1"))

        val ids = repository.observeQuests().first().map { it.id }.toSet()

        assertEquals(setOf(QuestId("quest-1"), QuestId("side-1")), ids)
    }

    @Test
    fun `deleteQuest removes the quest`() = runTest {
        repository.upsertQuest(sideQuest(id = "side-1"))

        repository.deleteQuest(QuestId("side-1"))

        assertNull(repository.getQuest(QuestId("side-1")))
        assertEquals(0, repository.observeQuests().first().size)
    }

    @Test
    fun `cadenceChangedOn round-trips`() = runTest {
        val quest = recurringQuest(id = "quest-1", cadenceChangedOn = LocalDate.parse("2026-07-01"))

        repository.upsertQuest(quest)

        assertEquals(quest, repository.getQuest(QuestId("quest-1")))
    }

    @Test
    fun `frozen accrual context round-trips on completions`() = runTest {
        val quest = recurringQuest(id = "quest-1", cadence = Cadence.Weekly)
        val record = completion(quest, LocalDate.parse("2026-01-05"))

        repository.recordCompletion(record)

        assertEquals(listOf(record), repository.completionsFor(QuestId("quest-1")))
    }

    @Test
    fun `recordCompletion then completionsFor returns the record`() = runTest {
        val quest = recurringQuest(id = "quest-1")
        val record = completion(quest, LocalDate.parse("2026-01-01"))

        repository.recordCompletion(record)

        assertEquals(listOf(record), repository.completionsFor(QuestId("quest-1")))
    }

    @Test
    fun `completionsInRange filters by periodStart across quests`() = runTest {
        val questA = recurringQuest(id = "quest-a")
        val questB = recurringQuest(id = "quest-b")
        val inRange = completion(questA, LocalDate.parse("2026-01-10"))
        val outOfRange = completion(questB, LocalDate.parse("2026-02-10"))

        repository.recordCompletion(inRange)
        repository.recordCompletion(outOfRange)

        val result = repository.completionsInRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))

        assertEquals(listOf(inRange), result)
    }

    @Test
    fun `deleteCompletion removes only the matching record`() = runTest {
        val quest = recurringQuest(id = "quest-1")
        val misTap = completion(quest, LocalDate.parse("2026-01-02"))
        val banked = completion(quest, LocalDate.parse("2026-01-01"))

        repository.recordCompletion(misTap)
        repository.recordCompletion(banked)
        repository.deleteCompletion(misTap)

        assertEquals(listOf(banked), repository.completionsFor(QuestId("quest-1")))
    }

    @Test
    fun `observeCompletions reflects every recorded completion`() = runTest {
        val quest = recurringQuest(id = "quest-1")
        val record = completion(quest, LocalDate.parse("2026-01-01"))

        repository.recordCompletion(record)

        assertEquals(listOf(record), repository.observeCompletions().first())
    }
}
