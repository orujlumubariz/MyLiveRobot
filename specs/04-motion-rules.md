# 04 Motion rules

All values live in `Tuning.kt`. Time in ms. The animator runs per frame with `dtMs` (clamp to 50 so a stall does not cause a jump).

## Layers (applied in this order each frame)
1. **Mood layer**: eases the numeric params toward the active mood's table row.
2. **Look layer**: eye `dx, dy` target (idle wander or touch follow).
3. **Blink layer**: multiplies eye `open`.
4. **Face drift + breathing**: `fx, fy, scale`.
5. **Gesture layer**: one-shot overrides (shake, nod, bounce, glitch), see below.
6. **Glow layer**: pulse or flicker on top of the mood's glow value.

## Easing
Library `Easing.kt`, functions on t in 0..1:
- `easeInOutCubic`: default for mood changes and drift.
- `easeOutBack` (overshoot 1.4): for Surprised, Curious, bounce pop-ins.
- `easeOutQuad`: look/eye movement.
- `linear`: spiral rotation, glow flicker.
- Params that are continuous (w, h, tilt, lidTop, dy, glow, mouth numbers) are eased with a critically damped spring: `v += (target - v) * (1 - exp(-dt / tau))`, `tau` 90 ms default (160 ms for slow moods Sad/Tired/Sleeping). Simple and never overshoots; use `easeOutBack` explicitly only where listed.

## Blink
- Closing 70 ms (easeInOutCubic), hold 40 ms, opening 110 ms. Total about 220 ms.
- Interval random 2.5 to 6.0 s (uniform). 20% chance of a double blink (second blink 180 ms after the first ends).
- Blink applies only to eye kinds RECT, DROOP, RING, HEART (they scale to a line). ARC_UP, ARC_DOWN, X, SPIRAL, CHEV, LINE do not blink.
- Tired: slower blink (closing 160, hold 120, opening 220), interval 1.5 to 3.5 s.
- Suppress blinking during mood transitions and gestures.

## Look (idle wander)
- Every 1.5 to 4.0 s pick a target `(dx, dy)` within an ellipse of 45 x 25 units (Neutral). Move over 180 ms with easeOutQuad, hold until next pick. 35% of picks return to centre.
- Per mood scale of the range: Sleeping 0, Tired 0.3, Bored 1.2 (looks around more), Surprised 0.2, Angry 0.4, Crazy: ignored (spiral).
- Touch follow overrides wander: eyes look toward the finger, clamped to the same ellipse x1.5, 120 ms.

## Face drift and breathing (always on)
- Drift: `fx, fy` follow a slow smooth wander (two summed sines per axis, periods around 7 s and 11 s), amplitude 12 x 8 units. Prevents a dead static image.
- Breathing: `scale = 1 + 0.015 * sin(2*pi*t / 4000)`. Sleeping: period 6000, amplitude 0.025.
- Drift amplitude x0.3 in Sleeping, x1.5 in Crazy.

## Mood transitions
- Default: **blink-swap** when any eye or mouth *kind* differs: close eyes 90 ms (easeInOutCubic), swap kinds and set target numbers instantly while closed, open 140 ms (easeOutBack for Surprised/Curious/Love, otherwise easeInOutCubic). Total about 250 ms.
- Same kinds (e.g. Neutral to Angry to Bored): no blink-swap; springs ease all numbers (tau as above).
- Mouth swaps ride the same blink-swap; when only the mouth kind changes, cross-fade alpha 120 ms.
- Minimum time between mood changes: 400 ms (queue the latest request).
- Sleeping entry: eyes droop slowly over 1200 ms (springs with tau 400), glow fades over 1500 ms. Waking: Sleeping, then Waking face for 600 ms, then target mood, with an `easeOutBack` open.

## Per-mood idle micro-animation
| Mood | Micro-animation |
|---|---|
| Neutral | blink + wander + drift only |
| Happy | tiny bounce: `fy` -6 units, period 1800 ms, easeInOut |
| Sad | eyes `dy` slow sink +10 over 3 s, rare glance down |
| Angry | tilt trembles +/-1.5 deg at 9 Hz amplitude, glow +10% pulses |
| Tired | head nod: `fy` +15 slowly then snaps back up 120 ms every 6-10 s |
| Surprised | holds still, eyes ring width pulses +4% once |
| Love | HEART scale beat: 1.0 to 1.12 to 1.0 every 800 ms (easeOutQuad up 120, down 680) |
| Wink | right eye re-opens to RECT for 400 ms every 5 s, then winks again (optional) |
| Sceptic | raised eye `dy` -20 to -28 slow drift; no blink on the LINE eye |
| Denying | see shake gesture; static between triggers |
| Broken | glow flicker random 0.3 to 0.9 every 60-250 ms; mouth jitters +/-4 units; occasional 80 ms full blackout |
| Crazy | spirals rotate 360 deg per 900 ms, opposite directions for the two eyes; face drift x1.5 |
| Charging | glow breathes 0.5 to 1.0, period 2400 ms, easeInOut |
| Low battery | slow glow flicker 0.25 to 0.4, period 1600 ms; slow tired blinks |

## Gestures (one-shots, return to the previous mood when done)
| Gesture | Motion | Duration |
|---|---|---|
| ShakeNo | `fx` oscillates +/-40 units, 3 full cycles, damping, easeOutQuad envelope; Denying face during it | 700 |
| NodYes | `fy` +/-25 units, 2 cycles | 600 |
| Bounce | `fy` -30 up (easeOutQuad 150) then back (easeInQuad 200), `scale` squash 0.96 on landing | 450 |
| Jolt | `scale` 1.08 instantly, spring back; Surprised face | 500 |
| GlitchBurst | 3 to 5 frames of random `fx` +/-30, glow 0 / 1 flashes, X eyes shown for the burst | 350 |
| Giggle | rapid `fy` +/-5 at 12 Hz, Happy face | 800 |
| Yawn | Tired face, mouth `w` grows 90 to 140 and `thick` to 40 (DASH becomes a filled pill) and back, eyes closed | 1800 |
| LookAround | eyes sweep left, right, centre, 350 ms each | 1100 |

## Acceptance for this file
With the debug panel, every mood idles without visible glitches for 60 s; blink interval is not rhythmic; transitions never show a half-morphed kind; frame time stays under 8 ms on a mid-range device.
