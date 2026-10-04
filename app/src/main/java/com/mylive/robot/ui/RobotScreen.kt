package com.mylive.robot.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.mylive.robot.Tuning
import com.mylive.robot.behavior.BaseState
import com.mylive.robot.behavior.BehaviorEngine
import com.mylive.robot.behavior.Inputs
import com.mylive.robot.behavior.TouchInterpreter
import com.mylive.robot.device.BatteryMonitor
import com.mylive.robot.device.ClockSource
import com.mylive.robot.face.FaceAnimator
import com.mylive.robot.face.FaceRenderer.drawFace
import com.mylive.robot.face.Mood

@Composable
fun RobotScreen(openPanelAtStart: Boolean = false) {
    val context = LocalContext.current
    val animator = remember { FaceAnimator() }
    val engine = remember { BehaviorEngine() }
    val touch = remember(engine) { TouchInterpreter(engine) }
    val clock = remember { ClockSource() }
    val battery = remember { BatteryMonitor(context.applicationContext) }
    // Redrawn every frame: the Canvas reads this counter, which the frame callback bumps.
    var frame by remember { mutableLongStateOf(0L) }

    // Debug panel: exists in debuggable builds only. Two fingers held for 1 s open it.
    val debuggable = DebugGate.isDebuggable(context.applicationInfo.flags)
    // Debug builds start on a fake 12:00 so the real clock does not put the robot to sleep while developing.
    val debugState = remember { DebugState().also { it.fakeTimeOn = debuggable } }
    var panelOpen by remember { mutableStateOf(openPanelAtStart && debuggable) }
    var page by remember { mutableIntStateOf(-1) } // -1 live face, 0 kinds gallery, 1 moods gallery
    var forcedMood by remember { mutableStateOf(Mood.Neutral) }

    // While the panel is open the face ignores touch; drop any touch that was in progress.
    LaunchedEffect(panelOpen) {
        if (panelOpen) touch.onCancel(System.nanoTime() / 1_000_000L)
    }

    // Battery: sticky broadcast, registered while the screen is shown.
    DisposableEffect(battery) {
        battery.start()
        onDispose { battery.stop() }
    }

    // App back in the foreground (not the first start): LookAround.
    DisposableEffect(context, engine) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>().firstOrNull()
        var stopped = false
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                stopped = true
            }

            override fun onStart(owner: LifecycleOwner) {
                if (stopped) {
                    stopped = false
                    engine.onForeground(System.nanoTime() / 1_000_000L)
                }
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // Frame loop on Compose's own frame clock: state written here is applied and drawn in the same frame.
    // (A raw Choreographer callback only got every other frame drawn on the test phone: 30 fps.)
    val driver = remember(animator, engine, touch) {
        FrameDriver(context, animator, engine, touch, battery, clock, debuggable, debugState)
    }
    LaunchedEffect(driver) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                driver.onFrame(frameTimeNanos)
                frame = frameTimeNanos
            }
        }
    }

    val holdForPanel = if (debuggable && !panelOpen) {
        Modifier.twoFingerHold(Tuning.DebugHoldMs) { panelOpen = true }
    } else {
        Modifier
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Tuning.BackgroundColor)
            .testTag("robot-root")
            .robotTouch(touch, enabled = !panelOpen)
            .then(holdForPanel),
    ) {
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
                onWake = { engine.noteInteraction(System.nanoTime() / 1_000_000L) },
                onClose = { panelOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Window brightness 0..1, or [Tuning.BrightnessNormal] to hand control back to the system. */
private fun setWindowBrightness(context: Context, value: Float) {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) {
            val params = c.window.attributes
            params.screenBrightness = value
            c.window.attributes = params
            return
        }
        c = c.baseContext
    }
}

/** One frame of the app: advance the animator, feed the engine, apply what the engine asks for. */
private class FrameDriver(
    private val context: Context,
    private val animator: FaceAnimator,
    private val engine: BehaviorEngine,
    private val touch: TouchInterpreter,
    private val battery: BatteryMonitor,
    private val clock: ClockSource,
    private val debuggable: Boolean,
    private val debugState: DebugState,
) {
    private val inputs = Inputs()
    private var last = 0L
    private var wasAuto = true
    private var engineMood: Mood? = null
    private var shownState: BaseState? = null
    private var shownReaction: String? = null

    fun onFrame(frameTimeNanos: Long) {
        if (last != 0L) animator.update((frameTimeNanos - last) / NANOS_PER_MS)
        last = frameTimeNanos
        runEngine(frameTimeNanos / 1_000_000L)
    }

    private fun runEngine(nowMs: Long) {
        val fakeTime = debuggable && debugState.fakeTimeOn
        val fakeBattery = debuggable && debugState.fakeBatteryOn
        inputs.minuteOfDay = if (fakeTime) debugState.minuteOfDay else clock.minuteOfDay(nowMs)
        val real = battery.info.value
        inputs.batteryPercent = if (fakeBattery) debugState.batteryPercent else real.percent
        inputs.charging = if (fakeBattery) debugState.charging else real.charging
        touch.update(nowMs)
        val out = engine.update(nowMs, inputs)

        if (debuggable && (shownState != engine.baseState || shownReaction != engine.reactionName)) {
            shownState = engine.baseState
            shownReaction = engine.reactionName
            debugState.engineStatus = "State: ${engine.baseState}  Reaction: ${engine.reactionName ?: "-"}"
        }

        val auto = !debuggable || debugState.autoBehavior
        if (auto && !wasAuto) engineMood = null // back on: resync the face with the engine
        wasAuto = auto
        if (!auto) {
            animator.lookAt(null)
            return
        }
        if (out.mood != engineMood) {
            engineMood = out.mood
            animator.setMood(out.mood)
        }
        out.gesture?.let { animator.play(it, out.gestureKeepsFace) }
        animator.lookAt(out.lookTarget)
        out.brightness?.let { setWindowBrightness(context, it) }
    }
}

private const val NANOS_PER_MS = 1_000_000f
