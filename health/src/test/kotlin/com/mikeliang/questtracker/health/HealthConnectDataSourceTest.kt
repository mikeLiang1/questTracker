package com.mikeliang.questtracker.health

import app.cash.turbine.test
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.health.HealthReading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthConnectDataSourceTest {

    private val api = FakeHealthConnectApi()
    private val clock = FakeClock(Instant.parse("2026-07-16T10:00:00Z"))
    private val dataSource = HealthConnectDataSource(api, clock)
    private val date = LocalDate.parse("2026-07-16")

    @Test
    fun `a day total comes back as Available`() = runTest {
        api.totals[HealthMetric.Steps] = 8_450.0

        assertEquals(
            HealthReading.Available(8_450.0),
            dataSource.readDay(HealthMetric.Steps, date),
        )
    }

    @Test
    fun `no data is Unavailable, never zero`() = runTest {
        api.totals[HealthMetric.SleepMinutes] = null

        assertEquals(HealthReading.Unavailable, dataSource.readDay(HealthMetric.SleepMinutes, date))
    }

    @Test
    fun `a read blowing up is Unavailable, not an exception`() = runTest {
        api.aggregateError = IllegalStateException("binder died")

        assertEquals(HealthReading.Unavailable, dataSource.readDay(HealthMetric.Steps, date))
    }

    @Test
    fun `revoked permission is Unavailable, not an exception`() = runTest {
        api.aggregateError = SecurityException("READ_STEPS revoked")

        assertEquals(HealthReading.Unavailable, dataSource.readDay(HealthMetric.Steps, date))
    }

    @Test
    fun `missing provider is Unavailable without touching the client`() = runTest {
        api.availability = HealthConnectAvailability.NotInstalled

        assertEquals(HealthReading.Unavailable, dataSource.readDay(HealthMetric.Steps, date))
        assertTrue(api.aggregateCalls.isEmpty())
    }

    @Test
    fun `day bounds are midnight to midnight in the user's zone`() = runTest {
        api.totals[HealthMetric.Steps] = 1.0
        clock.zoneId = ZoneId.of("Australia/Sydney") // UTC+10 in July

        dataSource.readDay(HealthMetric.Steps, date)

        val call = api.aggregateCalls.single()
        assertEquals(Instant.parse("2026-07-15T14:00:00Z"), call.start)
        assertEquals(Instant.parse("2026-07-16T14:00:00Z"), call.end)
    }

    @Test
    fun `observeToday polls and picks up new values`() = runTest {
        api.totals[HealthMetric.Steps] = 5_200.0

        dataSource.observeToday(HealthMetric.Steps).test {
            assertEquals(HealthReading.Available(5_200.0), awaitItem())

            api.totals[HealthMetric.Steps] = 5_950.0
            assertEquals(HealthReading.Available(5_950.0), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeToday follows the clock across midnight`() = runTest {
        api.totals[HealthMetric.Steps] = 9_000.0

        dataSource.observeToday(HealthMetric.Steps).test {
            awaitItem()
            clock.instant = Instant.parse("2026-07-17T00:05:00Z") // past midnight UTC
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val readDates = api.aggregateCalls.map { it.start }
        assertTrue(Instant.parse("2026-07-16T00:00:00Z") in readDates)
        assertTrue(Instant.parse("2026-07-17T00:00:00Z") in readDates)
    }

    @Test
    fun `observeToday emits Unavailable while reads fail, then recovers`() = runTest {
        api.aggregateError = IllegalStateException("flaky")

        dataSource.observeToday(HealthMetric.Steps).test {
            assertEquals(HealthReading.Unavailable, awaitItem())

            api.aggregateError = null
            api.totals[HealthMetric.Steps] = 100.0
            assertEquals(HealthReading.Available(100.0), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
