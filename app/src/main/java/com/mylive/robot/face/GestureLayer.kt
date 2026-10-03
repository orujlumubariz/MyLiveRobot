package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class Gesture(val durationMs: Float, val face: Mood?) {
    ShakeNo(Tuning.ShakeNoMs, Mood.Denying),
    NodYes(Tuning.NodYesMs, null),
    Bounce(Tuning.BounceMs, null),
    Jolt(Tuning.JoltMs, Mood.Surprised),
    GlitchBurst(Tuning.GlitchMs, Mood.Broken),
    Giggle(Tuning.GiggleMs, Mood.Happy),
    Yawn(Tuning.YawnMs, Mood.Tired),
    LookAround(Tuning.LookAroundMs, null),
}

/**
 * Gesture layer (spec 04): one-shot overrides of fx/fy/scale and a few face numbers. Pure Kotlin.
 * [FaceAnimator] switches the face for gestures that have one and returns to the previous mood.
 */
class GestureLayer(private val rng: Random) {
    var active = false; private set
    var gesture: Gesture? = null; private set

    // ---- outputs; neutral when inactive ----
    var fx = 0f; private set
    var fy = 0f; private set
    var scaleMul = 1f; private set
    var eyeOpenMul = 1f; private set
    var mouthWAdd = 0f; private set
    var mouthThickAdd = 0f; private set
    /** Eyes' dx while LookAround runs. */
    var lookActive = false; private set
    var lookDx = 0f; private set
    /** Replaces the glow when >= 0 (GlitchBurst flashes). */
    var glowAbs = -1f; private set

    private var t = 0f

    // GlitchBurst: n slots, each holding one random glitch state
    private var glitchFrames = 0
    private var glitchSlot = -1
    private var glitchFx = 0f
    private var glitchGlow = 0f

    fun start(g: Gesture) {
        gesture = g
        active = true
        t = 0f
        glitchFrames = Tuning.GlitchMinFrames + rng.nextInt(Tuning.GlitchMaxFrames - Tuning.GlitchMinFrames + 1)
        glitchSlot = -1
        update(0f)
    }

    /** Advances the gesture. Returns true on the frame it finishes (outputs are then neutral). */
    fun update(dtMs: Float): Boolean {
        val g = gesture
        if (!active || g == null) return false
        t += dtMs
        resetOutputs()
        if (t >= g.durationMs) {
            active = false
            gesture = null
            return true
        }
        val p = t / g.durationMs
        when (g) {
            Gesture.ShakeNo ->
                fx = Tuning.ShakeNoUnits * (1f - Easing.easeOutQuad(p)) * sin(TWO_PI * Tuning.ShakeNoCycles * p).toFloat()
            Gesture.NodYes -> fy = Tuning.NodYesUnits * sin(TWO_PI * Tuning.NodYesCycles * p).toFloat()
            Gesture.Bounce -> bounce()
            Gesture.Jolt -> scaleMul = 1f + (Tuning.JoltScale - 1f) * exp(-t / Tuning.JoltTauMs)
            Gesture.GlitchBurst -> glitch(p)
            Gesture.Giggle -> fy = Tuning.GiggleUnits * sin(TWO_PI * Tuning.GiggleHz * t / 1000.0).toFloat()
            Gesture.Yawn -> {
                val e = yawnEnvelope(p)
                mouthWAdd = Tuning.YawnMouthGrow * e
                mouthThickAdd = Tuning.YawnThickGrow * e
                eyeOpenMul = 1f - e
            }
            Gesture.LookAround -> lookAround()
        }
        return false
    }

    private fun resetOutputs() {
        fx = 0f; fy = 0f; scaleMul = 1f; eyeOpenMul = 1f
        mouthWAdd = 0f; mouthThickAdd = 0f
        lookActive = false; lookDx = 0f
        glowAbs = -1f
    }

    /** Up with easeOutQuad, back down with easeInQuad, then a small squash on landing. */
    private fun bounce() {
        val up = Tuning.BounceUpMs
        val down = up + Tuning.BounceDownMs
        when {
            t < up -> fy = -Tuning.BounceUnits * Easing.easeOutQuad(t / up)
            t < down -> fy = -Tuning.BounceUnits * (1f - Easing.easeInQuad((t - up) / Tuning.BounceDownMs))
            else -> {
                val q = ((t - down) / Tuning.BounceSquashMs).coerceIn(0f, 1f)
                scaleMul = 1f - (1f - Tuning.BounceSquash) * sin(PI.toFloat() * q)
            }
        }
    }

    private fun glitch(p: Float) {
        val slot = (p * glitchFrames).toInt().coerceAtMost(glitchFrames - 1)
        if (slot != glitchSlot) {
            glitchSlot = slot
            glitchFx = (rng.nextFloat() * 2f - 1f) * Tuning.GlitchShakeUnits
            glitchGlow = if (rng.nextBoolean()) 1f else 0f
        }
        fx = glitchFx
        glowAbs = glitchGlow
    }

    /** 0 -> 1 over the first 35%, held until 65%, back to 0 by the end. */
    private fun yawnEnvelope(p: Float): Float = when {
        p < 0.35f -> Easing.easeInOutCubic(p / 0.35f)
        p < 0.65f -> 1f
        else -> 1f - Easing.easeInOutCubic((p - 0.65f) / 0.35f)
    }

    /** Sweep left, then right, then back to centre, one segment each. */
    private fun lookAround() {
        val seg = Tuning.LookAroundSegmentMs
        val r = Tuning.LookRangeX
        lookActive = true
        lookDx = when {
            t < seg -> -r * Easing.easeInOutCubic(t / seg)
            t < 2 * seg -> -r + 2f * r * Easing.easeInOutCubic((t - seg) / seg)
            t < 3 * seg -> r * (1f - Easing.easeInOutCubic((t - 2 * seg) / seg))
            else -> 0f
        }
    }

    private companion object {
        const val TWO_PI = 2.0 * PI
    }
}
