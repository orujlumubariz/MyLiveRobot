package com.mylive.robot.behavior

import com.mylive.robot.Tuning
import com.mylive.robot.face.Gesture
import com.mylive.robot.face.Mood
import kotlin.random.Random

/**
 * Chooses the face (spec 05). Pure Kotlin: no Android types, driven by [update] with a monotonic
 * clock in ms, so tests can run it on a fake clock.
 *
 * Layers: base state (Awake, Sleeping, Charging, LowBattery), reactions (touch, device, glitch,
 * gesture timers) with priorities, and mood drift while Awake. The specific touch, time-of-day
 * and battery reactions are built on top of [react] in later tasks.
 */
class BehaviorEngine(private val rng: Random = Random.Default) {
    var baseState = BaseState.Awake
        private set

    /** Name of the running reaction, for the debug panel. */
    val reactionName: String? get() = current?.name

    private var started = false
    private var minuteOfDay = 12 * 60

    // reaction layer
    private var current: Reaction? = null
    private var stepIndex = 0
    private var stepStartMs = 0L
    private var lastReactionStartMs = Long.MIN_VALUE / 2
    private var pendingGesture: Gesture? = null

    // sleep / wake
    private var lastInteractionMs = 0L
    private var wakeUntilMs = 0L

    // drift
    private var driftMood = Mood.Neutral
    private var driftHolding = false
    private var driftNextMs = 0L

    // gesture timers
    private var lookAroundDueMs = 0L
    private var yawnDueMs = 0L
    private var criticalYawnDueMs = 0L

    private var lastBrightness = Tuning.BrightnessNormal

    fun update(nowMs: Long, inputs: Inputs): BehaviorOutput {
        if (!started) start(nowMs)
        minuteOfDay = inputs.minuteOfDay

        val next = when {
            isAsleep(nowMs) -> BaseState.Sleeping
            inputs.charging -> BaseState.Charging
            inputs.batteryPercent <= Tuning.LowBatteryPercent -> BaseState.LowBattery
            else -> BaseState.Awake
        }
        if (next != baseState) onBaseChanged(next, nowMs)

        advanceReaction(nowMs)
        if (baseState == BaseState.Awake && current == null) updateDrift(nowMs)
        if (baseState != BaseState.Sleeping) updateGestureTimers(nowMs, inputs)

        val step = current?.steps?.get(stepIndex)
        val gesture = pendingGesture
        pendingGesture = null
        return BehaviorOutput(
            mood = step?.mood ?: baseFace(),
            gesture = gesture,
            lookTarget = step?.look,
            brightness = brightnessChange(),
        )
    }

    /**
     * Starts [reaction] if it outranks (or ties with, newer wins) the running one. Returns whether it
     * started. While Sleeping only Touch and Glitch reactions get through. Touch reactions that follow
     * another touch reaction within [Tuning.ReactionDebounceMs] are ignored.
     */
    fun react(reaction: Reaction, nowMs: Long): Boolean {
        if (baseState == BaseState.Sleeping && reaction.priority.ordinal > Priority.Touch.ordinal) return false
        val cur = current
        if (cur != null) {
            if (reaction.priority.ordinal > cur.priority.ordinal) return false
            if (reaction.priority == Priority.Touch && cur.priority == Priority.Touch &&
                nowMs - lastReactionStartMs < Tuning.ReactionDebounceMs
            ) {
                return false
            }
        }
        current = reaction
        stepIndex = 0
        stepStartMs = nowMs
        lastReactionStartMs = nowMs
        pendingGesture = reaction.steps[0].gesture
        return true
    }

    /**
     * Any touch: resets the idle-sleep timer and wakes the robot. A touch inside the sleep window keeps it
     * awake for [Tuning.WakeForMs], then it falls asleep again. (Waking sequences come in task 14.)
     */
    fun noteInteraction(nowMs: Long) {
        if (!started) start(nowMs)
        val asleepNow = isAsleep(nowMs)
        lastInteractionMs = nowMs
        if (asleepNow && inSleepWindow(minuteOfDay)) wakeUntilMs = nowMs + Tuning.WakeForMs
    }

    private fun start(nowMs: Long) {
        started = true
        lastInteractionMs = nowMs
        resetDrift(nowMs)
        lookAroundDueMs = nowMs + lookAroundInterval()
        yawnDueMs = nowMs + yawnInterval()
        criticalYawnDueMs = nowMs + Tuning.CriticalYawnEveryMs
    }

    // ---- base state -------------------------------------------------------------------

    private fun isAsleep(nowMs: Long): Boolean {
        if (nowMs < wakeUntilMs) return false
        if (inSleepWindow(minuteOfDay)) return true
        return nowMs - lastInteractionMs >= Tuning.IdleSleepMs
    }

    private fun onBaseChanged(next: BaseState, nowMs: Long) {
        val previous = baseState
        baseState = next
        if (next == BaseState.Sleeping) {
            // only a touch reaction may keep running through sleep
            val cur = current
            if (cur != null && cur.priority.ordinal > Priority.Touch.ordinal) current = null
        }
        if (next == BaseState.Awake || previous == BaseState.Sleeping) resetDrift(nowMs)
    }

    private fun baseFace(): Mood = when (baseState) {
        BaseState.Sleeping -> Mood.Sleeping
        BaseState.Charging -> Mood.Charging
        BaseState.LowBattery -> Mood.LowBattery
        BaseState.Awake -> driftMood
    }

    private fun brightnessChange(): Float? {
        val wanted = if (baseState == BaseState.Sleeping && Tuning.DimWhileSleeping) {
            Tuning.SleepBrightness
        } else {
            Tuning.BrightnessNormal
        }
        if (wanted == lastBrightness) return null
        lastBrightness = wanted
        return wanted
    }

