package com.mylive.robot

import com.mylive.robot.face.FaceLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceLayoutTest {
    @Test fun wideScreenIsLimitedByHeight() {
        val s = FaceLayout.scale(1280f, 720f)
        assertEquals(1.2f, s, 1e-4f)
        assertEquals(40f, FaceLayout.offsetX(1280f, s), 1e-3f)
        assertEquals(0f, FaceLayout.offsetY(720f, s), 1e-3f)
    }

    @Test fun shortScreenIsCentredAndUniform() {
        val s = FaceLayout.scale(800f, 360f)
        assertEquals(0.6f, s, 1e-4f)
        assertEquals(100f, FaceLayout.offsetX(800f, s), 1e-3f)
        assertEquals(0f, FaceLayout.offsetY(360f, s), 1e-3f)
    }
}
