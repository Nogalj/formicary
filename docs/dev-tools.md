# Formicary dev tools

Two tools, both aimed at the same problem: **looking at this mod is expensive.** The
interesting content is buried 192 blocks down a custom dimension behind a thrown ender
pearl, and since the Ep2 colony field it is not even spread evenly: colonies sit on a
384-block grid with sparse wilds between them, and every chamber (and the one throne) lives
inside one. Before these, every visual check cost a play session.

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
| `locate colony` | Prints the nearest **colony centre** -- the middle of the density field, not a room. The first question worth asking since Ep2: everything else is inside one. |
| `locate throne` / `locate nursery` / `locate garden` / `locate larder` | Prints the nearest chamber of that kind: the block you would stand on, and the horizontal distance to it. |
| `tp colony` | Same lookup, then teleports you to the nearest standable spot in that column (a colony centre is an XZ, not a floor, so this one reads the real blocks). |
| `tp throne` / `tp nursery` / `tp garden` / `tp larder` | Same lookup, then teleports you there -- into the Formicary dimension from wherever you are. |
| `state` | Your position and dimension, the tier band your Y falls in, **the colony field `f` where you stand** with the distance to the centre it came from and which zone that puts you in, and the distance to the nearest throne and nursery. |
| `kit` | Full Chitin Armor set, 8 Trail Pheromone, 4 ender pearls, 16 Fungal Bloom. |
| `queenfight` | Summons a Queen Ant 6 blocks in front of you, with her throne home set where she lands (so her leash and arena behaviour actually run). |

### Things worth knowing

- **The lookup works from any dimension.** The colony's shape is a pure function of world
  position, so `locate throne` from the overworld is a preview of where you would come out;
  from inside the colony it is the real answer. `tp` always lands you in the Formicary
  dimension.
- **The coordinates come from the live world's own generator**, not from a re-derivation of
  the seed. `FormicaryDevCommands` pulls `ColonyChunkGenerator#noise` off the loaded
  dimension, and the nearest-chamber search
  (`ColonyNoise#nearestThrone` / `#nearestNursery` / ...) is the same cell arithmetic that
  carved the room. If the numbers are wrong, the terrain is wrong -- they cannot drift apart.
- **`state`'s `f` is the only way to see the colony field.** It is what decides whether the
  ground around you carries chambers, comb and ants at all, and it is completely invisible
  in game: "sparse wilds, working as designed" and "the carve broke" look identical from
  inside. The line names the zone as well as the number -- core, ring (chambers still
  generate), outer falloff, or wilds.
- **`locate nursery` / `garden` / `larder` skip chambers the colony field gated out.** Since
  Ep2 most 96-block cells produce no room, so an unfiltered "nearest" would name a set of
  coordinates that is solid soil. The search widens to 6 cell rings to guarantee it can
  always reach an eligible one.
- **The throne's stand position is the top of the dais**, the same block the generator seats
  the queen on. A nursery's, a garden's and a larder's is the floor slab plus one -- all
  three floors are forced solid by the same pure function that carved the room, so no block
  read is needed. `tp colony` is the exception and says so above.
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

(For a human launch, `play.cmd` at the repo root is the same thing double-clickable --
it sets `JAVA_HOME` itself, builds current code, and opens the client. Keep its console
window open while playing; closing it closes Minecraft.)

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

### The two-pass photography recipe (used for every Ep2 verification shoot)

Worldgen changes only exist in fresh chunks, so a shoot starts by forcing regeneration:
delete the scratch save's `dimensions/formicary` folder (overworld + player data survive;
the whole ant dimension regenerates under the current generator on next load). If no
scratch save exists, clone a real one — never shoot into a save Logan plays.

Then two autopilot passes, because framing needs coordinates that only exist after
generation:
1. **Scout pass** — entries all at one XZ, each running a `formicary dev locate ...`
   command (`seed`, `locate colony/throne/nursery/garden/larder` from a few offset
   points to find chambers at different tiers). The screenshots are throwaway; the
   PAYLOAD is `run/logs/latest.log`, where every locate reply lands.
2. **Framed pass** — cameras placed from the scout's coordinates, INSIDE each chamber
   (offset from the centre by less than the room radius; a camera outside the shell is
   a frame of solid soil), yaw/pitch aimed at the centre, `waitTicks` 100+ for chunk
   load and seeded mobs.

Both passes: spectator first entry, `--quickPlaySingleplayer <save>` via the patched
launch-script copy (recipe above). (`verified: 2026-08-20`, third shoot of the pattern)

### Photographing WORN armor -- and proving client registration actually fires

A custom armor model (`IClientItemExtensions#getHumanoidArmorModel`) has a failure mode no
headless test and no offline render can reach: the geometry can bake perfectly while the
*registration* path -- `RegisterClientExtensionsEvent`, `EntityRenderersEvent.AddLayers`,
the layer bake -- is mis-wired, and the player sees plain vanilla armor or a crash. An
armor stand in a real client exercises the whole chain, and doubles as the picture:

```
{"command": "gamerule sendCommandFeedback false"}          <- FIRST, or the chat overlay
{"command": "gamemode spectator"}                          <-   covers the subject entirely
{"command": "summon minecraft:armor_stand 3 99.0 0 {NoGravity:1b,ShowArms:1b,Rotation:[-90f,0f]}"}
{"command": "item replace entity @e[type=armor_stand,limit=1,sort=nearest] armor.head with formicary:chitin_helmet"}
   ... one per slot (armor.chest / armor.legs / armor.feet) ...
{"x": 0, "y": 99, "z": 0, "yaw": -90, "pitch": 12}         <- camera level with the stand
```

`NoGravity` is what keeps the stand at the summoned Y instead of falling out of frame, and
every camera aims at the stand, so the subject lands at the crosshair -- i.e. dead centre --
which makes cropping the shots afterwards a fixed box rather than a colour search. (A colour
search was tried first and matched the dirt hillside, whose browns are the chitin browns.)

**Scratch worlds: never clone one of Logan's saves.** A clone inherits his player, and on
2026-08-21 that player was *dead* -- every frame of the shoot was the respawn GUI, twice,
before the cause was obvious. Build the scratch world from the dedicated server's own world
instead, which has no player state to inherit:

```powershell
Copy-Item -Recurse run\world run\saves\devfresh
Remove-Item -Recurse -Force run\saves\devfresh\playerdata   # fresh, alive player at spawn
```

Then `--quickPlaySingleplayer devfresh`. Quick-play silently drops to the main menu if the
named save is absent, which reads as "the autopilot never armed" -- check
`run\saves\` before blaming the shot list. (`verified: 2026-08-21`)

## Scripting the dedicated server (headless probes)

- **`gradlew runServer` does not forward piped stdin to the game JVM** -- the server is a
  separate process the Gradle daemon spawns, not a child of your shell, so "echo stop |
  gradlew runServer" leaves an orphaned java.exe. (Cost the Ep2 spawn spike two orphaned
  servers before the workaround was found.)
- **The recipe that needs no stdin at all:** give the probe a temporary
  `ServerTickEvent.Post` listener that does its measurements and then calls
  `server.halt(false)` when finished. Plain `gradlew runServer` then starts, measures,
  and exits on its own -- no console, no process surgery. Delete the listener class before
  committing. (`verified: 2026-08-18`, ender-ant E4 probe)
- **Scripting the probe across seeds in PowerShell:** quote the whole property argument --
  `$seedArg = "-PprobeSeed=$s"; .\gradlew ... $seedArg` -- or PS 5.1 passes the literal
  string `$s` and the probe dies with `NumberFormatException`. (`verified: 2026-08-18`)
