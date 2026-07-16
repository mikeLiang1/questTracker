package com.mikeliang.questtracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.ProgressionTarget
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.data.recurringQuest
import com.mikeliang.questtracker.data.sideQuest
import com.mikeliang.questtracker.data.toDomain
import com.mikeliang.questtracker.data.toEntity
import java.time.DayOfWeek
import java.time.LocalTime
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
class QuestDaoTest {

    private lateinit var db: QuestTrackerDatabase
    private lateinit var dao: QuestDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuestTrackerDatabase::class.java).build()
        dao = db.questDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getQuest round-trips a side quest`() = runTest {
        val quest = sideQuest(id = "side-1", title = "Fix the fence")

        dao.upsert(quest.toEntity())

        assertEquals(quest, dao.getQuest("side-1")?.toDomain())
    }

    @Test
    fun `upsert then getQuest round-trips a recurring quest with progression and reminder`() = runTest {
        val quest = recurringQuest(
            id = "quest-1",
            cadence = Cadence.Weekly,
            type = QuestType.Progression,
            attribute = Attribute.Mind,
            progression = ProgressionTarget(amount = 10.0, unit = "pages", escalationLevel = 2),
            reminder = ReminderSchedule.Recurring(
                time = LocalTime.of(7, 30),
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
        )

        dao.upsert(quest.toEntity())

        assertEquals(quest, dao.getQuest("quest-1")?.toDomain())
    }

    @Test
    fun `upsert then getQuest round-trips a one-shot reminder`() = runTest {
        val quest = sideQuest(
            id = "side-2",
            reminder = ReminderSchedule.OneShot(at = java.time.LocalDateTime.parse("2026-02-01T09:00:00")),
        )

        dao.upsert(quest.toEntity())

        assertEquals(quest, dao.getQuest("side-2")?.toDomain())
    }

    @Test
    fun `getQuest returns null for unknown id`() = runTest {
        assertNull(dao.getQuest("does-not-exist"))
    }

    @Test
    fun `upsert with existing id replaces the row`() = runTest {
        val original = sideQuest(id = "side-1", title = "Original title")
        val edited = original.copy(title = "Edited title", status = QuestStatus.Retired)

        dao.upsert(original.toEntity())
        dao.upsert(edited.toEntity())

        val stored = dao.getQuest("side-1")?.toDomain()
        assertEquals("Edited title", stored?.title)
        assertEquals(QuestStatus.Retired, stored?.status)
    }

    @Test
    fun `observeQuests emits every upserted quest`() = runTest {
        dao.upsert(sideQuest(id = "side-1").toEntity())
        dao.upsert(recurringQuest(id = "quest-1").toEntity())

        val observed = dao.observeQuests().first().map { it.toDomain() }.map { it.id }.toSet()

        assertEquals(setOf(QuestId("side-1"), QuestId("quest-1")), observed)
    }
}
