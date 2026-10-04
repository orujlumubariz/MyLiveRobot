package com.mylive.robot

import com.mylive.robot.behavior.BaseState
import com.mylive.robot.behavior.BehaviorEngine
import com.mylive.robot.behavior.BehaviorOutput
import com.mylive.robot.behavior.Inputs
import com.mylive.robot.behavior.Triggers
import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.IdleMotion
import com.mylive.robot.face.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private class ClockSim(minute: Int, seed: Int = 1) {
    val engine = BehaviorEngine(Random(seed))
    val inputs = Inputs(minuteOfDay = minute)
    var now = 1_000L
    var last: BehaviorOutput = engine.update(now, inputs)
    val brightness = ArrayList<Float>()
    val reactions = LinkedHashSet<String>()

    fun run(ms: Long, step: Long = 50, onTick: (BehaviorOutput) -> Unit = {}) {
        val end = now + ms
        while (now < end) {
            now += step
            last = engine.update(now, inputs)
            last.brightness?.let { brightness.add(it) }
            engine.reactionName?.let { reactions.add(it) }
            onTick(last)
        }
    }

    fun at(h: Int, m: Int = 0) {
        inputs.minuteOfDay = h * 60 + m
    }
}

class SleepWakeTest {
    // ---- schedule ----------------------------------------------------------------------

    @Test fun morningWakeUpPlaysWakingThenHappyForEightSecondsThenAwake() {
        val s = ClockSim(minute = 3 * 60)
        s.run(30 * 60_000L, step = 1_000) // asleep through the night, no touch at all
        assertEquals(BaseState.Sleeping, s.engine.baseState)

        s.at(7, 0)
        s.run(200)
        assertEquals(BaseState.Awake, s.engine.baseState)
        assertEquals(Triggers.MORNING_WAKE, s.engine.reactionName)
        assertEquals(Mood.Waking, s.last.mood)
        s.run(600)
        assertEquals(Mood.Happy, s.last.mood)
        s.run(7_000)
        assertEquals(Mood.Happy, s.last.mood)
        s.run(1_000)
        assertNull(s.engine.reactionName)
        assertEquals(Mood.Neutral, s.last.mood)
        assertEquals(Tuning.BrightnessNormal, s.brightness.last(), 0f)
    }

    @Test fun noMorningSequenceWhenTheClockJumpsToNoon() {
        val s = ClockSim(minute = 23 * 60 + 30)
        s.run(3_000)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        s.at(12, 0)
        s.run(500)
        assertEquals(BaseState.Awake, s.engine.baseState)
        assertTrue(Triggers.MORNING_WAKE !in s.reactions)
    }

    @Test fun idleSleepDoesNotHoldTheRobotDownAfterTheWindowEnds() {
        val s = ClockSim(minute = 2 * 60)
        s.run(60 * 60_000L, step = 5_000) // idle for an hour: asleep by window and by idle
        s.at(7, 0)
        s.run(500)
        assertEquals(BaseState.Awake, s.engine.baseState)
        s.run(9 * 60_000L, step = 1_000) // stays up for the 10-minute idle period
        assertEquals(BaseState.Awake, s.engine.baseState)
    }

    @Test fun awakeThroughTheWindowEndGetsNoMorningSequence() {
        val s = ClockSim(minute = 23 * 60 + 30)
        s.run(3_000)
        s.engine.noteInteraction(s.now) // a touch wakes it for 3 minutes
        s.run(500)
        assertEquals(BaseState.Awake, s.engine.baseState)
        s.at(7, 0) // the schedule fires while it is awake
        s.run(1_000)
        assertTrue(Triggers.MORNING_WAKE !in s.reactions)
        assertEquals(BaseState.Awake, s.engine.baseState)
    }

    @Test fun fallsAsleepAt2300FromAwake() {
        val s = ClockSim(minute = 22 * 60 + 59)
        s.run(2_000, step = 100)
        assertEquals(BaseState.Awake, s.engine.baseState)
        s.at(23, 0)
        s.run(200)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        assertEquals(Mood.Sleeping, s.last.mood)
    }

    // ---- dimming ----------------------------------------------------------------------

    @Test fun screenDimsOnlyAfterTheSleepAnimationAndWakesAtOnce() {
        val s = ClockSim(minute = 22 * 60 + 59)
        s.run(500)
        s.at(23, 0)
        s.run(1_000)
        assertTrue("dimmed too early", s.brightness.isEmpty())
        s.run(800)
        assertEquals(listOf(Tuning.SleepBrightness), s.brightness)
        s.engine.noteInteraction(s.now)
        s.run(100)
        assertEquals(listOf(Tuning.SleepBrightness, Tuning.BrightnessNormal), s.brightness)
    }

    @Test fun waking_up_before_the_dim_delay_never_dims_at_all() {
        val s = ClockSim(minute = 23 * 60 + 30)
        s.run(800)
        s.engine.noteInteraction(s.now)
        s.run(3_000)
        assertTrue(s.brightness.isEmpty())
    }

    // ---- animation: going to sleep ----------------------------------------------------

