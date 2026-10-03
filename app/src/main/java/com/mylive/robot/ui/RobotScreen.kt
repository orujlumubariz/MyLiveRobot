package com.mylive.robot.ui

import android.view.Choreographer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.mylive.robot.Tuning
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.FaceRenderer.drawFace
import com.mylive.robot.face.Mood

@Composable
fun RobotScreen() {
    val animator = remember { FaceAnimator() }
    // Redrawn every frame: the Canvas reads this counter, which the frame callback bumps.
    var frame by remember { mutableLongStateOf(0L) }

    // Debug panel: exists in debuggable builds only. Two fingers held for 1 s open it.
    val debuggable = DebugGate.isDebuggable(LocalContext.current.applicationInfo.flags)
    val debugState = remember { DebugState() }
    var panelOpen by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(-1) } // -1 live face, 0 kinds gallery, 1 moods gallery
    var forcedMood by remember { mutableStateOf(Mood.Neutral) }

    // Choreographer instead of withFrameNanos: the callback gets a primitive Long, so no boxing per frame.
    DisposableEffect(animator) {
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            var last = 0L
            override fun doFrame(frameTimeNanos: Long) {
                if (last != 0L) animator.update((frameTimeNanos - last) / NANOS_PER_MS)
                last = frameTimeNanos
                frame = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }

    val root = if (debuggable) {
        Modifier.twoFingerHold(Tuning.DebugHoldMs) { panelOpen = true }
    } else {
        Modifier
    }
    Box(Modifier.fillMaxSize().background(Tuning.BackgroundColor).testTag("robot-root").then(root)) {
        if (page >= 0) {
            GalleryScreen(page)
        } else {
            // Face is centred on the full screen, not the cutout-safe area, so it looks centred to the eye.
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val tick = frame // subscribe this draw to the frame counter
                drawFace(animator.face)
            }
        }
        if (debuggable && panelOpen) {
            DebugPanel(
                state = debugState,
                currentMood = forcedMood,
                page = page,
                onMood = { animator.setMood(it); forcedMood = it; page = -1 },
                onGesture = { animator.play(it); page = -1 },
                onPage = { page = it },
                onClose = { panelOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private const val NANOS_PER_MS = 1_000_000f
