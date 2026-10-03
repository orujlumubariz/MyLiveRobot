package com.mylive.robot.face

import com.mylive.robot.Tuning

enum class EyeKind { RECT }

enum class MouthKind { DASH }

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
)

class MouthParams(
    var kind: MouthKind = MouthKind.DASH,
    var w: Float = Tuning.MouthWidth,
    var thick: Float = Tuning.MouthThick,
    var dy: Float = 0f,
)

class FaceParams(
    val leftEye: EyeParams = EyeParams(),
    val rightEye: EyeParams = EyeParams(),
    val mouth: MouthParams = MouthParams(),
    var fx: Float = 0f,
    var fy: Float = 0f,
    var glow: Float = Tuning.DefaultGlow,
    var scale: Float = 1f,
)
