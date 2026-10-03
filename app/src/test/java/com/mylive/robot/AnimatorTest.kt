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

    @Test fun moodSwitchSwapsKindsInstantlyAndEasesNumbers() {
        val a = FaceAnimator(Mood.Neutral)
        a.setMood(Mood.Happy)
        assertEquals(EyeKind.ARC_UP, a.face.leftEye.kind)
        assertEquals(80f, a.face.leftEye.w, 0f) // numbers have not moved yet
        a.update(16f)
        assertTrue(a.face.leftEye.w > 80f && a.face.leftEye.w < 110f)
        repeat(200) { a.update(16f) }
        assertEquals(110f, a.face.leftEye.w, 0.01f)
        assertEquals(70f, a.face.leftEye.h, 0.01f)
        assertEquals(Moods.spec(Mood.Happy).glow, a.face.glow, 0.001f)
    }

    @Test fun slowMoodsEaseSlower() {
        val fast = FaceAnimator(Mood.Neutral).apply { setMood(Mood.Angry) }
        val slow = FaceAnimator(Mood.Neutral).apply { setMood(Mood.Tired) }
        fast.update(50f)
        slow.update(50f)
        val fastFrac = (fast.face.leftEye.w - 80f) / (110f - 80f)
        val slowFrac = (slow.face.leftEye.w - 80f) / (110f - 80f)
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
