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
    const val BatteryThick = 10f               // BATTERY_LOW outline stroke
    const val BatteryHeightRatio = 0.5f        // body height, fraction of mouth w
    const val BatteryNubWidthRatio = 0.09f     // terminal nub width, fraction of mouth w
    const val BatteryNubHeightRatio = 0.4f     // terminal nub height, fraction of body height
    const val BatteryBarFraction = 0.2f        // charge left, fraction of inner width
    const val SpiralTurns = 2.5f
    const val SpiralSegments = 64

    // Motion (spec 04)
    const val MaxDtMs = 50f          // clamp frame delta so a stall does not cause a jump
    const val TauDefaultMs = 90f     // spring time constant
    const val TauSlowMs = 160f       // slow moods: Sad, Tired, Sleeping
    const val BackOvershoot = 1.4f   // easeOutBack

    // Mood transitions (spec 04)
    const val SwapCloseMs = 90f        // blink-swap: eyes close
    const val SwapOpenMs = 140f        // blink-swap: eyes open on the new face
    const val MouthFadeMs = 120f       // cross-fade when only the mouth kind changes
    const val MinMoodChangeMs = 400f   // latest request is queued until this has passed
    const val MaxEyeOpen = 1.25f       // easeOutBack may overshoot open past 1

    // Blink (spec 04)
    const val BlinkCloseMs = 70f
    const val BlinkHoldMs = 40f
    const val BlinkOpenMs = 110f
    const val BlinkMinIntervalMs = 2500f
    const val BlinkMaxIntervalMs = 6000f
    const val BlinkDoubleChance = 0.2f
    const val BlinkDoubleGapMs = 180f
    const val TiredBlinkCloseMs = 160f
    const val TiredBlinkHoldMs = 120f
    const val TiredBlinkOpenMs = 220f
    const val TiredBlinkMinIntervalMs = 1500f
    const val TiredBlinkMaxIntervalMs = 3500f
    const val BlinkSuppressAfterMoodMs = 500f  // no blinking while a mood change settles

    // Look wander (spec 04)
    const val LookMinIntervalMs = 1500f
    const val LookMaxIntervalMs = 4000f
    const val LookMoveMs = 180f
    const val LookRangeX = 45f
    const val LookRangeY = 25f
    const val LookCentreChance = 0.35f

    // Drift and breathing (spec 04)
    const val DriftAmpX = 12f
    const val DriftAmpY = 8f
    const val DriftSleepingMul = 0.3f
    const val DriftCrazyMul = 1.5f
    const val BreathPeriodMs = 4000f
    const val BreathAmp = 0.015f
    const val BreathSleepPeriodMs = 6000f
    const val BreathSleepAmp = 0.025f
    const val TauAmbientMs = 400f    // eases drift/breathing changes between moods

    // Per-mood idle micro-animations (spec 04)
    const val IdleGlowBlendMs = 300f          // glow overrides fade in so a mood change never pops
    const val HappyBounceUnits = 6f
    const val HappyBouncePeriodMs = 1800f
    const val SadSinkUnits = 10f
    const val SadSinkMs = 3000f
    const val SadGlanceUnits = 12f
    const val SadGlanceMs = 700f
    const val SadGlanceMinWaitMs = 8000f
    const val SadGlanceMaxWaitMs = 16000f
    const val AngryTremorDeg = 1.5f
    const val AngryTremorHz = 9f
    const val AngryGlowPulse = 0.1f
    const val AngryGlowPeriodMs = 1200f
    const val TiredNodUnits = 15f
    const val TiredNodDownMs = 1000f
    const val TiredNodUpMs = 120f
    const val TiredNodMinWaitMs = 6000f
    const val TiredNodMaxWaitMs = 10000f
    const val SurprisedPulse = 0.04f
    const val SurprisedPulseDelayMs = 250f
    const val SurprisedPulseMs = 500f
    const val LoveBeat = 0.12f
    const val LoveBeatPeriodMs = 800f
    const val LoveBeatUpMs = 120f
    const val SceptDriftUnits = 8f            // extra raise on top of the table's -20
    const val SceptDriftPeriodMs = 7000f
    const val BrokenGlowMin = 0.3f
    const val BrokenGlowMax = 0.9f
    const val BrokenFlickerMinMs = 60f
    const val BrokenFlickerMaxMs = 250f
    const val BrokenJitterUnits = 4f
    const val BrokenBlackoutMs = 80f
    const val BrokenBlackoutMinWaitMs = 3000f
    const val BrokenBlackoutMaxWaitMs = 8000f
    const val CrazySpinMs = 900f              // one full turn
    const val ChargingGlowMin = 0.5f
    const val ChargingGlowMax = 1.0f
    const val ChargingGlowPeriodMs = 2400f
    const val WinkReopenEnabled = false      // Mubariz did not like it; flip to true to bring it back
    const val WinkReopenEveryMs = 5000f      // Wink: right eye re-opens this often...
    const val WinkReopenHoldMs = 400f        // ...and stays open this long
    const val LowBatteryGlowMin = 0.25f
    const val LowBatteryGlowMax = 0.4f
    const val LowBatteryGlowPeriodMs = 1600f

    // Gestures (spec 04)
    const val ShakeNoMs = 700f
    const val ShakeNoUnits = 40f
    const val ShakeNoCycles = 3f
    const val NodYesMs = 600f
    const val NodYesUnits = 25f
    const val NodYesCycles = 2f
    const val BounceUpMs = 150f
    const val BounceDownMs = 200f
    const val BounceSquashMs = 100f
    const val BounceMs = 450f
    const val BounceUnits = 30f
    const val BounceSquash = 0.96f
    const val JoltMs = 500f
    const val JoltScale = 1.08f
    const val JoltTauMs = 130f
    const val GlitchMs = 350f
    const val GlitchMinFrames = 3
    const val GlitchMaxFrames = 5
    const val GlitchShakeUnits = 30f
    const val GiggleMs = 800f
    const val GiggleUnits = 5f
    const val GiggleHz = 12f
    const val YawnMs = 1800f
    const val YawnMouthGrow = 50f      // 90 -> 140
    const val YawnThickGrow = 26f      // 14 -> 40
    const val LookAroundSegmentMs = 350f
    const val LookAroundMs = 1100f

    // Behaviour engine (spec 05)
    const val ReactionDebounceMs = 150L        // same-priority touch reactions closer than this are ignored
    const val IdleSleepMs = 10L * 60_000L
    const val SleepStartMinute = 23 * 60       // 23:00
    const val WakeMinute = 7 * 60              // 07:00
    const val WakeForMs = 3L * 60_000L         // touch in the sleep window wakes the robot this long
    const val DimWhileSleeping = true          // one flag, see spec 05
    const val SleepBrightness = 0.05f
    const val BrightnessNormal = -1f           // WindowManager BRIGHTNESS_OVERRIDE_NONE
    const val LowBatteryPercent = 15
    const val CriticalBatteryPercent = 5
    const val CriticalYawnEveryMs = 60_000L
    const val MorningStartMinute = 7 * 60 + 30
    const val AfternoonStartMinute = 12 * 60
    const val EveningStartMinute = 18 * 60
    const val YawnStartMinute = 21 * 60
    const val YawnEveryMs = 300_000L
    const val YawnJitterMs = 30_000L
    const val DriftRestMinMs = 20_000L         // time on the plain face between idle moods
    const val DriftRestMaxMs = 60_000L
    const val DriftHoldMinMs = 10_000L         // how long a picked idle mood stays
    const val DriftHoldMaxMs = 40_000L
    const val LookAroundMinMs = 90_000L
    const val LookAroundMaxMs = 180_000L
    const val LookAroundAfternoonMinMs = 45_000L
    const val LookAroundAfternoonMaxMs = 90_000L
    const val GestureRetryMs = 5_000L          // retry delay when a timer gesture was outranked

    // Touch (spec 05)
    const val LongPressMs = 600L
    const val PetMs = 1000L                    // moving finger for this long = petting
    const val DoubleTapMs = 250L               // second tap within this = double tap; a lone tap waits this long
    const val RapidTapCount = 5
    const val RapidTapWindowMs = 2000L
    const val VeryRapidTapCount = 10
    const val VeryRapidTapWindowMs = 3000L
    const val SwipeSpeedPxPerS = 2000f
    const val EyeTapRadius = 120f              // virtual units around an eye centre
    const val TapLookMs = 450L                 // tap reaction: look at the tap, then bounce
    const val TapBounceMs = 450L
    const val EyeTapMs = 900L
    const val DoubleTapReactionMs = 1200L
    const val HappyReleaseMs = 2000L
    const val LoveReleaseMs = 3000L
    const val RapidAngryMs = 3000L
    const val RapidDenyMs = 1000L
    const val GlitchBrokenMs = 3000L
    const val GlitchCrazyMs = 2000L
    const val SwipeReactionMs = 700L
    const val WakeWakingMs = 600L
    const val WakeFollowUpMs = 900L
    const val HoldForever = 30_000L            // while held: the release ends or replaces it; capped in case an up event is lost
    const val LookFollowScale = 1.5f           // touch follow may reach this far past the idle ellipse
    const val LookFollowTauMs = 40f            // about 120 ms to settle

    // Debug panel
    const val DebugHoldMs = 1000L           // two-finger hold to open
    const val DebugPanelWidthDp = 340

    // Glow = stacked, progressively wider, translucent copies of the shape
    const val GlowLayers = 10
    const val GlowSpread = 3f
    const val GlowAlpha = 0.25f
}
