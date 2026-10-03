package com.mylive.robot.face

import com.mylive.robot.Tuning

/** Uniform fit of the 1000x600 virtual space into a w x h pixel area, centred. */
object FaceLayout {
    fun scale(w: Float, h: Float): Float =
        minOf(w / Tuning.VirtualWidth, h / Tuning.VirtualHeight)

    fun offsetX(w: Float, scale: Float): Float = (w - Tuning.VirtualWidth * scale) / 2f

    fun offsetY(h: Float, scale: Float): Float = (h - Tuning.VirtualHeight * scale) / 2f
}
