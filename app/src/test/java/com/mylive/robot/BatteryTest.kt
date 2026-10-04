package com.mylive.robot

import android.os.BatteryManager
import com.mylive.robot.behavior.BaseState
import com.mylive.robot.behavior.BehaviorEngine
import com.mylive.robot.behavior.BehaviorOutput
import com.mylive.robot.behavior.Inputs
import com.mylive.robot.behavior.Triggers
import com.mylive.robot.device.BatteryInfo
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.Gesture
import com.mylive.robot.face.Mood
import com.mylive.robot.face.Moods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private class PowerSim(percent: Int = 80, charging: Boolean = false, minute: Int = 12 * 60, seed: Int = 1) {
    val engine = BehaviorEngine(Random(seed))
    val inputs = Inputs(minuteOfDay = minute, batteryPercent = percent, charging = charging)
    var now = 1_000L
    var last: BehaviorOutput = engine.update(now, inputs)
    val gestures = ArrayList<Pair<Long, Gesture>>()
    val reactions = ArrayList<String>()
    private var lastTouch = now
    private var lastName: String? = null

    fun run(ms: Long, step: Long = 50, keepAwake: Boolean = true, onTick: (BehaviorOutput) -> Unit = {}) {
        val end = now + ms
        while (now < end) {
            now += step
            if (keepAwake && now - lastTouch >= 60_000L) {
                engine.noteInteraction(now)
                lastTouch = now
            }
            last = engine.update(now, inputs)
            last.gesture?.let { gestures.add(now to it) }
            val name = engine.reactionName
            if (name != lastName) { // a new reaction, even if it has the same name as one that just ended
                if (name != null) reactions.add(name)
                lastName = name
            }
            onTick(last)
        }
    }
}

class BatteryTest {
    // ---- reading the battery ----------------------------------------------------------

