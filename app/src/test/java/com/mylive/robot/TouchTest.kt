package com.mylive.robot

import com.mylive.robot.behavior.BaseState
import com.mylive.robot.behavior.BehaviorEngine
import com.mylive.robot.behavior.BehaviorOutput
import com.mylive.robot.behavior.Inputs
import com.mylive.robot.behavior.Priority
import com.mylive.robot.behavior.Reaction
import com.mylive.robot.behavior.Step
import com.mylive.robot.behavior.TouchInterpreter
import com.mylive.robot.behavior.Triggers
import com.mylive.robot.behavior.Vec2
import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.Gesture
import com.mylive.robot.face.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Fake-clock harness: a 2340x1080 screen, so the face is scale 1.8 with a 270 px left margin. */
private class TouchSim(minute: Int = 12 * 60, seed: Int = 1) {
    val engine = BehaviorEngine(Random(seed))
    val touch = TouchInterpreter(engine).also { it.screenW = 2340f; it.screenH = 1080f; it.slopPx = 24f }
    val inputs = Inputs(minuteOfDay = minute)
    var now = 1_000L
    var last: BehaviorOutput = engine.update(now, inputs)
    val gestures = ArrayList<Triple<Long, Gesture, Boolean>>()
    val reactionsSeen = LinkedHashSet<String>()

    init {
        frame()
    }

    private fun frame() {
        touch.update(now)
        last = engine.update(now, inputs)
        last.gesture?.let { gestures.add(Triple(now, it, last.gestureKeepsFace)) }
        engine.reactionName?.let { reactionsSeen.add(it) }
    }

    fun run(ms: Long, step: Long = 10, onTick: (BehaviorOutput) -> Unit = {}) {
        val end = now + ms
        while (now < end) {
            now += step
            frame()
            onTick(last)
        }
    }

    fun tap(x: Float, y: Float, holdMs: Long = 40) {
        touch.onDown(now, x, y)
        run(holdMs)
        touch.onUp(now, x, y, 0f, 0f)
    }

    /** [n] taps [gapMs] apart (start to start), then the clock advances to just after the last tap. */
    fun tapBurst(n: Int, gapMs: Long, x: Float = PLAIN_X, y: Float = PLAIN_Y) {
        repeat(n) {
            tap(x, y, holdMs = 30)
            if (it < n - 1) run(gapMs - 30)
        }
    }

    fun gestureNames() = gestures.map { it.second }

    companion object {
        // virtual (500, 83): far from both eyes
        const val PLAIN_X = 1170f
        const val PLAIN_Y = 150f
        // eye centres: virtual (300,230) and (700,230) -> px
        const val LEFT_EYE_X = 810f
        const val RIGHT_EYE_X = 1530f
        const val EYE_Y = 414f
    }
}

class TouchTest {
    @Test fun singleTapShowsCuriousLooksAtTheTapThenBounces() {
        val s = TouchSim()
        s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        s.run(150)
        assertNull(s.engine.reactionName) // a lone tap waits to see whether a second one follows
        s.run(200)
        assertEquals(Triggers.TAP, s.engine.reactionName)
        assertEquals(Mood.Curious, s.last.mood)
        val look = s.last.lookTarget
        assertNotNull(look)
        assertEquals(1170f / 2340f, look!!.x, 0.001f)
        assertEquals(150f / 1080f, look.y, 0.001f)
        s.run(500)
        assertEquals(listOf(Gesture.Bounce), s.gestureNames())
        assertNull(s.last.lookTarget)
        s.run(600)
        assertNull(s.engine.reactionName)
        assertEquals(Mood.Neutral, s.last.mood)
    }

    @Test fun tapNearAnEyeWinksWithThatEyeAndGiggles() {
        val right = TouchSim()
        right.tap(TouchSim.RIGHT_EYE_X, TouchSim.EYE_Y)
        right.run(400)
        assertEquals(Triggers.EYE_TAP, right.engine.reactionName)
        assertEquals(Mood.Wink, right.last.mood)
        val g = right.gestures.single()
        assertEquals(Gesture.Giggle, g.second)
        assertTrue("Giggle must keep the wink face", g.third)
        right.run(1_000)
        assertNull(right.engine.reactionName)

        val left = TouchSim()
        left.tap(TouchSim.LEFT_EYE_X, TouchSim.EYE_Y)
        left.run(400)
        assertEquals(Mood.WinkLeft, left.last.mood) // tapped the left eye: the sides swap
    }

