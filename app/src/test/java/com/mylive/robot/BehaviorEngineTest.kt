package com.mylive.robot

import com.mylive.robot.behavior.BaseState
import com.mylive.robot.behavior.BehaviorEngine
import com.mylive.robot.behavior.BehaviorOutput
import com.mylive.robot.behavior.Inputs
import com.mylive.robot.behavior.Priority
import com.mylive.robot.behavior.Reaction
import com.mylive.robot.behavior.Step
import com.mylive.robot.behavior.Vec2
import com.mylive.robot.face.Gesture
import com.mylive.robot.face.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Fake-clock harness around the engine. */
private class Sim(seed: Int = 1, minute: Int = 12 * 60) {
    val engine = BehaviorEngine(Random(seed))
    val inputs = Inputs(minuteOfDay = minute)
    var now = 1_000L
    var last: BehaviorOutput = engine.update(now, inputs)
    val gestures = ArrayList<Pair<Long, Gesture>>()
    val brightness = ArrayList<Float>()
    private var lastTouch = now

    /** Runs the clock forward; [keepAwake] taps every minute so idle sleep never kicks in. */
    fun run(ms: Long, step: Long = 100, keepAwake: Boolean = false, onTick: (BehaviorOutput) -> Unit = {}) {
        val end = now + ms
        while (now < end) {
            now += step
            if (keepAwake && now - lastTouch >= 60_000L) {
                engine.noteInteraction(now)
                lastTouch = now
            }
            last = engine.update(now, inputs)
            last.gesture?.let { gestures.add(now to it) }
            last.brightness?.let { brightness.add(it) }
            onTick(last)
        }
    }

    fun react(r: Reaction): Boolean = engine.react(r, now)
}

private fun reaction(name: String, p: Priority, mood: Mood?, ms: Long, gesture: Gesture? = null) =
    Reaction(name, p, listOf(Step(mood, ms, gesture)))

private fun min(h: Int, m: Int = 0) = h * 60 + m

class BehaviorEngineTest {
    // ---- base state -------------------------------------------------------------------

    @Test fun awakeByDefaultShowsNeutral() {
        val s = Sim()
        s.run(1_000)
        assertEquals(BaseState.Awake, s.engine.baseState)
        assertEquals(Mood.Neutral, s.last.mood)
    }

    @Test fun chargerAndLowBatteryChooseTheBaseFace() {
        val s = Sim()
        s.inputs.charging = true
        s.run(3_000) // the plug-in reaction plays first
        assertEquals(BaseState.Charging, s.engine.baseState)
        assertEquals(Mood.Charging, s.last.mood)

        s.inputs.charging = false
        s.inputs.batteryPercent = 16
        s.run(3_000)
        assertEquals(BaseState.Awake, s.engine.baseState)

        s.inputs.batteryPercent = 15
        s.run(3_000)
        assertEquals(BaseState.LowBattery, s.engine.baseState)
        assertEquals(Mood.LowBattery, s.last.mood)

        // charging wins over low battery
        s.inputs.charging = true
        s.run(3_000)
        assertEquals(Mood.Charging, s.last.mood)
    }

    @Test fun sleepsAt2300WakesAt0700AndDimsOnlyWhileAsleep() {
        val s = Sim(minute = min(22, 59))
        s.run(1_000)
        assertEquals(BaseState.Awake, s.engine.baseState)

        s.inputs.minuteOfDay = min(23)
        s.run(2_000) // the screen dims only after the sleep animation has played
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        assertEquals(Mood.Sleeping, s.last.mood)
        assertEquals(listOf(Tuning.SleepBrightness), s.brightness)

        s.inputs.minuteOfDay = min(6, 59)
        s.run(500)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        assertEquals(listOf(Tuning.SleepBrightness), s.brightness) // emitted once, not every frame

        s.inputs.minuteOfDay = min(7)
        s.run(500)
        assertEquals(BaseState.Awake, s.engine.baseState)
        assertEquals(listOf(Tuning.SleepBrightness, Tuning.BrightnessNormal), s.brightness)
    }

