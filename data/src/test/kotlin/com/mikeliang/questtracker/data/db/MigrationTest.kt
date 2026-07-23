package com.mikeliang.questtracker.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration coverage. The v1 case proves the exportSchema + MigrationTestHelper wiring; the
 * v1→v2 case validates the Phase 6b schema change and its accrual-context backfill.
 *
 * Uses the [BundledSQLiteDriver] (rather than the framework/support driver the other constructor
 * overloads use) because [androidx.sqlite.driver.SupportSQLiteDriver]'s open() matches database
 * names by their last path segment split on '/', which never matches on Windows' '\'-separated
 * paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test"),
        driver = BundledSQLiteDriver(),
        databaseClass = QuestTrackerDatabase::class,
    )

    @Test
    fun `version 1 database creates cleanly from the exported schema file`() {
        val connection = helper.createDatabase(1)
        assertNotNull(connection)
        connection.close()
    }

    @Test
    fun `migrating 1 to 2 backfills frozen accrual context from each record's quest`() {
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                "INSERT INTO quests (id, title, cadence, questType, attribute, createdAtEpochMillis, status) " +
                    "VALUES ('q-daily', 'Gym hour', 'Daily', 'Maintenance', 'Body', 0, 'Active')"
            )
            v1.execSQL(
                "INSERT INTO quests (id, title, cadence, questType, attribute, createdAtEpochMillis, status) " +
                    "VALUES ('q-weekly', 'Deep work', 'Weekly', 'Maintenance', 'Mind', 0, 'Active')"
            )
            v1.execSQL(
                "INSERT INTO quests (id, title, createdAtEpochMillis, status) " +
                    "VALUES ('q-side', 'Call plumber', 0, 'Active')"
            )
            for (questId in listOf("q-daily", "q-weekly", "q-side")) {
                v1.execSQL(
                    "INSERT INTO completions (questId, completedAtEpochMillis, periodStartEpochDay, source) " +
                        "VALUES ('$questId', 0, 20000, 'Manual')"
                )
            }
        }

        val v2 = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
        v2.use { connection ->
            val backfilled = mutableMapOf<String, Pair<String?, Double?>>()
            connection.prepare("SELECT questId, attribute, basePoints FROM completions").use { stmt ->
                while (stmt.step()) {
                    backfilled[stmt.getText(0)] =
                        (if (stmt.isNull(1)) null else stmt.getText(1)) to
                        (if (stmt.isNull(2)) null else stmt.getDouble(2))
                }
            }
            assertEquals("Body" to 1.0, backfilled["q-daily"])
            assertEquals("Mind" to 3.0, backfilled["q-weekly"])
            assertEquals(null to null, backfilled["q-side"])

            connection.prepare("SELECT COUNT(*) FROM quests WHERE cadenceChangedOnEpochDay IS NOT NULL").use { stmt ->
                stmt.step()
                assertEquals(0L, stmt.getLong(0))
            }
        }
    }

    @Test
    fun `migrating 2 to 3 adds journal_entries and defaults journalLinked to 0`() {
        helper.createDatabase(2).use { v2 ->
            v2.execSQL(
                "INSERT INTO quests (id, title, cadence, questType, attribute, createdAtEpochMillis, status) " +
                    "VALUES ('q-journal', 'One line of journal', 'Daily', 'Maintenance', 'Mind', 0, 'Active')"
            )
            v2.execSQL(
                "INSERT INTO completions (questId, completedAtEpochMillis, periodStartEpochDay, source, attribute, basePoints) " +
                    "VALUES ('q-journal', 0, 20000, 'Manual', 'Mind', 1.0)"
            )
        }

        val v3 = helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3))
        v3.use { connection ->
            // Existing quests are unlinked: linkage is an explicit user choice.
            connection.prepare("SELECT journalLinked FROM quests WHERE id = 'q-journal'").use { stmt ->
                stmt.step()
                assertEquals(0L, stmt.getLong(0))
            }
            // Completions survive untouched.
            connection.prepare("SELECT COUNT(*) FROM completions").use { stmt ->
                stmt.step()
                assertEquals(1L, stmt.getLong(0))
            }
            // The new table exists and is empty but queryable.
            connection.prepare("SELECT COUNT(*) FROM journal_entries").use { stmt ->
                stmt.step()
                assertEquals(0L, stmt.getLong(0))
            }
        }
    }

    @Test
    fun `migrating 3 to 4 leaves existing entries free-form`() {
        helper.createDatabase(3).use { v3 ->
            v3.execSQL(
                "INSERT INTO journal_entries (id, text, createdAtEpochMillis, entryDateEpochDay) " +
                    "VALUES ('e1', 'An old entry', 0, 20000)"
            )
        }

        val v4 = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4))
        v4.use { connection ->
            // Pre-scoping entries stay on the timeline: no invented quest links.
            connection.prepare("SELECT linkedQuestIds FROM journal_entries WHERE id = 'e1'").use { stmt ->
                stmt.step()
                assertEquals(true, stmt.isNull(0))
            }
        }
    }

    @Test
    fun `the full chain 1 to 4 runs cleanly`() {
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                "INSERT INTO quests (id, title, cadence, questType, attribute, createdAtEpochMillis, status) " +
                    "VALUES ('q-daily', 'Gym hour', 'Daily', 'Maintenance', 'Body', 0, 'Active')"
            )
            v1.execSQL(
                "INSERT INTO completions (questId, completedAtEpochMillis, periodStartEpochDay, source) " +
                    "VALUES ('q-daily', 0, 20000, 'Manual')"
            )
        }

        val v4 = helper.runMigrationsAndValidate(4, listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4))
        v4.use { connection ->
            connection.prepare(
                "SELECT journalLinked, (SELECT attribute FROM completions WHERE questId = 'q-daily') " +
                    "FROM quests WHERE id = 'q-daily'"
            ).use { stmt ->
                stmt.step()
                assertEquals(0L, stmt.getLong(0))
                assertEquals("Body", stmt.getText(1))
            }
            connection.prepare("SELECT COUNT(*) FROM journal_entries").use { stmt ->
                stmt.step()
                assertEquals(0L, stmt.getLong(0))
            }
        }
    }
}
