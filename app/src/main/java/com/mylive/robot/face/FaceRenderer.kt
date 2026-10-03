package com.mylive.robot.face

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mylive.robot.Tuning

/** Draws FaceParams. No mood logic. Nothing is allocated per frame. */
object FaceRenderer {

    fun DrawScope.drawFace(face: FaceParams) {
        val s = FaceLayout.scale(size.width, size.height)
        val canvas = drawContext.canvas
        canvas.save()
        canvas.translate(FaceLayout.offsetX(size.width, s), FaceLayout.offsetY(size.height, s))
        canvas.scale(s, s)
        canvas.translate(face.fx, face.fy)
        canvas.translate(Tuning.VirtualWidth / 2f, Tuning.VirtualHeight / 2f)
        canvas.scale(face.scale, face.scale)
        canvas.translate(-Tuning.VirtualWidth / 2f, -Tuning.VirtualHeight / 2f)

        drawEye(face.leftEye, Tuning.LeftEyeX, face.glow)
        drawEye(face.rightEye, Tuning.RightEyeX, face.glow)
        drawMouth(face.mouth, face.glow)
        canvas.restore()
    }

    private fun DrawScope.drawEye(eye: EyeParams, anchorX: Float, glow: Float) {
        // RECT only for now; other kinds arrive in task 4.
        val h = eye.h * eye.open
        val cx = anchorX + eye.dx
        val cy = Tuning.EyeY + eye.dy
        val radius = Tuning.EyeCornerRatio * eye.w
        fillRound(cx, cy, eye.w, h, radius, glow)
    }

    private fun DrawScope.drawMouth(mouth: MouthParams, glow: Float) {
        // DASH only for now: a round-capped bar.
        fillRound(
            Tuning.MouthX, Tuning.MouthY + mouth.dy, mouth.w, mouth.thick, mouth.thick / 2f, glow,
        )
    }

    /** Filled rounded rect centred on (cx, cy), with stacked glow layers behind it. */
    private fun DrawScope.fillRound(
        cx: Float, cy: Float, w: Float, h: Float, radius: Float, glow: Float,
    ) {
        for (i in Tuning.GlowLayers downTo 1) {
            val grow = i * Tuning.GlowSpread
            val a = Tuning.GlowAlpha * glow / Tuning.GlowLayers
            drawRoundRect(
                color = Tuning.FaceColor,
                topLeft = Offset(cx - w / 2f - grow, cy - h / 2f - grow),
                size = Size(w + 2f * grow, h + 2f * grow),
                cornerRadius = CornerRadius(radius + grow),
                alpha = a,
            )
        }
        drawRoundRect(
            color = Tuning.FaceColor,
            topLeft = Offset(cx - w / 2f, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius),
        )
    }
}
