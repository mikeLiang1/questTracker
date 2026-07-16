package com.mikeliang.questtracker.core.model

import java.time.Instant
import java.time.LocalDate

/** How a completion happened. Both are equally valid — manual is never second-class. */
enum class CompletionSource { Manual, AutoTracked }

/**
 * One banked completion. Permanent: records are only ever added, never removed or
 * downgraded.
 *
 * @property periodStart start date of the credited period (the completion date itself
 * for dailies and side quests, the ISO-week Monday for weeklies, the 1st for
 * monthlies), frozen at record time in the user's then-current zone — travelling
 * across timezones later never rewrites history.
 * @property escalationLevel the quest's [ProgressionTarget.escalationLevel] at
 * completion time; null for non-progression quests. Diminishing returns key off it.
 */
data class CompletionRecord(
    val questId: QuestId,
    val completedAt: Instant,
    val periodStart: LocalDate,
    val source: CompletionSource,
    val escalationLevel: Int? = null,
)
