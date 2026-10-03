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
    const val EyeStroke = 14f

    // Shape proportions
    const val DroopOuterRatio = 0.9f           // outer-bottom corner radius of DROOP, fraction of w
    const val ChevronHalfWidthRatio = 1f / 3f  // CHEV_L/R half width, fraction of w
    const val MouthChevronDepthRatio = 0.35f   // CHEVRON_V depth, fraction of mouth w
    const val MouthFrownHeightRatio = 0.3f     // ARC_FROWN height, fraction of mouth w
    const val MouthSmileHeightRatio = 0.3f     // ARC_SMILE height, fraction of mouth w
    const val MouthZigzagSegments = 4
    const val MouthZigzagAmpRatio = 0.09f      // ZIGZAG half-amplitude, fraction of mouth w
    const val SpiralTurns = 2.5f
    const val SpiralSegments = 64

    // Glow = stacked, progressively wider, translucent copies of the shape
    const val GlowLayers = 10
    const val GlowSpread = 3f
    const val GlowAlpha = 0.25f
}
