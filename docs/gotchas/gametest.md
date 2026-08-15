# Formicary gotchas -- GameTest framework

Moved verbatim from CLAUDE.md ("Banked rules" + the Build/run GameTest paragraph) by the
2026-08-14 /tidy-claude-md restructure, re-flowed 2026-08-15 to take in the play-test
round 1 rules; each entry keeps its original `verified:` date.
Open this file before writing ANY GameTest -- several of these traps fail silently.

GameTest facts (verified 2026-08-01 in ModTest, same pins): there is NO built-in empty
structure template -- `@GameTest(template=...)` needs a real `.nbt` at
`data/formicary/structure/<name>.nbt`. This repo generates its own with
`python assets-src\structures.py` (5x3x5 `platform` + 48x3x5 `long_platform`; add a size
to that script's `TEMPLATES` dict rather than hand-authoring nbt). Annotate the test class
`@GameTestHolder(Formicary.MODID)` and methods `@PrefixGameTestTemplate(false)` or the
template resolves under the wrong namespace/class-name prefix. GameTestServer runs at
NORMAL difficulty (Monsters safe).

- **GameTest structure templates can be written straight from python** -- gzipped NBT,
  root compound with `size` (LIST<INT>), `entities` (empty LIST<END>), `blocks`
  (LIST<COMPOUND> of `{pos: LIST<INT>[3], state: INT}`), `palette` (LIST<COMPOUND> of
  `{Name: STRING}`) and `DataVersion: 3953`. `assets-src/structures.py` does it; the
  layout was verified by reading ModTest's `platform.nbt` back, not recalled.
  `GameTestHelper.makeMockPlayer(GameType)` returns a bare `Player` that is **never added
  to the level**, so it can carry a `DamageSource` (vanilla's damage events then fire for
  real) but it cannot drive `ServerPlayerGameMode`, and `level.getNearestPlayer` will not
  see it. (`verified: 2026-08-13`)
- **`GameTestServer` cannot see datapack dimensions.** It bakes the `WorldPresets.FLAT`
  preset into a deliberately empty `LevelStem` registry, so a custom dimension is absent
  from the test server no matter what the datapack says. Verify dimension loading with
  `runServer` (the save grows `world/dimensions/<ns>/<path>/`), not with a GameTest.
  (`verified: 2026-08-13`)
- **`GameTestHelper.absolutePos(0,0,0)` is the STRUCTURE BLOCK, not the template's own
  origin** -- the placed template sits one block above it, so a template's `y=0` layer (the
  arena floor) is at *relative* `y=1`, and standable air starts at relative `y=2`. Every
  existing test in this repo happens to work either way (they place blocks at rel y=1,
  overwriting a floor block, and `helper.spawn` pushes entities out of solids), which is why
  it went unnoticed until M5 wrote a test that read the floor. Rule of thumb for anything
  height-sensitive: write every block the assertion reads, and derive Ys from
  `absolutePos` of a position you wrote. (`verified: 2026-08-13`)
- **`@GameTest(skyAccess = ...)` defaults to FALSE, which roofs the arena in BARRIER
  blocks** (`GameTestInfo.prepareTestStructure` -> `StructureUtils.encaseStructure(bounds,
  level, !skyAccess)`; the side walls are placed either way). Any assertion that depends on
  sky light -- `CropBlock#canSurvive` calls `hasSufficientLight`, so planting a crop is one
  -- fails silently under the default. (`verified: 2026-08-13`)
- **Vanilla crop loot is not uniformly self-seeding.** Read out of
  `data/minecraft/loot_table/blocks/*.json` in the client-extra jar: `carrots` and `potatoes`
  have an unconditional first pool (always >=1, and that item is the seed), while `wheat`'s
  seed pool is the fortune-binomial one alone -- ~9% of breaks yield zero `wheat_seeds`. Pick
  carrots/potatoes for any test that has to replant deterministically. (`verified: 2026-08-13`)
- **`GameTestHelper.makeMockPlayer` returns a bare `Player`, not a `ServerPlayer`** -- it
  has no `getAdvancements()`, so a custom `CriterionTrigger`'s actual award cannot be
  asserted through it. The only alternative, `makeMockServerPlayerInLevel()`, is
  `@Deprecated(forRemoval = true)` and adds a real player to the level via the player list.
  A trigger's own matching/predicate logic (a pure function on its `TriggerInstance`) is
  still directly testable; the award itself is not, headlessly. (`verified: 2026-08-14`)
- **`GameTestHelper.destroyBlock(pos)` hardcodes `dropBlock=false`** (`this.getLevel()
  .destroyBlock(this.absolutePos(pos), false, null)`) -- it skips `Block.dropResources`
  entirely, so neither the loot table nor a `DropExperienceBlock`'s `BlockDropsEvent` XP
  fires. A test asserting on break drops needs the real pipeline: `level.destroyBlock(pos,
  true)` (the `LevelWriter` default overload, `dropBlock=true`), not the helper's shortcut.
  (`verified: 2026-08-15`)
