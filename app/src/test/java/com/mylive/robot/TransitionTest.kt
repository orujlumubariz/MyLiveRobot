package com.mylive.robot

import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.MouthKind
import com.mylive.robot.face.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TransitionTest {
    private val dt = 8f

    /** An animator that is past its first blink window, so blinks cannot muddy the checks. */
    private fun animator(mood: Mood = Mood.Neutral) = FaceAnimator(mood, Random(21))

    @Test fun kindChangeNeverShowsAHalfMorph() {
        // Whenever the eye kind differs from the previous frame, the eyes must be fully closed.
        val moods = Mood.values()
        for (from in moods) for (to in moods) {
            if (from == to) continue
            val a = animator(from)
            repeat(10) { a.update(dt) }
            a.setMood(to)
            var prevKind = a.face.leftEye.kind
            repeat(120) {
                a.update(dt)
                val kind = a.face.leftEye.kind
                if (kind != prevKind) {
                    assertTrue("$from->$to open=${a.face.leftEye.open}", a.face.leftEye.open < 0.02f)
                    prevKind = kind
                }
            }
        }
    }

    @Test fun swapClosesThenOpensInAboutQuarterSecond() {
        val a = animator()
        a.update(dt)
        a.setMood(Mood.Happy)
        var minOpen = 1f
        var t = 0f
        var reopenedAt = -1f
        while (t < 600f) {
            a.update(dt)
            t += dt
            minOpen = minOf(minOpen, a.face.leftEye.open)
            if (minOpen < 0.05f && reopenedAt < 0f && a.face.leftEye.open > 0.99f) reopenedAt = t
        }
        assertTrue(minOpen < 0.05f)
        assertTrue("reopened at $reopenedAt", reopenedAt in 200f..320f)
    }

    @Test fun sameKindChangeDoesNotClose() {
        val a = animator()
        a.update(dt)
        a.setMood(Mood.Angry)
        repeat(60) {
            a.update(dt)
            assertEquals(1f, a.face.leftEye.open, 0.001f)
        }
    }

    @Test fun surprisedPopsOpenPastFullThenSettles() {
        val a = animator()
        a.setMood(Mood.Surprised)
        var maxOpen = 0f
        repeat(100) { a.update(dt); maxOpen = maxOf(maxOpen, a.face.leftEye.open) }
        assertTrue(maxOpen > 1f && maxOpen <= Tuning.MaxEyeOpen)
        repeat(100) { a.update(dt) }
        assertEquals(1f, a.face.leftEye.open, 0.001f)
    }

    @Test fun happyDoesNotPopOpen() {
        val a = animator()
        a.setMood(Mood.Happy)
        repeat(100) { a.update(dt); assertTrue(a.face.leftEye.open <= 1.0001f) }
    }

    @Test fun mouthOnlyChangeCrossFades() {
        // Neutral -> LowBattery keeps RECT eyes but changes DASH -> BATTERY_LOW.
        val a = animator()
        a.setMood(Mood.LowBattery)
        var minAlpha = 1f
        var kindSwitchAlpha = -1f
        var prevKind = a.face.mouth.kind
        repeat(60) {
            a.update(dt)
            minAlpha = minOf(minAlpha, a.face.mouth.alpha)
            if (a.face.mouth.kind != prevKind) {
                kindSwitchAlpha = a.face.mouth.alpha
                prevKind = a.face.mouth.kind
            }
        }
        assertEquals(MouthKind.BATTERY_LOW, a.face.mouth.kind)
        assertEquals(0f, minAlpha, 0.001f)
        assertTrue(kindSwitchAlpha in 0f..0.05f)
        assertEquals(1f, a.face.mouth.alpha, 0.001f)
        assertEquals(EyeKind.RECT, a.face.leftEye.kind)
    }

    @Test fun requestsWithinMinimumGapAreQueuedAndLatestWins() {
        val a = animator()
        a.setMood(Mood.Happy)
        a.update(100f.coerceAtMost(50f))
        a.setMood(Mood.Sad)
        a.setMood(Mood.Angry)
        assertEquals(Mood.Happy, a.mood) // not applied yet
        var t = 50f
        while (t < Tuning.MinMoodChangeMs - 10f) {
            a.update(dt)
            t += dt
            assertEquals(Mood.Happy, a.mood)
        }
        repeat(20) { a.update(dt) }
        assertEquals(Mood.Angry, a.mood)
        repeat(200) { a.update(dt) }
        assertEquals(EyeKind.RECT, a.face.leftEye.kind)
        assertEquals(110f, a.face.leftEye.w, 0.05f) // Angry numbers, never passed through Sad
    }

    @Test fun askingForTheCurrentMoodCancelsTheQueue() {
        val a = animator()
        a.setMood(Mood.Happy)
        a.update(dt)
        a.setMood(Mood.Sad)
        a.setMood(Mood.Happy)
        repeat(200) { a.update(dt) }
        assertEquals(Mood.Happy, a.mood)
    }

    @Test fun winkRightEyeReopensEveryFiveSecondsAndLeftEyeIsUntouched() {
        val a = animator(Mood.Wink)
        var prev = a.face.rightEye.kind
        val toRect = ArrayList<Float>()
        val toArc = ArrayList<Float>()
        var t = 0f
        while (t < 30_000f) {
            a.update(dt)
            t += dt
            assertEquals(EyeKind.RECT, a.face.leftEye.kind)
            val k = a.face.rightEye.kind
            if (k != prev) {
                assertTrue("half-morph at $t open=${a.face.rightEye.open}", a.face.rightEye.open < 0.02f)
                if (k == EyeKind.RECT) toRect.add(t) else toArc.add(t)
                prev = k
            }
        }
        assertTrue("toRect=$toRect toArc=$toArc", toRect.size in 4..5)
        assertEquals(toRect.size, toArc.size + (if (prev == EyeKind.RECT) 1 else 0))
        // open (RECT) for the 140 ms opening + 400 ms hold + 90 ms closing = ~630 ms
        toArc.forEachIndexed { i, end -> assertEquals(630f, end - toRect[i], 40f) }
        // cycles repeat about every 5 s plus the transition time
        toRect.zipWithNext { x, y -> assertEquals(5860f, y - x, 150f) }
    }

    @Test fun leavingWinkMidCycleRestoresTheRightEye() {
        val a = animator(Mood.Wink)
        var t = 0f
        while (t < 5300f) { a.update(dt); t += dt } // inside the first re-open
        a.setMood(Mood.Happy)
        repeat(300) { a.update(dt) }
        assertEquals(EyeKind.ARC_UP, a.face.rightEye.kind)
        assertEquals(EyeKind.ARC_UP, a.face.leftEye.kind)
        assertEquals(Mood.Happy, a.mood)
    }
}
