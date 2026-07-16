package com.mikeliang.questtracker.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Injected time source. Never read wall-clock time or the system zone directly inside
 * :core — midnight-boundary, DST, and timezone-travel logic must be testable with a
 * fake clock.
 */
interface Clock {

    /** The current moment. */
    fun now(): Instant

    /** The user's current time zone. */
    fun zone(): ZoneId

    /** The current date in the user's time zone. */
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}
