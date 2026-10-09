# GoofyAddons with A*

This repository now contains the combined Minecraft 26.3 client, the shared
trading foundation, and the calculator/collector tools. The original standalone
trader source and guides live under `integrations/goofyaddons/`; existing save
files retain their formats and paths. See [installation and migration](docs/GOOFYADDONS.md)
and [completed architecture work](trading-core/README.md).

Download the latest combined mod: [astar-client-0.2.26-BETA.jar](dist/astar-client-0.2.26-BETA.jar).

# A*

A Fabric mod for Minecraft 26.3 that walks your player to a block by itself.
Type `.A* x y z` and it finds the path, plans the movement and steers your
player there through the movement keys and the camera, the way a person would
play: sprinting, jumping up blocks, dropping down ledges, taking corners in
smooth curves. On Hypixel SkyBlock it can also etherwarp and cast Instant
Transmission with an Aspect of the Void.

Under it is a custom A\* pathfinder in plain Java for 3D block worlds, and
tools for looking at its searches: an editor that replays a search step by
step, and a route command that draws routes across whole maps such as the
Dwarven Mines.

> **Fair play.** `.A*` plays the game for you. Use it in singleplayer, on
> your own server, or where the server allows it. Automated movement breaks
> the rules of many servers, Hypixel included, and can get an account banned.

## Installing

