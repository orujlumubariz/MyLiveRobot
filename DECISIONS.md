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
