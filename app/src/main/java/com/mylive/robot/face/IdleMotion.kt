package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Per-mood idle micro-animations (spec 04). Pure Kotlin. Call [start] when the mood changes and
 * [update] every frame; the outputs are small offsets the animator adds on top of the other layers.
 * Neutral and Denying have none (Denying moves through the ShakeNo gesture, task 10).
 *
 * Wink's optional "right eye re-opens" idle is not built.
 */
class IdleMotion(private val rng: Random) {
    // ---- outputs, reset to neutral at the start of every update ----
    var fy = 0f; private set
    var eyeDy = 0f; private set          // added to both eyes
    var rightEyeDy = 0f; private set     // added to the right eye only
    var tilt = 0f; private set           // degrees, added to both eyes
    var eyeWMul = 1f; private set        // multiplies both eyes' w
    var mouthDx = 0f; private set
    var mouthDy = 0f; private set
    var faceAlpha = 1f; private set
    var spinLeft = 0f; private set       // degrees
    var spinRight = 0f; private set
    var glowMul = 1f; private set
    /** Replaces the mood's glow when >= 0, blended in by [glowBlend]. */
    var glowAbs = -1f; private set
    var glowBlend = 1f; private set

    private var mood = Mood.Neutral
    private var t = 0.0 // ms since the mood started

    // Sad
    private var glanceWait = 0f
    private var glanceT = -1f
    // Tired
    private var nodWait = 0f
    private var nodT = -1f
    // Broken
    private var flickerWait = 0f
    private var flickerGlow = 0f
    private var jitterX = 0f
    private var jitterY = 0f
    private var blackWait = 0f
    private var blackLeft = 0f

    fun start(newMood: Mood) {
        mood = newMood
        t = 0.0
        glanceWait = between(Tuning.SadGlanceMinWaitMs, Tuning.SadGlanceMaxWaitMs)
        glanceT = -1f
        nodWait = between(Tuning.TiredNodMinWaitMs, Tuning.TiredNodMaxWaitMs)
        nodT = -1f
        flickerWait = 0f
        flickerGlow = Tuning.BrokenGlowMax
        jitterX = 0f
        jitterY = 0f
        blackWait = between(Tuning.BrokenBlackoutMinWaitMs, Tuning.BrokenBlackoutMaxWaitMs)
        blackLeft = 0f
    }

    fun update(dtMs: Float) {
        t += dtMs
        fy = 0f; eyeDy = 0f; rightEyeDy = 0f; tilt = 0f; eyeWMul = 1f
        mouthDx = 0f; mouthDy = 0f; faceAlpha = 1f; spinLeft = 0f; spinRight = 0f
        glowMul = 1f; glowAbs = -1f
        glowBlend = (t / Tuning.IdleGlowBlendMs).coerceAtMost(1.0).toFloat()

        when (mood) {
            Mood.Happy -> fy = -Tuning.HappyBounceUnits * Easing.easeInOutCubic(triangle(Tuning.HappyBouncePeriodMs))
            Mood.Sad -> sad(dtMs)
            Mood.Angry -> {
                tilt = Tuning.AngryTremorDeg * sin(TWO_PI * Tuning.AngryTremorHz * (t % 1000.0) / 1000.0).toFloat()
                glowMul = 1f + Tuning.AngryGlowPulse * (0.5f - 0.5f * cosCycle(Tuning.AngryGlowPeriodMs))
            }
            Mood.Tired -> tired(dtMs)
            Mood.Surprised -> {
                val p = ((t - Tuning.SurprisedPulseDelayMs) / Tuning.SurprisedPulseMs).toFloat()
                if (p in 0f..1f) eyeWMul = 1f + Tuning.SurprisedPulse * sin(PI.toFloat() * p)
            }
            Mood.Love -> {
                val phase = (t % Tuning.LoveBeatPeriodMs).toFloat()
                val up = Tuning.LoveBeatUpMs
                val k = if (phase < up) {
                    Easing.easeOutQuad(phase / up)
                } else {
                    1f - Easing.easeInOutCubic((phase - up) / (Tuning.LoveBeatPeriodMs - up))
                }
                eyeWMul = 1f + Tuning.LoveBeat * k
            }
            Mood.Sceptic ->
                rightEyeDy = -Tuning.SceptDriftUnits * (0.5f - 0.5f * cosCycle(Tuning.SceptDriftPeriodMs))
            Mood.Broken -> broken(dtMs)
            Mood.Crazy -> {
                val a = (360.0 * (t % Tuning.CrazySpinMs) / Tuning.CrazySpinMs).toFloat()
                spinLeft = a
                spinRight = -a
            }
            Mood.Charging -> glowAbs = lerp(
                Tuning.ChargingGlowMin, Tuning.ChargingGlowMax, 0.5f - 0.5f * cosCycle(Tuning.ChargingGlowPeriodMs),
            )
            Mood.LowBattery -> glowAbs = lerp(
                Tuning.LowBatteryGlowMin, Tuning.LowBatteryGlowMax, 0.5f - 0.5f * cosCycle(Tuning.LowBatteryGlowPeriodMs),
            )
            else -> Unit
        }
    }

