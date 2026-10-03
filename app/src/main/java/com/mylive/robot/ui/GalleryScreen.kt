package com.mylive.robot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.robot.Tuning
import com.mylive.robot.face.EyeKind
import com.mylive.robot.face.EyeParams
import com.mylive.robot.face.FaceParams
import com.mylive.robot.face.FaceRenderer.drawFace
import com.mylive.robot.face.MouthKind
import com.mylive.robot.face.MouthParams
import com.mylive.robot.face.Mood
import com.mylive.robot.face.Moods

/** Debug gallery: every eye and mouth kind, each cell outlined at the virtual canvas edge. */
private class Cell(val label: String, val face: FaceParams)

private fun face(
    mouth: MouthParams = MouthParams(),
    eye: EyeParams.(left: Boolean) -> Unit = {},
) = FaceParams(
    leftEye = EyeParams().also { it.eye(true) },
    rightEye = EyeParams().also { it.eye(false) },
    mouth = mouth,
)

private fun kindCells() = listOf(
    Cell("RECT", face()),
    Cell("RECT lid + tilt", face { kind = EyeKind.RECT; w = 110f; h = 90f; tilt = 22f; lidTop = 0.3f }),
    Cell("DROOP", face { kind = EyeKind.DROOP; w = 110f; h = 100f; tilt = -18f; lidTop = 0.35f; dy = 15f }),
    Cell("ARC_UP", face { kind = EyeKind.ARC_UP; w = 110f; h = 70f }),
    Cell("ARC_DOWN", face { kind = EyeKind.ARC_DOWN; w = 110f; h = 40f }),
    Cell("RING", face { kind = EyeKind.RING; w = 100f }),
    Cell("X", face { kind = EyeKind.X; w = 90f }),
    Cell("SPIRAL", face { kind = EyeKind.SPIRAL; w = 110f }),
    Cell("HEART", face(MouthParams(MouthKind.ARC_SMILE, w = 100f, dy = 5f)) { kind = EyeKind.HEART; w = 120f }),
    Cell("CHEV_L / CHEV_R", face(MouthParams(MouthKind.ZIGZAG, w = 140f)) { left -> kind = if (left) EyeKind.CHEV_L else EyeKind.CHEV_R; w = 90f }),
    Cell("LINE", face { kind = EyeKind.LINE; w = 130f }),
    Cell("Mouth CHEVRON_V", face(MouthParams(MouthKind.CHEVRON_V, w = 90f, dy = 10f))),
    Cell("Mouth ARC_FROWN", face(MouthParams(MouthKind.ARC_FROWN, w = 100f, dy = 15f))),
)

private fun moodCells() = Mood.values().map { m ->
    Cell(m.name, FaceParams().also { Moods.apply(it, m) })
}


/** Debug gallery pages: 0 = every eye/mouth kind, 1 = every mood. */
@Composable
fun GalleryScreen(page: Int) {
    val kinds = remember { kindCells() }
    val moods = remember { moodCells() }
    val cells = if (page == 0) kinds else moods
    val columns = if (page == 0) 5 else 6
    Column(
        Modifier
            .fillMaxSize()
            .background(Tuning.BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    Column(Modifier.weight(1f)) {
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(Tuning.VirtualWidth / Tuning.VirtualHeight)
                                .border(1.dp, Color(0x33FFFFFF)),
                        ) { drawFace(cell.face) }
                        Text(cell.label, color = Tuning.FaceColor, fontSize = 12.sp)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
