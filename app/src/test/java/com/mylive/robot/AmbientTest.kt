package com.mylive.robot

import com.mylive.robot.face.BlinkProfile
import com.mylive.robot.face.Blinker
import com.mylive.robot.face.Drift
import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.LookWander
import com.mylive.robot.face.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class AmbientTest {
    private val dt = 16f

    /** Start times (ms) of each blink over [seconds], found where the multiplier leaves 1. */
    private fun blinkStarts(profile: BlinkProfile, seconds: Int, seed: Int): List<Float> {
        val b = Blinker(Random(seed))
        val starts = ArrayList<Float>()
        var t = 0f
        var wasOpen = true
        repeat((seconds * 1000 / dt).toInt()) {
            b.update(dt, profile, true)
            t += dt
            if (wasOpen && b.mult < 1f) starts.add(t)
            wasOpen = b.mult >= 1f
        }
        return starts
    }

    @Test fun blinkMultiplierStaysInRange() {
        val b = Blinker(Random(1))
        repeat(20_000) {
            b.update(dt, BlinkProfile.Normal, true)
            assertTrue(b.mult in 0f..1f)
        }
    }

    @Test fun blinkIntervalsAreNotRhythmic() {
        val starts = blinkStarts(BlinkProfile.Normal, 120, seed = 7)
        assertTrue(starts.size > 15)
        val gaps = starts.zipWithNext { a, b -> b - a }
        // the two blinks of a double blink are well under 1.5 s apart; the rest follow the 2.5..6 s interval
        val normal = gaps.filter { it > 1500f }
        assertTrue(normal.all { it in 2500f..7000f })
        assertTrue((normal.max() - normal.min()) > 1000f)
    }

    @Test fun doubleBlinksHappenSometimes() {
        val starts = blinkStarts(BlinkProfile.Normal, 300, seed = 3)
        val quickGaps = starts.zipWithNext { a, b -> b - a }.count { it < 1000f }
        assertTrue(quickGaps > 0)
        assertTrue(quickGaps < starts.size / 2)
    }

    @Test fun tiredBlinksAreMoreFrequentAndSlower() {
        val normal = blinkStarts(BlinkProfile.Normal, 120, seed = 5).size
        val tired = blinkStarts(BlinkProfile.Tired, 120, seed = 5).size
        assertTrue(tired > normal)
        assertTrue(BlinkProfile.Tired.closeMs > BlinkProfile.Normal.closeMs)
    }

    @Test fun disallowedBlinkingKeepsEyesOpen() {
        val b = Blinker(Random(2))
        repeat(2000) {
            b.update(dt, BlinkProfile.Normal, false)
            assertEquals(1f, b.mult, 0f)
        }
    }

    @Test fun onlySomeKindsBlink() {
        assertTrue(Blinker.canBlink(EyeKind.RECT))
        assertTrue(Blinker.canBlink(EyeKind.HEART))
        assertFalse(Blinker.canBlink(EyeKind.ARC_UP))
        assertFalse(Blinker.canBlink(EyeKind.X))
        assertFalse(Blinker.canBlink(EyeKind.LINE))
    }

    @Test fun lookStaysInsideEllipseAndMoves() {
        val look = LookWander(Random(4))
        var moved = false
        repeat(30_000) {
            look.update(dt, 1f)
            val nx = look.dx / Tuning.LookRangeX
            val ny = look.dy / Tuning.LookRangeY
            assertTrue(nx * nx + ny * ny <= 1.001f)
            if (abs(look.dx) > 5f) moved = true
        }
        assertTrue(moved)
    }

    @Test fun lookRangeScaleZeroStaysCentred() {
        val look = LookWander(Random(4))
        repeat(5000) { look.update(dt, 0f) }
        assertEquals(0f, look.dx, 1e-3f)
        assertEquals(0f, look.dy, 1e-3f)
    }

    @Test fun driftStaysWithinAmplitudeAndBreathesNearOne() {
        val d = Drift()
        var maxX = 0f
        repeat(20_000) {
            d.update(dt, 1f, Tuning.BreathAmp, Tuning.BreathPeriodMs)
            maxX = maxOf(maxX, abs(d.fx))
            assertTrue(abs(d.fy) <= Tuning.DriftAmpY + 0.01f)
            assertTrue(abs(d.scale - 1f) <= Tuning.BreathAmp + 1e-4f)
        }
        assertTrue(maxX <= Tuning.DriftAmpX + 0.01f)
        assertTrue(maxX > Tuning.DriftAmpX * 0.5f)
    }

    @Test fun sleepingDriftsLessThanNeutral() {
        fun maxFx(mood: Mood): Float {
            val a = FaceAnimator(mood, Random(1))
            var m = 0f
            repeat(30_000) { a.update(dt); m = maxOf(m, abs(a.face.fx)) }
            return m
        }
        assertTrue(maxFx(Mood.Sleeping) < maxFx(Mood.Neutral) * 0.5f)
    }

    @Test fun animatorBlinksNeutralEyesButNotArcEyes() {
        fun minOpen(mood: Mood): Float {
            val a = FaceAnimator(mood, Random(9))
            var m = 1f
            repeat(20_000) { a.update(dt); m = minOf(m, a.face.leftEye.open) }
            return m
        }
        assertTrue(minOpen(Mood.Neutral) < 0.1f)
        assertEquals(1f, minOpen(Mood.Happy), 0f)
    }

    @Test fun noBlinkWhileMoodSettles() {
        val a = FaceAnimator(Mood.Neutral, Random(11))
        repeat(2000) { a.update(dt) }
        a.setMood(Mood.Angry)
        var t = 0f
        while (t < Tuning.BlinkSuppressAfterMoodMs - 2 * dt) {
            a.update(dt)
            t += dt
            assertEquals(1f, a.face.leftEye.open, 0.02f) // Angry eased toward open = 1
        }
    }
}
