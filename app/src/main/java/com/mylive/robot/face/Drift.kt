package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.math.sin

/**
 * Face drift and breathing (spec 04, layer 4): slow two-sine wander of (fx, fy) plus a breathing
 * pulse on scale. Amplitudes and the breathing period ease toward their targets so a mood change
 * never makes the face jump.
 */
class Drift {
    var fx = 0f
        private set
    var fy = 0f
        private set
    var scale = 1f
        private set

    private var timeMs = 0.0
    private var driftMul = 1f
    private var breathAmp = Tuning.BreathAmp
    private var breathPeriod = Tuning.BreathPeriodMs
    private var breathPhase = 0.0

    fun update(dtMs: Float, targetDriftMul: Float, targetBreathAmp: Float, targetBreathPeriodMs: Float) {
        val k = Easing.springFactor(dtMs, Tuning.TauAmbientMs)
        driftMul = Easing.approach(driftMul, targetDriftMul, k)
        breathAmp = Easing.approach(breathAmp, targetBreathAmp, k)
        breathPeriod = Easing.approach(breathPeriod, targetBreathPeriodMs, k)

        timeMs += dtMs
        fx = (Tuning.DriftAmpX * driftMul * 0.5 * (wave(7000.0, 0.0) + wave(11300.0, 1.7))).toFloat()
        fy = (Tuning.DriftAmpY * driftMul * 0.5 * (wave(8900.0, 0.6) + wave(12700.0, 2.9))).toFloat()

        breathPhase = (breathPhase + TWO_PI * dtMs / breathPeriod) % TWO_PI
        scale = 1f + breathAmp * sin(breathPhase).toFloat()
    }

    private fun wave(periodMs: Double, phase: Double) = sin(TWO_PI * (timeMs % periodMs) / periodMs + phase)

    private companion object {
        const val TWO_PI = 2.0 * Math.PI
    }
}
