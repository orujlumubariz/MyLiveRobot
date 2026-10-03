package com.mylive.robot.ui

import android.view.Choreographer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.mylive.robot.Tuning
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.FaceRenderer.drawFace
import com.mylive.robot.face.Mood

@Composable
fun RobotScreen() {
    val animator = remember { FaceAnimator() }
    // Redrawn every frame: the Canvas reads this counter, which the frame callback bumps.
    var frame by remember { mutableLongStateOf(0L) }
    // TEMPORARY (until the debug panel, task 11): bottom strip forces moods; "Gallery" cycles pages.
    var page by remember { mutableIntStateOf(-1) }

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

    Box(Modifier.fillMaxSize().background(Tuning.BackgroundColor)) {
        if (page >= 0) {
            GalleryScreen(page)
        } else {
            // Face is centred on the full screen, not the cutout-safe area, so it looks centred to the eye.
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val tick = frame // subscribe this draw to the frame counter
                drawFace(animator.face)
            }
        }
        DebugStrip(
            onGallery = { page = if (page >= 1) -1 else page + 1 },
            onMood = { animator.setMood(it); page = -1 },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DebugStrip(onGallery: () -> Unit, onMood: (Mood) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC0B1214))
            .horizontalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onGallery) { Text("Gallery", color = Color.White, fontSize = 12.sp) }
        Mood.values().forEach { m ->
            TextButton(onClick = { onMood(m) }) { Text(m.name, color = Tuning.FaceColor, fontSize = 12.sp) }
        }
    }
}

private const val NANOS_PER_MS = 1_000_000f
