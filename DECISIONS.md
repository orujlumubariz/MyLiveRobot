# Decisions

- Removed the template Material theme (`ui/theme`); Material3 stays a dependency for the future debug panel only.
- Project lives in `C:\Users\mubar\AndroidStudioProjects\MyLiveRobot`; specs copied to `/specs`.
- Task 2: keep-screen-on is unconditional for now; the Sleeping-dim exception arrives with task 14.
- Task 3: glow is stacked translucent copies (10 layers, 3 units apart) instead of blur; cheap and safe on every GPU. Eye/mouth numbers moved into Tuning.
- Task 3 fix: face is centred on the full screen, not the cutout-safe area (cutout inset made it look off-centre). Revisit if a device cutout ever overlaps the face.
- Task 4: renderer draws through the native Android Canvas with reused Paint/Path objects (zero JVM allocation per frame). Lid/tilt cuts use `Path.op(INTERSECT)` so the glow follows the cut shape.
- Task 4: added `thick` and `spin` to EyeParams and `tilt` to MouthParams (spec 03 needs them). Fully closed RECT shrinks to a line as thick as the stroke.
- Task 4: TEMPORARY gallery toggle = tap anywhere. Replaced by the two-finger debug panel in task 11.
- Task 4 (Mubariz's call): Heart/Love mouth = small smile (new `MouthKind.ARC_SMILE`); Chevron/Denying mouth = zigzag (new `MouthKind.ZIGZAG`, w 140). This overrides the DASH for Love and Denying in spec 03; use these in `Moods.kt` (task 5).
- Task 5: moods are rows in `Moods.kt` (`Mood` enum + `MoodSpec`); `Moods.apply(face, mood)` copies a row into FaceParams in place. Full battery = Happy (no separate row). Wink's closed eye is ARC_UP, as on the sheet.
- Task 5: the `renderMood(mood): ImageBitmap` snapshot test hook from spec 03 is not built; the debug gallery (tap to cycle face, kinds, moods) covers visual checks. Revisit if automated snapshot tests are wanted.
- Task 5: Charging bolt glyph and glow pulse are deferred (tasks 9 and 15).
- Task 5 (Mubariz's call): LowBattery mouth = battery icon with one bar of charge (`MouthKind.BATTERY_LOW`, w 200), eyes stay half-lidded. Replaces the plain dash so the face is readable without explanation. A bar blink/flicker can be added in task 9.
- Task 5: Waking face is a placeholder; Mubariz wants to improve it through its animation (tasks 8/9/14).
- Task 6: frame loop uses `Choreographer.FrameCallback` (primitive Long, no boxing per frame) instead of `withFrameNanos`; the Canvas reads a `mutableLongStateOf` frame counter to redraw.
- Task 6: until the blink-swap (task 8), a mood switch changes eye/mouth *kinds* instantly while numbers ease, so some switches look odd (e.g. a spiral growing from RECT size). Expected.
- Task 6: TEMPORARY bottom strip of mood buttons plus a "Gallery" button replaces the tap-to-cycle gallery. Both go away with the debug panel (task 11).
- Task 6 perf check on the test phone (2340x1080): about 61 fps, jank ~0%, with 10 glow layers. `dumpsys gfxinfo` GPU percentiles were noisy (GPU clock scaling), so redo a profiler check on a mid-range device in task 16.
