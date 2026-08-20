# CLAUDE.md -- Formicary

A NeoForge Minecraft mod: an ant-colony dimension entered by throwing an ender pearl at a
savanna anthill; four ant mobs; a larva-raising loop that yields crop-harvesting worker
ants. Spec: `D:\MyProjects\_notes\formicary-build-prompt.md`. Decisions log: `docs/DECISIONS.md`.

This file tells Claude Code how the repo is wired and the correctness rules to follow.
Keep it updated: every time a version-specific mistake is caught, bank the fix verbatim in
the matching `docs/gotchas/` file and add a symptom-keyed line to the index below. Only a
rule that fires in most sessions AND fits in ~2 lines stays resident here.

## Project facts

- Loader: **NeoForge** | MC: **1.21** | NeoForge: **21.0.167** | Java: **21** (Gradle toolchain)
- Mod id: `formicary` | Main class: `com.nogal.formicary.Formicary` (annotated `@Mod`)
- Mappings: Mojang official (mojmap) at runtime + ParchmentMC `2024.11.10` parameter names/Javadoc
- Metadata is GENERATED, not hand-written: the `generateModMetadata` task expands
  `${mod_id}` / `${mod_name}` / `${mod_version}` etc. from `gradle.properties` into
  `src/main/templates/META-INF/neoforge.mods.toml`. To change mod identity, edit
  `gradle.properties` -- do NOT hardcode values into the toml.

## Build / run (PowerShell, from repo root)

CLI sessions must set `$env:JAVA_HOME = "C:\Users\Family\.jdks\jdk-21.0.11+10"` first --
the shell default is JVM 8 and Gradle refuses to launch (`verified: 2026-08-01`).

- Build + compile-check:   `.\gradlew build`
- Run client:              `.\gradlew runClient`
- Run dedicated server:    `.\gradlew runServer`
- Generate data (JSON):    `.\gradlew runData`   (outputs to `src/generated/resources`)
- Headless GameTests:      `.\gradlew runGameTestServer`

GameTests are enabled in the dev runs via `neoforge.enabledGameTestNamespaces` (derived
from `project.mod_id` in `build.gradle`, so the namespace is `formicary`). After ANY edit,
run `.\gradlew build` and fix every compile error before claiming a task is done -- the
compiler is the source of truth, not training memory.

Live texture/JSON iteration without restarting the client: edit assets ->
`.\gradlew processResources` -> F3+T in the running client (dev runs read
`build/resources/main`). Java class changes still need a client restart.
(`verified: 2026-08-13`)

1.21 datapack folders: `loot_table` (singular) and `neoforge/biome_modifier`.

## Ground truth -- do NOT trust training memory for API signatures

Minecraft/NeoForge APIs change every version, and training data blends Forge/Fabric/
NeoForge across many versions. Before using any MC/NeoForge symbol, verify against:

