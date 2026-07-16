package com.mikeliang.questtracker.core

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClockTest {

    @Test
    fun `fake clock returns the fixed instant it was given`() {
        val fixed = Instant.parse("2026-07-16T15:00:00Z")
        val clock = FakeClock(fixed)

        assertEquals(fixed, clock.now())
    }

    @Test
    fun `today depends on the zone - same instant is tomorrow in Sydney`() {
        val instant = Instant.parse("2026-07-16T15:00:00Z")

        val utc = FakeClock(instant, ZoneId.of("UTC"))
        val sydney = FakeClock(instant, ZoneId.of("Australia/Sydney"))

        assertEquals(date("2026-07-16"), utc.today())
        assertEquals(date("2026-07-17"), sydney.today())
    }
}
