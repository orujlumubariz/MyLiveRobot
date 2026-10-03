package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.random.Random

/**
 * Holds the FaceParams that get drawn. Pure Kotlin, driven by [update] with a frame delta,
 * allocation-free after construction.
 *
 * Layers (spec 04): mood (springs toward the table row, kept in [base]), look, blink,
 * drift + breathing, then the per-mood idle micro-animation ([IdleMotion]) on top.
 * The gesture layer comes later.
 *
 * Mood changes (spec 04): if an eye kind differs, a blink-swap runs (close, swap kinds and snap
 * numbers while closed, open). If only the mouth kind differs, the mouth cross-fades. Otherwise
 * the numbers just ease. Requests closer than [Tuning.MinMoodChangeMs] are queued, latest wins.
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
    private var queued: Mood? = null

    private enum class Swap { NONE, CLOSING, OPENING }
    private enum class Fade { NONE, OUT, IN }

    private var swap = Swap.NONE
    private var swapMs = 0f
    private var swapMult = 1f // eye `open` multiplier owned by the blink-swap
    private var nextSpec: MoodSpec = target
    private var nextTauMs = tauMs

    private var fade = Fade.NONE
    private var fadeMs = 0f
    private var mouthAlpha = 1f

    private val blinker = Blinker(rng)
    private val look = LookWander(rng)
    private val drift = Drift()
    private val idle = IdleMotion(rng)

    init {
        Moods.apply(base, target)
        copyBaseToFace()
        idle.start(initial)
    }

    fun setMood(newMood: Mood) {
        if (newMood == mood) {
            queued = null // asking for the current mood cancels a queued change
            return
        }
        if (sinceMoodMs < Tuning.MinMoodChangeMs) {
            queued = newMood
        } else {
            startMood(newMood)
        }
    }

    private fun startMood(newMood: Mood) {
        val spec = Moods.spec(newMood)
        val eyesDiffer = spec.left.kind != target.left.kind || spec.right.kind != target.right.kind
        val mouthDiffers = spec.mouth.kind != target.mouth.kind
        mood = newMood
        queued = null
        sinceMoodMs = 0f
        nextSpec = spec
        nextTauMs = tauFor(newMood)
        if (eyesDiffer) {
            swap = Swap.CLOSING
            swapMs = 0f
        } else {
            target = spec
            tauMs = nextTauMs
            if (mouthDiffers) {
                fade = Fade.OUT
                fadeMs = 0f
            }
        }
        if (lookScale(newMood) == 0f) look.recentre()
        idle.start(newMood)
    }

    fun update(dtMs: Float) {
        val dt = dtMs.coerceIn(0f, Tuning.MaxDtMs)
        sinceMoodMs += dt
        queued?.let { if (sinceMoodMs >= Tuning.MinMoodChangeMs) startMood(it) }
        updateSwap(dt)
        updateFade(dt)

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

        // 3. blink layer (the blink-swap multiplier applies to every kind, the blink only to some)
        blinker.update(dt, blinkProfile(mood), sinceMoodMs > Tuning.BlinkSuppressAfterMoodMs)
        face.leftEye.open = base.leftEye.open * swapMult * blinkMult(face.leftEye.kind)
        face.rightEye.open = base.rightEye.open * swapMult * blinkMult(face.rightEye.kind)
        face.mouth.alpha = mouthAlpha

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

        // 5. per-mood idle micro-animation, added on top
        idle.update(dt)
        face.fy += idle.fy
        face.leftEye.dy += idle.eyeDy
        face.rightEye.dy += idle.eyeDy + idle.rightEyeDy
        face.leftEye.tilt += idle.tilt
        face.rightEye.tilt += idle.tilt
        face.leftEye.w *= idle.eyeWMul
        face.rightEye.w *= idle.eyeWMul
        face.leftEye.spin = idle.spinLeft
        face.rightEye.spin = idle.spinRight
        face.mouth.dx = idle.mouthDx
        face.mouth.dy += idle.mouthDy
        if (idle.glowAbs >= 0f) face.glow += (idle.glowAbs - face.glow) * idle.glowBlend
        face.glow *= idle.glowMul
        face.alpha = idle.faceAlpha
    }

    private fun blinkMult(kind: EyeKind) = if (Blinker.canBlink(kind)) blinker.mult else 1f

    /** Blink-swap: close, swap kinds and snap numbers while closed, open. */
    private fun updateSwap(dt: Float) {
        when (swap) {
            Swap.NONE -> return
            Swap.CLOSING -> {
                swapMs += dt
                if (swapMs < Tuning.SwapCloseMs) {
                    swapMult = 1f - Easing.easeInOutCubic(swapMs / Tuning.SwapCloseMs)
                } else {
                    swapMult = 0f
                    val mouthDiffers = nextSpec.mouth.kind != target.mouth.kind
                    target = nextSpec
                    tauMs = nextTauMs
                    Moods.applyEye(base.leftEye, target.left)
                    Moods.applyEye(base.rightEye, target.right)
                    if (mouthDiffers) Moods.applyMouth(base.mouth, target.mouth)
                    swap = Swap.OPENING
                    swapMs = 0f
                }
            }
            Swap.OPENING -> {
                swapMs += dt
                val t = (swapMs / Tuning.SwapOpenMs).coerceAtMost(1f)
                swapMult = if (popsIn(mood)) Easing.easeOutBack(t) else Easing.easeInOutCubic(t)
                if (t >= 1f) {
                    swapMult = 1f
                    swap = Swap.NONE
                }
            }
        }
    }

    /** Mouth-only kind change: fade out, switch, fade in. */
    private fun updateFade(dt: Float) {
        when (fade) {
            Fade.NONE -> return
            Fade.OUT -> {
                fadeMs += dt
                val half = Tuning.MouthFadeMs / 2f
                if (fadeMs < half) {
                    mouthAlpha = 1f - fadeMs / half
                } else {
                    mouthAlpha = 0f
                    Moods.applyMouth(base.mouth, target.mouth)
                    fade = Fade.IN
                    fadeMs = 0f
                }
            }
            Fade.IN -> {
                fadeMs += dt
                val half = Tuning.MouthFadeMs / 2f
                mouthAlpha = (fadeMs / half).coerceAtMost(1f)
                if (fadeMs >= half) fade = Fade.NONE
            }
        }
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
        d.dx = s.dx
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

        /** Moods whose eyes pop open with easeOutBack after a blink-swap. */
        fun popsIn(mood: Mood): Boolean = mood == Mood.Surprised || mood == Mood.Curious || mood == Mood.Love

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
