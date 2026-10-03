package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.math.exp

/** Easing functions on t in 0..1 (spec 04), plus the spring step used for continuous params. */
object Easing {
    fun linear(t: Float): Float = t

    fun easeInQuad(t: Float): Float = t * t

    fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) {
            4f * t * t * t
        } else {
            val u = -2f * t + 2f
            1f - u * u * u / 2f
        }

    /** Overshoots past 1 and settles. Overshoot amount is [Tuning.BackOvershoot]. */
    fun easeOutBack(t: Float): Float {
        val c1 = Tuning.BackOvershoot
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    /** Blend factor for one frame of the critically damped spring: 1 - exp(-dt / tau). */
    fun springFactor(dtMs: Float, tauMs: Float): Float = 1f - exp(-dtMs / tauMs)

    /** Moves [value] toward [target] by blend factor [k] (from [springFactor]). Never overshoots. */
    fun approach(value: Float, target: Float, k: Float): Float = value + (target - value) * k
}
