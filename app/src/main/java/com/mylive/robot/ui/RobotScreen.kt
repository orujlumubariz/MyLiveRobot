package com.mylive.robot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.mylive.robot.Tuning
import com.mylive.robot.face.FaceParams
import com.mylive.robot.face.FaceRenderer.drawFace

@Composable
fun RobotScreen() {
    val face = remember { FaceParams() }
    // TEMPORARY (until the debug panel, task 11): tap anywhere to cycle face -> kinds -> moods.
    var page by remember { mutableIntStateOf(-1) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Tuning.BackgroundColor)
            .pointerInput(Unit) { detectTapGestures { page = if (page >= 1) -1 else page + 1 } },
    ) {
        if (page >= 0) {
            GalleryScreen(page)
        } else {
            // Face is centred on the full screen, not the cutout-safe area, so it looks centred to the eye.
            Canvas(Modifier.fillMaxSize()) { drawFace(face) }
        }
    }
}