    @Test fun idleSleepAfterTenMinutesAndATouchWakesIt() {
        val s = Sim()
        s.run(9 * 60_000L + 50_000L, step = 1_000)
        assertEquals(BaseState.Awake, s.engine.baseState)
        s.run(20_000, step = 1_000)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        assertEquals(Mood.Sleeping, s.last.mood)

        s.engine.noteInteraction(s.now)
        s.run(500)
        assertEquals(BaseState.Awake, s.engine.baseState)
        assertEquals(Tuning.BrightnessNormal, s.brightness.last(), 0f)
    }

    @Test fun interactionResetsTheIdleTimer() {
        val s = Sim()
        repeat(5) {
            s.run(8 * 60_000L, step = 1_000)
            s.engine.noteInteraction(s.now)
        }
        s.run(1_000)
        assertEquals(BaseState.Awake, s.engine.baseState)
    }

    @Test fun touchInTheSleepWindowWakesForThreeMinutesThenSleepsAgain() {
        val s = Sim(minute = min(23, 30))
        s.run(500)
        assertEquals(BaseState.Sleeping, s.engine.baseState)

        s.engine.noteInteraction(s.now)
        s.run(500)
        assertEquals(BaseState.Awake, s.engine.baseState)
        s.run(2 * 60_000L + 50_000L, step = 1_000)
        assertEquals(BaseState.Awake, s.engine.baseState)
        s.run(20_000, step = 1_000)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
    }

    // ---- priority ---------------------------------------------------------------------

    @Test fun glitchBeatsTouchBeatsDeviceBeatsGestureTimer() {
        val s = Sim()
        assertTrue(s.react(reaction("timer", Priority.GestureTimer, Mood.Bored, 5_000)))
        assertTrue(s.react(reaction("device", Priority.Device, Mood.Surprised, 5_000)))
        s.run(300)
        assertEquals(Mood.Surprised, s.last.mood)
        assertTrue(s.react(reaction("touch", Priority.Touch, Mood.Happy, 5_000)))
        s.run(300)
        assertEquals(Mood.Happy, s.last.mood)
        assertTrue(s.react(reaction("glitch", Priority.Glitch, Mood.Broken, 5_000)))
        s.run(300)
        assertEquals(Mood.Broken, s.last.mood)

        // lower priorities are refused while a glitch runs
        assertFalse(s.react(reaction("touch2", Priority.Touch, Mood.Love, 1_000)))
        assertFalse(s.react(reaction("device2", Priority.Device, Mood.Sad, 1_000)))
        assertFalse(s.react(reaction("timer2", Priority.GestureTimer, Mood.Bored, 1_000)))
        s.run(300)
        assertEquals(Mood.Broken, s.last.mood)
        assertEquals("glitch", s.engine.reactionName)
    }

    @Test fun equalPriorityNewerWinsOnceDebounced() {
        val s = Sim()
        assertTrue(s.react(reaction("a", Priority.Device, Mood.Surprised, 5_000)))
        s.run(50)
        assertTrue(s.react(reaction("b", Priority.Device, Mood.Sad, 5_000))) // no debounce for device
        s.run(100)
        assertEquals(Mood.Sad, s.last.mood)

        val t = Sim()
        assertTrue(t.react(reaction("a", Priority.Touch, Mood.Curious, 5_000)))
        t.run(200)
        assertTrue(t.react(reaction("b", Priority.Touch, Mood.Happy, 5_000)))
        t.run(100)
        assertEquals(Mood.Happy, t.last.mood)
    }

    @Test fun touchReactionsWithinDebounceAreIgnored() {
        val s = Sim()
        assertTrue(s.react(reaction("a", Priority.Touch, Mood.Curious, 5_000)))
        s.run(100)
        assertFalse(s.react(reaction("b", Priority.Touch, Mood.Happy, 5_000)))
        s.run(100)
        assertEquals(Mood.Curious, s.last.mood)
        assertEquals("a", s.engine.reactionName)
    }

