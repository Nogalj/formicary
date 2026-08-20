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
- **All arenas in a batch share one level on a tight grid, so any RADIUS search escapes its
  own arena.** `StructureGridSpawner` lays live arenas out with `SPACE_BETWEEN_COLUMNS = 5`
  / `SPACE_BETWEEN_ROWS = 6`, so a 16-block search from mid-arena reaches its neighbours --
  which routinely contain chests, mobs, and crops of their own. Any test exercising code
  that searches by radius (rather than reading a position the test wrote) must isolate
  itself, e.g. run 80 blocks above its arena floor (`lift()` in TamingGameTests). Same
  family: a real `ServerPlayer` left in the level by `makeMockServerPlayerInLevel` is
  visible to every other arena's target goals -- remove it via `PlayerList.remove` in the
  same test. (`verified: 2026-08-18`)
- **A drop a test places does not stay where the test put it.** The four-double
  `new ItemEntity(level, x, y, z, stack)` constructor delegates to the velocity overload with
  `level.random.nextDouble() * 0.2 - 0.1` on X and Z and `0.2` up (read in
  `reference/net/minecraft/world/entity/item/ItemEntity.java`), so every drop gets a random
  horizontal kick and settles somewhere the test did not choose. A test that spawns a drop and
  then asserts a mob reaches it is therefore sampling a distribution, not pinning a behaviour.
  Use the explicit-velocity overload `new ItemEntity(level, x, y, z, stack, 0, 0, 0)` whenever
  the drop's exact position is load-bearing -- `a_drop_just_outside_pickup_range_is_still_collected`
  does, which is what makes it deterministic where its live-loop sibling was not.
  (`verified: 2026-08-18`)
- **A movement assertion sampled N ticks after the test's own landed hit measures residual
  knockback, not just the behaviour under test.** `makeMockPlayer` leaves the attacker at the
  world origin, so a landed swing gives the mob ~0.3-0.4/tick of velocity on a fixed bearing
  (away from origin) that is unrelated to anything the test set up -- about 1.3-1.8 blocks of
  drift over a 10-tick window -- and `Entity.moveTo` resets position but NOT velocity, so
  repositioning the mob between swings does not shed it. This dragged legitimate 8.4-block
  ender-ant blinks under `a_hurt_ender_ant_blinks_at_least_eight_blocks`'s 8.0 lower bound
  (~6-9% flake; instrumented atBlink-vs-atPlus10 deltas of +-1.8), and symmetrically eats the
  regression-catching margin of any upper-bound (negative) assertion. Measure a synchronous
  reaction immediately after `hurt()` returns; where a delayed window is load-bearing (a
  negative assertion must give a deferred reaction time to happen), zero the velocity with
  `setDeltaMovement(Vec3.ZERO)` after the reposition instead. (`verified: 2026-08-18`)
- **A climbing mob does not stay in a `skyAccess = true` arena, and once out it is gone for
  good.** As of the 2026-08-19 climbing pass every ant except the queen and the larva has
  `WallClimberNavigation` + Spider's `horizontalCollision` -> `onClimbable` flag, which means
  *any* horizontal collision lifts it 0.2/tick. `StructureUtils.encaseStructure(bounds, level,
  !skyAccess)` places the side walls either way but only roofs the arena when `skyAccess` is
  false, so an open-topped arena is a wall the mob can now walk up and over. Worse, it cannot
  come back: the arena floor sits one block ABOVE the test world's surface, so outside the
  walls there is a one-block air gap running underneath the whole arena, and the escapee ends
  up below its own floor with `helper.getBounds().contains(...) == false`. Seen as a ~1-in-5
  timeout on `a_bound_worker_never_carries_two_crops_at_once`, whose worker idles for a
  200-tick deposit cooldown between trips -- long enough to brush a wall. Only long-idling
  tests are exposed; a test that finishes its business in a few dozen ticks rarely touches a
  wall at all. The fix is not a longer timeout: keep the default barrier roof and buy the
  light some other way. Crops need `CropBlock.hasSufficientLight`, which is
  `getRawBrightness(pos, 0) >= 8` -- block light counts, so a glowstone sunk into the arena
  floor lights a crop patch just as well as the sky does. Corollary for diagnosis: a
  GameTest timeout is mute by itself, so put the state that separates the hypotheses into the
  assertion message (here: chest count, pack count, position relative to the arena, and
  `inBounds`) -- those four numbers named the cause on the first failing run.
  (`verified: 2026-08-19`)
