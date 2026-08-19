package com.nogal.formicary.worldgen;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.BROOD_COMB_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_SOLDIERS_MAX_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_SOLDIERS_MIN_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_WORKERS_MAX_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_WORKERS_MIN_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FLOOR_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_FUNGAL_BLOOM_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_FUNGAL_CARPET_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_SPORE_CROP_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_BROOD_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_PROVISION_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_BROOD_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_EGG_CLUSTER_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_LARVAE_MAX;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_LARVAE_MIN;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_ROYAL_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RESIN_BLOCK_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ROOMY_CLEARANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ROYAL_COMB_CHANCE_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SPAWN_CLUSTERS_PER_CHUNK_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SPAWN_FLOOR_ATTEMPTS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SPAWN_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.between;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.rollCount;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_BROOD_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_DAIS_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_EGG_CLUSTER_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_RESIN_BLOCK_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_ROYAL_COMB_CHANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierIndex;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMaxY;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMinY;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.FungalSporeCropBlock;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.LarvaEntity;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.QueenAntEntity;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
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

    /**
     * The shape functions for a level, cached per {@link RandomState}.
     *
     * <p>Public so the dev tooling can ask the live world where its chambers are without
     * re-deriving the {@link #SHAPE_SEED} fork itself -- a second call to
     * {@code getOrCreateRandomFactory} with a mistyped name would produce a perfectly
     * plausible set of coordinates for a colony that does not exist.
     */
    public ColonyNoise noise(RandomState randomState) {
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
        ColonyNoise.Nursery[] chunkNurseries = noise.nurseriesNear(minX, minZ);
        ColonyNoise.Garden[] chunkGardens = noise.gardensNear(minX, minZ);
        ColonyNoise.Larder[] chunkLarders = noise.lardersNear(minX, minZ);
        // Resolved once per chunk, like the shafts: nine seeded draws that answer "which
        // colonies are around here", after which the field itself is closed-form arithmetic.
        ColonyNoise.Colony[] chunkColonies = noise.coloniesNear(minX, minZ);
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
                    ColonyNoise.Nursery[] columnNurseries = noise.nurseriesForColumn(chunkNurseries, x, z);
                    ColonyNoise.Garden[] columnGardens = noise.gardensForColumn(chunkGardens, x, z);
                    ColonyNoise.Larder[] columnLarders = noise.lardersForColumn(chunkLarders, x, z);
                    double field = noise.colonyField(chunkColonies, x, z);
                    int sectionIndex = -1;
                    LevelChunkSection section = null;
                    for (int y = top - 1; y >= bottom; y--) {
                        if (noise.isAir(field, columnShafts, columnThrones, columnNurseries, columnGardens,
                                columnLarders, x, y, z)) {
                            continue;
                        }
                        int index = chunk.getSectionIndex(y);
                        if (index != sectionIndex) {
                            sectionIndex = index;
                            section = chunk.getSection(index);
                        }
                        BlockState state;
                        if (noise.isDaylightMembrane(field, columnShafts, columnThrones, columnNurseries,
                                columnGardens, columnLarders, x, y, z)) {
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
        ColonyNoise.Nursery[] chunkNurseries = noise.nurseriesNear(minX, minZ);
        ColonyNoise.Garden[] chunkGardens = noise.gardensNear(minX, minZ);
        ColonyNoise.Larder[] chunkLarders = noise.lardersNear(minX, minZ);
        ColonyNoise.Colony[] chunkColonies = noise.coloniesNear(minX, minZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                ColonyNoise.Throne[] columnThrones = noise.thronesForColumn(chunkThrones, x, z);
                ColonyNoise.Nursery[] columnNurseries = noise.nurseriesForColumn(chunkNurseries, x, z);
                ColonyNoise.Garden[] columnGardens = noise.gardensForColumn(chunkGardens, x, z);
                ColonyNoise.Larder[] columnLarders = noise.lardersForColumn(chunkLarders, x, z);
                double field = noise.colonyField(chunkColonies, x, z);
                for (int y = bottom; y < top; y++) {
                    int tier = tierIndex(y);
                    boolean throne = columnThrones.length > 0 && noise.isInThroneRoom(columnThrones, x, y, z);
                    boolean nursery = !throne && columnNurseries.length > 0
                            && noise.isInNurseryRoom(columnNurseries, x, y, z);
                    boolean garden = !throne && !nursery && columnGardens.length > 0
                            && noise.isInGardenRoom(columnGardens, x, y, z);
                    boolean larder = !throne && !nursery && !garden && columnLarders.length > 0
                            && noise.isInLarderRoom(columnLarders, x, y, z);
                    boolean here = run(airRun, localX + 1, localZ + 1, y, bottom, top) > 0;
                    if (here) {
                        decorateFloorSpace(chunk, cursor, randomFactory, airRun, localX, localZ, x, y, z,
                                throne, nursery, garden, bottom, top);
                    } else {
                        decorateSurface(noise, chunk, cursor, randomFactory, airRun, localX, localZ, x, y, z, tier,
                                field, throne, nursery, larder, bottom, top);
                    }
                }
            }
        }

        // D3: force the two guaranteed Provision Comb blocks per larder, independent of
        // decorateLarderSurface's probabilistic roll above -- see ColonyNoise.Larder's own
        // javadoc for why a per-column decorator cannot guarantee a room-wide count on its
        // own. Runs after the main pass so it always wins; the target position is always
        // inside the chamber's forced-solid shell (see LARDER_COMB_RADIUS's javadoc), so it
        // never punches a hole in the room's own air.
        for (ColonyNoise.Larder larder : chunkLarders) {
            // The colony-field gate has to be repeated here: this loop reads lardersNear,
            // which deliberately keeps its fixed 3x3 cell order, rather than the pruned
            // per-column arrays. Without it a gated-out larder would still stamp two
            // Provision Comb blocks into the middle of a wild tunnel.
            if (!larder.inColony()) {
                continue;
            }
            forceProvisionComb(chunk, cursor, minX, minZ, larder.combX1(), larder.combY(), larder.combZ1());
            forceProvisionComb(chunk, cursor, minX, minZ, larder.combX2(), larder.combY(), larder.combZ2());
        }
    }

    /** Writes Provision Comb at (x, y, z) if that position falls inside this 16x16 chunk. */
    private static void forceProvisionComb(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            int minX, int minZ, int x, int y, int z) {
        if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16
                || y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
            return;
        }
        chunk.setBlockState(cursor.set(x, y, z), ModBlocks.PROVISION_COMB.get().defaultBlockState(), false);
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
                    ColonyNoise.Nursery[] nurseries =
                            noise.nurseriesForColumn(noise.nurseriesNear(chunkX, chunkZ), x, z);
                    ColonyNoise.Garden[] gardens = noise.gardensForColumn(noise.gardensNear(chunkX, chunkZ), x, z);
                    ColonyNoise.Larder[] larders = noise.lardersForColumn(noise.lardersNear(chunkX, chunkZ), x, z);
                    double field = noise.colonyField(x, z);
                    for (int y = bottom; y < top; y++) {
                        column[y - bottom] =
                                noise.isAir(field, shafts, thrones, nurseries, gardens, larders, x, y, z);
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

    /**
     * Air standing on a floor: egg clusters, and a garden chamber's fungus.
     *
     * <p><b>Every branch here is now a chamber branch</b> (play-test round 2). Egg clusters
     * lost their ambient chance in round 1 and the fungal carpet/bloom pair lost theirs in
     * round 2, for the same reason both times: a block scattered across every roomy floor in
     * the dimension is scenery, and it makes the room that is supposed to be full of it read
     * exactly like the corridor outside. Brood is in brood rooms, fungus is in farms, comb is
     * in patches -- one rule, three applications.
     *
     * <p>A consequence worth stating: this method no longer takes the tier or the colony
     * field, because nothing ambient is left to scale by them. A chamber's own dressing was
     * never scaled -- the room only exists where {@code f} already cleared
     * {@code CHAMBER_ELIGIBILITY_MIN_F}, and thinning its interior on top of that would leave
     * the rooms out on the ring's edge conspicuously bare inside.
     */
    private void decorateFloorSpace(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            PositionalRandomFactory randomFactory, byte[] airRun, int localX, int localZ,
            int x, int y, int z,
            boolean throne, boolean nursery, boolean garden, int bottom, int top) {
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
        if (nursery) {
            // Deliberately not gated on `roomy`: the chamber's interior is 7 tall, but under
            // the curve of the dome the air run above a floor block is shorter than
            // ROOMY_CLEARANCE, which would have left the eggs in a disc in the middle of the
            // room and bare floor around the rim.
            if (roll < NURSERY_EGG_CLUSTER_CHANCE) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.EGG_CLUSTER.get().defaultBlockState(), false);
            }
            return;
        }
        if (garden) {
            // Same reasoning as the nursery branch above for not gating on `roomy`.
            decorateGardenFloor(chunk, cursor, roll, x, y, z);
        }
        // No ambient branch: outside a chamber a carved floor is bare soil (round 2).
    }

    /**
     * A Fungus Garden chamber's floor (D2): three mutually exclusive overlays, rarer
     * checked first -- the same convention {@link #decorateSurface} uses (royal comb
     * before brood comb, resin weep before resin block).
     *
     * <p>The crop is planted at {@link FungalSporeCropBlock#MAX_AGE} unconditionally.
     * {@code FungalSporeCropBlock.LIGHT_BY_AGE} is {@code {2, 4, 5, 7, 8}} -- only the
     * mature stage (age 4) reaches the crop's own {@code CropBlock#canSurvive} bar
     * ({@code hasSufficientLight}, {@code getRawBrightness(pos, 0) >= 8}, verified in
     * {@code reference/}), and this dimension has no skylight to make up the difference
     * at any lower age. Planted at any other age it would fail its own survival check the
     * moment anything re-evaluates it -- the banked skyAccess trap in another coat. At
     * max age {@code CropBlock#isRandomlyTicking} is false, so it never re-rolls growth
     * and only ever needs to keep clearing that one bar with its own self-emitted light.
     */
    private void decorateGardenFloor(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, double roll,
            int x, int y, int z) {
        double cumulative = GARDEN_SPORE_CROP_CHANCE;
        if (roll < cumulative) {
            BlockState matureCrop = ModBlocks.FUNGAL_SPORE_CROP.get().defaultBlockState()
                    .setValue(FungalSporeCropBlock.AGE, FungalSporeCropBlock.MAX_AGE);
            chunk.setBlockState(cursor.set(x, y, z), matureCrop, false);
            return;
        }
        cumulative += GARDEN_FUNGAL_BLOOM_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.FUNGAL_BLOOM.get().defaultBlockState(), false);
            return;
        }
        cumulative += GARDEN_FUNGAL_CARPET_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.FUNGAL_CARPET.get().defaultBlockState(), false);
        }
    }

    /**
     * Solid fabric with air against it: comb lining, resin weeps, amber veins.
     *
     * <p>Same colony-field rule as {@link #decorateFloorSpace}: the ambient per-tier chances
     * are scaled by {@code field}, the chamber branches are not.
     */
    private void decorateSurface(ColonyNoise noise, ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            PositionalRandomFactory randomFactory, byte[] airRun, int localX, int localZ,
            int x, int y, int z, int tier, double field,
            boolean throne, boolean nursery, boolean larder, int bottom, int top) {
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
        if (nursery) {
            decorateNurserySurface(chunk, cursor, x, y, z, roll, sideOrCeiling);
            return;
        }
        if (larder) {
            decorateLarderSurface(chunk, cursor, x, y, z, roll);
            return;
        }
        double cumulative = 0.0;
        // Comb is patch-gated (play-test round 1): outside a patch these two branches are
        // skipped entirely rather than scaled, which also hands the whole 0..1 roll to the
        // resin blocks below instead of squeezing them into the tail of a comb chance.
        if (roomy && noise.isCombPatch(field, x, y, z)) {
            cumulative += ROYAL_COMB_CHANCE_BY_TIER[tier] * field;
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.ROYAL_COMB.get().defaultBlockState(), false);
                return;
            }
            cumulative += BROOD_COMB_CHANCE_BY_TIER[tier] * field;
            if (roll < cumulative) {
                chunk.setBlockState(cursor.set(x, y, z), ModBlocks.BROOD_COMB.get().defaultBlockState(), false);
                return;
            }
        }
        if (sideOrCeiling) {
            cumulative += RESIN_BLOCK_CHANCE_BY_TIER[tier] * field;
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
        }
    }

    /**
     * A nursery chamber's dressing: comb on every wall, laid on far thicker than the tier
     * around it.
     *
     * <p>Flat chances rather than the tier's patch gate, on purpose. The patch field exists
     * to stop comb reading as speckle across a whole gallery; a 12-block room whose walls
     * are a third comb is already the patch, and running the gate inside it would have cut
     * roughly nine tenths of the rooms' comb away for no gain in legibility.
     */
    private void decorateNurserySurface(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            int x, int y, int z, double roll, boolean sideOrCeiling) {
        double cumulative = NURSERY_ROYAL_COMB_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.ROYAL_COMB.get().defaultBlockState(), false);
            return;
        }
        cumulative += NURSERY_BROOD_COMB_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.BROOD_COMB.get().defaultBlockState(), false);
        }
    }

    /**
     * A larder chamber's own dressing (D3): the probabilistic half of "comb-lined walls,
     * stocked with Provision Comb" -- rarer checked first, the same convention every other
     * chamber-surface decorator in this class uses. The GUARANTEED half (spec: "a
     * deterministic minimum of 2 per larder") is not here at all -- see
     * {@link #buildSurface}'s force-write pass after the main decoration loop, and
     * {@link ColonyNoise.Larder}'s own javadoc for why a per-column decorator cannot
     * guarantee a room-wide count on its own.
     */
    private void decorateLarderSurface(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            int x, int y, int z, double roll) {
        double cumulative = LARDER_PROVISION_COMB_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.PROVISION_COMB.get().defaultBlockState(), false);
            return;
        }
        cumulative += LARDER_BROOD_COMB_CHANCE;
        if (roll < cumulative) {
            chunk.setBlockState(cursor.set(x, y, z), ModBlocks.BROOD_COMB.get().defaultBlockState(), false);
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
     *
     * <h2>Why generation-time seeding is the whole population (play-test round 1)</h2>
     * The owner reported the colony felt empty, which raised the question of whether runtime
     * respawn was topping it up at all. Read out of the 1.21 sources, it effectively is not,
     * for two compounding reasons:
     * <ul>
     *   <li>{@code MobCategory.CREATURE.isPersistent()} is {@code true}, and
     *       {@code NaturalSpawner.spawnForChunk} skips a persistent category unless its
     *       {@code forcedDespawn} argument is set -- which {@code ServerChunkCache.tickChunks}
     *       only passes on {@code gameTime % 400 == 0}. So the whole dimension gets one
     *       spawn attempt per loaded chunk per 20 seconds at best.</li>
     *   <li>Worse, {@code SpawnState.canSpawnForCategory} caps CREATUREs at
     *       {@code 10 * spawnableChunkCount / 289} -- about ten for one player -- counting
     *       every non-persistent mob in the level. Every colony ant overrides
     *       {@code removeWhenFarAway} to {@code false} (deliberately: a resident that
     *       evaporates at 128 blocks is a bug), so they never despawn and the cap is
     *       permanently full. Runtime respawn therefore approximately never fires here.</li>
     * </ul>
     * The choice made rather than fought: leave it. Ants that vanish behind you would be a
     * far worse dimension than one whose population is fixed at generation, and the
     * generation-time density below is what the play-test complaint is actually about. The
     * biome spawn lists are kept (minus the larva, which is now chamber-only) so that the
     * trickle which does get through spawns the right castes in the right tiers.
     */
    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        ChunkPos chunkPos = level.getCenter();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());

        spawnQueenIfThisChunkHoldsAThrone(level, chunkPos);
        spawnLarvaeInNurseryChambers(level, chunkPos, random);
        seedEnderAntsOnTheColonyFringe(level, chunkPos, random);

        // One field sample per chunk, at its centre: cluster density is a property of "which
        // part of the world is this chunk in", and a per-cluster sample would only add jitter
        // to a number the tunable already states as an expectation.
        ColonyNoise noise = noise(level.getLevel().getChunkSource().randomState());
        double field = noise.colonyField(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());

        for (int tier = 0; tier < TIER_COUNT; tier++) {
            int bandMin = Math.max(tierMinY(tier), level.getMinBuildHeight() + 1);
            int bandMax = Math.min(tierMaxY(tier), level.getMaxBuildHeight() - 2);
            if (bandMax <= bandMin) {
                continue;
            }
            spawnTier(level, tier, chunkPos, random, bandMin, bandMax, field);
        }
    }

    /**
     * Seats the queen on her dais, exactly once per chamber.
     *
     * <p>{@code spawnOriginalMobs} is the honest hook for it: {@code ChunkStatus.SPAWN} runs
     * it once in a chunk's whole life, so "the chunk containing this chamber's centre column
     * spawns the queen" is a once-ever event without needing a marker block entity, a saved
     * data flag, or a first-tick scan. It is also the same mechanism that already seeds the
     * four tiers' ants, so there is one story about how this dimension populates itself.
     *
     * <p>{@code getBlockState} is deliberately not consulted: the dais is written by
     * {@link #fill}, which runs several chunk statuses earlier, and its position is a pure
     * function of the chamber -- so the Y is known rather than searched for.
     */
    private void spawnQueenIfThisChunkHoldsAThrone(WorldGenRegion level, ChunkPos chunkPos) {
        ColonyNoise noise = noise(level.getLevel().getChunkSource().randomState());
        for (ColonyNoise.Throne throne : noise.thronesNear(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ())) {
            int x = (int) Math.round(throne.centreX());
            int z = (int) Math.round(throne.centreZ());
            if (SectionPos.blockToSectionCoord(x) != chunkPos.x || SectionPos.blockToSectionCoord(z) != chunkPos.z) {
                continue;
            }
            BlockPos seat = new BlockPos(x, throne.floorY() + THRONE_DAIS_HEIGHT + 1, z);
            QueenAntEntity queen = ModEntities.QUEEN_ANT.get().create(level.getLevel());
            if (queen == null) {
                Formicary.LOGGER.warn("Formicary: could not create the queen for the chamber at {}", seat);
                continue;
            }
            queen.moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F,
                    0.0F);
            queen.finalizeSpawn(level, level.getCurrentDifficultyAt(seat), MobSpawnType.CHUNK_GENERATION, null);
            queen.setThroneHome(seat);
            queen.setPersistenceRequired();
            level.addFreshEntityWithPassengers(queen);
            Formicary.LOGGER.info("Formicary: seated a queen at {}", seat);
        }
    }

    /**
     * Seeds one tier's mixed worker/soldier clusters into this chunk.
     *
     * <p>The composition is taken from {@link ColonyGeneratorTunables} rather than from the
     * biome's weighted spawn list, which is the change play-test round 1 asked for: a
     * weighted list draws one {@code SpawnerData} per group and so can only ever produce a
     * single-caste knot, and no set of weights expresses "these castes, in this ratio, in
     * the same party". Soldiers are placed first, so the escort ends up at the seed position
     * and the workers spread out around it rather than the other way round.
     *
     * <p>{@code field} scales the cluster count (Ep2). Expressing the density as clusters
     * per chunk rather than as a probability is what makes that a one-character change:
     * multiplying an expectation is meaningful, whereas multiplying vanilla's geometric
     * {@code creatureProbability} would have moved the mean by {@code p/(1-p)} instead.
     * {@link ColonyGeneratorTunables#rollCount} carries the fractional part, so a ring chunk
     * at {@code f = 0.4} really does average four tenths of the core's ants rather than
     * truncating to none.
     */
    private void spawnTier(WorldGenRegion level, int tier, ChunkPos chunkPos, RandomSource random,
            int bandMin, int bandMax, double field) {
        int originX = chunkPos.getMinBlockX();
        int originZ = chunkPos.getMinBlockZ();

        for (int cluster = rollCount(SPAWN_CLUSTERS_PER_CHUNK_BY_TIER[tier] * field, random);
                cluster > 0; cluster--) {
            int soldiers = between(CLUSTER_SOLDIERS_MIN_BY_TIER[tier], CLUSTER_SOLDIERS_MAX_BY_TIER[tier], random);
            int workers = between(CLUSTER_WORKERS_MIN_BY_TIER[tier], CLUSTER_WORKERS_MAX_BY_TIER[tier], random);
            if (soldiers + workers == 0) {
                continue;
            }
            // One carrier per caste for the group data vanilla threads through a spawn
            // group: a mixed cluster is two vanilla groups sharing a position, and handing a
            // worker's SpawnGroupData to a soldier would be a type the callee never made.
            // Deliberately locals, not fields: spawnOriginalMobs runs on the chunk worker
            // pool and one generator instance serves every chunk in the dimension at once.
            SpawnGroupData[] soldierGroup = new SpawnGroupData[1];
            SpawnGroupData[] workerGroup = new SpawnGroupData[1];
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);

            for (int member = 0; member < soldiers + workers; member++) {
                boolean soldier = member < soldiers;
                EntityType<? extends Mob> type =
                        soldier ? ModEntities.SOLDIER_ANT.get() : ModEntities.WORKER_ANT.get();
                SpawnGroupData[] groupData = soldier ? soldierGroup : workerGroup;
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < SPAWN_FLOOR_ATTEMPTS; attempt++) {
                    int y = findFloor(level, x, z, bandMin, bandMax, random);
                    if (y > Integer.MIN_VALUE) {
                        placed = trySpawn(level, type, x, y, z, chunkPos, groupData, random);
                    }
                    x = Mth.clamp(x + random.nextInt(7) - 3, originX, originX + 15);
                    z = Mth.clamp(z + random.nextInt(7) - 3, originZ, originZ + 15);
                }
            }
        }
    }

    /**
     * Seeds a nursery chamber's brood, exactly once per chamber.
     *
     * <p>Same mechanism, and the same reasoning, as
     * {@link #spawnQueenIfThisChunkHoldsAThrone}: {@code ChunkStatus.SPAWN} runs once in a
     * chunk's life, so "the chunk holding this chamber's centre column seeds it" is a
     * once-ever event with no marker block or saved flag. Positions are recomputed from
     * {@link ColonyNoise} rather than searched for -- the chamber's floor slab is forced
     * solid by the same pure function that carved the room, so {@code floorY + 1} is known
     * to be standable without reading a block.
     *
     * <p>These larvae are the only ones in the world: the ambient spawn entry was removed
     * from the Nurseries biome in this round, so brood exists in brood rooms and nowhere
     * else. {@code setPersistenceRequired} for the same reason -- placed content, not
     * population -- which also keeps them out of the CREATURE cap that the ambient ants have
     * to share.
     */
    private void spawnLarvaeInNurseryChambers(WorldGenRegion level, ChunkPos chunkPos, RandomSource random) {
        ColonyNoise noise = noise(level.getLevel().getChunkSource().randomState());
        for (ColonyNoise.Nursery nursery : noise.nurseriesNear(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ())) {
            // Same reason the Provision Comb pass repeats it: nurseriesNear keeps its fixed
            // 3x3 cell order rather than the pruned per-column arrays, so a chamber the
            // colony field gated out would otherwise seed larvae into solid soil.
            if (!nursery.inColony()) {
                continue;
            }
            int centreX = (int) Math.round(nursery.centreX());
            int centreZ = (int) Math.round(nursery.centreZ());
            if (SectionPos.blockToSectionCoord(centreX) != chunkPos.x
                    || SectionPos.blockToSectionCoord(centreZ) != chunkPos.z) {
                continue;
            }
            int count = between(NURSERY_LARVAE_MIN, NURSERY_LARVAE_MAX, random);
            for (int i = 0; i < count; i++) {
                // Spread around the floor on a ring well inside the wall, so a larva never
                // lands in the shell or under the curve of the dome.
                double angle = (i + random.nextDouble()) * Math.PI * 2.0 / count;
                double radius = (NURSERY_RADIUS - 2.0) * Math.sqrt(random.nextDouble());
                double x = nursery.centreX() + Math.cos(angle) * radius;
                double z = nursery.centreZ() + Math.sin(angle) * radius;
                int y = nursery.floorY() + 1;

                LarvaEntity larva = ModEntities.LARVA.get().create(level.getLevel());
                if (larva == null) {
                    Formicary.LOGGER.warn("Formicary: could not create a larva for the nursery at {}, {}",
                            centreX, centreZ);
                    continue;
                }
                larva.moveTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
                larva.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                        MobSpawnType.CHUNK_GENERATION, null);
                larva.setPersistenceRequired();
                level.addFreshEntityWithPassengers(larva);
            }
            Formicary.LOGGER.info("Formicary: seeded {} larvae in the nursery chamber at ({}, {}, {})",
                    count, centreX, nursery.floorY() + 1, centreZ);
        }
    }

    /**
     * Seeds the two or three ender ants a colony introduces itself with (Ep2, spec section
     * 5), exactly once per slot.
     *
     * <p>Third instance of the same mechanism as
     * {@link #spawnQueenIfThisChunkHoldsAThrone} and {@link #spawnLarvaeInNurseryChambers}:
     * the positions are a pure function of the colony cell ({@link ColonyNoise#
     * enderSlotsForCell}), {@code ChunkStatus.SPAWN} runs once in a chunk's life, and the
     * chunk that happens to contain a slot is the one that fills it. That is what makes "2-3
     * per colony" true of a colony rather than true on average.
     *
     * <p>Two deliberate departures from {@link #spawnTier}, which is the other thing in this
     * file that places mobs:
     * <ul>
     *   <li>{@code SpawnPlacements.checkSpawnRules} is <em>not</em> consulted. It would
     *       reject nearly every slot: the runtime predicate
     *       ({@code EnderAntEntity#checkSpawnRules}) demands block light 0 and a colony field
     *       below {@code ENDER_SPAWN_MAX_F} = 0.35, while these ants are placed on purpose in
     *       a lit-ish fringe at {@code f} in {@code [0.3, 0.8]}. This is placed content, the
     *       queen and the nursery brood are placed the same way, and a placement predicate is
     *       a rule about where a <em>population</em> may appear.</li>
     *   <li>{@code setPersistenceRequired()} -- unlike a runtime ender ant, which keeps
     *       {@code Mob}'s stock distance despawn on purpose. Without it these would be gone
     *       the first time a player walked 128 blocks away, which is to say before anyone
     *       ever met one, since {@code MobCategory.MONSTER} is not persistent.</li>
     * </ul>
     */
    private void seedEnderAntsOnTheColonyFringe(WorldGenRegion level, ChunkPos chunkPos, RandomSource random) {
        // Royal Depths only (tier 0), clamped into the buildable range the way spawnTier
        // clamps its own bands.
        int bandMin = Math.max(tierMinY(0), level.getMinBuildHeight() + 1);
        int bandMax = Math.min(tierMaxY(0), level.getMaxBuildHeight() - 2);
        if (bandMax <= bandMin) {
            return;
        }

        ColonyNoise noise = noise(level.getLevel().getChunkSource().randomState());
        for (ColonyNoise.EnderSlot slot : noise.enderSlotsNear(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ())) {
            int x = Mth.floor(slot.x());
            int z = Mth.floor(slot.z());
            if (SectionPos.blockToSectionCoord(x) != chunkPos.x || SectionPos.blockToSectionCoord(z) != chunkPos.z) {
                continue;
            }
            // The slot is an XZ in a ring, and one 1x1 column of the Royal Depths is solid
            // far more often than not -- a scripted runServer probe seeded only 1 of 2 slots
            // when this took a single column. So it wanders, exactly the way spawnTier's own
            // placement loop does: jitter a few blocks and try again, clamped into this
            // chunk because a WorldGenRegion refuses writes outside the chunks it owns.
            int originX = chunkPos.getMinBlockX();
            int originZ = chunkPos.getMinBlockZ();
            int y = Integer.MIN_VALUE;
            for (int attempt = 0; y == Integer.MIN_VALUE && attempt < SPAWN_FLOOR_ATTEMPTS; attempt++) {
                y = findFloor(level, x, z, bandMin, bandMax, random);
                if (y == Integer.MIN_VALUE) {
                    x = Mth.clamp(x + random.nextInt(7) - 3, originX, originX + 15);
                    z = Mth.clamp(z + random.nextInt(7) - 3, originZ, originZ + 15);
                }
            }
            if (y == Integer.MIN_VALUE) {
                // Still nothing: a chunk of the fringe that is genuinely solid through the
                // whole Royal Depths band. Expected occasionally rather than a fault -- the
                // colony simply seeds one fewer.
                Formicary.LOGGER.debug("Formicary: no Royal Depths floor for the ender ant slot at ({}, {})", x, z);
                continue;
            }

            Entity entity = ModEntities.ENDER_ANT.get().create(level.getLevel());
            if (!(entity instanceof Mob ant)) {
                Formicary.LOGGER.warn("Formicary: could not create the seeded ender ant at ({}, {}, {})", x, y, z);
                continue;
            }
            ant.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
            ant.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(x, y, z)),
                    MobSpawnType.CHUNK_GENERATION, null);
            ant.setPersistenceRequired();
            level.addFreshEntityWithPassengers(ant);
            Formicary.LOGGER.info("Formicary: seeded an ender ant on the colony fringe at ({}, {}, {})", x, y, z);
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

    private boolean trySpawn(WorldGenRegion level, EntityType<? extends Mob> type, int x, int y, int z,
            ChunkPos chunkPos, SpawnGroupData[] groupData, RandomSource random) {
        BlockPos pos = SpawnPlacements.getPlacementType(type).adjustSpawnPosition(level, new BlockPos(x, y, z));
        if (!type.canSummon() || !SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
            return false;
        }
        float width = type.getWidth();
        double spawnX = Mth.clamp(x, chunkPos.getMinBlockX() + width, chunkPos.getMinBlockX() + 16.0 - width);
        double spawnZ = Mth.clamp(z, chunkPos.getMinBlockZ() + width, chunkPos.getMinBlockZ() + 16.0 - width);
        if (!level.noCollision(type.getSpawnAABB(spawnX, pos.getY(), spawnZ))
                || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.CHUNK_GENERATION,
                        BlockPos.containing(spawnX, pos.getY(), spawnZ), level.getRandom())) {
            return false;
        }

        Entity entity;
        try {
            entity = type.create(level.getLevel());
        } catch (Exception exception) {
            Formicary.LOGGER.warn("Formicary: failed to create {} for chunk-generation spawn",
                    type.getDescriptionId(), exception);
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
        ColonyNoise.Nursery[] nurseries = noise.nurseriesForColumn(noise.nurseriesNear(chunkX, chunkZ), x, z);
        ColonyNoise.Garden[] gardens = noise.gardensForColumn(noise.gardensNear(chunkX, chunkZ), x, z);
        ColonyNoise.Larder[] larders = noise.lardersForColumn(noise.lardersNear(chunkX, chunkZ), x, z);
        double field = noise.colonyField(x, z);
        int bottom = height.getMinBuildHeight();
        BlockState[] column = new BlockState[height.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = bottom + i;
            if (noise.isAir(field, shafts, thrones, nurseries, gardens, larders, x, y, z)) {
                column[i] = AIR;
            } else if (noise.isDaylightMembrane(field, shafts, thrones, nurseries, gardens, larders, x, y, z)) {
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
        ColonyNoise.Colony colony = noise.nearestColony(pos.getX(), pos.getZ());
        info.add(String.format("colony f %.3f, centre %d %d (%.0f blocks)",
                noise.colonyField(pos.getX(), pos.getZ()),
                Math.round(colony.centreX()), Math.round(colony.centreZ()),
                Math.hypot(pos.getX() - colony.centreX(), pos.getZ() - colony.centreZ())));
    }
}
