package com.mylive.robot.face

import com.mylive.robot.Tuning

enum class EyeKind { RECT, DROOP, ARC_UP, ARC_DOWN, RING, X, SPIRAL, HEART, CHEV_L, CHEV_R, LINE }

enum class MouthKind { DASH, CHEVRON_V, ARC_FROWN, ARC_SMILE, ZIGZAG, BATTERY_LOW }

/** Mutable on purpose: the animator updates these in place, no allocation per frame. */
class EyeParams(
    var kind: EyeKind = EyeKind.RECT,
    var w: Float = Tuning.EyeWidth,
    var h: Float = Tuning.EyeHeight,
    var open: Float = 1f,
    var tilt: Float = 0f,
    var lidTop: Float = 0f,
    var dx: Float = 0f,
    var dy: Float = 0f,
    var thick: Float = Tuning.EyeStroke,
    /** Spiral rotation in degrees. */
    var spin: Float = 0f,
)

class MouthParams(
    var kind: MouthKind = MouthKind.DASH,
    var w: Float = Tuning.MouthWidth,
    var thick: Float = Tuning.MouthThick,
    var dy: Float = 0f,
    var tilt: Float = 0f,
    var dx: Float = 0f,
    /** 0..1, used by the mouth cross-fade. */
    var alpha: Float = 1f,
)

class FaceParams(
    val leftEye: EyeParams = EyeParams(),
    val rightEye: EyeParams = EyeParams(),
    val mouth: MouthParams = MouthParams(),
    var fx: Float = 0f,
    var fy: Float = 0f,
    var glow: Float = Tuning.DefaultGlow,
    var scale: Float = 1f,
    /** 0..1 for the whole face (Broken blackout). */
    var alpha: Float = 1f,
    /** 0..1: the charging bolt glyph in the top right corner. */
    var bolt: Float = 0f,
)
