package com.mylive.robot

import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.FaceParams
import com.mylive.robot.face.Mood
import com.mylive.robot.face.Moods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MoodsTest {
    @Test fun everyMoodHasASpec() {
        Mood.values().forEach { assertNotNull(it.name, Moods.spec(it)) }
    }

    @Test fun applyCopiesNumbersAndKeepsOffsets() {
        val face = FaceParams(fx = 12f, fy = -3f, scale = 1.1f)
        Moods.apply(face, Mood.Wink)
        assertEquals(EyeKind.RECT, face.leftEye.kind)
        assertEquals(EyeKind.ARC_UP, face.rightEye.kind)
        assertEquals(4f, face.mouth.tilt, 0f)
        assertEquals(0.9f, face.glow, 0f)
        assertEquals(12f, face.fx, 0f)
        assertEquals(1.1f, face.scale, 0f)
    }

    @Test fun applyFullyOverwritesPreviousMood() {
        val face = FaceParams()
        Moods.apply(face, Mood.Sad)
        Moods.apply(face, Mood.Neutral)
        assertEquals(EyeKind.RECT, face.leftEye.kind)
        assertEquals(0f, face.leftEye.tilt, 0f)
        assertEquals(0f, face.leftEye.lidTop, 0f)
        assertEquals(0f, face.leftEye.dy, 0f)
    }
}
