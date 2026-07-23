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
}
