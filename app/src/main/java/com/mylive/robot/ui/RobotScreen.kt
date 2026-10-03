package com.mylive.robot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mylive.robot.Tuning

@Composable
fun RobotScreen() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Tuning.BackgroundColor)
    }
}
