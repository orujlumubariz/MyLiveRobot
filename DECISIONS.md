# Decisions

- Removed the template Material theme (`ui/theme`); Material3 stays a dependency for the future debug panel only.
- Project lives in `C:\Users\mubar\AndroidStudioProjects\MyLiveRobot`; specs copied to `/specs`.
- Task 2: keep-screen-on is unconditional for now; the Sleeping-dim exception arrives with task 14.
- Task 3: glow is stacked translucent copies (10 layers, 3 units apart) instead of blur; cheap and safe on every GPU. Eye/mouth numbers moved into Tuning.
- Task 3 fix: face is centred on the full screen, not the cutout-safe area (cutout inset made it look off-centre). Revisit if a device cutout ever overlaps the face.
