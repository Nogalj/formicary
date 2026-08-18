# Formicary dev tools

Two tools, both aimed at the same problem: **looking at this mod is expensive.** The
interesting content is buried 192 blocks down a custom dimension behind a thrown ender
pearl, and a throne chamber sits on a 224-block grid. Before these, every visual check cost
a play session.

- `/formicary dev ...` -- jump to, summon and inspect the things worth looking at.
  (`src/main/java/com/nogal/formicary/command/FormicaryDevCommands.java`)
- The **shot-list autopilot** -- a JSON file of camera positions that the dev client flies
  through, screenshots, and then closes itself.
  (`src/main/java/com/nogal/formicary/client/ShotListAutopilot.java`)

Neither ships anything to a player: the command is gated at permission level 2 (so on a
normal server nobody sees it in tab-completion) and the autopilot is `Dist.CLIENT`-only and
does nothing at all unless `run/shotlist.json` exists.

---

## `/formicary dev`

All subcommands need permission level 2. Singleplayer with cheats on gives you level 4, so
in a dev client they just work.

| Subcommand | What it does |
|---|---|
| `locate throne` / `locate nursery` | Prints the nearest chamber of that kind: the block you would stand on, and the horizontal distance to it. |
| `tp throne` / `tp nursery` | Same lookup, then teleports you there -- into the Formicary dimension from wherever you are. |
| `state` | Your position and dimension, the tier band your Y falls in, and the distance to the nearest throne and nursery. |
| `kit` | Full Chitin Armor set, 8 Trail Pheromone, 4 ender pearls, 16 Fungal Bloom. |
| `queenfight` | Summons a Queen Ant 6 blocks in front of you, with her throne home set where she lands (so her leash and arena behaviour actually run). |

`locate` and `tp` for the fungal garden and the larder arrive with those blocks, in a later
task.

### Things worth knowing

- **The lookup works from any dimension.** The colony's shape is a pure function of world
  position, so `locate throne` from the overworld is a preview of where you would come out;
  from inside the colony it is the real answer. `tp` always lands you in the Formicary
  dimension.
- **The coordinates come from the live world's own generator**, not from a re-derivation of
  the seed. `FormicaryDevCommands` pulls `ColonyChunkGenerator#noise` off the loaded
  dimension, and the nearest-chamber search
  (`ColonyNoise#nearestThrone` / `#nearestNursery`) is the same cell arithmetic that carved
  the room. If the numbers are wrong, the terrain is wrong -- they cannot drift apart.
- **The throne's stand position is the top of the dais**, the same block the generator seats
  the queen on. A nursery's is the floor slab plus one.
- `tp` forces the destination chunk through generation before moving you, so you never land
  in an ungenerated column (which reads as air all the way down and looks exactly like a
  broken chamber).

---

## Shot-list autopilot

Drop a `run/shotlist.json`, launch the dev client, and it flies the camera through the list,
screenshots each stop, renames the list so it cannot re-run, and closes the client.

### Format

A JSON array. One object per shot:

```json
[
  {"label": "throne_wide",  "x": 112, "y": 22, "z": 112, "yaw": 90,  "pitch": 10, "waitTicks": 60},
  {"label": "throne_dais",  "x": 112, "y": 22, "z": 100, "yaw": 180, "pitch": 5},
  {"label": "nursery_comb", "x": 48,  "y": 60, "z": 48,  "command": "formicary dev tp nursery"}
]
```

| Field | Required | Default | Notes |
|---|---|---|---|
| `x`, `y`, `z` | yes | -- | Where the camera is put, via `tp @s`. |
| `yaw` | no | `0` | Degrees. Same convention as `/tp`: 0 = south (+Z), 90 = west (-X), 180 = north, -90 = east. |
| `pitch` | no | `0` | Degrees. Negative looks up, positive looks down. |
| `waitTicks` | no | `40` | Ticks between arriving and the shutter, i.e. how long chunks get to load. 20 ticks = 1 second. |
| `label` | no | `shot_<i>` | Becomes the file name. Anything outside `A-Za-z0-9._-` is replaced with `_`, so a label can never write outside the output directory. |
| `command` | no | -- | An extra command sent right after the `tp`, without a leading slash. This is what lets a shot list drive `/formicary dev` (or `gamemode spectator`, or `time set noon`) rather than only move the camera. |

