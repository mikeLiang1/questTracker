package com.mikeliang.questtracker.core.engine

/** The next rank ahead: always visible, never regressing. */
data class NextMilestone(
    val rank: Int,
    val threshold: Double,
    val pointsRemaining: Double,
)

/**
 * Milestone thresholds and titles (locked in the build plan): rank r is reached at
 * cumulative points 5·r·(r+1)/2 → 5, 15, 30, 50, 75, 105… Frequent early (a single
 * daily quest hits rank 1 in ~5 days), earned later. Titles are identity claims, not
 * scores.
 */
object Milestones {

    private val TITLES = listOf(
        "Unwritten",
        "Awakened",
        "Committed",
        "Consistent",
        "Established",
        "Relentless",
        "Exemplar",
    )

    /** Cumulative points required to hold [rank]. Rank 0 is free. */
    fun thresholdFor(rank: Int): Double {
        require(rank >= 0) { "Rank cannot be negative, was $rank" }
        return 2.5 * rank * (rank + 1)
    }

    /** The highest rank whose threshold [points] meets. */
    fun rankFor(points: Double): Int {
        require(points >= 0) { "Points cannot be negative, was $points" }
        var rank = 0
        while (thresholdFor(rank + 1) <= points) rank++
        return rank
    }

    /** Earned title for [rank]; past the ladder it continues as Exemplar II, III… */
    fun titleFor(rank: Int): String {
        require(rank >= 0) { "Rank cannot be negative, was $rank" }
        return if (rank < TITLES.size) TITLES[rank] else "${TITLES.last()} ${roman(rank - TITLES.size + 2)}"
    }

    /** What's next from [points] — feeds "N more to [title]" copy. */
    fun nextMilestone(points: Double): NextMilestone {
        val nextRank = rankFor(points) + 1
        val threshold = thresholdFor(nextRank)
        return NextMilestone(nextRank, threshold, threshold - points)
    }

    private fun roman(n: Int): String {
        val numerals = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
        var rest = n
        return buildString {
            for ((value, symbol) in numerals) {
                while (rest >= value) {
                    append(symbol)
                    rest -= value
                }
            }
        }
    }
}
