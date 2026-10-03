package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.random.Random

/**
 * Holds the FaceParams that get drawn. Pure Kotlin, driven by [update] with a frame delta,
 * allocation-free after construction.
 *
 * Layers (spec 04): mood (springs toward the table row, kept in [base]), look, blink,
 * drift + breathing. The gesture and glow layers come later. Kinds switch instantly for now;
 * the blink-swap that hides the switch arrives in task 8.
 */
class FaceAnimator(initial: Mood = Mood.Neutral, rng: Random = Random.Default) {
    /** What the renderer draws: [base] plus the ambient layers. */
    val face = FaceParams()

    /** Mood layer output: eased numbers only, no blink/look/drift. */
    private val base = FaceParams()

    var mood: Mood = initial
        private set

    private var target: MoodSpec = Moods.spec(initial)
    private var tauMs: Float = tauFor(initial)
    private var sinceMoodMs = Float.MAX_VALUE / 2f

    private val blinker = Blinker(rng)
    private val look = LookWander(rng)
    private val drift = Drift()

    init {
        Moods.apply(base, target)
        copyBaseToFace()
    }

    fun setMood(newMood: Mood) {
        mood = newMood
        target = Moods.spec(newMood)
        tauMs = tauFor(newMood)
        sinceMoodMs = 0f
        base.leftEye.kind = target.left.kind
        base.rightEye.kind = target.right.kind
        base.mouth.kind = target.mouth.kind
        copyBaseToFace()
        if (lookScale(newMood) == 0f) look.recentre()
    }

    fun update(dtMs: Float) {
        val dt = dtMs.coerceIn(0f, Tuning.MaxDtMs)
        sinceMoodMs += dt

        // 1. mood layer
        val k = Easing.springFactor(dt, tauMs)
        easeEye(base.leftEye, target.left, k)
        easeEye(base.rightEye, target.right, k)
        val m = base.mouth
        m.w = Easing.approach(m.w, target.mouth.w, k)
        m.thick = Easing.approach(m.thick, target.mouth.thick, k)
        m.dy = Easing.approach(m.dy, target.mouth.dy, k)
        m.tilt = Easing.approach(m.tilt, target.mouth.tilt, k)
        base.glow = Easing.approach(base.glow, target.glow, k)
        copyBaseToFace()

        // 2. look layer
        look.update(dt, lookScale(mood))
        face.leftEye.dx = look.dx
        face.rightEye.dx = look.dx
        face.leftEye.dy = base.leftEye.dy + look.dy
        face.rightEye.dy = base.rightEye.dy + look.dy

        // 3. blink layer
        blinker.update(dt, blinkProfile(mood), sinceMoodMs > Tuning.BlinkSuppressAfterMoodMs)
        if (Blinker.canBlink(face.leftEye.kind)) face.leftEye.open = base.leftEye.open * blinker.mult
        if (Blinker.canBlink(face.rightEye.kind)) face.rightEye.open = base.rightEye.open * blinker.mult

        // 4. drift + breathing
        val sleeping = mood == Mood.Sleeping
        drift.update(
            dt,
            driftMul(mood),
            if (sleeping) Tuning.BreathSleepAmp else Tuning.BreathAmp,
            if (sleeping) Tuning.BreathSleepPeriodMs else Tuning.BreathPeriodMs,
        )
        face.fx = drift.fx
        face.fy = drift.fy
        face.scale = drift.scale
    }

    private fun easeEye(e: EyeParams, t: EyeSpec, k: Float) {
        e.w = Easing.approach(e.w, t.w, k)
        e.h = Easing.approach(e.h, t.h, k)
        e.open = Easing.approach(e.open, t.open, k)
        e.tilt = Easing.approach(e.tilt, t.tilt, k)
        e.lidTop = Easing.approach(e.lidTop, t.lidTop, k)
        e.dy = Easing.approach(e.dy, t.dy, k)
        e.thick = Easing.approach(e.thick, t.thick, k)
    }

    private fun copyBaseToFace() {
        copyEye(base.leftEye, face.leftEye)
        copyEye(base.rightEye, face.rightEye)
        val s = base.mouth
        val d = face.mouth
        d.kind = s.kind
        d.w = s.w
        d.thick = s.thick
        d.dy = s.dy
        d.tilt = s.tilt
        face.glow = base.glow
    }

    private fun copyEye(s: EyeParams, d: EyeParams) {
        d.kind = s.kind
        d.w = s.w
        d.h = s.h
        d.open = s.open
        d.tilt = s.tilt
        d.lidTop = s.lidTop
        d.dx = s.dx
        d.dy = s.dy
        d.thick = s.thick
        d.spin = s.spin
    }

    private companion object {
        fun tauFor(mood: Mood): Float = when (mood) {
            Mood.Sad, Mood.Tired, Mood.Sleeping -> Tuning.TauSlowMs
            else -> Tuning.TauDefaultMs
        }

        /** Scale of the idle look range per mood (spec 04). Crazy ignores looking (spiral). */
        fun lookScale(mood: Mood): Float = when (mood) {
            Mood.Sleeping, Mood.Crazy -> 0f
            Mood.Tired -> 0.3f
            Mood.Bored -> 1.2f
            Mood.Surprised -> 0.2f
            Mood.Angry -> 0.4f
            else -> 1f
        }

        fun blinkProfile(mood: Mood): BlinkProfile = when (mood) {
            Mood.Tired, Mood.LowBattery -> BlinkProfile.Tired
            else -> BlinkProfile.Normal
        }

        fun driftMul(mood: Mood): Float = when (mood) {
            Mood.Sleeping -> Tuning.DriftSleepingMul
            Mood.Crazy -> Tuning.DriftCrazyMul
            else -> 1f
        }
    }
}
