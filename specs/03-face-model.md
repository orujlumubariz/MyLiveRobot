# 03 Face construction model

Source: the 12-face sheet (My_Robot screenshot) and `../expression-sheet-review.md`. This file turns it into data. Moods are **rows in a table** (`Moods.kt`), not drawn assets.

## Virtual canvas
Draw in a virtual space of **1000 x 600** units, centred and scaled uniformly to fit the safe area (`scale = min(w/1000, h/600)`). Background fills the whole screen. All sizes below are virtual units.

Colours: `ON = #35E0E8` (features), `BG = #0B1214`, glow = same colour drawn wider at 25% alpha (blur radius 24 units, or two stacked strokes if blur is too slow). `glow` param scales glow alpha 0..1.

Anchor points (face centre offset `(fx, fy)` is added to everything, used for drift/shake):
- Left eye centre `(300, 230)`, right eye centre `(700, 230)`
- Mouth centre `(500, 440)`

## FaceParams (what the renderer draws; everything animatable is a Float)
```
Eye (one per side):
  kind      EyeKind          (not animatable, swapped via blink-swap, see 04)
  w, h      size of the shape
  open      0..1             1 = full height, 0 = closed line (blink drives this)
  tilt      degrees          rotation of the top lid line; + = inner side lower (angry), - = inner side higher (sad)
  lidTop    0..1             fraction of height covered from the top (half-lidded = 0.5)
  dx, dy    offset from anchor (look direction)
Mouth:
  kind      MouthKind        DASH | CHEVRON_V | ARC_FROWN
  w         width
  thick     stroke thickness
  dy        offset from anchor
Global:
  fx, fy    face offset (drift, shake)
  glow      0..1
  scale     1.0 default (breathing pulse, squash)
```
Stroke style for lines/arcs: `thick` wide, round caps. Solid shapes (RECT, HEART) are filled.

## EyeKind and how each is drawn
| Kind | Drawing |
|---|---|
| RECT | rounded rect `w x h`, corner radius `0.4*w`; `open` scales height; `lidTop`/`tilt` cut the top with a straight line |
| DROOP | RECT cut by a lid line plus rounded outer-bottom, reads as a half teardrop (Sad). Implement as RECT with `tilt = -18`, `lidTop = 0.35`, and a larger radius on the outer bottom corner |
| ARC_UP | stroked arc shaped like an arch ∩, width `w`, height `h` (Happy) |
| ARC_DOWN | stroked arc shaped like a smile-curve ∪, width `w`, height `h` (Tired, closed eye, Wink's closed side) |
| RING | stroked circle, diameter `w`, `thick` stroke (Surprised) |
| X | two crossing strokes in a `w x w` box (Broken) |
| SPIRAL | stroked Archimedean spiral, 2.5 turns, diameter `w`; rotates continuously while the mood is active (Crazy) |
| HEART | filled heart, width `w`; beats (see 04) (Love) |
| CHEV_L / CHEV_R | `>` on the left eye, `<` on the right eye (Denying), stroke `thick` |
| LINE | horizontal bar `w` wide, `thick` tall (Sceptic's lowered side, Sleeping) |

Rule: eyes of different kinds never morph into each other. A kind change uses the **blink-swap** (close, swap, open) from file 04.

## Default numbers
Eye RECT neutral: `w 80, h 150, open 1, tilt 0, lidTop 0`. Mouth DASH: `w 110, thick 14, dy 0`. Stroke `thick` for eyes: 14. `glow 0.8`.

## Value table: the 12 faces from the sheet
Only deviations from the defaults are listed. L/R means left/right eye; "same" = both identical.

| Mood | Eye kind | Eye numbers | Mouth | Glow | Category |
|---|---|---|---|---|---|
| Neutral | RECT | defaults | DASH 110 | 0.8 | core |
| Happy | ARC_UP | w 110, h 70 | CHEVRON_V w 90, dy +10 | 1.0 | core |
| Sad | DROOP | w 110, h 100, tilt -18, lidTop 0.35, dy +15 | ARC_FROWN w 100, dy +15 | 0.55 | core |
| Angry | RECT | w 110, h 90, tilt +22, lidTop 0.3 | DASH 130 | 1.0 | core |
| Tired | ARC_DOWN | w 110, h 40 | DASH 90 | 0.5 | core |
| Surprised | RING | w 100, thick 14, dy -10 | DASH 60 | 1.0 | core |
| Love | HEART | w 120 | DASH 110 | 1.0 | core |
| Wink | L: RECT defaults; R: ARC_UP w 100 h 40 | | DASH 110, tilted 4 deg | 0.9 | reaction |
| Sceptic | L: RECT w 110 h 100 lidTop 0.55, dy +5; R: LINE w 130 (raised, dy -20) | | DASH 100, dy +5 | 0.7 | reaction |
| Denying | CHEV_L / CHEV_R | w 90 | DASH 100 | 0.9 | reaction |
| Broken | X | w 90 | DASH 110, jitters | 0.6, flickers | glitch |
| Crazy | SPIRAL | w 110 | DASH 110, jitters | 1.0 | glitch |

Note on Wink: left eye open RECT, right eye is the closed arc, as on the sheet; implement as per-eye kinds.

## Added states (missing from the sheet, needed by V1 goals; invented defaults, adjust after seeing them)
| State face | Eye kind | Numbers | Mouth | Glow |
|---|---|---|---|---|
| Sleeping | ARC_DOWN | w 110, h 30, dy +30 | DASH 60, dy +20 | 0.25 |
| Waking (transition face) | RECT | open 0.3, lidTop 0.4 | DASH 80 | 0.5 |
| Bored | RECT | w 90, h 110, lidTop 0.45 | DASH 90 | 0.6 |
| Curious (touched) | RECT | w 95, h 170 | DASH 70 | 1.0 |
| Charging | ARC_UP | w 110, h 50 | DASH 70 | pulses 0.5 to 1.0 (see 04); small bolt glyph, 60 units, top right at `(900, 70)` |
| Low battery | RECT | w 80, h 150, open 0.55, lidTop 0.3 | DASH 70 | 0.35 |
| Full battery | = Happy | | | |

## Rendering rules
- The renderer reads only `FaceParams`; it contains no mood logic.
- Pre-build `Path` objects once; update by transform where possible; no allocation per frame.
- Left/right eyes mirror each other for `tilt` (angry on both sides points inward-down).
- Test hook: a function `renderMood(mood): ImageBitmap` for snapshot checks against the sheet.

## Acceptance for this file
Each of the 12 sheet faces is recognisable next to the original screenshot at 1280x720 and at 800x360, and Happy, Tired, Wink arcs are distinguishable at 800x360.
