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

    private var following = false
    private var followX = 0f
    private var followY = 0f

    /**
     * Touch follow (spec 04): look toward a normalised screen point, clamped to the idle ellipse
     * scaled by [Tuning.LookFollowScale]. Overrides the wander until [release].
     */
    fun follow(nx: Float, ny: Float) {
        var tx = (nx - 0.5f) * 2f * Tuning.LookRangeX * Tuning.LookFollowScale
        var ty = (ny - 0.5f) * 2f * Tuning.LookRangeY * Tuning.LookFollowScale
        val ex = tx / (Tuning.LookRangeX * Tuning.LookFollowScale)
        val ey = ty / (Tuning.LookRangeY * Tuning.LookFollowScale)
        val r = sqrt(ex * ex + ey * ey)
        if (r > 1f) {
            tx /= r
            ty /= r
        }
        followX = tx
        followY = ty
        following = true
    }

    /** Back to idle wander: eases to centre first. */
    fun release() {
        if (!following) return
        following = false
        moveTo(0f, 0f)
        waitMs = nextInterval()
    }

    /** @param rangeScale per-mood scale of the wander range; 0 means look straight ahead. */
    fun update(dtMs: Float, rangeScale: Float) {
        if (following) {
            val k = Easing.springFactor(dtMs, Tuning.LookFollowTauMs)
            dx = Easing.approach(dx, followX, k)
            dy = Easing.approach(dy, followY, k)
            return
        }
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
