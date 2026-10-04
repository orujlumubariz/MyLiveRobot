package com.mylive.robot.behavior

import com.mylive.robot.Tuning
import com.mylive.robot.face.Gesture
import com.mylive.robot.face.Mood
import kotlin.math.abs

/** The touch reactions of spec 05, as data for [BehaviorEngine.react]. */
object Triggers {
    const val TAP = "Tap"
    const val EYE_TAP = "EyeTap"
    const val DOUBLE_TAP = "DoubleTap"
    const val LONG_PRESS = "LongPress"
    const val HAPPY_RELEASE = "HappyRelease"
    const val PET = "Pet"
    const val LOVE_RELEASE = "LoveRelease"
    const val RAPID_TAPS = "RapidTaps"
    const val VERY_RAPID_TAPS = "VeryRapidTaps"
    const val SWIPE = "Swipe"
    const val WAKE = "WakeUp"

    /** Curious, eyes on the tap point, then a Bounce. */
    fun tap(at: Vec2) = Reaction(
        TAP, Priority.Touch,
        listOf(
            Step(Mood.Curious, Tuning.TapLookMs, look = at),
            Step(Mood.Curious, Tuning.TapBounceMs, gesture = Gesture.Bounce),
        ),
    )

    /** Wink with the tapped eye (right eye: default Wink; left eye: mirrored), plus a Giggle. */
    fun eyeTap(rightEye: Boolean) = Reaction(
        EYE_TAP, Priority.Touch,
        listOf(
            Step(
                if (rightEye) Mood.Wink else Mood.WinkLeft, Tuning.EyeTapMs,
                gesture = Gesture.Giggle, keepFace = true,
            ),
        ),
    )

    fun doubleTap() = Reaction(
        DOUBLE_TAP, Priority.Touch,
        listOf(Step(Mood.Surprised, Tuning.DoubleTapReactionMs, gesture = Gesture.Jolt)),
    )

    /** Lasts until the finger lifts; the touch layer then plays [happyRelease]. */
    fun longPress() = Reaction(
        LONG_PRESS, Priority.Touch,
        listOf(Step(Mood.Happy, Tuning.HoldForever, gesture = Gesture.Giggle)),
    )

    fun happyRelease() = Reaction(HAPPY_RELEASE, Priority.Touch, listOf(Step(Mood.Happy, Tuning.HappyReleaseMs)))

    /** Lasts until the finger lifts; the eyes follow it via [BehaviorEngine.setLookTarget]. */
    fun pet() = Reaction(PET, Priority.Touch, listOf(Step(Mood.Love, Tuning.HoldForever)))

    fun loveRelease() = Reaction(LOVE_RELEASE, Priority.Touch, listOf(Step(Mood.Love, Tuning.LoveReleaseMs)))

    /** Angry, then Denying with a ShakeNo. Skips the debounce so it can follow quick taps. */
    fun rapidTaps() = Reaction(
        RAPID_TAPS, Priority.Touch,
        listOf(
            Step(Mood.Angry, Tuning.RapidAngryMs),
            Step(Mood.Denying, Tuning.RapidDenyMs, gesture = Gesture.ShakeNo),
        ),
        ignoreDebounce = true,
    )

    /** GlitchBurst, Broken, then Crazy. Glitch priority outranks everything else. */
    fun veryRapidTaps() = Reaction(
        VERY_RAPID_TAPS, Priority.Glitch,
        listOf(
            Step(Mood.Broken, Tuning.GlitchBrokenMs, gesture = Gesture.GlitchBurst),
            Step(Mood.Crazy, Tuning.GlitchCrazyMs),
        ),
    )

    /** Surprised, eyes snapping to the screen edge the swipe points at. [vx], [vy] in any units. */
    fun swipe(vx: Float, vy: Float): Reaction {
        val len = kotlin.math.hypot(vx, vy)
        val nx = if (len == 0f) 0f else vx / len
        val ny = if (len == 0f) 0f else vy / len
        val target = Vec2(0.5f + nx * 0.5f, 0.5f + ny * 0.5f)
        return Reaction(SWIPE, Priority.Touch, listOf(Step(Mood.Surprised, Tuning.SwipeReactionMs, look = target)))
    }

    /** A touch on a sleeping robot: Waking, then Curious (Tired inside the sleep window), then the base face. */
    fun wakeUp(inSleepWindow: Boolean) = Reaction(
        WAKE, Priority.Touch,
        listOf(
            Step(Mood.Waking, Tuning.WakeWakingMs),
            Step(if (inSleepWindow) Mood.Tired else Mood.Curious, Tuning.WakeFollowUpMs),
        ),
    )

    @Suppress("unused")
    private fun same(a: Float, b: Float) = abs(a - b) < 1e-6f
}
