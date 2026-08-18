# Formicary Ep2 Release Pass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. House execution: the `execute-plan` skill dispatches `implementer` agents; verification stays in the main loop.

**Goal:** Ship the Ep2 feature/refinement pass and leave the mod publishable on CurseForge/Modrinth at 1.0.0 (spec: `docs/superpowers/specs/2026-08-18-ep2-release-pass-design.md`, commit `660251d`).

**Architecture:** Colony field as a density-modulation layer over the existing noise generator; new content (chambers, ender ant, queen phases, items) rides existing registration/datagen patterns; dev tooling (commands + shot-list autopilot) lands early to cheapen all visual verification.

**Tech Stack:** NeoForge 21.0.167 / MC 1.21 / Java 21 / mojmap+Parchment. Datagen for all JSON. GameTests + NoiseProbe for verification.

**Hard rules for every task (from `CLAUDE.md` — read it first, plus the `docs/gotchas/` file the index names for your subsystem):**
- `$env:JAVA_HOME = "C:\Users\Family\.jdks\jdk-21.0.11+10"` before any gradlew. Never `.\gradlew clean`.
- Verify every MC/NeoForge signature against `reference/` before use; a missing class there is a partial extraction, not a missing API.
- All content datagen'd (`.\gradlew runData` after datagen changes; generated JSON goes to `src/generated/resources`).
- `.\gradlew build` green before any commit claims done. GameTests: `.\gradlew runGameTestServer *> $env:TEMP\formicary-gts.log` then Select-String the log (piping live output breaks the exit code).
- Conventional commits, one per task. Never tag, never push.

**Execution order & cut line (from spec):** WP-A and WP-B first (independent), WP-C must be probe-green by **Thu Aug 20 EOD** or defers to 1.1, WP-D is P0 and does NOT wait on WP-C, then WP-E/F/G (P1), WP-H/I (P2), WP-J last. Logan play-test **Fri Aug 21**; 1.0.0 freeze after.

---

## WP-A: Dev tooling (§13)

### Task A1: `/formicary dev` command suite

**Files:**
- Create: `src/main/java/com/nogal/formicary/command/FormicaryDevCommands.java`
- Modify: `src/main/java/com/nogal/formicary/Formicary.java` (register `RegisterCommandsEvent` listener on the NeoForge event bus — verify current event-listener wiring style in the file)

- [ ] **Step 1: Command tree.** Root `formicary` → literal `dev`, `.requires(src -> src.hasPermission(2))`. Subcommands:
  - `locate throne|nursery` — compute the nearest structure center from the player's position using the same cell math the generator uses (`ColonyNoise` throne/nursery cell functions — they are position-pure; expose a small static query helper in `ColonyNoise` if the existing methods are private). Print coordinates + distance in chat.
  - `tp throne|nursery` — locate, then teleport the player to the chamber floor center (use the located Y the cell math returns; verify teleport API against `reference/` — server-side `Entity#teleportTo`).
  - `state` — print: player pos, tier name for current Y (`ColonyGeneratorTunables.tierIndex`), nearest throne/nursery distances. (Colony-field values are added by Task C4.)
  - `kit` — give full Chitin Armor set + 8 Trail Pheromone + 4 ender pearls + 16 Fungal Bloom.
  - `queenfight` — summon a Queen Ant 6 blocks in front of the player.
  - `locate`/`tp` for `garden|larder` are added by Task D4 (blocks exist then).
- [ ] **Step 2: Build + manual smoke** — `.\gradlew build` green; in dev client, each subcommand runs and prints sane values. Non-op in a release environment cannot see the command (permission gate).
- [ ] **Step 3: Commit** `feat(dev): /formicary dev command suite (locate, tp, state, kit, queenfight)`

### Task A2: Shot-list autopilot

**Files:**
- Create: `src/main/java/com/nogal/formicary/client/ShotListAutopilot.java` (client-only; guard registration with `Dist.CLIENT`)
- Create: `docs/dev-tools.md` (how to write a shot list + run it)

