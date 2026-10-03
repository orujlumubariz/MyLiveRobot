package com.mylive.robot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mylive.robot.Tuning
import com.mylive.robot.face.FaceParams
import com.mylive.robot.face.FaceRenderer.drawFace

@Composable
fun RobotScreen() {
    val face = remember { FaceParams() }
    // Face is centred on the full screen, not the cutout-safe area, so it looks centred to the eye.
    Box(Modifier.fillMaxSize().background(Tuning.BackgroundColor)) {
        Canvas(Modifier.fillMaxSize()) {
            drawFace(face)
        }
    }
}