    @Test fun droppedLowerPriorityReactionNeverResumes() {
        val s = Sim()
        assertTrue(s.react(reaction("device", Priority.Device, Mood.Surprised, 3_000)))
        s.run(500)
        assertTrue(s.react(reaction("touch", Priority.Touch, Mood.Happy, 1_000)))
        s.run(1_500)
        assertNull(s.engine.reactionName)
        assertEquals(Mood.Neutral, s.last.mood) // back to the base face, not to Surprised
        s.run(3_000)
        assertEquals(Mood.Neutral, s.last.mood)
    }

    @Test fun reactionStepsRunInOrderEmitGesturesOnceAndHandBack() {
        val s = Sim()
        val r = Reaction(
            "tap", Priority.Touch,
            listOf(
                Step(Mood.Curious, 300, Gesture.Bounce, look = Vec2(0.25f, 0.75f)),
                Step(Mood.Happy, 400),
            ),
        )
        assertTrue(s.react(r))
        val seen = ArrayList<Mood>()
        var look: Vec2? = null
        s.run(1_000, step = 20) { out ->
            if (seen.lastOrNull() != out.mood) seen.add(out.mood)
            if (out.mood == Mood.Curious) look = out.lookTarget
            if (out.mood == Mood.Happy) assertNull(out.lookTarget)
        }
        assertEquals(listOf(Mood.Curious, Mood.Happy, Mood.Neutral), seen)
        assertEquals(listOf(Gesture.Bounce), s.gestures.map { it.second })
        assertEquals(Vec2(0.25f, 0.75f), look)
    }

    @Test fun chargerReactionBeatsMoodDriftThenTheChargingFaceStays() {
        val s = Sim(seed = 4)
        // wait until drift is showing something other than Neutral
        var guard = 0
        while (s.last.mood == Mood.Neutral && guard++ < 10_000) s.run(1_000, step = 1_000, keepAwake = true)
        assertTrue(s.last.mood != Mood.Neutral)

        val plug = Reaction(
            "charger", Priority.Device,
            listOf(Step(Mood.Surprised, 700), Step(Mood.Happy, 2_000)),
        )
        assertTrue(s.react(plug))
        s.inputs.charging = true
        s.run(300)
        assertEquals(Mood.Surprised, s.last.mood)
        s.run(1_000)
        assertEquals(Mood.Happy, s.last.mood)
        s.run(2_000)
        assertEquals(Mood.Charging, s.last.mood)
    }

    @Test fun sleepingIsOnlyOverriddenByTouchOrGlitch() {
        val s = Sim(minute = min(23, 30))
        s.run(500)
        assertEquals(BaseState.Sleeping, s.engine.baseState)

        assertFalse(s.react(reaction("charger", Priority.Device, Mood.Surprised, 700)))
        assertFalse(s.react(reaction("yawn", Priority.GestureTimer, Mood.Tired, 700)))
        s.run(300)
        assertEquals(Mood.Sleeping, s.last.mood)

        assertTrue(s.react(reaction("touch", Priority.Touch, Mood.Curious, 700)))
        s.run(300)
        assertEquals(Mood.Curious, s.last.mood)
        s.run(600)
        assertEquals(Mood.Sleeping, s.last.mood)
    }

    @Test fun fallingAsleepDropsDeviceAndTimerReactionsButNotTouch() {
        val s = Sim(minute = min(22, 59))
        s.run(500)
        assertTrue(s.react(reaction("device", Priority.Device, Mood.Surprised, 60_000)))
        s.run(300)
        assertEquals(Mood.Surprised, s.last.mood)
        s.inputs.minuteOfDay = min(23)
        s.run(300)
        assertEquals(Mood.Sleeping, s.last.mood)

        val t = Sim(minute = min(22, 59))
        t.run(500)
        assertTrue(t.react(reaction("touch", Priority.Touch, Mood.Happy, 60_000)))
        t.inputs.minuteOfDay = min(23)
        t.run(300)
        assertEquals(Mood.Happy, t.last.mood)
    }

