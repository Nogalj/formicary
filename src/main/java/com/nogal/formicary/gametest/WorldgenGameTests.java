package com.nogal.formicary.gametest;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_SOLDIERS_MAX_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_SOLDIERS_MIN_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_WORKERS_MAX_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_WORKERS_MIN_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_DOME_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_SHELL_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_WALL_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SPAWN_CLUSTERS_PER_CHUNK_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.between;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.rollCount;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMaxY;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMinY;

import java.util.List;
import java.util.Optional;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.WorkerAntEntity;
import com.nogal.formicary.worldgen.ColonyNoise;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the play-test round 1 worldgen package.
 *
 * <p>What can and cannot be tested here is decided by one banked fact: {@code
 * GameTestServer} bakes the {@code WorldPresets.FLAT} preset into a deliberately empty
 * {@code LevelStem} registry, so the Formicary dimension does not exist on the test server
 * and no assertion can be made about a generated colony chunk. The dimension-side evidence
 * lives in {@code NoiseProbe} instead (air fractions, walkability, chamber frequency, comb
 * patch sizes, spawn density).
 *
 * <p>What is left is still worth having, and it is the part most likely to break silently:
 * {@link ColonyNoise} and {@code ColonyGeneratorTunables} are pure functions of position
 * that need nothing but a {@code PositionalRandomFactory}, so every geometric invariant the
 * generator relies on can be asserted directly. Plus one genuinely end-to-end case -- the
 * anthill template's baked worker ants -- which needs a real level and gets one.
 */
@GameTestHolder(Formicary.MODID)
public class WorldgenGameTests {

    /** A colony's shape, seeded the way {@code NoiseProbe} seeds it. */
    private static ColonyNoise noise(long seed) {
        return new ColonyNoise(new XoroshiroRandomSource(seed).forkPositional());
    }

    private static final long[] SEEDS = {1234567L, 42L, 987654321L, 8675309L};

    // ------------------------------------------------------------------
    // Overworld workers at the anthill (the structure template's entities)
    // ------------------------------------------------------------------

