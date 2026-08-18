# Task E1 -- ender ant spawn-path spike

**Verdict: PASS.** All three legs proved out against the real, persistent dev world
(`run/world`), driven by a scripted `runServer` session with no GUI/network client
involved. Numbers and log excerpts below are from the final run (server PID 102988,
started 2026-08-18 06:27, ended 06:32 -- `run/logs/latest.log` at commit time; see
"How this was run" for why the server was launched directly rather than via `gradlew`).

## Method

**Placeholder.** Cloned the worker ant's registration pattern (own `DeferredRegister`,
`EntityType.Builder.of(EnderAntSpikeEntity::new, MobCategory.MONSTER).sized(0.9F, 0.6F)`)
as `formicary:ender_ant_spike`, entirely inside a new `com.nogal.formicary.spike` package
plus one marked line in `Formicary`'s constructor -- see "Cleanup" for why that isolation
mattered. `EnderAntSpikeEntity` deliberately does **not** override `removeWhenFarAway` the
way `WorkerAntEntity` does (that override is what makes colony residents never despawn);
leg (c) needs `Mob`'s stock distance-based despawn behaviour, so the spike could not just
reuse `WorkerAntEntity::new`.

**Biome wiring.** `royal_depths.json` and `nurseries.json` are hand-authored (checked: no
datagen provider touches `data/formicary/worldgen/biome/*`, unlike loot tables/recipes/tags
which do go through `DataGenerators`). Added `formicary:ender_ant_spike` to both "monster"
lists (previously empty), weight 20, minCount 1, maxCount 2 -- exactly the task's spec.

**Spawn-event listener.** `net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent`,
verified against `reference/net/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent.java`:
"fired before `Mob#finalizeSpawn` is called ... in vanilla code this event is injected by a
transformer and not via patch, so calls cannot be traced via call hierarchy." Registered on
`NeoForge.EVENT_BUS` (the GAME bus), server side only -- exactly matches the natural-spawn
call site in `NaturalSpawner.spawnCategoryForPosition` (`mob.finalizeSpawn(...)`, itself
`@VisibleForDebug`-adjacent production code, not test-only).

**Placement predicate.** Registered via `RegisterSpawnPlacementsEvent` (MOD bus,
`Operation.REPLACE`), reading `ColonyChunkGenerator#noise(RandomState)` off the live level's
own generator (same pattern `FormicaryDevCommands#colonyNoise` already uses) and vetoing
on `colonyField(x, z) < ColonyGeneratorTunables.ENDER_SPAWN_MAX_F` (0.35) -- the exact rule
the spec names for the real ender ant. Every call logs `[E1 SPIKE] placement check at ...`.

**How this was run.** `.\gradlew runServer` does not forward console stdin to the actual
server JVM when driven non-interactively (confirmed both with and without `--no-daemon`:
the real server process is a *separate* JVM the Gradle daemon/JavaExec task spawns, not a
direct child of the invoking process, so a piped `StandardInput` never reaches it -- two
runs sent `stop` and it was never received, leaving an orphaned server process both times,
cleaned up via `Stop-Process`). Worked around by letting one `gradlew runServer` boot far
enough to capture the *live* java.exe command line via `Get-CimInstance Win32_Process`
(classpath, `@serverRunVmArgs.txt`/`@serverRunProgramArgs.txt` argfiles, `-Dfml.modFolders`,
main class `net.neoforged.devlaunch.Main`), killing that process tree, then relaunching
`java.exe` **directly** with that exact captured command line and a real
`RedirectStandardInput`/`RedirectStandardOutput` pipe -- a true parent-child process with no
Gradle/batch-file layer in between, which forwarded console I/O correctly. Working directory
must be `run/` (matches `--gameDir .` in the captured args); an earlier attempt that
launched from the repo root instead created a second, throwaway world (`world/`,
`config/`, `server.properties`, etc.) directly under the repo -- caught via `git status`
before committing and deleted; the actual `run/world` dev save was never touched by that
mistake.

A fully scripted session (Task A2's `runClient`/GUI shot-list rig was **not** used --
this needed no rendering, just console commands): boot, `formicary dev spike setup`,
periodic `formicary dev spike report` for 5 real minutes, `teardown`, `stop`. `setup`
constructs two fake `ServerPlayer`s via the exact recipe vanilla's own
`GameTestHelper#makeMockServerPlayerInLevel` uses (`Connection(PacketFlow.SERVERBOUND)` +
`EmbeddedChannel` + `PlayerList#placeNewPlayer`), one witness in each probe region, then
teleports them into the Formicary dimension with `ServerPlayer#teleportTo` (a real player
never touches this dimension without one: `DistanceManager`'s `naturalSpawnChunkCounter` --
which sets the whole `SpawnState` cap `NaturalSpawner.spawnForChunk` checks -- is populated
*only* by `addPlayer`/`removePlayer`, verified in `ChunkMap`/`DistanceManager`; force-loading
a chunk bypasses the "a player must be nearby" gate in `ServerChunkCache#tickChunks` but
does nothing for that cap, so with zero players online the cap is always `0`). These
witnesses never have `Connection#tick()` called on them (the only call site in the whole
1.21 source tree is `ServerConnectionListener.tick()`'s loop over connections it itself
accepted at socket-accept time) so there is no keep-alive timeout to fight.

