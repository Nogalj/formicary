package com.nogal.formicary.worldgen;

import net.minecraft.util.Mth;

/**
 * EVERY magic number the Formicary dimension's shape is made of, in one place.
 *
 * <p>This exists so the visual iteration pass (which needs a running client, and so happens
 * after the generator lands) can retune the look without archaeology: nothing in
 * {@link ColonyChunkGenerator} or {@link ColonyNoise} hardcodes a constant, they all read
 * from here. {@link NoiseProbe} runs the same numbers headlessly and prints air fractions,
 * tunnel widths and cross-sections, so a change here can be sanity-checked before booting
 * a client.
 *
 * <h2>Tier indices</h2>
 * Every {@code *_BY_TIER} array is indexed <b>bottom-up</b> by {@link #tierIndex(int)}:
 * <pre>
 *   0 = Royal Depths     y   0 -  47
 *   1 = Nurseries        y  48 -  95
 *   2 = Fungal Gardens   y  96 - 143
 *   3 = Upper Galleries  y 144 - 191
 * </pre>
 * The player enters at the top and descends, so the array order reads "deepest first".
 */
public final class ColonyGeneratorTunables {

    // ------------------------------------------------------------------
    // Vertical layout
    // ------------------------------------------------------------------

    /** Dimension floor. Must be a multiple of 16 (DimensionType codec enforces it). */
    public static final int MIN_Y = 0;
    /** Dimension height. Must be a multiple of 16. Four 48-block tiers. */
    public static final int HEIGHT = 192;
    /** Blocks per tier band. {@code HEIGHT / TIER_COUNT}. */
    public static final int TIER_HEIGHT = 48;
    public static final int TIER_COUNT = 4;

    /** Solid Hardened Soil cap at the bottom -- never carved, so there is no exposed void. */
    public static final int FLOOR_THICKNESS = 5;
    /** Solid Packed Soil cap at the top. M5 embeds exit membranes here; keep it plain. */
    public static final int CEILING_THICKNESS = 6;

    /** Lowest carvable Y. */
    public static final int FLOOR_TOP = MIN_Y + FLOOR_THICKNESS;
    /** First Y of the ceiling cap; carving stops below this. */
    public static final int CEILING_BOTTOM = MIN_Y + HEIGHT - CEILING_THICKNESS;

    /** Where {@code getSpawnHeight} points -- inside the Upper Galleries. M5 owns real entry. */
    public static final int SPAWN_HEIGHT = 176;

    // ------------------------------------------------------------------
    // Worm tunnels -- two noise fields, carve where both are near zero
    // ------------------------------------------------------------------

    /**
     * Horizontal sampling scale of both worm fields. A single-octave Perlin field's feature
     * size is about {@code 1 / scale} blocks, so 0.024 gives roughly 40-block-long lazy
     * curves. Smaller = longer, lazier tunnels.
     */
    public static final double TUNNEL_XZ_SCALE = 0.024;
    /**
     * Vertical sampling scale. Deliberately above the XZ scale: the tunnel runs along
     * {@code grad(a) x grad(b)}, so making both fields vary faster in Y tips that direction
     * toward the horizontal, which is what makes these read as galleries rather than chimneys.
     */
    public static final double TUNNEL_Y_SCALE = 0.038;

    /**
     * Half-width of the carved slab around each worm field's zero set, in raw noise units.
     * Tunnel thickness is roughly {@code halfWidth / scale} blocks per side, so 0.07 at the
     * scales above is about a 3-block bore. Spec: narrow in the Upper Galleries, widest in
     * Fungal Gardens.
     */
    public static final double[] TUNNEL_HALF_WIDTH_BY_TIER = {0.085, 0.095, 0.115, 0.070};

