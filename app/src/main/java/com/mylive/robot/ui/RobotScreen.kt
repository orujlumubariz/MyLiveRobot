package com.mylive.robot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mylive.robot.Tuning
import com.mylive.robot.face.FaceParams
import com.mylive.robot.face.FaceRenderer.drawFace

@Composable
fun RobotScreen() {
    val face = remember { FaceParams() }
    // Background fills the whole screen; the face is laid out inside the cutout-safe area.
    Box(Modifier.fillMaxSize().background(Tuning.BackgroundColor)) {
        Canvas(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            drawFace(face)
        }
    }
}
