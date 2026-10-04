package com.mylive.robot.behavior

import com.mylive.robot.Tuning
import com.mylive.robot.face.FaceLayout
import kotlin.math.hypot

/**
 * Turns raw single-finger input into the touch reactions of spec 05. Pure Kotlin: feed it pointer
 * events and call [update] every frame with the same clock the engine uses (ms). Coordinates are
 * screen pixels; set [screenW]/[screenH] and [slopPx] from the UI.
 */
class TouchInterpreter(private val engine: BehaviorEngine) {
    var screenW = 1f
    var screenH = 1f
    var slopPx = 24f

    // current touch
    private var down = false
    private var downMs = 0L
    private var downX = 0f
    private var downY = 0f
    private var curX = 0f
    private var curY = 0f
    private var moved = false
    private var dragStartMs = 0L
    private var consumed = false // this touch only woke the robot
    private var longPress = false
    private var pet = false

    // taps
    private val taps = ArrayDeque<Long>()
    private var pendingTapMs = -1L
    private var pendingX = 0f
    private var pendingY = 0f
    private var suppressUntilMs = 0L
    private var rapidCooldownUntilMs = 0L

    fun onDown(nowMs: Long, x: Float, y: Float) {
        val wasAsleep = engine.baseState == BaseState.Sleeping
        engine.noteInteraction(nowMs)
        down = true
        downMs = nowMs
        downX = x; downY = y; curX = x; curY = y
        moved = false
        longPress = false
        pet = false
        consumed = false
        if (wasAsleep) {
            engine.react(Triggers.wakeUp(engine.inSleepWindowNow), nowMs)
            consumed = true // the touch that wakes the robot is not also a tap
        }
    }

    fun onMove(nowMs: Long, x: Float, y: Float) {
        if (!down) return
        curX = x; curY = y
        if (!moved && hypot(x - downX, y - downY) > slopPx) {
            moved = true
            dragStartMs = nowMs
        }
        if (pet && !consumed) engine.setLookTarget(normalised(x, y))
    }

    /** A second finger landed or the system cancelled the touch: forget it, fire nothing. */
    fun onCancel(nowMs: Long) {
        if (down && !consumed) finishHold(nowMs)
        down = false
        consumed = false
    }

    /** [vx], [vy]: release velocity in px/s. */
    fun onUp(nowMs: Long, x: Float, y: Float, vx: Float, vy: Float) {
        if (!down) return
        down = false
        curX = x; curY = y
        if (consumed) {
            consumed = false
            return
        }
        when {
            longPress || pet -> finishHold(nowMs)
            moved -> {
                if (hypot(vx, vy) >= Tuning.SwipeSpeedPxPerS) engine.react(Triggers.swipe(vx, vy), nowMs)
            }
            else -> handleTap(nowMs, x, y)
        }
    }

    /** Call every frame: long press, petting, and the lone-tap wait all need the clock. */
    fun update(nowMs: Long) {
        if (down && !consumed) {
            if (!moved && !longPress && nowMs - downMs >= Tuning.LongPressMs) {
                longPress = true
                engine.react(Triggers.longPress(), nowMs)
            }
            if (moved && !longPress && !pet && nowMs - dragStartMs >= Tuning.PetMs) {
                pet = true
                engine.react(Triggers.pet(), nowMs)
                engine.setLookTarget(normalised(curX, curY))
            }
        }
        if (pendingTapMs >= 0L && nowMs - pendingTapMs >= Tuning.DoubleTapMs) {
            pendingTapMs = -1L
            fireSingleTap(nowMs, pendingX, pendingY)
        }
    }

    /** End of a long press or a petting drag: hand over to the "release" reaction if it is still ours. */
    private fun finishHold(nowMs: Long) {
        if (longPress) {
            if (engine.reactionName == Triggers.LONG_PRESS) engine.react(Triggers.happyRelease(), nowMs)
        } else if (pet) {
            engine.setLookTarget(null)
            if (engine.reactionName == Triggers.PET) engine.react(Triggers.loveRelease(), nowMs)
        }
        longPress = false
        pet = false
    }

    private fun handleTap(nowMs: Long, x: Float, y: Float) {
        taps.addLast(nowMs)
        while (taps.isNotEmpty() && nowMs - taps.first() > Tuning.VeryRapidTapWindowMs) taps.removeFirst()
        val inRapidWindow = taps.count { nowMs - it <= Tuning.RapidTapWindowMs }

        when {
            taps.size >= Tuning.VeryRapidTapCount -> {
                engine.react(Triggers.veryRapidTaps(), nowMs)
                taps.clear()
                pendingTapMs = -1L
                val until = nowMs + Tuning.GlitchBrokenMs + Tuning.GlitchCrazyMs
                suppressUntilMs = until
                rapidCooldownUntilMs = until
            }
            inRapidWindow >= Tuning.RapidTapCount && nowMs >= rapidCooldownUntilMs -> {
                engine.react(Triggers.rapidTaps(), nowMs)
                pendingTapMs = -1L
                val until = nowMs + Tuning.RapidAngryMs + Tuning.RapidDenyMs
                suppressUntilMs = until
                rapidCooldownUntilMs = until
            }
            nowMs < suppressUntilMs -> pendingTapMs = -1L // counted, but must not interrupt the reaction
            pendingTapMs >= 0L && nowMs - pendingTapMs <= Tuning.DoubleTapMs -> {
                pendingTapMs = -1L
                engine.react(Triggers.doubleTap(), nowMs)
            }
            else -> {
                pendingTapMs = nowMs
                pendingX = x
                pendingY = y
            }
        }
    }

    private fun fireSingleTap(nowMs: Long, x: Float, y: Float) {
        val scale = FaceLayout.scale(screenW, screenH)
        val vx = (x - FaceLayout.offsetX(screenW, scale)) / scale
        val vy = (y - FaceLayout.offsetY(screenH, scale)) / scale
        val dl = hypot(vx - Tuning.LeftEyeX, vy - Tuning.EyeY)
        val dr = hypot(vx - Tuning.RightEyeX, vy - Tuning.EyeY)
        val nearest = minOf(dl, dr)
        if (nearest <= Tuning.EyeTapRadius) {
            engine.react(Triggers.eyeTap(rightEye = dr <= dl), nowMs)
        } else {
            engine.react(Triggers.tap(normalised(x, y)), nowMs)
        }
    }

    private fun normalised(x: Float, y: Float) = Vec2(x / screenW, y / screenH)
}
