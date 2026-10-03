package com.mylive.robot.face

import com.mylive.robot.Tuning

/** Every face the robot can show. Full battery reuses [Happy]. */
enum class Mood {
    // core
    Neutral, Happy, Sad, Angry, Tired, Surprised, Love,
    // reaction
    Wink, Sceptic, Denying,
    // glitch
    Broken, Crazy,
    // added states
    Sleeping, Waking, Bored, Curious, Charging, LowBattery,
}

/** Immutable numbers for one eye. Only deviations from the defaults need to be written. */
class EyeSpec(
    val kind: EyeKind = EyeKind.RECT,
    val w: Float = Tuning.EyeWidth,
    val h: Float = Tuning.EyeHeight,
    val open: Float = 1f,
    val tilt: Float = 0f,
    val lidTop: Float = 0f,
    val dy: Float = 0f,
    val thick: Float = Tuning.EyeStroke,
)

class MouthSpec(
    val kind: MouthKind = MouthKind.DASH,
    val w: Float = Tuning.MouthWidth,
    val dy: Float = 0f,
    val tilt: Float = 0f,
    val thick: Float = Tuning.MouthThick,
)

/** One row of the mood table (spec 03). Left and right default to the same eye. */
class MoodSpec(
    val left: EyeSpec = EyeSpec(),
    val right: EyeSpec = left,
    val mouth: MouthSpec = MouthSpec(),
    val glow: Float = Tuning.DefaultGlow,
)

object Moods {
    private val table: Map<Mood, MoodSpec> = mapOf(
        Mood.Neutral to MoodSpec(),
        Mood.Happy to MoodSpec(
            left = EyeSpec(EyeKind.ARC_UP, w = 110f, h = 70f),
            mouth = MouthSpec(MouthKind.CHEVRON_V, w = 90f, dy = 10f),
            glow = 1.0f,
        ),
        Mood.Sad to MoodSpec(
            left = EyeSpec(EyeKind.DROOP, w = 110f, h = 100f, tilt = -18f, lidTop = 0.35f, dy = 15f),
            mouth = MouthSpec(MouthKind.ARC_FROWN, w = 100f, dy = 15f),
            glow = 0.55f,
        ),
        Mood.Angry to MoodSpec(
            left = EyeSpec(EyeKind.RECT, w = 110f, h = 90f, tilt = 22f, lidTop = 0.3f),
            mouth = MouthSpec(w = 130f),
            glow = 1.0f,
        ),
        Mood.Tired to MoodSpec(
            left = EyeSpec(EyeKind.ARC_DOWN, w = 110f, h = 40f),
            mouth = MouthSpec(w = 90f),
            glow = 0.5f,
        ),
        Mood.Surprised to MoodSpec(
            left = EyeSpec(EyeKind.RING, w = 100f, dy = -10f),
            mouth = MouthSpec(w = 60f),
            glow = 1.0f,
        ),
        Mood.Love to MoodSpec(
            left = EyeSpec(EyeKind.HEART, w = 120f),
            mouth = MouthSpec(MouthKind.ARC_SMILE, w = 100f, dy = 5f), // Mubariz's call, see DECISIONS.md
            glow = 1.0f,
        ),
        Mood.Wink to MoodSpec(
            left = EyeSpec(),
            right = EyeSpec(EyeKind.ARC_UP, w = 100f, h = 40f),
            mouth = MouthSpec(tilt = 4f),
            glow = 0.9f,
        ),
        Mood.Sceptic to MoodSpec(
            left = EyeSpec(EyeKind.RECT, w = 110f, h = 100f, lidTop = 0.55f, dy = 5f),
            right = EyeSpec(EyeKind.LINE, w = 130f, dy = -20f),
            mouth = MouthSpec(w = 100f, dy = 5f),
            glow = 0.7f,
        ),
        Mood.Denying to MoodSpec(
            left = EyeSpec(EyeKind.CHEV_L, w = 90f),
            right = EyeSpec(EyeKind.CHEV_R, w = 90f),
            mouth = MouthSpec(MouthKind.ZIGZAG, w = 140f), // Mubariz's call, see DECISIONS.md
            glow = 0.9f,
        ),
        Mood.Broken to MoodSpec(
            left = EyeSpec(EyeKind.X, w = 90f),
            glow = 0.6f,
        ),
        Mood.Crazy to MoodSpec(
            left = EyeSpec(EyeKind.SPIRAL, w = 110f),
            glow = 1.0f,
        ),
        Mood.Sleeping to MoodSpec(
            left = EyeSpec(EyeKind.ARC_DOWN, w = 110f, h = 30f, dy = 30f),
            mouth = MouthSpec(w = 60f, dy = 20f),
            glow = 0.25f,
        ),
        Mood.Waking to MoodSpec(
            left = EyeSpec(EyeKind.RECT, open = 0.3f, lidTop = 0.4f),
            mouth = MouthSpec(w = 80f),
            glow = 0.5f,
        ),
        Mood.Bored to MoodSpec(
            left = EyeSpec(EyeKind.RECT, w = 90f, h = 110f, lidTop = 0.45f),
            mouth = MouthSpec(w = 90f),
            glow = 0.6f,
        ),
        Mood.Curious to MoodSpec(
            left = EyeSpec(EyeKind.RECT, w = 95f, h = 170f),
            mouth = MouthSpec(w = 70f),
            glow = 1.0f,
        ),
        Mood.Charging to MoodSpec(
            left = EyeSpec(EyeKind.ARC_UP, w = 110f, h = 50f),
            mouth = MouthSpec(w = 70f),
            glow = 1.0f, // pulses 0.5..1.0 (task 9); bolt glyph arrives with task 15
        ),
        Mood.LowBattery to MoodSpec(
            left = EyeSpec(EyeKind.RECT, w = 80f, h = 150f, open = 0.55f, lidTop = 0.3f, dy = 10f),
            mouth = MouthSpec(MouthKind.BATTERY_LOW, w = 200f, dy = 10f, thick = Tuning.BatteryThick),
            glow = 0.35f,
        ),
    )

    fun spec(mood: Mood): MoodSpec = table.getValue(mood)

    /** Copies a spec into [face] in place. Offsets (fx, fy, scale) are left alone. */
    fun apply(face: FaceParams, spec: MoodSpec) {
        applyEye(face.leftEye, spec.left)
        applyEye(face.rightEye, spec.right)
        applyMouth(face.mouth, spec.mouth)
        face.glow = spec.glow
    }

    fun apply(face: FaceParams, mood: Mood) = apply(face, spec(mood))

    fun applyMouth(m: MouthParams, s: MouthSpec) {
        m.kind = s.kind
        m.w = s.w
        m.thick = s.thick
        m.dy = s.dy
        m.tilt = s.tilt
    }

    fun applyEye(e: EyeParams, s: EyeSpec) {
        e.kind = s.kind
        e.w = s.w
        e.h = s.h
        e.open = s.open
        e.tilt = s.tilt
        e.lidTop = s.lidTop
        e.dx = 0f
        e.dy = s.dy
        e.thick = s.thick
        e.spin = 0f
    }
}