    // ------------------------------------------------------------------
    // Blob chambers -- two scales so tiers differ in chamber SIZE, not just count.
    // Two independent fields (rather than one field re-scaled per tier) keeps the
    // carve continuous across a tier boundary instead of leaving a seam there.
    //
    // Feature size is about 1/scale blocks: SMALL is a ~14-block cell (rooms a handful of
    // blocks across), LARGE a ~29-block cell (cathedral rooms). The first pass used
    // 0.021 / 0.0085 here and the probe showed it merging into 40-block mega-caverns --
    // low-frequency noise thresholded generously does not make "a few big rooms", it makes
    // one continuous void.
    // ------------------------------------------------------------------

    public static final double CHAMBER_SMALL_XZ_SCALE = 0.070;
    public static final double CHAMBER_SMALL_Y_SCALE = 0.090;
    public static final double CHAMBER_LARGE_XZ_SCALE = 0.034;
    public static final double CHAMBER_LARGE_Y_SCALE = 0.045;

    /** Carve where the small-blob field exceeds this. Lower = more/bigger small chambers. */
    public static final double[] CHAMBER_SMALL_THRESHOLD_BY_TIER = {0.50, 0.32, 0.36, 0.44};
    /** Carve where the large-blob field exceeds this. Lower = more/bigger cathedral rooms. */
    public static final double[] CHAMBER_LARGE_THRESHOLD_BY_TIER = {0.34, 0.55, 0.50, 9.00};

    // ------------------------------------------------------------------
    // Organic wall jitter -- purely additive, so it can bulge a shaft wall outward
    // but never pinch one shut (which would break the connectivity guarantee).
    // ------------------------------------------------------------------

    public static final double WALL_JITTER_SCALE = 0.13;
    public static final double WALL_JITTER_AMOUNT = 1.1;

    // ------------------------------------------------------------------
    // Connectivity spine -- a jittered grid of descending helicoid ramps.
    // These, not the noise tunnels, are what guarantees you can walk the whole
    // dimension top-to-bottom AND bottom-to-top without mining (mining the fabric
    // is gated behind full Chitin Armor, so a dead end would be a soft-lock).
    //
    // The ramp is defined by a closed-form FLOOR HEIGHT, not by a distance field around a
    // helix path. That matters: floor height is a function of the bearing from the axis
    // alone, so the step between two neighbouring columns is
    //   dY = (1 / RAMP_RADIANS_PER_BLOCK) / distanceFromAxis
    // and keeping that below 1 over the whole annulus makes the ramp walkable in both
    // directions by construction -- no matter what the noise or the wall jitter do. The
    // first version carved a tube around the helix instead and let the floor emerge; the
    // probe's walkability BFS showed that ramp breaking on 2 seeds out of 4.
    // ------------------------------------------------------------------

    /** One ramp per this many blocks on each axis. */
    public static final int SHAFT_SPACING = 48;
    /** Seed-jitter of the ramp axis inside its cell, in blocks (total spread). */
    public static final double SHAFT_JITTER = 16.0;
    /** Distance from the axis to the middle of the ramp. */
    public static final double RAMP_CENTER_RADIUS = 7.5;
    /** Half the ramp's walkable width. The inner edge sits at CENTER - this. */
    public static final double RAMP_HALF_WIDTH = 3.0;
    /**
     * Radians of turn per block of rise, i.e. the ramp climbs {@code 1 / this} = 3.85 blocks
     * of Y per radian, and a full turn descends {@code 2*PI / this} = about 24 blocks.
     * Steepness at the ramp's inner edge is {@code (1/this) / (CENTER - HALF_WIDTH)} = 0.85
     * blocks of rise per block walked; push that above 1.0 (by widening the ramp inward or
     * shrinking this) and the inner edge stops being climbable.
     */
    public static final double RAMP_RADIANS_PER_BLOCK = 0.26;
    /** Air blocks carved above each ramp floor block. Needs >= 2 for a player. */
    public static final int RAMP_AIR_HEIGHT = 3;