    @Test fun eyeAreaIsWithin120VirtualUnits() {
        // 110 units right of the right eye is still an eye tap; 130 is a plain tap
        val near = TouchSim()
        near.tap(TouchSim.RIGHT_EYE_X + 110f * 1.8f, TouchSim.EYE_Y)
        near.run(400)
        assertEquals(Triggers.EYE_TAP, near.engine.reactionName)
        val far = TouchSim()
        far.tap(TouchSim.RIGHT_EYE_X + 130f * 1.8f, TouchSim.EYE_Y)
        far.run(400)
        assertEquals(Triggers.TAP, far.engine.reactionName)
    }

    @Test fun doubleTapIsSurprisedWithAJolt() {
        val s = TouchSim()
        s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        s.run(120)
        s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        s.run(50)
        assertEquals(Triggers.DOUBLE_TAP, s.engine.reactionName)
        assertEquals(Mood.Surprised, s.last.mood)
        assertEquals(listOf(Gesture.Jolt), s.gestureNames())
        s.run(1_500)
        assertNull(s.engine.reactionName)
        assertFalse(Triggers.TAP in s.reactionsSeen) // the first tap did not fire on its own
    }

    @Test fun longPressStaysHappyWhileHeldThenHappyForTwoSecondsAfterRelease() {
        val s = TouchSim()
        s.touch.onDown(s.now, 1170f, 500f)
        s.run(500)
        assertNull(s.engine.reactionName)
        s.run(200)
        assertEquals(Triggers.LONG_PRESS, s.engine.reactionName)
        assertEquals(Mood.Happy, s.last.mood)
        assertEquals(listOf(Gesture.Giggle), s.gestureNames())
        s.run(5_000)
        assertEquals(Triggers.LONG_PRESS, s.engine.reactionName) // still held
        assertEquals(Mood.Happy, s.last.mood)

        s.touch.onUp(s.now, 1170f, 500f, 0f, 0f)
        s.run(100)
        assertEquals(Triggers.HAPPY_RELEASE, s.engine.reactionName)
        s.run(1_800)
        assertEquals(Mood.Happy, s.last.mood)
        s.run(300)
        assertNull(s.engine.reactionName)
        assertEquals(Mood.Neutral, s.last.mood)
    }

    @Test fun pettingTurnsLoveFollowsTheFingerAndLastsThreeSecondsAfterRelease() {
        val s = TouchSim()
        var x = 1000f
        s.touch.onDown(s.now, x, 500f)
        var pettingAt = -1L
        val start = s.now
        repeat(15) {
            s.run(100)
            x += 40f
            s.touch.onMove(s.now, x, 500f)
            if (pettingAt < 0 && s.engine.reactionName == Triggers.PET) pettingAt = s.now - start
        }
        assertTrue("petting began at $pettingAt", pettingAt in 1_000L..1_300L)
        s.run(20) // let the last move reach the engine
        assertEquals(Mood.Love, s.last.mood)
        val look = s.last.lookTarget
        assertNotNull(look)
        assertEquals(x / 2340f, look!!.x, 0.001f) // follows the finger

        s.touch.onUp(s.now, x, 500f, 20f, 0f)
        s.run(100)
        assertEquals(Triggers.LOVE_RELEASE, s.engine.reactionName)
        assertNull(s.last.lookTarget)
        s.run(2_800)
        assertEquals(Mood.Love, s.last.mood)
        s.run(300)
        assertEquals(Mood.Neutral, s.last.mood)
    }

    @Test fun aShortSlowDragDoesNothing() {
        val s = TouchSim()
        s.touch.onDown(s.now, 1000f, 500f)
        s.run(100)
        s.touch.onMove(s.now, 1100f, 500f)
        s.run(100)
        s.touch.onUp(s.now, 1100f, 500f, 400f, 0f)
        s.run(2_000)
        assertTrue(s.reactionsSeen.isEmpty())
    }

    @Test fun fiveRapidTapsGiveAngryThenDenyingWithAShake() {
        val s = TouchSim()
        s.tapBurst(5, 200)
        s.run(50)
        assertEquals(Triggers.RAPID_TAPS, s.engine.reactionName)
        assertEquals(Mood.Angry, s.last.mood)

        // more tapping must not interrupt the reaction
        s.run(150)
        s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        s.run(150)
        s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        s.run(200)
        assertEquals(Triggers.RAPID_TAPS, s.engine.reactionName)
        assertEquals(Mood.Angry, s.last.mood)

        s.run(2_700) // Angry lasts 3 s from the fifth tap
        assertEquals(Mood.Denying, s.last.mood)
        assertEquals(Gesture.ShakeNo, s.gestureNames().last())
        s.run(1_100)
        assertNull(s.engine.reactionName)
        assertEquals(Mood.Neutral, s.last.mood)
    }

