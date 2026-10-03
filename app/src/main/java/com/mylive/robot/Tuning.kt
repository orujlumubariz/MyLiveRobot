package com.mylive.robot

import androidx.compose.ui.graphics.Color

/** All tunable numbers live here. No magic numbers elsewhere. */
object Tuning {
    val FaceColor = Color(0xFF35E0E8)
    val BackgroundColor = Color(0xFF0B1214)

    // Virtual canvas (spec 03)
    const val VirtualWidth = 1000f
    const val VirtualHeight = 600f

    // Anchors
    const val LeftEyeX = 300f
    const val RightEyeX = 700f
    const val EyeY = 230f
    const val MouthX = 500f
    const val MouthY = 440f

    // Default face numbers
    const val EyeWidth = 80f
    const val EyeHeight = 150f
    const val EyeCornerRatio = 0.4f
    const val MouthWidth = 110f
    const val MouthThick = 14f
    const val DefaultGlow = 0.8f

    // Glow = stacked, progressively wider, translucent copies of the shape
    const val GlowLayers = 10
    const val GlowSpread = 3f
    const val GlowAlpha = 0.25f
}