- **A radius search escaping its arena does not have to reach an entity to wreck a test -- it
  can reach a BLOCK, and then a higher-priority goal quietly eats the whole run.** The grid
  entry above is about what a search finds; this is about what finding it costs. A tamed
  worker's `HarvestCropsGoal.canUse` sweeps `CropScanner` over `PATROL_RADIUS = 16` blocks
  around the **bound chest** -- a 33x33x7 box, against arenas that are 9 wide and 5-6 apart --
  so a ripe crop planted by a *neighbouring test* is a perfectly ordinary find. Harvest is
  goal priority 2 and `CollectDroppedItemsGoal` is 3, and `GoalSelector` only lets a strictly
  lower-numbered goal preempt a running one, so once harvest starts the pickup goal never gets
  `MOVE`/`LOOK` and **never starts at all** -- the failing test's own drop is never targeted,
  and its trace is empty rather than wrong. Caught 2026-08-20 on
  `a_drop_just_outside_pickup_range_is_still_collected`, which plants no crop of its own:
  arena bounds `[..., 9131134] -> [..., 9131143]`, chest anchor `z=9131138`, harvest target
  `BlockPos{x=2152091, y=-58, z=9131152} minecraft:beetroots[age=3]` -- `cropOffsetFromAnchor
  =(0,0,14)`, nine blocks past its own wall. Three things make this worse than it sounds.
  (a) **No timeout budget can fix it**: `APPROACH_TIMEOUT_TICKS` stops the goal, then `canUse`
  re-scans, picks the identical unreachable crop and starts again -- observed as a second
  `HARVEST START` on the same block. (b) **It presents as a latency problem**, because the
  test just times out; raising `timeoutTicks` 100 -> 200 changed nothing. (c) **It looks like
  goal-tick parity**, because the trigger is "two unrelated tests were added earlier in the
  session" -- which does shift `(tickCount + getId()) % 2`, and also re-packs the arena grid.
  The grid is the real coupling; parity was a coincidence riding along. Diagnosis that
  actually named it in one run: dump *which goals are running* on the mob each tick, not just
  its position -- `worker.goalSelector.getAvailableGoals()` with `WrappedGoal::isRunning` put
  `HarvestCropsGoal[RUN]` on screen while the drop sat 1.700 blocks away untouched. The fix is
  per-test and lives in `TamingGameTests.isolateFromForeignCrops`: `goalSelector.removeAllGoals`
  (which is `@VisibleForTesting` in vanilla) strips the goal that is not under test. Pre-loading
  the pack to close harvest's `getPack().isEmpty()` gate does NOT work -- a non-empty pack wakes
  `DepositToChestGoal` at priority 1 and starves the pickup identically -- and no anchor
  position clears a 16-block sweep on a 5-block grid. `lift()` is not available either: these
  tests need ground to walk on, and the two existing lifted tests already own that altitude.
  (`verified: 2026-08-20`)
- **A climbing ant a test deliberately traps against a wall stays wedged there even after its
  goal retargets somewhere else -- unless the new target's approach avoids the same wall.**
  Building a real cage for a climbing ant (see the entry above) works exactly as intended: the
  ant gets pinned against the wall, frozen (`onClimbable() == true`, position unchanged) for
  the whole approach timeout. The surprise is what happens *after* the goal gives up and
  retargets a second, genuinely reachable position: if the straight line from the ant's wedged
  position to that new target still runs anywhere near the same wall, the ant stays wedged --
  confirmed empirically 2026-08-20 while writing
  `a_bound_worker_gives_up_on_an_unreachable_crop_and_harvests_a_reachable_one`, whose first
  draft put the reachable crop just past the far side of the cage (adjacent, one cell outside
  it) and left the ant frozen against the cage indefinitely even once the goal's own blacklist
  correctly excluded the caged crop and picked the reachable one -- `WallClimberNavigation`'s
  "no path -> push the mob at the target with the move control" fallback (see
  `docs/gotchas/entity-ai.md`'s `AntClimbing` note) still pushes straight through the same
  wall it is already pinned against when the target lies on the far side of it. The fix is
  geometric, not code: place a second target so the direct line back to it retraces the
  ant's own approach (e.g. due west along the same row the ant walked in on) rather than
  passing the cage again -- confirmed to escape cleanly once repositioned that way. Any test
  that traps a climbing ant and then expects it to do something else afterward needs this
  checked, not assumed. (`verified: 2026-08-20`)
- **RESOLVED -- the former "Flake watch" on `bound_worker_collects_a_ground_item_and_deposits_it`.**
  Reproduced 2026-08-18 at 2 failures in 33 runs (~6%), then twice more with instrumentation.
  It was **not** arena cross-contamination, which was the standing prime suspect: a diagnostic
  dump taken at the timeout tick showed the 28-block neighbourhood around the arena held
  exactly one item entity and exactly one tamed worker -- both the test's own -- and no
  container anywhere near holding the deposited item. The real defect was a permanent pathing
  deadlock in `CollectDroppedItemsGoal` (mechanism banked in `entity-ai.md`), and the only
  random ingredient was the `ItemEntity` kick above deciding whether that run's drop settled
  inside the deadlock band. A per-tick trace showed the worker frozen at 1.537 blocks from its
  own drop with `navDone=true` from tick 25 to tick 600. Worth keeping as method: the
  contamination hypothesis was cheap to *test* (dump the neighbourhood at failure) and would
  have cost a wrong fix to assume. (`verified: 2026-08-18`)
