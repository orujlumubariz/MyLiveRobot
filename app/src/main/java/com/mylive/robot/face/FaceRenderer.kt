package com.mylive.robot.face

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.mylive.robot.Tuning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Draws FaceParams. No mood logic. Paints, paths and rects are reused, so nothing is
 * allocated on the JVM heap per frame. Draw runs on the UI thread only.
 */
object FaceRenderer {
    private val argb = Tuning.FaceColor.toArgb()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = argb }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = argb
    }
    private val path = Path()
    private val lid = Path()
    private val rect = RectF()
    private val radii = FloatArray(8)
    private lateinit var c: Canvas
    private var alphaMul = 1f // 0..1, set per feature for the mouth cross-fade

    fun DrawScope.drawFace(face: FaceParams) {
        val s = FaceLayout.scale(size.width, size.height)
        c = drawContext.canvas.nativeCanvas
        c.save()
        c.translate(FaceLayout.offsetX(size.width, s), FaceLayout.offsetY(size.height, s))
        c.scale(s, s)
        c.translate(face.fx, face.fy)
        c.scale(face.scale, face.scale, Tuning.VirtualWidth / 2f, Tuning.VirtualHeight / 2f)

        drawEye(face.leftEye, Tuning.LeftEyeX, 1f, face.glow)
        drawEye(face.rightEye, Tuning.RightEyeX, -1f, face.glow)
        drawMouth(face.mouth, face.glow)
        c.restore()
    }

    // ---- eyes -------------------------------------------------------------------------

    /** innerSign: +1 for the left eye (inner side is +x), -1 for the right eye. */
    private fun drawEye(e: EyeParams, anchorX: Float, innerSign: Float, glow: Float) {
        val cx = anchorX + e.dx
        val cy = Tuning.EyeY + e.dy
        val open = e.open.coerceIn(0f, Tuning.MaxEyeOpen)
        val w = e.w
        path.reset()
        when (e.kind) {
            EyeKind.RECT, EyeKind.DROOP -> {
                rectEye(e, cx, cy, innerSign, open)
                fillShape(path, glow)
            }
            EyeKind.ARC_UP -> {
                val h = max(e.h * open, MIN_EXTENT)
                rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + 1.5f * h)
                path.arcTo(rect, 180f, 180f, true)
                strokeShape(path, e.thick, glow)
            }
            EyeKind.ARC_DOWN -> {
                val h = max(e.h * open, MIN_EXTENT)
                rect.set(cx - w / 2f, cy - 1.5f * h, cx + w / 2f, cy + h / 2f)
                path.arcTo(rect, 0f, 180f, true)
                strokeShape(path, e.thick, glow)
            }
            EyeKind.RING -> {
                val h = max(w * open, MIN_EXTENT)
                rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                path.addOval(rect, Path.Direction.CW)
                strokeShape(path, e.thick, glow)
            }
            EyeKind.X -> {
                val hx = w / 2f
                val hy = hx * open
                path.moveTo(cx - hx, cy - hy); path.lineTo(cx + hx, cy + hy)
                path.moveTo(cx + hx, cy - hy); path.lineTo(cx - hx, cy + hy)
                strokeShape(path, e.thick, glow)
            }
            EyeKind.SPIRAL -> {
                spiral(cx, cy, w / 2f, open, e.spin)
                strokeShape(path, e.thick, glow)
            }
            EyeKind.HEART -> {
                heart(cx, cy, w, open)
                fillShape(path, glow)
            }
            EyeKind.CHEV_L, EyeKind.CHEV_R -> {
                val dir = if (e.kind == EyeKind.CHEV_L) 1f else -1f // +1 points right
                val hx = w * Tuning.ChevronHalfWidthRatio
                val hy = w / 2f * open
                path.moveTo(cx - dir * hx, cy - hy)
                path.lineTo(cx + dir * hx, cy)
                path.lineTo(cx - dir * hx, cy + hy)
                strokeShape(path, e.thick, glow)
            }
            EyeKind.LINE -> {
                val half = max(w - e.thick, 0f) / 2f
                path.moveTo(cx - half, cy); path.lineTo(cx + half, cy)
                strokeShape(path, e.thick, glow)
            }
        }
    }

    /** RECT and DROOP: rounded rect, optionally cut by a (tilted) lid line. Result in [path]. */
    private fun rectEye(e: EyeParams, cx: Float, cy: Float, innerSign: Float, open: Float) {
        val w = e.w
        val h = max(e.h * open, e.thick) // fully closed = a line as thick as the stroke
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val r = min(Tuning.EyeCornerRatio * w, min(w, h) / 2f)
        if (e.kind == EyeKind.DROOP) {
            radii.fill(r)
            val outer = min(Tuning.DroopOuterRatio * w, h)
            val i = if (innerSign > 0f) 6 else 4 // outer-bottom corner: BL for left eye, BR for right
            radii[i] = outer
            radii[i + 1] = outer
            path.addRoundRect(rect, radii, Path.Direction.CW)
        } else {
            path.addRoundRect(rect, r, r, Path.Direction.CW)
        }
        if (e.lidTop > 0f || e.tilt != 0f) {
            val slope = tan(e.tilt * DEG) * innerSign
            val y0 = cy - h / 2f + e.lidTop * h
            lid.reset()
            lid.moveTo(cx - w, y0 - slope * w)
            lid.lineTo(cx + w, y0 + slope * w)
            lid.lineTo(cx + w, cy + h)
            lid.lineTo(cx - w, cy + h)
            lid.close()
            path.op(lid, Path.Op.INTERSECT)
        }
    }

    private fun spiral(cx: Float, cy: Float, radius: Float, open: Float, spinDeg: Float) {
        val n = Tuning.SpiralSegments
        val spin = spinDeg * DEG
        path.moveTo(cx, cy)
        for (i in 1..n) {
            val t = i.toFloat() / n
            val a = spin + Tuning.SpiralTurns * TWO_PI * t
            path.lineTo(cx + radius * t * cos(a), cy + radius * t * sin(a) * open)
        }
    }

    /** Heart outline in fractions of w; y is squashed by open. */
    private fun heart(cx: Float, cy: Float, w: Float, open: Float) {
        val k = w * open
        path.moveTo(cx, cy - 0.18f * k)
        path.cubicTo(cx, cy - 0.44f * k, cx - 0.5f * w, cy - 0.44f * k, cx - 0.5f * w, cy - 0.12f * k)
        path.cubicTo(cx - 0.5f * w, cy + 0.15f * k, cx - 0.1f * w, cy + 0.28f * k, cx, cy + 0.42f * k)
        path.cubicTo(cx + 0.1f * w, cy + 0.28f * k, cx + 0.5f * w, cy + 0.15f * k, cx + 0.5f * w, cy - 0.12f * k)
        path.cubicTo(cx + 0.5f * w, cy - 0.44f * k, cx, cy - 0.44f * k, cx, cy - 0.18f * k)
        path.close()
    }

    // ---- mouth ------------------------------------------------------------------------

    private fun drawMouth(m: MouthParams, glow: Float) {
        val x = Tuning.MouthX
        val cy = Tuning.MouthY + m.dy
        val half = max(m.w - m.thick, 0f) / 2f
        alphaMul = m.alpha.coerceIn(0f, 1f)
        c.save()
        c.rotate(m.tilt, x, Tuning.MouthY)
        path.reset()
        when (m.kind) {
            MouthKind.DASH -> {
                path.moveTo(x - half, cy); path.lineTo(x + half, cy)
            }
            MouthKind.CHEVRON_V -> {
                val d = m.w * Tuning.MouthChevronDepthRatio / 2f
                path.moveTo(x - half, cy - d); path.lineTo(x, cy + d); path.lineTo(x + half, cy - d)
            }
            MouthKind.ARC_FROWN -> {
                val h = m.w * Tuning.MouthFrownHeightRatio
                rect.set(x - m.w / 2f, cy - h / 2f, x + m.w / 2f, cy + 1.5f * h)
                path.arcTo(rect, 180f, 180f, true)
            }
            MouthKind.ARC_SMILE -> {
                val h = m.w * Tuning.MouthSmileHeightRatio
                rect.set(x - m.w / 2f, cy - 1.5f * h, x + m.w / 2f, cy + h / 2f)
                path.arcTo(rect, 0f, 180f, true)
            }
            MouthKind.ZIGZAG -> {
                val n = Tuning.MouthZigzagSegments
                val amp = m.w * Tuning.MouthZigzagAmpRatio
                path.moveTo(x - half, cy + amp)
                for (i in 1..n) {
                    val sign = if (i % 2 == 1) -1f else 1f
                    path.lineTo(x - half + 2f * half * i / n, cy + sign * amp)
                }
            }
            MouthKind.BATTERY_LOW -> {
                batteryOutline(x, cy, m)
                strokeShape(path, m.thick, glow)
                batteryBar(x, cy, m)
                fillShape(path, glow)
                c.restore()
                alphaMul = 1f
                return
            }
        }
        strokeShape(path, m.thick, glow)
        c.restore()
        alphaMul = 1f
    }

    /** Battery body plus terminal nub, as outline strokes, in [path]. */
    private fun batteryOutline(x: Float, cy: Float, m: MouthParams) {
        val h = m.w * Tuning.BatteryHeightRatio
        val r = h * 0.2f
        rect.set(x - m.w / 2f, cy - h / 2f, x + m.w / 2f, cy + h / 2f)
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        val nubW = m.w * Tuning.BatteryNubWidthRatio
        val nubH = h * Tuning.BatteryNubHeightRatio
        rect.set(x + m.w / 2f + m.thick / 2f, cy - nubH / 2f, x + m.w / 2f + m.thick / 2f + nubW, cy + nubH / 2f)
        path.addRoundRect(rect, nubW / 2f, nubW / 2f, Path.Direction.CW)
    }

    /** The one remaining bar of charge, filled, at the left inside the body. In [path]. */
    private fun batteryBar(x: Float, cy: Float, m: MouthParams) {
        val h = m.w * Tuning.BatteryHeightRatio
        val gap = m.thick * 0.9f
        val inner = m.w - m.thick
        val left = x - m.w / 2f + m.thick / 2f + gap
        val barH = h - m.thick - 2f * gap
        rect.set(left, cy - barH / 2f, left + inner * Tuning.BatteryBarFraction, cy + barH / 2f)
        path.reset()
        path.addRoundRect(rect, 3f, 3f, Path.Direction.CW)
    }

    // ---- primitives: shape + stacked glow layers --------------------------------------

    private fun glowAlpha(glow: Float) = (255f * alphaMul * Tuning.GlowAlpha * glow / Tuning.GlowLayers).toInt()

    /** Filled shape; glow is a wide stroke of the outline behind it. */
    private fun fillShape(p: Path, glow: Float) {
        val a = glowAlpha(glow)
        for (i in Tuning.GlowLayers downTo 1) {
            stroke.strokeWidth = 2f * i * Tuning.GlowSpread
            stroke.alpha = a
            c.drawPath(p, stroke)
        }
        fill.alpha = (255f * alphaMul).toInt()
        c.drawPath(p, fill)
    }

    private fun strokeShape(p: Path, thick: Float, glow: Float) {
        val a = glowAlpha(glow)
        for (i in Tuning.GlowLayers downTo 1) {
            stroke.strokeWidth = thick + 2f * i * Tuning.GlowSpread
            stroke.alpha = a
            c.drawPath(p, stroke)
        }
        stroke.strokeWidth = thick
        stroke.alpha = (255f * alphaMul).toInt()
        c.drawPath(p, stroke)
    }

    private const val MIN_EXTENT = 0.01f
    private const val DEG = (PI / 180.0).toFloat()
    private const val TWO_PI = (2.0 * PI).toFloat()
}
