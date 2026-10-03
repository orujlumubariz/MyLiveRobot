# 06 Ordered task list for Claude Code

Do tasks in order. Each is one commit, app stays runnable. Spec references in brackets. "Done when" is the acceptance test; show Mubariz a screenshot or short screen recording for visual tasks.

| # | Task | Done when |
|---|---|---|
| 1 | **Project skeleton** [02]: Gradle project, package layout, `Tuning.kt`, empty `MainActivity` with black Compose screen | builds, installs, black screen |
| 2 | **Landscape + immersive** [02]: sensorLandscape, no recreation on rotate, hide system bars, cutout handling, keep-screen-on | rotating 180 deg keeps state; no bars; swipe from edge shows bars transiently |
| 3 | **Face renderer, static Neutral** [03]: virtual canvas scaling, `FaceParams`, RECT eyes, DASH mouth, glow | Neutral matches the sheet at 1280x720 and 800x360, centred, scales without distortion |
| 4 | **All eye and mouth kinds** [03]: RECT with lid/tilt, DROOP, ARC_UP/DOWN, RING, X, SPIRAL (static), HEART, CHEV, LINE; mouth DASH/CHEVRON_V/ARC_FROWN | debug gallery screen shows all kinds with no clipping |
| 5 | **Mood table + 12 faces** [03]: `Moods.kt` rows, gallery of the 12 sheet faces plus added states | each face recognisable vs the sheet; Happy/Tired/Wink distinguishable at 800x360 |
| 6 | **Animator core** [04]: frame loop, spring easing, `Easing.kt`, instant mood switch via debug buttons, params ease to target | switching mood eases smoothly, no allocation per frame (verify in profiler) |
| 7 | **Blink + look wander + drift + breathing** [04] | blink never rhythmic; neutral looks alive for 60 s; Sleeping/Tired variants differ |
| 8 | **Blink-swap transitions** [04] | switching between any two moods with different kinds shows close, swap, open; never a half-morph |
| 9 | **Per-mood micro-animations** [04]: Love beat, Broken flicker, Crazy spin, Angry tremble, etc. | each mood shows its idle from the 04 table |
| 10 | **Gestures** [04]: ShakeNo, NodYes, Bounce, Jolt, GlitchBurst, Giggle, Yawn, LookAround | debug panel triggers each; returns to prior mood after |
| 11 | **Debug panel** [02]: two-finger 1 s long press, force mood/gesture, fake battery/time | works in debug build only |
| 12 | **BehaviorEngine skeleton + tests** [05]: states, priority, mood drift, fake clock | JUnit tests for priority and drift pass |
| 13 | **Touch input** [05]: tap, eye tap, double tap, long press, pet, rapid taps, swipe | each row in the touch table produces its reaction |
| 14 | **Time of day** [05]: sleep/wake schedule, idle sleep, dimming flag | fake clock to 23:00 gives sleep sequence; touch wakes; 07:00 wake-up sequence |
| 15 | **Battery/charging** [05]: `BatteryMonitor`, all device-event reactions, charging bolt glyph | plug/unplug and thresholds (via debug fake and via real device) trigger the right faces |
| 16 | **Polish pass**: tune `Tuning.kt` numbers with Mubariz, performance check on a mid-range device, app icon | 60 fps, no jank on transitions, Mubariz signs off the feel |
| 17 | **Optional later**: motion sensor reactions, haptics, sound, extra moods | only when Mubariz asks |

## Per-task rules
- Unit tests for pure logic (engine, easing, timers); manual checks for visuals.
- Update `DECISIONS.md` in the repo whenever a default was picked where the spec was silent.
- If a number in the spec looks wrong on screen, change it in `Tuning.kt`/`Moods.kt` and note it; do not ask.

## First prompt to give Claude Code
> Read CLAUDE.md and the spec files 02 to 06 in /specs. Start with task 1 of 06-task-list.md. Finish one task at a time, run the build, commit, then tell me what to look at before starting the next one.

Copy this folder into the repo as `/specs` and put file 01 at the repo root as `CLAUDE.md`.
