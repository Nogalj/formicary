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
    /** Solid Packed Soil cap at the top, apart from the M5 membrane patches below. */
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
    // Daylight Membrane exit patches (M5) -- the way out, embedded in the ceiling cap
    //
    // Deliberately a 2D field: a patch is a hole in the roof, and a roof is flat. It is
    // masked by "is the block directly under the cap actually air", so a patch only ever
    // appears where a player standing in the Upper Galleries can see it -- which is also
    // why the threshold has to be generous. Roughly two thirds of ceiling columns in this
    // dimension sit over solid fabric, so a patch that lands there is invisible and does
    // not count toward reachability. NoiseProbe's `membrane` section measures what these
    // two numbers actually produce (coverage, and the distance from an exposed ceiling
    // point to the nearest visible patch); do not change them without re-running it.
    // ------------------------------------------------------------------

    /**
     * Horizontal sampling scale of the patch field. Feature size is about {@code 1/scale}
     * blocks, so 0.035 gives roughly 29-block cells. Chosen over a coarser 0.022 because
     * the coarse field left blob-free regions with the nearest exit 110-135 blocks away
     * however the threshold was set; smaller, more numerous cells bring the worst case in
     * without making the patches themselves any bigger.
     */
    public static final double MEMBRANE_XZ_SCALE = 0.035;

    /**
     * A patch forms where the field is above this. Lower = more and bigger patches. The
     * field is single-octave Perlin, measured span about [-0.90, 0.91], so 0.30 keeps the
     * peaks only: 10-16% of columns, which after the visibility mask is 1.0-1.8% of the
     * ceiling. Measured distance from an exposed ceiling point to the nearest visible patch
     * (NoiseProbe, {@code -PprobeWhat=membrane}), median / p95 / max in blocks:
     * <pre>
     *   seed 1234567 : 24 /  47 /  56
     *   seed 42      : 22 /  61 /  86
     *   seed 987654321: 21 / 54 /  61
     * </pre>
     * against the spec's "roughly one patch reachable within ~40-60 blocks of any point".
     * 0.40 was rejected: it holds on two seeds but drifts to median 53 / max 102 on the
     * third.
     */
    public static final double MEMBRANE_THRESHOLD = 0.30;

    /**
     * How many layers of the ceiling cap a patch replaces, counting up from
     * {@link #CEILING_BOTTOM}. Two, so breaking the exposed layer does not delete the exit.
     */
    public static final int MEMBRANE_THICKNESS = 2;

    // ------------------------------------------------------------------
    // Arrival pocket (M5) -- where an ender pearl thrown at an anthill puts the player
    // ------------------------------------------------------------------

    /** Highest Y the arrival search will stand a player on. Leaves headroom under the cap. */
    public static final int ENTRY_SCAN_TOP = CEILING_BOTTOM - 2;

    /** Lowest Y the arrival search will accept -- still well inside the Upper Galleries. */
    public static final int ENTRY_SCAN_BOTTOM = MIN_Y + 3 * TIER_HEIGHT + 4;

    /**
     * The Y the arrival pocket prefers to be carved at when the scan finds no natural floor
     * at the anthill's XZ. A preference, not a rule: the carve searches down (then up) from
     * here for a level that already has solid ground beneath it.
     */
    public static final int ENTRY_CARVE_PREFERRED_Y = 168;

    /** Half-width of the carved pocket in X/Z; 2 gives a 5x5 footprint. */
    public static final int ENTRY_CARVE_RADIUS = 2;

    /** Air blocks carved above the pocket floor. */
    public static final int ENTRY_CARVE_HEIGHT = 4;

    // ------------------------------------------------------------------
    // The queen's throne chamber (M7) -- a rare domed room in the Royal Depths
    //
    // Rarity is a grid, like the connectivity shafts: one chamber per THRONE_SPACING
    // cell, so a committed explorer always finds one and most entry points have none.
    //
    // The chamber is deliberately NOT placed at a free XZ. It hangs off the nearest
    // connectivity ramp at a fixed offset, and its floor is set to the exact Y that
    // ramp's walkway reaches at the approach bearing -- which is what makes the
    // approach corridor meet the spine at a walkable height by construction rather
    // than by luck. See ColonyNoise#throneForCell for the arithmetic.
    // ------------------------------------------------------------------

    /** One throne chamber per this many blocks on each axis. */
    public static final int THRONE_SPACING = 224;

    /** Interior radius of the chamber: 14 gives a 28-block-wide room. */
    public static final double THRONE_RADIUS = 14.0;

    /** Height of the vertical wall before the dome starts, above the floor. */
    public static final int THRONE_WALL_HEIGHT = 6;

    /** Height of the dome above the wall. Interior clearance is the sum: 13. */
    public static final int THRONE_DOME_HEIGHT = 7;

    /** How thick the forced-solid shell around the interior is. */
    public static final double THRONE_SHELL_THICKNESS = 2.0;

    /** Radius of the raised dais the queen is seated on. */
    public static final double THRONE_DAIS_RADIUS = 4.0;

    /** Layers of dais above the chamber floor. The queen stands one block above it. */
    public static final int THRONE_DAIS_HEIGHT = 2;

    /**
     * Radius of the one-block step ring around the dais.
     *
     * <p>Not decoration: without it the dais is a two-block ledge, which nothing can climb.
     * {@code NoiseProbe -PprobeWhat=throne} caught exactly that -- the room joined the ramp
     * but the plinth itself was unreachable, so the queen could step off it and never get
     * back, and her home-restriction goal would have spent the fight pathing at a wall.
     */
    public static final double THRONE_DAIS_STEP_RADIUS = 7.0;

    /**
     * Distance from the ramp axis to the chamber's centre. Must exceed
     * {@code THRONE_RADIUS + THRONE_SHELL_THICKNESS + SHAFT_MAX_REACH} so the helicoid
     * never intrudes into the room itself -- at 34 the nearest the ramp's carve gets to
     * the interior is 34 - 11.6 - 14 = 8 blocks of solid fabric.
     */
    public static final double THRONE_APPROACH_DISTANCE = 34.0;

    /** Half-width of the approach corridor; 1.5 gives a 3-block-wide passage. */
    public static final double THRONE_CORRIDOR_HALF_WIDTH = 1.5;

    /** Air blocks carved above the corridor floor. */
    public static final int THRONE_CORRIDOR_HEIGHT = 4;

    /** Where the corridor starts, measured from the ramp axis outward. */
    public static final double THRONE_CORRIDOR_START = 3.0;

    /** Where it stops -- just inside the chamber's shell, which is already air. */
    public static final double THRONE_CORRIDOR_END =
            THRONE_APPROACH_DISTANCE - THRONE_RADIUS + 2.0;

    /**
     * Lowest floor the chamber will sit at. The ramp turn chosen is the first one at or
     * above this, and the ramp descends {@code 2*PI / RAMP_RADIANS_PER_BLOCK} = 24 blocks
     * per turn, so the floor always lands in {@code [8, 33)} and the interior
     * ({@code floor + 13}) plus its shell stays inside the Royal Depths band (y &lt; 48).
     */
    public static final int THRONE_FLOOR_MIN_Y = 8;

    /**
     * Widest a chamber's carve can reach from its centre; used to prune the per-column
     * search. The corridor, not the dome, is what sets it: its far end sits
     * {@code THRONE_APPROACH_DISTANCE - THRONE_CORRIDOR_START} from the centre.
     */
    public static final double THRONE_MAX_REACH = Math.max(
            THRONE_RADIUS + THRONE_SHELL_THICKNESS,
            THRONE_APPROACH_DISTANCE - THRONE_CORRIDOR_START + THRONE_CORRIDOR_HALF_WIDTH) + 1.0;

    // --- decoration inside the chamber, replacing the per-tier chances above ---
    // This is the Royal Comb's natural home: the non-boss source of Royal Jelly, and
    // the reason a throne room is worth walking into even before the fight.

    public static final double THRONE_ROYAL_COMB_CHANCE = 0.090;
    public static final double THRONE_BROOD_COMB_CHANCE = 0.060;
    public static final double THRONE_RESIN_WEEP_CHANCE = 0.050;
    public static final double THRONE_RESIN_BLOCK_CHANCE = 0.040;
    public static final double THRONE_EGG_CLUSTER_CHANCE = 0.060;

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
