# Camera rotations

How `.A*` turns the camera, where it falls short, and how to put your own
rotation code in its place.

## They're not the best

The rotations were made to look reasonable and to steer well, not to pass
for a person under close study. What they don't do well:

- **Sharp turns next to jumps and drops.** On a staircase of one-block jumps
  that turns a corner, there's no flat ground to curve on, so the camera
  turns about 90° over about 6 ticks (up to about 25° in one tick). Attempts
  to smooth it (lopsided curves, a run-up, blending the aim in the air) made
  it worse or got the player stuck, so they were dropped.
- **Hand-picked constants.** Turn rates, sweep times and drift sizes were
  tuned by eye and from a few dozen recorded casts, not from a large set of
  real players' mouse data.
- **The drift is noise.** The small wander while walking (`.A* hand`) is
  smoothed random noise, not a model of a real hand or mouse.
- **Fixed sensitivity.** Walking turns are rounded to whole mouse counts at
  the default sensitivity (0.5), not at the player's own setting.
- **Two separate systems.** Walking and teleporting turn the camera with
  different code, so a hand-off between them can look different from either.
- **Anti-cheat.** None of this was tested against a server's rotation checks.
  Even human-looking turns can be flagged.

## Turn shapes (easing curves)

Big turns follow a curve from [easings.net](https://easings.net/): the share
of the turn made a fraction of the way through its time. The curves are in
`pathing/src/main/java/astar/pathing/Easing.java` (`easeInOutSine`,
`easeInOutQuad`, `easeInOutCubic`, `easeInOutQuart`, `easeOutSine`,
`easeOutQuad`, `easeOutCubic`, `easeOutQuart`, `easeOutExpo`, and
`minimumJerk`, the shape every turn had before).

- **Ordinary turns** use an in-out curve, `easeInOutSine` by default: they
  start and end slowly, like a calm hand.
- **Flicks** use an out curve, `easeOutSine` by default: they start at speed
  and slow into the target. A flick is a walking turn over 75 degrees, or a
  turn between the casts of an air chain.
- `.A* ease <turns> [flicks]` picks them in the game (kept in
  `config/astar-mouse-ease.txt`), and `--ease turns,flicks` does the same for
  the `route --exec` checks.
- A turn begun while the camera is still moving carries that motion on and
  lets it die away smoothly, so the curve never jerks the camera from a turn
  already under way. A target that drifts a little stretches the turn's
  reach; one that jumps more than 10 degrees starts a new turn.
- Turn times are unchanged (Fitts's law), so the planner's timings still hold.
- The sharper out curves (`easeOutCubic` and up) start so fast that the
  walker got stuck more often at the start of a stretch in the checks below
  (12 failed of 300 disturbed routes against 5), so `easeOutSine` is the
  default flick.

## Where rotations come from

There are two paths, one for walking and one for teleports.

### Walking: `Executor` and `AimController`

`movement/src/main/java/astar/movement/exec/`

Each game tick, `Executor.tick`:

1. asks the `Follower` for the yaw to head in and the keys to hold;
2. works out the camera's **target yaw**. That is the heading, turned toward a
   point further down the route while strafing, plus the drift;
3. turns the camera toward it over the tick's frames with
   `AimController.frame(yaw, target, targetRate, dt)`, three frames a tick;
4. works out the pitch in `lookPitch`: a spring toward head height over a
   point on the route ahead, so on level ground it looks where it's going,
   `REST_PITCH` (6 degrees) below level, and tips down a drop or up a climb;
5. calls `PlayerController.tick(keys, yaw, pitch)`.

Small changes of target are the steady turn (a critically damped spring);
only turns over 30 degrees are swept along an easing curve.

In the game, `PlayerController` is `Navigator.GameController`. Its `tick`
sets the player's yaw and pitch, and the game draws the frames in between
from where the camera was.

**The yaw is the steering wheel.** The player moves along the yaw plus the
direction of the held keys, and the follower predicts next tick's movement
from the yaw it gets back. So a rotation that lags far behind the target
makes the player run wide on corners, and in the end get stuck.

### Teleports: `HandAim` and `HandTurn`

- `client/src/main/java/astar/client/HandAim.java` turns the view onto a
  cast a tick at a time. `WarpCast` and `TransmitCast` call
  `step(nowYaw, nowPitch, wantYaw, wantPitch, fast)` each tick and click once
  `left()` (degrees still to go) is small enough and the crosshair check
  passes.
- `pathing/src/main/java/astar/pathing/HandTurn.java` says how long a turn
  takes (`turnTicks`), and `chainWait` gives the ticks between the clicks of
  an air chain.
- **The planner uses these times.** `TransmitHops` and `Flights` use them to
  price casts, and to work out how far the player falls while turning
  between casts. So the timings and the turning code have to agree.

`HandAim` is a port of the `HumanRotation` in the older pathfinder: a turn
timed by Fitts's law, shaped by the `.A* ease` curves (ordinary turns on the
ground, the flick curve mid-air).

## Putting in your own

Pick the seam that fits your code. Each is one class, so a port doesn't touch
the planner or the follower.

### 1. Your walking turns: replace `AimController.frame`

This is the simplest. Keep the method's contract:
- **In:** the camera's yaw now; the target yaw (any angle, go the short way
  round); how fast the target is turning (degrees per second); and the
  frame's length in seconds.
