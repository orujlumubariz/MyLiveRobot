package com.mylive.robot

import com.mylive.robot.face.Easing
import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.Mood
import com.mylive.robot.face.Moods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatorTest {
    @Test fun easingEndpoints() {
        val fns = listOf(
            Easing::linear, Easing::easeInQuad, Easing::easeOutQuad,
            Easing::easeInOutCubic, Easing::easeOutBack,
        )
        fns.forEach {
            assertEquals(0f, it(0f), 1e-5f)
            assertEquals(1f, it(1f), 1e-5f)
        }
    }

    @Test fun easeOutBackOvershootsAndOthersDoNot() {
        assertTrue(Easing.easeOutBack(0.7f) > 1f)
        var t = 0f
        while (t <= 1f) {
            assertTrue(Easing.easeInOutCubic(t) in 0f..1f)
            assertTrue(Easing.easeOutQuad(t) in 0f..1f)
            t += 0.01f
        }
    }

    @Test fun springNeverOvershootsAndConverges() {
        var v = 0f
        var prev = v
        repeat(300) {
            v = Easing.approach(v, 10f, Easing.springFactor(16.7f, Tuning.TauDefaultMs))
            assertTrue(v >= prev && v <= 10f)
            prev = v
        }
        assertEquals(10f, v, 1e-3f)
    }

    @Test fun springReachesAboutTwoThirdsAfterOneTau() {
        val k = Easing.springFactor(Tuning.TauDefaultMs, Tuning.TauDefaultMs)
        assertEquals(0.632f, k, 1e-3f)
    }

    @Test fun moodSwitchSwapsKindsAfterTheEyesCloseAndEasesNumbers() {
        val a = FaceAnimator(Mood.Neutral)
        a.setMood(Mood.Happy)
        assertEquals(EyeKind.RECT, a.face.leftEye.kind) // not swapped yet: blink-swap closes first
        repeat(40) { a.update(16f) }
        assertEquals(EyeKind.ARC_UP, a.face.leftEye.kind)
        repeat(200) { a.update(16f) }
        assertEquals(110f, a.face.leftEye.w, 0.01f)
        assertEquals(70f, a.face.leftEye.h, 0.01f)
        assertEquals(Moods.spec(Mood.Happy).glow, a.face.glow, 0.001f)
    }

    @Test fun sameKindMoodChangeEasesWithoutSwap() {
        val a = FaceAnimator(Mood.Neutral)
        a.setMood(Mood.Angry)
        a.update(16f)
        assertTrue(a.face.leftEye.w > 80f && a.face.leftEye.w < 110f)
        assertEquals(EyeKind.RECT, a.face.leftEye.kind)
    }

    @Test fun slowMoodsEaseSlower() {
        // Tired -> Sleeping and Happy -> Charging both keep ARC kinds, so only numbers ease.
        val slow = FaceAnimator(Mood.Tired).apply { setMood(Mood.Sleeping) }
        val fast = FaceAnimator(Mood.Happy).apply { setMood(Mood.Charging) }
        slow.update(50f)
        fast.update(50f)
        val slowFrac = (40f - slow.face.leftEye.h) / (40f - 30f)
        val fastFrac = (70f - fast.face.leftEye.h) / (70f - 50f)
        assertTrue(fastFrac > slowFrac)
    }

    @Test fun stallIsClampedToMaxDt() {
        val a = FaceAnimator(Mood.Neutral).apply { setMood(Mood.Angry) }
        val b = FaceAnimator(Mood.Neutral).apply { setMood(Mood.Angry) }
        a.update(5000f)
        b.update(Tuning.MaxDtMs)
        assertEquals(b.face.leftEye.w, a.face.leftEye.w, 1e-4f)
    }
}
