package com.mikeliang.questtracker.core.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MilestonesTest {

    @ParameterizedTest
    @CsvSource("0, 0", "1, 5", "2, 15", "3, 30", "4, 50", "5, 75", "6, 105")
    fun `thresholds follow the 5r(r+1) over 2 curve`(rank: Int, expected: Double) {
        assertEquals(expected, Milestones.thresholdFor(rank))
    }

    @ParameterizedTest
    @CsvSource(
        "0.0, 0",
        "4.5, 0",
        "5.0, 1",   // exactly at a threshold earns the rank
        "14.5, 1",
        "15.0, 2",
        "30.0, 3",
        "104.5, 5",
        "105.0, 6",
    )
    fun `rankFor is the highest threshold met`(points: Double, expected: Int) {
        assertEquals(expected, Milestones.rankFor(points))
    }

    @Test
    fun `titles climb the ladder then continue as Exemplar numerals`() {
        assertEquals("Unwritten", Milestones.titleFor(0))
        assertEquals("Awakened", Milestones.titleFor(1))
        assertEquals("Committed", Milestones.titleFor(2))
        assertEquals("Consistent", Milestones.titleFor(3))
        assertEquals("Established", Milestones.titleFor(4))
        assertEquals("Relentless", Milestones.titleFor(5))
        assertEquals("Exemplar", Milestones.titleFor(6))
        assertEquals("Exemplar II", Milestones.titleFor(7))
        assertEquals("Exemplar III", Milestones.titleFor(8))
        assertEquals("Exemplar V", Milestones.titleFor(10))
    }

    @Test
    fun `nextMilestone reports the gap to the next rank`() {
        val next = Milestones.nextMilestone(17.5)

        assertEquals(3, next.rank)
        assertEquals(30.0, next.threshold)
        assertEquals(12.5, next.pointsRemaining)
    }

    @Test
    fun `nextMilestone from zero points targets rank 1`() {
        val next = Milestones.nextMilestone(0.0)

        assertEquals(1, next.rank)
        assertEquals(5.0, next.pointsRemaining)
    }

    @Test
    fun `negative inputs are rejected`() {
        assertThrows<IllegalArgumentException> { Milestones.thresholdFor(-1) }
        assertThrows<IllegalArgumentException> { Milestones.rankFor(-0.1) }
        assertThrows<IllegalArgumentException> { Milestones.titleFor(-1) }
    }
}
