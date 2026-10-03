package com.mylive.robot.face

import com.mylive.robot.Tuning

/**
 * Holds the FaceParams that get drawn and eases them toward the active mood's table row.
 * Pure Kotlin, driven by [update] with a frame delta. Allocation-free after construction.
 *
 * Only the mood layer exists so far (spec 04, layer 1). Kinds switch instantly for now;
 * the blink-swap that hides the switch arrives in task 8.
 */
class FaceAnimator(initial: Mood = Mood.Neutral) {
    val face = FaceParams()

    var mood: Mood = initial
        private set

    private var target: MoodSpec = Moods.spec(initial)
    private var tauMs: Float = tauFor(initial)

    init {
        Moods.apply(face, target)
    }

    fun setMood(newMood: Mood) {
        mood = newMood
        target = Moods.spec(newMood)
        tauMs = tauFor(newMood)
        face.leftEye.kind = target.left.kind
        face.rightEye.kind = target.right.kind
        face.mouth.kind = target.mouth.kind
    }

    fun update(dtMs: Float) {
        val dt = dtMs.coerceIn(0f, Tuning.MaxDtMs)
        val k = Easing.springFactor(dt, tauMs)
        easeEye(face.leftEye, target.left, k)
        easeEye(face.rightEye, target.right, k)
        val m = face.mouth
        m.w = Easing.approach(m.w, target.mouth.w, k)
        m.thick = Easing.approach(m.thick, target.mouth.thick, k)
        m.dy = Easing.approach(m.dy, target.mouth.dy, k)
        m.tilt = Easing.approach(m.tilt, target.mouth.tilt, k)
        face.glow = Easing.approach(face.glow, target.glow, k)
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

    private companion object {
        fun tauFor(mood: Mood): Float = when (mood) {
            Mood.Sad, Mood.Tired, Mood.Sleeping -> Tuning.TauSlowMs
            else -> Tuning.TauDefaultMs
        }
    }
}
