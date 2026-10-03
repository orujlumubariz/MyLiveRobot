package com.mylive.robot.face

import com.mylive.robot.Tuning
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Look layer, idle wander (spec 04): gives the eyes a (dx, dy) that moves to new targets now and then. */
class LookWander(private val rng: Random) {
    var dx = 0f
        private set
    var dy = 0f
        private set

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var moveMs = Tuning.LookMoveMs // starts finished
    private var waitMs = nextInterval()

    /** @param rangeScale per-mood scale of the wander range; 0 means look straight ahead. */
    fun update(dtMs: Float, rangeScale: Float) {
        waitMs -= dtMs
        if (waitMs <= 0f) {
            pick(rangeScale)
            waitMs = nextInterval()
        }
        moveMs += dtMs
        val k = Easing.easeOutQuad((moveMs / Tuning.LookMoveMs).coerceAtMost(1f))
        dx = fromX + (toX - fromX) * k
        dy = fromY + (toY - fromY) * k
    }

    /** Heads back to centre right away, e.g. when the mood stops looking around. */
    fun recentre() = moveTo(0f, 0f)

    private fun pick(rangeScale: Float) {
        if (rangeScale <= 0f || rng.nextFloat() < Tuning.LookCentreChance) {
            moveTo(0f, 0f)
            return
        }
        val angle = rng.nextFloat() * TWO_PI
        val r = sqrt(rng.nextFloat())
        moveTo(
            r * cos(angle) * Tuning.LookRangeX * rangeScale,
            r * sin(angle) * Tuning.LookRangeY * rangeScale,
        )
    }

    private fun moveTo(x: Float, y: Float) {
        fromX = dx
        fromY = dy
        toX = x
        toY = y
        moveMs = 0f
    }

    private fun nextInterval() =
        Tuning.LookMinIntervalMs + rng.nextFloat() * (Tuning.LookMaxIntervalMs - Tuning.LookMinIntervalMs)

    private companion object {
        const val TWO_PI = (2.0 * Math.PI).toFloat()
    }
}