    // ---- mood drift -------------------------------------------------------------------

    private val allowed = setOf(Mood.Neutral, Mood.Happy, Mood.Bored, Mood.Sceptic, Mood.Tired, Mood.Wink)

    @Test fun driftNeverPicksReactionMoodsNotEvenAngry() {
        for (minute in listOf(min(7, 30), min(10), min(14), min(19), min(22, 30))) {
            val seen = HashSet<Mood>()
            val s = Sim(seed = minute, minute = minute)
            s.run(8 * 3_600_000L, step = 1_000, keepAwake = true) { seen.add(it.mood) }
            assertTrue("at $minute: $seen", allowed.containsAll(seen))
            assertTrue(Mood.Angry !in seen && Mood.Sad !in seen && Mood.Love !in seen)
            assertTrue(Mood.Surprised !in seen && Mood.Broken !in seen && Mood.Crazy !in seen && Mood.Denying !in seen)
        }
    }

    @Test fun driftRestsTwentyToSixtySecondsAndHoldsTenToFortySeconds() {
        val s = Sim(seed = 11)
        var mood = s.last.mood
        var runStart = s.now
        val holds = ArrayList<Long>()
        val rests = ArrayList<Long>()
        s.run(6 * 3_600_000L, step = 500, keepAwake = true) { out ->
            if (out.mood != mood) {
                val len = s.now - runStart
                if (mood == Mood.Neutral) rests.add(len) else holds.add(len)
                mood = out.mood
                runStart = s.now
            }
        }
        assertTrue(holds.size > 100)
        assertTrue("holds ${holds.min()}..${holds.max()}", holds.all { it in 9_500L..40_500L })
        // a Neutral pick joins the rest before and after it, so rests can be longer than 60 s
        assertTrue("rests min ${rests.min()}", rests.all { it >= 19_500L })
    }

    @Test fun neutralDominatesAndWinkIsRare() {
        val counts = HashMap<Mood, Long>()
        val s = Sim(seed = 5, minute = min(10))
        s.run(24 * 3_600_000L, step = 1_000, keepAwake = true) { counts.merge(it.mood, 1L, Long::plus) }
        val neutral = counts.getValue(Mood.Neutral)
        assertTrue(neutral > counts.filterKeys { it != Mood.Neutral }.values.max())
        assertTrue(counts.getValue(Mood.Happy) > counts.getValue(Mood.Wink))
    }

    @Test fun weightsShiftWithTheTimeOfDay() {
        fun w(minute: Int, mood: Mood) = BehaviorEngine.driftWeights(minute).first { it.first == mood }.second
        val base = w(min(23, 30), Mood.Happy) // default row
        assertTrue(w(min(9), Mood.Happy) > base) // mornings lean Happy
        assertTrue(w(min(14), Mood.Bored) > w(min(9), Mood.Bored)) // afternoons lean Bored
        val tired = (18..22).map { w(min(it, 30), Mood.Tired) }
        assertEquals(tired.sorted(), tired)
        assertEquals(tired.size, tired.toSet().size) // strictly rising each hour
        for (m in 0 until 24 * 60) {
            val moods = BehaviorEngine.driftWeights(m).map { it.first }.toSet()
            assertEquals(allowed, moods)
            assertTrue(BehaviorEngine.driftWeights(m).all { it.second > 0 })
        }
    }

    @Test fun chargingAndLowBatteryHoldTheirFaceNoDrift() {
        val s = Sim(seed = 2)
        s.inputs.charging = true
        s.run(5_000, keepAwake = true) // the plug-in reaction plays first
        s.run(30 * 60_000L, step = 1_000, keepAwake = true) { assertEquals(Mood.Charging, it.mood) }
        val t = Sim(seed = 2)
        t.inputs.batteryPercent = 10
        t.run(5_000, keepAwake = true) // the "battery fell to 15%" reaction plays first
        t.run(30 * 60_000L, step = 1_000, keepAwake = true) { assertEquals(Mood.LowBattery, it.mood) }
    }

