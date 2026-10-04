package com.mylive.robot.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylive.robot.Tuning
import com.mylive.robot.face.Gesture
import com.mylive.robot.face.Mood
import kotlinx.coroutines.withTimeoutOrNull

/** Debug builds only: the panel exists only when the app is installed as debuggable. */
object DebugGate {
    fun isDebuggable(applicationFlags: Int): Boolean = (applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

/**
 * Values the debug panel can fake. The behaviour engine reads these once it exists
 * (battery in task 15, time of day in task 14); until then they are only stored.
 */
class DebugState {
    var fakeBatteryOn by mutableStateOf(false)
    var batteryPercent by mutableIntStateOf(80)
    var charging by mutableStateOf(false)
    var fakeTimeOn by mutableStateOf(false)
    var minuteOfDay by mutableIntStateOf(12 * 60)

    /** When off, the behaviour engine is ignored so a forced mood or gesture stays put. */
    var autoBehavior by mutableStateOf(true)

    /** Base state and running reaction, written by the screen for display. */
    var engineStatus by mutableStateOf("")

    /** Sets a quick scenario by faking the inputs the engine reads. */
    fun scenario(minute: Int, battery: Int, isCharging: Boolean) {
        fakeTimeOn = true
        minuteOfDay = minute
        fakeBatteryOn = true
        batteryPercent = battery
        charging = isCharging
    }
}

/**
 * Calls [onHold] when exactly two fingers stay down, without moving past touch slop, for
 * [holdMs]. Lifting a finger, adding a third, or moving too far cancels.
 */
fun Modifier.twoFingerHold(holdMs: Long, onHold: () -> Unit): Modifier = pointerInput(holdMs) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val start = HashMap<PointerId, Offset>()
        // wait for the second finger, remembering where both fingers landed
        while (true) {
            val e = awaitPointerEvent()
            val pressed = e.changes.filter { it.pressed }
            if (pressed.isEmpty()) return@awaitEachGesture
            if (pressed.size >= 2) {
                pressed.forEach { start[it.id] = it.position }
                break
            }
        }
        // null means the timeout elapsed with both fingers still down and still
        val cancelled = withTimeoutOrNull(holdMs) {
            while (true) {
                val e = awaitPointerEvent()
                val pressed = e.changes.filter { it.pressed }
                if (pressed.size != 2) return@withTimeoutOrNull true
                val moved = pressed.any { c ->
                    val s = start[c.id]
                    s == null || (c.position - s).getDistance() > viewConfiguration.touchSlop * 2f
                }
                if (moved) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (cancelled == null) onHold()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(
    state: DebugState,
    currentMood: Mood,
    page: Int,
    onMood: (Mood) -> Unit,
    onGesture: (Gesture) -> Unit,
    onPage: (Int) -> Unit,
    onWake: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Tuning.FaceColor)) {
        Column(
            modifier
                .testTag("debug-panel")
                .width(Tuning.DebugPanelWidthDp.dp)
                .fillMaxHeight()
                .background(Color(0xFF121E21)) // solid, so the face behind it never shows through
                .drawBehind { drawRect(Tuning.FaceColor.copy(alpha = 0.35f), size = Size(2.dp.toPx(), size.height)) }
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Debug panel", color = Tuning.FaceColor, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }

            Section("View")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Face", "Kinds", "Moods").forEachIndexed { i, label ->
                    FilterChip(selected = page == i - 1, onClick = { onPage(i - 1) }, label = { Text(label) })
                }
            }

            Section("Behaviour")
            LabeledSwitch("Auto behaviour (engine)", state.autoBehavior) { state.autoBehavior = it }
            Text(state.engineStatus, color = Color.White, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { state.scenario(12 * 60, 80, false) }, label = { Text("Awake") })
                AssistChip(onClick = { state.scenario(23 * 60 + 30, 80, false) }, label = { Text("Sleeping") })
                AssistChip(onClick = { state.scenario(12 * 60, 60, true) }, label = { Text("Charging") })
                AssistChip(onClick = { state.scenario(12 * 60, 12, false) }, label = { Text("Low battery") })
                AssistChip(onClick = onWake, label = { Text("Touch (wake)") })
            }
            Text("Battery events (turns the fake battery on)", color = Color.Gray, fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { state.fakeBatteryOn = true; state.charging = true }, label = { Text("Plug in") })
                AssistChip(onClick = { state.fakeBatteryOn = true; state.charging = false }, label = { Text("Unplug") })
                listOf(16, 15, 5, 99, 100).forEach { pct ->
                    AssistChip(
                        onClick = { state.fakeBatteryOn = true; state.batteryPercent = pct },
                        label = { Text("$pct%") },
                    )
                }
            }
            Text("Jump the fake clock", color = Color.Gray, fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("22:59" to 22 * 60 + 59, "23:00" to 23 * 60, "06:59" to 6 * 60 + 59, "07:00" to 7 * 60, "12:00" to 12 * 60)
                    .forEach { (label, minute) ->
                        AssistChip(
                            onClick = { state.fakeTimeOn = true; state.minuteOfDay = minute },
                            label = { Text(label) },
                        )
                    }
            }
            Text(
                "A forced mood lasts until the engine changes the face; switch auto off to hold it.",
                color = Color.Gray,
                fontSize = 11.sp,
            )

            Section("Mood")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Mood.values().forEach { m ->
                    FilterChip(selected = m == currentMood, onClick = { onMood(m) }, label = { Text(m.name) })
                }
            }

            Section("Gesture")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Gesture.values().forEach { g ->
                    AssistChip(onClick = { onGesture(g) }, label = { Text(g.name) })
                }
            }

            Section("Fake battery")
            LabeledSwitch("Use fake battery", state.fakeBatteryOn) { state.fakeBatteryOn = it }
            Text("Level ${state.batteryPercent}%", color = Color.White, fontSize = 13.sp)
            Slider(
                value = state.batteryPercent.toFloat(),
                onValueChange = { state.batteryPercent = it.toInt() },
                valueRange = 0f..100f,
                enabled = state.fakeBatteryOn,
            )
            LabeledSwitch("Charging", state.charging, enabled = state.fakeBatteryOn) { state.charging = it }

            Section("Fake time")
            LabeledSwitch("Use fake time", state.fakeTimeOn) { state.fakeTimeOn = it }
            Text(
                "Time %02d:%02d".format(state.minuteOfDay / 60, state.minuteOfDay % 60),
                color = Color.White,
                fontSize = 13.sp,
            )
            Slider(
                value = state.minuteOfDay.toFloat(),
                onValueChange = { state.minuteOfDay = it.toInt() },
                valueRange = 0f..(24f * 60f - 1f),
                enabled = state.fakeTimeOn,
            )
            Text(
                "Battery and time are stored here; the engine uses them from tasks 14 and 15.",
                color = Color.Gray,
                fontSize = 11.sp,
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Hide panel") }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Tuning.FaceColor, fontSize = 12.sp)
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
