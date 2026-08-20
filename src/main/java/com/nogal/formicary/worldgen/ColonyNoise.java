package com.nogal.formicary.worldgen;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ACCENT_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ACCENT_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
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
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_COMB_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_COMB_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_END;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_HALF_WIDTH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_CORRIDOR_START;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_DOME_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_ELIGIBILITY_MIN_F;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LARDER_MAX_REACH;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * {@link #coloniesNear}'s own derivation leaves 528 blocks to the nearest excluded
     * centre against an outer radius of 150, so 56 blocks of slack changes nothing.
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
     * {@code COLONY_SPACING - COLONY_JITTER} = 288 blocks rather than "whatever the noise
     * did", and 288 is the number the boss-bar invariant rests on.
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
     * {@code C} the own-cell centre is at most {@code 192*sqrt(2) + 48} = 320 blocks away
     * while a centre two cells out is at least {@code 2*384 - 192 - 48} = 528. The nearest
     * centre is therefore always inside the ring, and since
     * {@link ColonyGeneratorTunables#COLONY_OUTER_RADIUS} is 150 no colony outside it can
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
     * that colony's own, not a neighbour's: the ring tops out at about 131 blocks from the
     * centre while the nearest other centre is at least {@code COLONY_SPACING -
     * COLONY_JITTER} = 288 away, so no other colony can be closer than 157. The bearings are
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
     * within 76 blocks of its colony centre -- inside
     * {@link ColonyGeneratorTunables#COLONY_CORE_RADIUS} = 100, i.e. always at full density.
     * {@link NoiseProbe}'s colony section asserts exactly that rather than trusting it.
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
     * field is 1.0 -- well inside {@link ColonyGeneratorTunables#COLONY_CORE_RADIUS} = 100,
     * where the field is still exactly 1.0. Writing the test anyway would let "one throne per
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
     * <p>{@code comb1}/{@code comb2} are the two guaranteed Provision Comb positions (spec:
     * "a deterministic minimum of 2 per larder from the chamber's own seeded random"),
     * drawn from the SAME random stream as the chamber's own geometry, immediately after
     * the approach bearing -- so a larder's shape and its guaranteed loot are one seeded
     * draw, not two that could silently disagree on a re-derivation. They cannot be a
     * probabilistic top-up: {@code ColonyChunkGenerator#decorateLarderSurface} sees one
     * wall block at a time as the per-chunk decoration pass visits it and has no way to
     * count what it has already placed across a room that can span several chunks, so the
     * only way to GUARANTEE a count is to pick the positions up front and force-write them
     * regardless of what the probabilistic roll does elsewhere in the room. Each position
     * sits {@link ColonyGeneratorTunables#LARDER_COMB_RADIUS} out (one block past the
     * interior radius, safely inside the forced-solid shell even after independent
     * per-axis rounding from a non-integer centre) at a fixed
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
            int combX1, int combZ1, int combX2, int combZ2, int combY,
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

        // The two guaranteed comb slots -- same stream, drawn right after the bearing.
        // NOT a free choice of angle: the corridor punches through the shell at the
        // bearing facing back toward the ramp axis (`approach + PI`, seen from the
        // centre), and an early version of this method drew both angles uniformly over
        // the full circle -- NoiseProbe's larder section caught 22 of 578 sampled slots
        // (3.8%) landing inside that doorway, where they would have blocked the room's
        // only entrance instead of decorating its shell. Anchoring both slots 90 degrees
        // off the doorway bearing, with only +-30 degrees of jitter for variety, keeps
        // them at least 60 degrees from a doorway whose own angular width at this radius
        // is under 10 degrees (`atan(LARDER_CORRIDOR_HALF_WIDTH / LARDER_COMB_RADIUS)`) --
        // comfortable margin, not a coincidence of the two particular seeds tested.
        double doorwayBearing = approach + Math.PI;
        double combJitter = Math.PI / 6.0;
        double combAngle1 = doorwayBearing + Math.PI / 2.0 + (random.nextDouble() * 2.0 - 1.0) * combJitter;
        double combAngle2 = doorwayBearing - Math.PI / 2.0 + (random.nextDouble() * 2.0 - 1.0) * combJitter;
        int combX1 = (int) Math.round(centreXd + LARDER_COMB_RADIUS * Math.cos(combAngle1));
        int combZ1 = (int) Math.round(centreZd + LARDER_COMB_RADIUS * Math.sin(combAngle1));
        int combX2 = (int) Math.round(centreXd + LARDER_COMB_RADIUS * Math.cos(combAngle2));
        int combZ2 = (int) Math.round(centreZd + LARDER_COMB_RADIUS * Math.sin(combAngle2));

        return new Larder(centreXd, centreZd, shaft.axisX(), shaft.axisZ(), dirX, dirZ, floorY, tier, tierDraw,
                combX1, combZ1, combX2, combZ2, floorY + LARDER_COMB_HEIGHT,
                isChamberAnchored(shaft, centreXd, centreZd, LARDER_ELIGIBILITY_MIN_F));
    }

    /**
     * Whether a chamber centred at (x, z) would sit inside a throne's exclusion ball -- i.e.
     * within {@link ColonyGeneratorTunables#THRONE_LARDER_CLEARANCE} of a throne centre.
     *
     * <p><b>Only the nearest colony's throne is checked, and that is exact rather than an
     * approximation.</b> A throne centre lands within 76 blocks of its own colony centre
     * ({@link #throneForCell} derives that bound), so a throne inside the 62.5-block clearance
     * of this point belongs to a colony centre at most 138.5 blocks away -- and colony centres
     * are at least {@code COLONY_SPACING - COLONY_JITTER} = 288 apart, so no second colony can
     * be that close. One candidate is all there is.
     *
     * <p>The 3x3 scan mirrors {@link #nearestColony}'s own derivation rather than calling it,
     * because what {@link #throneForCell} needs is the winning <em>cell</em> and that method
     * returns only the centre. Cost is nine one-draw colony centres plus one throne, and only
     * on a tier-0 pick.
     */
    private boolean crowdsAThrone(double x, double z) {
        int cellX = Math.floorDiv(Mth.floor(x), COLONY_SPACING);
        int cellZ = Math.floorDiv(Mth.floor(z), COLONY_SPACING);
        int bestX = cellX;
        int bestZ = cellZ;
        double bestSq = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Colony colony = colonyCenterForCell(cellX + dx, cellZ + dz);
                double ddx = x - colony.centreX();
                double ddz = z - colony.centreZ();
                double distanceSq = ddx * ddx + ddz * ddz;
                if (distanceSq < bestSq) {
                    bestSq = distanceSq;
                    bestX = cellX + dx;
                    bestZ = cellZ + dz;
                }
            }
        }
        Throne throne = throneForCell(bestX, bestZ);
        double gapX = x - throne.centreX();
        double gapZ = z - throne.centreZ();
        return gapX * gapX + gapZ * gapZ < THRONE_LARDER_CLEARANCE * THRONE_LARDER_CLEARANCE;
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
     * throne centre lands anywhere within 124 blocks of its 384-block cell centre, so the
     * nearest of the 3x3 can be up to {@code 192*sqrt(2) + 124} = 396 blocks away while a
     * chamber two cells out can sit as close as {@code 2*384 + 192 - 124 - 384} = 452 --
     * close enough to matter. Two rings settle it in both directions.
     */
    private static final int NEAREST_QUERY_CELL_RADIUS = 2;

    /**
     * Cell rings searched by {@link #nearestNursery} / {@link #nearestGarden} /
     * {@link #nearestLarder}, whose 96-block cells now have to be filtered for colony
     * eligibility before the answer means anything.
     *
     * <p>Six, and the number is derived rather than generous. From an arbitrary point the
     * nearest colony centre is at most {@code 192*sqrt(2) + 48} = 320 blocks away, and a
     * chamber inside that colony is eligible out to about 134 blocks from its centre
     * (solving the falloff for {@link ColonyGeneratorTunables#CHAMBER_ELIGIBILITY_MIN_F}),
     * so an eligible chamber always exists within {@code 320 + 134} = 454 blocks. Six rings
     * of 96 reach 576, which covers it with margin. 169 cells of closed-form arithmetic is
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

    /** Whether (x, z) is inside the corridor's footprint, measured along the axis ray. */
    private static boolean isInLarderCorridor(Larder larder, int x, int z) {
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
}
