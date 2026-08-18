# Formicary Ep2 — Release Pass Design

**Date:** 2026-08-18 · **Status:** draft, pending Logan's review (adversarial panel review applied)
**Goal:** the Episode 2 feature/refinement pass, ending with the mod publishable on
CurseForge/Modrinth. Ep2 video slot: Sun Aug 23. The upload itself is Logan's.

Scope was brainstormed 2026-08-17/18 from Logan's 8-item Ep2 list plus play-test
observations. Approach for the one architectural fork (colony sectioning) was chosen
from three options: **colony field over the existing generator**; rejected were an
authored-structure rewrite (too big for the week) and a boss-bar-only fix (keeps the
uniform mega-nest feel). A three-lens adversarial review (claims verifier, reasoning
critic, refuter) ran against source on 2026-08-18; its findings are folded in below.

---

## Priority order and cut line

The video ships Sunday regardless; the public release does not have to — **the
CurseForge/Modrinth build may trail the video by a day or two.**

- **P0 — release blockers (ship no matter what):** §2 exits (visibility +
  affordability floor), §3 chambers (the larders carry §2's pearl floor), §4
  boss-bar gate, §7 (sneak-click toggle + harvesting rework), §11 release
  mechanics.
- **P1 — episode headliners:** §1 colony field, §5 ender ant, §6 queen. **§13 dev
  tooling lands early in P1** — every visual verification after it gets cheaper
  (the colony-locate command rides along with §1's noise code; the shot-list
  autopilot is independent and can land first).
- **P2 — polish:** §8 items, §9 building blocks, §10 textures.
- **§3 does NOT depend on §1:** the new chambers use the nursery's existing
  construction on their own spacing grids, which works on today's uniform layout;
  §1 merely *adds* the `f > ~0.2` eligibility gate when it lands. This is what
  keeps §2's pearl floor (and the Peaceful exit economy) in P0 even if the colony
  field slips.
- **Gate:** the colony field (§1) must be probe-green by **Thu Aug 20 EOD** or it
  defers to a post-video 1.1 (§4/§5 degrade gracefully — the bar gate alone kills
  the two-bars symptom at today's 224 spacing, and the ender ant's spawn predicate
  simply sees `f`-low everywhere until the field lands).
- **Human pass:** Logan play-tests a fresh world **no later than Fri Aug 21** (the
  day before the Saturday shoot); 1.0.0 is frozen only after that pass. (Banked
  lesson: live verification can be fully green and the first human contact still
  broken.)

## 1. Colony field (worldgen)

The dimension stops being one continuous mega-nest. A new top-level layer:

- **Colony centers** on a jittered grid, `COLONY_SPACING ≈ 384` blocks. Density
  falloff `f(dist to nearest center)`: `1.0` inside the core
  (`COLONY_CORE_RADIUS ≈ 100`), smoothstep to `≈ 0` by `COLONY_OUTER_RADIUS ≈ 150`.
- `f` modulates: chamber small/large thresholds, comb-patch threshold, floor/wall
  decoration chances, generation-time ant-cluster density.
- **Chamber eligibility (nursery / fungus garden / larder) extends through the
  falloff ring** (`f > ~0.2`), not core-only — nurseries are the only larva source,
  and arrival XZ is uncorrelated with colonies (portals are 1:1), so pushing all
  content into cores (~21% of area) would make half of first entries land in empty
  tunnels with nothing findable. The near-arrival experience is a named tunable for
  Logan's Friday play-test.
- **Unmodulated (global):** worm tunnels, helicoid ramps (the no-softlock
  connectivity guarantee — untouched), ceiling membrane, arrival pockets.
- **One queen per colony, by construction:** the throne's own 224 grid is removed;
  the throne anchors to the colony center (nearest-ramp hang-off construction
  unchanged — its floor-Y arithmetic is ramp-relative and ramps don't move).
- **Throne corridor floor becomes forced-solid** (nursery precedent, same
  construction, subordinate to shaftState precedence). The current unforced floor
  was justified by a measured 17% air fraction in the Royal Depths
  (DECISIONS.md:823-826); the colony field deliberately raises carve density in
  cores — where every throne now lives — so that measurement no longer holds.
- **Fungus-garden overlap bound, re-derived:** the nursery 96-cell math gives
  neighbouring chamber centers ≥ 32 blocks apart; radius-8 gardens need ~22 for
  shells not to touch. Holds with margin; the probe's overlap check extends to the
  new chamber types anyway.

**NoiseProbe colony section asserts:** exactly one throne per colony; density
profile matches the falloff; sparse-zone air/chamber fraction well below core
values; core air fraction (backs the forced-corridor-floor change); chamber
reachability re-run for ALL chamber types under the new field; max distance from an
arbitrary point to the nearest colony core (the findability stat — the 224 grid was
chosen from a measured "nearest queen 130-176 blocks"; 384 must stay in "committed
exploration finds it" range); and **min colony separation (spacing − 2×jitter) >
2× the §4 bar hysteresis radius** — the invariant that makes two simultaneous boss
bars impossible.

**Compat:** worldgen changes appear only in newly generated chunks. Fresh worlds for
footage and release.

## 2. Exits — visible AND affordable

Play-test problem: pearl into the nest, no exit anywhere above you.

**Mechanics fact (from code, not open question):** passing out through membrane
**consumes a thrown ender pearl** (`PortalEvents.java` → `AnthillPortal.exitColony`).
So exits have two failure modes — can't *find* one, can't *afford* one — and §2/§5
are a coupled system: the ender ant is what makes exit pearls renewable.

- **Visibility: exposed ceiling ⇒ membrane.** Every ceiling column with air directly
  beneath the cap carries membrane (noise threshold dropped, visibility mask stays —
  the machinery supports exactly this). Rule for the player: *if you can see the
  roof, that spot is an exit.* Note: membrane emits light 15, so this turns exposed
  stretches of the Upper Galleries into a lit band — flag for the client feel pass,
  and it suppresses ender-ant spawns near the ceiling (block-light-0 gate), which is
  fine.
- **Arrival exit guarantee.** The entry carve (runtime portal code, not worldgen)
  changes to guarantee a near-cap pocket at the portal's XZ: scan band narrowed
  toward the cap, membrane column punched through the cap by the same runtime
  `setBlock` path the carve already uses, and a forced floor slab where no natural
  floor exists — the floor slab is already M5's one sanctioned block-placing
  operation; this extends it, still subordinate to shaftState precedence. The M5
  "only removes blocks" note in DECISIONS.md gets amended alongside.
- **Affordability floor: every larder's Provision Comb loot guarantees 1-2 ender
  pearls** per larder. This is the no-pearl fallback (you can always *find* a pearl,
  even having arrived on your last one) and the Peaceful-difficulty answer (no
  hostile spawns on Peaceful → no ender ant farming → larder pearls carry the exit
  economy).

## 3. New chambers

Both use the nursery's reachable-by-construction build (ramp hang-off, corridor at
walkable Y, probe-verified) on their own spacing grids — **no §1 dependency**; when
the colony field lands, the `f > ~0.2` eligibility gate (§1) is added on top.

- **Fungus Garden** — Fungal Gardens tier (y 96–143), one per 96 cell, radius ≈ 8.
  Floor carpeted in fungal growth with wild **harvestable fungal spore crops**
  planted at generation. Implementation must verify planted crops survive: crop
  `canSurvive` needs light ≥ 8 and the dimension has no skylight (the banked
  skyAccess trap) — either the garden's bloom/carpet lighting covers it or crops
  plant at an age whose own light clears the bar. Accepted in writing: these crops
  are worker-harvestable, so a player who binds a worker inside a colony core can
  strip a garden — that's their call, not a bug.
- **Larder (food storage)** — Upper Galleries tier (y 144–191), one per 96 cell.
  Comb-lined, stocked with **Provision Comb** (new block) whose loot drops food
  items (§8) **plus the guaranteed exit pearls (§2)**.

## 4. Queen boss bar

Current behavior (verified): the bar is plumbed through tracked-player events and
her client tracking range is 16 chunks — so the bar shows from ~256 blocks away.
That, not a "linger bug" (no repro recorded), is what you experienced.

Fix: **explicit radius gate** — the bar is shown only within **~20 blocks** of the
queen (inside/at the throne room; the room's interior radius is 14), removed with
hysteresis at ~28 and on death, unload, dimension change, or player disconnect.
Paired with §1's separation invariant, two simultaneous queen bars become
impossible; even without §1 (cut-line case), the 20-block gate alone kills the
symptom at today's 224 spacing.

## 5. Ender Ant (new hostile caste)

- **Mechanism (not expressible as biome data):** biomes here are Y-bands; "between
  colonies" is XZ noise. The ant registers in the **two deep-tier biomes'** MONSTER
  lists only (Royal Depths + Nurseries — preserving the chosen deep-tier flavor;
  both lists currently empty) **plus a custom `SpawnPlacements` predicate sampling
  the colony falloff `f`** (ColonyNoise is position-pure, so the sample is cheap) —
  spawns where `f` is low and block light is 0. Upper tiers stay ender-free. The dimension's `monster_spawn_light_level
  = 0` plus emissive decoration concentrating in cores means spawns naturally skew
  to the dark inter-colony wilds — which is the intent.
- **Despawn policy (deliberate, two-tier):** runtime-spawned ender ants **despawn
  normally** — a deliberate break from the "residents never despawn" convention
  (documented in DECISIONS.md when implemented), because persistence-required
  hostiles would accumulate to the ~70 MONSTER cap and permanently stop respawning
  (the same class of dead-end as the banked CREATURE rule). The **few seeded per
  colony at generation are persistence-required** so they don't evaporate before
  anyone meets one.
- **Base class:** `PathfinderMob`, not `Monster` — the queen's own banked precedent
  (Monster brings `shouldDespawnInPeaceful() == true`); seeded ender ants survive
  Peaceful. Runtime spawning is category-driven and skips MONSTER on Peaceful
  regardless — the larder pearls (§2) carry the Peaceful exit economy.
- **Behavior:** aggressive on sight at short range; teleports 8–16 blocks to close
  distance or when hurt; melee bite. ~20 HP / 4 dmg (normal).
- **Drops:** 0–1 ender pearl (+Looting), XP above a worker's.
- **Look:** ant body plan, near-black chitin + purple accents, portal particles on
  teleport. Routed through the `assets-src/models.py` pipeline + contact-sheet QA
  like every other mob (banked entity-models rule).
- **Technical spike (early, planning gate):** prove the full path, not just
  category viability — MONSTER-category runtime spawn fires in this dimension, the
  colony-field predicate actually restricts placement, and the despawn policy
  behaves (runtime ants cycle, seeded ants persist, cap doesn't lock).
- **GameTests:** teleport-on-hurt, pearl loot, aggro acquisition.

## 6. Queen — model and fight

**Model fixes (from play-test):** legs re-rooted so they visibly attach to the
thorax; antennae rebuilt — longer, segmented, curved forward with a menacing sweep;
the flat-yellow patch on the head crest gets real texture (mottled chitin plating).
Contact-sheet renders for QA before in-game.

**Fight phases — unified with the existing machinery.** The queen already fires
one-shot summon bursts at 75/50/25% HP via a persisted one-way bitmask
(`firedPhases`) — that pattern exists precisely because polled health comparisons
re-fire every tick. The new phases join it, not fight it:

| Phase | HP band | Behavior (all thresholds one-way latches on the same bitmask pattern) |
|---|---|---|
| 1 | from start | current melee + **acid spit**: lobbed projectile at targets 6–16 blocks out, brief poison; **cooldown ~4s** |
| 2 | latched < 60% | adds **burrow slam**: digs into the dais (brief invulnerability + soil particles), erupts under the target, AoE knock-up; **cooldown ~12s**; **abort rule:** if the target is invalid at eruption (dead, disguised — `canAttack` refuses disguised players — or gone), she emerges harmlessly at the burrow point |
| 3 | latched < 30% | **frenzy**: +30% move/attack speed, summon bursts amped **3 → 5 soldiers** |

Existing 75/50/25 bursts stay unchanged; frenzy modifies burst *size* only.

**GameTests:** one per trigger, with two banked traps designed in: drive HP with
`setHealth` (repeated `hurt()` trips the invulnerableTime silent no-op), and the
fight arena template is added to `assets-src/structures.py` TEMPLATES, not
hand-authored NBT.

## 7. Tamed workers

- **Sneak-click becomes a true toggle (Logan, 2026-08-18):** today binding is
  chest-driven only — right-click a chest and the nearest unbound owned worker
  takes it; sneak-clicking an unbound ant does nothing but a sound. New behavior:
  sneak-click a **bound** worker → unbind, announce "following" (unchanged);
  sneak-click an **unbound** worker → bind to the **nearest deposit chest within
  ~16 blocks** and announce "harvesting", or announce "no chest nearby" and stay
  following if none is in range. Chest-click binding stays as-is. Every
  interaction announces the resulting state — no more silent branches.
- **Smarter harvesting** (the radius machinery already exists and is respected —
  no change claimed there): **nearest-mature-crop targeting** (the scanner is
  currently a budgeted cursor, not nearest-first); **guaranteed replant** — close
  the real gap where a wheat harvest rolling zero seeds leaves the tile bare
  (always reserve one seed for replant before depositing the rest); and the
  **one-crop rule**: harvest a single mature crop → carry to chest → deposit →
  head out again. A visible shuttle run, not a vacuum sweep (current
  sweep-until-pack-full is deliberately nerfed: "too good"). Ground-item ferrying
  stays as-is.
- **GameTests:** after one harvest the next act is a deposit, not a second crop;
  replant happens even on a zero-seed roll; nearest-mature is chosen; sneak-click
  on an unbound worker binds to the nearest in-range chest (and announces the
  no-chest case).
  **Expected to move:** `the_crop_scan_is_tick_budgeted_and_incremental`
  (TamingGameTests) pins the budgeted-cursor scanner that nearest-mature targeting
  replaces — it gets explicitly retargeted, named in the commit. The single-crop
  `bound_worker_harvests_replants_and_deposits` test passes unchanged. Note for the
  implementer: the current deposit trip has three triggers (pack full, field
  exhausted, 40-tick idle — `DepositToChestGoal`); the one-crop rule supersedes
  them — remove/repurpose deliberately, don't leave dead constants.

## 8. New items

Naming note: **"chitin" below is the existing `formicary:chitin` item** — no new
intermediate item.

- **Chitin tool set** — sword, pickaxe, axe, shovel, hoe. Crafted from chitin +
  resin + sticks. Identity: fast (chitin's mining-speed theme), mid durability,
  tier between iron and diamond.
- **Foods:** **Honeyed Comb** (comb + sugar; quick good food), **Fungal Stew**
  (bowl food from fungus-garden crops; hearty + short night vision), **Royal Jelly
  Treat** (jelly + comb; brief absorption).
- **Brew:** **Hardened Chitin potion** — Awkward + chitin → Resistance, joining the
  disguise brew.
- **Provision Comb** (block, §3) drops these foods + guaranteed exit pearls (§2).

## 9. Decorative building blocks

Three families crafted from colony materials, datagen'd like existing blocks:

- **Packed Soil Bricks** — block + stairs + slab + wall
- **Hardened Soil Tiles** — block + stairs + slab
- **Polished Resin** — block + stairs + slab (makes resin worth mining)

## 10. Texture fixes

- **Soil family:** the base texture is good but has chunky same-color blotches —
  rework toward finer detailed specks and/or internal gradients within the
  blotches; stays tiling-quiet (random rotation variants allowed if they help).
- **Comb blocks — re-diagnose, don't re-apply:** a cap-UV rotation fix for exactly
  this floor-vs-wall mismatch already shipped 2026-08-13
  (`ModBlockStateProvider.combBlock`, CLOCKWISE_90 on UP/DOWN), yet the mismatch is
  still visible in play (re-reported 2026-08-18). The task is finding why the
  shipped fix doesn't land visually (wrong direction? texture row offset changed
  after?) — blindly re-applying the same rotation is explicitly not the fix.
- **Resin Weep:** full retexture (NOT Resin Block — that one is fine).
- **Chitin armor:** visual redesign — item icons and worn layer textures.

## 11. Release readiness

- **Version → 1.0.0** (`gradle.properties`; metadata is generated — never hand-edit
  the toml). Frozen only after Logan's Fri Aug 21 fresh-world pass (see cut line).
- **mods.toml metadata real:** description, author, GitHub repo + issues URLs.
- **Mod icon is a real art task:** no logo asset exists in the repo (template
  `logoFile` is commented out) — produce/borrow the Formicary logo PNG, wire it in.
- **README:** install instructions + screenshots. **CHANGELOG** written.
- **Jar smoke test outside the dev environment, both sides mandatory:** headless
  dedicated server **and** a standalone client launch — the crash class most likely
  to escape headless GameTests is client-only (renderers, `Dist.CLIENT` violations).
- **Small polish (author's addition — cut if unwanted):** the Pheromone Horn gives
  an action-bar "no room to summon" message instead of silently no-oping (no
  cooldown is charged either way — verified).
- License (MIT), GitHub publish: already done. CurseForge/Modrinth upload: Logan's,
  and may trail the video.

## 12. Verification bar

- **GameTests:** all 53 existing tests **green or explicitly retargeted** — the §7
  rework is *expected* to move the scan-budget test (see §7), and each retarget is
  named. New tests per §5–§7; Provision Comb loot + brewing recipe tests for §8.
- **NoiseProbe:** colony section per §1 (including the separation invariant and
  findability stat) + exposed-ceiling⇒membrane assert per §2, re-run across the
  standard seeds.
- **§2's runtime half has no headless route** (the arrival pocket is portal code;
  the probe can't touch registry-reaching classes, GameTestServer can't load the
  dimension — both banked). Route: pocket/membrane-column arithmetic lives in
  `ColonyGeneratorTunables` (registry-free) so the probe can assert the math, and
  the live behavior is covered by a scripted `runServer` pass.
- `.\gradlew build` + `runData` green before any commit claims done.
- Fresh-world client pass for all visual work (queen model, textures, colony feel,
  the §2 lit-ceiling change) — driven by the §13 shot-list autopilot where it has
  landed, falling back to the manual client only for interactive checks.

## 13. Dev tooling (approved 2026-08-18)

Two dev-only builds that attack the "slow and inaccurate" in-game verification loop.
Both are development aids — **excluded from release behavior** (dev-environment or
op-gated; no effect for a normal player).

- **Dev command suite** (`/formicary dev ...`): locate/teleport to the nearest
  colony center, throne, nursery, fungus garden, or larder; spawn the queen-fight
  scenario; grant the chitin kit; and **state dumps as text** — e.g. "nearest colony
  center X/Z, f here, chambers in range" — so knowing game state is a printout, not
  a pixel-read. Also directly useful for Logan's own play-testing and video shoots.
- **Shot-list autopilot:** a dev-only client hook that, on world load, runs a
  scripted route from a JSON shot list — teleport camera to position, face angle,
  wait for chunks, screenshot **internally** (the game grabs its own framebuffer),
  next entry, then exit. No window focus, no synthesized input, no desktop capture:
  it runs correctly while the machine is in use. Every visual check in §12 becomes
  "run the shot list, review the folder."

## Out of scope (deliberate)

Pearl-line/teleport items beyond the ant's drops; pheromone utility items; new
tamed roles or a colony overview item; authored colony structures; a barracks
chamber; cross-faction ender-ant-vs-colony combat. The disguise-doesn't-cover-your-
tamed-ant nit stays open (not raised for Ep2).