    @Test fun tenVeryRapidTapsGlitchThenBrokenThenCrazyThenBack() {
        val s = TouchSim()
        s.tapBurst(10, 150)
        s.run(50)
        assertEquals(Triggers.VERY_RAPID_TAPS, s.engine.reactionName)
        assertEquals(Mood.Broken, s.last.mood)
        assertEquals(Gesture.GlitchBurst, s.gestureNames().last())
        s.run(3_000)
        assertEquals(Mood.Crazy, s.last.mood)
        s.run(2_100)
        assertNull(s.engine.reactionName)
        assertEquals(Mood.Neutral, s.last.mood)
        assertFalse("the Angry sequence was replaced before it could shake", Gesture.ShakeNo in s.gestureNames())
    }

    @Test fun fastSwipeIsSurprisedAndEyesSnapToThatSide() {
        val right = TouchSim()
        right.touch.onDown(right.now, 500f, 500f)
        right.run(40)
        right.touch.onMove(right.now, 1100f, 520f)
        right.run(20)
        right.touch.onUp(right.now, 1100f, 520f, 3_000f, 100f)
        right.run(50)
        assertEquals(Triggers.SWIPE, right.engine.reactionName)
        assertEquals(Mood.Surprised, right.last.mood)
        assertTrue(right.last.lookTarget!!.x > 0.95f)
        right.run(800)
        assertNull(right.engine.reactionName)

        val left = TouchSim()
        left.touch.onDown(left.now, 1500f, 500f)
        left.run(40)
        left.touch.onMove(left.now, 900f, 500f)
        left.touch.onUp(left.now, 900f, 500f, -3_000f, 0f)
        left.run(50)
        assertTrue(left.last.lookTarget!!.x < 0.05f)
    }

    @Test fun touchWhileSleepingInTheSleepWindowGoesWakingThenTiredAndStaysAwakeThreeMinutes() {
        val s = TouchSim(minute = 23 * 60 + 30)
        s.run(500)
        assertEquals(BaseState.Sleeping, s.engine.baseState)

        s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        s.run(100)
        assertEquals(Mood.Waking, s.last.mood)
        s.run(600)
        assertEquals(Mood.Tired, s.last.mood)
        s.run(900)
        assertEquals(BaseState.Awake, s.engine.baseState)
        assertEquals(Mood.Neutral, s.last.mood)
        assertEquals(setOf(Triggers.WAKE), s.reactionsSeen) // the waking touch is not also a tap
        s.run(2 * 60_000L, step = 1_000)
        assertEquals(BaseState.Awake, s.engine.baseState)
        s.run(70_000L, step = 1_000)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
    }

    @Test fun touchAfterIdleSleepGoesWakingThenCurious() {
        val s = TouchSim()
        s.run(10 * 60_000L + 10_000L, step = 1_000)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        s.run(100)
        assertEquals(Mood.Waking, s.last.mood)
        s.run(600)
        assertEquals(Mood.Curious, s.last.mood)
        s.run(1_000)
        assertEquals(Mood.Neutral, s.last.mood)
        assertEquals(BaseState.Awake, s.engine.baseState)
    }

    @Test fun aSecondFingerCancelsTheTouch() {
        val s = TouchSim()
        s.touch.onDown(s.now, 1170f, 500f)
        s.run(100)
        s.touch.onCancel(s.now)
        s.touch.onUp(s.now, 1170f, 500f, 0f, 0f)
        s.run(2_000)
        assertTrue(s.reactionsSeen.isEmpty())
    }

    @Test fun cancellingALongPressStillHandsBackTheFace() {
        val s = TouchSim()
        s.touch.onDown(s.now, 1170f, 500f)
        s.run(800)
        assertEquals(Triggers.LONG_PRESS, s.engine.reactionName)
        s.touch.onCancel(s.now)
        s.run(100)
        assertEquals(Triggers.HAPPY_RELEASE, s.engine.reactionName)
        s.run(2_200)
        assertNull(s.engine.reactionName)
    }