    /**
     * A round landing chamber centred on the ramp axis at each tier boundary. Purely for
     * readability -- it marks the tier change and gives the spine somewhere to open out --
     * the ramp itself keeps its floor straight through it.
     */
    public static final double LANDING_RADIUS = 11.0;
    public static final int LANDING_HEIGHT = 6;

    /** Widest a shaft's carve can reach from its axis; used to prune the per-column search. */
    public static final double SHAFT_MAX_REACH =
            Math.max(RAMP_CENTER_RADIUS + RAMP_HALF_WIDTH + WALL_JITTER_AMOUNT, LANDING_RADIUS + WALL_JITTER_AMOUNT)
                    + 1.0;

    // ------------------------------------------------------------------
    // Solid palette
    // ------------------------------------------------------------------

    /** Sampling scale of the blotchy field that decides structural accents. */
    public static final double ACCENT_XZ_SCALE = 0.055;
    public static final double ACCENT_Y_SCALE = 0.075;

    /** Hardened Soil appears where the accent field is above this, per tier. */
    public static final double[] HARDENED_ACCENT_THRESHOLD_BY_TIER = {0.10, 0.34, 0.36, 0.32};
    /** Royal Depths only: buried Resin Block veins where the accent field is below this. */
    public static final double ROYAL_RESIN_THRESHOLD = -0.42;

    // ------------------------------------------------------------------
    // Floor / wall decoration (placed in buildSurface, see ColonyChunkGenerator)
    // All chances are 0..1, rolled per candidate block from a positional random.
    // ------------------------------------------------------------------

    /** A floor is "roomy" (chamber, not tunnel) if there is still air this far above it. */
    public static final int ROOMY_CLEARANCE = 4;

    public static final double[] FUNGAL_CARPET_CHANCE_BY_TIER = {0.00, 0.02, 0.16, 0.025};
    public static final double[] FUNGAL_BLOOM_CHANCE_BY_TIER = {0.00, 0.01, 0.07, 0.012};
    /** Resin Weep embedded in walls / ceilings. */
    public static final double[] RESIN_WEEP_CHANCE_BY_TIER = {0.035, 0.010, 0.045, 0.008};
    /** Brood Comb lining roomy surfaces -- the Nurseries' signature. */
    public static final double[] BROOD_COMB_CHANCE_BY_TIER = {0.010, 0.170, 0.000, 0.000};
    /** Egg Cluster on roomy floors. */
    public static final double[] EGG_CLUSTER_CHANCE_BY_TIER = {0.000, 0.060, 0.000, 0.000};
    /** Royal Comb -- rare, and only where brood comb already belongs. */
    public static final double[] ROYAL_COMB_CHANCE_BY_TIER = {0.004, 0.008, 0.000, 0.000};
    /** Royal Depths' sparse amber accents: Resin Block exposed in a wall. */
    public static final double[] RESIN_BLOCK_CHANCE_BY_TIER = {0.020, 0.000, 0.000, 0.000};

    // ------------------------------------------------------------------
    // Mob spawning at chunk generation (see ColonyChunkGenerator#spawnOriginalMobs)
    // ------------------------------------------------------------------

    /** Attempts made per tier, per chunk, to find a floor to seed a spawn group on. */
    public static final int SPAWN_FLOOR_ATTEMPTS = 12;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Bottom-up tier index for a block Y: 0 = Royal Depths ... 3 = Upper Galleries. */
    public static int tierIndex(int y) {
        return Mth.clamp((y - MIN_Y) / TIER_HEIGHT, 0, TIER_COUNT - 1);
    }

    /** Lowest block Y of a tier band. */
    public static int tierMinY(int tier) {
        return MIN_Y + tier * TIER_HEIGHT;
    }

    /** One past the highest block Y of a tier band. */
    public static int tierMaxY(int tier) {
        return MIN_Y + (tier + 1) * TIER_HEIGHT;
    }

    private ColonyGeneratorTunables() {
    }
}