    /** Slow sink over 3 s, plus a rare glance down. */
    private fun sad(dt: Float) {
        val sink = Tuning.SadSinkUnits * Easing.easeInOutCubic((t / Tuning.SadSinkMs).coerceAtMost(1.0).toFloat())
        var glance = 0f
        if (glanceT >= 0f) {
            glanceT += dt
            val p = glanceT / Tuning.SadGlanceMs
            if (p >= 1f) {
                glanceT = -1f
                glanceWait = between(Tuning.SadGlanceMinWaitMs, Tuning.SadGlanceMaxWaitMs)
            } else {
                glance = Tuning.SadGlanceUnits * sin(PI.toFloat() * p)
            }
        } else {
            glanceWait -= dt
            if (glanceWait <= 0f) glanceT = 0f
        }
        eyeDy = sink + glance
    }

    /** Head nod: sinks slowly, then snaps back up. */
    private fun tired(dt: Float) {
        if (nodT < 0f) {
            nodWait -= dt
            if (nodWait <= 0f) nodT = 0f
            return
        }
        nodT += dt
        val down = Tuning.TiredNodDownMs
        val up = Tuning.TiredNodUpMs
        if (nodT < down) {
            fy = Tuning.TiredNodUnits * Easing.easeInOutCubic(nodT / down)
        } else if (nodT < down + up) {
            fy = Tuning.TiredNodUnits * (1f - Easing.easeOutQuad((nodT - down) / up))
        } else {
            nodT = -1f
            nodWait = between(Tuning.TiredNodMinWaitMs, Tuning.TiredNodMaxWaitMs)
        }
    }

    /** Glow flicker, mouth jitter, and the odd 80 ms blackout. */
    private fun broken(dt: Float) {
        flickerWait -= dt
        if (flickerWait <= 0f) {
            flickerGlow = between(Tuning.BrokenGlowMin, Tuning.BrokenGlowMax)
            jitterX = between(-Tuning.BrokenJitterUnits, Tuning.BrokenJitterUnits)
            jitterY = between(-Tuning.BrokenJitterUnits, Tuning.BrokenJitterUnits)
            flickerWait = between(Tuning.BrokenFlickerMinMs, Tuning.BrokenFlickerMaxMs)
        }
        if (blackLeft > 0f) {
            blackLeft -= dt
            faceAlpha = 0f
            if (blackLeft <= 0f) blackWait = between(Tuning.BrokenBlackoutMinWaitMs, Tuning.BrokenBlackoutMaxWaitMs)
        } else {
            blackWait -= dt
            if (blackWait <= 0f) {
                blackLeft = Tuning.BrokenBlackoutMs
                faceAlpha = 0f
            }
        }
        glowAbs = flickerGlow
        mouthDx = jitterX
        mouthDy = jitterY
    }

    /** 0 -> 1 -> 0 over [periodMs], linear in time. */
    private fun triangle(periodMs: Float): Float {
        val p = ((t % periodMs) / periodMs).toFloat()
        return if (p < 0.5f) 2f * p else 2f * (1f - p)
    }

    /** cos of the cycle phase: 1 at the start of each period, -1 halfway. */
    private fun cosCycle(periodMs: Float): Float = cos(TWO_PI * (t % periodMs) / periodMs).toFloat()

    private fun between(a: Float, b: Float) = a + rng.nextFloat() * (b - a)

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

    private companion object {
        const val TWO_PI = 2.0 * PI
    }
}