- **Out:** the yaw after this frame.
- It's called `framesPerTick` times a tick (3, set in `Executor.Settings`).
  If your code works once per tick, set that to 1.
- `carry(from)` hands the turn in progress to the new controller when the
  route is planned again partway (`Executor.carryCamera`). Keep it, or every
  recovery will jerk the camera.
- The executor also calls `hand(random)` and `handed(hand)` (unevenness and
  which hand), `mayPause(may)` (a big turn may start a moment late) and
  `tilt()` (how far a sweep tips the pitch). Those can do nothing (`tilt()`
  returning 0) if your turns don't need them.
- `AimController.Settings` has the knobs if you only want to change the feel:
  `omega` (how stiff the turn is), `maxRate` (degrees per second),
  `degreesPerCount` (mouse count size), `flick`, `flickBase`,
  `flickPerBit` and `undershoot` (big-turn sweeps), and `ease`, `snap` and
  `snapEase` (the sweeps' curves, see above).

The drift and the look-ahead while strafing are added before `frame` is
called, in `Executor.tick`. To turn them off, set `lookAhead` to false in
`Executor.Settings`, or change `DRIFT_YAW` and `DRIFT_PITCH`. Pitch is
`Executor.lookPitch`.

### 2. Your teleport turns: replace `HandAim.step`

Keep the contract: return the view one tick on from `(nowYaw, nowPitch)`
toward `(wantYaw, wantPitch)`. `fast` means mid-air, where the fall doesn't
wait. Keep `left()` as the degrees still off after the step.

If your turns are quicker or slower than these, change
`HandTurn.turnTicks` to match. Otherwise the planner times air chains for the
old speed, and casts land short or get re-aimed.

### 3. Your whole camera: hook `Navigator.GameController.tick`

If your rotation code has to own the camera (it moves the mouse itself, say),
treat the `yaw` and `pitch` passed to `tick` as your target and let your code
move the camera. The executor reads the real camera back every tick
(`observe()`), so it copes with a camera that differs from what it asked
for. It steers worse the further behind yours is, so check it as below.

## Checking a change

Run these before and after, on the built-in Mines map:

```powershell
.\gradlew.bat route --args="--exec --pairs 200"
.\gradlew.bat route --args="--exec --pairs 300 --disturb"
.\gradlew.bat route --args="--exec --pairs 300 --disturb --speed-effect 7"
```

- **Stuck stretches** (first run) should stay at 0. The same output prints
  how much the camera turned per 100 blocks.
- **Arrived / failed** with pushes and lag-backs (second run) shouldn't get
  worse, and neither should the same at Speed VII (third run).

These only cover walking turns (seams 1 and 3). Teleport turns only run in
the game: try `.A* it` and `.A* aotv` trips in singleplayer with the
practice Aspect of the Void (see the [README](../README.md)), with
`"-Pastar.trace.casts=true"` on `runClient` to print each cast's phases.