- [ ] **Step 1: Behavior contract.** On client world load (`ClientTickEvent` state machine — verify 1.21 event name in `reference/`; the pre/post split gotcha is banked in `docs/gotchas/events-portals.md`), if `run/shotlist.json` exists: parse entries `[{x,y,z,yaw,pitch,waitTicks,label}]`. For each entry: issue `player.connection.sendCommand("tp @s x y z yaw pitch")` (dev world has cheats; spectator recommended in the doc), wait `waitTicks` (default 40) for chunks, then `Screenshot.grab(...)` (verify exact signature in `reference/` — `net.minecraft.client.Screenshot`) writing to `run/screenshots/shotlist/<label>.png`. After the last entry: rename/delete the json (so a relaunch doesn't re-run it), then close the client (verify `Minecraft#stop` vs `close` semantics in `reference/`).
- [ ] **Step 2: Failure behavior.** Malformed json → log an error, do nothing (never crash the client). Missing chunks at deadline → screenshot anyway (a bad frame is diagnostic).
- [ ] **Step 3: Verify** — `.\gradlew build` green; write a 2-entry shot list against the dev world, run `.\gradlew runClient`, confirm both PNGs appear and the client exits itself.
- [ ] **Step 4: Commit** `feat(dev): shot-list autopilot for scripted client screenshots`

---

## WP-B: P0 quick wins — exits visibility + boss bar (§2, §4)

### Task B1: Exposed ceiling ⇒ membrane

**Files:**
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyNoise.java:730-739` (membrane guard chain)
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyGeneratorTunables.java` (retire `MEMBRANE_THRESHOLD` usage; keep the constant documented as superseded or delete + fix references — grep first)
- Modify: `src/main/java/com/nogal/formicary/worldgen/NoiseProbe.java` (membrane section)

- [ ] **Step 1: Drop the noise-threshold guard, keep the cap-band and isAir-beneath guards** (verified separable at ColonyNoise.java:730-739). Every ceiling column with air directly beneath the cap now returns membrane for the `MEMBRANE_THICKNESS` layers.
- [ ] **Step 2: NoiseProbe:** delete the now-dead `MEMBRANE_THRESHOLD_LADDER` sweep (NoiseProbe.java:113-163) and replace the membrane-distance measurement with the new invariant — assert over the sample area: `exposedCeilingColumn ⇒ membrane present` (zero violations) and report the membrane fraction of total ceiling (informational).
- [ ] **Step 3: Run probe** on the three standard seeds (1234567, 42, 987654321): invariant holds on all three. Run full GameTest suite: 53/53 (no test pins membrane scarcity — verify by grep before assuming).
- [ ] **Step 4: Commit** `feat(worldgen): membrane on every exposed ceiling column — visible roof = exit`

### Task B2: Arrival pocket under the cap + guaranteed membrane column

**Files:**
- Modify: `src/main/java/com/nogal/formicary/portal/AnthillPortal.java:141-207` (`findOrCarveEntryPocket` / `carveEntryPocket`)
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyGeneratorTunables.java:293-309` (entry constants)
- Modify: `docs/DECISIONS.md` (amend the M5 "only removes blocks" note — the floor slab exception at :223-229 extends to the membrane column punch)

- [ ] **Step 1: Retune the scan band toward the cap:** `ENTRY_SCAN_BOTTOM` rises so the accepted pocket floor is always within ~12 blocks of `CEILING_BOTTOM` (pocket Y ∈ [174, 184]); `ENTRY_CARVE_PREFERRED_Y = 180`. Keep the constants in Tunables (registry-free) and express the "pocket top is within reach of the cap" rule as a static predicate there so the probe/tests can assert the arithmetic without loading portal code.
- [ ] **Step 2: Membrane column punch:** after the pocket is found/carved, at the pocket's center XZ: replace cap blocks with membrane for `MEMBRANE_THICKNESS` layers at `CEILING_BOTTOM..`, and clear any solid blocks between pocket ceiling and cap bottom (runtime `colony.setBlock`, the path the carve already uses). Floor slab behavior unchanged. Respect shaftState precedence exactly as the existing carve does.
- [ ] **Step 3: Verify live:** scripted `runServer` pass — dev script throws a pearl at a placed Anthill Core (or invokes the portal seam directly the way `PortalGameTests` would if the dimension loaded), then asserts from the server log/command output: pocket Y in band, membrane present at pocket XZ cap. Manual client spot-check via A2 shot list.
- [ ] **Step 4: Commit** `feat(portal): arrival pocket sits under the cap with a guaranteed membrane exit above`

### Task B3: Queen boss bar radius gate

**Files:**
- Modify: `src/main/java/com/nogal/formicary/entity/QueenAntEntity.java` (bar plumbing at :410-420, tick)
- Modify: `src/main/java/com/nogal/formicary/gametest/` — new test in the queen's test class (grep for the existing boss-bar test home)

- [ ] **Step 1:** Add constants `BOSS_BAR_RADIUS = 20.0`, `BOSS_BAR_RADIUS_EXIT = 28.0`. In the queen's server tick (every 10 ticks is fine): for each `ServerPlayer` in the level, add to the `ServerBossEvent` if within 20, remove if beyond 28. Keep `startSeenByPlayer`/`stopSeenByPlayer` overrides calling remove (they now handle untrack/unload/dimension-change/disconnect); death path already removes via vanilla `die`/discard — verify and cover.
- [ ] **Step 2: GameTest** (dimension-independent — queen in an overworld arena): mock player at 30 blocks → not in `getPlayers()` of the bar; move to 15 → present; move to 40 → absent. Use the structure-template + mock-player rules from `docs/gotchas/gametest.md`.
- [ ] **Step 3:** Suite green (53 + 1). **Step 4: Commit** `fix(queen): boss bar visible only within 20 blocks, hysteresis 28`

---

## WP-C: Colony field (§1) — Thursday gate

### Task C1: The field + density modulation

**Files:**
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyGeneratorTunables.java` (new block of constants)
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyNoise.java` (field function + modulation at the chamber/comb/decoration sample sites)
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyChunkGenerator.java` (spawn-cluster density modulation)

- [ ] **Step 1: Constants** (exact, into Tunables with javadoc in the file's style):

```java
// ------------------------------------------------------------------
// Colony field (Ep2) -- the dimension is colonies, not one mega-nest
// ------------------------------------------------------------------
/** One colony per this many blocks on each axis. */
public static final int COLONY_SPACING = 384;
/** Seed-jitter of the colony centre inside its cell, in blocks (total spread).
 *  Bounded so min centre separation = SPACING - JITTER = 288 >> 2*BOSS_BAR_RADIUS_EXIT,
 *  the invariant that makes two simultaneous boss bars impossible. */
public static final double COLONY_JITTER = 96.0;
/** Full nest density inside this radius of the centre. */
public static final double COLONY_CORE_RADIUS = 100.0;
/** Density reaches ~0 here; between colonies only worm tunnels + ramps remain. */
public static final double COLONY_OUTER_RADIUS = 150.0;
/** Chambers (nursery/garden/larder) may generate where the field exceeds this. */
public static final double CHAMBER_ELIGIBILITY_MIN_F = 0.2;
/** Ender ants may runtime-spawn where the field is BELOW this -- the dark wilds. */
public static final double ENDER_SPAWN_MAX_F = 0.35;
```

- [ ] **Step 2: Field function** in `ColonyNoise` (position-pure, same seeded-jitter construction as `shaftForCell`): `colonyCenterForCell(cellX, cellZ)` and `colonyField(x, z)` returning `f = 1 - smoothstep(CORE_RADIUS, OUTER_RADIUS, distToNearestCenter)` (check the 3x3 neighbouring cells like the existing nearest-shaft search).
- [ ] **Step 3: Modulation.** At each existing sample site, blend the threshold toward "never" as f falls: `effectiveThreshold = lerp(9.0, baseThreshold, f)` for chamber small/large and comb patch; decoration and spawn-cluster chances multiply by `f`. Worm tunnels, ramps, membrane, landings untouched.
- [ ] **Step 4:** Build green; probe still runs (numbers will change — that's C3's job to pin). **Commit** `feat(worldgen): colony field — dense cores, sparse wilds between`

### Task C2: Throne re-anchor + forced corridor floor

**Files:**
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyNoise.java` (`throneForCell` at :302-323, corridor construction at :388-389)
- Modify: `src/main/java/com/nogal/formicary/worldgen/ColonyGeneratorTunables.java` (retire `THRONE_SPACING` in favor of the colony cell; keep the name pointing at `COLONY_SPACING` or update references — grep all uses)

- [ ] **Step 1:** Throne cell becomes the colony cell: `throneForCell` keys off `COLONY_SPACING` and hangs the chamber off the ramp nearest the *colony center* (floor-Y arithmetic is ramp-relative and unchanged — verified). Exactly one throne per colony by construction. Update every `THRONE_SPACING` reader: ColonyNoise cell math (:286-337) AND NoiseProbe's per-throne density arithmetic (:423, :644).
- [ ] **Step 2:** Corridor floor forced-solid at height 0 exactly like the nursery corridor (ColonyNoise.java:573-574 pattern), subordinate to shaftState. Update the asymmetry comment at :561-565 — the 17% justification is invalidated by the field (cite spec §1).
- [ ] **Step 3:** Build green. **Commit** `feat(worldgen): one throne per colony; corridor floor forced solid`

### Task C3: NoiseProbe colony section

**Files:**
- Modify: `src/main/java/com/nogal/formicary/worldgen/NoiseProbe.java`

- [ ] **Step 1: New `-PprobeWhat=colony` section asserting, per seed:** (a) exactly one throne per colony cell and none outside cores; (b) monotone density profile — mean chamber-air fraction sampled in rings at r=0..250 from a centre decreases past CORE_RADIUS and is <10% of core value beyond OUTER; (c) min pairwise colony-centre separation ≥ 288 (the invariant); (d) findability — max distance from 64 random points to nearest colony centre ≤ 340 (worst corner of a 384 cell + jitter); (e) core air fraction reported (backs the C2 floor change); (f) chamberWalk reachability re-run for throne + nursery under the field.
- [ ] **Step 2: Run all three standard seeds — every assert green.** This is the **Thursday gate evidence**.
- [ ] **Step 3:** Full GameTest suite still 53+/green. **Commit** `test(worldgen): NoiseProbe colony section — the Ep2 field invariants`

### Task C4: Colony-aware dev commands

**Files:** Modify: `src/main/java/com/nogal/formicary/command/FormicaryDevCommands.java`

- [ ] **Step 1:** Add `locate colony` / `tp colony` (nearest colony center) and extend `state` with `f` at the player's position. **Step 2:** build + smoke. **Commit** `feat(dev): colony locate/tp + field readout`

---

## WP-D: Chambers + provisions (§3 + food items) — P0, independent of WP-C

### Task D1: Food items + Provision Comb block

**Files:**
- Modify: `src/main/java/com/nogal/formicary/item/ModItems.java` (+3 foods), `src/main/java/com/nogal/formicary/block/ModBlocks.java` (+PROVISION_COMB)
- Modify: datagen providers (models, lang, loot, recipes — follow the comb-block precedents in each provider)
- Test: loot GameTest in the items/blocks test class

- [ ] **Step 1: Items** (FoodProperties — verify builder API in `reference/`): `HONEYED_COMB` (nutrition 4, saturation 0.6, fast-ish eat), `FUNGAL_STEW` (bowl food, nutrition 8, sat 0.6, returns bowl, +Night Vision 10s on eat — match the fungus NV duration precedent), `ROYAL_JELLY_TREAT` (nutrition 4, +Absorption I 20s, alwaysEdible).
- [ ] **Step 2: Block** `PROVISION_COMB`: comb-family visuals (D-family texture task in I-pass; placeholder = brood comb texture recolored via `assets-src` script now, not hand-pixeled), hardness like comb, **loot table (datagen): 1-2 ender pearls guaranteed + 1-2 random food items** — this is the §2 affordability floor; no Silk Touch special-case.
- [ ] **Step 3: GameTest:** break a placed Provision Comb with `player.gameMode.destroyBlock`-equivalent used by existing loot tests (NOT `helper.destroyBlock` — banked: it drops nothing); assert ≥1 ender pearl among drops.
- [ ] **Step 4:** `runData`, build, suite green. **Commit** `feat(content): provision comb (guaranteed exit pearls) + colony foods`

### Task D2: Fungus Garden chamber

**Files:**
- Modify: `ColonyGeneratorTunables.java` (GARDEN_* constants — clone the NURSERY_* block: SPACING 96, RADIUS 8.0, tier band = Fungal Gardens, floor-min derived exactly like `NURSERY_FLOOR_MIN_Y` but for tier 2: `MIN_Y + 2*TIER_HEIGHT + LANDING_HEIGHT`)
- Modify: `ColonyNoise.java` (gardenState — clone the nursery construction; when WP-C has landed, both gate on `CHAMBER_ELIGIBILITY_MIN_F`)
- Modify: `ColonyChunkGenerator.java` (decoration pass: dense fungal carpet ~0.5/bloom ~0.15 on garden floor; plant `FUNGAL_SPORE_CROP` patches at ~0.12 of floor blocks **at age ≥ the first age whose `LIGHT_BY_AGE` ≥ 8** — the canSurvive light bar, verified: LIGHT_BY_AGE tops at 8; confirm which age reaches it and plant at that age)

- [ ] **Step 1:** Constants + state fn + decoration, exactly on the nursery pattern (corridor floor forced solid, shell, ramp hang-off).
- [ ] **Step 2:** NoiseProbe: add garden to the chamber overlap + reachability checks (the 96-cell bound re-derivation for radius 8 is in spec §1 — assert centres ≥ 32 apart in probe output).
- [ ] **Step 3:** Probe green 3 seeds; suite green. **Commit** `feat(worldgen): fungus garden chambers — the colony's farm rooms`

### Task D3: Larder chamber

**Files:** same trio as D2 (LARDER_* constants; tier band = Upper Galleries, tier 3; RADIUS 7.0; interior: comb-lined walls — brood comb chance ~0.5 on wall blocks, PROVISION_COMB ~0.18 of wall blocks, min 2 guaranteed per larder — place deterministically from the chamber's own seeded random)

- [ ] **Step 1:** Implement on the same pattern. **Step 2:** probe overlap+reachability includes larder; assert ≥2 provision combs per generated larder in a probe count. **Step 3:** probe + suite green. **Commit** `feat(worldgen): larder chambers stocked with provision comb`

### Task D4: Dev locate/tp for garden|larder

- [ ] Extend `FormicaryDevCommands` (same cell math), build, smoke, **Commit** `feat(dev): locate/tp garden and larder`

---

## WP-E: Ender Ant (§5)

### Task E1: Spawn spike (proof before build)

**Files:** Create: `docs/superpowers/plans/notes-e1-spike.md` (findings); temporary code allowed but must not land in the final commit unless promoted.

- [ ] **Step 1:** Register a placeholder entity (worker clone, MONSTER category) in `royal_depths` + `nurseries` biome JSON monster lists (weight 20, minCount 1, maxCount 2). Scripted `runServer` on the dev world: force-load deep chunks, run 5 in-game minutes, log natural-spawn events (a `FinalizeSpawnEvent`/spawn-event listener counting spawns — verify the 1.21 event name in `reference/`).
- [ ] **Step 2: Prove:** (a) runtime MONSTER spawns fire in the dimension at block-light 0; (b) a `SpawnPlacements` predicate registered for the type IS consulted (log from inside it) and can veto by position; (c) a spawned ant with default despawn rules despawns when the player-distance rule says so (cap does not lock). Record numbers in the notes file.
- [ ] **Step 3:** If any leg fails → STOP, report to main loop (the spec's §5 mechanism must be redesigned; do not improvise). **Commit** (notes + biome JSON only if kept) `chore(spike): ender ant spawn-path proof`

### Task E2: Entity class + AI

**Files:**
- Create: `src/main/java/com/nogal/formicary/entity/EnderAntEntity.java`
- Modify: `src/main/java/com/nogal/formicary/entity/ModEntities.java` (register, MONSTER category, clientTrackingRange like workers)

**Contract:** extends `PathfinderMob` (queen precedent — survives Peaceful; QueenAntEntity.java:45-49). Attributes: 20 max health, 4 attack damage, 0.3 speed. Goals: melee attack, `NearestAttackableTargetGoal<Player>` range ~12 (respect disguise via the `canAttack` seam exactly like soldiers — ColonyAnger.colonyMayAttack). **Teleport:** on `hurt()` (after `super.hurt` returns true) and in a repositioning goal when target is 8+ blocks away for 40+ ticks: random-teleport 8-16 blocks (adapt `EnderMan#teleport`'s ground-seek loop from `reference/` — do NOT invent; portal particles + sound on both ends). XP: `xpReward = 8` — this is not a `TamableAnimal`, plain `xpReward` works (the banked Animal override trap does not apply, but verify by test). Runtime-spawned = default despawn rules; generation-seeded ones get `setPersistenceRequired()` (E4).

- [ ] **Step 1:** Implement to contract; build green. **Step 2: Commit** `feat(entity): ender ant — teleporting deep-tier hostile`

### Task E3: Model, texture, renderer

**Files:** Modify: `assets-src/models.py` (new spec dict: worker body plan, near-black palette #1A1420, purple accents #8A2BE2/#B26EE8, taller legs); Create: model/renderer classes on the worker-renderer pattern; Modify: client registration, lang, datagen.

- [ ] **Step 1:** `python assets-src\models.py` → contact sheet QA (all-angle eyeball) BEFORE in-game. Follow `docs/gotchas/entity-models.md` for every signature. **Step 2:** build + shot-list render check. **Commit** `feat(entity): ender ant model + renderer`

### Task E4: Spawn wiring

**Files:** Modify: `royal_depths.json` + `nurseries.json` (monster list: ender ant, weight 20, 1-2 — via the biome datagen if biomes are datagen'd, else the JSON directly — check which); Create: `SpawnPlacements` registration (colony-field predicate: `colonyField(x,z) < ENDER_SPAWN_MAX_F` **when WP-C has landed; before that, register the predicate returning true** — the field simply reads as low everywhere, per the spec's cut-line note) + block-light ≤ 0 + standard on-ground; Modify: `ColonyChunkGenerator.spawnOriginalMobs` (seed 2-3 persistence-required ender ants per colony in the Royal Depths tier ring `f ∈ [0.3, 0.8]` when field exists, else near-throne band).

- [ ] **Step 1:** Wire per contract; the E1 spike's proven event/paths are the template. **Step 2:** suite green; scripted runServer confirms live spawns in the wilds and none in lit cores. **Commit** `feat(entity): ender ant spawning — deep tiers, dark wilds, seeded colonies`

### Task E5: GameTests

- [ ] Three tests in a new `EnderAntGameTests` class (overworld arena; 6.0F swings per the invulnerableTime rule): (1) hurt → position changed ≥ 8 blocks within 10 ticks (teleport-on-hurt); (2) kill via `hurt(genericKill)`-equivalent used by existing loot tests → ender pearl in drops with a looting-2 weapon variant asserting ≥ pearl count without looting (loot GameTest precedent); (3) player within 12 → becomes target within 40 ticks (mock-player visibility rules from `docs/gotchas/gametest.md`). Suite green. **Commit** `test(entity): ender ant teleport, loot, aggro`

---

## WP-F: Queen (§6)

### Task F1: Model + texture fixes

**Files:** Modify: `assets-src/models.py` (queen spec dict: leg roots moved to thorax contact points; antennae → 3-segment forward-curved sweep, thicker base; head crest region gets mottled chitin plating in the texture painter), regenerate + contact sheets; model class geometry updated to match.

- [ ] **Step 1:** Paint + geometry + contact-sheet QA (legs visibly rooted, antennae menacing, no flat-yellow crest). **Step 2:** build + shot-list in-game check. **Commit** `feat(queen): model fixes — rooted legs, menacing antennae, textured crest`

### Task F2: Acid spit

**Files:** Create: `src/main/java/com/nogal/formicary/entity/AcidSpitProjectile.java` (+ModEntities registration + simple renderer — `LlamaSpit` is the reference pattern; verify in `reference/`); Modify: `QueenAntEntity.java` (spit goal).

**Contract:** active from fight start; fires when target distance ∈ [6, 16] and cooldown elapsed; cooldown 80 ticks stored on the queen (not the goal — survives goal restarts); projectile deals 4 + Poison 60 ticks on player hit. No lingering pool.

- [ ] **Step 1:** implement; **Step 2:** GameTest — queen + mock player at 10 blocks, `setHealth` full, assert an AcidSpit entity exists within 100 ticks (spit fired); suite green. **Commit** `feat(queen): acid spit — range is no longer free`

### Task F3: Burrow slam

**Files:** Modify: `QueenAntEntity.java`.

**Contract:** unlocked by one-way latch at health < 60% (a new bit alongside the existing `firedPhases` bitmask, persisted the same way — QueenAntEntity.java:93,127 pattern); triggers when target 5-14 blocks away and cooldown (240 ticks) elapsed. Sequence: 30-tick burrow (no collision damage taken — `invulnerableTime`-independent flag checked in `hurt`; soil particles at her position), then erupt at the target's current position: teleport there, 4-block-radius AoE — 6 damage + knock-up 0.8 to all non-ant living entities. **Abort:** if at eruption tick the target is dead, disguised (`canAttack` false), or >20 blocks away → emerge at the burrow point, no AoE.
- [ ] **Step 1:** implement; **Step 2:** GameTests — (a) `setHealth(maxHealth*0.5)`, target at 8 blocks → burrow state entered within 60 ticks; (b) target killed mid-burrow → queen emerges at origin, mock bystander at origin takes no damage. Suite green. **Commit** `feat(queen): burrow slam — kiting is no longer free`

### Task F4: Frenzy

**Files:** Modify: `QueenAntEntity.java` (latch at <30%: `AttributeModifier` +30% movement speed + attack-interval reduction in the melee goal; `BURST_SOLDIERS` effectively 5 while frenzied — parameterize `summonWave` count).

- [ ] **Step 1:** implement (existing 75/50/25 bursts untouched); **Step 2:** GameTest — `setHealth(maxHealth*0.25)` → speed attribute modified AND next burst summons 5; suite green. **Commit** `feat(queen): frenzy below 30% — the end is the hardest part`

### Task F5: Arena template

- [ ] If F2-F4 tests need a larger arena than existing templates: add it to `assets-src/structures.py` TEMPLATES (:197) and regenerate — never hand-author NBT. Fold into whichever task first needs it (likely F2), not a separate commit.

---

## WP-G: Tamed workers (§7)

### Task G1: Sneak-click toggle

**Files:** Modify: `src/main/java/com/nogal/formicary/entity/TamedWorkerAntEntity.java:377-404` (`mobInteract` unbound branch).

- [ ] **Step 1:** Unbound branch: search the nearest `ModBlockTags.WORKER_DEPOSITS` block within 16 (a straightforward `BlockPos.betweenClosedStream` scan of the 33³ box is fine at interaction frequency — server side only); found → `bindTo(pos)` + announce `state.harvesting` (actionbar, the existing pattern); none → announce new lang key `state.no_chest` ("No storage nearby") and stay following. Bound branch unchanged. Add the lang entry in the language datagen provider.
- [ ] **Step 2:** GameTest — worker + chest 6 blocks away, sneak-click via the static seam pattern (`bindNearestFollower` precedent at :199-233 — add an equivalent seam for the ant-side bind so the mock-player limitation doesn't bite); assert bound to that chest. Second test: no chest in range → still unbound.
- [ ] **Step 3:** suite green. **Commit** `feat(worker): sneak-click binds to the nearest chest — the ant-side path`

### Task G2: One-crop loop + nearest-mature + replant guarantee

**Files:** Modify: `HarvestCropsGoal.java`, `DepositToChestGoal.java`, `CropScanner.java` (replace cursor with nearest-mature search), `TamedWorkerAntEntity.java` (harvest/replant at :277-293).

- [ ] **Step 1: One-crop:** after a successful harvest, the worker's next act is the deposit trip (deposit goal precondition becomes `!pack.isEmpty()`), then back out. Remove the pack-full/idle-lull triggers **deliberately**: `DEPOSIT_AFTER_IDLE_TICKS` (used only at TamedWorkerAntEntity:99 + DepositToChestGoal:65) goes; **`isPackFull` itself STAYS** — the ferry goal depends on it (CollectDroppedItemsGoal:63,95) — only its deposit-goal/harvest-goal gating changes.
- [ ] **Step 2: Nearest-mature:** scanner returns the nearest mature crop within `PATROL_RADIUS` of the bound chest (full scan is fine at the deposit-trip cadence; keep a per-tick budget if profiling says so — don't pre-optimize).
- [ ] **Step 3: Replant guarantee:** in `harvest`, when `takeSeed` returns EMPTY (the ~9% wheat zero-roll — CropHarvest.java:65-84), replant the age-0 state anyway (the seed conceptually never left the ground). Delete the "left bare" branch.
- [ ] **Step 4: Tests:** retarget `the_crop_scan_is_tick_budgeted_and_incremental` (TamingGameTests.java:339-368) to pin nearest-mature selection instead (name the retarget in the commit body). New tests: (a) two mature crops, one harvest → next act is chest deposit, second crop untouched until after; (b) forced zero-seed context (assert replant happened across N harvests — the existing ~9%-flake mitigation pattern in that file shows how); (c) nearest of two mature crops is chosen. `bound_worker_harvests_replants_and_deposits` must pass unchanged.
- [ ] **Step 5:** suite green (count will shift — every change named). **Commit** `feat(worker): one-crop shuttle loop, nearest-mature targeting, guaranteed replant`

---

## WP-H: Items & blocks (§8, §9)

### Task H1: Chitin tool set

**Files:** Modify: `ModItems.java` (+5), datagen providers (models/lang/recipes), `ModCreativeModeTabs`.

- [ ] **Step 1:** `Tier` (verify the 1.21 `Tier`/`ToolMaterial` shape in `reference/` — it changed across versions): durability 400, speed 7.5 (above iron 6), damage bonus 2.0, enchantability 18, repair = `formicary:chitin`. Sword/pickaxe/axe/shovel/hoe via the vanilla item classes. Recipes: standard vanilla tool shapes — chitin heads, stick handles (no third ingredient; YAGNI).
- [ ] **Step 2:** runData; recipe GameTest or datagen-validation is enough (recipes are data — the banked datagen gotchas apply); build + suite green. **Commit** `feat(items): chitin tool set — fast, mid-durability`

### Task H2: Hardened Chitin brew

- [ ] **Step 1:** Brewing recipe Awkward + `formicary:chitin` → new `hardened_chitin` potion (Resistance I, 3:00), on the exact registration pattern of the disguise brew (grep for its `PotionBrewing` registration; 1.21 uses the builder/event — copy that seam). Creative-tab + lang via the disguise precedent. **Step 2:** GameTest if the disguise brew has one (mirror it); suite green. **Commit** `feat(brewing): hardened chitin potion — resistance from the colony's shell`

### Task H3: Decorative block families

**Files:** `ModBlocks.java` (+10: packed_soil_bricks + stairs/slab/wall; hardened_soil_tiles + stairs/slab; polished_resin + stairs/slab), datagen (states/models/loot/recipes/lang/tags — mineable tags matching their parents), `assets-src` texture scripts for the three base textures.

- [ ] **Step 1:** Blocks + datagen (stairs/slabs/walls have dedicated datagen helpers — follow any vanilla-style provider usage already in the repo; recipes: 4-in-square → 4 bricks/tiles/polished, stairs/slab/wall standard shapes + stonecutter? No stonecutter — crafting only, YAGNI).
- [ ] **Step 2:** Textures via `assets-src` scripts (deterministic, committed). **Step 3:** runData + build + shot-list eyeball of a sample wall of each. **Commit** `feat(blocks): decorative families — soil bricks, soil tiles, polished resin`

### Task H4: Food recipes

- [ ] Crafting recipes: honeyed comb = brood comb + sugar (shapeless); fungal stew = bowl + 2 fungal bloom + 1 fungal spore (shapeless); royal jelly treat = royal jelly + honeyed comb (shapeless). runData; build. **Commit** `feat(recipes): colony foods craftable`

---

## WP-I: Textures & armor (§10)

### Task I1: Soil family detail rework
- [ ] Rework the soil texture generators in `assets-src` (blotch scale down, per-blotch internal gradient, fine speck layer); regenerate; A2 shot-list wall check vs before (keep before/after PNGs in the task evidence). **Commit** `art(blocks): soil textures — finer specks, gradient blotches`

### Task I2: Comb orientation re-diagnosis
- [ ] **Diagnose first:** place brood comb floor+wall in dev client, F3+T iterate: is the shipped CLOCKWISE_90 (ModBlockStateProvider.java:95-110) wrong direction, wrong faces, or is the *texture itself* directional in a way rotation can't reconcile? Fix the actual cause (datagen rotation or texture). Evidence: before/after screenshots. **Commit** `fix(blocks): comb caps finally align with sides — <actual cause>`

### Task I3: Resin Weep retexture
- [ ] `assets-src` rework (drip forms readable at distance; NOT Resin Block); regenerate + shot-list check. **Commit** `art(blocks): resin weep retexture`

### Task I4: Chitin armor redesign
- [ ] Rework armor item icons + the two worn-layer textures in `assets-src` (armor layer texture rules are banked in `docs/gotchas/items-blocks.md`); shot-list check worn on a player (autopilot in third person via `/gamemode` + F5 is fiddly — a posed armor stand in the shot list is the reliable rig). **Commit** `art(armor): chitin armor visual redesign`

---

## WP-J: Release (§11)

### Task J1: Pheromone Horn message
- [ ] `PheromoneHornItem.java:63-68`: on the no-room path, `player.displayClientMessage(Component.translatable("item.formicary.pheromone_horn.no_room"), true)` before the fail return (server side; lang entry via datagen). GameTest: summon into a sealed 1x1 → no ant, and (if the mock player surfaces actionbar — it does not reliably; assert via the seam's return instead per gametest gotchas) no cooldown charged. **Commit** `fix(items): pheromone horn says why it did nothing`

### Task J2: Identity — version, metadata, icon, docs
- [ ] `gradle.properties`: `mod_version=1.0.0` (**only after the Fri Aug 21 play-test pass — this step is literally last**), `mod_description` real, authors=Nogal, GitHub repo+issues URLs in the template's `[[mods]]` fields; icon: export the Formicary logo (source: `D:\MyProjects\_assets\Recordings\Ep001\` logo assets) as 256x256 `logo.png` into `src/main/resources`, uncomment `logoFile`. README: what-is-this, features, install (NeoForge 21.0.167+MC 1.21, drop in mods/), screenshots from the A2 shot lists. CHANGELOG.md: 1.0.0 entry. **Commit** `chore(release): 1.0.0 identity — metadata, icon, README, changelog`

### Task J3: External smoke test
- [ ] `.\gradlew build`; copy `build/libs/formicary-1.0.0.jar` to a **standalone** NeoForge 21.0.167 install under the scratchpad (server: run the NeoForge installer `--install-server`, accept eula, launch headless, assert clean startup + dimension registration in the log, stop). **Client:** install NeoForge into a portable launcher profile and boot with the jar — verify main menu reached + a world with the mod loads (this is the mandatory client half; coordinate with Logan if launcher auth blocks a headless route — his 2-minute manual launch is an acceptable rig). Record both logs as evidence. **Commit** (docs note only) `chore(release): external smoke evidence`

---

## Final gate (before Logan's Fri Aug 21 play-test)

- [ ] Full suite: `.\gradlew runGameTestServer` — all green (new count recorded; every retarget named in its commit).
- [ ] NoiseProbe: all sections, 3 standard seeds, green.
- [ ] `runData` clean; `build` green.
- [ ] Fresh default-terrain world via A2 shot list: colony core, wilds gap, garden, larder, arrival pocket + membrane, queen (bar gating), ender ant sighting, one of each deco family placed.
- [ ] Then: hand Logan the client (rig teardown rule — restore any keybind/options mutations), he play-tests, and only after his pass does J2's version flip + the 1.0.0 freeze happen.

## Self-review (run before saving)

1. **Spec coverage:** §1→C1-C4, §2→B1/B2/D1(pearls), §3→D2/D3, §4→B3, §5→E1-E5, §6→F1-F5, §7→G1/G2, §8→D1/H1/H2/H4, §9→H3, §10→I1-I4, §11→J1-J3, §12→embedded per-task + final gate, §13→A1/A2/C4/D4. No orphan sections.
2. **Placeholders:** none knowingly left; contracts name exact files/symbols/constants; the two deliberate "verify in reference/" points are house law, not gaps.
3. **Consistency:** `COLONY_*` names match C1↔C3↔E4↔D2; `BOSS_BAR_RADIUS(_EXIT)` B3↔C1 invariant; food item names D1↔H4↔D3 loot.