    @Test fun driftPausesWhileAReactionRuns() {
        val s = Sim(seed = 3)
        s.run(60_000, step = 1_000, keepAwake = true)
        assertTrue(s.react(reaction("long", Priority.Device, Mood.Sad, 5 * 60_000L)))
        s.run(5 * 60_000L - 2_000, step = 1_000, keepAwake = true) { assertEquals(Mood.Sad, it.mood) }
    }

    // ---- gesture timers ---------------------------------------------------------------

    private fun countGestures(seed: Int, minute: Int, ms: Long, g: Gesture, battery: Int = 100): Int {
        val s = Sim(seed, minute)
        s.inputs.batteryPercent = battery
        s.run(ms, step = 1_000, keepAwake = true)
        // the drop to 5% also plays one Tired + Yawn reaction of its own
        return s.gestures.count { it.second == g }
    }

    @Test fun lookAroundHappensEveryMinuteOrTwoAndMoreOftenInTheAfternoon() {
        val morning = countGestures(1, min(10), 3_600_000L, Gesture.LookAround)
        val afternoon = countGestures(1, min(14), 3_600_000L, Gesture.LookAround)
        assertTrue("morning $morning", morning in 19..41)
        assertTrue("afternoon $afternoon", afternoon in 38..82)
        assertTrue(afternoon > morning)
    }

    @Test fun yawnsOnlyAfterNinePmAboutEveryFiveMinutes() {
        assertEquals(0, countGestures(1, min(20), 30 * 60_000L, Gesture.Yawn))
        val n = countGestures(1, min(21, 15), 60 * 60_000L, Gesture.Yawn)
        assertTrue("yawns $n", n in 10..14)
    }

    @Test fun criticalBatteryYawnsEveryMinuteOnlyAtFivePercentOrLess() {
        val critical = countGestures(1, min(12), 10 * 60_000L, Gesture.Yawn, battery = 5)
        assertTrue("critical $critical", critical in 9..11) // 1 for the drop itself + about one a minute
        assertEquals(0, countGestures(1, min(12), 10 * 60_000L, Gesture.Yawn, battery = 6))
        val charging = Sim(1)
        charging.inputs.batteryPercent = 3
        charging.inputs.charging = true
        charging.run(10 * 60_000L, step = 1_000, keepAwake = true)
        assertEquals(0, charging.gestures.count { it.second == Gesture.Yawn })
    }

    @Test fun noGestureTimersWhileSleeping() {
        val s = Sim(minute = min(23, 30))
        s.run(60 * 60_000L, step = 1_000)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        assertTrue(s.gestures.isEmpty())
    }

    @Test fun timerGesturesWaitForHigherPriorityReactionsThenRetry() {
        val s = Sim(seed = 8, minute = min(10))
        assertTrue(s.react(reaction("long touch", Priority.Touch, Mood.Happy, 5 * 60_000L)))
        s.run(5 * 60_000L - 1_000, step = 1_000, keepAwake = true)
        assertTrue(s.gestures.isEmpty())
        s.run(8_000, step = 1_000, keepAwake = true) // reaction over: the overdue LookAround retries within ~5 s
        assertEquals(1, s.gestures.count { it.second == Gesture.LookAround })
    }

    @Test fun timerGesturesDoNotChangeTheFace() {
        val s = Sim(seed = 1, minute = min(10))
        var prev = s.last.mood
        var gestures = 0
        s.run(30 * 60_000L, step = 100, keepAwake = true) { out ->
            if (out.gesture == Gesture.LookAround) {
                gestures++
                assertEquals(prev, out.mood) // the gesture plays on whatever face is showing
                assertNull(out.lookTarget)
            }
            prev = out.mood
        }
        assertTrue(gestures > 5)
    }
}
