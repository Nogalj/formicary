package com.nogal.formicary.worldgen;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.BROOD_COMB_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.EGG_CLUSTER_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FLOOR_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FUNGAL_BLOOM_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FUNGAL_CARPET_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RESIN_BLOCK_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RESIN_WEEP_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ROOMY_CLEARANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ROYAL_COMB_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SPAWN_FLOOR_ATTEMPTS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SPAWN_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_BROOD_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_DAIS_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_EGG_CLUSTER_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_RESIN_BLOCK_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_RESIN_WEEP_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_ROYAL_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierIndex;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMaxY;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMinY;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * The Formicary dimension's terrain: solid soil, hollowed into an anthill.
 *
 * <p>Shape lives in {@link ColonyNoise} and every constant in
 * {@link ColonyGeneratorTunables}; this class is the plumbing that turns those pure
 * functions into chunks, plus decoration and chunk-generation spawning.
 *
 * <p>Registered as a {@code MapCodec} in {@link ModWorldgen} so the data-driven
 * {@code data/formicary/dimension/formicary.json} can name it as
 * {@code "type": "formicary:colony"}. Without that registry bridge the dimension JSON
 * fails to parse and the server never starts.
 */
public class ColonyChunkGenerator extends ChunkGenerator {

    public static final MapCodec<ColonyChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
                    .apply(instance, ColonyChunkGenerator::new));

    /**
     * Names under which the world seed is forked. {@code RandomState} hands out a
     * {@link PositionalRandomFactory} per name, derived from the level seed -- which is how
     * a custom generator gets seeded worldgen randomness without needing the raw seed (the
     * abstract methods never receive one).
     */
    private static final ResourceLocation SHAPE_SEED =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "colony_shape");
    private static final ResourceLocation DECORATION_SEED =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "colony_decoration");

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Column footprint the decorator samples: the chunk plus a one-block skirt. */
    private static final int DECOR_SPAN = 18;

    /**
     * Keyed by identity ({@code RandomState} does not override equals), and in practice
     * holds exactly one entry -- the level's own state. Concurrent because chunk filling
     * runs on the background worker pool.
     */
    private final Map<RandomState, ColonyNoise> noiseByRandomState = new ConcurrentHashMap<>();

    /** Resolved once at construction, which happens at world load, long after registration. */
    private final BlockState[] fabricStates;

    /** The M5 exit block, patched into the ceiling cap. Resolved with the fabric states. */
    private final BlockState membraneState;

    /** The M7 throne chamber's plinth block. */
    private final BlockState daisState;

    public ColonyChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
        this.membraneState = ModBlocks.DAYLIGHT_MEMBRANE.get().defaultBlockState();
        this.daisState = ModBlocks.RESIN_BLOCK.get().defaultBlockState();
        this.fabricStates = new BlockState[5];
        this.fabricStates[ColonyNoise.FABRIC_PACKED_SOIL] = ModBlocks.PACKED_SOIL.get().defaultBlockState();
        this.fabricStates[ColonyNoise.FABRIC_AMBER_EARTH] = ModBlocks.AMBER_EARTH.get().defaultBlockState();
        this.fabricStates[ColonyNoise.FABRIC_DEEP_LOAM] = ModBlocks.DEEP_LOAM.get().defaultBlockState();
        this.fabricStates[ColonyNoise.FABRIC_HARDENED_SOIL] = ModBlocks.HARDENED_SOIL.get().defaultBlockState();
        this.fabricStates[ColonyNoise.FABRIC_RESIN_BLOCK] = ModBlocks.RESIN_BLOCK.get().defaultBlockState();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private ColonyNoise noise(RandomState randomState) {
        return this.noiseByRandomState.computeIfAbsent(randomState,
                state -> new ColonyNoise(state.getOrCreateRandomFactory(SHAPE_SEED)));
    }

    // ------------------------------------------------------------------
    // Terrain
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(
                Util.wrapThreadWithTaskName("formicary_fill_colony", () -> fill(randomState, chunk)),
                Util.backgroundExecutor());
    }

    private ChunkAccess fill(RandomState randomState, ChunkAccess chunk) {
        ColonyNoise noise = noise(randomState);
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        // Clamp to the chunk's own range as well as ours: if a datapack ever retunes the
        // dimension_type's min_y/height out from under the generator, write nothing outside it.
        int bottom = Math.max(MIN_Y, chunk.getMinBuildHeight());
        int top = Math.min(MIN_Y + HEIGHT, chunk.getMaxBuildHeight());

        ColonyNoise.Shaft[] chunkShafts = noise.shaftsNear(minX, minZ);
        ColonyNoise.Throne[] chunkThrones = noise.thronesNear(minX, minZ);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        Set<LevelChunkSection> held = new HashSet<>();
        for (int index = chunk.getSectionIndex(top - 1); index >= chunk.getSectionIndex(bottom); index--) {
            LevelChunkSection section = chunk.getSection(index);
            section.acquire();
            held.add(section);
        }
        try {
            for (int localX = 0; localX < 16; localX++) {
                int x = minX + localX;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int z = minZ + localZ;
                    ColonyNoise.Shaft[] columnShafts = noise.shaftsForColumn(chunkShafts, x, z);
                    ColonyNoise.Throne[] columnThrones = noise.thronesForColumn(chunkThrones, x, z);
                    int sectionIndex = -1;
                    LevelChunkSection section = null;
                    for (int y = top - 1; y >= bottom; y--) {
                        if (noise.isAir(columnShafts, columnThrones, x, y, z)) {
                            continue;
                        }
                        int index = chunk.getSectionIndex(y);
                        if (index != sectionIndex) {
                            sectionIndex = index;
                            section = chunk.getSection(index);
                        }
                        BlockState state;
                        if (noise.isDaylightMembrane(columnShafts, columnThrones, x, y, z)) {
                            state = this.membraneState;
                        } else if (noise.isThroneDais(columnThrones, x, y, z)) {
                            // The queen's plinth: resin, so the dais reads as built rather
                            // than as a lump the carve happened to leave behind.
                            state = this.daisState;
                        } else {
                            state = this.fabricStates[noise.fabricKind(x, y, z)];
                        }
                        section.setBlockState(localX, y & 15, localZ, state, false);
                        oceanFloor.update(localX, y, localZ, state);
                        worldSurface.update(localX, y, localZ, state);
                    }
                }
            }
        } finally {
            for (LevelChunkSection section : held) {
                section.release();
            }
        }
        return chunk;
    }

    // ------------------------------------------------------------------
    // Decoration
    // ------------------------------------------------------------------

    /**
     * Floor and wall dressing, done here rather than as biome features.
     *
     * <p>Placed features are aimed at a heightmap surface -- one decorated Y per column --
     * which in a dimension that is solid rock with a hollow inside would only ever dress the
     * ceiling cap. Every carved floor and wall in the colony needs dressing, at any depth,
     * so the generator (which already knows exactly where the carve is) does it directly.
     * The biome JSONs therefore ship an empty {@code features} list, and the whole look of a
     * tier stays adjustable from {@link ColonyGeneratorTunables} alone.
     */
    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState,
            ChunkAccess chunk) {
        ColonyNoise noise = noise(randomState);
        PositionalRandomFactory randomFactory = randomState.getOrCreateRandomFactory(DECORATION_SEED);
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int bottom = Math.max(FLOOR_TOP, chunk.getMinBuildHeight());
        int top = Math.min(CEILING_BOTTOM, chunk.getMaxBuildHeight());
        if (top <= bottom) {
            return;
        }

        byte[] airRun = airRunLengths(noise, chunk, minX, minZ, bottom, top);
        ColonyNoise.Throne[] chunkThrones = noise.thronesNear(minX, minZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                ColonyNoise.Throne[] columnThrones = noise.thronesForColumn(chunkThrones, x, z);
                for (int y = bottom; y < top; y++) {
                    int tier = tierIndex(y);
                    boolean throne = noise.isInThroneRoom(columnThrones, x, y, z);
                    boolean here = run(airRun, localX + 1, localZ + 1, y, bottom, top) > 0;
                    if (here) {
                        decorateFloorSpace(chunk, cursor, randomFactory, airRun, localX, localZ, x, y, z, tier,
                                throne, bottom, top);
                    } else {
                        decorateSurface(chunk, cursor, randomFactory, airRun, localX, localZ, x, y, z, tier,
                                throne, bottom, top);
                    }
                }
            }
        }
    }

    /**
     * For every column in an 18x18 footprint, the height of the contiguous air run each Y
     * belongs to (0 when solid), capped at {@link Byte#MAX_VALUE}.
     *
     * <p>The one-block skirt is what lets wall decoration look sideways without reading a
     * neighbouring chunk. Inside the chunk the answer comes from the blocks already written
     * by {@link #fill}; on the skirt it is recomputed from {@link ColonyNoise}, which is a
     * pure function of world position and so agrees with whatever that chunk will contain.
     */
    private byte[] airRunLengths(ColonyNoise noise, ChunkAccess chunk, int minX, int minZ, int bottom, int top) {
        int height = top - bottom;
        byte[] runs = new byte[DECOR_SPAN * DECOR_SPAN * height];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean[] column = new boolean[height];

        for (int ix = 0; ix < DECOR_SPAN; ix++) {
            int x = minX + ix - 1;
            boolean insideX = ix >= 1 && ix <= 16;
            for (int iz = 0; iz < DECOR_SPAN; iz++) {
                int z = minZ + iz - 1;
                boolean inside = insideX && iz >= 1 && iz <= 16;
                if (inside) {
                    for (int y = bottom; y < top; y++) {
                        column[y - bottom] = chunk.getBlockState(cursor.set(x, y, z)).isAir();
                    }
                } else {
                    int chunkX = x - Math.floorMod(x, 16);
                    int chunkZ = z - Math.floorMod(z, 16);
                    ColonyNoise.Shaft[] shafts = noise.shaftsForColumn(noise.shaftsNear(chunkX, chunkZ), x, z);
                    ColonyNoise.Throne[] thrones = noise.thronesForColumn(noise.thronesNear(chunkX, chunkZ), x, z);
                    for (int y = bottom; y < top; y++) {
                        column[y - bottom] = noise.isAir(shafts, thrones, x, y, z);
                    }
                }

                int base = (ix * DECOR_SPAN + iz) * height;
                int y = 0;
                while (y < height) {
                    if (!column[y]) {
                        y++;
                        continue;
                    }
                    int end = y;
                    while (end < height && column[end]) {
                        end++;
                    }
                    byte length = (byte) Math.min(end - y, Byte.MAX_VALUE);
                    for (int fill = y; fill < end; fill++) {
                        runs[base + fill] = length;
                    }
                    y = end;
                }
            }
        }
        return runs;
    }

    private static int run(byte[] runs, int ix, int iz, int y, int bottom, int top) {
        if (ix < 0 || iz < 0 || ix >= DECOR_SPAN || iz >= DECOR_SPAN || y < bottom || y >= top) {
            return 0;
        }
        return runs[(ix * DECOR_SPAN + iz) * (top - bottom) + (y - bottom)];
    }

    /** Air standing on a floor: fungus, carpet, egg clusters. */
    private void decorateFloorSpace(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            PositionalRandomFactory randomFactory, byte[] airRun, int localX, int localZ,
            int x, int y, int z, int tier, boolean throne, int bottom, int top) {
        boolean solidBelow = run(airRun, localX + 1, localZ + 1, y - 1, bottom, top) == 0;
        if (!solidBelow || run(airRun, localX + 1, localZ + 1, y + 1, bottom, top) == 0) {
            return;
        }
        boolean roomy = run(airRun, localX + 1, localZ + 1, y, bottom, top) >= ROOMY_CLEARANCE + 1;

        double roll = randomFactory.at(x, y, z).nextDouble();
        if (throne) {
            // The queen's floor: egg clusters, nothing fungal (the Royal Depths grow none).
            if (roomy && roll < THRONE_EGG_CLUSTER_CHANCE) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.EGG_CLUSTER.get().defaultBlockState(), false);
            }
            return;
        }
        double cumulative = FUNGAL_BLOOM_CHANCE_BY_TIER[tier];
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.FUNGAL_BLOOM.get().defaultBlockState(), false);
            return;
        }
        cumulative += FUNGAL_CARPET_CHANCE_BY_TIER[tier];
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.FUNGAL_CARPET.get().defaultBlockState(), false);
            return;
        }
        if (roomy) {
            cumulative += EGG_CLUSTER_CHANCE_BY_TIER[tier];
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.EGG_CLUSTER.get().defaultBlockState(), false);
            }
        }
    }

    /** Solid fabric with air against it: comb lining, resin weeps, amber veins. */
    private void decorateSurface(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            PositionalRandomFactory randomFactory, byte[] airRun, int localX, int localZ,
            int x, int y, int z, int tier, boolean throne, int bottom, int top) {
        int above = run(airRun, localX + 1, localZ + 1, y + 1, bottom, top);
        int below = run(airRun, localX + 1, localZ + 1, y - 1, bottom, top);
        int north = run(airRun, localX + 1, localZ, y, bottom, top);
        int south = run(airRun, localX + 1, localZ + 2, y, bottom, top);
        int west = run(airRun, localX, localZ + 1, y, bottom, top);
        int east = run(airRun, localX + 2, localZ + 1, y, bottom, top);
        int deepest = Math.max(Math.max(above, below), Math.max(Math.max(north, south), Math.max(west, east)));
        if (deepest == 0) {
            return;
        }
        boolean roomy = deepest >= ROOMY_CLEARANCE + 2;
        boolean sideOrCeiling = below > 0 || north > 0 || south > 0 || west > 0 || east > 0;

        double roll = randomFactory.at(x, y, z).nextDouble();
        if (throne) {
            decorateThroneSurface(chunk, cursor, x, y, z, roll, sideOrCeiling);
            return;
        }
        double cumulative = 0.0;
        if (roomy) {
            cumulative += ROYAL_COMB_CHANCE_BY_TIER[tier];
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.ROYAL_COMB.get().defaultBlockState(), false);
                return;
            }
            cumulative += BROOD_COMB_CHANCE_BY_TIER[tier];
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.BROOD_COMB.get().defaultBlockState(), false);
                return;
            }
        }
        if (sideOrCeiling) {
            cumulative += RESIN_WEEP_CHANCE_BY_TIER[tier];
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.RESIN_WEEP.get().defaultBlockState(), false);
                return;
            }
            cumulative += RESIN_BLOCK_CHANCE_BY_TIER[tier];
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.RESIN_BLOCK.get().defaultBlockState(), false);
            }
        }
    }

    /**
     * The throne chamber's own dressing (M7): comb and resin, laid on thick.
     *
     * <p>The per-tier chances are skipped entirely rather than scaled, because the Royal
     * Depths' Royal Comb chance is 0.004 -- a multiplier big enough to matter here would be
     * absurd everywhere else. This is where the block belongs: the spec makes Royal Comb the
     * non-boss source of Royal Jelly, and the queen's chamber is the room it names.
     */
    private void decorateThroneSurface(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            int x, int y, int z, double roll, boolean sideOrCeiling) {
        double cumulative = THRONE_ROYAL_COMB_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.ROYAL_COMB.get().defaultBlockState(), false);
            return;
        }
        cumulative += THRONE_BROOD_COMB_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.BROOD_COMB.get().defaultBlockState(), false);
            return;
        }
        cumulative += THRONE_RESIN_BLOCK_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.RESIN_BLOCK.get().defaultBlockState(), false);
            return;
        }
        if (sideOrCeiling) {
            cumulative += THRONE_RESIN_WEEP_CHANCE;
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.RESIN_WEEP.get().defaultBlockState(), false);
            }
        }
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Seeds each tier from its own biome's spawn list as chunks generate.
     *
     * <p>Vanilla's {@code NaturalSpawner.spawnMobsForChunkGeneration} is deliberately not
     * used. Two things about it are wrong here, both verified in the 1.21 sources:
     * {@code NoiseBasedChunkGenerator} feeds it the biome at {@code maxBuildHeight - 1},
     * which in a four-band dimension is always the Upper Galleries; and its
     * {@code getTopNonCollidingPos} walks down from the heightmap to the <i>first</i> air
     * pocket under the ceiling (the {@code hasCeiling} branch), so every spawn it makes
     * lands in the topmost gallery no matter which biome it was handed. Running it four
     * times would populate the top tier four times over and leave the Nurseries and Royal
     * Depths empty. This does the same job per band instead, with the same placement and
     * event checks vanilla applies.
     */
    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        ChunkPos chunkPos = level.getCenter();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());

        for (int tier = 0; tier < TIER_COUNT; tier++) {
            int bandMin = Math.max(tierMinY(tier), level.getMinBuildHeight() + 1);
            int bandMax = Math.min(tierMaxY(tier), level.getMaxBuildHeight() - 2);
            if (bandMax <= bandMin) {
                continue;
            }
            Holder<Biome> biome = level.getBiome(
                    new BlockPos(chunkPos.getMiddleBlockX(), (bandMin + bandMax) / 2, chunkPos.getMiddleBlockZ()));
            spawnTier(level, biome, chunkPos, random, bandMin, bandMax);
        }
    }

    private void spawnTier(WorldGenRegion level, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random,
            int bandMin, int bandMax) {
        MobSpawnSettings settings = biome.value().getMobSettings();
        WeightedRandomList<MobSpawnSettings.SpawnerData> spawners = settings.getMobs(MobCategory.CREATURE);
        if (spawners.isEmpty()) {
            return;
        }
        int originX = chunkPos.getMinBlockX();
        int originZ = chunkPos.getMinBlockZ();

        while (random.nextFloat() < settings.getCreatureProbability()) {
            Optional<MobSpawnSettings.SpawnerData> picked = spawners.getRandom(random);
            if (picked.isEmpty()) {
                continue;
            }
            MobSpawnSettings.SpawnerData data = picked.get();
            int groupSize = data.minCount + random.nextInt(1 + data.maxCount - data.minCount);
            // One-slot carrier for the group data vanilla threads through a spawn group.
            // Deliberately a local, not a field: spawnOriginalMobs runs on the chunk worker
            // pool and one generator instance serves every chunk in the dimension at once.
            SpawnGroupData[] groupData = new SpawnGroupData[1];
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);

            for (int member = 0; member < groupSize; member++) {
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < SPAWN_FLOOR_ATTEMPTS; attempt++) {
                    int y = findFloor(level, x, z, bandMin, bandMax, random);
                    if (y > Integer.MIN_VALUE) {
                        placed = trySpawn(level, data, x, y, z, chunkPos, groupData, random);
                    }
                    x = Mth.clamp(x + random.nextInt(7) - 3, originX, originX + 15);
                    z = Mth.clamp(z + random.nextInt(7) - 3, originZ, originZ + 15);
                }
            }
        }
    }

    /**
     * Finds a floor inside the given Y band: air at {@code y} and {@code y+1} with something
     * solid underneath. Scans down from a random height in the band so a chunk does not
     * always seed its mobs on the band's topmost ledge.
     */
    private static int findFloor(WorldGenRegion level, int x, int z, int bandMin, int bandMax, RandomSource random) {
        int start = bandMin + random.nextInt(bandMax - bandMin);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? start : bandMax - 1;
            for (int y = from; y > bandMin; y--) {
                if (level.getBlockState(cursor.set(x, y, z)).isAir()
                        && level.getBlockState(cursor.set(x, y + 1, z)).isAir()
                        && !level.getBlockState(cursor.set(x, y - 1, z)).isAir()) {
                    return y;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean trySpawn(WorldGenRegion level, MobSpawnSettings.SpawnerData data, int x, int y, int z,
            ChunkPos chunkPos, SpawnGroupData[] groupData, RandomSource random) {
        BlockPos pos = SpawnPlacements.getPlacementType(data.type).adjustSpawnPosition(level, new BlockPos(x, y, z));
        if (!data.type.canSummon() || !SpawnPlacements.isSpawnPositionOk(data.type, level, pos)) {
            return false;
        }
        float width = data.type.getWidth();
        double spawnX = Mth.clamp(x, chunkPos.getMinBlockX() + width, chunkPos.getMinBlockX() + 16.0 - width);
        double spawnZ = Mth.clamp(z, chunkPos.getMinBlockZ() + width, chunkPos.getMinBlockZ() + 16.0 - width);
        if (!level.noCollision(data.type.getSpawnAABB(spawnX, pos.getY(), spawnZ))
                || !SpawnPlacements.checkSpawnRules(data.type, level, MobSpawnType.CHUNK_GENERATION,
                        BlockPos.containing(spawnX, pos.getY(), spawnZ), level.getRandom())) {
            return false;
        }

        Entity entity;
        try {
            entity = data.type.create(level.getLevel());
        } catch (Exception exception) {
            Formicary.LOGGER.warn("Formicary: failed to create {} for chunk-generation spawn",
                    data.type.getDescriptionId(), exception);
            return false;
        }
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        mob.moveTo(spawnX, pos.getY(), spawnZ, random.nextFloat() * 360.0F, 0.0F);
        if (!net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, level, MobSpawnType.CHUNK_GENERATION)) {
            return false;
        }
        groupData[0] = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.CHUNK_GENERATION, groupData[0]);
        level.addFreshEntityWithPassengers(mob);
        return true;
    }

    // ------------------------------------------------------------------
    // Remaining ChunkGenerator contract
    // ------------------------------------------------------------------

    /** No carvers: this generator does its own carving in {@link #fill}. */
    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    /** There is no water anywhere in the colony; the floor cap doubles as "sea level". */
    @Override
    public int getSeaLevel() {
        return MIN_Y;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return Mth.clamp(SPAWN_HEIGHT, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    /** The top of the world is always the solid ceiling cap, so the surface is the roof. */
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return Math.min(MIN_Y + HEIGHT, level.getMaxBuildHeight());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        ColonyNoise noise = noise(random);
        int chunkX = x - Math.floorMod(x, 16);
        int chunkZ = z - Math.floorMod(z, 16);
        ColonyNoise.Shaft[] shafts = noise.shaftsForColumn(noise.shaftsNear(chunkX, chunkZ), x, z);
        ColonyNoise.Throne[] thrones = noise.thronesForColumn(noise.thronesNear(chunkX, chunkZ), x, z);
        int bottom = height.getMinBuildHeight();
        BlockState[] column = new BlockState[height.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = bottom + i;
            if (noise.isAir(shafts, thrones, x, y, z)) {
                column[i] = AIR;
            } else if (noise.isDaylightMembrane(shafts, thrones, x, y, z)) {
                column[i] = this.membraneState;
            } else if (noise.isThroneDais(thrones, x, y, z)) {
                column[i] = this.daisState;
            } else {
                column[i] = this.fabricStates[noise.fabricKind(x, y, z)];
            }
        }
        return new NoiseColumn(bottom, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        int tier = tierIndex(pos.getY());
        ColonyNoise noise = noise(random);
        info.add("Formicary tier " + tier + " (" + tierMinY(tier) + ".." + (tierMaxY(tier) - 1) + ")");
        info.add(String.format("tunnel a/b %.3f / %.3f", noise.probeTunnelA(pos.getX(), pos.getY(), pos.getZ()),
                noise.probeTunnelB(pos.getX(), pos.getY(), pos.getZ())));
        info.add(String.format("chamber s/l %.3f / %.3f",
                noise.probeChamberSmall(pos.getX(), pos.getY(), pos.getZ()),
                noise.probeChamberLarge(pos.getX(), pos.getY(), pos.getZ())));
    }
}
