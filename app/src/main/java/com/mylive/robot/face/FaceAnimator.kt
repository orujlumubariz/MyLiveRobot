package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.random.Random

/**
 * Holds the FaceParams that get drawn. Pure Kotlin, driven by [update] with a frame delta,
 * allocation-free after construction.
 *
 * Layers (spec 04): mood (springs toward the table row, kept in [base]), look, blink,
 * drift + breathing, then the per-mood idle micro-animation ([IdleMotion]) on top.
 * Then the gesture layer: one-shots that may swap the face and return to the previous mood.
 *
 * Mood changes (spec 04): if an eye kind differs, a blink-swap runs (close, swap kinds and snap
 * numbers while closed, open). If only the mouth kind differs, the mouth cross-fades. Otherwise
 * the numbers just ease. Requests closer than [Tuning.MinMoodChangeMs] are queued, latest wins.
 */
class   FaceAnimator(
    initial: Mood = Mood.Neutral,
    rng: Random = Random.Default,
    private val winkReopen: Boolean = Tuning.WinkReopenEnabled,
) {
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
    private var swapCloseMs = Tuning.SwapCloseMs
    private var swapOpenMs = Tuning.SwapOpenMs
    private var swapFrom = 0f // `open` multiplier the opening starts from (0 after a full close)
    private var swapPops = false // open with easeOutBack
    private var glowTarget = target.glow
    private var glowTauMs = tauMs
    private var nextSpec: MoodSpec = target
    private var nextTauMs = tauMs

    // Wink idle: the closed right eye periodically re-opens to the left eye's shape, then winks again.
    private enum class Wink { OFF, WAIT, CLOSE1, OPEN1, HOLD, CLOSE2, OPEN2 }
    private var wink = Wink.OFF
    private var winkMs = 0f
    private var rightMult = 1f // right-eye-only `open` multiplier owned by the wink idle
    private var rightTarget: EyeSpec = target.right

    private var fade = Fade.NONE
    private var fadeMs = 0f
    private var mouthAlpha = 1f

    private val blinker = Blinker(rng)
    private val look = LookWander(rng)
    private val drift = Drift()
    private val idle = IdleMotion(rng)
    private val gesture = GestureLayer(rng)

    /** True while a gesture runs. */
    val gestureActive: Boolean get() = gesture.active

    private var faceOverridden = false // a gesture swapped the face and must hand it back
    private var returnMood: Mood = initial

    init {
        Moods.apply(base, target)
        copyBaseToFace()
        idle.start(initial)
        if (initial == Mood.Wink && winkReopen) wink = Wink.WAIT
    }

    /** Plays a one-shot gesture. Gestures that have a face show it instantly, then return to the previous mood. */
    fun play(g: Gesture, keepFace: Boolean = false) {
        if (!faceOverridden) returnMood = mood
        val gestureFace = if (keepFace) null else g.face
        if (gestureFace != null && mood != gestureFace) {
            snapTo(gestureFace)
            faceOverridden = true
        }
        gesture.start(g)
    }

    /** Eyes look toward a normalised screen point (touch follow); null goes back to idle wander. */
    fun lookAt(target: com.mylive.robot.behavior.Vec2?) {
        if (target == null) look.release() else look.follow(target.x, target.y)
    }

    fun setMood(newMood: Mood) {
        if (faceOverridden) {
            returnMood = newMood // applied when the gesture ends
            return
        }
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

    /** Instant face change (no blink-swap) used by gestures. */
    private fun snapTo(newMood: Mood) {
        val spec = Moods.spec(newMood)
        mood = newMood
        target = spec
        rightTarget = spec.right
        tauMs = tauFor(newMood)
        nextSpec = spec
        glowTarget = spec.glow
        glowTauMs = tauFor(newMood)
        queued = null
        sinceMoodMs = 0f
        swap = Swap.NONE
        swapMult = 1f
        fade = Fade.NONE
        mouthAlpha = 1f
        wink = Wink.OFF
        rightMult = 1f
        Moods.apply(base, spec)
        copyBaseToFace()
        if (lookScale(newMood) == 0f) look.recentre()
        idle.start(newMood)
    }

    private fun startMood(newMood: Mood) {
        val spec = Moods.spec(newMood)
        val eyesDiffer = spec.left.kind != target.left.kind || spec.right.kind != target.right.kind
        val mouthDiffers = spec.mouth.kind != target.mouth.kind
        val previous = mood
        mood = newMood
        queued = null
        sinceMoodMs = 0f
        nextSpec = spec
        nextTauMs = tauFor(newMood)
        glowTarget = spec.glow
        glowTauMs = if (newMood == Mood.Sleeping) Tuning.GlowTauSleepMs else nextTauMs
        swapCloseMs = if (newMood == Mood.Sleeping) Tuning.SleepCloseMs else Tuning.SwapCloseMs
        swapOpenMs = if (previous == Mood.Sleeping) Tuning.WakeOpenMs else Tuning.SwapOpenMs
        swapPops = popsIn(newMood) || previous == Mood.Waking
        swapFrom = 0f
        wink = if (newMood == Mood.Wink && winkReopen) Wink.WAIT else Wink.OFF
        winkMs = 0f
        rightMult = 1f
        if (eyesDiffer) {
            swap = Swap.CLOSING
            swapMs = 0f
        } else {
            target = spec
            rightTarget = spec.right
            tauMs = nextTauMs
            if (mouthDiffers) {
                fade = Fade.OUT
                fadeMs = 0f
            }
            if (previous == Mood.Waking) {
                // same eye shapes: pop open from the sleepy half-open eyes with an overshoot
                base.leftEye.open = spec.left.open
                base.rightEye.open = spec.right.open
                swapFrom = Moods.spec(Mood.Waking).left.open
                swapMult = swapFrom
                swapOpenMs = Tuning.WakePopMs
                swap = Swap.OPENING
                swapMs = 0f
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
        updateWink(dt)

        // 1. mood layer
        val k = Easing.springFactor(dt, tauMs)
        easeEye(base.leftEye, target.left, k)
        easeEye(base.rightEye, rightTarget, k)
        val m = base.mouth
        m.w = Easing.approach(m.w, target.mouth.w, k)
        m.thick = Easing.approach(m.thick, target.mouth.thick, k)
        m.dy = Easing.approach(m.dy, target.mouth.dy, k)
        m.tilt = Easing.approach(m.tilt, target.mouth.tilt, k)
        base.glow = Easing.approach(base.glow, glowTarget, Easing.springFactor(dt, glowTauMs))
        base.bolt = Easing.approach(base.bolt, target.bolt, Easing.springFactor(dt, Tuning.BoltTauMs))
        copyBaseToFace()

        // 2. look layer
        look.update(dt, lookScale(mood))
        face.leftEye.dx = look.dx
        face.rightEye.dx = look.dx
        face.leftEye.dy = base.leftEye.dy + look.dy
        face.rightEye.dy = base.rightEye.dy + look.dy

        // 3. blink layer (the blink-swap multiplier applies to every kind, the blink only to some)
        blinker.update(dt, blinkProfile(mood), sinceMoodMs > Tuning.BlinkSuppressAfterMoodMs && !gesture.active)
        face.leftEye.open = base.leftEye.open * swapMult * blinkMult(face.leftEye.kind)
        face.rightEye.open = base.rightEye.open * swapMult * rightMult * blinkMult(face.rightEye.kind)
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

        // 6. gesture layer
        val gestureDone = gesture.update(dt)
        if (gesture.active) {
            face.fx += gesture.fx
            face.fy += gesture.fy
            face.scale *= gesture.scaleMul
            face.leftEye.open *= gesture.eyeOpenMul
            face.rightEye.open *= gesture.eyeOpenMul
            face.mouth.w += gesture.mouthWAdd
            face.mouth.thick += gesture.mouthThickAdd
            if (gesture.lookActive) {
                face.leftEye.dx = gesture.lookDx
                face.rightEye.dx = gesture.lookDx
            }
            if (gesture.glowAbs >= 0f) face.glow = gesture.glowAbs
        }
        if (gestureDone && faceOverridden) {
            faceOverridden = false
            if (returnMood != mood) startMood(returnMood)
        }
    }

    private fun blinkMult(kind: EyeKind) = if (Blinker.canBlink(kind)) blinker.mult else 1f

    /** Blink-swap: close, swap kinds and snap numbers while closed, open. */
    private fun updateSwap(dt: Float) {
        when (swap) {
            Swap.NONE -> return
            Swap.CLOSING -> {
                swapMs += dt
                if (swapMs < swapCloseMs) {
                    swapMult = 1f - Easing.easeInOutCubic(swapMs / swapCloseMs)
                } else {
                    swapMult = 0f
                    swapFrom = 0f
                    val mouthDiffers = nextSpec.mouth.kind != target.mouth.kind
                    target = nextSpec
                    rightTarget = target.right
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
                val t = (swapMs / swapOpenMs).coerceAtMost(1f)
                val eased = if (swapPops) Easing.easeOutBack(t) else Easing.easeInOutCubic(t)
                swapMult = swapFrom + (1f - swapFrom) * eased
                if (t >= 1f) {
                    swapMult = 1f
                    swap = Swap.NONE
                }
            }
        }
    }

    /** Wink idle: right eye closes, becomes the left eye's shape, opens, holds, then reverses. */
    private fun updateWink(dt: Float) {
        when (wink) {
            Wink.OFF -> return
            Wink.WAIT -> {
                if (swap != Swap.NONE) return // wait until the mood has fully arrived
                winkMs += dt
                if (winkMs >= Tuning.WinkReopenEveryMs) { wink = Wink.CLOSE1; winkMs = 0f }
            }
            Wink.CLOSE1 -> if (closeRight(dt, target.left)) wink = Wink.OPEN1
            Wink.OPEN1 -> if (openRight(dt)) wink = Wink.HOLD
            Wink.HOLD -> {
                winkMs += dt
                if (winkMs >= Tuning.WinkReopenHoldMs) { wink = Wink.CLOSE2; winkMs = 0f }
            }
            Wink.CLOSE2 -> if (closeRight(dt, target.right)) wink = Wink.OPEN2
            Wink.OPEN2 -> if (openRight(dt)) wink = Wink.WAIT
        }
    }

    /** Closes the right eye; when shut, swaps it to [next] and returns true. */
    private fun closeRight(dt: Float, next: EyeSpec): Boolean {
        winkMs += dt
        if (winkMs < Tuning.SwapCloseMs) {
            rightMult = 1f - Easing.easeInOutCubic(winkMs / Tuning.SwapCloseMs)
            return false
        }
        rightMult = 0f
        Moods.applyEye(base.rightEye, next)
        rightTarget = next
        winkMs = 0f
        return true
    }

    private fun openRight(dt: Float): Boolean {
        winkMs += dt
        val t = (winkMs / Tuning.SwapOpenMs).coerceAtMost(1f)
        rightMult = Easing.easeInOutCubic(t)
        if (t < 1f) return false
        rightMult = 1f
        winkMs = 0f
        return true
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
        face.bolt = base.bolt
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
            Mood.Sad, Mood.Tired -> Tuning.TauSlowMs
            Mood.Sleeping -> Tuning.TauSleepMs
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
