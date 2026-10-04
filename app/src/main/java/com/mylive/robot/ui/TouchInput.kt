package com.mylive.robot.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import com.mylive.robot.behavior.TouchInterpreter

/**
 * Forwards single-finger input to [interpreter]. When [enabled] is false (the debug panel is open) the
 * face ignores touch completely. A second finger cancels the touch, so the
 * two-finger debug hold never also counts as a tap. Events are stamped with the same clock the
 * frame loop uses (System.nanoTime based).
 */
fun Modifier.robotTouch(interpreter: TouchInterpreter, enabled: Boolean = true): Modifier =
    onSizeChanged {
        interpreter.screenW = it.width.toFloat()
        interpreter.screenH = it.height.toFloat()
    }.pointerInput(interpreter, enabled) {
        if (!enabled) return@pointerInput
        interpreter.slopPx = viewConfiguration.touchSlop
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(first.uptimeMillis, first.position)
            interpreter.onDown(nowMs(), first.position.x, first.position.y)
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                if (!cancelled && event.changes.count { it.pressed } >= 2) {
                    cancelled = true
                    interpreter.onCancel(nowMs())
                }
                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    if (!cancelled) {
                        val v = tracker.calculateVelocity()
                        interpreter.onUp(nowMs(), change.position.x, change.position.y, v.x, v.y)
                    }
                    break
                }
                if (!cancelled) interpreter.onMove(nowMs(), change.position.x, change.position.y)
            }
        }
    }

private fun nowMs(): Long = System.nanoTime() / 1_000_000L
