package com.mylive.robot.device

import java.time.LocalTime

/** Local time of day as minutes since midnight, re-read at most once a second. */
class ClockSource(private val now: () -> LocalTime = { LocalTime.now() }) {
    private var cachedAtMs = Long.MIN_VALUE
    private var cached = 0

    fun minuteOfDay(nowMs: Long): Int {
        if (nowMs - cachedAtMs >= REFRESH_MS || nowMs < cachedAtMs) {
            val t = now()
            cached = t.hour * 60 + t.minute
            cachedAtMs = nowMs
        }
        return cached
    }

    private companion object {
        const val REFRESH_MS = 1_000L
    }
}
