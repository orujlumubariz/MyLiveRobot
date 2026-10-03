# 01 Overview and project brief (copy as CLAUDE.md in the repo root)

## What we are building
A native Android app. One screen, landscape, full-screen. The screen shows a robot **face only** (no body): two eyes and a mouth, glowing cyan on near-black, LED/pixel look. The face is alive: it blinks, drifts, changes mood, and reacts to touch, time of day, and battery/charging.

## Scope V1
Vector face drawn on a Compose Canvas, idle animation, 12 expressions + extra states, touch reactions, behavior states, time-of-day, battery/charging reactions. Optional later: motion sensor reactions, sound, haptics.

## Out of scope (do not add)
AI/LLM, backend, accounts, cloud, analytics, ads, settings screens beyond a hidden debug panel, body/arms, per-mood colours.

## Settled decisions (do not reopen)
- Face only. Single colour: cyan `#35E0E8` on `#0B1214`. Glow is a lighter/blurred copy of the same cyan.
- Kotlin, Jetpack Compose, drawing with `Canvas` (no bitmaps, no Lottie, no game engine).
- Face is **parameterised** (numbers per feature) so moods are data, not hand-drawn assets.
- Identifier spelling: `Sceptic` (UK), as on the sheet.

## Defaults I picked (change only if Mubariz says so)
- "Denying" is a static face (> <) AND the head-shake motion exists as a separate gesture used on rejection triggers.
- "Broken" and "Crazy" are glitch states, not moods; they are triggered by special events only.
- Mouth is simple (dash) on most moods on purpose; Happy and Sad use curves.
- Only the face moves inside the screen; there is no visible bezel in the app (the bezel on the sheet was a mock-up frame).

## How Claude Code should work
- Follow the spec files 02 to 06 in this folder, in order. Task list is in `06`.
- One task = one small commit. Keep the app runnable after every task.
- Keep all tunable numbers (durations, sizes, probabilities) in one `Tuning.kt`. No magic numbers elsewhere.
- Rendering must stay at 60 fps on a mid-range phone: no allocations inside the draw loop, reuse `Path` and `Paint` objects.
- Ask Mubariz only when a spec is silent AND the choice is visible to the user. Otherwise pick a default and note it in `DECISIONS.md` in the repo.

## Spec files
| File | Content |
|---|---|
| 02 | Project setup, architecture, package layout |
| 03 | Face model and value tables per mood |
| 04 | Motion rules (blink, drift, transitions, easing) |
| 05 | Behavior states, triggers, priority |
| 06 | Ordered task list with acceptance criteria |