1. The decompiled, Parchment-named sources in `reference/` (gitignored; copied from
   ModTest's extraction -- identical version pins). Grep real signatures there.
2. Official docs: https://docs.neoforged.net/docs/ (set the version switcher to 1.21).
3. The official 1.21 MDK: https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle

If a signature cannot be verified, say so -- never invent one.
`reference/` is a PARTIAL extraction -- a missing class is NOT a missing API. Re-extract
recipe + the log of what each milestone added: `docs/gotchas/reference-extraction.md`.

## Hard rules (footguns)

- Mappings are Mojang official at runtime. NEVER use old Forge SRG names (`func_*`, `field_*`).
- Register all content with **DeferredRegister** (see `Formicary.java` for the pattern) and
  register each DeferredRegister to the mod event bus in the constructor.
- **World/Level is NOT thread-safe.** Any world modification off the main thread must be
  marshalled onto the server thread: `server.execute(...)` or an event's `enqueueWork(...)`.
- Respect sides: keep client-only code (rendering, `Minecraft.getInstance()`, screens) out
  of common/server paths -- gate it with `Dist.CLIENT`.
- Prefer **NeoForge events/hooks** over Mixins. Only reach for a Mixin when no event exists,
  and flag it for review.
- Prefer **data-driven content** (JSON via datagen / `runData`) over hardcoded Java.
- Item data uses the **1.21 Data Components** system, not pre-1.21 NBT.
- **Tear the input rig down before handing Logan the client.** The screenshot-driving rig
  rebinds attack/use to G/H in `run/options.txt`; leaving it bound ships him a client that
  cannot break blocks (2026-08-14 -- his first play session, one flat bug report). Any rig
  that mutates shared state (options, configs, the dev world) needs its teardown written at
  the same time as its setup, and the audit question is "what persistent files did my
  instrumentation touch?", not "did my tests pass?". Live in-game verification can be fully
  green and the first human contact still broken.

## Entity models -- art pipeline

Programmer art, no Blockbench: one spec dict per mob in `assets-src/models.py` drives both
texture painting and orthographic QA previews -- run `python assets-src\models.py` after
edits. Before ANY model/render work, open `docs/gotchas/entity-models.md` (1.21 signature
changes, held-item layer, UV rules, the full pipeline description).

## Gotcha index -- open on symptom match

All under `docs/gotchas/`. Every entry is a verbatim banked rule with its `verified:`
date; the index line is the only route to it -- open the file before working in its
subsystem, and bank new rules there, not here.

- **runData floods "Missing loottable" for vanilla blocks/mobs**; a custom loot condition
  fails datagen validation; "Pattern references symbol '#' but it's not defined in the
  key"; `RecipeProvider` ctor/override shape + output folders; custom advancement
  `CriterionTrigger` registration or the codec rejecting empty criteria -> `datagen.md`
- **Writing or debugging ANY GameTest** (open it FIRST -- several traps fail silently):
  structure-template NBT authoring, arena floor off-by-one (`absolutePos` is the structure
  block), BARRIER roof killing light-dependent asserts (`skyAccess`), mock-player limits
  (no advancements, invisible to `getNearestPlayer`), custom dimension absent on
  GameTestServer, replant tests flaking ~9% on wheat seeds, `helper.destroyBlock` dropping
  nothing and popping no XP (it hardcodes `dropBlock=false`), a distance assertion sampled
  ticks after the test's own swing drifting on residual knockback, an ant vanishing from a
  long-running `skyAccess = true` arena (it climbs out and cannot get back) -> `gametest.md`.
  A test swing that lands no damage and leaves no grudge is the `hurt` invulnerability
  window -- that one is in `entity-ai.md`.
- **Server dies at JSON parse loading a dimension**; biome bands land at 1/4 height; mobs
  spawn only in the top band or inside solid blocks; a neighbour read wraps to the same
  chunk (`& 15`); jigsaw/structure/template-pool JSON shapes; a template stamps terrain
  without clearing air; a mob that spawned during generation vanishes from a
  `getChunk`-generated chunk; an entity baked into a template silently never appears (its
  `blockPos` fell outside the piece bounds); `MobCategory.CREATURE` mobs never spawn at
  runtime at all; a headless tool dies with `IllegalArgumentException: Not bootstrapped`
  -> `worldgen.md`
- **Mob AI / taming / boss**: `mobInteract` access compile error, sit/stay vs guard mode,
  goals only ticking every other tick, boss-bar plumbing, "weaker access privileges" on
  `getVoicePitch`, `canBeLeashed` signature, mob forgets its `restrictTo` post on relog,
  owner-aware goals for non-`TamableAnimal` allies, a cleared target that comes back two
  ticks later (or a mob that will not stop attacking something -- the seam is `canAttack`),
  a hit that provokes damage handlers but records no grudge/knockback/damage (the
  `invulnerableTime` early return -- bites 1.0F GameTest swings), retaliation invisible
  when the hit lands on the mob's spawn tick, `xpReward` set on a `TamableAnimal` but the
  mob still drops 1-3 XP, a mob that parks next to what it wants and never closes the last
  step (`moveTo`'s arrival is looser than a goal's own reach) -> `entity-ai.md`
- **Any entity model/render work**: held item invisible, `renderToBuffer` signature,
  UV/texture-resolution mismatch, `setupAnim` drift; full art pipeline -> `entity-models.md`
- **Which 1.21 event replaced `LivingHurtEvent` / `PlayerTickEvent`**; `javap` can't find
  `EventBusSubscriber`; a cancelled pearl keeps flying after `ProjectileImpactEvent`;
  cross-dimension teleport no-ops (`DimensionTransition`) -> `events-portals.md`
- **Armor materials / layer textures**; data attachments (`getData` stores the default,
  `copyOnDeath` throws, respawn loss); two containers sharing one `ItemStack`; "Cannot get
  property ... does not exist in Block" (CropBlock AGE); crop growth-speed retune; a block
  that pops XP with an empty loot table, or Silk Touch zeroing that XP
  -> `items-blocks.md`
- **A class is missing from `reference/`** -> it's a partial extraction, not a missing
  API; re-extract recipe + per-milestone log -> `reference-extraction.md`
- **Scripting a headless `runServer`/`runClient`** (piped stdin never reaches the game JVM;
  `--args` replaces the whole argfile; the self-driving `ServerTickEvent` probe recipe; the
  shot-list autopilot) -> `docs/dev-tools.md` (lives outside `gotchas/`, next to the tools)

## Workflow

- Conventional commits, one per logical step. Commit as you go; never leave finished work
  uncommitted. Never tag or push unless explicitly asked.
- Work milestone by milestone (M0-M8 in the spec); don't start M(n+1) with M(n) broken.
