package com.mylive.robot

import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.IdleMotion
import com.mylive.robot.face.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class IdleMotionTest {
    private val dt = 4f

    private fun run(mood: Mood, ms: Float, seed: Int = 1, step: (IdleMotion, Float) -> Unit) {
        val m = IdleMotion(Random(seed))
        m.start(mood)
        var t = 0f
        while (t < ms) {
            m.update(dt)
            t += dt
            step(m, t)
        }
    }

    @Test fun neutralAndDenyingHaveNoIdleMotion() {
        for (mood in listOf(Mood.Neutral, Mood.Denying, Mood.Wink)) {
            run(mood, 5000f) { m, _ ->
                assertEquals(0f, m.fy, 0f)
                assertEquals(0f, m.eyeDy, 0f)
                assertEquals(0f, m.tilt, 0f)
                assertEquals(1f, m.eyeWMul, 0f)
                assertEquals(1f, m.faceAlpha, 0f)
                assertEquals(-1f, m.glowAbs, 0f)
            }
        }
    }

    @Test fun happyBouncesSixUnitsUpAndBack() {
        var min = 0f
        var max = -100f
        run(Mood.Happy, 5400f) { m, _ -> min = minOf(min, m.fy); max = maxOf(max, m.fy) }
        assertEquals(-6f, min, 0.05f)
        assertTrue(max <= 0.0001f)
    }

    @Test fun sadSinksTenUnitsOverThreeSecondsAndGlancesRarely() {
        var atThreeSeconds = 0f
        var maxDy = 0f
        run(Mood.Sad, 40_000f) { m, t ->
            if (abs(t - 3000f) < dt / 2) atThreeSeconds = m.eyeDy
            maxDy = maxOf(maxDy, m.eyeDy)
        }
        assertEquals(10f, atThreeSeconds, 0.5f)
        assertTrue("glance reached $maxDy", maxDy > Tuning.SadSinkUnits + 5f)
        assertTrue(maxDy <= Tuning.SadSinkUnits + Tuning.SadGlanceUnits + 0.01f)
    }

    @Test fun angryTremblesWithinOneAndAHalfDegreesAndPulsesGlow() {
        var maxTilt = 0f
        var maxGlow = 0f
        var minGlow = 9f
        run(Mood.Angry, 3000f) { m, _ ->
            maxTilt = maxOf(maxTilt, abs(m.tilt))
            maxGlow = maxOf(maxGlow, m.glowMul)
            minGlow = minOf(minGlow, m.glowMul)
        }
        assertEquals(1.5f, maxTilt, 0.1f)
        assertEquals(1.1f, maxGlow, 0.01f)
        assertEquals(1.0f, minGlow, 0.01f)
    }

    @Test fun tiredNodsDownThenSnapsBackUp() {
        var max = 0f
        var min = 0f
        var snapFrames = 0
        var prev = 0f
        run(Mood.Tired, 60_000f) { m, _ ->
            max = maxOf(max, m.fy)
            min = minOf(min, m.fy)
            if (m.fy < prev - 0.5f) snapFrames++ // frames spent moving up fast
            prev = m.fy
        }
        assertEquals(15f, max, 0.1f)
        assertTrue(min >= -0.0001f)
        // the up-snap lasts 120 ms = about 30 frames per nod, far shorter than the 1 s drop
        assertTrue(snapFrames in 20..400)
    }

    @Test fun surprisedPulsesOnceAboutFourPercent() {
        var max = 1f
        var lateMax = 1f
        run(Mood.Surprised, 8000f) { m, t ->
            max = maxOf(max, m.eyeWMul)
            if (t > 1000f) lateMax = maxOf(lateMax, m.eyeWMul)
        }
        assertEquals(1.04f, max, 0.002f)
        assertEquals(1f, lateMax, 0f)
    }

    @Test fun loveBeatsTwelvePercentEvery800Ms() {
        var max = 1f
        var maxAt120 = 0f
        run(Mood.Love, 4000f) { m, t ->
            max = maxOf(max, m.eyeWMul)
            if (abs(t - 120f) < dt / 2) maxAt120 = m.eyeWMul
            assertTrue(m.eyeWMul in 1f..1.1201f)
        }
        assertEquals(1.12f, max, 0.002f)
        assertEquals(1.12f, maxAt120, 0.01f)
    }

    @Test fun scepticRaisedEyeDriftsBetweenZeroAndEightExtraUnits() {
        var min = 0f
        run(Mood.Sceptic, 14_000f) { m, _ ->
            min = minOf(min, m.rightEyeDy)
            assertTrue(m.rightEyeDy <= 0.0001f)
        }
        assertEquals(-8f, min, 0.05f)
    }

    @Test fun brokenFlickersJittersAndBlacksOut() {
        var minGlow = 9f
        var maxGlow = 0f
        var maxJitter = 0f
        var blackoutMs = 0f
        var blackouts = 0
        var prevBlack = false
        run(Mood.Broken, 120_000f) { m, _ ->
            minGlow = minOf(minGlow, m.glowAbs)
            maxGlow = maxOf(maxGlow, m.glowAbs)
            maxJitter = maxOf(maxJitter, abs(m.mouthDx), abs(m.mouthDy))
            val black = m.faceAlpha == 0f
            if (black) blackoutMs += dt
            if (black && !prevBlack) blackouts++
            prevBlack = black
        }
        assertTrue(minGlow >= Tuning.BrokenGlowMin - 0.001f && minGlow < 0.4f)
        assertTrue(maxGlow <= Tuning.BrokenGlowMax + 0.001f && maxGlow > 0.8f)
        assertTrue(maxJitter in 3f..4.001f)
        assertTrue(blackouts in 10..40)
        assertEquals(80f, blackoutMs / blackouts, 8f)
    }

    @Test fun crazySpinsOppositeWaysOnceEvery900Ms() {
        run(Mood.Crazy, 3000f) { m, _ ->
            assertEquals(-m.spinLeft, m.spinRight, 1e-3f)
            assertTrue(m.spinLeft in 0f..360f)
        }
        val m = IdleMotion(Random(1))
        m.start(Mood.Crazy)
        repeat((450f / dt).toInt()) { m.update(dt) }
        assertEquals(180f, m.spinLeft, 3f)
    }

    @Test fun chargingGlowBreathesBetweenHalfAndFull() {
        var min = 9f
        var max = 0f
        run(Mood.Charging, 7200f) { m, _ -> min = minOf(min, m.glowAbs); max = maxOf(max, m.glowAbs) }
        assertEquals(0.5f, min, 0.01f)
        assertEquals(1.0f, max, 0.01f)
    }

    @Test fun lowBatteryGlowStaysInItsDimBand() {
        var min = 9f
        var max = 0f
        run(Mood.LowBattery, 4800f) { m, _ -> min = minOf(min, m.glowAbs); max = maxOf(max, m.glowAbs) }
        assertEquals(0.25f, min, 0.01f)
        assertEquals(0.4f, max, 0.01f)
    }

    @Test fun animatorAppliesIdleOutputsToTheDrawnFace() {
        val a = FaceAnimator(Mood.Crazy, Random(5))
        repeat(60) { a.update(16f) }
        assertEquals(-a.face.leftEye.spin, a.face.rightEye.spin, 1e-3f)
        assertTrue(a.face.leftEye.spin != 0f)

        val b = FaceAnimator(Mood.Charging, Random(5))
        var min = 9f
        var max = 0f
        repeat(400) { b.update(16f); min = minOf(min, b.face.glow); max = maxOf(max, b.face.glow) }
        assertTrue(min < 0.6f && max > 0.95f)
    }

    @Test fun glowOverrideBlendsInInsteadOfPopping() {
        val a = FaceAnimator(Mood.Neutral, Random(5)) // glow 0.8
        a.setMood(Mood.Charging)
        a.update(16f)
        assertTrue(abs(a.face.glow - 0.8f) < 0.15f)
    }
}
