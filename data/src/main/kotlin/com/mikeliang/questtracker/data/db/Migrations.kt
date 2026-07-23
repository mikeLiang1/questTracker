package com.mikeliang.questtracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * v1 → v2 (Phase 6b): quest editing.
 *
 * - `quests.cadenceChangedOnEpochDay` — stamp of the most recent cadence edit;
 *   consistency scoring restarts its window there. Null for every existing quest
 *   (nothing was ever edited before v2).
 * - `completions.attribute` / `completions.basePoints` — accrual context frozen at
 *   record time, so later edits never move or re-price banked points. Backfilled
 *   from each record's quest: exact, because no edit ever happened before v2, so a
 *   quest's current attribute/cadence *are* its records' record-time values.
 *   Side-quest records get NULL/NULL automatically (their quest rows have no
 *   cadence/attribute), which is the domain encoding of the identity firewall.
 *
 * Both `migrate` overloads are implemented: the driver path ([SQLiteConnection])
 * runs in tests via the BundledSQLiteDriver helper, the support path
 * ([SupportSQLiteDatabase]) in the production compatibility-mode builder.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {

    private val statements = listOf(
        "ALTER TABLE quests ADD COLUMN cadenceChangedOnEpochDay INTEGER",
        "ALTER TABLE completions ADD COLUMN attribute TEXT",
        "ALTER TABLE completions ADD COLUMN basePoints REAL",
        """
        UPDATE completions SET
          attribute = (SELECT q.attribute FROM quests q WHERE q.id = completions.questId),
          basePoints = (SELECT CASE q.cadence
                          WHEN 'Daily' THEN 1.0
                          WHEN 'Weekly' THEN 3.0
                          WHEN 'Monthly' THEN 10.0
                        END
                        FROM quests q WHERE q.id = completions.questId)
        """.trimIndent(),
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        statements.forEach(db::execSQL)
    }

    override fun migrate(connection: SQLiteConnection) {
        statements.forEach(connection::execSQL)
    }
}

/**
 * v2 → v3 (Phase 7b): journaling.
 *
 * - `quests.journalLinked` — opt-in flag: saving a journal entry auto-completes the
 *   quest for its current period. Defaults to 0 for every existing quest (linkage is
 *   an explicit user choice; no title-matching backfill — a Sage user flips the
 *   toggle on their journal quest in the edit sheet).
 * - `journal_entries` — free-text entries, the one mutable table. No FK to
 *   completions: a completion banked by an entry save is an ordinary append-only
 *   record and survives the entry's edit or deletion.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {

    private val statements = listOf(
        "ALTER TABLE quests ADD COLUMN journalLinked INTEGER NOT NULL DEFAULT 0",
        """
        CREATE TABLE IF NOT EXISTS `journal_entries` (
          `id` TEXT NOT NULL,
          `text` TEXT NOT NULL,
          `createdAtEpochMillis` INTEGER NOT NULL,
          `entryDateEpochDay` INTEGER NOT NULL,
          `editedAtEpochMillis` INTEGER,
          PRIMARY KEY(`id`)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_journal_entries_entryDateEpochDay` ON `journal_entries` (`entryDateEpochDay`)",
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        statements.forEach(db::execSQL)
    }

    override fun migrate(connection: SQLiteConnection) {
        statements.forEach(connection::execSQL)
    }
}
