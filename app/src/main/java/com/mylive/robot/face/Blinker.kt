package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.random.Random

class BlinkProfile(
    val closeMs: Float,
    val holdMs: Float,
    val openMs: Float,
    val minIntervalMs: Float,
    val maxIntervalMs: Float,
) {
    companion object {
        val Normal = BlinkProfile(
            Tuning.BlinkCloseMs, Tuning.BlinkHoldMs, Tuning.BlinkOpenMs,
            Tuning.BlinkMinIntervalMs, Tuning.BlinkMaxIntervalMs,
        )
        val Tired = BlinkProfile(
            Tuning.TiredBlinkCloseMs, Tuning.TiredBlinkHoldMs, Tuning.TiredBlinkOpenMs,
            Tuning.TiredBlinkMinIntervalMs, Tuning.TiredBlinkMaxIntervalMs,
        )
    }
}

/** Blink layer (spec 04): produces a multiplier for eye `open`, 1 = open, 0 = closed. */
class Blinker(private val rng: Random) {
    private enum class Phase { WAIT, CLOSE, HOLD, OPEN, GAP }

    var mult = 1f
        private set

    private var phase = Phase.WAIT
    private var t = 0f
    private var waitMs = interval(BlinkProfile.Normal)
    private var doublePending = false

    /** @param allowed false during mood transitions and gestures: resets and holds the eyes open. */
    fun update(dtMs: Float, profile: BlinkProfile, allowed: Boolean) {
        if (!allowed) {
            phase = Phase.WAIT
            mult = 1f
            waitMs = interval(profile)
            return
        }
        when (phase) {
            Phase.WAIT -> {
                waitMs -= dtMs
                if (waitMs <= 0f) {
                    doublePending = rng.nextFloat() < Tuning.BlinkDoubleChance
                    phase = Phase.CLOSE
                    t = 0f
                }
            }
            Phase.CLOSE -> {
                t += dtMs
                mult = 1f - Easing.easeInOutCubic((t / profile.closeMs).coerceAtMost(1f))
                if (t >= profile.closeMs) { phase = Phase.HOLD; t = 0f }
            }
            Phase.HOLD -> {
                mult = 0f
                t += dtMs
                if (t >= profile.holdMs) { phase = Phase.OPEN; t = 0f }
            }
            Phase.OPEN -> {
                t += dtMs
                mult = Easing.easeInOutCubic((t / profile.openMs).coerceAtMost(1f))
                if (t >= profile.openMs) {
                    mult = 1f
                    t = 0f
                    if (doublePending) {
                        doublePending = false
                        phase = Phase.GAP
                    } else {
                        phase = Phase.WAIT
                        waitMs = interval(profile)
                    }
                }
            }
            Phase.GAP -> {
                t += dtMs
                if (t >= Tuning.BlinkDoubleGapMs) { phase = Phase.CLOSE; t = 0f }
            }
        }
    }

    private fun interval(p: BlinkProfile) =
        p.minIntervalMs + rng.nextFloat() * (p.maxIntervalMs - p.minIntervalMs)

    companion object {
        /** Only kinds that scale to a line blink (spec 04). */
        fun canBlink(kind: EyeKind) =
            kind == EyeKind.RECT || kind == EyeKind.DROOP || kind == EyeKind.RING || kind == EyeKind.HEART
    }
}
