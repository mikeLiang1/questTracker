package com.mikeliang.questtracker.core

/**
 * Injected time source. Never call `System.currentTimeMillis()` directly from :core —
 * midnight-boundary and DST-sensitive logic must be testable without wall-clock time.
 */
interface Clock {
    fun now(): Long
}
