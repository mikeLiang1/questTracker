package com.mikeliang.questtracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClockTest {

    @Test
    fun `fake clock returns the fixed instant it was given`() {
        val fixedInstant = 1_752_000_000_000L
        val clock = object : Clock {
            override fun now() = fixedInstant
        }

        assertEquals(fixedInstant, clock.now())
    }
}
