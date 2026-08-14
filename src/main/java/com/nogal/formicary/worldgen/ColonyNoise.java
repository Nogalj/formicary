package com.nogal.formicary.worldgen;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ACCENT_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ACCENT_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_LARGE_THRESHOLD_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_LARGE_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_LARGE_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SMALL_THRESHOLD_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SMALL_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_SMALL_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FLOOR_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.HARDENED_ACCENT_THRESHOLD_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LANDING_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.LANDING_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MEMBRANE_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MEMBRANE_THRESHOLD;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MEMBRANE_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_AIR_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_CENTER_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_HALF_WIDTH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.RAMP_RADIANS_PER_BLOCK;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ROYAL_RESIN_THRESHOLD;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SHAFT_JITTER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SHAFT_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SHAFT_SPACING;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TUNNEL_HALF_WIDTH_BY_TIER;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TUNNEL_XZ_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TUNNEL_Y_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.WALL_JITTER_AMOUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.WALL_JITTER_SCALE;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierIndex;

import java.util.List;

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

    public ColonyNoise(PositionalRandomFactory factory) {
        this.factory = factory;
        this.tunnelA = octave(factory, "colony_tunnel_a");
        this.tunnelB = octave(factory, "colony_tunnel_b");
        this.chamberSmall = octave(factory, "colony_chamber_small");
        this.chamberLarge = octave(factory, "colony_chamber_large");
        this.accent = octave(factory, "colony_accent");
        this.wallJitter = octave(factory, "colony_wall_jitter");
        this.membrane = octave(factory, "colony_membrane");
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

    /**
     * Every shaft that can possibly reach into the 16x16 area rooted at
     * ({@code blockMinX}, {@code blockMinZ}).
     *
     * <p>The 3x3 cell neighbourhood is provably enough: a cell's axis lands within
     * {@code SHAFT_JITTER/2} of its centre, so the nearest axis two cells away is at least
     * {@code 2*SHAFT_SPACING - SHAFT_JITTER/2 - 16} = 88 blocks from the chunk, far outside
     * {@link ColonyGeneratorTunables#SHAFT_MAX_REACH}.
     */
    public Shaft[] shaftsNear(int blockMinX, int blockMinZ) {
        int cellX = Math.floorDiv(blockMinX, SHAFT_SPACING);
        int cellZ = Math.floorDiv(blockMinZ, SHAFT_SPACING);
        Shaft[] out = new Shaft[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out[i++] = shaftForCell(cellX + dx, cellZ + dz);
            }
        }
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
                    int boundary = MIN_Y + band * TIER_HEIGHT;
                    if (y >= boundary && y < boundary + LANDING_HEIGHT) {
                        landingVerdict = SHAFT_AIR;
                    } else if (y == boundary - 1) {
                        landingVerdict = SHAFT_SOLID;
                    }
                }
            }
        }
        return landingVerdict;
    }

    /** Additive-only wall jitter in blocks, always in {@code [0, WALL_JITTER_AMOUNT]}. */
    private double jitter(int x, int z) {
        double n = this.wallJitter.getValue(x * WALL_JITTER_SCALE, 0.0, z * WALL_JITTER_SCALE);
        return WALL_JITTER_AMOUNT * (0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, n)));
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

    /** True where a blob chamber carves. */
    public boolean isChamberCarved(int x, int y, int z) {
        int tier = tierIndex(y);
        double small = this.chamberSmall.getValue(
                x * CHAMBER_SMALL_XZ_SCALE, y * CHAMBER_SMALL_Y_SCALE, z * CHAMBER_SMALL_XZ_SCALE);
        if (small > CHAMBER_SMALL_THRESHOLD_BY_TIER[tier]) {
            return true;
        }
        double large = this.chamberLarge.getValue(
                x * CHAMBER_LARGE_XZ_SCALE, y * CHAMBER_LARGE_Y_SCALE, z * CHAMBER_LARGE_XZ_SCALE);
        return large > CHAMBER_LARGE_THRESHOLD_BY_TIER[tier];
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
     */
    public boolean isAir(Shaft[] columnShafts, int x, int y, int z) {
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
        return isTunnelCarved(x, y, z) || isChamberCarved(x, y, z);
    }

    // ------------------------------------------------------------------
    // Daylight Membrane -- the M5 exit, embedded in the ceiling cap
    // ------------------------------------------------------------------

    /**
     * Whether the ceiling block at (x, y, z) is a Daylight Membrane rather than plain cap.
     *
     * <p>Three conditions, all necessary:
     * <ul>
     *   <li>{@code y} is in the bottom {@link ColonyGeneratorTunables#MEMBRANE_THICKNESS}
     *       layers of the cap, so the patch is flush with the ceiling's underside.</li>
     *   <li>The block directly below the cap is air, so the patch is actually <i>visible</i>
     *       from inside the Upper Galleries. Roughly two thirds of the ceiling in this
     *       dimension is backed by solid fabric; a patch there would be a decoration nobody
     *       ever sees, and would make the reachability number a lie.</li>
     *   <li>The 2D patch field is above {@link ColonyGeneratorTunables#MEMBRANE_THRESHOLD}.</li>
     * </ul>
     *
     * <p>The visibility test reads {@link #isAir} at a fixed Y rather than the chunk, so it
     * stays a pure function of world position -- the same property that lets
     * {@code ColonyChunkGenerator#getBaseColumn} and the chunk fill agree without either
     * looking at the other's output.
     */
    public boolean isDaylightMembrane(Shaft[] columnShafts, int x, int y, int z) {
        if (y < CEILING_BOTTOM || y >= CEILING_BOTTOM + MEMBRANE_THICKNESS) {
            return false;
        }
        if (!isAir(columnShafts, x, CEILING_BOTTOM - 1, z)) {
            return false;
        }
        return probeMembrane(x, z) > MEMBRANE_THRESHOLD;
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
            case 3 -> FABRIC_PACKED_SOIL;
            case 2 -> FABRIC_AMBER_EARTH;
            case 1 -> FABRIC_DEEP_LOAM;
            // Royal Depths: Deep Loam shot through with Hardened Soil (above) and resin.
            default -> a < ROYAL_RESIN_THRESHOLD ? FABRIC_RESIN_BLOCK : FABRIC_DEEP_LOAM;
        };
    }
}
