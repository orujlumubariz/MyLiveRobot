package com.mylive.robot

import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.Gesture
import com.mylive.robot.face.GestureLayer
import com.mylive.robot.face.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class GestureTest {
    private val dt = 4f

    private fun play(g: Gesture, seed: Int = 1, step: (GestureLayer, Float) -> Unit) {
        val layer = GestureLayer(Random(seed))
        layer.start(g)
        var t = 0f
        var finished = false
        while (t < g.durationMs + 100f) {
            val done = layer.update(dt)
            t += dt
            if (done) {
                assertFalse(finished)
                finished = true
                assertTrue("$g finished at $t", abs(t - g.durationMs) <= dt + 0.01f)
            }
            if (layer.active) step(layer, t)
        }
        assertTrue("$g never finished", finished)
        assertFalse(layer.active)
        assertEquals(0f, layer.fx, 0f)
        assertEquals(0f, layer.fy, 0f)
        assertEquals(1f, layer.scaleMul, 0f)
        assertEquals(1f, layer.eyeOpenMul, 0f)
    }

    @Test fun everyGestureFinishesOnTimeAndLeavesNeutralOutputs() {
        Gesture.values().forEach { play(it) { _, _ -> } }
    }

    @Test fun shakeNoOscillatesAndDamps() {
        var early = 0f
        var late = 0f
        var maxAbs = 0f
        var crossings = 0
        var prev = 0f
        play(Gesture.ShakeNo) { l, t ->
            maxAbs = maxOf(maxAbs, abs(l.fx))
            if (t < 233f) early = maxOf(early, abs(l.fx))
            if (t > 467f) late = maxOf(late, abs(l.fx))
            if (prev != 0f && l.fx * prev < 0f) crossings++
            if (l.fx != 0f) prev = l.fx
        }
        assertTrue(maxAbs <= Tuning.ShakeNoUnits + 0.01f && maxAbs > 25f)
        assertTrue(late < early * 0.5f)
        assertTrue("crossings=$crossings", crossings in 4..6) // 3 cycles = 6 zero crossings, ends at 0
    }

    @Test fun nodYesMovesTwentyFiveUnitsTwice() {
        var max = 0f
        var min = 0f
        play(Gesture.NodYes) { l, _ -> max = maxOf(max, l.fy); min = minOf(min, l.fy) }
        assertEquals(25f, max, 0.5f)
        assertEquals(-25f, min, 0.5f)
    }

    @Test fun bounceRisesThirtyUnitsThenLandsWithASquash() {
        var minFy = 0f
        var minAt = 0f
        var minScale = 1f
        play(Gesture.Bounce) { l, t ->
            if (l.fy < minFy) { minFy = l.fy; minAt = t }
            minScale = minOf(minScale, l.scaleMul)
        }
        assertEquals(-30f, minFy, 0.3f)
        assertEquals(150f, minAt, dt + 1f)
        assertEquals(0.96f, minScale, 0.002f)
    }

    @Test fun joltStartsAt108AndSettles() {
        var first = 0f
        var last = 0f
        play(Gesture.Jolt) { l, t ->
            if (first == 0f) first = l.scaleMul
            last = l.scaleMul
            assertTrue(l.scaleMul <= 1.0801f && l.scaleMul >= 0.999f)
        }
        assertEquals(1.08f, first, 0.01f)
        assertTrue(last < 1.005f)
    }

    @Test fun glitchBurstShakesWithFlashesInThreeToFiveSlots() {
        for (seed in 1..40) {
            val changes = HashSet<Float>()
            var maxFx = 0f
            val glows = HashSet<Float>()
            var changesCount = 0
            var prevFx = Float.NaN
            play(Gesture.GlitchBurst, seed) { l, _ ->
                maxFx = maxOf(maxFx, abs(l.fx))
                glows.add(l.glowAbs)
                if (l.fx != prevFx) { changesCount++; prevFx = l.fx }
                changes.add(l.fx)
            }
            assertTrue(maxFx <= Tuning.GlitchShakeUnits + 0.01f)
            assertTrue(glows.all { it == 0f || it == 1f })
            assertTrue("seed $seed slots=$changesCount", changesCount in 3..5)
        }
    }

    @Test fun giggleWobblesFiveUnitsAtTwelveHertz() {
        var max = 0f
        var crossings = 0
        var prev = 0f
        play(Gesture.Giggle) { l, _ ->
            max = maxOf(max, abs(l.fy))
            if (prev != 0f && l.fy * prev < 0f) crossings++
            if (l.fy != 0f) prev = l.fy
        }
        assertEquals(5f, max, 0.2f)
        // 12 Hz over 0.8 s = 9.6 cycles = about 19 zero crossings
        assertTrue("crossings=$crossings", crossings in 17..21)
    }

    @Test fun yawnOpensTheMouthWideAndClosesTheEyes() {
        var w = 0f
        var thick = 0f
        var minOpen = 1f
        play(Gesture.Yawn) { l, _ ->
            w = maxOf(w, l.mouthWAdd)
            thick = maxOf(thick, l.mouthThickAdd)
            minOpen = minOf(minOpen, l.eyeOpenMul)
        }
        assertEquals(50f, w, 0.1f)
        assertEquals(26f, thick, 0.1f)
        assertEquals(0f, minOpen, 0.01f)
    }

    @Test fun lookAroundSweepsLeftRightThenCentre() {
        var min = 0f
        var minAt = 0f
        var max = 0f
        var maxAt = 0f
        var last = 99f
        play(Gesture.LookAround) { l, t ->
            assertTrue(l.lookActive)
            if (l.lookDx < min) { min = l.lookDx; minAt = t }
            if (l.lookDx > max) { max = l.lookDx; maxAt = t }
            last = l.lookDx
        }
        assertEquals(-Tuning.LookRangeX, min, 0.5f)
        assertEquals(350f, minAt, 2 * dt)
        assertEquals(Tuning.LookRangeX, max, 0.5f)
        assertEquals(700f, maxAt, 2 * dt)
        assertEquals(0f, last, 1f)
    }

    // ---- through the animator ----

    private fun settle(a: FaceAnimator, ms: Float, dt: Float = 8f) {
        var t = 0f
        while (t < ms) { a.update(dt); t += dt }
    }

    @Test fun shakeNoShowsDenyingInstantlyThenReturnsToThePreviousMood() {
        val a = FaceAnimator(Mood.Neutral, Random(3))
        settle(a, 100f)
        a.play(Gesture.ShakeNo)
        assertEquals(EyeKind.CHEV_L, a.face.leftEye.kind) // instant, no blink-swap
        var maxFx = 0f
        var t = 0f
        while (t < Gesture.ShakeNo.durationMs - 10f) {
            a.update(8f); t += 8f
            maxFx = maxOf(maxFx, abs(a.face.fx))
            assertEquals(EyeKind.CHEV_L, a.face.leftEye.kind)
        }
        assertTrue(maxFx > 15f)
        settle(a, 600f)
        assertEquals(Mood.Neutral, a.mood)
        assertEquals(EyeKind.RECT, a.face.leftEye.kind)
        assertFalse(a.gestureActive)
    }

    @Test fun gestureWithoutAFaceKeepsTheMood() {
        val a = FaceAnimator(Mood.Sad, Random(3))
        settle(a, 100f)
        a.play(Gesture.NodYes)
        settle(a, 700f)
        assertEquals(Mood.Sad, a.mood)
        assertEquals(EyeKind.DROOP, a.face.leftEye.kind)
    }

    @Test fun joltReturnsToSadNotNeutral() {
        val a = FaceAnimator(Mood.Sad, Random(3))
        settle(a, 100f)
        a.play(Gesture.Jolt)
        assertEquals(Mood.Surprised, a.mood)
        settle(a, 1200f)
        assertEquals(Mood.Sad, a.mood)
        assertEquals(EyeKind.DROOP, a.face.leftEye.kind)
    }

    @Test fun givingTheSameFaceAsTheCurrentMoodNeverSwaps() {
        val a = FaceAnimator(Mood.Happy, Random(3))
        settle(a, 100f)
        a.play(Gesture.Giggle)
        var t = 0f
        while (t < 1200f) {
            a.update(8f); t += 8f
            assertEquals(Mood.Happy, a.mood)
            assertEquals(EyeKind.ARC_UP, a.face.leftEye.kind)
        }
    }

    @Test fun moodRequestedDuringAGestureWinsWhenItEnds() {
        val a = FaceAnimator(Mood.Neutral, Random(3))
        settle(a, 100f)
        a.play(Gesture.ShakeNo)
        settle(a, 200f)
        a.setMood(Mood.Angry)
        assertEquals(Mood.Denying, a.mood) // still showing the gesture face
        settle(a, 1200f)
        assertEquals(Mood.Angry, a.mood)
    }

    @Test fun noBlinkingWhileAGestureRuns() {
        val a = FaceAnimator(Mood.Neutral, Random(8))
        settle(a, 100f)
        a.play(Gesture.NodYes)
        var t = 0f
        while (t < Gesture.NodYes.durationMs - 10f) {
            a.update(8f); t += 8f
            assertEquals(1f, a.face.leftEye.open, 0.001f)
        }
    }

    @Test fun yawnClosesTheEyesAndGrowsTheMouthOnTheDrawnFace() {
        val a = FaceAnimator(Mood.Neutral, Random(3))
        settle(a, 100f)
        a.play(Gesture.Yawn)
        var minOpen = 1f
        var maxW = 0f
        var maxThick = 0f
        var t = 0f
        while (t < Gesture.Yawn.durationMs - 10f) {
            a.update(8f); t += 8f
            minOpen = minOf(minOpen, a.face.leftEye.open)
            maxW = maxOf(maxW, a.face.mouth.w)
            maxThick = maxOf(maxThick, a.face.mouth.thick)
        }
        assertTrue(minOpen < 0.05f)
        assertTrue("mouth w $maxW", maxW > 130f)
        assertTrue("mouth thick $maxThick", maxThick > 35f)
    }
}
