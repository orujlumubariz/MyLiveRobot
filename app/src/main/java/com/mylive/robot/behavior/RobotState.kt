package com.mylive.robot.behavior

import com.mylive.robot.face.Gesture
import com.mylive.robot.face.Mood

/** Normalised screen point, 0..1 on both axes. */
data class Vec2(val x: Float, val y: Float)

/** Long-lived state from time and battery (spec 05, layer 1). */
enum class BaseState { Awake, Sleeping, Charging, LowBattery }

/**
 * Who gets to show a face. Lower ordinal wins; equal priority: newer wins; a reaction that loses is
 * dropped and never resumes. Base state and drift are not reactions, they are the fallback faces.
 */
enum class Priority { Glitch, Touch, Device, GestureTimer }

/** One beat of a reaction: show [mood] (null = the base face), start [gesture], look at [look]. */
class Step(
    val mood: Mood?,
    val durationMs: Long,
    val gesture: Gesture? = null,
    val look: Vec2? = null,
)

/** A short sequence that overrides the base face, then hands back. */
class Reaction(val name: String, val priority: Priority, val steps: List<Step>) {
    init {
        require(steps.isNotEmpty()) { "a reaction needs at least one step" }
    }
}

/** What the engine reads each frame. Mutable so one instance can be reused with no allocation. */
class Inputs(
    var minuteOfDay: Int = 12 * 60,
    var batteryPercent: Int = 100,
    var charging: Boolean = false,
)

/** What the engine asks for (spec 05, output contract). */
data class BehaviorOutput(
    /** Face to show now. */
    val mood: Mood,
    /** One-shot to start this frame, null if none. */
    val gesture: Gesture?,
    /** Normalised screen point to look at; null = idle wander. */
    val lookTarget: Vec2?,
    /** Window brightness 0..1, [com.mylive.robot.Tuning.BrightnessNormal] = give control back, null = leave as is. */
    val brightness: Float?,
)
