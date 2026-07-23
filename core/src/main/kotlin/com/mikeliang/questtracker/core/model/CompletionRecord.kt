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
 * @property attribute the quest's attribute at completion time, frozen at record time
 * like [periodStart] — editing the quest's attribute later never moves banked points.
 * Null for side quests (the identity firewall) and for records banked before freezing
 * existed (accrual falls back to the quest's current attribute for those).
 * @property basePoints the accrual base for the quest's cadence at completion time,
 * frozen so a cadence edit never re-prices banked completions. Null for side quests
 * and pre-freeze records (same fallback).
 */
data class CompletionRecord(
    val questId: QuestId,
    val completedAt: Instant,
    val periodStart: LocalDate,
    val source: CompletionSource,
    val escalationLevel: Int? = null,
    val attribute: Attribute? = null,
    val basePoints: Double? = null,
)