    @Test fun batteryInfoParsesLevelScaleAndChargerState() {
        assertEquals(BatteryInfo(42, true), BatteryInfo.from(level = 42, scale = 100, plugged = BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals(BatteryInfo(50, false), BatteryInfo.from(1, 2, 0))
        assertEquals(BatteryInfo(100, true), BatteryInfo.from(100, 100, BatteryManager.BATTERY_PLUGGED_USB))
    }

    @Test fun anyPluggedSourceCountsAsOnTheCharger() {
        assertTrue(BatteryInfo.from(50, 100, BatteryManager.BATTERY_PLUGGED_AC)!!.charging)
        assertTrue(BatteryInfo.from(50, 100, BatteryManager.BATTERY_PLUGGED_USB)!!.charging)
        assertTrue(BatteryInfo.from(50, 100, BatteryManager.BATTERY_PLUGGED_WIRELESS)!!.charging)
        assertFalse(BatteryInfo.from(50, 100, 0)!!.charging) // unplugged, whatever the status field still says
    }

    @Test fun unusableReadingsAreRejectedAndPercentIsClamped() {
        assertNull(BatteryInfo.from(-1, 100, 0))
        assertNull(BatteryInfo.from(50, 0, 0))
        assertEquals(100, BatteryInfo.from(150, 100, 0)!!.percent)
        assertEquals(0, BatteryInfo.from(0, 100, 0)!!.percent)
    }

    // ---- charger ----------------------------------------------------------------------

    @Test fun chargerConnectedIsSurprisedThenHappyThenChargingStays() {
        val s = PowerSim()
        s.inputs.charging = true
        s.run(100)
        assertEquals(Triggers.CHARGER_CONNECTED, s.engine.reactionName)
        assertEquals(Mood.Surprised, s.last.mood)
        s.run(700)
        assertEquals(Mood.Happy, s.last.mood)
        s.run(2_100)
        assertEquals(Mood.Charging, s.last.mood)
        assertNull(s.engine.reactionName)
        assertEquals(BaseState.Charging, s.engine.baseState)
        s.run(60_000L, step = 1_000)
        assertEquals(Mood.Charging, s.last.mood)
        assertEquals(listOf(Triggers.CHARGER_CONNECTED), s.reactions)
    }

    @Test fun chargerDisconnectedIsCuriousUnlessTheBatteryIsUnder30() {
        val high = PowerSim(percent = 80, charging = true)
        high.run(500)
        high.inputs.charging = false
        high.run(100)
        assertEquals(Triggers.CHARGER_DISCONNECTED, high.engine.reactionName)
        assertEquals(Mood.Curious, high.last.mood)
        high.run(1_600)
        assertEquals(Mood.Neutral, high.last.mood)

        val low = PowerSim(percent = 29, charging = true)
        low.run(500)
        low.inputs.charging = false
        low.run(100)
        assertEquals(Mood.Sad, low.last.mood)
        low.run(2_000)
        assertTrue(low.last.mood != Mood.Sad)

        val edge = PowerSim(percent = 30, charging = true)
        edge.run(500)
        edge.inputs.charging = false
        edge.run(100)
        assertEquals(Mood.Curious, edge.last.mood)
    }

    @Test fun chargerEventsBeatDriftAndAreDroppedWhileSleeping() {
        val awake = PowerSim()
        awake.run(60_000L, step = 1_000)
        awake.inputs.charging = true
        awake.run(100)
        assertEquals(Mood.Surprised, awake.last.mood)

        val asleep = PowerSim(minute = 23 * 60 + 30)
        asleep.run(2_000, keepAwake = false)
        assertEquals(BaseState.Sleeping, asleep.engine.baseState)
        asleep.inputs.charging = true
        asleep.run(3_000, keepAwake = false)
        assertEquals(Mood.Sleeping, asleep.last.mood) // Charging does not override Sleeping
        assertTrue(asleep.reactions.isEmpty())
    }

    // ---- battery thresholds ------------------------------------------------------------

    @Test fun droppingTo15PercentIsSadThenTheLowBatteryFace() {
        val s = PowerSim(percent = 16)
        s.run(500)
        assertTrue(s.reactions.isEmpty())
        s.inputs.batteryPercent = 15
        s.run(100)
        assertEquals(Triggers.BATTERY_LOW, s.engine.reactionName)
        assertEquals(Mood.Sad, s.last.mood)
        s.run(2_000)
        assertEquals(BaseState.LowBattery, s.engine.baseState)
        assertEquals(Mood.LowBattery, s.last.mood)
        s.run(60_000L, step = 1_000)
        assertEquals(listOf(Triggers.BATTERY_LOW), s.reactions) // once, not every frame
    }

    @Test fun droppingTo5PercentIsTiredWithAYawnThenLowBatteryAndYawnsEveryMinute() {
        val s = PowerSim(percent = 6)
        // 6% is already low: the start is not an event
        s.run(500)
        assertTrue(s.reactions.isEmpty())
        s.inputs.batteryPercent = 5
        s.run(100)
        assertEquals(Triggers.BATTERY_CRITICAL, s.engine.reactionName)
        assertEquals(Mood.Tired, s.last.mood)
        assertEquals(listOf(Gesture.Yawn), s.gestures.map { it.second })
        s.run(2_500)
        assertEquals(Mood.LowBattery, s.last.mood)
        s.run(70_000L, step = 500)
        assertEquals(2, s.gestures.count { it.second == Gesture.Yawn }) // plus one a minute from then on
    }

    @Test fun fallingStraightPast15To4OnlyPlaysTheCriticalReaction() {
        val s = PowerSim(percent = 40)
        s.run(500)
        s.inputs.batteryPercent = 4
        s.run(3_000)
        assertEquals(listOf(Triggers.BATTERY_CRITICAL), s.reactions)
    }

    @Test fun flappingAroundTheThresholdDoesNotRepeatTheReaction() {
        val s = PowerSim(percent = 17)
        s.run(500)
        repeat(6) {
            s.inputs.batteryPercent = 15
            s.run(3_000)
            s.inputs.batteryPercent = 16
            s.run(3_000)
        }
        assertEquals(listOf(Triggers.BATTERY_LOW), s.reactions)
        // climbing well above the level re-arms it
        s.inputs.batteryPercent = 25
        s.run(3_000)
        s.inputs.batteryPercent = 14
        s.run(3_000)
        assertEquals(listOf(Triggers.BATTERY_LOW, Triggers.BATTERY_LOW), s.reactions)
    }

    @Test fun startingAlreadyLowDoesNotReact() {
        val s = PowerSim(percent = 10)
        s.run(5_000)
        assertTrue(s.reactions.isEmpty())
        assertEquals(Mood.LowBattery, s.last.mood)
    }

    @Test fun lowBatteryReactionsDoNotFireWhileCharging() {
        val s = PowerSim(percent = 40, charging = true)
        s.run(500)
        s.inputs.batteryPercent = 10
        s.run(5_000)
        assertTrue(s.reactions.isEmpty())
    }

    // ---- full battery -----------------------------------------------------------------

    @Test fun reaching100OnTheChargerIsHappyFor20SecondsOnceThenChargingStays() {
        val s = PowerSim(percent = 99, charging = true)
        s.run(500)
        s.inputs.batteryPercent = 100
        s.run(100)
        assertEquals(Triggers.BATTERY_FULL, s.engine.reactionName)
        assertEquals(Mood.Happy, s.last.mood)
        s.run(19_000, step = 500)
        assertEquals(Mood.Happy, s.last.mood)
        s.run(1_500, step = 500)
        assertEquals(Mood.Charging, s.last.mood)
        s.run(120_000L, step = 1_000)
        assertEquals(Mood.Charging, s.last.mood)
        assertEquals(listOf(Triggers.BATTERY_FULL), s.reactions) // once
    }

    @Test fun pluggingInAlready100DoesNotPlayTheFullSequence() {
        val s = PowerSim(percent = 100)
        s.run(500)
        s.inputs.charging = true
        s.run(10_000, step = 500)
        assertEquals(listOf(Triggers.CHARGER_CONNECTED), s.reactions)
    }

    // ---- foreground -------------------------------------------------------------------

    @Test fun returningToTheAppPlaysLookAround() {
        val s = PowerSim()
        s.run(1_000)
        s.engine.onForeground(s.now)
        s.run(100)
        assertEquals(Triggers.FOREGROUND, s.engine.reactionName)
        assertEquals(listOf(Gesture.LookAround), s.gestures.map { it.second })
        s.run(1_300)
        assertNull(s.engine.reactionName)
    }

    @Test fun returningToTheAppWakesAnIdleSleepingRobotAndLooksAround() {
        val s = PowerSim()
        s.run(11 * 60_000L, step = 1_000, keepAwake = false)
        assertEquals(BaseState.Sleeping, s.engine.baseState)
        s.engine.onForeground(s.now)
        s.run(200)
        assertEquals(BaseState.Awake, s.engine.baseState)
        assertEquals(Triggers.FOREGROUND, s.engine.reactionName)
    }

    // ---- the bolt glyph ----------------------------------------------------------------

    @Test fun onlyTheChargingFaceShowsTheBolt() {
        for (m in Mood.values()) {
            assertEquals(m.name, if (m == Mood.Charging) 1f else 0f, Moods.spec(m).bolt, 0f)
        }
    }

    @Test fun boltFadesInOnChargingAndOutAgain() {
        val a = FaceAnimator(Mood.Neutral, Random(2))
        repeat(20) { a.update(16f) }
        assertEquals(0f, a.face.bolt, 0f)
        a.setMood(Mood.Charging)
        var prev = 0f
        repeat(120) {
            a.update(16f)
            assertTrue(a.face.bolt in 0f..1f)
            assertTrue(a.face.bolt >= prev - 1e-4f)
            prev = a.face.bolt
        }
        assertEquals(1f, a.face.bolt, 0.02f)
        a.setMood(Mood.Neutral)
        repeat(120) { a.update(16f) }
        assertEquals(0f, a.face.bolt, 0.02f)
        assertNotNull(a)
    }
}
