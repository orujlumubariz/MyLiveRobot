# 02 Android project setup

## Project
- Name: `MyLiveRobot`, package `com.mylive.robot` (change freely; keep it one constant).
- Language Kotlin, UI Jetpack Compose (Material3 only for the debug panel), Gradle Kotlin DSL, version catalog (`libs.versions.toml`).
- `minSdk 26` (Android 8, covers ~97% of devices, gives `BatteryManager` and vibration effect APIs). `targetSdk`/`compileSdk` = latest stable at time of creation.
- No third-party libraries in V1 except AndroidX (core-ktx, activity-compose, lifecycle-runtime-compose, compose BOM). Unit tests: JUnit 4.

## Manifest and window
- Single `MainActivity`, `android:screenOrientation="sensorLandscape"` (both landscape directions).
- `android:configChanges="orientation|screenSize|keyboardHidden|uiMode"` so rotation does not recreate the activity.
- Theme: no action bar, black window background (`#0B1214`).
- Immersive full-screen: `WindowCompat.setDecorFitsSystemWindows(window, false)`, hide status + navigation bars with `WindowInsetsControllerCompat`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Re-apply on `onWindowFocusChanged`.
- Display cutout: `layoutInDisplayCutoutMode = shortEdges`. Face is laid out inside the safe area but the background fills the whole screen.
- Keep screen on while the app is in the foreground (`FLAG_KEEP_SCREEN_ON`), unless state is Sleeping (then allow dim, see 05).
- Permissions: none in V1 (battery state comes from the sticky `ACTION_BATTERY_CHANGED` broadcast; vibration permission only when haptics are added).

## Architecture
Simple unidirectional flow, no DI framework.

```
Inputs (touch, clock, battery)  ->  BehaviorEngine  ->  FaceState target
                                                          |
                          Animator (per frame, 60fps)  <--+
                                                          v
                                  FaceParams (current numbers) -> Canvas draw
```

- `BehaviorEngine`: pure Kotlin, no Android types, driven by `update(nowMs, events)`. Chooses the current mood/state and one-shot gestures. Unit-testable with a fake clock.
- `FaceAnimator`: holds current `FaceParams`, eases them toward the target `FaceParams` of the active mood, applies blink/drift layers. Pure Kotlin, takes `dtMs`.
- `FaceRenderer`: draws `FaceParams` on a Compose `DrawScope`. No logic.
- Frame loop: one `LaunchedEffect` with `withFrameNanos`, calls engine and animator, then invalidates the canvas via a `mutableLongStateOf` frame counter.
- Android adapters (thin): `BatteryMonitor` (BroadcastReceiver -> `Flow<BatteryInfo>`), `ClockSource`, touch via `pointerInput`.

## Package layout
```
com.mylive.robot
  MainActivity.kt
  ui/        RobotScreen.kt, DebugPanel.kt
  face/      FaceParams.kt, Moods.kt (value tables), FaceRenderer.kt, FaceAnimator.kt, Easing.kt
  behavior/  BehaviorEngine.kt, RobotState.kt, Triggers.kt, Gestures.kt
  device/    BatteryMonitor.kt, ClockSource.kt
  Tuning.kt
```

## Debug panel
Hidden: long-press with two fingers for 1 s opens a panel to force any mood/state/gesture, toggle fake battery/time. Needed so Mubariz can see every face without waiting. Debug builds only.

## Acceptance for setup
App installs, opens straight into a black landscape screen with no system bars, survives rotation 180 degrees without restart, and `./gradlew test` runs.