    private fun animator(from: Mood) = FaceAnimator(from, Random(3)).also { a -> repeat(60) { a.update(8f) } }

    @Test fun sleepEntryClosesTheEyesSlowlyThenSwapsAndTheGlowFades() {
        val a = animator(Mood.Happy)
        val glow0 = a.face.glow
        assertTrue(glow0 > 0.9f)
        a.setMood(Mood.Sleeping)
        var t = 0f
        var openAt600 = -1f
        var glowAt300 = -1f
        var glowAt1500 = -1f
        var kindChangedAt = -1f
        var prevGlow = glow0
        while (t < 2_000f) {
            a.update(8f)
            t += 8f
            if (kindChangedAt < 0 && a.face.leftEye.kind == EyeKind.ARC_DOWN) kindChangedAt = t
            if (t in 600f..607f) openAt600 = a.face.leftEye.open
            if (t in 300f..307f) glowAt300 = a.face.glow
            if (t in 1500f..1507f) glowAt1500 = a.face.glow
            assertTrue("glow must only fall", a.face.glow <= prevGlow + 1e-4f)
            prevGlow = a.face.glow
        }
        assertEquals(1200f, kindChangedAt, 40f)
        assertTrue("open at 600 ms was $openAt600", openAt600 in 0.3f..0.7f)
        assertTrue("glow at 300 ms was $glowAt300", glowAt300 > 0.55f)
        assertTrue("glow at 1500 ms was $glowAt1500", glowAt1500 < 0.31f)
        assertEquals(Moods_glow(Mood.Sleeping), a.face.glow, 0.02f)
    }

    private fun Moods_glow(m: Mood) = com.mylive.robot.face.Moods.spec(m).glow

    // ---- animation: waking up ---------------------------------------------------------

    /** Time from the eye shape changing until the eyes are at their Waking height again. */
    private fun openTimeAfterSwap(from: Mood, to: Mood): Float {
        val a = animator(from)
        a.setMood(to)
        var t = 0f
        var swapAt = -1f
        val full = com.mylive.robot.face.Moods.spec(to).left.open * 0.97f
        while (t < 2_000f) {
            a.update(4f)
            t += 4f
            if (swapAt < 0 && a.face.leftEye.kind != com.mylive.robot.face.Moods.spec(from).left.kind) swapAt = t
            if (swapAt >= 0 && a.face.leftEye.open >= full) return t - swapAt
        }
        return -1f
    }

    @Test fun eyesOpenSlowlyWhenLeavingSleeping() {
        val slow = openTimeAfterSwap(Mood.Sleeping, Mood.Waking)
        val normal = openTimeAfterSwap(Mood.Neutral, Mood.Happy)
        assertTrue("slow=$slow normal=$normal", slow > 250f && normal in 0f..160f)
        assertTrue(slow > 2f * normal)
    }

    @Test fun leavingWakingPopsTheEyesOpenWithAnOvershoot() {
        val a = animator(Mood.Waking)
        a.setMood(Mood.Neutral)
        a.update(8f)
        assertTrue("starts from the sleepy half-open eyes: ${a.face.leftEye.open}", a.face.leftEye.open < 0.5f)
        var max = 0f
        var t = 0f
        while (t < 600f) {
            a.update(8f)
            t += 8f
            max = maxOf(max, a.face.leftEye.open)
            assertEquals(EyeKind.RECT, a.face.leftEye.kind) // no blink-swap, same eye shape
        }
        assertTrue("overshoot $max", max > 1.0f && max <= Tuning.MaxEyeOpen)
        assertEquals(1f, a.face.leftEye.open, 0.02f)
    }

    @Test fun wakingEyesStartLowAndLiftAsTheRobotWakes() {
        val idle = IdleMotion(Random(1))
        idle.start(Mood.Waking)
        idle.update(1f)
        assertEquals(Tuning.WakingLiftUnits, idle.eyeDy, 0.2f)
        var prev = idle.eyeDy
        var t = 1f
        while (t < 800f) {
            idle.update(5f)
            t += 5f
            assertTrue(idle.eyeDy <= prev + 1e-4f)
            prev = idle.eyeDy
        }
        assertEquals(0f, idle.eyeDy, 0.01f)
        assertFalse(idle.eyeDy < 0f)
    }

    @Test fun noHalfMorphThroughTheWholeSleepAndWakeCycle() {
        // Happy -> Sleeping -> Waking -> Happy: eye kind changes only while the eyes are shut
        val a = animator(Mood.Happy)
        var prevKind = a.face.leftEye.kind
        val plan = listOf(Mood.Sleeping to 2_000f, Mood.Waking to 1_500f, Mood.Happy to 1_500f)
        for ((mood, ms) in plan) {
            a.setMood(mood)
            var t = 0f
            while (t < ms) {
                a.update(4f)
                t += 4f
                if (a.face.leftEye.kind != prevKind) {
                    assertTrue("open=${a.face.leftEye.open} entering $mood", a.face.leftEye.open < 0.02f)
                    prevKind = a.face.leftEye.kind
                }
            }
        }
        assertEquals(Mood.Happy, a.mood)
    }
}