    /**
     * The acceptance criterion for "worker ants should appear around savanna anthills",
     * end to end: placing the real {@code formicary:anthill} template puts real worker ants
     * in the world.
     *
     * <p>This is the whole reason the mechanism chosen was baked template entities rather
     * than {@code spawn_overrides} or a biome modifier -- it is the only one of the three
     * that produces ants at the moment the structure generates, and therefore the only one
     * that can be asserted at all without a running savanna.
     *
     * <p>{@link StructurePlaceSettings} is left at its defaults on purpose: that is what
     * makes this a test of the template rather than of the call. {@code SinglePoolElement
     * .getSettings} (which is what jigsaw placement actually uses) sets
     * {@code ignoreEntities} false and {@code finalizeEntities} true, and the default
     * settings agree with it on the first, which is the flag that decides whether entities
     * are placed at all.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "arena_platform", skyAccess = true)
    public static void the_anthill_template_brings_its_own_worker_ants(GameTestHelper helper) {
        Optional<StructureTemplate> loaded = helper.getLevel().getStructureManager()
                .get(ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "anthill"));
        helper.assertTrue(loaded.isPresent(), "setup: the anthill structure template should load");

        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        boolean placed = loaded.get().placeInWorld(helper.getLevel(), origin, origin,
                new StructurePlaceSettings(), helper.getLevel().getRandom(), 2);
        helper.assertTrue(placed, "setup: the anthill template should place");

        List<WorkerAntEntity> workers = helper.getLevel()
                .getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds().inflate(4.0));
        helper.assertTrue(workers.size() >= 3,
                "a freshly placed anthill should bring at least 3 worker ants, found " + workers.size());

        // Not a bonus assertion: three ants stacked in one column would satisfy the count
        // above and fail the actual requirement, which is that they read as ants milling
        // AROUND the mound. The template puts them on three different sides of it.
        double spread = 0.0;
        for (WorkerAntEntity a : workers) {
            for (WorkerAntEntity b : workers) {
                spread = Math.max(spread, a.position().distanceTo(b.position()));
            }
        }
        helper.assertTrue(spread >= 3.0,
                "the workers should be spread around the mound, widest separation was " + spread);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Nursery chambers
    // ------------------------------------------------------------------

    /**
     * Every chamber, and its shell, stays inside the Nurseries band.
     *
     * <p>The floor is derived from a ramp's helicoid height, so it is not a constant and not
     * obviously bounded -- {@code NURSERY_FLOOR_MIN_Y} plus the ramp's 24-block period is
     * what confines it, and getting either wrong would put brood rooms in the Fungal Gardens
     * or hanging through the tier floor with no complaint from the compiler.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void nursery_chambers_stay_inside_the_nurseries_tier(GameTestHelper helper) {
        int shell = (int) Math.ceil(NURSERY_SHELL_THICKNESS);
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            for (int cellX = -6; cellX <= 6; cellX++) {
                for (int cellZ = -6; cellZ <= 6; cellZ++) {
                    ColonyNoise.Nursery nursery = centreNurseryOfCell(noise, cellX, cellZ);
                    int bottom = nursery.floorY() - shell;
                    int top = nursery.floorY() + NURSERY_WALL_HEIGHT + NURSERY_DOME_HEIGHT + shell;
                    helper.assertTrue(bottom >= tierMinY(1) && top < tierMaxY(1),
                            "a nursery chamber at cell (" + cellX + ", " + cellZ + ") seed " + seed
                                    + " spans y[" + bottom + ", " + top + "], outside the Nurseries band y["
                                    + tierMinY(1) + ", " + tierMaxY(1) + ")");
                }
            }
        }
        helper.succeed();
    }

    /**
     * No two chambers ever overlap.
     *
     * <p>This is the invariant {@code NURSERY_SPACING} exists to protect, and it was a real
     * failure before the cell was widened to 96: two chambers 5.8 blocks apart, with one's
     * forced-solid shell sealing the other's corridor and its brood inside. The bound is
     * geometric rather than statistical, so a single counterexample anywhere is a bug -- the
     * 13x13 cell sweep across four seeds is 676 chambers per seed's worth of chances to find
     * one.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void nursery_chambers_never_overlap(GameTestHelper helper) {
        double clearance = 2.0 * (NURSERY_RADIUS + NURSERY_SHELL_THICKNESS);
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            for (int cellX = -6; cellX < 6; cellX++) {
                for (int cellZ = -6; cellZ < 6; cellZ++) {
                    ColonyNoise.Nursery here = centreNurseryOfCell(noise, cellX, cellZ);
                    for (int dx = 0; dx <= 1; dx++) {
                        for (int dz = 0; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            ColonyNoise.Nursery other = centreNurseryOfCell(noise, cellX + dx, cellZ + dz);
                            double distance = Math.hypot(here.centreX() - other.centreX(),
                                    here.centreZ() - other.centreZ());
                            helper.assertTrue(distance > clearance,
                                    "nursery chambers at cells (" + cellX + ", " + cellZ + ") and ("
                                            + (cellX + dx) + ", " + (cellZ + dz) + ") on seed " + seed
                                            + " are " + distance + " apart, closer than the " + clearance
                                            + " their shells need");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * A chamber's brood floor is standable: solid at {@code floorY}, clear above it.
     *
     * <p>{@code ColonyChunkGenerator#spawnLarvaeInNurseryChambers} places larvae at
     * {@code floorY + 1} without reading a single block, on exactly this guarantee. If the
     * shell arithmetic ever stopped forcing the floor slab solid, the larvae would be placed
     * in mid air and fall out of the room, and nothing else in the build would notice.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void a_nursery_chamber_has_a_solid_floor_and_clear_air_above_it(GameTestHelper helper) {
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            for (int cellX = -2; cellX <= 2; cellX++) {
                for (int cellZ = -2; cellZ <= 2; cellZ++) {
                    ColonyNoise.Nursery nursery = centreNurseryOfCell(noise, cellX, cellZ);
                    int x = (int) Math.round(nursery.centreX());
                    int z = (int) Math.round(nursery.centreZ());
                    ColonyNoise.Shaft[] shafts = shaftsAt(noise, x, z);
                    ColonyNoise.Throne[] thrones = thronesAt(noise, x, z);
                    ColonyNoise.Nursery[] nurseries = nurseriesAt(noise, x, z);
                    ColonyNoise.Garden[] gardens = gardensAt(noise, x, z);
                    ColonyNoise.Larder[] larders = lardersAt(noise, x, z);

                    helper.assertFalse(
                            noise.isAir(shafts, thrones, nurseries, gardens, larders, x, nursery.floorY(), z),
                            "a nursery chamber's floor slab at (" + x + ", " + nursery.floorY() + ", " + z
                                    + ") on seed " + seed + " should be solid");
                    for (int above = 1; above <= 2; above++) {
                        helper.assertTrue(
                                noise.isAir(shafts, thrones, nurseries, gardens, larders, x,
                                        nursery.floorY() + above, z),
                                "a nursery chamber's air at (" + x + ", " + (nursery.floorY() + above) + ", "
                                        + z + ") on seed " + seed + " should be clear for a larva to stand in");
                    }
                }
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * The Nurseries biome no longer offers a larva to the ambient spawner.
     *
     * <p>"Nursery chambers are the sole larva source" is a claim about a datapack file, and
     * a datapack file is exactly the kind of thing that gets edited back by accident. The
     * biome registry is a datapack registry, so it is populated on the test server even
     * though the dimension that uses it is not.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void no_colony_biome_spawns_larvae(GameTestHelper helper) {
        String[] tiers = {"royal_depths", "nurseries", "fungal_gardens", "upper_galleries"};
        int checked = 0;
        for (String tier : tiers) {
            Optional<Biome> biome = helper.getLevel().registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getOptional(ResourceKey.create(Registries.BIOME,
                            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, tier)));
            if (biome.isEmpty()) {
                continue;
            }
            checked++;
            for (MobSpawnSettings.SpawnerData data : biome.get().getMobSettings()
                    .getMobs(MobCategory.CREATURE).unwrap()) {
                helper.assertFalse(data.type == ModEntities.LARVA.get(),
                        "biome formicary:" + tier + " still lists the larva as an ambient spawn; "
                                + "nursery chambers are meant to be the only source");
            }
        }
        helper.assertTrue(checked == tiers.length,
                "setup: expected all " + tiers.length + " colony biomes in the registry, found " + checked);
        helper.succeed();
    }

    /**
     * Seeded clusters are mixed, and the mix is 3-4 workers to a soldier.
     *
     * <p>Asserted over the real roll functions rather than over the constants, because the
     * thing that broke in play-test round 1 was not a number but a shape: vanilla's
     * one-{@code SpawnerData}-per-group draw could not express a mixed party at any weights.
     * The bounds are deliberately loose (3:1 to 4:1 exactly, per tier) -- this is a
     * regression guard on the composition rule, not a re-derivation of the arithmetic.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void ant_clusters_mix_workers_and_soldiers(GameTestHelper helper) {
        RandomSource random = new XoroshiroRandomSource(4242L);
        for (int tier = 1; tier < TIER_COUNT; tier++) {
            long workers = 0;
            long soldiers = 0;
            long clusters = 0;
            for (int trial = 0; trial < 4000; trial++) {
                int wanted = between(CLUSTER_WORKERS_MIN_BY_TIER[tier], CLUSTER_WORKERS_MAX_BY_TIER[tier], random);
                int guards = between(CLUSTER_SOLDIERS_MIN_BY_TIER[tier], CLUSTER_SOLDIERS_MAX_BY_TIER[tier], random);
                helper.assertTrue(wanted > 0 && guards > 0,
                        "tier " + tier + " must field a mixed cluster, got " + wanted + " workers and "
                                + guards + " soldiers");
                workers += wanted;
                soldiers += guards;
                clusters++;
            }
            double ratio = (double) workers / soldiers;
            helper.assertTrue(ratio >= 3.0 && ratio <= 4.0,
                    "tier " + tier + " should field 3-4 workers per soldier, got " + ratio
                            + " over " + clusters + " clusters");
        }

        // The Royal Depths stay soldier-only by design (M4a), so the mixed rule must NOT
        // have been applied there.
        helper.assertTrue(CLUSTER_WORKERS_MAX_BY_TIER[0] == 0,
                "the Royal Depths should field no workers");
        helper.assertTrue(CLUSTER_SOLDIERS_MIN_BY_TIER[0] > 0,
                "the Royal Depths should still field soldiers");
        helper.succeed();
    }

    /**
     * The cluster count per chunk is honoured on average, including its fractional part.
     *
     * <p>{@code rollCount} is the one piece of new arithmetic between a tuned number and the
     * population the player sees; a truncating bug there would silently drop every tier to
     * zero, since all four values are below 1.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void the_cluster_count_averages_out_to_its_tunable(GameTestHelper helper) {
        RandomSource random = new XoroshiroRandomSource(31337L);
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            double expected = SPAWN_CLUSTERS_PER_CHUNK_BY_TIER[tier];
            long total = 0;
            int trials = 200000;
            for (int trial = 0; trial < trials; trial++) {
                total += rollCount(expected, random);
            }
            double mean = (double) total / trials;
            helper.assertTrue(Math.abs(mean - expected) < 0.01,
                    "tier " + tier + " should average " + expected + " clusters per chunk, got " + mean);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Decoration
    // ------------------------------------------------------------------

    /**
     * Comb grows in patches, not speckle.
     *
     * <p>Stated as the property that actually distinguishes the two, and that a threshold
     * alone cannot fake: a patch field agrees with its own neighbour far more often than an
     * independent per-block roll of the same coverage would. For an independent roll at
     * coverage {@code p} the agreement rate is {@code 1 - 2p(1-p)}, which at the Nurseries'
     * 14% is about 76%; a contiguous field sits near 100% because disagreement only happens
     * at a patch's boundary.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void comb_grows_in_contiguous_patches(GameTestHelper helper) {
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            long inside = 0;
            long samples = 0;
            long agreements = 0;
            for (int x = -40; x < 40; x++) {
                for (int z = -40; z < 40; z++) {
                    for (int y = tierMinY(1); y < tierMaxY(1); y += 3) {
                        boolean here = noise.isCombPatch(x, y, z);
                        boolean next = noise.isCombPatch(x + 1, y, z);
                        inside += here ? 1 : 0;
                        agreements += here == next ? 1 : 0;
                        samples++;
                    }
                }
            }
            double coverage = (double) inside / samples;
            double agreement = (double) agreements / samples;
            double independent = 1.0 - 2.0 * coverage * (1.0 - coverage);
            helper.assertTrue(coverage > 0.02 && coverage < 0.35,
                    "comb patch coverage in the Nurseries should be a minority of positions, got " + coverage
                            + " on seed " + seed);
            helper.assertTrue(agreement > independent + 0.15,
                    "comb patches should be contiguous: neighbour agreement " + agreement
                            + " is no better than the " + independent
                            + " an independent per-block roll of the same coverage would give (seed " + seed + ")");
        }
        helper.succeed();
    }

    /**
     * The Royal Depths do not read like the Nurseries.
     *
     * <p>The play-test note was that the two deepest tiers looked the same, and they did:
     * both were majority Deep Loam. The assertion is deliberately about the CONTRAST rather
     * than about either tier alone -- a future retune is free to move both, and only has to
     * keep them different.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void the_royal_depths_read_differently_from_the_nurseries(GameTestHelper helper) {
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            long deepHardened = 0;
            long deepTotal = 0;
            long nurseryLoam = 0;
            long nurseryTotal = 0;
            for (int x = -32; x < 32; x++) {
                for (int z = -32; z < 32; z++) {
                    for (int y = tierMinY(0) + 8; y < tierMaxY(0); y += 4) {
                        deepTotal++;
                        int kind = noise.fabricKind(x, y, z);
                        deepHardened += kind == ColonyNoise.FABRIC_HARDENED_SOIL
                                || kind == ColonyNoise.FABRIC_RESIN_BLOCK ? 1 : 0;
                    }
                    for (int y = tierMinY(1); y < tierMaxY(1); y += 4) {
                        nurseryTotal++;
                        nurseryLoam += noise.fabricKind(x, y, z) == ColonyNoise.FABRIC_DEEP_LOAM ? 1 : 0;
                    }
                }
            }
            double deep = (double) deepHardened / deepTotal;
            double nursery = (double) nurseryLoam / nurseryTotal;
            helper.assertTrue(deep > 0.7,
                    "the Royal Depths should be dominated by hardened soil and resin, got " + deep
                            + " on seed " + seed);
            helper.assertTrue(nursery > 0.7,
                    "the Nurseries should still be dominated by deep loam, got " + nursery
                            + " on seed " + seed);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * The chamber belonging to one cell. {@code nurseriesNear} returns the 3x3 neighbourhood
     * in {@code dx}-major order, so index 4 is the cell itself.
     */
    private static ColonyNoise.Nursery centreNurseryOfCell(ColonyNoise noise, int cellX, int cellZ) {
        return noise.nurseriesNear(cellX * NURSERY_SPACING, cellZ * NURSERY_SPACING)[4];
    }

    private static ColonyNoise.Shaft[] shaftsAt(ColonyNoise noise, int x, int z) {
        return noise.shaftsForColumn(noise.shaftsNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
    }

    private static ColonyNoise.Throne[] thronesAt(ColonyNoise noise, int x, int z) {
        return noise.thronesForColumn(noise.thronesNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
    }

    private static ColonyNoise.Nursery[] nurseriesAt(ColonyNoise noise, int x, int z) {
        return noise.nurseriesForColumn(
                noise.nurseriesNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
    }

    private static ColonyNoise.Garden[] gardensAt(ColonyNoise noise, int x, int z) {
        return noise.gardensForColumn(
                noise.gardensNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
    }

    private static ColonyNoise.Larder[] lardersAt(ColonyNoise noise, int x, int z) {
        return noise.lardersForColumn(
                noise.lardersNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
    }
}
