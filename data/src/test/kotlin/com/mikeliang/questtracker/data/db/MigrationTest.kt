package com.mikeliang.questtracker.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration scaffold. :data is at schema version 1, so there is nothing to migrate yet — this
 * proves the exportSchema + MigrationTestHelper wiring is correct now, so that the day a v2
 * ships, the only new step is adding a `helper.runMigrationsAndValidate(2, MIGRATION_1_2)` case
 * beside this one.
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
}
