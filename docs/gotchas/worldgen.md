# Formicary gotchas -- worldgen (dimension, chunk generator, structures, spawning)

Moved verbatim from CLAUDE.md "Banked rules" by the 2026-08-14 /tidy-claude-md
restructure, re-flowed 2026-08-15 to take in the play-test round 1 rules; each entry
keeps its original `verified:` date. Routed by the symptom index in CLAUDE.md.

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
- **`ProtoChunk#getBlockState` masks X/Z with `& 15`.** Reading a neighbour one block
  outside the chunk silently wraps to the opposite edge of the *same* chunk instead of
  failing. Anything in worldgen that looks sideways must either stay inside the chunk or
  recompute from a position-pure function. (`verified: 2026-08-13`)
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
- **A structure template can carry ENTITIES, and a jigsaw piece always places them.**
  `SinglePoolElement.getSettings` calls `setIgnoreEntities(false)` and
  `setFinalizeEntities(true)` unconditionally, so template entities are spawned the instant
  the piece generates and get `finalizeSpawn(..., MobSpawnType.STRUCTURE, null)`. NBT layout
  (read out of `StructureTemplate.load`, not recalled): `entities` LIST&lt;COMPOUND&gt; of
  `{pos: LIST<DOUBLE>[3], blockPos: LIST<INT>[3], nbt: COMPOUND}`, where `nbt` needs only
  `id` -- `addEntitiesToWorld` overwrites `Pos` itself before calling
  `EntityType.create(CompoundTag, Level)`, but `Motion` and `Rotation` are read from what
  you wrote. **`blockPos` is the silent one:** it is tested against
  `placementIn.getBoundingBox()` and an entity outside the template footprint is dropped
  with no log line. `assets-src/structures.py` writes them. (`verified: 2026-08-15`)
- **`MobCategory.CREATURE` barely spawns at runtime, and in a dimension whose mobs never
  despawn it does not spawn at all.** Two independent gates, both verified in the 1.21
  sources: `CREATURE.isPersistent()` is `true` and `NaturalSpawner.spawnForChunk` skips a
  persistent category unless `forcedDespawn` is set, which `ServerChunkCache.tickChunks`
  only passes on `gameTime % 400 == 0`; and `SpawnState.canSpawnForCategory` caps CREATUREs
  at `10 * spawnableChunkCount / 289` (about ten per player), counting every mob that is not
  `isPersistenceRequired()`. Any mob overriding `removeWhenFarAway` to `false` therefore
  occupies that cap forever. Design consequence for this mod: the colony's population is
  whatever `spawnOriginalMobs` seeds, permanently. (`verified: 2026-08-15`)
- **A headless tool on the mod's classpath must not touch a class whose static initialiser
  reaches `BuiltInRegistries`.** `NoiseProbe` runs without `Bootstrap.bootStrap()`, so
  merely *referencing* a static method on `ColonyChunkGenerator` loads its superclass
  `ChunkGenerator`, whose `<clinit>` builds a registry codec and dies with
  `IllegalArgumentException: Not bootstrapped`. Shared arithmetic the probe needs belongs in
  a registry-free class -- `ColonyGeneratorTunables` is the one here. (`verified: 2026-08-15`)
- **Block-light propagation is asynchronous: a same-tick read after `setBlockAndUpdate` sees
  the STALE light level.** Placing glowstone and immediately calling
  `getBrightness(LightLayer.BLOCK, pos)` in the same tick phase returns 0, which makes any
  light-gated logic (spawn predicates here) look broken when it is fine. Read light on a
  later tick. Cost one full probe-run of confusion during the ender-ant spawn spike.
  (`verified: 2026-08-18`)