    // ---- reactions --------------------------------------------------------------------

    private fun advanceReaction(nowMs: Long) {
        while (true) {
            val cur = current ?: return
            val step = cur.steps[stepIndex]
            if (nowMs - stepStartMs < step.durationMs) return
            stepStartMs += step.durationMs
            stepIndex++
            if (stepIndex >= cur.steps.size) {
                current = null
                return
            }
            pendingGesture = cur.steps[stepIndex].gesture
        }
    }

    // ---- mood drift -------------------------------------------------------------------

    private fun resetDrift(nowMs: Long) {
        driftMood = Mood.Neutral
        driftHolding = false
        driftNextMs = nowMs + between(Tuning.DriftRestMinMs, Tuning.DriftRestMaxMs)
    }

    /** Rest on Neutral for 20-60 s, then hold a weighted pick for 10-40 s, and repeat. */
    private fun updateDrift(nowMs: Long) {
        if (nowMs < driftNextMs) return
        if (driftHolding) {
            driftMood = Mood.Neutral
            driftHolding = false
            driftNextMs = nowMs + between(Tuning.DriftRestMinMs, Tuning.DriftRestMaxMs)
        } else {
            driftMood = pick(driftWeights(minuteOfDay))
            driftHolding = true
            driftNextMs = nowMs + between(Tuning.DriftHoldMinMs, Tuning.DriftHoldMaxMs)
        }
    }

    private fun pick(weights: List<Pair<Mood, Int>>): Mood {
        var roll = rng.nextInt(weights.sumOf { it.second })
        for ((mood, w) in weights) {
            if (roll < w) return mood
            roll -= w
        }
        return weights.last().first
    }

    // ---- gesture timers ---------------------------------------------------------------

    private fun updateGestureTimers(nowMs: Long, inputs: Inputs) {
        if (baseState == BaseState.Awake && nowMs >= lookAroundDueMs) {
            val started = react(gestureReaction(Gesture.LookAround), nowMs)
            lookAroundDueMs = nowMs + if (started) lookAroundInterval() else Tuning.GestureRetryMs
        }
        if (baseState == BaseState.Awake && minuteOfDay >= Tuning.YawnStartMinute && nowMs >= yawnDueMs) {
            val started = react(gestureReaction(Gesture.Yawn), nowMs)
            yawnDueMs = nowMs + if (started) yawnInterval() else Tuning.GestureRetryMs
        }
        if (baseState == BaseState.LowBattery && inputs.batteryPercent <= Tuning.CriticalBatteryPercent &&
            nowMs >= criticalYawnDueMs
        ) {
            val started = react(gestureReaction(Gesture.Yawn), nowMs)
            criticalYawnDueMs = nowMs + if (started) Tuning.CriticalYawnEveryMs else Tuning.GestureRetryMs
        }
    }

    private fun gestureReaction(g: Gesture) =
        Reaction(g.name, Priority.GestureTimer, listOf(Step(null, g.durationMs.toLong(), gesture = g)))

    private fun lookAroundInterval(): Long =
        if (minuteOfDay >= Tuning.AfternoonStartMinute && minuteOfDay < Tuning.EveningStartMinute) {
            between(Tuning.LookAroundAfternoonMinMs, Tuning.LookAroundAfternoonMaxMs)
        } else {
            between(Tuning.LookAroundMinMs, Tuning.LookAroundMaxMs)
        }

    private fun yawnInterval(): Long =
        Tuning.YawnEveryMs + between(-Tuning.YawnJitterMs, Tuning.YawnJitterMs)

    private fun between(a: Long, b: Long): Long = a + (rng.nextDouble() * (b - a)).toLong()

    companion object {
        /** 23:00 to 06:59. */
        fun inSleepWindow(minuteOfDay: Int): Boolean =
            minuteOfDay >= Tuning.SleepStartMinute || minuteOfDay < Tuning.WakeMinute

        /**
         * Weights for idle mood drift (spec 05). Only these moods are ever picked spontaneously: Angry, Sad,
         * Love, Surprised, Broken, Crazy and Denying are reactions.
         */
        fun driftWeights(minuteOfDay: Int): List<Pair<Mood, Int>> = when {
            // 07:30-11:59: toward Happy and Neutral
            minuteOfDay >= Tuning.MorningStartMinute && minuteOfDay < Tuning.AfternoonStartMinute -> listOf(
                Mood.Neutral to 55, Mood.Happy to 30, Mood.Bored to 6,
                Mood.Sceptic to 4, Mood.Tired to 3, Mood.Wink to 6,
            )
            // 12:00-17:59: Neutral and Bored
            minuteOfDay >= Tuning.AfternoonStartMinute && minuteOfDay < Tuning.EveningStartMinute -> listOf(
                Mood.Neutral to 50, Mood.Happy to 8, Mood.Bored to 28,
                Mood.Sceptic to 8, Mood.Tired to 4, Mood.Wink to 2,
            )
            // 18:00-22:59: Tired rises every hour
            minuteOfDay >= Tuning.EveningStartMinute && minuteOfDay < Tuning.SleepStartMinute -> {
                val hoursIn = (minuteOfDay - Tuning.EveningStartMinute) / 60
                listOf(
                    Mood.Neutral to 50, Mood.Happy to 10, Mood.Bored to 12,
                    Mood.Sceptic to 6, Mood.Tired to 8 + 8 * hoursIn, Mood.Wink to 3,
                )
            }
            else -> listOf(
                Mood.Neutral to 50, Mood.Happy to 15, Mood.Bored to 15,
                Mood.Sceptic to 8, Mood.Tired to 8, Mood.Wink to 4,
            )
        }
    }
}
