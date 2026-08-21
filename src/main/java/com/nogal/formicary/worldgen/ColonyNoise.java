package com.nogal.formicary.worldgen;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ACCENT_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ACCENT_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ARRIVAL_CONNECTOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ARRIVAL_CONNECTOR_MAX_CLIMB;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ARRIVAL_CONNECTOR_MAX_DESCENT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ARRIVAL_CONNECTOR_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENTRY_CARVE_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_ELIGIBILITY_MIN_F;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_FLOOR_MIN_Y_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SLOT_JITTER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SLOT_STEP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_LARGE_THRESHOLD_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.COMB_PATCH_THRESHOLD_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.COMB_PATCH_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.COMB_PATCH_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_LARGE_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_LARGE_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SMALL_THRESHOLD_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SMALL_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SMALL_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.COLONY_ENDER_ANTS_MAX;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.COLONY_ENDER_ANTS_MIN;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.COLONY_JITTER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.COLONY_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENDER_SEED_INNER_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENDER_SEED_OUTER_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.between;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FLOOR_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_APPROACH_DISTANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_CORRIDOR_END;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_CORRIDOR_HALF_WIDTH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_CORRIDOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_CORRIDOR_START;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_DOME_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_FLOOR_MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_SHELL_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.GARDEN_WALL_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.HARDENED_ACCENT_THRESHOLD_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LANDING_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LANDING_WALL_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_APPROACH_DISTANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_COMB_DOORWAY_EXCLUSION;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_COMB_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_COMB_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_END;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_HALF_WIDTH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_START;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_DOME_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_ELIGIBILITY_MIN_F;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_PROVISION_COMB_MAX;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_PROVISION_COMB_MIN;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_SHELL_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_WALL_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MEMBRANE_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MEMBRANE_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NEVER_THRESHOLD;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_APPROACH_DISTANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_CORRIDOR_END;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_CORRIDOR_HALF_WIDTH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_CORRIDOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_CORRIDOR_START;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_DOME_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_FLOOR_MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_SHELL_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.NURSERY_WALL_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_AIR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_CENTER_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_HALF_WIDTH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_RADIANS_PER_BLOCK;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ROYAL_RESIN_THRESHOLD;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SHAFT_JITTER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SHAFT_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SHAFT_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCHES_MAX_PER_CHUNK;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCHES_MIN_PER_CHUNK;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_AGGREGATE_WEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_DIRT_WEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_HORIZONTAL_BIAS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_MAX_LAYERS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_MAX_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_MAX_SIZE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_MIN_LAYERS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_PATCH_MIN_SIZE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_SPECKLE_CLUSTERS_PER_CHUNK_PER_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_SPECKLE_MAX_SIZE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SOIL_SPECKLE_MIN_SIZE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_APPROACH_DISTANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_CORRIDOR_END;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_CORRIDOR_HALF_WIDTH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_CORRIDOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_CORRIDOR_START;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_DAIS_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_DAIS_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_DAIS_STEP_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_DOME_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_FLOOR_MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_LARDER_CLEARANCE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_SHELL_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_WALL_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TUNNEL_HALF_WIDTH_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TUNNEL_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TUNNEL_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.WALL_JITTER_AMOUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.WALL_JITTER_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.colonyFalloff;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierIndex;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMaxY;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMinY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * The shape of the Formicary dimension, as pure functions of block position.
 *
 * <p>Deliberately free of registries, chunks and levels: it needs nothing but a
 * {@link PositionalRandomFactory} (which the game derives from the world seed via
 * {@code RandomState#getOrCreateRandomFactory}). That is what lets {@link NoiseProbe} run
 * the exact carve the game will run, headlessly, and report air fractions and
 * cross-sections -- the only way to sanity check terrain without a client.
 *
 * <p>Every field is a single-octave {@link PerlinNoise}. Verified in the decompiled 1.21
 * sources: with {@code octaves = List.of(0)} the amplitude list is {@code [1.0]} and both
 * {@code lowestFreqInputFactor} and {@code lowestFreqValueFactor} come out as exactly 1.0,
 * so {@code getValue(x, y, z)} is raw {@code ImprovedNoise} at those coordinates. That
 * makes the thresholds in {@link ColonyGeneratorTunables} mean something stable instead of
 * riding on an octave-dependent scale factor.
 */
public final class ColonyNoise {

    /** Solid palette kinds returned by {@link #fabricKind}. */
    public static final int FABRIC_PACKED_SOIL = 0;
    public static final int FABRIC_AMBER_EARTH = 1;
    public static final int FABRIC_DEEP_LOAM = 2;
    public static final int FABRIC_HARDENED_SOIL = 3;
    public static final int FABRIC_RESIN_BLOCK = 4;

    private final PositionalRandomFactory factory;
    private final PerlinNoise tunnelA;
    private final PerlinNoise tunnelB;
    private final PerlinNoise chamberSmall;
    private final PerlinNoise chamberLarge;
    private final PerlinNoise accent;
    private final PerlinNoise wallJitter;
    private final PerlinNoise membrane;
    private final PerlinNoise combPatch;

    public ColonyNoise(PositionalRandomFactory factory) {
        this.factory = factory;
        this.tunnelA = octave(factory, "colony_tunnel_a");
        this.tunnelB = octave(factory, "colony_tunnel_b");
        this.chamberSmall = octave(factory, "colony_chamber_small");
        this.chamberLarge = octave(factory, "colony_chamber_large");
        this.accent = octave(factory, "colony_accent");
        this.wallJitter = octave(factory, "colony_wall_jitter");
        this.membrane = octave(factory, "colony_membrane");
        this.combPatch = octave(factory, "colony_comb_patch");
    }

    private static PerlinNoise octave(PositionalRandomFactory factory, String name) {
        return PerlinNoise.create(factory.fromHashOf(name), List.of(0));
    }

    // ------------------------------------------------------------------
    // Connectivity shafts
    // ------------------------------------------------------------------

    /**
     * One descending helical ramp. {@code axisX}/{@code axisZ} is the axis it winds around;
     * {@code phase} rotates where on the circle the ramp sits at {@code y = MIN_Y}.
     */
    public record Shaft(double axisX, double axisZ, double phase) {
    }

    private Shaft shaftForCell(int cellX, int cellZ) {
        RandomSource random = this.factory.at(cellX, 0, cellZ);
        double half = SHAFT_JITTER * 0.5;
        double x = (double) cellX * SHAFT_SPACING + SHAFT_SPACING * 0.5 + (random.nextDouble() * 2.0 - 1.0) * half;
        double z = (double) cellZ * SHAFT_SPACING + SHAFT_SPACING * 0.5 + (random.nextDouble() * 2.0 - 1.0) * half;
        double phase = random.nextDouble() * Math.PI * 2.0;
        return new Shaft(x, z, phase);
    }

    public static final Shaft[] NO_SHAFTS = new Shaft[0];

    /**
     * Whether a cell's ramp is <b>realized</b> -- i.e. actually carved into the world
     * (play-test round 2, item 5).
     *
     * <p>Ramps used to be unconditional everywhere on the 48-block grid, which made 4000
     * blocks of wilds look like a maintained mineshaft with nothing in it. They are now
     * colony infrastructure: a colony digs its own vertical circulation, and the ground
     * between colonies has worm tunnels and nothing else.
     *
     * <p>The test is on the <b>axis</b>, at the same {@link
     * ColonyGeneratorTunables#CHAMBER_ELIGIBILITY_MIN_F} the chambers use, and it is
     * all-or-nothing for the whole column: a ramp that existed for part of its height would
     * be a descent that ends in a wall, which is the one thing the connectivity spine may
     * never be.
     *
     * <p><b>What is deliberately NOT gated</b>, because between them they are the mod's
     * no-softlock guarantee: the worm tunnels, the ceiling membrane, and the arrival pockets.
     * A player who arrives in the wilds can still see and reach an exit through the cap
     * directly above them; what they cannot do is descend a ramp that a colony never dug.
     * That is inconvenience by design, and the walk to the next colony is bounded by
     * {@code NoiseProbe}'s own findability measurement.
     */
    public boolean isShaftRealized(Shaft shaft) {
        return colonyField(shaft.axisX(), shaft.axisZ()) >= CHAMBER_ELIGIBILITY_MIN_F;
    }

    /** As {@link #isShaftRealized(Shaft)}, against a colony set already resolved. */
    private boolean isShaftRealized(Colony[] colonies, Shaft shaft) {
        return colonyField(colonies, shaft.axisX(), shaft.axisZ()) >= CHAMBER_ELIGIBILITY_MIN_F;
    }

    /**
     * Every <b>realized</b> shaft that can possibly reach into the 16x16 area rooted at
     * ({@code blockMinX}, {@code blockMinZ}).
     *
     * <p>The 3x3 cell neighbourhood is provably enough: a cell's axis lands within
     * {@code SHAFT_JITTER/2} of its centre, so the nearest axis two cells away is at least
     * {@code 2*SHAFT_SPACING - SHAFT_JITTER/2 - 16} = 88 blocks from the chunk, far outside
     * {@link ColonyGeneratorTunables#SHAFT_MAX_REACH}.
     *
     * <p>Filtering here, rather than at each consumer, is the point: {@link #isAir}, the
     * probe, the cross-sections and the dev commands all reach the spine through this one
     * method, so they cannot disagree about which ramps exist. It can return an empty array
     * -- out in the wilds that is the correct answer.
     *
     * <p>One colony neighbourhood serves all nine axes. An axis sits at most
     * {@code SHAFT_SPACING + SHAFT_JITTER/2} = 56 blocks outside the chunk, and
     * {@link #coloniesNear}'s own derivation leaves 384 blocks to the nearest excluded
     * centre against an outer radius of 100, so 56 blocks of slack changes nothing.
     */
    public Shaft[] shaftsNear(int blockMinX, int blockMinZ) {
        int cellX = Math.floorDiv(blockMinX, SHAFT_SPACING);
        int cellZ = Math.floorDiv(blockMinZ, SHAFT_SPACING);
        Colony[] colonies = coloniesNear(blockMinX, blockMinZ);
        Shaft[] scratch = new Shaft[9];
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Shaft shaft = shaftForCell(cellX + dx, cellZ + dz);
                if (isShaftRealized(colonies, shaft)) {
                    scratch[count++] = shaft;
                }
            }
        }
        if (count == 0) {
            return NO_SHAFTS;
        }
        Shaft[] out = new Shaft[count];
        System.arraycopy(scratch, 0, out, 0, count);
        return out;
    }

    /** Shafts from {@code candidates} whose carve can reach the column at (x, z). */
    public Shaft[] shaftsForColumn(Shaft[] candidates, int x, int z) {
        int count = 0;
        Shaft[] scratch = new Shaft[candidates.length];
        double reach = SHAFT_MAX_REACH;
        for (Shaft shaft : candidates) {
            double dx = x - shaft.axisX();
            double dz = z - shaft.axisZ();
            if (dx * dx + dz * dz <= reach * reach) {
                scratch[count++] = shaft;
            }
        }
        if (count == candidates.length) {
            return candidates;
        }
        Shaft[] out = new Shaft[count];
        System.arraycopy(scratch, 0, out, 0, count);
        return out;
    }

    /** {@link #shaftState} verdicts. SOLID and AIR both outrank the noise carve. */
    public static final int SHAFT_SOLID = -1;
    public static final int SHAFT_NONE = 0;
    public static final int SHAFT_AIR = 1;

    private static final double TWO_PI = Math.PI * 2.0;
    /** Blocks of descent per full turn of the ramp. */
    private static final double RAMP_PERIOD = TWO_PI / RAMP_RADIANS_PER_BLOCK;

    /**
     * What the connectivity spine says about (x, y, z): forced solid (a ramp floor block),
     * forced air (the walkway above one, or a landing chamber), or nothing.
     *
     * <p>The ramp is a helicoid, not a tube: for a column at bearing {@code t} from the
     * axis, the ramp's floor sits at {@code t / RAMP_RADIANS_PER_BLOCK} above the bottom,
     * repeated every {@link #RAMP_PERIOD} blocks of height. Two consequences, both
     * load-bearing:
     * <ul>
     *   <li>The floor is <b>forced solid</b>, so a cathedral chamber crossing the shaft
     *       cannot swallow the walkway and turn the descent into a one-way drop.</li>
     *   <li>The step between neighbouring columns is bounded by
     *       {@code (1 / RAMP_RADIANS_PER_BLOCK) / innerRadius} regardless of what the noise
     *       fields or the wall jitter are doing, so it is always a walk or a one-block
     *       jump -- reversible, hence traversable in both directions.</li>
     * </ul>
     * The bearing wraps at +-PI, but the turn immediately above supplies the floor on the
     * far side of that seam, so the spiral is continuous across it.
     */
    public int shaftState(Shaft[] shafts, int x, int y, int z) {
        int landingVerdict = SHAFT_NONE;
        for (Shaft shaft : shafts) {
            double dx = x - shaft.axisX();
            double dz = z - shaft.axisZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            // Jitter is sampled in 2D on purpose: the annulus a column belongs to must not
            // change with height, or the walkway would develop one-block-tall pinch points
            // at its ragged edge. It only ever widens the ramp, never narrows it.
            double widen = jitter(x, z);

            if (distance >= RAMP_CENTER_RADIUS - RAMP_HALF_WIDTH - widen
                    && distance <= RAMP_CENTER_RADIUS + RAMP_HALF_WIDTH + widen) {
                double bearing = Math.atan2(dz, dx) - shaft.phase();
                bearing -= Math.floor(bearing / TWO_PI) * TWO_PI;
                double base = MIN_Y + bearing / RAMP_RADIANS_PER_BLOCK;
                int turn = (int) Math.floor((y - base) / RAMP_PERIOD);
                for (int k = -1; k <= 1; k++) {
                    int floorY = (int) Math.floor(base + (turn + k) * RAMP_PERIOD);
                    if (y == floorY) {
                        return SHAFT_SOLID;
                    }
                    if (y > floorY && y <= floorY + RAMP_AIR_HEIGHT) {
                        return SHAFT_AIR;
                    }
                }
            }

            if (landingVerdict == SHAFT_NONE && distance <= LANDING_RADIUS + widen) {
                for (int band = 1; band < TIER_COUNT; band++) {
                    int height = y - (MIN_Y + band * TIER_HEIGHT);
                    if (height >= 0 && height < LANDING_INTERIOR_HEIGHT
                            && distance <= landingRadius(height) + widen) {
                        landingVerdict = SHAFT_AIR;
                    } else if (height == -1) {
                        landingVerdict = SHAFT_SOLID;
                    }
                }
            }
        }
        return landingVerdict;
    }

    /**
     * The landing's radius at {@code height} air blocks above its floor disc -- the rounded
     * profile play-test round 2 gave it, in one place so {@link NoiseProbe} measures the
     * shape the carve actually uses.
     *
     * <p>Three segments, bottom to top: a one-block fillet where the wall meets the floor,
     * {@link ColonyGeneratorTunables#LANDING_WALL_HEIGHT} of straight wall at the full
     * radius, then the dome's quarter-ellipse up to
     * {@link ColonyGeneratorTunables#LANDING_INTERIOR_HEIGHT}. The dome is the same
     * {@code radius * sqrt(1 - t^2)} curve {@link #isInsideThrone} and its three siblings
     * use, so the dimension has one dome idiom rather than two.
     *
     * <p>The <b>floor disc itself is deliberately not tapered</b>: it is forced solid at the
     * full radius one block below this, because that disc is where the ramp's walkway crosses
     * the landing and a step there would be a step in the connectivity spine.
     */
    public static double landingRadius(int height) {
        double radius = LANDING_RADIUS;
        if (height > LANDING_WALL_HEIGHT) {
            double t = (double) (height - LANDING_WALL_HEIGHT)
                    / (LANDING_INTERIOR_HEIGHT - LANDING_WALL_HEIGHT);
            radius *= Math.sqrt(Math.max(0.0, 1.0 - t * t));
        }
        return height == 0 ? radius - 1.0 : radius;
    }

    /** Additive-only wall jitter in blocks, always in {@code [0, WALL_JITTER_AMOUNT]}. */
    private double jitter(int x, int z) {
        double n = this.wallJitter.getValue(x * WALL_JITTER_SCALE, 0.0, z * WALL_JITTER_SCALE);
        return WALL_JITTER_AMOUNT * (0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, n)));
    }

    // ------------------------------------------------------------------
    // The colony field (Ep2 D1) -- dense cores, sparse wilds between
    //
    // Deliberately NOT a noise field. A colony has to be findable ("walk 400 blocks and you
    // will hit one"), a throne has to be one per colony, and the boss-bar separation
    // invariant has to be provable -- all three are statements about a MINIMUM spacing,
    // which a Perlin field cannot make. So it is the same jittered grid the connectivity
    // shafts use, and the density falloff is a closed-form function of distance to the
    // nearest centre. See ColonyGeneratorTunables' colony section for the constants and for
    // what f does and does not modulate.
    // ------------------------------------------------------------------

    /** One colony's centre. There is no radius here: the falloff is a pure function of it. */
    public record Colony(double centreX, double centreZ) {
    }

    /**
     * The colony belonging to one {@link ColonyGeneratorTunables#COLONY_SPACING} cell.
     *
     * <p>Identical construction to {@link #shaftForCell}: the cell centre plus a seeded
     * offset of up to {@code COLONY_JITTER / 2} on each axis. That bound is the whole point
     * -- it is what makes the minimum separation between two centres
     * {@code COLONY_SPACING - COLONY_JITTER} = 192 blocks rather than "whatever the noise
     * did", and 192 is the number the boss-bar invariant rests on.
     */
    public Colony colonyCenterForCell(int cellX, int cellZ) {
        // y = 5: a sixth independent stream, so this never draws the same numbers as
        // shaftForCell (0), throneForCell (1), nurseryForCell (2), gardenForCell (3) or
        // larderForCell (4) for the same cell.
        RandomSource random = this.factory.at(cellX, 5, cellZ);
        double half = COLONY_JITTER * 0.5;
        double x = (double) cellX * COLONY_SPACING + COLONY_SPACING * 0.5 + (random.nextDouble() * 2.0 - 1.0) * half;
        double z = (double) cellZ * COLONY_SPACING + COLONY_SPACING * 0.5 + (random.nextDouble() * 2.0 - 1.0) * half;
        return new Colony(x, z);
    }

    /**
     * The 3x3 cell neighbourhood around a point, which is provably every colony whose field
     * can reach it.
     *
     * <p>A centre lands within 48 blocks of its cell centre, so from any point in cell
     * {@code C} the own-cell centre is at most {@code 144*sqrt(2) + 48} = 252 blocks away
     * while a centre two cells out is at least {@code 2*288 - 144 - 48} = 384. The nearest
     * centre is therefore always inside the ring, and since
     * {@link ColonyGeneratorTunables#COLONY_OUTER_RADIUS} is 128 no colony outside it can
     * contribute anything but zero.
     */
    public Colony[] coloniesNear(int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, COLONY_SPACING);
        int cellZ = Math.floorDiv(blockZ, COLONY_SPACING);
        Colony[] out = new Colony[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out[i++] = colonyCenterForCell(cellX + dx, cellZ + dz);
            }
        }
        return out;
    }

    /**
     * The colony density at (x, z) given a candidate set: {@code 1.0} inside a core,
     * smoothstepped to {@code 0.0} by the outer radius.
     *
     * <p>The overload taking the candidates is the one generation uses -- {@code coloniesNear}
     * is nine seeded draws, and a whole chunk shares one answer to "which colonies are
     * around here", so resolving them per column (let alone per block) would be nine
     * {@code RandomSource} allocations for a number that cannot have changed.
     */
    public double colonyField(Colony[] colonies, double x, double z) {
        double bestSq = Double.MAX_VALUE;
        for (Colony colony : colonies) {
            double dx = x - colony.centreX();
            double dz = z - colony.centreZ();
            bestSq = Math.min(bestSq, dx * dx + dz * dz);
        }
        return colonyFalloff(Math.sqrt(bestSq));
    }

    /** The colony density at (x, z), resolving the 3x3 neighbourhood itself. */
    public double colonyField(double x, double z) {
        return colonyField(coloniesNear(Mth.floor(x), Mth.floor(z)), x, z);
    }

    /** The colony whose centre is horizontally nearest (x, z). */
    public Colony nearestColony(double x, double z) {
        Colony[] colonies = coloniesNear(Mth.floor(x), Mth.floor(z));
        Colony best = colonies[0];
        double bestSq = Double.MAX_VALUE;
        for (Colony colony : colonies) {
            double dx = x - colony.centreX();
            double dz = z - colony.centreZ();
            double distanceSq = dx * dx + dz * dz;
            if (distanceSq < bestSq) {
                bestSq = distanceSq;
                best = colony;
            }
        }
        return best;
    }

    /**
     * Whether a chamber centred at (x, z) is inside a colony at all.
     *
     * <p>Evaluated once per chamber, at construction, and carried on the chamber record --
     * not re-derived per block. The test is on the chamber's own centre rather than on its
     * cell's, because the room is what the player walks into: a cell whose centre is in the
     * ring but whose chamber jittered out into the wilds is a room in the wilds.
     */
    public boolean isChamberEligible(double x, double z) {
        return isChamberEligible(x, z, CHAMBER_ELIGIBILITY_MIN_F);
    }

    /** As above, against a chamber kind's own eligibility floor. */
    public boolean isChamberEligible(double x, double z, double minField) {
        return colonyField(x, z) > minField;
    }

    /**
     * Whether a chamber hung off {@code anchor} actually generates: its own centre must be
     * inside a colony, <b>and</b> the ramp it hangs off must be one that got dug.
     *
     * <p>The second half is new in play-test round 2 and is not redundant. Every one of these
     * rooms is reachable "by construction" only in the sense that its corridor lands on its
     * ramp's walkway at the exact height that walkway reaches -- so a room whose ramp is not
     * realized is a room whose only doorway opens onto solid soil, 16 blocks of dead-end
     * corridor from anything. The two tests can genuinely disagree: the chamber centre sits
     * {@code APPROACH_DISTANCE} = 24 blocks from the axis, and the colony falloff moves by up
     * to 0.9 over that distance, so a chamber pointing outward from a fringe ramp can clear
     * the gate while its own ramp does not.
     *
     * <p>This is deliberately a gate on the CHAMBER rather than an exception carved into
     * {@link #isShaftRealized} for ramps that happen to have rooms on them: the spine has to
     * mean the same thing everywhere, and a ramp resurrected out in the wilds because a
     * larder wanted it would be exactly the "descent from nowhere to nowhere" the gate exists
     * to remove.
     */
    private boolean isChamberAnchored(Shaft anchor, double centreX, double centreZ, double minField) {
        return isShaftRealized(anchor) && isChamberEligible(centreX, centreZ, minField);
    }

    // ------------------------------------------------------------------
    // Chamber slots (play-test round 2, item 7) -- where the three 96-grid rooms hang
    // ------------------------------------------------------------------

    /** Slot index of each 96-grid chamber kind around its shared anchor ramp. */
    private static final int SLOT_NURSERY = 0;
    private static final int SLOT_GARDEN = 1;
    private static final int SLOT_LARDER = 2;

    /**
     * The base bearing every 96-grid chamber in one cell measures its slot from.
     *
     * <p>The nursery, garden and larder grids share a cell size (96), a cell centre
     * ({@code cell*96 + 48}), the anchor ramp that centre resolves to, and an approach
     * distance (24). Everything about where their three rooms sit therefore comes down to
     * three approach bearings -- and until round 2 those were three independent-looking draws,
     * the first {@code nextDouble()} of {@code factory.at(cellX, 2|3|4, cellZ)}.
     *
     * <p><b>They were not independent.</b> Measured on the real
     * {@code XoroshiroPositionalRandomFactory}: at cell (0, 0) the three first draws agree to
     * nine decimal places, and over the 17x17 cells the probe samples the mean angular gap
     * between the nursery's and the garden's bearing is <b>6.4 degrees</b> where independent
     * draws would give 90, with 80 of 289 cell-pairs landing within one degree of each other.
     * The cause is in the 1.21 sources, not in the seed: {@code at(x, y, z)} is
     * {@code new XoroshiroRandomSource(Mth.getSeed(x, y, z) ^ seedLo, seedHi)}, {@code y}
     * enters only through {@code Mth.getSeed}'s low three bits, that value is mixed by a
     * single quadratic and shifted right 16, and near the origin the whole pre-mix quantity is
     * small enough that the {@code y} difference survives only in the low bits of
     * {@code seedLo}. {@code seedHi} is byte-identical across the three calls, and Xoroshiro's
     * <em>first</em> output is one rotate-and-add -- not enough diffusion to carry a low-bit
     * difference into the top 53 bits {@code nextDouble} reads. It decorrelates as the cell
     * coordinates grow (mean 33 degrees at |cell| &lt;= 64, 95 at |cell| &lt;= 1024), which is
     * exactly why it was invisible: the bug lives where the player starts.
     *
     * <p>So the separation is no longer asked of the seed. One draw, from one stream, gives
     * the cell a base bearing; the three kinds take fixed slots
     * {@link ColonyGeneratorTunables#CHAMBER_SLOT_STEP} apart from it. Each kind still jitters
     * off its slot using its own stream -- and if those jitters come out nearly equal, as this
     * finding says they often will, the three rooms simply rotate together and the 120-degree
     * gaps survive untouched. Correlated jitter cannot hurt; only a correlated <em>base</em>
     * could, and there is now only one base.
     *
     * <p>{@code y = 7}: an eighth stream, after shaft (0), throne (1), nursery (2), garden
     * (3), larder (4), colony centre (5) and ender slots (6).
     */
    private double chamberBaseBearing(int cellX, int cellZ) {
        return this.factory.at(cellX, 7, cellZ).nextDouble() * TWO_PI;
    }

    /**
     * A chamber kind's approach bearing: its slot off the cell's shared base, plus its own
     * seeded jitter of at most {@link ColonyGeneratorTunables#CHAMBER_SLOT_JITTER}.
     *
     * <p>{@code random} must be the kind's own per-cell stream and this must be its
     * <b>first</b> draw, so the draws that follow (the larder's tier pick and comb angles)
     * keep the ordering their own javadocs describe.
     */
    private static double chamberSlotBearing(double baseBearing, int slot, RandomSource random) {
        return baseBearing + slot * CHAMBER_SLOT_STEP + (random.nextDouble() * 2.0 - 1.0) * CHAMBER_SLOT_JITTER;
    }

    // ------------------------------------------------------------------
    // Seeded ender ants (Ep2 E4) -- the introduction, out on the core's fringe
    // ------------------------------------------------------------------

    /**
     * One ender ant that generation places, as an XZ. There is no Y here: the tier band is
     * fixed (Royal Depths) but the floor inside it is a property of the carve, so
     * {@code ColonyChunkGenerator} reads it off the chunk rather than deriving it.
     */
    public record EnderSlot(double x, double z) {
    }

    /**
     * The two or three fringe positions one colony seeds an ender ant at.
     *
     * <p>Slots, not a per-chunk probability, and for the reason
     * {@code spawnQueenIfThisChunkHoldsAThrone} places the queen the way it does: "2-3 per
     * colony" is a statement about a colony, and a chunk-local dice roll can only ever
     * approximate it. Making the positions a pure function of the cell means the chunk that
     * happens to contain one seeds it, exactly once, with no marker block and no saved flag.
     *
     * <p>Each slot lands on a ring whose radius is drawn from
     * {@code [ENDER_SEED_INNER_RADIUS, ENDER_SEED_OUTER_RADIUS]} -- the distances at which
     * the field is {@code ENDER_SEED_MAX_F} and {@code ENDER_SEED_MIN_F}. The field there is
     * that colony's own, not a neighbour's: the ring tops out at about 91 blocks from the
     * centre while the nearest other centre is at least {@code COLONY_SPACING -
     * COLONY_JITTER} = 192 away, so no other colony can be closer than 101. The bearings are
     * spread evenly with a jitter inside each sector rather than drawn independently, so two
     * of three ants never land on top of each other.
     */
    public EnderSlot[] enderSlotsForCell(int cellX, int cellZ) {
        // y = 6: a seventh independent stream, after shaft (0), throne (1), nursery (2),
        // garden (3), larder (4) and the colony centre itself (5).
        RandomSource random = this.factory.at(cellX, 6, cellZ);
        Colony colony = colonyCenterForCell(cellX, cellZ);
        int count = between(COLONY_ENDER_ANTS_MIN, COLONY_ENDER_ANTS_MAX, random);
        EnderSlot[] slots = new EnderSlot[count];
        for (int i = 0; i < count; i++) {
            double bearing = (i + random.nextDouble()) * TWO_PI / count;
            double radius = ENDER_SEED_INNER_RADIUS
                    + random.nextDouble() * (ENDER_SEED_OUTER_RADIUS - ENDER_SEED_INNER_RADIUS);
            slots[i] = new EnderSlot(colony.centreX() + Math.cos(bearing) * radius,
                    colony.centreZ() + Math.sin(bearing) * radius);
        }
        return slots;
    }

    /**
     * Every seeded slot that could fall in the chunk containing {@code (blockX, blockZ)}.
     *
     * <p>The 3x3 cell neighbourhood is more than enough by the same argument
     * {@link #coloniesNear} makes: a slot sits within about 131 blocks of its own colony
     * centre, and every centre that close is inside the ring.
     */
    public EnderSlot[] enderSlotsNear(int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, COLONY_SPACING);
        int cellZ = Math.floorDiv(blockZ, COLONY_SPACING);
        List<EnderSlot> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Collections.addAll(out, enderSlotsForCell(cellX + dx, cellZ + dz));
            }
        }
        return out.toArray(new EnderSlot[0]);
    }

    // ------------------------------------------------------------------
    // The queen's throne chamber (M7, re-anchored to the colony in Ep2)
    // ------------------------------------------------------------------

    /**
     * One throne chamber: a domed room centred at ({@code centreX}, {@code centreZ}) whose
     * floor is at {@code floorY}, joined to the connectivity ramp at
     * ({@code axisX}, {@code axisZ}) by a straight corridor running along the unit vector
     * ({@code dirX}, {@code dirZ}).
     */
    public record Throne(double centreX, double centreZ, double axisX, double axisZ,
            double dirX, double dirZ, int floorY) {
    }

    /** {@link #throneState} verdicts. */
    public static final int THRONE_NONE = 0;
    public static final int THRONE_AIR = 1;
    public static final int THRONE_SOLID = -1;
    /** The raised platform the queen is seated on -- solid, but a different block. */
    public static final int THRONE_DAIS = -2;

    public static final Throne[] NO_THRONES = new Throne[0];

    /**
     * The chamber belonging to one colony -- one per
     * {@link ColonyGeneratorTunables#COLONY_SPACING} cell, which is the same cell the colony
     * itself lives in, so <b>exactly one throne (and one queen) per colony by
     * construction</b>. Before Ep2 this was its own 224-block grid whose relationship to
     * anything else was a coincidence of periods.
     *
     * <p>Its XZ is <em>derived from a connectivity ramp</em> rather than jittered freely,
     * and that is the whole trick: the chamber sits {@code THRONE_APPROACH_DISTANCE} from
     * the ramp axis at a seed-chosen bearing, and its floor is set to the exact height the
     * ramp's walkway reaches at that bearing. So the approach corridor -- carved flat at
     * that height, straight back to the axis -- lands on the spine's walkway, and the room
     * is reachable by construction. Placing the room at a free XZ instead would have made
     * "can the player get in?" a property of the noise, which is exactly the sort of thing
     * the M4a walkability work established cannot be assumed.
     *
     * <p>What the Ep2 re-anchor changed is only <em>which</em> ramp: the one whose own cell
     * contains the COLONY CENTRE, instead of the one containing an abstract 224-grid point.
     * The floor-Y arithmetic below is ramp-relative and is untouched. The offset that
     * introduces is bounded and small: the ramp axis is at most
     * {@code 24*sqrt(2) + SHAFT_JITTER/2} = 41.9 blocks from the colony centre and the room
     * hangs {@code THRONE_APPROACH_DISTANCE} = 34 beyond it, so a throne centre is always
     * within 76 blocks of its colony centre -- which is exactly
     * {@link ColonyGeneratorTunables#COLONY_CORE_RADIUS} after round 3, i.e. always at full
     * density, provably and on every seed.
     *
     * <p><b>Round 3 opened by taking the core to 64 and measured what that costs.</b> 1.5-1.8%
     * of thrones landed outside their own core, and the round-2 note that the worst measured
     * offset was 62.4 turned out to be a 169-colony artifact -- widened to 3721 colonies per
     * seed the same distribution reaches 72.3-73.2. The core went back to the algebraic bound
     * rather than to the smallest value that happened to measure clean; see
     * {@code COLONY_CORE_RADIUS} for that decision. {@link NoiseProbe}'s colony section
     * asserts the containment on every run regardless, which is what caught it.
     *
     * <p>The ramp floor at bearing {@code t} is {@code MIN_Y + t / RAMP_RADIANS_PER_BLOCK}
     * plus any whole number of {@link #RAMP_PERIOD}s; the turn taken is the first one at or
     * above {@link ColonyGeneratorTunables#THRONE_FLOOR_MIN_Y}, which keeps the whole room
     * (13 of interior plus its shell) inside the Royal Depths band.
     *
     * <p><b>Not gated on {@link #isShaftRealized}</b>, unlike the three 96-grid chambers,
     * because for a throne the gate is provably a tautology: this ramp's cell is the one
     * containing the COLONY CENTRE, so its axis lands within
     * {@code SHAFT_SPACING/2 * sqrt(2) + SHAFT_JITTER/2} = 41.9 blocks of a point where the
     * field is 1.0 -- still well inside {@link ColonyGeneratorTunables#COLONY_CORE_RADIUS} = 76
     * after round 3's compaction, where the field is exactly 1.0. Writing the test anyway would let "one throne per
     * colony" quietly become "one throne per colony, usually"; {@link NoiseProbe} asserts the
     * tautology instead, which is the honest place for it.
     */
    private Throne throneForCell(int cellX, int cellZ) {
        // y = 1 rather than 0 so this never draws the same stream as shaftForCell -- and
        // rather than 5, so the approach bearing is independent of the colony's own jitter.
        RandomSource random = this.factory.at(cellX, 1, cellZ);
        Colony colony = colonyCenterForCell(cellX, cellZ);
        int centreX = Mth.floor(colony.centreX());
        int centreZ = Mth.floor(colony.centreZ());
        Shaft shaft = shaftForCell(Math.floorDiv(centreX, SHAFT_SPACING), Math.floorDiv(centreZ, SHAFT_SPACING));

        double approach = random.nextDouble() * TWO_PI;
        double dirX = Math.cos(approach);
        double dirZ = Math.sin(approach);

        // Same bearing arithmetic shaftState uses, so the two agree on where the floor is.
        double bearing = approach - shaft.phase();
        bearing -= Math.floor(bearing / TWO_PI) * TWO_PI;
        double base = MIN_Y + bearing / RAMP_RADIANS_PER_BLOCK;
        int turn = (int) Math.ceil((THRONE_FLOOR_MIN_Y - base) / RAMP_PERIOD);
        int floorY = (int) Math.floor(base + turn * RAMP_PERIOD);

        return new Throne(shaft.axisX() + THRONE_APPROACH_DISTANCE * dirX,
                shaft.axisZ() + THRONE_APPROACH_DISTANCE * dirZ,
                shaft.axisX(), shaft.axisZ(), dirX, dirZ, floorY);
    }

    /**
     * Every throne chamber that can possibly reach into the 16x16 area rooted at
     * ({@code blockMinX}, {@code blockMinZ}).
     *
     * <p>A 3x3 cell neighbourhood is far more than enough -- a chamber's centre lands
     * within 124 blocks of its cell centre (48 of colony jitter, 42 to the ramp axis, 34 of
     * approach) and reaches {@link ColonyGeneratorTunables#THRONE_MAX_REACH} from there, so
     * no chamber ever crosses a 384-block cell boundary -- but it is kept at 3x3 so the
     * pruning stays correct if those numbers are retuned.
     */
    public Throne[] thronesNear(int blockMinX, int blockMinZ) {
        int cellX = Math.floorDiv(blockMinX, COLONY_SPACING);
        int cellZ = Math.floorDiv(blockMinZ, COLONY_SPACING);
        Throne[] out = new Throne[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out[i++] = throneForCell(cellX + dx, cellZ + dz);
            }
        }
        return out;
    }

    /** Chambers from {@code candidates} whose carve can reach the column at (x, z). */
    public Throne[] thronesForColumn(Throne[] candidates, int x, int z) {
        int count = 0;
        Throne[] scratch = new Throne[candidates.length];
        for (Throne throne : candidates) {
            double dx = x - throne.centreX();
            double dz = z - throne.centreZ();
            if (dx * dx + dz * dz <= THRONE_MAX_REACH * THRONE_MAX_REACH) {
                scratch[count++] = throne;
            }
        }
        if (count == 0) {
            return NO_THRONES;
        }
        Throne[] out = new Throne[count];
        System.arraycopy(scratch, 0, out, 0, count);
        return out;
    }

    /**
     * What a throne chamber says about (x, y, z): the dais, the shell, the hollow interior,
     * the approach corridor, or nothing.
     *
     * <p>Order matters. The dais outranks the interior (it is a plinth standing in the
     * room), and the corridor outranks the shell (it is the doorway through it).
     *
     * <p><b>The corridor's own floor is forced solid (Ep2)</b>, exactly as
     * {@link #nurseryState} has always done it: {@code height == 0} inside the corridor
     * footprint is {@link #THRONE_SOLID}, not left to the noise. See that method's javadoc
     * for why the old asymmetry no longer holds. Still subordinate to {@link #shaftState},
     * which {@link #isAir} consults first and which agrees with it by construction -- the
     * chamber's floor Y <em>is</em> the ramp's walkway height at that bearing.
     */
    public int throneState(Throne[] thrones, int x, int y, int z) {
        for (Throne throne : thrones) {
            double dx = x - throne.centreX();
            double dz = z - throne.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            int height = y - throne.floorY();

            if (height >= 1 && height <= THRONE_DAIS_HEIGHT && distance <= THRONE_DAIS_RADIUS) {
                return THRONE_DAIS;
            }
            // One-block step ring, so the plinth can be climbed rather than only fallen off.
            if (height == 1 && distance <= THRONE_DAIS_STEP_RADIUS) {
                return THRONE_DAIS;
            }
            if (height >= 0 && height <= THRONE_CORRIDOR_HEIGHT && isInCorridor(throne, x, z)) {
                return height == 0 ? THRONE_SOLID : THRONE_AIR;
            }
            if (isInsideThrone(distance, height, 0.0)) {
                return THRONE_AIR;
            }
            if (isInsideThrone(distance, height, THRONE_SHELL_THICKNESS)) {
                return THRONE_SOLID;
            }
        }
        return THRONE_NONE;
    }

    /**
     * The room's shape: a cylinder of {@link ColonyGeneratorTunables#THRONE_RADIUS} topped
     * by a half-ellipsoid dome. {@code grow} inflates it in every direction at once, which
     * is how the shell is derived -- "inside the grown shape but not the real one" -- so
     * the wall thickness never has to be reasoned about face by face, and the floor slab
     * below {@code height = 1} comes out of the same expression.
     */
    private static boolean isInsideThrone(double distance, double height, double grow) {
        double radius = THRONE_RADIUS + grow;
        double dome = THRONE_DOME_HEIGHT + grow;
        if (height < 1.0 - grow || height > THRONE_WALL_HEIGHT + dome) {
            return false;
        }
        if (height <= THRONE_WALL_HEIGHT) {
            return distance <= radius;
        }
        double t = (height - THRONE_WALL_HEIGHT) / dome;
        return distance <= radius * Math.sqrt(Math.max(0.0, 1.0 - t * t));
    }

    /** Whether (x, z) is inside the corridor's footprint, measured along the axis ray. */
    private static boolean isInCorridor(Throne throne, int x, int z) {
        double px = x - throne.axisX();
        double pz = z - throne.axisZ();
        double along = px * throne.dirX() + pz * throne.dirZ();
        if (along < THRONE_CORRIDOR_START || along > THRONE_CORRIDOR_END) {
            return false;
        }
        return Math.abs(px * throne.dirZ() - pz * throne.dirX()) <= THRONE_CORRIDOR_HALF_WIDTH;
    }

    /** Whether the solid block at (x, y, z) is part of a throne chamber's dais. */
    public boolean isThroneDais(Throne[] thrones, int x, int y, int z) {
        return thrones.length > 0 && throneState(thrones, x, y, z) == THRONE_DAIS;
    }

    /** Whether (x, y, z) is inside a throne chamber's hollow (used to pick decoration). */
    public boolean isInThroneRoom(Throne[] thrones, int x, int y, int z) {
        if (thrones.length == 0) {
            return false;
        }
        for (Throne throne : thrones) {
            double dx = x - throne.centreX();
            double dz = z - throne.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double height = y - throne.floorY();
            if (isInsideThrone(distance, height, THRONE_SHELL_THICKNESS)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Nursery chambers (play-test round 1)
    // ------------------------------------------------------------------

    /**
     * One brood chamber: a small domed room in the Nurseries tier, built exactly like
     * {@link Throne} -- centred at ({@code centreX}, {@code centreZ}) with its floor at
     * {@code floorY}, joined to the connectivity ramp at ({@code axisX}, {@code axisZ}) by a
     * straight corridor along the unit vector ({@code dirX}, {@code dirZ}).
     *
     * <p>{@code inColony} is the Ep2 colony-field gate, evaluated once here rather than per
     * block: false means this cell's chamber fell in the wilds and does not generate at all.
     * It rides on the record so that {@link #nurseriesNear} can keep returning a fixed 3x3
     * in cell order (the probe and the GameTests index into it) while every consumer still
     * gets the gate for free.
     */
    public record Nursery(double centreX, double centreZ, double axisX, double axisZ,
            double dirX, double dirZ, int floorY, boolean inColony) {
    }

    /** {@link #nurseryState} verdicts. */
    public static final int NURSERY_NONE = 0;
    public static final int NURSERY_AIR = 1;
    public static final int NURSERY_SOLID = -1;

    public static final Nursery[] NO_NURSERIES = new Nursery[0];

    /**
     * The chamber belonging to one {@link ColonyGeneratorTunables#NURSERY_SPACING} cell.
     *
     * <p>Structurally identical to {@link #throneForCell}: the ramp whose own cell contains
     * this cell's centre is resolved first, and the room then hangs off that ramp at a
     * seed-chosen bearing with its floor set to the ramp's own walkway height there.
     *
     * <p>The floor is the first ramp turn at or above
     * {@link ColonyGeneratorTunables#NURSERY_FLOOR_MIN_Y}, which puts it in {@code [56, 80)}
     * -- the room and its shell therefore stay inside the Nurseries band, and can never
     * collide with a throne chamber (whose floors live in {@code [8, 33)}).
     *
     * <p>The approach bearing is slot {@link #SLOT_NURSERY} off this cell's shared base
     * bearing -- see {@link #chamberBaseBearing} for why it is no longer a free draw.
     */
    private Nursery nurseryForCell(int cellX, int cellZ) {
        // y = 2: this cell's nursery stream. Its first draw is now the slot jitter rather
        // than the whole bearing; see chamberBaseBearing for what that fixed.
        RandomSource random = this.factory.at(cellX, 2, cellZ);
        int centreX = cellX * NURSERY_SPACING + NURSERY_SPACING / 2;
        int centreZ = cellZ * NURSERY_SPACING + NURSERY_SPACING / 2;
        Shaft shaft = shaftForCell(Math.floorDiv(centreX, SHAFT_SPACING), Math.floorDiv(centreZ, SHAFT_SPACING));

        double approach = chamberSlotBearing(chamberBaseBearing(cellX, cellZ), SLOT_NURSERY, random);
        double dirX = Math.cos(approach);
        double dirZ = Math.sin(approach);

        double bearing = approach - shaft.phase();
        bearing -= Math.floor(bearing / TWO_PI) * TWO_PI;
        double base = MIN_Y + bearing / RAMP_RADIANS_PER_BLOCK;
        int turn = (int) Math.ceil((NURSERY_FLOOR_MIN_Y - base) / RAMP_PERIOD);
        int floorY = (int) Math.floor(base + turn * RAMP_PERIOD);

        double centreXd = shaft.axisX() + NURSERY_APPROACH_DISTANCE * dirX;
        double centreZd = shaft.axisZ() + NURSERY_APPROACH_DISTANCE * dirZ;
        return new Nursery(centreXd, centreZd, shaft.axisX(), shaft.axisZ(), dirX, dirZ, floorY,
                isChamberAnchored(shaft, centreXd, centreZd, CHAMBER_ELIGIBILITY_MIN_F));
    }

    /**
     * Every nursery chamber that can possibly reach into the 16x16 area rooted at
     * ({@code blockMinX}, {@code blockMinZ}).
     *
     * <p>3x3 is provably enough, and unlike the throne's the margin here is worth writing
     * down because the cell is small. A chamber's centre lands within
     * {@code SHAFT_SPACING/2 + SHAFT_JITTER/2 + NURSERY_APPROACH_DISTANCE} = 56 blocks of
     * its cell centre and its carve reaches
     * {@link ColonyGeneratorTunables#NURSERY_MAX_REACH} = 23 from there, so 79 blocks from
     * the cell centre at worst. A chunk lies wholly inside one cell (both 16 and 96 are
     * multiples of 16) and so within 48 blocks of that cell's centre. Two cells away is 192
     * blocks: {@code 192 - 79 = 113 > 48}, no overlap. One cell away is 96:
     * {@code 96 - 79 = 17 < 48}, so the ring of eight neighbours is genuinely needed.
     */
    public Nursery[] nurseriesNear(int blockMinX, int blockMinZ) {
        int cellX = Math.floorDiv(blockMinX, NURSERY_SPACING);
        int cellZ = Math.floorDiv(blockMinZ, NURSERY_SPACING);
        Nursery[] out = new Nursery[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out[i++] = nurseryForCell(cellX + dx, cellZ + dz);
            }
        }
        return out;
    }

    /**
     * Chambers from {@code candidates} whose carve can reach the column at (x, z), skipping
     * the ones the colony field gated out -- they carve nothing, so keeping them would only
     * make every consumer re-ask the same question per block.
     */
    public Nursery[] nurseriesForColumn(Nursery[] candidates, int x, int z) {
        int count = 0;
        Nursery[] scratch = new Nursery[candidates.length];
        for (Nursery nursery : candidates) {
            if (!nursery.inColony()) {
                continue;
            }
            double dx = x - nursery.centreX();
            double dz = z - nursery.centreZ();
            if (dx * dx + dz * dz <= NURSERY_MAX_REACH * NURSERY_MAX_REACH) {
                scratch[count++] = nursery;
            }
        }
        if (count == 0) {
            return NO_NURSERIES;
        }
        Nursery[] out = new Nursery[count];
        System.arraycopy(scratch, 0, out, 0, count);
        return out;
    }

    // ------------------------------------------------------------------
    // Fungus Garden chambers (Ep2 D2) -- cloned end to end from the Nursery section
    // above; see ColonyGeneratorTunables' garden section for the constant derivation.
    // ------------------------------------------------------------------

    /**
     * One farm chamber: a small domed room in the Fungal Gardens tier, built exactly like
     * {@link Nursery} -- centred at ({@code centreX}, {@code centreZ}) with its floor at
     * {@code floorY}, joined to the connectivity ramp at ({@code axisX}, {@code axisZ}) by a
     * straight corridor along the unit vector ({@code dirX}, {@code dirZ}), and gated by the
     * same {@code inColony} colony-field test.
     */
    public record Garden(double centreX, double centreZ, double axisX, double axisZ,
            double dirX, double dirZ, int floorY, boolean inColony) {
    }

    /** {@link #gardenState} verdicts. */
    public static final int GARDEN_NONE = 0;
    public static final int GARDEN_AIR = 1;
    public static final int GARDEN_SOLID = -1;

    public static final Garden[] NO_GARDENS = new Garden[0];

    /**
     * The chamber belonging to one {@link ColonyGeneratorTunables#GARDEN_SPACING} cell.
     *
     * <p>Structurally identical to {@link #nurseryForCell}: the ramp whose own cell
     * contains this cell's centre is resolved first, and the room then hangs off that ramp
     * at a seed-chosen bearing with its floor set to the ramp's own walkway height there.
     *
     * <p>The floor is the first ramp turn at or above
     * {@link ColonyGeneratorTunables#GARDEN_FLOOR_MIN_Y}, which puts it in
     * {@code [104, 128)} -- the room and its shell therefore stay inside the Fungal
     * Gardens band, and can never collide with a throne or nursery chamber (whose floors
     * live in the disjoint bands {@code [8, 33)} and {@code [56, 80)}).
     *
     * <p>The approach bearing is slot {@link #SLOT_GARDEN} off this cell's shared base
     * bearing, i.e. a third of a turn round from its nursery -- see
     * {@link #chamberBaseBearing}.
     */
    private Garden gardenForCell(int cellX, int cellZ) {
        // y = 3: this cell's garden stream; first draw is the slot jitter.
        RandomSource random = this.factory.at(cellX, 3, cellZ);
        int centreX = cellX * GARDEN_SPACING + GARDEN_SPACING / 2;
        int centreZ = cellZ * GARDEN_SPACING + GARDEN_SPACING / 2;
        Shaft shaft = shaftForCell(Math.floorDiv(centreX, SHAFT_SPACING), Math.floorDiv(centreZ, SHAFT_SPACING));

        double approach = chamberSlotBearing(chamberBaseBearing(cellX, cellZ), SLOT_GARDEN, random);
        double dirX = Math.cos(approach);
        double dirZ = Math.sin(approach);

        double bearing = approach - shaft.phase();
        bearing -= Math.floor(bearing / TWO_PI) * TWO_PI;
        double base = MIN_Y + bearing / RAMP_RADIANS_PER_BLOCK;
        int turn = (int) Math.ceil((GARDEN_FLOOR_MIN_Y - base) / RAMP_PERIOD);
        int floorY = (int) Math.floor(base + turn * RAMP_PERIOD);

        double centreXd = shaft.axisX() + GARDEN_APPROACH_DISTANCE * dirX;
        double centreZd = shaft.axisZ() + GARDEN_APPROACH_DISTANCE * dirZ;
        return new Garden(centreXd, centreZd, shaft.axisX(), shaft.axisZ(), dirX, dirZ, floorY,
                isChamberAnchored(shaft, centreXd, centreZd, CHAMBER_ELIGIBILITY_MIN_F));
    }

    /**
     * Every garden chamber that can possibly reach into the 16x16 area rooted at
     * ({@code blockMinX}, {@code blockMinZ}). Same 3x3 justification as
     * {@link #nurseriesNear}: GARDEN_SPACING matches NURSERY_SPACING exactly and
     * GARDEN_MAX_REACH is close to the nursery's own value, so the margin argument
     * carries over unchanged.
     */
    public Garden[] gardensNear(int blockMinX, int blockMinZ) {
        int cellX = Math.floorDiv(blockMinX, GARDEN_SPACING);
        int cellZ = Math.floorDiv(blockMinZ, GARDEN_SPACING);
        Garden[] out = new Garden[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out[i++] = gardenForCell(cellX + dx, cellZ + dz);
            }
        }
        return out;
    }

    /** As {@link #nurseriesForColumn}: within reach, and not gated out by the colony field. */
    public Garden[] gardensForColumn(Garden[] candidates, int x, int z) {
        int count = 0;
        Garden[] scratch = new Garden[candidates.length];
        for (Garden garden : candidates) {
            if (!garden.inColony()) {
                continue;
            }
            double dx = x - garden.centreX();
            double dz = z - garden.centreZ();
            if (dx * dx + dz * dz <= GARDEN_MAX_REACH * GARDEN_MAX_REACH) {
                scratch[count++] = garden;
            }
        }
        if (count == 0) {
            return NO_GARDENS;
        }
        Garden[] out = new Garden[count];
        System.arraycopy(scratch, 0, out, 0, count);
        return out;
    }

    // ------------------------------------------------------------------
    // Larder chambers (Ep2 D3) -- cloned end to end from the Nursery/Garden sections
    // above; see ColonyGeneratorTunables' larder section for the constant derivation.
    // ------------------------------------------------------------------

    /**
     * One food-storage chamber: a small domed room in a tier the cell picks, built
     * exactly like {@link Garden} -- centred at ({@code centreX}, {@code centreZ}) with its
     * floor at {@code floorY}, joined to the connectivity ramp at
     * ({@code axisX}, {@code axisZ}) by a straight corridor along the unit vector
     * ({@code dirX}, {@code dirZ}).
     *
     * <p>{@code combX}/{@code combZ} are the {@link ColonyGeneratorTunables#LARDER_PROVISION_COMB_MIN}-
     * {@link ColonyGeneratorTunables#LARDER_PROVISION_COMB_MAX} guaranteed Provision Comb
     * positions (play-test round 2: the per-wall-block chance measured over 10 per larder
     * in play and is gone outright -- every comb in a larder is now one of these), drawn
     * from the SAME random stream as the chamber's own geometry, immediately after the
     * approach bearing -- so a larder's shape and its guaranteed loot are one seeded draw,
     * not two that could silently disagree on a re-derivation. They cannot be a
     * probabilistic top-up: {@code ColonyChunkGenerator#decorateLarderSurface} sees one
     * wall block at a time as the per-chunk decoration pass visits it and has no way to
     * count what it has already placed across a room that can span several chunks, so the
     * only way to GUARANTEE a count is to pick the positions up front and force-write them
     * regardless of what the rest of the room's decoration does. Each position sits
     * {@link ColonyGeneratorTunables#LARDER_COMB_RADIUS} out (one block past the interior
     * radius, safely inside the forced-solid shell even after independent per-axis
     * rounding from a non-integer centre) at a fixed
     * {@link ColonyGeneratorTunables#LARDER_COMB_HEIGHT} above the floor (well inside the
     * cylindrical wall, clear of the dome's curve).
     */
    /**
     * @param tier     the band this larder is actually dug into
     * @param tierDraw the band the cell <em>drew</em>, before the throne gate had its say.
     *                 They differ only where the gate fired, which is what lets
     *                 {@link NoiseProbe} report how often it does without a second copy of the
     *                 draw order living somewhere it could drift out of step.
     */
    public record Larder(double centreX, double centreZ, double axisX, double axisZ,
            double dirX, double dirZ, int floorY, int tier, int tierDraw,
            int[] combX, int[] combZ, int combY,
            boolean inColony) {
    }

    /** {@link #larderState} verdicts. */
    public static final int LARDER_NONE = 0;
    public static final int LARDER_AIR = 1;
    public static final int LARDER_SOLID = -1;

    public static final Larder[] NO_LARDERS = new Larder[0];

    /**
     * The chamber belonging to one {@link ColonyGeneratorTunables#LARDER_SPACING} cell.
     *
     * <p>Structurally identical to {@link #gardenForCell}: the ramp whose own cell contains
     * this cell's centre is resolved first, and the room then hangs off that ramp with its
     * floor set to the ramp's own walkway height at the approach bearing. Two things are the
     * larder's alone, and both are play-test round 2, item 7.
     *
     * <p><b>It can be dug into any tier.</b> The floor used to be the first ramp turn at or
     * above one hard number, so every larder in the world was in the top tier. The cell
     * now picks a tier uniformly from {@code [0, LARDER_TIER_COUNT)} and the floor is the
     * first ramp turn at or above {@link ColonyGeneratorTunables#CHAMBER_FLOOR_MIN_Y_BY_TIER}
     * for that tier -- {@code [8, 33)}, {@code [56, 80)}, {@code [104, 128)} or
     * {@code [152, 177)}, each keeping the room and its shell wholly inside its band.
     *
     * <p><b>Which means the bands stopped separating the rooms.</b> Before this, the four
     * chamber kinds could not interact because their floors lived in four disjoint bands --
     * and that was doing more work than it looked, because the nursery, garden and larder
     * grids all have the same 96-block cell with the same {@code cell*96 + 48} centre, so all
     * three resolve the <em>same</em> anchor ramp and hang at the same 24-block approach
     * distance. Their bearings were three separate positional-stream draws, which sounds like
     * three independent directions and measurably was not: on seed 1234567 the three rooms of
     * a cell landed within about 1.5 blocks of each other in XZ, kept apart by 48 blocks of
     * height and nothing else. (The RNG finding behind that is written up on
     * {@link #chamberBaseBearing}.) A larder that leaves its band would have walked straight
     * into whatever else the cell had.
     *
     * <p>So the three kinds are now placed at explicit slots -- nursery at
     * {@code base + 0}, garden at {@code base + 2pi/3}, larder at {@code base + 4pi/3} off one
     * shared per-cell base bearing, each jittered by at most
     * {@link ColonyGeneratorTunables#CHAMBER_SLOT_JITTER}. Worst case the two nearest are
     * {@link ColonyGeneratorTunables#CHAMBER_SLOT_MIN_SEPARATION} = 30.85 blocks apart against
     * the 22.0 they need; {@link NoiseProbe}'s chamber-pair invariant measures the real
     * minimum rather than trusting the algebra.
     *
     * <p><b>The throne guard.</b> One collision the slots cannot settle: the throne is not on
     * the 96 grid, has no slot, and lives in tier 0, so a tier-0 larder is a second room in
     * the Royal Depths with nothing arranging the two. The gate is a distance, not a
     * relationship between cells -- a tier-0 pick is bumped to tier 1 whenever the room would
     * land within {@link ColonyGeneratorTunables#THRONE_LARDER_CLEARANCE} = 62.5 blocks of its
     * colony's throne centre, which is the two chambers' carve envelopes plus 2 blocks of
     * margin and therefore cannot be beaten by any seed. Bumping rather than re-drawing keeps
     * the tier a pure function of the cell.
     *
     * <p>A guard on the anchor ramp's <em>cell</em> was tried first and measurably was not
     * enough: it removed every same-shaft case and left 3 larders on seed 1234567 and 10 on
     * seed 987654321 overlapping a throne from <em>one cell away</em>, worst pair 3.8 blocks
     * centre to centre. Thrones hang off the ramp cell containing the colony centre, which is
     * even as often as odd, while larders only ever anchor to odd/odd cells -- so the throne
     * is frequently on a ramp no larder can share, one cell from several that do. The distance
     * bound covers the same-shaft case too (those centres are 10 to 58 apart), so there is no
     * separate shaft test.
     */
    private Larder larderForCell(int cellX, int cellZ) {
        // y = 4: this cell's larder stream. Draw order is load-bearing and is asserted
        // nowhere but here: slot jitter, then the tier, then the two comb angles.
        RandomSource random = this.factory.at(cellX, 4, cellZ);
        int centreX = cellX * LARDER_SPACING + LARDER_SPACING / 2;
        int centreZ = cellZ * LARDER_SPACING + LARDER_SPACING / 2;
        Shaft shaft = shaftForCell(Math.floorDiv(centreX, SHAFT_SPACING), Math.floorDiv(centreZ, SHAFT_SPACING));

        double approach = chamberSlotBearing(chamberBaseBearing(cellX, cellZ), SLOT_LARDER, random);
        double dirX = Math.cos(approach);
        double dirZ = Math.sin(approach);

        // Resolved before the tier, because the tier gate is a question about where the room
        // lands. Neither depends on floorY, so nothing here is circular.
        double centreXd = shaft.axisX() + LARDER_APPROACH_DISTANCE * dirX;
        double centreZd = shaft.axisZ() + LARDER_APPROACH_DISTANCE * dirZ;

        int tierDraw = random.nextInt(LARDER_TIER_COUNT);
        int tier = tierDraw;
        if (tier == 0 && crowdsAThrone(centreXd, centreZd)) {
            tier = 1;
        }

        double bearing = approach - shaft.phase();
        bearing -= Math.floor(bearing / TWO_PI) * TWO_PI;
        double base = MIN_Y + bearing / RAMP_RADIANS_PER_BLOCK;
        int turn = (int) Math.ceil((CHAMBER_FLOOR_MIN_Y_BY_TIER[tier] - base) / RAMP_PERIOD);
        int floorY = (int) Math.floor(base + turn * RAMP_PERIOD);

        // The guaranteed comb slots -- same stream, drawn right after the bearing. NOT a
        // free choice of angle: the corridor punches through the shell at the bearing
        // facing back toward the ramp axis (`approach + PI`, seen from the centre), and an
        // early version of this method drew angles uniformly over the full circle --
        // NoiseProbe's larder section caught 22 of 578 sampled slots (3.8%) landing inside
        // that doorway, where they would have blocked the room's only entrance instead of
        // decorating its shell. Round 2 raised the count from a fixed 2 to 5-7 (drawn
        // below), which does not fit the old anchor-and-jitter shape -- there is no room
        // left to anchor seven slots 90 degrees off one bearing -- so the derivation
        // generalizes instead: every slot's bearing is drawn from the arc that keeps
        // LARDER_COMB_DOORWAY_EXCLUSION clear on both sides of the doorway, split into
        // `count` equal sectors with the slot placed anywhere inside its own sector (the
        // same full-sector-jitter scheme `enderSlotsForCell` already uses for its own
        // variable count). No slot can land in the excluded zone regardless of `count` or
        // the RNG, by construction rather than by margin -- see
        // LARDER_COMB_DOORWAY_EXCLUSION's own javadoc for the clearance arithmetic.
        int combCount = between(LARDER_PROVISION_COMB_MIN, LARDER_PROVISION_COMB_MAX, random);
        double doorwayBearing = approach + Math.PI;
        double arcStart = doorwayBearing + LARDER_COMB_DOORWAY_EXCLUSION;
        double usableArc = TWO_PI - 2.0 * LARDER_COMB_DOORWAY_EXCLUSION;
        int[] combX = new int[combCount];
        int[] combZ = new int[combCount];
        for (int i = 0; i < combCount; i++) {
            double angle = arcStart + (i + random.nextDouble()) * usableArc / combCount;
            int[] slot = innerShellSlot(centreXd, centreZd, angle);
            combX[i] = slot[0];
            combZ[i] = slot[1];
        }

        return new Larder(centreXd, centreZd, shaft.axisX(), shaft.axisZ(), dirX, dirZ, floorY, tier, tierDraw,
                combX, combZ, floorY + LARDER_COMB_HEIGHT,
                isChamberAnchored(shaft, centreXd, centreZd, LARDER_ELIGIBILITY_MIN_F));
    }

    /**
     * The block a Provision Comb slot at {@code angle} actually goes in: the <b>innermost</b>
     * block of the larder's shell that has a face onto the room, rather than whatever block
     * a fixed radius happens to round to.
     *
     * <p>This is the round-3 fix for "the larder has three combs in it". The slots were
     * placed at {@link ColonyGeneratorTunables#LARDER_COMB_RADIUS} = 8 and checked for being
     * inside the shell, which spans {@code (7, 9]} -- and that check passed, on every slot of
     * every sampled larder, for two rounds. Inside the shell is not the same claim as on the
     * inside face of it: rounding two independent integer coordinates off a non-integer
     * centre puts the true distance anywhere in about {@code [7.3, 8.7]}, and the far half of
     * that band has a block of plain fabric between the comb and the room. Measured by
     * {@code NoiseProbe#larders}: 599 of 599 slots written, and only 223 of them visible --
     * a mean of <b>2.2 combs per larder</b> against the 5-7 placed, which is the count the
     * play-test reported.
     *
     * <p>Walking outward and taking the first block that is both shell and room-facing makes
     * visibility a property of the construction rather than of the rounding. The
     * quarter-block step is finer than the lattice, so no candidate block on the ray is
     * skipped. Only the four horizontal neighbours are consulted, and that is not a
     * simplification: at {@link ColonyGeneratorTunables#LARDER_COMB_HEIGHT} the room is a
     * cylinder, so the blocks above and below a slot share its distance from the axis exactly
     * and are shell whenever it is.
     */
    private static int[] innerShellSlot(double centreX, double centreZ, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (double radius = LARDER_RADIUS - 1.0; radius <= LARDER_RADIUS + LARDER_SHELL_THICKNESS;
                radius += 0.25) {
            int x = (int) Math.round(centreX + radius * cos);
            int z = (int) Math.round(centreZ + radius * sin);
            if (isInsideWallFace(centreX, centreZ, x, z)) {
                return new int[] {x, z};
            }
            // Sidestep once. On a diagonal bearing the ray leaves the hollow through a
            // corner, and the corner block's own neighbours are all shell -- the block that
            // faces the room is beside it rather than on the ray. Measured, that was the
            // last 25 of 599 slots still landing behind a block of fabric.
            for (int[] face : COMB_FACES) {
                if (isInsideWallFace(centreX, centreZ, x + face[0], z + face[1])) {
                    return new int[] {x + face[0], z + face[1]};
                }
            }
        }
        // No block on this ray is both shell and room-facing, which the lattice makes very
        // hard to arrange; fall back to the nominal radius, which is still inside the shell.
        return new int[] {(int) Math.round(centreX + LARDER_COMB_RADIUS * cos),
            (int) Math.round(centreZ + LARDER_COMB_RADIUS * sin)};
    }

    /**
     * Whether a block is shell with a face onto the room -- the inside surface of the wall,
     * which is the only place a comb is worth putting.
     */
    private static boolean isInsideWallFace(double centreX, double centreZ, int x, int z) {
        double distance = Math.hypot(x - centreX, z - centreZ);
        if (distance <= LARDER_RADIUS || distance > LARDER_RADIUS + LARDER_SHELL_THICKNESS) {
            return false;
        }
        for (int[] face : COMB_FACES) {
            if (Math.hypot(x + face[0] - centreX, z + face[1] - centreZ) <= LARDER_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static final int[][] COMB_FACES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Whether a chamber centred at (x, z) would sit inside a throne's exclusion ball -- i.e.
     * within {@link ColonyGeneratorTunables#THRONE_LARDER_CLEARANCE} of a throne centre.
     *
     * <p><b>Every throne in the 3x3 cell neighbourhood is checked, and the neighbourhood is
     * what makes that exact.</b> A throne centre lands within 76 blocks of its own colony
     * centre ({@link #throneForCell} derives that bound), so a throne inside the 62.5-block
     * clearance of this point belongs to a colony centre at most 138.5 blocks away -- and a
     * centre two cells out sits at least {@code 2*288 - 144 - 48} = 384 blocks from any point
     * in this cell. Nine candidates are all there are.
     *
     * <p><b>Round 3 is why this is nine rather than one.</b> Round 2 checked only the nearest
     * colony's throne and argued that was exact, on the grounds that two centres both within
     * 138.5 blocks of one point would be at most 277 apart against a minimum separation of
     * {@code COLONY_SPACING - COLONY_JITTER} = 224. The round-3 compaction took that minimum
     * to 192, and 192 &lt; 277 -- so a larder sitting between two colonies could clear its own
     * colony's throne and land inside a neighbour's. That is exactly the failure round 2 spent
     * a whole item eliminating (a cell-neighbourhood guard let 3 larders through on seed
     * 1234567 and 10 on seed 987654321), so the bound was restored rather than left to the
     * probe to catch. Cost is eight extra one-draw thrones on a tier-0 pick.
     *
     * <p>The 3x3 scan mirrors {@link #nearestColony}'s own derivation rather than calling it,
     * because what {@link #throneForCell} needs is the <em>cell</em> and that method returns
     * only the centre.
     */
    private boolean crowdsAThrone(double x, double z) {
        int cellX = Math.floorDiv(Mth.floor(x), COLONY_SPACING);
        int cellZ = Math.floorDiv(Mth.floor(z), COLONY_SPACING);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Throne throne = throneForCell(cellX + dx, cellZ + dz);
                double gapX = x - throne.centreX();
                double gapZ = z - throne.centreZ();
                if (gapX * gapX + gapZ * gapZ < THRONE_LARDER_CLEARANCE * THRONE_LARDER_CLEARANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Every larder chamber that can possibly reach into the 16x16 area rooted at
     * ({@code blockMinX}, {@code blockMinZ}). Same 3x3 justification as
     * {@link #gardensNear}: LARDER_SPACING matches NURSERY_SPACING/GARDEN_SPACING exactly
     * and LARDER_MAX_REACH is close to their own values, so the margin argument carries
     * over unchanged.
     */
    public Larder[] lardersNear(int blockMinX, int blockMinZ) {
        int cellX = Math.floorDiv(blockMinX, LARDER_SPACING);
        int cellZ = Math.floorDiv(blockMinZ, LARDER_SPACING);
        Larder[] out = new Larder[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out[i++] = larderForCell(cellX + dx, cellZ + dz);
            }
        }
        return out;
    }

    /** As {@link #nurseriesForColumn}: within reach, and not gated out by the colony field. */
    public Larder[] lardersForColumn(Larder[] candidates, int x, int z) {
        int count = 0;
        Larder[] scratch = new Larder[candidates.length];
        for (Larder larder : candidates) {
            if (!larder.inColony()) {
                continue;
            }
            double dx = x - larder.centreX();
            double dz = z - larder.centreZ();
            if (dx * dx + dz * dz <= LARDER_MAX_REACH * LARDER_MAX_REACH) {
                scratch[count++] = larder;
            }
        }
        if (count == 0) {
            return NO_LARDERS;
        }
        Larder[] out = new Larder[count];
        System.arraycopy(scratch, 0, out, 0, count);
        return out;
    }

    // ------------------------------------------------------------------
    // Nearest-chamber queries -- the seam the dev tooling reads
    // (/formicary dev locate|tp|state). Deliberately here rather than duplicated in the
    // command class: the cell arithmetic is the generator's, and a second copy of it would
    // be a copy that can silently disagree with the terrain it claims to describe.
    // ------------------------------------------------------------------

    /**
     * Cell rings searched by {@link #nearestThrone}.
     *
     * <p>Two, not the one that {@link #thronesNear} uses, because this is a different
     * question. That one asks "whose carve can touch this chunk", which the 3x3 ring
     * provably answers. "Whose centre is closest to this point" has to look further: a
     * throne centre lands anywhere within 124 blocks of its 288-block cell centre, so the
     * nearest of the 3x3 can be up to {@code 144*sqrt(2) + 124} = 328 blocks away while a
     * throne two cells out can sit as close as {@code 2*288 - 144 - 124} = 308 -- i.e. closer
     * than the 3x3's own worst case, so the second ring is not optional. A third would be
     * pointless: three cells out is at least {@code 3*288 - 144 - 124} = 596.
     */
    private static final int NEAREST_QUERY_CELL_RADIUS = 2;

    /**
     * Cell rings searched by {@link #nearestNursery} / {@link #nearestGarden} /
     * {@link #nearestLarder}, whose 96-block cells now have to be filtered for colony
     * eligibility before the answer means anything.
     *
     * <p>Six, and the number is derived rather than generous. From an arbitrary point the
     * nearest colony centre is at most {@code 144*sqrt(2) + 48} = 252 blocks away, and a
     * chamber inside that colony is eligible out to at most
     * {@link ColonyGeneratorTunables#COLONY_OUTER_RADIUS} = 100 blocks from its centre
     * (whatever {@link ColonyGeneratorTunables#CHAMBER_ELIGIBILITY_MIN_F} is set to -- the
     * field is exactly zero beyond the outer radius), so an eligible chamber always exists
     * within {@code 252 + 100} = 352 blocks. Six rings of 96 reach 576, which covers it with
     * margin both compactions have only widened. 169 cells of closed-form arithmetic is
     * nothing for a command that runs on demand.
     */
    private static final int NEAREST_ELIGIBLE_CELL_RADIUS = 6;

    /** The throne chamber whose centre is horizontally nearest (x, z). */
    public Throne nearestThrone(int x, int z) {
        int cellX = Math.floorDiv(x, COLONY_SPACING);
        int cellZ = Math.floorDiv(z, COLONY_SPACING);
        Throne best = throneForCell(cellX, cellZ);
        double bestDistanceSq = distanceSq(x, z, best.centreX(), best.centreZ());
        for (int dx = -NEAREST_QUERY_CELL_RADIUS; dx <= NEAREST_QUERY_CELL_RADIUS; dx++) {
            for (int dz = -NEAREST_QUERY_CELL_RADIUS; dz <= NEAREST_QUERY_CELL_RADIUS; dz++) {
                Throne candidate = throneForCell(cellX + dx, cellZ + dz);
                double distanceSq = distanceSq(x, z, candidate.centreX(), candidate.centreZ());
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * The nursery chamber whose centre is horizontally nearest (x, z) and which actually
     * generates -- i.e. one that passed the colony-eligibility gate.
     *
     * <p>Filtering matters here in a way it does not for the throne: since Ep2 the majority
     * of 96-block cells produce no room at all, so an unfiltered "nearest" would name a set
     * of coordinates that is solid soil. The fallback to the unfiltered nearest exists only
     * so the dev command reports something rather than nothing if the derivation above ever
     * stops holding; {@link NoiseProbe}'s colony section is what would catch that.
     */
    public Nursery nearestNursery(int x, int z) {
        int cellX = Math.floorDiv(x, NURSERY_SPACING);
        int cellZ = Math.floorDiv(z, NURSERY_SPACING);
        Nursery best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int dx = -NEAREST_ELIGIBLE_CELL_RADIUS; dx <= NEAREST_ELIGIBLE_CELL_RADIUS; dx++) {
            for (int dz = -NEAREST_ELIGIBLE_CELL_RADIUS; dz <= NEAREST_ELIGIBLE_CELL_RADIUS; dz++) {
                Nursery candidate = nurseryForCell(cellX + dx, cellZ + dz);
                if (!candidate.inColony()) {
                    continue;
                }
                double distanceSq = distanceSq(x, z, candidate.centreX(), candidate.centreZ());
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    best = candidate;
                }
            }
        }
        return best != null ? best : nurseryForCell(cellX, cellZ);
    }

    /** The garden chamber whose centre is horizontally nearest (x, z), gated as above. */
    public Garden nearestGarden(int x, int z) {
        int cellX = Math.floorDiv(x, GARDEN_SPACING);
        int cellZ = Math.floorDiv(z, GARDEN_SPACING);
        Garden best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int dx = -NEAREST_ELIGIBLE_CELL_RADIUS; dx <= NEAREST_ELIGIBLE_CELL_RADIUS; dx++) {
            for (int dz = -NEAREST_ELIGIBLE_CELL_RADIUS; dz <= NEAREST_ELIGIBLE_CELL_RADIUS; dz++) {
                Garden candidate = gardenForCell(cellX + dx, cellZ + dz);
                if (!candidate.inColony()) {
                    continue;
                }
                double distanceSq = distanceSq(x, z, candidate.centreX(), candidate.centreZ());
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    best = candidate;
                }
            }
        }
        return best != null ? best : gardenForCell(cellX, cellZ);
    }

    /** The larder chamber whose centre is horizontally nearest (x, z), gated as above. */
    public Larder nearestLarder(int x, int z) {
        int cellX = Math.floorDiv(x, LARDER_SPACING);
        int cellZ = Math.floorDiv(z, LARDER_SPACING);
        Larder best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int dx = -NEAREST_ELIGIBLE_CELL_RADIUS; dx <= NEAREST_ELIGIBLE_CELL_RADIUS; dx++) {
            for (int dz = -NEAREST_ELIGIBLE_CELL_RADIUS; dz <= NEAREST_ELIGIBLE_CELL_RADIUS; dz++) {
                Larder candidate = larderForCell(cellX + dx, cellZ + dz);
                if (!candidate.inColony()) {
                    continue;
                }
                double distanceSq = distanceSq(x, z, candidate.centreX(), candidate.centreZ());
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    best = candidate;
                }
            }
        }
        return best != null ? best : larderForCell(cellX, cellZ);
    }

    private static double distanceSq(int x, int z, double centreX, double centreZ) {
        double dx = x - centreX;
        double dz = z - centreZ;
        return dx * dx + dz * dz;
    }

    /**
     * What a nursery chamber says about (x, y, z): the hollow interior, the approach
     * corridor and its walkway, the shell, or nothing.
     *
     * <p>The corridor's own floor is forced solid, because a nursery hangs 24 blocks out in
     * the Nurseries tier -- the airiest band in the dimension (24% air) -- so leaving the
     * floor to the noise would eventually produce a doorway opening onto a drop.
     *
     * <p><b>This used to be an asymmetry with {@link #throneState}, and Ep2 ended it.</b>
     * The throne's corridor floor was left unforced on the strength of one measurement: the
     * Royal Depths were 17% air (DECISIONS.md), and the probe had always shown the throne's
     * 34-block approach landing on ground. The colony field (spec section 1) invalidates
     * that argument rather than refining it -- every throne now sits inside a colony core,
     * which is precisely where the field pushes carve density <em>up</em> from the measured
     * average, and the air fraction the exemption rested on is no longer the air fraction
     * anywhere a throne exists. Forcing both floors is now the rule with no exception to
     * remember.
     *
     * <p>Chambers whose {@link Nursery#inColony()} is false are skipped outright: outside a
     * colony the room does not generate, so it says nothing about any position.
     */
    public int nurseryState(Nursery[] nurseries, int x, int y, int z) {
        for (Nursery nursery : nurseries) {
            if (!nursery.inColony()) {
                continue;
            }
            double dx = x - nursery.centreX();
            double dz = z - nursery.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            int height = y - nursery.floorY();

            if (height >= 0 && height <= NURSERY_CORRIDOR_HEIGHT && isInNurseryCorridor(nursery, x, z)) {
                return height == 0 ? NURSERY_SOLID : NURSERY_AIR;
            }
            if (isInsideNursery(distance, height, 0.0)) {
                return NURSERY_AIR;
            }
            if (isInsideNursery(distance, height, NURSERY_SHELL_THICKNESS)) {
                return NURSERY_SOLID;
            }
        }
        return NURSERY_NONE;
    }

    /** The room's shape, exactly as {@link #isInsideThrone} but with the nursery's numbers. */
    private static boolean isInsideNursery(double distance, double height, double grow) {
        double radius = NURSERY_RADIUS + grow;
        double dome = NURSERY_DOME_HEIGHT + grow;
        if (height < 1.0 - grow || height > NURSERY_WALL_HEIGHT + dome) {
            return false;
        }
        if (height <= NURSERY_WALL_HEIGHT) {
            return distance <= radius;
        }
        double t = (height - NURSERY_WALL_HEIGHT) / dome;
        return distance <= radius * Math.sqrt(Math.max(0.0, 1.0 - t * t));
    }

    /** Whether (x, z) is inside the corridor's footprint, measured along the axis ray. */
    private static boolean isInNurseryCorridor(Nursery nursery, int x, int z) {
        double px = x - nursery.axisX();
        double pz = z - nursery.axisZ();
        double along = px * nursery.dirX() + pz * nursery.dirZ();
        if (along < NURSERY_CORRIDOR_START || along > NURSERY_CORRIDOR_END) {
            return false;
        }
        return Math.abs(px * nursery.dirZ() - pz * nursery.dirX()) <= NURSERY_CORRIDOR_HALF_WIDTH;
    }

    /** Whether (x, y, z) is inside a nursery chamber's hollow (used to pick decoration). */
    public boolean isInNurseryRoom(Nursery[] nurseries, int x, int y, int z) {
        for (Nursery nursery : nurseries) {
            if (!nursery.inColony()) {
                continue;
            }
            double dx = x - nursery.centreX();
            double dz = z - nursery.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double height = y - nursery.floorY();
            if (isInsideNursery(distance, height, NURSERY_SHELL_THICKNESS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a garden chamber says about (x, y, z): the hollow interior, the approach
     * corridor and its walkway, the shell, or nothing. Same precedence as
     * {@link #nurseryState}: the corridor's own floor is forced solid -- a 24-block
     * approach through the Fungal Gardens tier (the second-airiest band after the
     * Nurseries) cannot be trusted to leave a floor under it any more than the nursery's
     * own approach could. Ineligible chambers are skipped for the same reason as there.
     */
    public int gardenState(Garden[] gardens, int x, int y, int z) {
        for (Garden garden : gardens) {
            if (!garden.inColony()) {
                continue;
            }
            double dx = x - garden.centreX();
            double dz = z - garden.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            int height = y - garden.floorY();

            if (height >= 0 && height <= GARDEN_CORRIDOR_HEIGHT && isInGardenCorridor(garden, x, z)) {
                return height == 0 ? GARDEN_SOLID : GARDEN_AIR;
            }
            if (isInsideGarden(distance, height, 0.0)) {
                return GARDEN_AIR;
            }
            if (isInsideGarden(distance, height, GARDEN_SHELL_THICKNESS)) {
                return GARDEN_SOLID;
            }
        }
        return GARDEN_NONE;
    }

    /** The room's shape, exactly as {@link #isInsideNursery} but with the garden's numbers. */
    private static boolean isInsideGarden(double distance, double height, double grow) {
        double radius = GARDEN_RADIUS + grow;
        double dome = GARDEN_DOME_HEIGHT + grow;
        if (height < 1.0 - grow || height > GARDEN_WALL_HEIGHT + dome) {
            return false;
        }
        if (height <= GARDEN_WALL_HEIGHT) {
            return distance <= radius;
        }
        double t = (height - GARDEN_WALL_HEIGHT) / dome;
        return distance <= radius * Math.sqrt(Math.max(0.0, 1.0 - t * t));
    }

    /** Whether (x, z) is inside the corridor's footprint, measured along the axis ray. */
    private static boolean isInGardenCorridor(Garden garden, int x, int z) {
        double px = x - garden.axisX();
        double pz = z - garden.axisZ();
        double along = px * garden.dirX() + pz * garden.dirZ();
        if (along < GARDEN_CORRIDOR_START || along > GARDEN_CORRIDOR_END) {
            return false;
        }
        return Math.abs(px * garden.dirZ() - pz * garden.dirX()) <= GARDEN_CORRIDOR_HALF_WIDTH;
    }

    /** Whether (x, y, z) is inside a garden chamber's hollow (used to pick decoration). */
    public boolean isInGardenRoom(Garden[] gardens, int x, int y, int z) {
        for (Garden garden : gardens) {
            if (!garden.inColony()) {
                continue;
            }
            double dx = x - garden.centreX();
            double dz = z - garden.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double height = y - garden.floorY();
            if (isInsideGarden(distance, height, GARDEN_SHELL_THICKNESS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a larder chamber says about (x, y, z): the hollow interior, the approach
     * corridor and its walkway, the shell, or nothing. Same precedence as
     * {@link #gardenState}/{@link #nurseryState}: the corridor's own floor is forced solid,
     * and an ineligible chamber says nothing.
     */
    public int larderState(Larder[] larders, int x, int y, int z) {
        for (Larder larder : larders) {
            if (!larder.inColony()) {
                continue;
            }
            double dx = x - larder.centreX();
            double dz = z - larder.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            int height = y - larder.floorY();

            if (height >= 0 && height <= LARDER_CORRIDOR_HEIGHT && isInLarderCorridor(larder, x, z)) {
                return height == 0 ? LARDER_SOLID : LARDER_AIR;
            }
            if (isInsideLarder(distance, height, 0.0)) {
                return LARDER_AIR;
            }
            if (isInsideLarder(distance, height, LARDER_SHELL_THICKNESS)) {
                return LARDER_SOLID;
            }
        }
        return LARDER_NONE;
    }

    /** The room's shape, exactly as {@link #isInsideGarden} but with the larder's numbers. */
    private static boolean isInsideLarder(double distance, double height, double grow) {
        double radius = LARDER_RADIUS + grow;
        double dome = LARDER_DOME_HEIGHT + grow;
        if (height < 1.0 - grow || height > LARDER_WALL_HEIGHT + dome) {
            return false;
        }
        if (height <= LARDER_WALL_HEIGHT) {
            return distance <= radius;
        }
        double t = (height - LARDER_WALL_HEIGHT) / dome;
        return distance <= radius * Math.sqrt(Math.max(0.0, 1.0 - t * t));
    }

    /**
     * Whether (x, z) is inside the corridor's footprint, measured along the axis ray.
     *
     * <p>Public so {@link NoiseProbe} can ask the exact same question of a provision-comb
     * slot that {@link #larderState} asks of a wall block -- "is this in the doorway" needs
     * one definition, not a second geometric re-derivation that could silently disagree.
     */
    public static boolean isInLarderCorridor(Larder larder, int x, int z) {
        double px = x - larder.axisX();
        double pz = z - larder.axisZ();
        double along = px * larder.dirX() + pz * larder.dirZ();
        if (along < LARDER_CORRIDOR_START || along > LARDER_CORRIDOR_END) {
            return false;
        }
        return Math.abs(px * larder.dirZ() - pz * larder.dirX()) <= LARDER_CORRIDOR_HALF_WIDTH;
    }

    /** Whether (x, y, z) is inside a larder chamber's hollow (used to pick decoration). */
    public boolean isInLarderRoom(Larder[] larders, int x, int y, int z) {
        for (Larder larder : larders) {
            if (!larder.inColony()) {
                continue;
            }
            double dx = x - larder.centreX();
            double dz = z - larder.centreZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double height = y - larder.floorY();
            if (isInsideLarder(distance, height, LARDER_SHELL_THICKNESS)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Noise carve
    // ------------------------------------------------------------------

    /** True where the worm tunnels carve. */
    public boolean isTunnelCarved(int x, int y, int z) {
        double halfWidth = TUNNEL_HALF_WIDTH_BY_TIER[tierIndex(y)];
        double a = this.tunnelA.getValue(x * TUNNEL_XZ_SCALE, y * TUNNEL_Y_SCALE, z * TUNNEL_XZ_SCALE);
        if (Math.abs(a) >= halfWidth) {
            return false;
        }
        double b = this.tunnelB.getValue(x * TUNNEL_XZ_SCALE, y * TUNNEL_Y_SCALE, z * TUNNEL_XZ_SCALE);
        return Math.abs(b) < halfWidth;
    }

    /**
     * True where a blob chamber carves, at colony density {@code field}.
     *
     * <p>The one place the colony field changes the <em>shape</em> of the world rather than
     * its dressing. Both thresholds are lerped from
     * {@link ColonyGeneratorTunables#NEVER_THRESHOLD} at {@code field = 0} to their tuned
     * per-tier value at {@code field = 1}, so a core carves exactly what it always did, the
     * ring thins out continuously, and the wilds are worm tunnels and ramps only. Raising a
     * threshold rather than post-multiplying an "is carved" chance is what keeps the result
     * a contiguous blob field instead of a spray of holes: the blobs shrink toward their own
     * peaks as f drops.
     */
    public boolean isChamberCarved(double field, int x, int y, int z) {
        // At f = 0 both thresholds are NEVER_THRESHOLD, which no Perlin value reaches, so
        // this is not just an optimisation -- it is the same answer, two lookups cheaper.
        if (field <= 0.0) {
            return false;
        }
        int tier = tierIndex(y);
        double small = this.chamberSmall.getValue(
                x * CHAMBER_SMALL_XZ_SCALE, y * CHAMBER_SMALL_Y_SCALE, z * CHAMBER_SMALL_XZ_SCALE);
        if (small > Mth.lerp(field, NEVER_THRESHOLD, CHAMBER_SMALL_THRESHOLD_BY_TIER[tier])) {
            return true;
        }
        double large = this.chamberLarge.getValue(
                x * CHAMBER_LARGE_XZ_SCALE, y * CHAMBER_LARGE_Y_SCALE, z * CHAMBER_LARGE_XZ_SCALE);
        return large > Mth.lerp(field, NEVER_THRESHOLD, CHAMBER_LARGE_THRESHOLD_BY_TIER[tier]);
    }

    /**
     * The one authority on "is this block air". Everything above and below the caps is
     * solid unconditionally, which is what keeps the dimension free of exposed void at
     * {@code y = MIN_Y} and {@code y = MIN_Y + HEIGHT - 1}.
     *
     * <p>The connectivity spine outranks the noise in <b>both</b> directions -- it carves
     * through solid fabric, and it also refuses to be carved away, keeping its floor even
     * where a cathedral chamber would have hollowed it out. Without that the ramp loses its
     * walkway wherever a big room crosses it and the descent becomes a one-way drop, which
     * is a soft-lock: mining the fabric is gated behind a full set of Chitin Armor.
     *
     * <p>{@code field} is the colony density at this column (a pure function of X/Z, so one
     * value serves the whole column). It is threaded in rather than looked up here because
     * resolving it costs nine seeded draws and cannot change with Y -- see
     * {@link #colonyField(Colony[], double, double)}.
     *
     * <p>The throne chamber (M7), the nursery chambers, and (Ep2) the garden and larder
     * chambers sit <em>below</em> the spine in that order, deliberately: they may carve
     * through the noise and force their own shells solid, but never override a ramp floor
     * or a ramp walkway. That keeps M4a's walkability guarantee exactly as it was -- the
     * rooms hang off the spine, they do not cut into it.
     *
     * <p>Their order <em>relative to each other</em> used to be arbitrary, because the four
     * kinds' floors lived in four disjoint Y bands and so could not interact at all. Play-test
     * round 2 ended that: a larder now picks its tier per cell and can share a band with any
     * of the other three. What replaced the bands is geometric, not an ordering rule -- the
     * three 96-grid kinds take slots 120 degrees apart around their shared ramp (at least
     * {@link ColonyGeneratorTunables#CHAMBER_SLOT_MIN_SEPARATION} = 30.85 blocks between any
     * two centres), and a tier-0 larder is bumped off the throne's own ramp. See
     * {@link #larderForCell}. The order below is therefore still arbitrary among the four, and
     * {@link NoiseProbe} asserts the separation rather than leaving it to be reasoned about.
     */
    public boolean isAir(double field, Shaft[] columnShafts, Throne[] columnThrones, Nursery[] columnNurseries,
            Garden[] columnGardens, Larder[] columnLarders, int x, int y, int z) {
        if (y < FLOOR_TOP || y >= CEILING_BOTTOM) {
            return false;
        }
        if (columnShafts.length > 0) {
            int state = shaftState(columnShafts, x, y, z);
            if (state == SHAFT_AIR) {
                return true;
            }
            if (state == SHAFT_SOLID) {
                return false;
            }
        }
        if (columnThrones.length > 0) {
            int state = throneState(columnThrones, x, y, z);
            if (state == THRONE_AIR) {
                return true;
            }
            if (state != THRONE_NONE) {
                return false;
            }
        }
        if (columnNurseries.length > 0) {
            int state = nurseryState(columnNurseries, x, y, z);
            if (state == NURSERY_AIR) {
                return true;
            }
            if (state != NURSERY_NONE) {
                return false;
            }
        }
        if (columnGardens.length > 0) {
            int state = gardenState(columnGardens, x, y, z);
            if (state == GARDEN_AIR) {
                return true;
            }
            if (state != GARDEN_NONE) {
                return false;
            }
        }
        if (columnLarders.length > 0) {
            int state = larderState(columnLarders, x, y, z);
            if (state == LARDER_AIR) {
                return true;
            }
            if (state != LARDER_NONE) {
                return false;
            }
        }
        return isTunnelCarved(x, y, z) || isChamberCarved(field, x, y, z);
    }

    // ------------------------------------------------------------------
    // Daylight Membrane -- the M5 exit, embedded in the ceiling cap
    // ------------------------------------------------------------------

    /**
     * Whether the ceiling block at (x, y, z) is a Daylight Membrane rather than plain cap.
     *
     * <p>Two conditions, both necessary and (since Ep2) jointly sufficient:
     * <ul>
     *   <li>{@code y} is in the bottom {@link ColonyGeneratorTunables#MEMBRANE_THICKNESS}
     *       layers of the cap, so the patch is flush with the ceiling's underside.</li>
     *   <li>The block directly below the cap is air, so the patch is actually <i>visible</i>
     *       from inside the top tier. Only 10.4-13.1% of ceiling columns qualify
     *       (measured by {@code NoiseProbe -PprobeWhat=membrane} over 192x192 columns on
     *       seeds 1234567 / 42 / 987654321); a patch anywhere else is a decoration nobody
     *       ever sees, and would make any reachability claim a lie.</li>
     * </ul>
     *
     * <p><b>Ep2 dropped the third condition</b> -- a threshold on a 2D patch field, which
     * kept only its peaks. The rule it leaves behind is the one a player can actually learn:
     * <em>every bit of roof you can see is a way out</em>. Under the threshold the answer to
     * "can I leave from here" was a noise lookup with no tell in the world, and play-test
     * round 1's report was that the exits simply could not be found; measured reachability
     * (median 21-24 blocks to the nearest patch) had been fine, which is exactly why the
     * measurement was the wrong target. Visible ceiling is scarce enough on its own -- it is
     * the mask, not the field, that was ever doing the rationing.
     *
     * <p><b>The colony field does not modulate this rule</b> (spec section 1 lists the
     * membrane among the unmodulated globals). It reaches the answer only through
     * {@link #isAir}: a sparse-zone ceiling has less air under it, so it carries fewer
     * exits, but "visible roof = way out" holds identically at every value of {@code field}.
     * An exit rule that thinned out with density would be one that can strand a player in
     * exactly the part of the world they are least equipped for.
     *
     * <p>The visibility test reads {@link #isAir} at a fixed Y rather than the chunk, so it
     * stays a pure function of world position -- the same property that lets
     * {@code ColonyChunkGenerator#getBaseColumn} and the chunk fill agree without either
     * looking at the other's output.
     */
    public boolean isDaylightMembrane(double field, Shaft[] columnShafts, Throne[] columnThrones,
            Nursery[] columnNurseries, Garden[] columnGardens, Larder[] columnLarders, int x, int y, int z) {
        if (y < CEILING_BOTTOM || y >= CEILING_BOTTOM + MEMBRANE_THICKNESS) {
            return false;
        }
        return isAir(field, columnShafts, columnThrones, columnNurseries, columnGardens, columnLarders,
                x, CEILING_BOTTOM - 1, z);
    }

    // ------------------------------------------------------------------
    // Comb patches (play-test round 1)
    // ------------------------------------------------------------------

    /**
     * Whether comb is allowed to grow at (x, y, z) at all.
     *
     * <p>The gate that turns comb from speckle into patches. It is a plain threshold on a
     * low-frequency field, so a patch is a contiguous blob of world positions -- the
     * per-block chance in {@link ColonyGeneratorTunables#BROOD_COMB_CHANCE_BY_TIER} then
     * only decides how solid that blob is and how ragged its edge. Being a pure function of
     * position (rather than, say, a flood fill seeded at a lucky roll) is what keeps a patch
     * whole across a chunk boundary.
     *
     * <p>The threshold is modulated by the colony field the same way the blob chambers' are
     * -- lerped from {@link ColonyGeneratorTunables#NEVER_THRESHOLD} at {@code field = 0} --
     * so comb is a thing colonies grow, and the wilds have none of it. Raising the threshold
     * (rather than scaling the per-block chance) is again what keeps the surviving patches
     * whole: at half density you get the same patches, smaller, not the same patches full of
     * holes.
     */
    public boolean isCombPatch(double field, int x, int y, int z) {
        if (field <= 0.0) {
            return false;
        }
        return probeCombPatch(x, y, z)
                > Mth.lerp(field, NEVER_THRESHOLD, COMB_PATCH_THRESHOLD_BY_TIER[tierIndex(y)]);
    }

    // ------------------------------------------------------------------
    // Raw field readouts -- only for NoiseProbe and addDebugScreenInfo. Thresholds in
    // ColonyGeneratorTunables are in these units, so being able to print the real span of
    // each field is what keeps them tuned from measurement rather than from guesswork.
    // ------------------------------------------------------------------

    public double probeTunnelA(int x, int y, int z) {
        return this.tunnelA.getValue(x * TUNNEL_XZ_SCALE, y * TUNNEL_Y_SCALE, z * TUNNEL_XZ_SCALE);
    }

    public double probeTunnelB(int x, int y, int z) {
        return this.tunnelB.getValue(x * TUNNEL_XZ_SCALE, y * TUNNEL_Y_SCALE, z * TUNNEL_XZ_SCALE);
    }

    public double probeChamberSmall(int x, int y, int z) {
        return this.chamberSmall.getValue(
                x * CHAMBER_SMALL_XZ_SCALE, y * CHAMBER_SMALL_Y_SCALE, z * CHAMBER_SMALL_XZ_SCALE);
    }

    public double probeChamberLarge(int x, int y, int z) {
        return this.chamberLarge.getValue(
                x * CHAMBER_LARGE_XZ_SCALE, y * CHAMBER_LARGE_Y_SCALE, z * CHAMBER_LARGE_XZ_SCALE);
    }

    public double probeAccent(int x, int y, int z) {
        return this.accent.getValue(x * ACCENT_XZ_SCALE, y * ACCENT_Y_SCALE, z * ACCENT_XZ_SCALE);
    }

    /** The Daylight Membrane patch field, before the threshold and the visibility mask. */
    public double probeMembrane(int x, int z) {
        return this.membrane.getValue(x * MEMBRANE_XZ_SCALE, 0.0, z * MEMBRANE_XZ_SCALE);
    }

    /** The comb-patch field, before the per-tier threshold. */
    public double probeCombPatch(int x, int y, int z) {
        return this.combPatch.getValue(x * COMB_PATCH_XZ_SCALE, y * COMB_PATCH_Y_SCALE, z * COMB_PATCH_XZ_SCALE);
    }

    // ------------------------------------------------------------------
    // Solid palette
    // ------------------------------------------------------------------

    /** Which fabric block fills a solid position. See the {@code FABRIC_*} constants. */
    public int fabricKind(int x, int y, int z) {
        if (y < FLOOR_TOP) {
            return FABRIC_HARDENED_SOIL;
        }
        if (y >= CEILING_BOTTOM) {
            return FABRIC_PACKED_SOIL;
        }
        int tier = tierIndex(y);
        double a = this.accent.getValue(x * ACCENT_XZ_SCALE, y * ACCENT_Y_SCALE, z * ACCENT_XZ_SCALE);
        if (a > HARDENED_ACCENT_THRESHOLD_BY_TIER[tier]) {
            return FABRIC_HARDENED_SOIL;
        }
        return switch (tier) {
            // Tier 2 is the top band since round 2 retired the Upper Galleries (whose own
            // Packed Soil survives as the ceiling cap, handled above).
            case 2 -> FABRIC_AMBER_EARTH;
            case 1 -> FABRIC_DEEP_LOAM;
            // Royal Depths: Deep Loam shot through with Hardened Soil (above) and resin.
            default -> a < ROYAL_RESIN_THRESHOLD ? FABRIC_RESIN_BLOCK : FABRIC_DEEP_LOAM;
        };
    }

    // ------------------------------------------------------------------
    // Arrival connector (play-test round 3, item 1) -- the no-softlock guarantee, carved
    //
    // A pearl thrown at an anthill lands the player at that anthill's XZ, which is
    // uncorrelated with anything down here, and AnthillPortal then hollows a 5x5 pocket out
    // of whatever fabric is there. Measured before the fix (NoiseProbe#arrivals, 256
    // arbitrary arrival columns per seed): only 24.6% / 28.9% / 35.9% of them could walk
    // out of that pocket to a worm tunnel or a ramp on seeds 1234567 / 42 / 987654321.
    // Three quarters of all arrivals were sealed in, which is exactly the report round 3
    // opened with.
    //
    // The route is computed HERE rather than in AnthillPortal for the reason every other
    // shape in this class lives here: the probe has to be able to measure it without a
    // level (see the "Not bootstrapped" rule in docs/gotchas/worldgen.md), and a second
    // copy of the arithmetic living next to the block writes is a copy that can silently
    // disagree with the terrain it is cutting through. AnthillPortal owns only the writes.
    // ------------------------------------------------------------------

    /**
     * The passage from an arrival pocket to the traversable network.
     *
     * @param air     blocks to clear, in no particular order
     * @param floor   blocks to make solid -- the corridor's own walkway, and disjoint from
     *                {@code air} by construction (they come out of one map)
     * @param route   the standing positions the walk passes through, pocket exclusive
     * @param targetX the network block the route lands on
     * @param length  how many steps that walk is
     * @param joined  whether a network block was reachable at all
     */
    public record ArrivalConnector(PocketBlock[] air, PocketBlock[] floor, int[][] route,
            int targetX, int targetY, int targetZ, int length, boolean joined) {
    }

    /**
     * Routes a walkable passage from the arrival pocket at ({@code pocketX},
     * {@code pocketFloorY}, {@code pocketZ}) to the nearest block of the traversable
     * network, and returns the blocks that carving it costs.
     *
     * <p><b>What counts as the network</b> is the pair the design notes have always called
     * the no-softlock guarantee: worm-tunnel air, which is ungated and therefore exists in
     * the wilds as much as in a core, and ramp/landing air, which since round 2 exists only
     * inside a colony. Chamber air deliberately does not count on its own -- a blob chamber
     * can be a sealed bubble, and joining one would prove nothing.
     *
     * <p><b>It is a search over walkable space, not a straight line.</b> Three straight-line
     * routers were written and measured first, and each one left a residue of arrivals whose
     * own corridor they could not follow: 81 of 256 sampled columns when a fixed precedence
     * resolved the clash between one step's walkway and the next step's air, 64 when the
     * first step dug the floor out from under the player, and a stubborn 1 or 2 per seed
     * where the corridor met the connectivity ramp's helicoid side-on or ran out of headroom
     * against the ceiling cap. All three are the same shape of mistake -- proposing a route
     * and then hoping the terrain along it cooperates. What is searched here is the graph the
     * acceptance test actually walks: a node is <em>somewhere to stand</em>, an edge is a
     * one-block step to an orthogonal neighbour, and the corridor is whatever the cheapest
     * path through that graph turns out to be.
     *
     * <p><b>Cost is blocks changed</b>, which is what makes the passage look deliberate: a
     * node whose feet, head and floor are already what they need to be is free, so the route
     * runs down an existing tunnel wherever one is going the right way and only bores where
     * it has to. It is Dijkstra rather than A* because the goal is not one point -- it is
     * whichever network block is cheapest to reach.
     *
     * <p><b>Two blocks are never touched.</b> The connectivity spine's forced-solid floor is
     * never removed and its walkway air is never filled, so no arrival can put a hole in the
     * ramp a colony descends -- one rule stricter than the pocket carve next door. Nodes that
     * would need either are simply not in the graph.
     */
    public ArrivalConnector arrivalConnector(int pocketX, int pocketFloorY, int pocketZ) {
        ArrivalRegion region = new ArrivalRegion(pocketX, pocketFloorY, pocketZ);
        int start = region.node(pocketX, pocketFloorY, pocketZ);
        if (start < 0) {
            return new ArrivalConnector(NO_POCKET_BLOCKS, NO_POCKET_BLOCKS, NO_ROUTE,
                    pocketX, pocketFloorY, pocketZ, 0, false);
        }

        int[] dist = new int[region.nodes()];
        int[] from = new int[region.nodes()];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(from, -1);
        dist[start] = 0;
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        queue.add(new long[] {0L, start});
        int goal = -1;

        // At most one height per column: a corridor cannot cross over itself. Two nodes in
        // one column three blocks apart want contradictory things of the block between them
        // -- one wants it solid to stand on, the other wants it clear over its head -- and
        // whichever claim is written second silently wins. Measured, that was the last 7 to
        // 9 stranded columns per seed: a route perfect end to end except where it doubled
        // back under itself. Settling the column at the cheapest height that reaches it
        // removes the case from the graph rather than repairing it afterwards.
        boolean[] settled = new boolean[region.columns()];

        while (!queue.isEmpty()) {
            long[] head = queue.poll();
            int node = (int) head[1];
            if (head[0] > dist[node]) {
                continue;
            }
            if (settled[region.columnOf(node)]) {
                continue;
            }
            settled[region.columnOf(node)] = true;
            if (region.isGoal(node)) {
                goal = node;
                break;
            }
            for (int step = 0; step < ARRIVAL_STEPS.length; step++) {
                int next = region.step(node, ARRIVAL_STEPS[step][0], ARRIVAL_STEPS[step][1],
                        ARRIVAL_STEPS[step][2]);
                if (next < 0) {
                    continue;
                }
                int cost = dist[node] + region.carveCost(next);
                if (cost < dist[next]) {
                    dist[next] = cost;
                    from[next] = node;
                    queue.add(new long[] {cost, next});
                }
            }
        }

        if (goal < 0) {
            return new ArrivalConnector(NO_POCKET_BLOCKS, NO_POCKET_BLOCKS, NO_ROUTE,
                    pocketX, pocketFloorY, pocketZ, 0, false);
        }

        List<int[]> reversed = new ArrayList<>();
        for (int node = goal; node != start && node >= 0; node = from[node]) {
            reversed.add(region.position(node));
        }
        Collections.reverse(reversed);
        int[][] route = reversed.toArray(new int[0][]);

        // Feet, head and walkway for every step of the path first, so the widening pass
        // below can see -- and refuse to undo -- every block the walk depends on.
        Map<PocketBlock, Boolean> plan = new HashMap<>();
        // The arrival's own footing is claimed alongside the path's, even though the pocket
        // pass already dug it: the widening below reaches a block sideways into every
        // neighbouring column, and the first step of the path is a neighbour of the pocket.
        // Without the claim it cuts the floor out from under the player before they take a
        // step -- measured at 139 of 256 sampled columns, every one of them a route that was
        // otherwise walkable end to end.
        region.claimStanding(plan, pocketX, pocketFloorY, pocketZ);
        for (int[] point : route) {
            region.claimStanding(plan, point[0], point[1], point[2]);
        }
        for (int[] point : route) {
            region.widen(plan, point[0], point[1], point[2]);
        }

        List<PocketBlock> air = new ArrayList<>();
        List<PocketBlock> floor = new ArrayList<>();
        for (Map.Entry<PocketBlock, Boolean> entry : plan.entrySet()) {
            (entry.getValue() ? air : floor).add(entry.getKey());
        }
        int[] target = region.position(goal);
        return new ArrivalConnector(air.toArray(new PocketBlock[0]), floor.toArray(new PocketBlock[0]), route,
                target[0], target[1], target[2], route.length, true);
    }

    /** The twelve moves out of a standing position: four compass steps, up, level or down. */
    private static final int[][] ARRIVAL_STEPS = {
        {1, 0, -1}, {1, 0, 0}, {1, 0, 1}, {-1, 0, -1}, {-1, 0, 0}, {-1, 0, 1},
        {0, 1, -1}, {0, 1, 0}, {0, 1, 1}, {0, -1, -1}, {0, -1, 0}, {0, -1, 1},
    };

    /**
     * The block of world the arrival search reasons over, and the graph it searches.
     *
     * <p>Everything the search asks about a position -- is it air, does the spine claim it,
     * is it network -- is resolved once per column into three bit arrays covering the Y band,
     * because a Dijkstra revisits a column many times and {@link #isAir} is not cheap. The
     * arrays are filled lazily: a search that finds a tunnel four blocks away never touches
     * the other four thousand columns.
     */
    private final class ArrivalRegion {
        private final int pocketX;
        private final int pocketFloorY;
        private final int pocketZ;
        private final int span;
        private final int lowY;
        private final int bandHeight;
        private final boolean[] resolved;
        private final boolean[] air;
        private final boolean[] spineSolid;
        private final boolean[] spineAir;
        private final boolean[] network;
        /** Private to one search, so two of them on the worker pool cannot share arrays. */
        private final ColumnCache columns = new ColumnCache();

        ArrivalRegion(int pocketX, int pocketFloorY, int pocketZ) {
            this.pocketX = pocketX;
            this.pocketFloorY = pocketFloorY;
            this.pocketZ = pocketZ;
            this.span = ARRIVAL_CONNECTOR_MAX_REACH * 2 + 1;
            // One block of margin under the band and two over it: a node standing at the
            // lowest Y still asks about its own floor, and one at the highest still asks
            // about the block over its head.
            this.lowY = Math.max(FLOOR_TOP + 1, pocketFloorY - ARRIVAL_CONNECTOR_MAX_DESCENT);
            int highY = Math.min(CEILING_BOTTOM - 2, pocketFloorY + ARRIVAL_CONNECTOR_MAX_CLIMB);
            this.bandHeight = Math.max(1, highY - this.lowY + 1);
            this.resolved = new boolean[this.span * this.span];
            int cells = this.span * this.span * (this.bandHeight + 3);
            this.air = new boolean[cells];
            this.spineSolid = new boolean[cells];
            this.spineAir = new boolean[cells];
            this.network = new boolean[cells];
        }

        int nodes() {
            return this.span * this.span * this.bandHeight;
        }

        int columns() {
            return this.span * this.span;
        }

        int columnOf(int node) {
            return node / this.bandHeight;
        }

        /** The node index for a standing position, or -1 when it is outside or unusable. */
        int node(int x, int y, int z) {
            int ix = x - this.pocketX + ARRIVAL_CONNECTOR_MAX_REACH;
            int iz = z - this.pocketZ + ARRIVAL_CONNECTOR_MAX_REACH;
            int iy = y - this.lowY;
            if (ix < 0 || iz < 0 || ix >= this.span || iz >= this.span || iy < 0 || iy >= this.bandHeight) {
                return -1;
            }
            return (ix * this.span + iz) * this.bandHeight + iy;
        }

        int[] position(int node) {
            int iy = node % this.bandHeight;
            int iz = (node / this.bandHeight) % this.span;
            int ix = node / (this.bandHeight * this.span);
            return new int[] {ix - ARRIVAL_CONNECTOR_MAX_REACH + this.pocketX, iy + this.lowY,
                iz - ARRIVAL_CONNECTOR_MAX_REACH + this.pocketZ};
        }

        /**
         * The node reached by one step, or -1 when there is nothing to stand on there.
         *
         * <p>A position is standable-after-carving when neither the feet block nor the one
         * over the head is spine floor (this carve will not remove those), and the block
         * under the feet is not spine walkway (it will not fill those either).
         */
        int step(int node, int dx, int dz, int dy) {
            int[] here = position(node);
            int x = here[0] + dx;
            int y = here[1] + dy;
            int z = here[2] + dz;
            int next = node(x, y, z);
            if (next < 0 || this.spineSolid[cell(x, y, z)] || this.spineSolid[cell(x, y + 1, z)]
                    || this.spineAir[cell(x, y - 1, z)]) {
                return -1;
            }
            return next;
        }

        /** How many blocks standing at this node costs -- zero where the world already fits. */
        int carveCost(int node) {
            int[] here = position(node);
            int cost = this.air[cell(here[0], here[1], here[2])] ? 0 : 1;
            cost += this.air[cell(here[0], here[1] + 1, here[2])] ? 0 : 1;
            return cost + (this.air[cell(here[0], here[1] - 1, here[2])] ? 1 : 0);
        }

        /**
         * Whether a node is somewhere the player can be said to have left the pocket and
         * joined the network: its feet are in worm-tunnel or ramp air, outside the footprint
         * the pocket carve hollowed out. A tunnel block inside that footprint proves nothing,
         * since the carve is what put it there.
         */
        boolean isGoal(int node) {
            int[] here = position(node);
            return this.network[cell(here[0], here[1], here[2])]
                    && (Math.abs(here[0] - this.pocketX) > ENTRY_CARVE_RADIUS
                            || Math.abs(here[2] - this.pocketZ) > ENTRY_CARVE_RADIUS);
        }

        /**
         * Feet, head and walkway for one step of the path.
         *
         * <p>The walkway is claimed <b>even when it is already solid</b>, and that is the
         * point rather than a redundancy: {@link #widen} runs afterwards and clears three
         * blocks of air in every neighbouring column, which without a claim on it would
         * happily cut the floor out from under the step next door whenever the path changes
         * height. Measured, that alone dropped the walk from 100% to 33%. The write side
         * treats a claim on an already-solid block as the no-op it is.
         */
        void claimStanding(Map<PocketBlock, Boolean> plan, int x, int y, int z) {
            plan.put(new PocketBlock(x, y, z), Boolean.TRUE);
            plan.put(new PocketBlock(x, y + 1, z), Boolean.TRUE);
            // Never floor over the arrival's own standing room: the pocket was dug by a
            // different pass, and a walkway laid at the player's feet would suffocate them
            // on the tick they materialise.
            if (!(x == this.pocketX && z == this.pocketZ && y - 1 >= this.pocketFloorY)) {
                plan.put(new PocketBlock(x, y - 1, z), Boolean.FALSE);
            }
        }

        /**
         * Opens the corridor out to three blocks across and one over head height, so it reads
         * like the worm tunnels it joins rather than like a one-block mine shaft.
         *
         * <p>Purely cosmetic, and deliberately unable to be anything else: it never claims a
         * block another step of the path has already claimed as its walkway, and it never
         * touches the spine. Every block it does claim is one the walk did not depend on.
         */
        void widen(Map<PocketBlock, Boolean> plan, int x, int y, int z) {
            // Only where the route actually had to bore. A cost-free stretch is one that
            // runs through a cavity the world already had, and widening that would be this
            // pass reshaping terrain it did not dig -- the passage should read as a bore
            // where it is one and as the tunnel it joined where it is that.
            if (this.air[cell(x, y, z)] && this.air[cell(x, y + 1, z)]) {
                return;
            }
            for (int[] offset : CONNECTOR_STAMP) {
                int nx = x + offset[0];
                int nz = z + offset[1];
                for (int dy = 0; dy < ARRIVAL_CONNECTOR_HEIGHT + offset[2]; dy++) {
                    int ny = y + dy;
                    if (ny < FLOOR_TOP || ny >= CEILING_BOTTOM || node(nx, ny, nz) < 0
                            || this.spineSolid[cell(nx, ny, nz)]) {
                        continue;
                    }
                    PocketBlock block = new PocketBlock(nx, ny, nz);
                    if (!plan.containsKey(block)) {
                        plan.put(block, Boolean.TRUE);
                    }
                }
            }
        }

        /** Index into the block arrays, resolving the column the first time it is asked for. */
        private int cell(int x, int y, int z) {
            int ix = x - this.pocketX + ARRIVAL_CONNECTOR_MAX_REACH;
            int iz = z - this.pocketZ + ARRIVAL_CONNECTOR_MAX_REACH;
            int column = ix * this.span + iz;
            if (!this.resolved[column]) {
                resolve(column, x, z);
            }
            return column * (this.bandHeight + 3) + (y - this.lowY + 1);
        }

        private void resolve(int column, int x, int z) {
            this.resolved[column] = true;
            Column resolvedColumn = this.columns.column(x, z);
            int base = column * (this.bandHeight + 3);
            for (int i = 0; i < this.bandHeight + 3; i++) {
                int y = this.lowY - 1 + i;
                if (y < FLOOR_TOP || y >= CEILING_BOTTOM) {
                    continue;
                }
                boolean isAir = resolvedColumn.air(x, y, z);
                this.air[base + i] = isAir;
                int shaft = resolvedColumn.shafts().length == 0
                        ? SHAFT_NONE : shaftState(resolvedColumn.shafts(), x, y, z);
                this.spineSolid[base + i] = shaft == SHAFT_SOLID;
                this.spineAir[base + i] = shaft == SHAFT_AIR;
                this.network[base + i] = isAir && (shaft == SHAFT_AIR || isTunnelCarved(x, y, z));
            }
        }
    }

    /**
     * The corridor's cross-section as (dx, dz, extra height) triples: a plus rather than a
     * 3x3 box, with one extra block of headroom over the middle. Three blocks wide across
     * either axis, and rounded rather than square, so it reads like the worm tunnels it
     * joins.
     */
    private static final int[][] CONNECTOR_STAMP = {{0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private static final PocketBlock[] NO_POCKET_BLOCKS = new PocketBlock[0];

    private static final int[][] NO_ROUTE = new int[0][];

    /** One column's five feature arrays, resolved once and asked about many Y values. */
    private final class Column {
        private final double field;
        private final Shaft[] shafts;
        private final Throne[] thrones;
        private final Nursery[] nurseries;
        private final Garden[] gardens;
        private final Larder[] larders;

        Column(double field, Shaft[] shafts, Throne[] thrones, Nursery[] nurseries, Garden[] gardens,
                Larder[] larders) {
            this.field = field;
            this.shafts = shafts;
            this.thrones = thrones;
            this.nurseries = nurseries;
            this.gardens = gardens;
            this.larders = larders;
        }

        Shaft[] shafts() {
            return this.shafts;
        }

        boolean air(int x, int y, int z) {
            return isAir(this.field, this.shafts, this.thrones, this.nurseries, this.gardens, this.larders, x, y, z);
        }

        boolean plainFabric(int x, int y, int z) {
            return isPlainFabric(this.shafts, this.thrones, this.nurseries, this.gardens, this.larders, x, y, z);
        }

        /** Whether the connectivity spine claims this block as its walkway floor. */
        boolean spineSolid(int x, int y, int z) {
            return this.shafts.length > 0 && shaftState(this.shafts, x, y, z) == SHAFT_SOLID;
        }
    }

    /**
     * Per-chunk memoisation of the five {@code *Near} lookups behind {@link Column}.
     *
     * <p>The arrival search reads thousands of columns spread over about two dozen chunks,
     * and the {@code *Near} arrays are a pure function of the chunk -- resolving them per
     * column instead would be the same answer for roughly ninety times the seeded draws.
     */
    private final class ColumnCache {
        private final Map<Long, Object[]> byChunk = new HashMap<>();

        Column column(int x, int z) {
            int chunkX = x - Math.floorMod(x, 16);
            int chunkZ = z - Math.floorMod(z, 16);
            Object[] near = this.byChunk.computeIfAbsent(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL),
                    key -> new Object[] {shaftsNear(chunkX, chunkZ), thronesNear(chunkX, chunkZ),
                            nurseriesNear(chunkX, chunkZ), gardensNear(chunkX, chunkZ), lardersNear(chunkX, chunkZ)});
            return new Column(colonyField(x, z),
                    shaftsForColumn((Shaft[]) near[0], x, z),
                    thronesForColumn((Throne[]) near[1], x, z),
                    nurseriesForColumn((Nursery[]) near[2], x, z),
                    gardensForColumn((Garden[]) near[3], x, z),
                    lardersForColumn((Larder[]) near[4], x, z));
        }
    }

    // ------------------------------------------------------------------
    // Soil patches (play-test round 2 item 8, re-specced in round 3, scaled again in round
    // 5) -- vanilla dirt, gravel and sand through ordinary fabric. A pure function of chunk
    // position, like
    // everything else in this class: the block WRITES only happen in ColonyChunkGenerator
    // (only it can touch a ChunkAccess), but which positions a patch claims and what
    // material fills it are decided here, which is what lets NoiseProbe measure them
    // without a running dimension. Consumed by ColonyChunkGenerator#fill, after its main
    // pass has already decided the ordinary block palette -- see #isPlainFabric.
    // ------------------------------------------------------------------

    /** {@link SoilPatch#material()} values. */
    public static final int SOIL_MATERIAL_DIRT = 0;
    public static final int SOIL_MATERIAL_GRAVEL = 1;
    public static final int SOIL_MATERIAL_SAND = 2;
    /** Round-5 micro-patch materials: the mod's own tier soils, used as speckle. */
    public static final int SOIL_MATERIAL_PACKED_SOIL = 3;
    public static final int SOIL_MATERIAL_AMBER_EARTH = 4;
    /** {@link #SOIL_SPECKLE_MATERIAL_BY_TIER} sentinel: this tier grows no speckle. */
    public static final int SOIL_MATERIAL_NONE = -1;

    /**
     * The walk's step directions, with the four horizontal ones repeated
     * {@link ColonyGeneratorTunables#SOIL_PATCH_HORIZONTAL_BIAS} times each. Drawing
     * uniformly from this array is what makes a patch a sheet rather than a ball.
     */
    private static final int[][] PATCH_STEPS = buildPatchSteps();

    private static int[][] buildPatchSteps() {
        int[][] horizontal = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        int[][] steps = new int[horizontal.length * SOIL_PATCH_HORIZONTAL_BIAS + 2][];
        int at = 0;
        for (int repeat = 0; repeat < SOIL_PATCH_HORIZONTAL_BIAS; repeat++) {
            for (int[] step : horizontal) {
                steps[at++] = step;
            }
        }
        steps[at++] = new int[] {0, 1, 0};
        steps[at] = new int[] {0, -1, 0};
        return steps;
    }

    /** One block a carve or decoration pass claims. */
    public record PocketBlock(int x, int y, int z) {
    }

    /** One realized soil patch: the blocks it claims and which material fills all of them. */
    public record SoilPatch(PocketBlock[] blocks, int material, int tier) {
    }

    /**
     * Which aggregate a tier's patches use alongside dirt (play-test round 3).
     *
     * <p>Indexed by tier, so it is a fact about where the player is standing rather than a
     * world-wide weighted draw: sand in the Fungal Gardens at the top, gravel in the
     * Nurseries and the Royal Depths below. {@link NoiseProbe} asserts the separation -- no
     * sand under the top tier, no gravel in it -- rather than trusting this array to be read
     * correctly at the one call site.
     */
    public static final int[] SOIL_PATCH_AGGREGATE_BY_TIER =
            {SOIL_MATERIAL_GRAVEL, SOIL_MATERIAL_GRAVEL, SOIL_MATERIAL_SAND};

    /**
     * Which soil a tier's round-5 micro-patches speckle its plain fabric with, or
     * {@link #SOIL_MATERIAL_NONE} for a tier that grows none. Indexed bottom-up like every
     * other {@code *_BY_TIER} array.
     *
     * <p>Play-test round 5, verbatim: "add packed soil randomly generating through the top
     * layer, small 1~3 block sized patches very frequent. and use amber earth in the second
     * layer in the same small 1~3 block sized patches very frequent."
     *
     * <p><b>Both entries are chosen to contrast with the fabric they land in</b>, which is
     * the whole point of the ask, and the mapping is only legible next to
     * {@link #fabricKind}: tier 2 (the Fungal Gardens, the "top layer") is Amber Earth
     * shot through with Hardened Soil, so Packed Soil is a block that appears nowhere else
     * in the band; tier 1 (the Nurseries, the "second layer") is Deep Loam and Hardened
     * Soil, so Amber Earth is likewise foreign there. Tier 0 is left alone -- the Royal
     * Depths are already 83% Hardened Soil veined with resin, and the ask named two layers.
     * {@link NoiseProbe} asserts the contrast per block rather than trusting this paragraph
     * to survive the next palette retune.
     */
    public static final int[] SOIL_SPECKLE_MATERIAL_BY_TIER =
            {SOIL_MATERIAL_NONE, SOIL_MATERIAL_AMBER_EARTH, SOIL_MATERIAL_PACKED_SOIL};

    /**
     * A micro-patch's step directions: the plain six neighbours, unweighted.
     *
     * <p>Deliberately not {@link #PATCH_STEPS}. A seam is weighted flat because 40-210
     * blocks in a ball would read as a lump; a cluster of one to three blocks has no shape
     * to speak of, so the horizontal bias would only cost draws to express a preference
     * nothing can see.
     */
    private static final int[][] SPECKLE_STEPS =
            {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};

    /**
     * Whether the solid position at (x, y, z) is plain fabric -- not a chamber's own shell
     * or dais, not the ramp/landing's forced-solid floor, and not the floor or ceiling cap.
     *
     * <p>{@link #isAir} decides solidity by checking, in order, the shaft and then each of
     * the four chamber kinds, falling through to the ambient tunnel/blob carve only once
     * all four say "not mine". This mirrors exactly that set of checks without the ambient
     * fallback, so combined with a separate {@code !isAir(...)} at the same position it
     * answers a narrower question {@link #isAir} itself cannot: not just "is this solid"
     * but "is this solid for no load-bearing reason at all" -- ordinary wilds or colony
     * fabric that a cosmetic decoration pass is free to touch, as opposed to a ramp floor
     * that keeps the spine walkable or a shell that keeps a room's neighbour from breaching
     * into it. {@link #soilPatchesForChunk} is the one caller; every other kind of solid
     * reaches {@code isAir} = false by a rule this method exists to keep a patch away from.
     *
     * <p>Callers are expected to already know the position is solid for the SAME column
     * arrays -- this only distinguishes which kind of solid it is, the same relationship
     * {@link #fabricKind} has to the finished palette.
     */
    public boolean isPlainFabric(Shaft[] columnShafts, Throne[] columnThrones, Nursery[] columnNurseries,
            Garden[] columnGardens, Larder[] columnLarders, int x, int y, int z) {
        if (y < FLOOR_TOP || y >= CEILING_BOTTOM) {
            return false;
        }
        if (columnShafts.length > 0 && shaftState(columnShafts, x, y, z) != SHAFT_NONE) {
            return false;
        }
        if (columnThrones.length > 0 && throneState(columnThrones, x, y, z) != THRONE_NONE) {
            return false;
        }
        if (columnNurseries.length > 0 && nurseryState(columnNurseries, x, y, z) != NURSERY_NONE) {
            return false;
        }
        if (columnGardens.length > 0 && gardenState(columnGardens, x, y, z) != GARDEN_NONE) {
            return false;
        }
        return columnLarders.length == 0 || larderState(columnLarders, x, y, z) == LARDER_NONE;
    }

    /**
     * Every soil patch that can reach into the chunk at ({@code minX}, {@code minZ}),
     * including the ones seeded by its eight neighbours.
     *
     * <p>The 3x3 neighbourhood is what lets a patch cross a chunk boundary, which round 3's
     * much larger patches need: a seam up to 27 blocks across confined to the chunk that
     * seeded it would be a seam with straight edges every 16 blocks. A patch wanders at most
     * {@link ColonyGeneratorTunables#SOIL_PATCH_MAX_RADIUS} = 13 from its seed column, and a
     * chunk two out starts 32 blocks away so its nearest seed column would need a radius of
     * 17 to reach here -- the ring is provably enough for any radius up to 16. Each chunk
     * writes only the blocks that land inside itself, and both chunks derive the same patch
     * because it is a pure function of the seed chunk -- the same discipline every other
     * cross-chunk feature in this class uses.
     */
    public SoilPatch[] soilPatchesNear(int minX, int minZ) {
        ColumnCache cache = new ColumnCache();
        List<SoilPatch> patches = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Collections.addAll(patches, soilPatchesForChunk(cache, minX + dx * 16, minZ + dz * 16));
            }
        }
        return patches.toArray(new SoilPatch[0]);
    }

    /**
     * The {@link ColonyGeneratorTunables#SOIL_PATCHES_MIN_PER_CHUNK}-{@link
     * ColonyGeneratorTunables#SOIL_PATCHES_MAX_PER_CHUNK} soil patches <b>seeded by</b> the
     * chunk at ({@code minX}, {@code minZ}). Their blocks may lie outside it; see
     * {@link #soilPatchesNear}.
     *
     * <p>Each patch is a bounded random walk with the vertical steps rationed: a seed
     * position is drawn inside the chunk's own XZ and inside one tier, and if it lands on
     * {@link #isPlainFabric} fabric the walk repeatedly picks a random already-claimed block
     * and a random neighbour of it, keeping the neighbour only if it too is plain fabric, is
     * inside the patch's own box, and is not already claimed. The walk stops once it reaches
     * its target size, or once it exhausts a generous attempt budget without finding new
     * eligible fabric -- whichever comes first, so a patch walled in by a chamber shell on
     * most sides terminates early instead of looping forever hunting for room that is not
     * there, the same way a vanilla ore vein terminates against a cave.
     *
     * <p><b>The patch stays inside the tier it was seeded in</b>, which is what makes the
     * per-tier palette meaningful: a seam that straddled a boundary would put sand two
     * blocks into the Nurseries. The box is at most three blocks tall against a 48-block
     * band, so confining it costs nothing.
     *
     * <p>Not gated by the colony field at all, unlike every chamber/comb chance elsewhere
     * in this class -- these are flavor blocks the colony field has no opinion about, in
     * the wilds exactly as much as in a colony core.
     */
    private SoilPatch[] soilPatchesForChunk(ColumnCache cache, int minX, int minZ) {
        // y = 8: a ninth independent stream, after shaft (0), throne (1), nursery (2),
        // garden (3), larder (4), colony centre (5), ender slots (6) and the chamber base
        // bearing (7) -- keyed by the chunk's own block coordinates rather than a cell,
        // since a patch is a per-chunk feature with no larger grid of its own.
        RandomSource random = this.factory.at(minX, 8, minZ);
        int count = between(SOIL_PATCHES_MIN_PER_CHUNK, SOIL_PATCHES_MAX_PER_CHUNK, random);
        List<SoilPatch> patches = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int targetSize = between(SOIL_PATCH_MIN_SIZE, SOIL_PATCH_MAX_SIZE, random);
            int layers = between(SOIL_PATCH_MIN_LAYERS, SOIL_PATCH_MAX_LAYERS, random);
            int tier = random.nextInt(TIER_COUNT);
            int material = random.nextInt(SOIL_PATCH_DIRT_WEIGHT + SOIL_PATCH_AGGREGATE_WEIGHT)
                    < SOIL_PATCH_DIRT_WEIGHT ? SOIL_MATERIAL_DIRT : SOIL_PATCH_AGGREGATE_BY_TIER[tier];
            int seedX = minX + random.nextInt(16);
            int seedZ = minZ + random.nextInt(16);
            int bandBottom = Math.max(FLOOR_TOP, tierMinY(tier));
            int bandTop = Math.min(CEILING_BOTTOM, tierMaxY(tier));
            if (bandTop - bandBottom <= layers) {
                continue;
            }
            int baseY = bandBottom + random.nextInt(bandTop - bandBottom - layers);
            int seedY = baseY + random.nextInt(layers);
            if (!isPatchEligible(cache, seedX, seedY, seedZ)) {
                continue;
            }

            List<int[]> claimed = new ArrayList<>(targetSize);
            Set<Long> claimedKeys = new HashSet<>();
            claimed.add(new int[] {seedX, seedY, seedZ});
            claimedKeys.add(positionKey(seedX, seedY, seedZ));
            int attempts = 0;
            int maxAttempts = targetSize * 8;
            while (claimed.size() < targetSize && attempts < maxAttempts) {
                attempts++;
                int[] from = claimed.get(random.nextInt(claimed.size()));
                int[] offset = PATCH_STEPS[random.nextInt(PATCH_STEPS.length)];
                int nx = from[0] + offset[0];
                int ny = from[1] + offset[1];
                int nz = from[2] + offset[2];
                if (Math.abs(nx - seedX) > SOIL_PATCH_MAX_RADIUS || Math.abs(nz - seedZ) > SOIL_PATCH_MAX_RADIUS
                        || ny < baseY || ny >= baseY + layers
                        || !claimedKeys.add(positionKey(nx, ny, nz))
                        || !isPatchEligible(cache, nx, ny, nz)) {
                    continue;
                }
                claimed.add(new int[] {nx, ny, nz});
            }

            patches.add(new SoilPatch(toBlocks(claimed), material, tier));
        }
        return patches.toArray(new SoilPatch[0]);
    }

    // ------------------------------------------------------------------
    // Soil micro-patches (play-test round 5, item 3) -- the mod's own tier soils used as
    // speckle through the two upper bands' plain fabric.
    //
    // Logan, verbatim: "add packed soil randomly generating through the top layer, small
    // 1~3 block sized patches very frequent. and use amber earth in the second layer in the
    // same small 1~3 block sized patches very frequent."
    //
    // A separate pass rather than a knob on the seams, because it is the OPPOSITE shape:
    // the seams got 3.5x bigger in the same round precisely so they read as something to
    // dig at, and these exist to break up the flat fabric between them. Sharing one
    // algorithm would have meant one distribution trying to be both.
    //
    // Same safety rule as the seams and through the same predicate: fabric-material
    // substitution only. A speckle never replaces air, never touches a chamber shell or
    // interior, a ramp or landing floor, a comb, the membrane or the floor/ceiling caps --
    // see #isPatchEligible, which is #isPlainFabric plus "and it is actually solid".
    // ------------------------------------------------------------------

    /**
     * Every micro-patch that can reach into the chunk at ({@code minX}, {@code minZ}),
     * including the ones seeded by its eight neighbours.
     *
     * <p>The 3x3 ring is the same discipline {@link #soilPatchesNear} uses and is very
     * comfortably sufficient here: a cluster of at most
     * {@link ColonyGeneratorTunables#SOIL_SPECKLE_MAX_SIZE} blocks grown one step at a time
     * wanders at most {@code SOIL_SPECKLE_MAX_SIZE - 1} = 2 blocks from its seed column,
     * against the 17 a chunk two out would need. Confining a cluster to its own chunk
     * instead would have truncated the two-block overhang on every chunk border, which is
     * the same straight-edge artifact round 3 introduced the ring to remove -- smaller, but
     * the same kind, and the ring costs nothing to reuse.
     */
    public SoilPatch[] soilSpecklesNear(int minX, int minZ) {
        ColumnCache cache = new ColumnCache();
        List<SoilPatch> speckles = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Collections.addAll(speckles, soilSpecklesForChunk(cache, minX + dx * 16, minZ + dz * 16));
            }
        }
        return speckles.toArray(new SoilPatch[0]);
    }

    /**
     * Both fabric-substitution passes for one chunk -- {@link #soilPatchesNear}'s seams and
     * {@link #soilSpecklesNear}'s micro-patches -- over one shared {@link ColumnCache}.
     *
     * <p>The generator's single call site, because the two passes write identically (a
     * {@link SoilPatch}'s material index against a block state) and resolving the same
     * eighteen chunks' feature arrays twice would be the same answer for twice the seeded
     * draws. The two are still separately reachable above, which is how {@link NoiseProbe}
     * measures each distribution on its own.
     */
    public SoilPatch[] soilFabricNear(int minX, int minZ) {
        ColumnCache cache = new ColumnCache();
        List<SoilPatch> all = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Collections.addAll(all, soilPatchesForChunk(cache, minX + dx * 16, minZ + dz * 16));
                Collections.addAll(all, soilSpecklesForChunk(cache, minX + dx * 16, minZ + dz * 16));
            }
        }
        return all.toArray(new SoilPatch[0]);
    }

    /**
     * The micro-patches <b>seeded by</b> the chunk at ({@code minX}, {@code minZ}) --
     * {@link ColonyGeneratorTunables#SOIL_SPECKLE_CLUSTERS_PER_CHUNK_PER_TIER} attempts in
     * each tier {@link #SOIL_SPECKLE_MATERIAL_BY_TIER} names a material for. An attempt that
     * seeds on air or on load-bearing solid is dropped, so the realized count is lower than
     * the attempt count and varies with how hollow the neighbourhood is.
     *
     * <p>Each cluster is a one-to-three block walk over the plain six neighbours, confined
     * to its own tier band so the material stays a fact about which layer the player is in
     * -- the same confinement the seams have, and asserted the same way.
     */
    private SoilPatch[] soilSpecklesForChunk(ColumnCache cache, int minX, int minZ) {
        // y = 9: a tenth independent stream, after the soil seams' 8.
        //
        // THE FIRST DRAW IS BURNED, and that is not superstition. `factory.at(x, y, z)` puts
        // y into Mth.getSeed's low bits only, seedHi comes out byte-identical, and
        // Xoroshiro's first output is one rotate-and-add -- so near the origin two streams
        // differing only in y agree in draw #1 to nine decimal places (measured in play-test
        // round 2; the chamber-bearing bug it caused is in docs/DECISIONS.md). Later draws
        // diffuse fine. Burning one therefore makes this stream independent of the seams'
        // y = 8 stream everywhere, rather than only far from spawn.
        //
        // Both tiers are then drawn from THIS ONE stream in sequence rather than from two
        // y-keyed streams of their own, for exactly the same reason: sequential draws off a
        // single Xoroshiro are independent by construction, y-adjacent ones are not.
        RandomSource random = this.factory.at(minX, 9, minZ);
        random.nextLong();
        List<SoilPatch> speckles = new ArrayList<>();

        for (int tier = 0; tier < TIER_COUNT; tier++) {
            int material = SOIL_SPECKLE_MATERIAL_BY_TIER[tier];
            if (material == SOIL_MATERIAL_NONE) {
                continue;
            }
            int bandBottom = Math.max(FLOOR_TOP, tierMinY(tier));
            int bandTop = Math.min(CEILING_BOTTOM, tierMaxY(tier));
            if (bandTop <= bandBottom) {
                continue;
            }
            for (int i = 0; i < SOIL_SPECKLE_CLUSTERS_PER_CHUNK_PER_TIER; i++) {
                int seedX = minX + random.nextInt(16);
                int seedZ = minZ + random.nextInt(16);
                int seedY = bandBottom + random.nextInt(bandTop - bandBottom);
                int targetSize = between(SOIL_SPECKLE_MIN_SIZE, SOIL_SPECKLE_MAX_SIZE, random);
                if (!isPatchEligible(cache, seedX, seedY, seedZ)) {
                    continue;
                }

                List<int[]> claimed = new ArrayList<>(targetSize);
                Set<Long> claimedKeys = new HashSet<>();
                claimed.add(new int[] {seedX, seedY, seedZ});
                claimedKeys.add(positionKey(seedX, seedY, seedZ));
                int attempts = 0;
                int maxAttempts = targetSize * 8;
                while (claimed.size() < targetSize && attempts < maxAttempts) {
                    attempts++;
                    int[] from = claimed.get(random.nextInt(claimed.size()));
                    int[] offset = SPECKLE_STEPS[random.nextInt(SPECKLE_STEPS.length)];
                    int nx = from[0] + offset[0];
                    int ny = from[1] + offset[1];
                    int nz = from[2] + offset[2];
                    if (ny < bandBottom || ny >= bandTop
                            || !claimedKeys.add(positionKey(nx, ny, nz))
                            || !isPatchEligible(cache, nx, ny, nz)) {
                        continue;
                    }
                    claimed.add(new int[] {nx, ny, nz});
                }

                speckles.add(new SoilPatch(toBlocks(claimed), material, tier));
            }
        }
        return speckles.toArray(new SoilPatch[0]);
    }

    /**
     * Whether a patch may claim (x, y, z): solid fabric that nothing load-bearing owns.
     *
     * <p>Resolved through {@link ColumnCache} rather than through one chunk's own arrays,
     * because a patch is allowed to leave the chunk that seeded it and the answer has to be
     * the same whichever chunk is asking.
     */
    private boolean isPatchEligible(ColumnCache cache, int x, int y, int z) {
        Column column = cache.column(x, z);
        return column.plainFabric(x, y, z) && !column.air(x, y, z);
    }

    /**
     * A walk's already-claimed set key. Exact rather than a hash: y is inside
     * {@code [0, HEIGHT)} so eight bits hold it, and 26 bits each cover x and z out to
     * +-33,554,432, past the 30,000,000 world border. Nothing in a patch or a cluster is
     * more than {@link ColonyGeneratorTunables#SOIL_PATCH_MAX_RADIUS} blocks from its seed
     * anyway, so two claimed positions cannot alias even in principle.
     *
     * <p>Replaced a linear scan of the claimed list when round 5 took a seam to 270 blocks
     * and 2160 walk attempts -- that scan was quadratic in the target size, and the target
     * size had just gone up 4.5x. Consumes no randomness and rejects exactly the same
     * positions, so the terrain is bit-identical to what the scan produced.
     */
    private static long positionKey(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 34) | (((long) z & 0x3FFFFFFL) << 8) | (y & 0xFFL);
    }

    private static PocketBlock[] toBlocks(List<int[]> claimed) {
        PocketBlock[] blocks = new PocketBlock[claimed.size()];
        for (int b = 0; b < claimed.size(); b++) {
            int[] pos = claimed.get(b);
            blocks[b] = new PocketBlock(pos[0], pos[1], pos[2]);
        }
        return blocks;
    }
}