    @Test fun everyTouchCountsAsInteractionSoTheRobotDoesNotIdleSleep() {
        val s = TouchSim()
        repeat(12) {
            s.run(60_000L, step = 1_000)
            s.tap(TouchSim.PLAIN_X, TouchSim.PLAIN_Y)
        }
        s.run(1_000, step = 100)
        assertEquals(BaseState.Awake, s.engine.baseState)
    }

    // ---- engine and animator pieces the touch layer relies on ----

    @Test fun lookOverrideIsClearedWhenTheReactionChangesOrEnds() {
        val e = BehaviorEngine(Random(1))
        val inputs = Inputs()
        e.update(1_000, inputs)
        e.react(Reaction("a", Priority.Touch, listOf(Step(Mood.Love, 1_000))), 1_000)
        e.setLookTarget(Vec2(0.9f, 0.1f))
        assertEquals(Vec2(0.9f, 0.1f), e.update(1_100, inputs).lookTarget)
        e.react(Reaction("b", Priority.Touch, listOf(Step(Mood.Happy, 1_000))), 1_500)
        assertNull(e.update(1_600, inputs).lookTarget)
        e.setLookTarget(Vec2(0.2f, 0.2f))
        assertNotNull(e.update(1_700, inputs).lookTarget)
        assertNull(e.update(2_600, inputs).lookTarget) // reaction over
    }

    @Test fun ignoreDebounceReactionsFollowQuickTouchReactions() {
        val e = BehaviorEngine(Random(1))
        e.update(1_000, Inputs())
        assertTrue(e.react(Reaction("tap", Priority.Touch, listOf(Step(Mood.Curious, 900))), 1_000))
        assertFalse(e.react(Reaction("plain", Priority.Touch, listOf(Step(Mood.Happy, 900))), 1_050))
        assertTrue(e.react(Reaction("rage", Priority.Touch, listOf(Step(Mood.Angry, 900)), ignoreDebounce = true), 1_060))
    }

    @Test fun gestureKeepFaceFlagIsReportedOnTheFrameTheStepStarts() {
        val e = BehaviorEngine(Random(1))
        e.update(1_000, Inputs())
        e.react(Triggers.eyeTap(rightEye = true), 1_000)
        val out = e.update(1_010, Inputs())
        assertEquals(Gesture.Giggle, out.gesture)
        assertTrue(out.gestureKeepsFace)
        assertFalse(e.update(1_020, Inputs()).gestureKeepsFace)
    }

    @Test fun eyesFollowATouchPointWithinOneAndAHalfTimesTheIdleRange() {
        val a = FaceAnimator(Mood.Neutral, Random(2))
        a.lookAt(Vec2(1f, 0.5f))
        repeat(40) { a.update(16f) }
        assertEquals(Tuning.LookRangeX * 1.5f, a.face.leftEye.dx, 1f)
        assertEquals(0f, a.face.leftEye.dy, 1f)

        a.lookAt(Vec2(1f, 1f)) // corner: clamped onto the ellipse
        repeat(60) { a.update(16f) }
        val nx = a.face.leftEye.dx / (Tuning.LookRangeX * 1.5f)
        val ny = a.face.leftEye.dy / (Tuning.LookRangeY * 1.5f)
        assertTrue(nx * nx + ny * ny <= 1.01f)
        assertTrue(a.face.leftEye.dx > 20f && a.face.leftEye.dy > 10f)

        a.lookAt(null)
        repeat(30) { a.update(16f) }
        assertTrue(abs(a.face.leftEye.dx) < 3f && abs(a.face.leftEye.dy) < 3f)
    }

    @Test fun followSettlesInAboutOneTwentiethOfASecondScale() {
        val a = FaceAnimator(Mood.Neutral, Random(2))
        a.lookAt(Vec2(1f, 0.5f))
        repeat(8) { a.update(16f) } // ~128 ms
        assertTrue(a.face.leftEye.dx > Tuning.LookRangeX * 1.5f * 0.9f)
    }

    @Test fun aGestureThatKeepsTheFaceDoesNotSwapIt() {
        val a = FaceAnimator(Mood.Wink, Random(2))
        repeat(20) { a.update(16f) }
        a.play(Gesture.Giggle, keepFace = true)
        var maxFy = 0f
        repeat(60) {
            a.update(8f)
            maxFy = maxOf(maxFy, abs(a.face.fy))
            assertEquals(Mood.Wink, a.mood)
            assertEquals(EyeKind.ARC_UP, a.face.rightEye.kind)
        }
        assertTrue(maxFy > 3f)
    }
}