You need Minecraft 26.3, [Fabric Loader](https://fabricmc.net/use/) 0.16 or
newer, [Fabric API](https://modrinth.com/mod/fabric-api) and Java 25.

Build the mod from this repository (any JDK 17 or newer runs Gradle; it
downloads Java 25 for the build if it isn't installed):

```powershell
.\gradlew.bat -p client build
```

Put `client\build\libs\astar-client-0.2.26-BETA.jar` (with the trader and Mines map inside)
in your `.minecraft\mods` folder, next to Fabric API. On Linux or macOS use
`./gradlew -p client build`. To try it without installing,
`.\gradlew.bat -p client runClient` starts a development game with the mod.

Turn **auto-jump off** (Options, Controls): the game's own jumps get in the
way of the planned ones.

## The A* Client window

Press **G** in game (the key is in Options, Controls, under *A\* pathfinding*)
for the mod's window. Going somewhere stays a command (`.A*`); the window is
for everything around it. Its sidebar has:

- **Search**, a box at the top: type and its page lists every setting whose
  name or description matches, in a card for each page it's on.
- **Pathfinder settings**, a group that folds away:
  - **Drawing**: whether the route is drawn, its look (ribbon, painted floor,
    comet, breadcrumbs, light tube, lines, key nodes only or none) and its colour.
  - **Path**, **Teleport**, **Camera** and **Mouse**: every setting from
    `.A* config`, with a slider or a switch each, what it does under its
    name and a reset once it's changed.
  - **Caches**: the maps saved for this server (use, rename or forget each)
    and the cache settings.
- **Macros**: integrated SkyBlock trading controls, limits, account checks, market selection and regional rests. See [Trading inside A*](docs/GOOFYADDONS.md).
- **Themes**: the window's colours. Each theme goes with one of the route
  colours and draws the route in it too.

## Commands

Type them in chat with a dot, not a slash: `.A*` or `.Astar` (any case), so
`.Astar 120 64 -30` is the same as `.A* 120 64 -30`. They never reach the
server.

The chat box helps like it does with slash commands:

- **Tab** completes. `.` then Tab gives `.A* `; after that a list shows what can come
  next, with a grey hint of the arguments. Where coordinates go, the first suggestion is
  the block you're looking at, so `.A* ` and Tab walks you there.
- **Up and Down** bring back earlier `.A*` commands, kept across restarts. Type `.` first
  and they step through only your `.A*` commands, each once, newest first. (While the
  suggestion list is open, Up and Down move in the list instead, as with slash commands.)

| Command | What it does |
| --- | --- |
| `.A* <x> <y> <z>` | Walks to that block. |
| `.A* warp <x> <y> <z>` | Walks and etherwarps where that's quicker. |
| `.A* it <x> <y> <z>` | Walks and casts Instant Transmission, in single casts and in chains through the air. |
| `.A* aotv <x> <y> <z>` | Both etherwarps and Instant Transmission. |
| `.A* stop` | Stops, and gives you the controls back. |
| `.A* resume` | Plans again from where you are and carries on after a pause. |
| `.A* show [colour\|look]` | Hides or shows the route drawn in the world. With a colour (`red`, `orange`, `yellow`, `green`, `cyan`, `blue`, `purple`, `pink`, `white`) it draws it in that one. With a look it draws it that way: `ribbon` (a band on the ground with arrows), `floor` (the blocks along the way painted, with arrows at steps), `comet` (only the next few blocks, fading), `crumbs` (glowing dots with a pulse), `tube` (a glowing tube at waist height), `lines` (plain lines at eye height), `keys` (only the key nodes and teleport landings) or `none` (nothing drawn). Remembered for next time. |
| `.A* config` | Your settings: route costs, camera and mouse. See [Settings](#settings). |
| `.A* hand left\|right` | Which hand you hold the mouse in, so the camera's small drifts turn the way yours would. |
| `.A* ease [turns] [flicks]` | The [easings.net](https://easings.net/) curves the camera turns with: `turns` for ordinary turns (default `easeInOutSine`), `flicks` for very big turns and turns between mid-air casts (default `easeOutSine`). With none, says which are set. |
| `.A* map [name]` | Says which saved map it thinks you're on; with a name, tells it (`dwarven-mines` comes with the mod). |
| `.A* cache [on\|off\|forget]` | Says what's saved for this place; `off` and `on` stop and restart saving chunks, `forget` deletes this place's saved chunks. |
| `.A* cache maps` | Lists the maps saved for this server and dimension, with their chunks and size. |
| `.A* cache forget <map>` | Deletes what's saved for that map. |
| `.A* cache rename <name>` | Gives the map you're on a name of your own (`place-3` to `crystal-hollows`, say). |

Pressing any movement key or turning the mouse pauses it and hands you the
controls. `/trace` records movement for calibrating the physics; see
[`docs/TRACES.md`](docs/TRACES.md).

## Settings

Everything about how `.A*` plans and moves can be changed: what each kind
of move costs when it picks a route (jumps, drops, walls, turns,
etherwarps, casts) and how the camera and mouse move (how far ahead it looks,
how high it aims, how still the hand rests, the easing curves, overshoot and
more), and how maps are cached (saving chunks, telling maps apart, whole-map
copies and their files). Every setting starts at what `.A*` does now.

They're kept in `config/astar.toml` in the game folder, written the first
time the game starts with the mod, with a comment above each saying what it
does, its default and its range. Change them either way:

- **In the game:** `.A* config set camera.view_height 0.5` (tab completes
  the names and values). Camera and overlay settings apply at once, mouse
  settings from the next `.A*`, and route costs from the next route (the
  map's move graph is rebuilt for them in the background, a few seconds on
  the Mines, and kept, so switching back is instant).
- **In the file:** edit it in any text editor and run `.A* config reload`.
  Anything it can't use is reported in chat; values out of range are clamped.

| Command | What it does |
| --- | --- |
| `.A* config` | The sections, which settings you've changed, and where the file is. |
| `.A* config list <section>` | Every setting in `path`, `teleport`, `camera`, `mouse`, `overlay` or `cache`, with its value. |
| `.A* config get <name>` | One setting: what it does, its default and range. |
| `.A* config set <name> <value>` | Changes it and saves the file. |
| `.A* config reset <name\|section\|all>` | Back to the defaults. |
| `.A* config reload` | Reads the file again after you edit it. |

A name is `section.name` (`path.jump`), or just the name when it's unique
(`jump`). Costs are in blocks walked: a straight step is 1, so with
`path.jump = 6` a route only jumps up a block when going round would be more
than six steps longer.

A few examples:

```toml
[path]
drop = 6          # walk down stairs and slopes even more, drop off ledges less
wall = 0          # hug walls instead of keeping to the middle of the way

[teleport]
etherwarp_min = 20  # allow shorter etherwarps (the map's hops are found again)

[camera]
view_height = 0.5   # look a little lower
drift = 0           # no small hand wander

[mouse]
hand = "left"
ease_turns = "easeInOutCubic"
```

<details>
<summary>Every setting</summary>

**path**: What each move costs when .A* plans a route, in blocks walked: a straight step costs 1, so a move that costs 3 is taken only if it saves three steps. Raise a cost to see that move less, lower it to see it more. The map's move graph is redone with the new costs (a few seconds on a big map), kept per set of costs.

| Setting | Default | Range | What it does |
| --- | --- | --- | --- |
| `walk` | 1 | 0.1 to 10 | One straight step along x or z. |
| `diagonal` | 1.414214 | 0.1 to 20 | One diagonal step (1.414 is its true length). |
| `jump` | 2 | 0.1 to 100 | Jumping up a block. |
| `drop` | 2 | 0.1 to 100 | Stepping off a ledge, plus drop_per_block for each block fallen. High, so routes walk down stairs and slopes rather than dropping. |
| `drop_per_block` | 1 | 0 to 50 | Added to a drop for each block fallen. |
| `swim` | 20 | 0.1 to 1000 | Each block swum. High, so routes keep out of water. |
| `climb` | 20 | 0.1 to 1000 | Each block climbed on ladders and vines. High, as .A* can't climb yet. |
| `wall` | 0.3 | 0 to 10 | Added to a step right beside a wall or a ledge, so routes keep to the middle of the way. 0 hugs walls. |
| `turn` | 0.1 | 0 to 5 | Added per 45 degrees a route turns, so it takes fewer, gentler turns. |

**teleport**: Etherwarp (.A* warp) and Instant Transmission (.A* it), with .A* aotv for both. Costs are in blocks walked, like path's.

| Setting | Default | Range | What it does |
| --- | --- | --- | --- |
| `etherwarp_cost` | 8 | 0.5 to 500 | One etherwarp: stopping, aiming, the click and the server's answer. Lower it to etherwarp more. |
| `etherwarp_min` | 35 | 3 to 57 | The shortest etherwarp, in blocks; shorter hops are walked. Changing it finds the map's hops again. |
| `it_cost` | 5 | 0.5 to 500 | One Instant Transmission cast. Lower it to cast more. |
| `it_min` | 10 | 1 to 12 | The shortest cast, in blocks; shorter ones are walked. Changing it finds the map's casts again. |
| `aim_time` | 1 | 0.25 to 4 | How long turning onto a cast on the ground takes, times the usual: 0.5 is twice as quick, 2 twice as slow. |
| `aim_variation` | 0.1 | 0 to 0.5 | How much each of those turns' times varies, as a share either way. |

**camera**: Where the camera looks while .A* walks.

| Setting | Default | Range | What it does |
| --- | --- | --- | --- |
| `look_ahead` | 2.5 | 0 to 30 | How far ahead along the route the camera looks standing still, in blocks. |
| `look_ahead_per_speed` | 7 | 0 to 50 | And how much further per block a tick of speed (about 4 blocks walking, 7 sprinting with Speed VII). |
| `strafe_look` | 3 | 0 to 30 | How far ahead it looks while strafing round a turn, standing. |
| `strafe_look_per_speed` | 6 | 0 to 50 | And further per block a tick of speed. |
| `ridge_look` | 1.2 | 0 to 20 | How far ahead it looks on a narrow ridge, standing (closer, as a player watches their feet there). |
| `ridge_look_per_speed` | 2 | 0 to 50 | And further per block a tick of speed. |
| `view_height` | 1 | -1 to 3 | How high over the route's floor the camera aims, in blocks. 0 looks at the floor; the overlay's line is drawn at this height. |
| `pitch_min` | -10 | -90 to 90 | The furthest it looks up, in degrees (negative is up). |
| `pitch_max` | 45 | -90 to 90 | The furthest it looks down, in degrees. |
| `pitch_speed` | 90 | 5 to 1000 | The fastest it tilts up or down, in degrees a second. |
| `strafe` | true | true or false | Whether the keys steer the body round turns (W with A or D) while the camera looks further ahead. false turns the camera through every turn, which keeps to the route less closely (1 of 70 Mines stretches went off it). |
| `strafe_lead` | 40 | 0 to 90 | The furthest the camera leads the way the body goes while strafing, in degrees. |
| `hold` | 2 | 0 to 30 | How far, in degrees, where it wants to look may stray before a resting hand moves the mouse. 0 follows every little change. |
| `hold_strafing` | 6 | 0 to 30 | The same while the keys steer (strafing). |
| `drift` | 0.35 | 0 to 10 | How far the camera wanders left and right like a hand does, in degrees. 0 for none. |
| `drift_pitch` | 0.4 | 0 to 10 | The same up and down. |
| `air_turn_accel` | 1500 | 50 to 100000 | How quickly a turn may speed up in the air, in degrees a second squared: lower turns more gently over jumps and drops. |

**mouse**: How the hand on the mouse turns the camera, walking and teleporting.

| Setting | Default | Range | What it does |
| --- | --- | --- | --- |
| `hand` | right | right, left | Which hand moves the mouse: it shapes which big turns overshoot. |
| `ease_turns` | easeInOutSine | any [easings.net](https://easings.net/) curve | The easing curve of ordinary turns (see easings.net). |
| `ease_flicks` | easeOutSine | any [easings.net](https://easings.net/) curve | The curve of flicks: very big turns, and turns between casts in the air. |
| `stiffness` | 25 | 1 to 200 | How tightly small turns close on where the camera should look (a turn settles in about 4 / stiffness seconds). |
| `turn_speed` | 540 | 30 to 5000 | The fastest a steady turn goes, in degrees a second. |
| `turn_accel` | 4000 | 50 to 100000 | How quickly a steady turn speeds up or slows down, in degrees a second squared. |
| `sweep_over` | 30 | 1 to 360 | Turns bigger than this, in degrees, are one quick eased sweep of the hand. |
| `flick_over` | 75 | 1 to 360 | Sweeps bigger than this are flicks (ease_flicks). |
| `sweep_time` | 0.1 | 0 to 2 | A sweep's shortest time, in seconds. |
| `sweep_time_per_doubling` | 0.07 | 0 to 2 | How much longer a sweep takes each time the turn doubles, in seconds (Fitts's law). |
| `undershoot` | 0.95 | 0.5 to 1 | How much of the turn a sweep makes before the steady turn takes over (1 for all of it). |
| `overshoot_out` | 0.4 | 0 to 1 | The share of big outward turns the hand carries past the target and brings back. |
| `overshoot_in` | 0.2 | 0 to 1 | The same for turns across the body. |

**overlay**: The route drawn in the world (.A* show).

| Setting | Default | Range | What it does |
| --- | --- | --- | --- |
| `lead_ahead` | 4 | 0 to 30 | How far ahead of the player the lead box is drawn, in blocks. |

**cache**: The maps .A* keeps so it can plan past the render distance: chunks saved as you explore, one map per place (each Hypixel island is its own), under `.minecraft/astar/places/<server>/<dimension>/<map>/`, and a whole-map copy with its move graph for the map you're on. `.A* cache maps` lists them.

| Setting | Default | Range | What it does |
| --- | --- | --- | --- |
| `save_chunks` | true | true or false | Whether chunks you see are saved to the map you're on (.A* cache on\|off). |
| `auto_detect` | true | true or false | Whether the map you're on is told from its blocks. false waits for .A* map <name> (or another mod) to say which it is, and saves nothing until then. |
| `match` | 0.75 | 0.3 to 1 | How alike a loaded chunk and a saved one must be, as a share of their blocks, to count as the same map. Higher keeps similar islands apart. |
| `matches_needed` | 4 | 1 to 64 | How many chunks must match before settling on a map. |
| `new_after` | 30 | 4 to 1000 | How many chunks may load with no map matching before a new map starts. |
| `bundled_mines` | true | true or false | Whether the Dwarven Mines map that comes with the mod is used, so .A* plans across the Mines on your first visit. |
| `whole_map` | true | true or false | Whether a copy of the whole map you're on is made in the background, with its move graph and heuristic tables, so any trip on it plans in one go (about 200 MB of memory for the Mines). |
| `whole_map_size` | 64 | 4 to 256 | The biggest whole-map copy, in millions of blocks; bigger maps plan in stretches instead. |
| `keep_files` | true | true or false | Whether each map's graph, heuristic tables and teleports are kept in files beside its chunks (nav.bin, warps.bin, casts.bin), so they're read back in well under a second instead of built again. |

</details>

## How it gets there

- **Routes** head in 16 directions and pay a little for every turn, so open
  ground is crossed in a few long straight lines. Sharp corners are rounded
  off with curves that bend in and out evenly, taken at sprint speed. Routes
  keep to the middle of corridors and a step back from drops, and walk down
  stairs and slopes rather than step off ledges when the way round isn't
  much longer.
- **The route you see** is a band lying on the ground from your feet to the
  goal, with arrows gliding along it the way you're going and a marker a few
  blocks ahead to walk towards. The block you stand on at each turn, jump or
  drop is lit, and teleports are drawn as a beam to where you land. Walls
  hide the band, except for a faint line through them nearby so you can see
  where it goes next.
- **The camera** looks down the route a little ahead (further the faster
  you go), eases in and out of big turns and drifts a little, like a hand
  on a mouse. It never crouches. The rotations aren't perfect: see
  [`docs/ROTATIONS.md`](docs/ROTATIONS.md) for their limits and how to swap
  in your own.
- **Adventure mode.** It never breaks or places blocks and never opens
  doors, so routes go around closed doors.
- **Ladders and water** aren't climbed or swum yet. Routes go around them
  where they can; when there's no other way it stops in front of them.
- **When things go wrong** (it's pushed, lagged back, stuck, off the route,
  or a block or door appears ahead), it finds a short way back onto the
  route, or plans the whole way again. It reads the blocks from the game as
  it goes, and stops if you die.
- **Speed effects** are read from the player, so it plans and steers for
  Speed VII as well as for walking pace.
- **Planning** happens off the game thread. While you play it keeps a copy
  of the blocks around you ready, with its move graph and heuristic tables,
  so a `.A*` usually starts within a fraction of a second.

## Teleporting with the Aspect of the Void

`.A* warp`, `.A* it` and `.A* aotv` need an item named **Aspect of the
Void** in your hotbar. They walk short bits to line up, then:
- **etherwarp:** stop, sneak, aim at the top of a block up to 57 blocks away
  and right-click. Only hops of 35 blocks or more are used, and only where
  every nearby aim lands on the same block;
- **Instant Transmission:** right-click without sneaking, up to 12 blocks
  along the view. Single casts are made on the move; chains of casts through
  the air each have their own view, climbing and turning like a player's.

If a cast doesn't land where planned, it plans again from wherever you are,
leaving that hop out. Teleporting trips need the whole map's copy (below);
the first trip on a new map waits for it and for the hops, which are then
kept on disk.

**In singleplayer**, any item named "Aspect of the Void" teleports the same
way (sneak and right-click to etherwarp, right-click to cast), so trips can be
tried with real clicks:

```
/give @s diamond_shovel[custom_name="Aspect of the Void"]
```

## Long trips and saved maps

The goal has to be within your render distance, unless the map is saved:
- **The Dwarven Mines** come with the mod. When the chunks around you match
  them, `.A*` plans across the whole map.
- **Other places** are saved as you explore: every chunk the game loads is
  kept under `.minecraft/astar/places/<server>/<dimension>/` (about 4 KB a
  chunk), and the next visit is recognised by its blocks, so changing server
  names don't matter.
- **Whole maps.** Once it knows the map, it makes a copy of all of it in the
  background with its move graph and heuristic tables, and keeps them in
  `nav.bin` beside the saved chunks (about 35 MB for the Mines; read back in
  well under a second; with changed route costs, in a `nav-*.bin` of their
  own). Trips anywhere on the map then plan in one go.

When the way runs past what's loaded, it walks to the last point of the route
inside your loaded chunks and plans the next stretch from there.

The mod also writes `config/astar.toml` (your [settings](#settings); `.A*
hand` and `.A* ease` are kept there too) and `astar-casts.log` in the game
folder (every Aspect of the Void click, for checking the teleport rules
against the server's).

## For developers

The main build (Java 21) has the pathfinder, the movement simulator and the
tools; the mod is a separate build so the main one never downloads Minecraft.

```bash
./gradlew edit     # interactive editor: edit the world, replay the search step by step
./gradlew route    # find and draw a route across the Dwarven Mines (or any world save)
./gradlew test     # all modules
./gradlew -p client build   # the mod
```

### Route maps and heatmaps

`route` finds a route across a map and draws it as images in
`build\viz\route\`. Run it with no path for the built-in Dwarven Mines, or
give it a world folder or `.zip`. It remembers the last map you gave it.

```powershell
.\gradlew.bat route                                              # the Mines, two far-apart points
.\gradlew.bat route --args="--from 168,202,283 --to -168,207,86"  # your own endpoints, in game coordinates
.\gradlew.bat route --args="C:\path\to\world.zip"                # another map
.\gradlew.bat route --args="--warp --transmit"                    # with etherwarps and Instant Transmission
```

| Image | What it shows |
| --- | --- |
| `overview.png` | The map from above (each column's highest floor, in grey), with the route coloured by height and its jumps and drops marked |
| `heights.png` | The heatmap: the same view with the floors themselves coloured by height, and the route in white |
| `profile.png` | Height against distance along the route |
| `levels.png` | Close-ups of the six levels the route uses most |
| `teleports.png` | With `--warp` or `--transmit`: the height map cropped to the route, walking in white, etherwarps in magenta and Instant Transmissions in orange with a dot per cast, numbered in order |

To look around a map yourself, `.\gradlew.bat edit` opens the editor on it.
Its view menu switches between the map from above in grey, coloured by
height, or one level at a time, and you can drag the start and goal to
re-route. Every option of both is in [`docs/TOOLS.md`](docs/TOOLS.md).

| Module | What it holds | Depends on |
| --- | --- | --- |
| `core` | The A\* search: `AStarSearch`, `MinHeap`, costs, heuristics, `SearchListener` | nothing |
| `pathing` | The block world, movement rules, move graph, landmarks, smoothing, and the teleport planner (`WarpHops`, `TransmitHops`, `Flights`) | `core` |
| `mcworld` | Reads Minecraft 1.18+ world saves and imports islands into a block world | `pathing` |
| `movement` | The player's movement: input scripts, recorded traces, the physics simulator, the execution plan, the executor and its recovery (`Journey`) | `pathing` |
| `viz` | Renderer, Swing editor, console and PNG demos, the `route` tool | `mcworld`, `movement` |
| `client` | The Fabric 26.3 mod: `.A*` and `/trace` | `movement`, `mcworld`, `pathing`, `core` (compiled in) |

Docs:
- [`docs/PATHFINDING.md`](docs/PATHFINDING.md): the library API, movement
  rules and costs, smoothing, and the search core.
- [`docs/TOOLS.md`](docs/TOOLS.md): the editor, map import, and the `route`
  command's options, including the simulator checks used after every
  executor change.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): the layers and how
  planning and execution fit together.
- [`docs/ROTATIONS.md`](docs/ROTATIONS.md): how the camera turns, where it
  falls short, and how to put in your own rotation code.
- [`docs/TRACES.md`](docs/TRACES.md): recording movement in the game to
  calibrate the simulator.
- [`docs/ROADMAP.md`](docs/ROADMAP.md): what's built, what's next, and
  what's been noted for later.

The Dwarven Mines map in `maps/dwarven-mines.zip` is a copy of Hypixel's, used
to test routes; `edit` and `route` open it when no other map has been opened.

## Questions

Message me on Discord: **`.netherite_`**. Bug reports and ideas are also
welcome as [GitHub issues](https://github.com/abdyzam50-rgb/A-/issues).

## License

[PolyForm Noncommercial 1.0.0](LICENSE). You can use it, change it and
share it for free, as long as it's not for money and the `Required Notice`
line stays with it. Selling it, or using it in anything commercial, needs a
separate deal: ask on Discord (`.netherite_`).

The integration branch builds A* with GoofyAddons as one JAR for Minecraft 26.3.
See [Trading inside A*](docs/GOOFYADDONS.md) for installation, preserved data and scope.

The optional [Discord companion](docs/DISCORD.md) posts profit status and contact alerts and accepts owner-only manual player controls. Server transfers restart saved-position verification before trading resumes.
