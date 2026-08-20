package com.nogal.formicary.gametest;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_ELIGIBILITY_MIN_F;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_SOLDIERS_MAX_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_SOLDIERS_MIN_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_WORKERS_MAX_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CLUSTER_WORKERS_MIN_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_ELIGIBILITY_MIN_F;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_DOME_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_SHELL_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_WALL_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SHAFT_SPACING;
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
     *
     * <p><b>Retargeted for the Ep2 colony field.</b> Before it, every cell in the sweep had
     * a chamber; now a cell whose chamber fell in the wilds generates nothing, and asserting
     * a solid floor there would be asserting that an absent room has one. The invariant is
     * unchanged and every assertion below is exactly as strong as it was -- what moved is
     * the population it runs over: chambers that actually generate
     * ({@link ColonyNoise.Nursery#inColony()}), widened to a 15x15 cell sweep so the same
     * larva-seeding guarantee is still checked over hundreds of rooms. The count guard is
     * not a formality: without it, a bug that gated every chamber out would turn this test
     * into a vacuous pass, which is precisely the failure mode the gate introduces.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void a_nursery_chamber_has_a_solid_floor_and_clear_air_above_it(GameTestHelper helper) {
        int checked = 0;
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            for (int cellX = -7; cellX <= 7; cellX++) {
                for (int cellZ = -7; cellZ <= 7; cellZ++) {
                    ColonyNoise.Nursery nursery = centreNurseryOfCell(noise, cellX, cellZ);
                    if (!nursery.inColony()) {
                        continue;
                    }
                    checked++;
                    int x = (int) Math.round(nursery.centreX());
                    int z = (int) Math.round(nursery.centreZ());
                    double field = noise.colonyField(x, z);
                    ColonyNoise.Shaft[] shafts = shaftsAt(noise, x, z);
                    ColonyNoise.Throne[] thrones = thronesAt(noise, x, z);
                    ColonyNoise.Nursery[] nurseries = nurseriesAt(noise, x, z);
                    ColonyNoise.Garden[] gardens = gardensAt(noise, x, z);
                    ColonyNoise.Larder[] larders = lardersAt(noise, x, z);

                    helper.assertFalse(
                            noise.isAir(field, shafts, thrones, nurseries, gardens, larders, x, nursery.floorY(), z),
                            "a nursery chamber's floor slab at (" + x + ", " + nursery.floorY() + ", " + z
                                    + ") on seed " + seed + " should be solid");
                    for (int above = 1; above <= 2; above++) {
                        helper.assertTrue(
                                noise.isAir(field, shafts, thrones, nurseries, gardens, larders, x,
                                        nursery.floorY() + above, z),
                                "a nursery chamber's air at (" + x + ", " + (nursery.floorY() + above) + ", "
                                        + z + ") on seed " + seed + " should be clear for a larva to stand in");
                    }
                }
            }
        }
        helper.assertTrue(checked >= 40,
                "setup: the sweep should find plenty of in-colony nursery chambers to check, found " + checked);
        helper.succeed();
    }

    /**
     * Every nursery / garden / larder chamber that generates is inside a colony <em>and</em>
     * hangs off a ramp that got dug, and the ones outside are genuinely gated out rather
     * than merely rare.
     *
     * <p>The gate is the load-bearing half of "dense cores, sparse wilds": the density
     * profile is measured by {@code NoiseProbe}, but "no chamber ever generates below its
     * eligibility floor" is a geometric invariant, so a single counterexample is a bug rather
     * than a statistic and it belongs here. The last assertion is the one that keeps the
     * others honest: a gate that rejected everything would satisfy them alone.
     *
     * <p><b>Retargeted for play-test round 2, and strictly strengthened.</b> The gate the
     * original asserted -- {@code inColony == field > CHAMBER_ELIGIBILITY_MIN_F} -- is no
     * longer the whole rule, so keeping it would have pinned behaviour the round deliberately
     * replaced. Two things changed and both are now asserted rather than dropped: a chamber
     * also has to hang off a realized ramp (item 5; without it the room's only doorway opens
     * onto solid soil), and the larder has its own looser floor
     * {@link ColonyGeneratorTunables#LARDER_ELIGIBILITY_MIN_F} (item 7). The assertion is
     * still an exact bi-implication over the real conjunction, so nothing was loosened -- it
     * catches a chamber generating where it should not AND a chamber missing where it should
     * be, exactly as before.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void chambers_generate_only_inside_colonies(GameTestHelper helper) {
        int eligible = 0;
        int total = 0;
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            for (int cellX = -7; cellX <= 7; cellX++) {
                for (int cellZ = -7; cellZ <= 7; cellZ++) {
                    ColonyNoise.Nursery nursery = centreNurseryOfCell(noise, cellX, cellZ);
                    ColonyNoise.Garden garden =
                            noise.gardensNear(cellX * GARDEN_SPACING, cellZ * GARDEN_SPACING)[4];
                    ColonyNoise.Larder larder =
                            noise.lardersNear(cellX * LARDER_SPACING, cellZ * LARDER_SPACING)[4];
                    total += 3;
                    eligible += (nursery.inColony() ? 1 : 0) + (garden.inColony() ? 1 : 0)
                            + (larder.inColony() ? 1 : 0);

                    assertGate(helper, noise, seed, "nursery", nursery.inColony(),
                            nursery.centreX(), nursery.centreZ(), nursery.axisX(), nursery.axisZ(),
                            CHAMBER_ELIGIBILITY_MIN_F);
                    assertGate(helper, noise, seed, "garden", garden.inColony(),
                            garden.centreX(), garden.centreZ(), garden.axisX(), garden.axisZ(),
                            CHAMBER_ELIGIBILITY_MIN_F);
                    assertGate(helper, noise, seed, "larder", larder.inColony(),
                            larder.centreX(), larder.centreZ(), larder.axisX(), larder.axisZ(),
                            LARDER_ELIGIBILITY_MIN_F);
                }
            }
        }
        helper.assertTrue(eligible > 0 && eligible < total,
                "the colony field should let some chambers through and gate others out, got " + eligible
                        + " of " + total);
        helper.succeed();
    }

    /**
     * {@code inColony} agrees, in both directions, with the conjunction the generator
     * actually applies: the field at the chamber's own centre clears its kind's floor, and
     * the ramp it hangs off is one the colony dug.
     */
    private static void assertGate(GameTestHelper helper, ColonyNoise noise, long seed, String kind,
            boolean inColony, double centreX, double centreZ, double axisX, double axisZ, double minField) {
        double field = noise.colonyField(centreX, centreZ);
        boolean anchored = noise.isShaftRealized(new ColonyNoise.Shaft(axisX, axisZ, 0.0));
        helper.assertTrue(inColony == (field > minField && anchored),
                "a " + kind + " chamber on seed " + seed + " reports inColony=" + inColony
                        + " at colony field " + field + " (floor " + minField + ") with its ramp "
                        + (anchored ? "realized" : "not realized")
                        + ", which disagrees with the eligibility gate");
    }

    /**
     * A ramp is realized only inside a colony (play-test round 2, item 5).
     *
     * <p>The generator-side half of the gate, asserted where a geometric invariant belongs.
     * {@code NoiseProbe} measures how many ramps that leaves and whether every colony still
     * has enough of them -- numbers, which need a wide sweep and a tool that can print. This
     * is the part that is true or false: no ramp stands out in the wilds, and
     * {@code shaftsNear} -- the single seam {@code isAir}, the probe and the dev commands all
     * read the spine through -- never hands one back.
     *
     * <p>The second assertion is the non-vacuity guard, and it is not a formality here: a
     * gate that realized nothing would satisfy the first assertion perfectly and leave the
     * dimension with no vertical circulation anywhere.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void ramps_are_realized_only_inside_colonies(GameTestHelper helper) {
        int realized = 0;
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            ColonyNoise.Colony core = noise.nearestColony(0.0, 0.0);
            int coreX = (int) Math.round(core.centreX());
            int coreZ = (int) Math.round(core.centreZ());
            for (int cellX = -8; cellX <= 8; cellX++) {
                for (int cellZ = -8; cellZ <= 8; cellZ++) {
                    for (ColonyNoise.Shaft shaft : noise.shaftsNear(coreX + cellX * SHAFT_SPACING,
                            coreZ + cellZ * SHAFT_SPACING)) {
                        realized++;
                        double field = noise.colonyField(shaft.axisX(), shaft.axisZ());
                        helper.assertTrue(field >= CHAMBER_ELIGIBILITY_MIN_F,
                                "shaftsNear handed back a ramp at (" + shaft.axisX() + ", " + shaft.axisZ()
                                        + ") on seed " + seed + " where the colony field is " + field
                                        + ", below the " + CHAMBER_ELIGIBILITY_MIN_F + " realization gate");
                    }
                }
            }
        }
        helper.assertTrue(realized > 0,
                "the gate should still realize ramps inside colonies, got none across " + SEEDS.length + " seeds");
        helper.succeed();
    }

    /**
     * No chamber floor can land inside a landing's dome (play-test round 2, item 6).
     *
     * <p>{@code ColonyNoise#shaftState} outranks every chamber, so a chamber whose corridor
     * walkway fell inside the landing's air would have its floor cut out from under it -- the
     * bug {@code NURSERY_FLOOR_MIN_Y} was written to close, which the probe originally caught
     * as a room with 113 standable floor blocks and 0 of them reachable. Round 2 domed the
     * landing, raising its reach from {@code LANDING_HEIGHT} = 6 to
     * {@code LANDING_INTERIOR_HEIGHT} = 8, and moved the three floor minimums to match.
     *
     * <p>Asserted as the relationship rather than as the numbers: the three constants may be
     * retuned freely, and what may never happen is a chamber floor inside a landing. The
     * chamber floors themselves come from the real generator arithmetic (the first ramp turn
     * at or above the minimum) over a wide sweep, so this catches a retune of the ramp period
     * as well as a retune of the landing.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void chamber_floors_clear_the_landing_dome(GameTestHelper helper) {
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            for (int cellX = -6; cellX <= 6; cellX++) {
                for (int cellZ = -6; cellZ <= 6; cellZ++) {
                    assertClearsLandings(helper, seed, "nursery",
                            centreNurseryOfCell(noise, cellX, cellZ).floorY());
                    assertClearsLandings(helper, seed, "garden",
                            noise.gardensNear(cellX * GARDEN_SPACING, cellZ * GARDEN_SPACING)[4].floorY());
                    assertClearsLandings(helper, seed, "larder",
                            noise.lardersNear(cellX * LARDER_SPACING, cellZ * LARDER_SPACING)[4].floorY());
                }
            }
        }
        helper.succeed();
    }

    /** No tier boundary's landing reaches up to {@code floorY}. */
    private static void assertClearsLandings(GameTestHelper helper, long seed, String kind, int floorY) {
        for (int band = 1; band < TIER_COUNT; band++) {
            int boundary = tierMinY(band);
            helper.assertFalse(floorY >= boundary && floorY < boundary + LANDING_INTERIOR_HEIGHT,
                    "a " + kind + " chamber on seed " + seed + " has its floor at y=" + floorY
                            + ", inside the landing carved at y[" + boundary + ", "
                            + (boundary + LANDING_INTERIOR_HEIGHT) + ") -- shaftState outranks every chamber, so"
                            + " its corridor floor would be cut out from under it");
        }
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
        String[] tiers = {"royal_depths", "nurseries", "fungal_gardens"};
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
     *
     * <p><b>Retargeted for the Ep2 colony field.</b> Comb is now something a colony grows:
     * its patch threshold is lerped to "never" out in the wilds, and the origin is wilds on
     * essentially every seed, so sampling there would measure the gate rather than the
     * patches. The window moves to a colony <em>core</em> instead -- an 80x80 box centred on
     * the colony nearest the origin never leaves
     * {@code COLONY_CORE_RADIUS} (its corners are 57 blocks out), so the field is a flat 1.0
     * across it and both bounds below are asserted against exactly the same numbers they
     * were before the field existed. Nothing was loosened.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void comb_grows_in_contiguous_patches(GameTestHelper helper) {
        for (long seed : SEEDS) {
            ColonyNoise noise = noise(seed);
            ColonyNoise.Colony core = noise.nearestColony(0.0, 0.0);
            int coreX = (int) Math.round(core.centreX());
            int coreZ = (int) Math.round(core.centreZ());
            // The whole window lies inside one 384-block colony cell (88 blocks from its
            // centre at worst), so one neighbourhood resolves the field for all of it.
            ColonyNoise.Colony[] colonies = noise.coloniesNear(coreX, coreZ);
            long inside = 0;
            long samples = 0;
            long agreements = 0;
            for (int dx = -40; dx < 40; dx++) {
                int x = coreX + dx;
                for (int dz = -40; dz < 40; dz++) {
                    int z = coreZ + dz;
                    double field = noise.colonyField(colonies, x, z);
                    double fieldNext = noise.colonyField(colonies, x + 1, z);
                    for (int y = tierMinY(1); y < tierMaxY(1); y += 3) {
                        boolean here = noise.isCombPatch(field, x, y, z);
                        boolean next = noise.isCombPatch(fieldNext, x + 1, y, z);
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
