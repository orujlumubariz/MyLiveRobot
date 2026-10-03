# 05 Behavior states and triggers

`BehaviorEngine` is pure Kotlin: `update(nowMs, inputs) -> BehaviorOutput(mood, gesture?, lookTarget?)`. Faces and gestures are defined in 03 and 04.

## Layers of state
1. **Base state** (long-lived, from time and battery): Awake or Sleeping, plus a base mood.
2. **Reaction** (short, from touch/events): temporary mood and/or gesture that overrides the base mood for a duration, then returns.
3. **Glitch** (rare, random or special): Broken / Crazy bursts.

## Base states
| State | When | Face | Notes |
|---|---|---|---|
| Awake | default | Neutral, with slow mood drift (below) | |
| Sleeping | time-based quiet hours, or no interaction for 10 min (idle sleep) | Sleeping | screen dimmed via window brightness 0.05 (or just dim glow if brightness change is blocked); any touch wakes |
| Charging | device plugged in | Charging face | overrides Awake mood, not Sleeping |
| LowBattery | battery <= 15% and not charging | Low battery face | <= 5%: add Tired yawns every 60 s |
| FullBattery | reached 100% while charging | Happy for 20 s once, then Charging face stays | |

## Time of day (local clock, defaults; constants in Tuning)
| Window | Behaviour |
|---|---|
| 23:00 to 06:59 | Sleeping (sleep entry animation when the window starts; stays asleep until touched) |
| 07:00 to 07:30 | Wake-up: Waking face then Happy for 8 s, then Awake |
| 07:30 to 11:59 | Awake, moods weighted toward Happy/Neutral |
| 12:00 to 17:59 | Neutral/Bored weighted, more LookAround |
| 18:00 to 22:59 | Calm: Tired weight rises each hour, yawn every ~5 min after 21:00 |
A touch while Sleeping in the sleep window wakes the robot for 3 minutes (Waking then Tired), then it falls asleep again.

## Mood drift (Awake, no interaction)
Every 20 to 60 s pick a new base mood from a weighted list; keep it 10 to 40 s. Allowed idle moods: Neutral (weight 50), Happy (15), Bored (15), Sceptic (8), Tired (8), Wink (4). Never pick Angry, Sad, Love, Surprised, Broken, Crazy, Denying spontaneously: those are reactions. Weights shift by time of day as above.

## Touch triggers
Detect in the Compose `pointerInput`. Coordinates normalised 0..1 across the screen.
| Input | Reaction | Duration |
|---|---|---|
| Single tap | Curious face, eyes look at the tap point, then Bounce | 900 |
| Tap on an eye area (within 120 units of an eye) | Wink (if tapped eye is right: wink right; left: swap sides), Giggle | 900 |
| Double tap | Surprised + Jolt | 1200 |
| Long press (>= 600 ms) | Happy, eyes close (ARC_UP) while held, Giggle; on release Happy for 2 s | while held |
| Drag / pet (continuous moving finger, > 1 s total) | Love, eyes follow the finger; on release Love for 3 s | while dragging |
| Rapid taps (>= 5 taps in 2 s) | Angry, then after 3 s Denying + ShakeNo | 4000 |
| Very rapid taps (>= 10 in 3 s) | GlitchBurst then Broken for 3 s, then Crazy 2 s, then back | 5000 |
| Swipe fast (velocity > 2000 px/s) | Surprised + eyes snap in swipe direction | 700 |
| Touch while Sleeping | Waking face, 600 ms, then Curious, then base | 1500 |
Ignore input for 150 ms after a reaction starts (debounce). Reactions never stack: new reaction replaces the old one by priority below.

## Device triggers
| Event | Reaction |
|---|---|
| Charger connected | Surprised 700 ms, then Happy 2 s, then Charging base |
| Charger disconnected | Sad 2 s if battery < 30%, else Neutral/Curious 1.5 s |
| Battery drops to 15% | Sad 2 s, then Low battery base |
| Battery drops to 5% | Tired + Yawn, Low battery base |
| Battery reaches 100% | FullBattery sequence |
| App returns to foreground | LookAround, then Neutral |
| (Later, optional) shake device | Crazy 2 s then Sad; (face down) Sleeping |

## Priority (higher wins; equal priority: newer wins; a lower one that is dropped does not resume)
1. Glitch (Broken / Crazy sequences)
2. Touch reaction
3. Device event reaction (charger, battery threshold)
4. Gesture timers (yawn, LookAround)
5. Base state face (Sleeping / Charging / LowBattery / Awake)
6. Mood drift

Exception: Sleeping base state is overridden only by touch (priority 2) or wake-up schedule.

## Engine output contract
```
data class BehaviorOutput(
  val mood: Mood,              // face to show now
  val gesture: Gesture?,       // one-shot to start this frame (null if none)
  val lookTarget: Vec2?,       // normalised screen point to look at, null = idle wander
  val brightness: Float?       // null = leave, else 0..1 window brightness
)
```
Engine tests (fake clock, JUnit): wake-up at 07:00, sleep at 23:00, five rapid taps give Angry then Denying, charger plug priority over drift, no spontaneous Angry.

## Defaults I picked (flagged)
- Sleep hours 23:00 to 07:00 and 10 min idle sleep. Quiet-hour screen dimming may be disliked on an always-on display; make it one flag `DIM_WHILE_SLEEPING`.
- Spurious "rage" and "glitch" easter eggs from rapid tapping are my invention; cheap to remove.
- No sound or haptics in V1. Gesture table has a hook `onGesture(gesture)` where later haptics (short tick for tap, rumble for Broken) plug in.
