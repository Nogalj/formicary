# CLAUDE.md -- Formicary

A NeoForge Minecraft mod: an ant-colony dimension entered by throwing an ender pearl at a
savanna anthill; four ant mobs; a larva-raising loop that yields crop-harvesting worker
ants. Spec: `D:\MyProjects\_notes\formicary-build-prompt.md`. Decisions log: `docs/DECISIONS.md`.

This file tells Claude Code how the repo is wired and the correctness rules to follow.
Keep it updated: every time a version-specific mistake is caught, bank the fix here as a rule.

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

GameTest facts (verified 2026-08-01 in ModTest, same pins): there is NO built-in empty
structure template -- `@GameTest(template=...)` needs a real `.nbt` at
`data/formicary/structure/<name>.nbt`. This repo generates its own with
`python assets-src\structures.py` (5x3x5 `platform` + 48x3x5 `long_platform`; add a size
to that script's `TEMPLATES` dict rather than hand-authoring nbt). Annotate the test class
`@GameTestHolder(Formicary.MODID)` and methods `@PrefixGameTestTemplate(false)` or the
template resolves under the wrong namespace/class-name prefix. GameTestServer runs at
NORMAL difficulty (Monsters safe).
1.21 datapack folders: `loot_table` (singular) and `neoforge/biome_modifier`.

## Ground truth -- do NOT trust training memory for API signatures

Minecraft/NeoForge APIs change every version, and training data blends Forge/Fabric/
NeoForge across many versions. Before using any MC/NeoForge symbol, verify against:

1. The decompiled, Parchment-named sources in `reference/` (gitignored; copied from
   ModTest's extraction -- identical version pins). Grep real signatures there.
2. Official docs: https://docs.neoforged.net/docs/ (set the version switcher to 1.21).
3. The official 1.21 MDK: https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle

If a signature cannot be verified, say so -- never invent one.

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

## Entity models -- art pipeline

Programmer art first: the no-Blockbench pipeline from ModTest (`assets-src/models.py`
there) is the reference approach -- one python spec per model drives both the texture
painting (PIL) and orthographic QA previews (`assets-src/previews/`), then the same
numbers are hand-translated into `LayerDefinition` Java. Box-UV face rects in ModTest's
script are verified against decompiled `ModelPart.java` -- trust it over memory.
This repo now has its own `assets-src/models.py` (worker ant, M2) -- add new mobs as
another spec dict there rather than starting a new script. Run
`python assets-src\models.py` to regenerate textures + previews after edits; its
preview renderer is a real orthographic projection (rotations applied Rz*Ry*Rx, faces
depth-sorted), so what the contact sheet shows is what the game draws.
Entity textures go to `src/main/resources/assets/formicary/textures/entity/`.
`D:\MyProjects\ModTest\src\main\java\com\nogal\modtest\client\model\TarantulaModel.java`
is a known-correct 1.21 entity model reference.

1.21 entity-model rules (all verified in ModTest):

1. **`renderToBuffer` takes an int colour, not four floats.** `Model` declares
   `renderToBuffer(PoseStack, VertexConsumer, int, int, int color)` and `ModelPart.render`
   has only `(PoseStack, VertexConsumer, int, int)` / `(..., int color)` overloads.
2. **`new ResourceLocation(...)` is gone** -- use `ResourceLocation.fromNamespaceAndPath(ns, path)`.
3. `LayerDefinition.create(mesh, W, H)` bakes the texture resolution -- it must match the
   painted texture or UVs are wrong.
4. In `setupAnim`, write rest poses **absolutely** every frame. Reading a part's current
   `zRot` to use as a rest value accumulates drift, because nothing resets a plain
   `EntityModel`'s parts between frames (only `HierarchicalModel.animate()` does).

## Banked rules (caught during this build)

- **Datagen `BlockLootSubProvider.getKnownBlocks()` defaults to EVERY block in the game**
  (`BuiltInRegistries.BLOCK`) -- leave it unoverridden and runData throws "Missing
  loottable" for all vanilla blocks. Always override it to return this mod's
  `ModBlocks.BLOCKS.getEntries()`. (`verified: 2026-08-13`)
- **Live texture/JSON iteration without restarting the client:** edit assets ->
  `.\gradlew processResources` -> F3+T in the running client (dev runs read
  `build/resources/main`). Java class changes still need a client restart.
  (`verified: 2026-08-13`)
- **A plain `MobRenderer` draws NOTHING for a mob's main-hand item.** Vanilla's
  `ItemInHandLayer` requires the model to implement `ArmedModel`, which no custom
  non-humanoid does. Copy `FoxHeldItemLayer` instead: a `RenderLayer` that parks the
  PoseStack on the head pivot, follows the head rotation, then calls
  `context.getItemInHandRenderer().renderItem(entity, stack, ItemDisplayContext.GROUND,
  false, poseStack, buffer, light)`. See `client/renderer/WorkerAntCarriedItemLayer.java`.
  (`verified: 2026-08-13`)
- **`reference/` is a PARTIAL extraction** (it was seeded from ModTest's block/datagen-era
  copy). A missing class is not a missing API. Re-extract on demand from
  `build/moddev/artifacts/neoforge-21.0.167-sources.jar` with
  `[System.IO.Compression.ZipFile]::OpenRead(...)` filtered by package prefix -- M2 had to
  add `world/phys`, `client/renderer`, `world/item`, `core`, `util`, `com/mojang/math`,
  `world/level/EntityGetter.java`, `world/level/Level.java` and
  `world/entity/ai/navigation`. M3b had to add the whole of `tags/`,
  `data/tags/`, `world/level/storage/loot/` and `advancements/critereon/`, plus
  `server/level/ServerLevel.java`, `world/entity/ai/targeting/TargetingConditions.java`,
  `world/entity/player/Player.java`, `world/damagesource/DamageSources.java` and
  `net/neoforged/neoforge/common/NeoForge.java`. M3a had to add `world/InteractionResult.java`,
  `world/InteractionHand.java`, `world/item/ItemUtils.java`,
  `data/loot/EntityLootSubProvider.java` + `packs/VanillaEntityLoot.java`,
  `world/entity/EntityType.java`, `world/entity/player/Inventory.java`,
  `world/level/storage/loot/{parameters/LootContextParamSets,predicates/
  LootItemRandomChanceCondition,functions/EnchantedCountIncreaseFunction,
  functions/SetItemCountFunction,providers/number/UniformGenerator,
  entries/LootItem,LootPool,LootTable}.java` and `sounds/SoundSource.java`. M6 had to add
  `world/{Container,SimpleContainer,ContainerHelper,CompoundContainer,WorldlyContainer,
  Containers,ContainerListener}.java`,
  `world/level/block/entity/{ChestBlockEntity,BaseContainerBlockEntity,
  RandomizableContainerBlockEntity}.java`,
  `world/entity/{OwnableEntity}.java`, `world/entity/ai/goal/{GoalSelector,
  target/TargetGoal}.java` and `network/syncher/{EntityDataSerializers,
  SynchedEntityData}.java`. M7 had to add `world/BossEvent.java` (the top-level
  `net/minecraft/world/*.java` files are thin on the ground in this copy -- a sibling of an
  already-present class is no guarantee). (`verified: 2026-08-13`)
- **NeoForge event names for 1.21 that training memory gets wrong.** All three verified in
  the extracted 21.0.167 sources: damage is
  `net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent` (fires in
  `LivingEntity#hurt` before mitigation -- the old `LivingHurtEvent` is GONE, and
  `LivingDamageEvent` is now an abstract `Pre`/`Post` pair fired later in the sequence);
  block breaking is `net.neoforged.neoforge.event.level.BlockEvent.BreakEvent`; mining
  speed is `net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed`
  (`getOriginalSpeed()` / `getNewSpeed()` / `setNewSpeed(float)`). The game bus enum is
  `EventBusSubscriber.Bus.GAME` -- and `EventBusSubscriber` ships in the FML *loader* jar
  (`net.neoforged.fancymodloader:loader`), NOT the neoforge sources jar, so `javap` on
  that jar is the only way to check it. (`verified: 2026-08-13`)
- **1.21 armor materials are a REGISTRY, not an enum.** `Registries.ARMOR_MATERIAL`;
  vanilla builds each entry with `Registry.registerForHolder(...)` in `ArmorMaterials`,
  and `ArmorItem` takes a `Holder<ArmorMaterial>` -- so a `DeferredRegister` +
  `DeferredHolder` is the mod-side equivalent and no ordering dance is needed
  (`ArmorItem` memoises its attribute modifiers, so it never dereferences the holder
  during registration). The `ArmorMaterial.Layer` asset name resolves to
  `<ns>:textures/models/armor/<path>_layer_1.png` (outer: HEAD/CHEST/FEET) and
  `_layer_2.png` (inner: LEGS only -- `HumanoidArmorLayer.usesInnerModel` returns true
  just for `EquipmentSlot.LEGS`). Humanoid overlay UV rects are 64x32 with head at
  `texOffs(0,0) 8x8x8`, body `(16,16) 8x12x4`, arm `(40,16) 4x12x4`, leg `(0,16) 4x12x4`.
  (`verified: 2026-08-13`)
- **Custom loot conditions:** register a `LootItemConditionType(MapCodec<...>)` into
  `Registries.LOOT_CONDITION_TYPE` via `DeferredRegister`; copy vanilla's
  `LootItemKilledByPlayerCondition` shape (stateless singleton + `MapCodec.unit`). The
  player who broke a block arrives as `LootContextParams.THIS_ENTITY`, which
  `LootContextParamSets.BLOCK` declares **optional** -- use `getParamOrNull`, and declare
  it in `getReferencedContextParams()` or datagen validation rejects the table.
  (`verified: 2026-08-13`)
- **GameTest structure templates can be written straight from python** -- gzipped NBT,
  root compound with `size` (LIST<INT>), `entities` (empty LIST<END>), `blocks`
  (LIST<COMPOUND> of `{pos: LIST<INT>[3], state: INT}`), `palette` (LIST<COMPOUND> of
  `{Name: STRING}`) and `DataVersion: 3953`. `assets-src/structures.py` does it; the
  layout was verified by reading ModTest's `platform.nbt` back, not recalled.
  `GameTestHelper.makeMockPlayer(GameType)` returns a bare `Player` that is **never added
  to the level**, so it can carry a `DamageSource` (vanilla's damage events then fire for
  real) but it cannot drive `ServerPlayerGameMode`, and `level.getNearestPlayer` will not
  see it. (`verified: 2026-08-13`)
- **`EntityLootSubProvider.getKnownEntityTypes()` defaults to EVERY entity type in the
  game** (`BuiltInRegistries.ENTITY_TYPE`) -- the same trap as
  `BlockLootSubProvider.getKnownBlocks()` (above), and it throws "Missing loottable" for
  every vanilla mob if left unoverridden. Always override it to return just this mod's
  `EntityType`s. `EntityType#getDefaultLootTable()` derives the table id as
  `entities/<namespace>/<path>` automatically, so `add(EntityType, builder)` (no explicit
  key) is enough. (`verified: 2026-08-13`)
- **Custom worldgen: the registries hold `MapCodec`s, not instances.** A data-driven
  `dimension` JSON names a generator via its `"type"` field, which
  `ChunkGenerator.CODEC` / `BiomeSource.CODEC` dispatch through
  `BuiltInRegistries.CHUNK_GENERATOR` / `BIOME_SOURCE`. So the DeferredRegisters are
  `DeferredRegister<MapCodec<? extends ChunkGenerator>>` against `Registries.CHUNK_GENERATOR`
  (path `worldgen/chunk_generator`) and `Registries.BIOME_SOURCE` (`worldgen/biome_source`),
  and what you register is the class's `CODEC` field. Miss the bridge and the server dies at
  JSON parse, not at runtime. Datapack directories: `data/<ns>/dimension_type/` for
  `Registries.DIMENSION_TYPE`, and **`data/<ns>/dimension/`** for `Registries.LEVEL_STEM`
  -- the registry named `minecraft:dimension` holds `LevelStem`, while `Registries.DIMENSION`
  is the separate runtime `Level` registry sharing that id. (`verified: 2026-08-13`)
- **`BiomeSource#getNoiseBiome` takes QUART coordinates, not blocks** (one sample per
  4x4x4). Convert with `QuartPos.toBlock(y)` before comparing against block-space Y bands,
  or every boundary lands at a quarter of its intended height. Verified against
  `BiomeSource#getBiomesWithin` and `TheEndBiomeSource`. (`verified: 2026-08-13`)
- **A custom `ChunkGenerator` never receives the world seed.** `fillFromNoise` /
  `buildSurface` only get a `RandomState`; take seeded randomness from
  `randomState.getOrCreateRandomFactory(ResourceLocation)`, which forks the level seed and
  is cached per name. (For a non-`NoiseBasedChunkGenerator`, `ChunkMap` builds that
  `RandomState` from `NoiseGeneratorSettings.dummy()` and the real level seed.)
  `PerlinNoise.create(random, List.of(0))` is the predictable primitive: with a single
  octave 0 both of its internal scale factors come out as exactly 1.0, so `getValue` is raw
  `ImprovedNoise` at the coordinates you pass and your thresholds mean something stable.
  Measured span over ~1M samples: min -0.90, max 0.91, mean |v| 0.215. (`verified: 2026-08-13`)
- **`NaturalSpawner.spawnMobsForChunkGeneration` only ever populates the topmost cave in a
  `has_ceiling` dimension.** Its `getTopNonCollidingPos` takes the ceiling branch and walks
  down to the *first* air pocket below the roof, and `NoiseBasedChunkGenerator` feeds it the
  biome at `maxBuildHeight - 1`. In a vertically banded dimension, calling it once per band
  fills the top band N times and leaves the rest empty -- write a per-band spawner instead.
  Also: an `EntityType` with no registered `SpawnPlacements` falls back to
  `NO_RESTRICTIONS`, which approves spawning inside solid blocks; register
  `SpawnPlacementTypes.ON_GROUND` via `RegisterSpawnPlacementsEvent` (mod bus).
  (`verified: 2026-08-13`)
- **`GameTestServer` cannot see datapack dimensions.** It bakes the `WorldPresets.FLAT`
  preset into a deliberately empty `LevelStem` registry, so a custom dimension is absent
  from the test server no matter what the datapack says. Verify dimension loading with
  `runServer` (the save grows `world/dimensions/<ns>/<path>/`), not with a GameTest.
  (`verified: 2026-08-13`)
- **`ProtoChunk#getBlockState` masks X/Z with `& 15`.** Reading a neighbour one block
  outside the chunk silently wraps to the opposite edge of the *same* chunk instead of
  failing. Anything in worldgen that looks sideways must either stay inside the chunk or
  recompute from a position-pure function. (`verified: 2026-08-13`)
- **`GameTestHelper.absolutePos(0,0,0)` is the STRUCTURE BLOCK, not the template's own
  origin** -- the placed template sits one block above it, so a template's `y=0` layer (the
  arena floor) is at *relative* `y=1`, and standable air starts at relative `y=2`. Every
  existing test in this repo happens to work either way (they place blocks at rel y=1,
  overwriting a floor block, and `helper.spawn` pushes entities out of solids), which is why
  it went unnoticed until M5 wrote a test that read the floor. Rule of thumb for anything
  height-sensitive: write every block the assertion reads, and derive Ys from
  `absolutePos` of a position you wrote. (`verified: 2026-08-13`)
- **A data-driven structure a mod can point at its own `.nbt` must be `minecraft:jigsaw`.**
  Every other registered structure type (`desert_pyramid`, `igloo`, `swamp_hut`,
  `ocean_ruin`, ...) generates its pieces in Java, so its JSON has no template field at all
  -- checked against the vanilla files in
  `build/moddev/artifacts/neoforge-21.0.167-client-extra-aka-minecraft-resources.jar`, which
  is the place to read real 1.21 worldgen JSON shapes. Jigsaw with `size: 1` and a
  one-element `template_pool` is the single-piece case. Datapack dirs (all under
  `data/<ns>/`): `worldgen/structure`, `worldgen/template_pool`, `worldgen/structure_set`,
  and the template itself in `structure/`. `spawn_overrides` is a REQUIRED field of every
  structure JSON; `terrain_adaptation` is optional. Placement: with
  `"project_start_to_heightmap"` set, `JigsawPlacement.addPieces` moves the piece so
  `boundingBox.minY() + getGroundLevelDelta() == firstFreeHeight`, and
  `getGroundLevelDelta()` is **1** and is not overridden by `SinglePoolElement` -- so
  template `y=0` lands on the topmost solid block (replacing the surface block) and `y=1` is
  the first block standing proud of the ground. (`verified: 2026-08-13`)
- **A hand-written structure NBT that omits air leaves the terrain alone.**
  `StructureTemplate.placeInWorld` only ever touches positions listed in `blocks`, so a
  template with no air entries stamps its shape onto the world instead of clearing a box
  first. (Vanilla-saved structures DO list their air, which is why
  `legacy_single_pool_element` and its `BlockIgnoreProcessor.STRUCTURE_AND_AIR` exist.)
  `assets-src/structures.py` writes layered templates this way. (`verified: 2026-08-13`)
- **`ProjectileImpactEvent` cancel does not stop the projectile.** `ThrowableProjectile#tick`
  reads `if (hit != MISS && !EventHooks.onProjectileImpact(this, hit)) hitTargetOrDeflectSelf(hit);`
  -- cancelling suppresses `onHit` (and with it the pearl's teleport, fall damage and
  endermite roll) but leaves the entity alive and still moving, so it must be `discard()`ed
  explicitly or it fires the event again on the next block. Event class is
  `net.neoforged.neoforge.event.entity.ProjectileImpactEvent`. Also: there is no bare
  `PlayerTickEvent` to subscribe to in 1.21 -- it is an abstract `Pre`/`Post` pair under
  `net.neoforged.neoforge.event.tick`. (`verified: 2026-08-13`)
- **Data attachments are a real registry**, `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`, so
  `DeferredRegister.create(...)` + registering `AttachmentType.builder(...).build()` is the
  pattern. Three traps: `getData` *stores the default in the holder* and so can never mean
  "absent" -- use `getExistingData` for optional state; `copyOnDeath()` throws unless a
  serializer was set first, and without it a serialised attachment survives relogs but NOT
  respawns; and `serialize(Codec)` is the easy route (`BlockPos.CODEC` exists).
  (`verified: 2026-08-13`)
- **Cross-dimension teleport in 1.21 is `DimensionTransition`, not `PortalInfo`.** The
  command-equivalent entry point is
  `ServerPlayer#teleportTo(ServerLevel, x, y, z, Set<RelativeMovement>, yRot, xRot)` -- it
  adds the `TicketType.POST_TELEPORT` chunk ticket, then delegates to
  `teleportTo(ServerLevel, x, y, z, yaw, pitch)`, which routes a cross-dimension move through
  `changeDimension(new DimensionTransition(...))`. The plain `teleportTo(double, double,
  double)` cannot change dimension at all. Generate the destination chunk yourself
  (`level.getChunk(cx, cz)`) before reading blocks there: an ungenerated chunk reads as air
  all the way down, which looks exactly like a safe landing spot. (`verified: 2026-08-13`)
- **`TamableAnimal` traps.** (a) `mobInteract` is **public** by the time it reaches
  `Animal`, so an override in any `TamableAnimal` subclass must be `public` too --
  `protected` (what `PathfinderMob` subclasses in this repo use) is a compile error, not a
  warning. (b) `OwnerHurtByTargetGoal` and `OwnerHurtTargetGoal` both open with
  `if (isTame() && !isOrderedToSit())`, so vanilla's sit/stay cannot back a "stays put but
  still fights" mode -- that needs its own flag. (c) `OwnableEntity#getOwner` resolves
  through `level().getPlayerByUUID`, so it is always null for a `GameTestHelper` mock
  player; assert on `getOwnerUUID()` instead. (`verified: 2026-08-13`)
- **`Mob.serverAiStep` runs `goalSelector.tick()` -- and therefore every non-running goal's
  `canUse()` -- only on alternating ticks** (`(tickCount + getId()) % 2`); the other tick
  gets `tickRunningGoals(false)`. Anything budgeting work per `canUse` call is really
  budgeting per *two* ticks. (`verified: 2026-08-13`)
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
- **Container transfer: `HopperBlockEntity.getContainerAt(Level, BlockPos)` and
  `addItem(@Nullable Container source, Container dest, ItemStack, @Nullable Direction)` are
  the public entry points** (the first handles double chests via `ChestBlock.getContainer`).
  `tryMoveInItem` hands the destination the *same* `ItemStack` object when the target slot is
  empty and returns `ItemStack.EMPTY`, so the caller MUST write the returned remainder back
  into its own slot or the two containers alias one stack. (`verified: 2026-08-13`)
- **A chunk generated by a bare `level.getChunk(cx, cz)` never loads its worldgen entities.**
  The chunk reaches `ChunkStatus.FULL`, but the entities `spawnOriginalMobs` handed to the
  `WorldGenRegion` live in the `ProtoChunk` and are only handed to the level by
  `LevelChunk.runPostLoad()`, which fires on the `FullChunkStatus` promotion a *ticket*
  drives -- and they are not written by `ChunkSerializer` either (entities have their own
  storage since 1.17), so they are silently lost on save. Any probe or tool that generates
  chunks in order to look at what spawned must take a ticket:
  `ServerLevel#setChunkForced(x, z, true)` is the one-liner. Cost an M7 probe run: the queen
  logged as seated and then could not be found, on a chunk that had already been written to
  disk without her. (`verified: 2026-08-13`)
- **1.21 boss-bar and mob-signature gotchas.** `ServerBossEvent(Component, BossBarColor,
  BossBarOverlay)` lives in `net.minecraft.server.level`, its enums in
  `net.minecraft.world.BossEvent` (which is NOT in the seeded `reference/` -- extract
  `net/minecraft/world/BossEvent.java`), and the plumbing is `Entity#startSeenByPlayer` /
  `#stopSeenByPlayer` overridden to `addPlayer`/`removePlayer` (copy `WitherBoss`). Two
  signatures that training memory gets wrong and the compiler catches:
  `Mob#canBeLeashed()` takes **no** `Player` in 1.21, and `LivingEntity#getVoicePitch()` is
  **public**, so a `protected` override is a "weaker access privileges" error. `Mob` also does
  **not** persist its `restrictTo` restriction -- save the centre yourself and re-apply it in
  `readAdditionalSaveData` or a relog frees the mob. (`verified: 2026-08-13`)
- **Vanilla's owner-aware goals are `TamableAnimal`-only.** `OwnerHurtByTargetGoal`,
  `OwnerHurtTargetGoal` and `FollowOwnerGoal` all take a `TamableAnimal` in their constructors,
  so a "temporarily allied" mob that is not one has to reimplement them. The vanilla shape
  worth copying is the timestamp guard: read `getLastHurtByMob()` / `getLastHurtMob()` together
  with `getLastHurtByMobTimestamp()` / `getLastHurtMobTimestamp()` and refuse to re-adopt a
  grudge whose timestamp you have already seen. (`verified: 2026-08-13`)
- **`RecipeProvider`'s hook is `protected void buildRecipes(RecipeOutput)`** (the
  `(RecipeOutput, HolderLookup.Provider)` overload just delegates), and its constructor takes
  the `CompletableFuture<HolderLookup.Provider>` from `GatherDataEvent#getLookupProvider`,
  not a resolved provider. Output folders are `data/<ns>/recipe/` (singular, like
  `loot_table`) and `data/<ns>/advancement/recipes/<category>/`. (`verified: 2026-08-13`)

## Workflow

- Conventional commits, one per logical step. Commit as you go; never leave finished work
  uncommitted. Never tag or push unless explicitly asked.
- Work milestone by milestone (M0-M8 in the spec); don't start M(n+1) with M(n) broken.