**Dev-world gotcha, banked into the fix.** The first run against `run/world` produced
*zero* spawns and *zero* placement checks for the full 5 minutes -- not a mechanism
failure, `/gamerule doMobSpawning` on the console showed `false`, left that way by an
earlier play-test/GameTest session. `ServerChunkCache#tickChunks`'s outer gate
(`flag1 = level.getGameRules().getBoolean(RULE_DOMOBSPAWNING)`) short-circuits everything
downstream, including this whole spike, when that is off. `setup` now saves the rule's
current value and forces it `true`; `teardown` restores exactly what it found (confirmed in
the final run's log: `restored doMobSpawning=false`) -- the same "leave persistent state
exactly as found" discipline `docs/gotchas/gui-input-rig.md` and the CLAUDE.md hard rule
about tearing down shared-state rigs already bank for the screenshot rig.

Two witnesses (not one) because natural spawning's per-position distance checks
(`isRightDistanceToPlayerAndSpawnPoint`, `isValidSpawnPostitionForType`) need a player
within the type's despawn distance (128 blocks, since `EnderAntSpikeEntity` does not
override `canSpawnFarFromPlayer`) *in 3D*, and the core probe (radius 60 from the colony
centre) and wilds probe (radius 210) are ~150 blocks apart -- too far for one witness to
cover both regions inside that budget. Each witness sits at Y 40 (the Royal Depths /
Nurseries tier boundary) so it stays within 128 blocks of any candidate spawn Y in either
edited biome (Y 0-95).

## Leg (a) -- runtime MONSTER spawns fire at block light 0

**PASS.** Final report at the 5-minute mark: `finalizeSpawns=460`, `blockLight[min=0,max=8]`
-- `min` stayed `0` from the very first report onward. Real log line (server PID 102988,
the final run against `run/world`):

```
[E1 SPIKE] finalizeSpawn at -71, 34, -69 blockLight=0 spawnType=NATURAL
```