Output lands in `run/screenshots/shotlist/<label>.png`.

### Running it

**Put the client in spectator first.** The simplest way is a first entry with
`"command": "gamemode spectator"`. Spectator means no fall damage on a drop into a shaft, no
suffocation when a `tp` lands you inside solid fabric between shots, no hand or hotbar in
frame, and no mobs reacting to you -- all four of which will otherwise ruin a shot
eventually.

It also, measurably, decides whether you get a picture at all. The same throne chamber, same
position, same `waitTicks`, shot twice while building this tool: 700 KB of lit room in
spectator, 32 KB of near-black with a hotbar across it in creative. The colony has no sky
and almost no light sources -- **in survival or creative you are photographing an unlit
cave.**

Then:

```powershell
$env:JAVA_HOME = "C:\Users\Family\.jdks\jdk-21.0.11+10"
.\gradlew runClient
```

...and open a world. The autopilot arms the moment a player exists in a level, whichever
world that is.

**To skip the menus and load a save directly**, Minecraft's `--quickPlaySingleplayer <save
folder>` does it -- but it cannot be passed through Gradle. `.\gradlew runClient
--args="..."` **replaces** the run's whole argument list rather than adding to it, and the
launch dies immediately with `Could not find main class or main method. Given main class:
--quickPlaySingleplayer`. Use the launch script ModDevGradle can generate instead:

```powershell
.\gradlew classes createClientLaunchScript
# then run build\moddev\runClient.cmd with the extra arguments appended to its java line
```

Copy that `.cmd` somewhere scratch, append `--quickPlaySingleplayer dev` to the end of the
`java.exe` line, and run the copy. (Editing the generated file in place does not work:
`prepareClientRun` notices its output changed and rewrites it.) `dev` is the save-folder
name under `run/saves/`.

### What it does and does not touch

The run's only lasting change to the workspace is renaming `run/shotlist.json` to
`run/shotlist.done.json` -- which is the point: the next launch is a normal client, not a
replay. The rename **is** the teardown, and it happens before the client closes.

What a shot list does inside the *world* is another matter, and it is on you: `gamemode
spectator`, `time set`, and `formicary dev kit` all persist in the save. Point shot lists at
a scratch world (`run/saves/dev`), never at a world whose state matters.

### Failure behaviour

- **No `run/shotlist.json`** -- the autopilot never arms. This is the normal case.
- **Malformed or empty JSON** -- one `ERROR` line naming the file, and the autopilot is off
  for the rest of the session. It never throws into the client tick loop, and it
  deliberately leaves the file in place: an unparseable list is a typo to fix and re-run,
  not a run that happened.
- **Chunks still loading at the deadline** -- the shot is taken anyway. A frame of grey fog
  is diagnostic; a hang is not. If shots come out unloaded, raise `waitTicks`.
- **The rename fails** -- every PNG is already on disk, so the run is fine, but the next
  launch would replay the list. It logs an `ERROR` saying exactly that.

### Reading the log

Everything the autopilot does is logged at INFO under the mod's logger, in
`run/logs/latest.log`:

```
Shot list: 3 entries loaded from ...\run\shotlist.json
Shot list: entry 1/3 'throne_wide' at 112.0 22.0 112.0
Shot list: screenshot.success -> throne_wide.png
Shot list: retired ...\shotlist.json -> ...\shotlist.done.json
Shot list: done, closing the client
```

Command output from a `command` field lands in the same file: `/formicary dev`'s replies are
sent with logging enabled, so with the default `logAdminCommands` gamerule the integrated
server writes them there too.