`spawnType=NATURAL` on every logged spawn confirms these are real `NaturalSpawner` calls
(`MobSpawnType.NATURAL`), not generation-time seeding or a command-summoned mob. The
non-zero `max` (8) is expected and not a leg failure: the placeholder's `SpawnPlacements`
predicate is unconditional on light (matching the worker/soldier convention -- "the colony
has no sky and only 0.3 ambient light, so any light test vanilla animals use would reject
every position"), so a few spawns near an emissive decoration (Resin Weep, Fungal Bloom)
picked up non-zero block light. The real ender ant (Task E4) is the one that needs to
decide whether to add its own light gate; the spike's job was only to confirm MONSTER
spawns are not otherwise blocked in this dimension, which they are not.

## Leg (b) -- the SpawnPlacements predicate is consulted and vetoes by position

**PASS**, with a clean threshold split. Final report: `placementChecks=1901`,
`allowed=479`, `vetoed=1422`. Cross-tabulating every logged `field=` value against its
`allowed=` outcome across all 1901 checks in the 5-minute run:

- **Vetoed** (`allowed=false`): field range **0.351 -- 1.000** (1104 of them at exactly
  `1.000` -- deep in the forced core region). Zero vetoed checks below 0.351.
- **Allowed** (`allowed=true`): field range **0.000 -- 0.346** (302 at exactly `0.000` --
  deep in the forced wilds region). Zero allowed checks above 0.346.

The split sits exactly either side of `ENDER_SPAWN_MAX_F = 0.35` with no crossover across
1901 samples -- the predicate is both reached (it logs from inside itself, so a silent
short-circuit elsewhere would have shown zero log lines, not 1901) and doing the vetoing
math the spec names, not some other gate. Real log lines from the same run:

```
[E1 SPIKE] placement check at -127, 34, -214 field=1.000 allowed=false
[E1 SPIKE] placement check at -71, 34, -69 field=0.028 allowed=true
```

## Leg (c) -- default despawn rules apply; the MONSTER cap does not lock

**PASS.** Report timeline from the same run (`elapsedTicks` since `setup`,
`currentlyAlive` = live `ender_ant_spike` count in the level, `finalizeSpawns` = cumulative):

| elapsedTicks | ~real time | finalizeSpawns (cumulative) | currentlyAlive |
|---|---|---|---|
| 100 | ~5s | 104 | 104 |
| 700 | ~35s | 113 | 102 |
| 1300 | ~65s | 161 | 89 |
| 6100 | ~300s (5 min) | 460 | 88 |

`currentlyAlive` plateaus in the high-80s/low-100s across the whole window while
`finalizeSpawns` keeps climbing the entire time (104 -> 460, still rising at the last
report before teardown) -- spawning never stalls, and the population is not monotonically
growing toward whatever the cap happens to be. The only way `currentlyAlive` can fall while
`finalizeSpawns` keeps rising is despawns actually happening (`EnderAntSpikeEntity`'s
uninherited, stock `Mob` despawn behaviour), which is the direct contrast with the banked
`MobCategory.CREATURE` rule in `docs/gotchas/worldgen.md`: `CREATURE.isPersistent()` is
`true` so `NaturalSpawner.spawnForChunk` skips it outside the rare `forcedDespawn` window,
and every colony ant overrides `removeWhenFarAway` to `false`, so the CREATURE population is
permanently whatever generation seeded -- the cap fills once and never opens again.
`MONSTER.isPersistent()` is `false` (verified in `MobCategory.java`: `MONSTER("monster", 70,
false, false, 128)`), so `spawnForChunk`'s `(forcedDespawn || !mobcategory.isPersistent())`
gate is unconditionally true for it, and with a `removeWhenFarAway` that was never
overridden, the population actually churns instead of locking.

Teardown confirms the cleanup path too: `currentlyAlive=0` immediately after
`/formicary dev spike teardown`, with `finalizeSpawns` at its final cumulative value (461)
-- every remaining entity was discarded, nothing left resident in the dev world.

## Cleanup

Per the task brief: the placeholder entity registration is **removed** before the final
commit. This session's diff at commit time contains only:

- This notes file.
- `docs/gotchas/worldgen.md` -- unchanged (no new banked rule needed; the existing
  `MobCategory.CREATURE` entry already frames the contrast this spike measured).

**Not kept:** the `com.nogal.formicary.spike` package (entity, registration, listener,
commands) and the one marked registration line in `Formicary`'s constructor -- deleted
entirely, not left disabled, since nothing in it is directly promotable (`EnderAntEntity`
in Task E2 is a real mob with AI, attributes and a model, not this bare placeholder).

**Not kept either:** the `formicary:ender_ant_spike` entries in `royal_depths.json` /
`nurseries.json`. The task brief allows the biome JSON lists to stay "if the next package
will reuse them as-is" -- but they name the placeholder's own registry id, which stops
existing the moment the registration is removed; leaving them would mean an unresolvable
`"type": "formicary:ender_ant_spike"` reference in a biome file. Verified, not assumed:
`MobSpawnSettings.SpawnerData.CODEC`'s `type` field is
`BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type")`
(`reference/net/minecraft/world/level/biome/MobSpawnSettings.java:140`) -- a name that
doesn't resolve in the entity-type registry fails that codec at datapack load. Both
files are reverted to their pre-spike state: `"monster": []`. **What Task E4 should reuse
verbatim**, though, is the exact shape proved out here -- add one entry per biome,
`"type": "formicary:ender_ant"`, `"weight": 20`, `"minCount": 1`, `"maxCount": 2`, in both
`royal_depths.json` and `nurseries.json` -- and the `SpawnPlacements` predicate rule
(`colonyField(x, z) < ENDER_SPAWN_MAX_F`, `Operation.REPLACE`, `SpawnPlacementTypes.ON_GROUND`
/ `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES`) verified against the real event/predicate
pipeline in this spike, not re-derived from scratch.

## Numbers for the record (final dev-world run)

- Colony centre: `-147 -190`. Core probe `-87 -190` (field 1.000). Wilds probe `63 -190`
  (field 0.327 -- inside another colony's falloff ring, still under the 0.35 threshold, so
  correctly allowed; not the flat-zero "true wilds" the constant name suggests everywhere,
  a reminder that `ENDER_SPAWN_MAX_F` is read against the *nearest* colony's field, not
  colony-relative distance alone).
- 50 chunks force-loaded (two 5x5 blocks, one per probe).
- Run window: 300 real seconds (~6100 game ticks) after `setup`. Interpreted "5 in-game
  minutes" as 5 real-world minutes of a live-ticking server, not `/time`-accelerated game
  time -- the natural-spawn cadence this leg measures (`ServerChunkCache#tickChunks`,
  `SpawnState`'s cap, despawn checks) is itself real-tick-driven, so wall-clock minutes is
  the meaningful unit here.
- `placementChecks=1901`, `allowed=479`, `vetoed=1422`, split cleanly at field 0.35.
- `finalizeSpawns=460` cumulative (461 including one after the last report before
  teardown), `currentlyAlive` plateaued 88-104, `blockLight[min=0,max=8]`.
- No exceptions, no crashes, "Can't keep up" once (right after `setup` force-loaded 50
  chunks + two witnesses' own view-distance chunks simultaneously; the server caught up on
  its own within the same tick cycle and never repeated it).
