package com.nogal.formicary.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

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

    /**
     * Hardened Soil appears where the accent field is above this, per tier.
     *
     * <p>Tier 0 is the odd one out and deliberately so (play-test round 1: "the Royal Depths
     * did not read as a different place from the Nurseries"). The accent field is
     * single-octave Perlin measured at {@code P(v > t)} = 36% at 0.10, 24% at 0.20, 18% at
     * 0.26, so the old 0.10 made the Royal Depths 36% Hardened Soil against 64% Deep Loam --
     * i.e. mostly the same Deep Loam that IS the Nurseries' fabric (89% of it). Pushing the
     * threshold to a negative value inverts that: {@code P(v > -0.26) = 1 - P(v > 0.26)} is
     * about 82%, so the deep tier becomes overwhelmingly packed, hardened soil and the walk
     * down from the Nurseries changes colour in one step. The remaining 18% is Deep Loam
     * mottling and the resin veins below.
     */
    public static final double[] HARDENED_ACCENT_THRESHOLD_BY_TIER = {-0.26, 0.34, 0.36, 0.32};
    /**
     * Royal Depths only: buried Resin Block veins where the accent field is below this.
     * Raised from -0.42 to -0.46 alongside the threshold above -- the veins now have to
     * carry the tier's amber accent on their own (they used to sit inside a large field of
     * Deep Loam), and 4.3% of the tier's solid volume is enough to read as veined without
     * competing with the hardened fabric.
     */
    public static final double ROYAL_RESIN_THRESHOLD = -0.46;

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
    /** Royal Depths' sparse amber accents: Resin Block exposed in a wall. */
    public static final double[] RESIN_BLOCK_CHANCE_BY_TIER = {0.020, 0.000, 0.000, 0.000};

    // --- comb: rarer overall, but in patches rather than speckle ---------------
    //
    // Play-test round 1: comb "looked like static". It was a flat per-block chance on every
    // roomy surface (0.170 in the Nurseries), which is exactly a speckle generator -- 17% of
    // a wall's blocks scattered at random reads as noise, never as a growth. Comb is now
    // gated by its own low-frequency field FIRST: outside a patch no comb can appear at all,
    // inside one the per-block chance is high enough that the patch is a solid blob with a
    // ragged edge. Total comb comes out lower than before (see NoiseProbe -PprobeWhat=comb),
    // which is the point -- fewer, bigger, findable.

    /**
     * Sampling scale of the comb-patch field. Feature size is about {@code 1/scale} blocks,
     * so 0.18 gives roughly 5-6 block cells. This number, not the threshold, is what sets
     * patch SIZE, and it was chosen by measurement: at 0.11 the Nurseries' patches came out
     * averaging 13 blocks with a 133-block monster in a 96x96 sample -- the blobs had begun
     * to percolate into sheets. See {@code NoiseProbe -PprobeWhat=comb}.
     */
    public static final double COMB_PATCH_XZ_SCALE = 0.18;
    /** Slightly faster in Y so a patch hugs a wall instead of running floor-to-ceiling. */
    public static final double COMB_PATCH_Y_SCALE = 0.21;

    /**
     * Comb may only appear where the patch field is above this. Measured on the identical
     * single-octave field: {@code P(v > 0.30) = 14.0%}, {@code P(v > 0.55) = 1.5%}. 9.00 is
     * "never" for the two tiers that grow no comb.
     */
    public static final double[] COMB_PATCH_THRESHOLD_BY_TIER = {0.55, 0.30, 9.00, 9.00};

    /**
     * Brood Comb density <em>inside</em> a patch -- no longer a global per-block chance. It
     * is high because a sparse patch is just speckle with extra steps: what makes a patch
     * read as one thing is that nearly every eligible block in it is comb, with the ragged
     * edge coming from the field's own boundary rather than from holes punched through the
     * middle. Measured totals over roomy surfaces (probe, 96x96, seed 1234567): Nurseries
     * 142 per 1000 against the old flat 178, in patches averaging 7.8 blocks; Royal Depths
     * 10 per 1000 against the old 14.
     */
    public static final double[] BROOD_COMB_CHANCE_BY_TIER = {0.820, 0.900, 0.000, 0.000};
    /** Royal Comb -- rare, and only inside a comb patch, where brood comb already belongs. */
    public static final double[] ROYAL_COMB_CHANCE_BY_TIER = {0.030, 0.045, 0.000, 0.000};

    // ------------------------------------------------------------------
    // Daylight Membrane exits (M5, retuned in Ep2) -- the way out, embedded in the cap
    //
    // The rule is now a one-liner with no tunable in it: a ceiling column with air under
    // it IS a membrane. What used to ration the exits was a 2D patch field thresholded at
    // its peaks; Ep2 retired that threshold outright, because the scarcity it produced was
    // invisible from the ground (see ColonyNoise#isDaylightMembrane for the reasoning) and
    // the visibility mask -- "is the block directly under the cap actually air" -- already
    // rations hard on its own: only ~12% of ceiling columns in this dimension have air
    // beneath them at all. NoiseProbe's `membrane` section now asserts the invariant
    // (exposed ceiling column => membrane, zero violations) rather than measuring a
    // distance-to-nearest-exit; run it after anything that changes the carve.
    // ------------------------------------------------------------------

    /**
     * Horizontal sampling scale of the patch field.
     *
     * <p>No longer consumed by generation: with {@code MEMBRANE_THRESHOLD} retired in Ep2
     * the field gates nothing, and it survives only as a {@link NoiseProbe} readout
     * ({@code ColonyNoise#probeMembrane}) in case a future pass wants to bring back a
     * rationed variant. Kept at the measured value: feature size is about {@code 1/scale}
     * blocks, so 0.035 gives roughly 29-block cells -- a coarser 0.022 was rejected in M5
     * because it left blob-free regions 110-135 blocks from an exit at every threshold.
     */
    public static final double MEMBRANE_XZ_SCALE = 0.035;

    /**
     * How many layers of the ceiling cap a patch replaces, counting up from
     * {@link #CEILING_BOTTOM}. Two, so breaking the exposed layer does not delete the exit.
     */
    public static final int MEMBRANE_THICKNESS = 2;

    // ------------------------------------------------------------------
    // Arrival pocket (M5, retuned in Ep2) -- where a pearl thrown at an anthill lands you
    //
    // Ep2 pulls the whole band up against the ceiling cap. The pocket is no longer just
    // "somewhere legal in the Upper Galleries": it is the near side of a guaranteed exit,
    // with a membrane punched through the cap directly above it (AnthillPortal
    // #openMembraneColumn). A pocket 30 blocks below the roof would put that membrane out
    // of pearl range behind whatever the carve happened to leave in between, so the band
    // and the punch are one design, not two.
    // ------------------------------------------------------------------

    /**
     * How far below {@link #CEILING_BOTTOM} an arrival pocket floor may sit.
     *
     * <p>12 blocks: far enough that the search still has a real choice of natural floors in
     * a tier of narrow tunnels, close enough that the chimney up to the membrane is a
     * couple of pearl-lengths and stays in frame when the player looks up on arrival.
     */
    public static final int ENTRY_MAX_DROP_BELOW_CAP = 12;

    /** Highest Y the arrival search will stand a player on. Leaves headroom under the cap. */
    public static final int ENTRY_SCAN_TOP = CEILING_BOTTOM - 2;

    /** Lowest Y the arrival search will accept -- {@link #ENTRY_MAX_DROP_BELOW_CAP} down. */
    public static final int ENTRY_SCAN_BOTTOM = CEILING_BOTTOM - ENTRY_MAX_DROP_BELOW_CAP;

    /**
     * Whether a pocket floor at {@code floorY} is close enough under the cap for the
     * membrane punched above it to be the exit the arrival is supposed to advertise.
     *
     * <p>The one owner of "under the cap": {@link AnthillPortal} accepts a natural floor
     * only if this says so, and its carve fallback is bounded by the same two constants.
     * It lives here, in the registry-free class, for the reason {@link #rollCount} does --
     * a headless checker (and any GameTest that must not load the dimension) can call it,
     * whereas merely touching {@code AnthillPortal} drags in blocks and levels.
     */
    public static boolean entryPocketUnderCap(int floorY) {
        return floorY >= ENTRY_SCAN_BOTTOM && floorY <= ENTRY_SCAN_TOP;
    }

    /**
     * The Y the arrival pocket prefers to be carved at when the scan finds no natural floor
     * at the anthill's XZ. A preference, not a rule: the carve searches down (then up) from
     * here for a level that already has solid ground beneath it. 180 is one block under the
     * highest floor the band can hold a whole pocket at ({@code ENTRY_SCAN_TOP -
     * ENTRY_CARVE_HEIGHT + 1} = 181), which keeps the fallback search a two-sided one:
     * mostly downward, with a rung above it still available.
     */
    public static final int ENTRY_CARVE_PREFERRED_Y = 180;

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
    // Nursery chambers (play-test round 1) -- small brood rooms in the Nurseries tier
    //
    // Same construction as the throne chamber above, and for the same reason: a room hung
    // off a connectivity ramp at a fixed offset, with its floor set to the exact Y that
    // ramp's walkway reaches at the approach bearing, is reachable BY CONSTRUCTION. A room
    // dropped at a free XZ would make "can the player get in?" a property of the noise --
    // acceptable for a decorative pocket, not for the only place larvae exist.
    //
    // Where they differ is count and size: one per 96-block cell against the throne's 224
    // (5.4x as many) and 12 blocks across against its 28.
    //
    // 96 is the smallest cell that CANNOT produce two overlapping chambers, and that bound
    // is why it is not smaller. A chamber's centre lands within
    // {@code SHAFT_SPACING/2 + SHAFT_JITTER/2 + NURSERY_APPROACH_DISTANCE} = 56 blocks of
    // its cell centre and no closer than 8 blocks the other way, so two chambers in
    // neighbouring cells are at least {@code 96 - 56 - 8 = 32} blocks apart against the 16
    // they would need to touch. A 48-block cell was tried first and measurably failed: the
    // probe found two chambers 5.8 blocks apart, one of whose shells sealed the other's
    // corridor, leaving its brood in a solid bubble (0 of 67 floor blocks reachable). Two
    // rooms at different floor heights merging into one is not a cosmetic problem -- it is
    // an unannounced drop and a sealed room, and no resolution rule inside `nurseryState`
    // fixes both.
    // ------------------------------------------------------------------

    /** One nursery chamber per this many blocks on each axis. */
    public static final int NURSERY_SPACING = 96;

    /** Interior radius: 6 gives a 12-block-wide room, against the throne's 28. */
    public static final double NURSERY_RADIUS = 6.0;

    /** Height of the vertical wall before the dome starts, above the floor. */
    public static final int NURSERY_WALL_HEIGHT = 3;

    /** Height of the dome above the wall. Interior clearance is the sum: 7. */
    public static final int NURSERY_DOME_HEIGHT = 4;

    /** How thick the forced-solid shell around the interior is. */
    public static final double NURSERY_SHELL_THICKNESS = 2.0;

    /**
     * Distance from the ramp axis to the chamber's centre. Must exceed
     * {@code NURSERY_RADIUS + NURSERY_SHELL_THICKNESS + SHAFT_MAX_REACH} = 21.1 so the
     * helicoid never intrudes into the room; at 24 the nearest the ramp's carve gets to the
     * shell is about 5 blocks of solid fabric.
     */
    public static final double NURSERY_APPROACH_DISTANCE = 24.0;

    /** Half-width of the approach corridor; 1.0 gives a 3-block-wide passage. */
    public static final double NURSERY_CORRIDOR_HALF_WIDTH = 1.0;

    /** Air blocks carved above the corridor floor. */
    public static final int NURSERY_CORRIDOR_HEIGHT = 3;

    /** Where the corridor starts, measured from the ramp axis outward. */
    public static final double NURSERY_CORRIDOR_START = 3.0;

    /** Where it stops -- just inside the chamber's shell, which is already air. */
    public static final double NURSERY_CORRIDOR_END =
            NURSERY_APPROACH_DISTANCE - NURSERY_RADIUS + 2.0;

    /**
     * Lowest floor the chamber will sit at. The ramp turn chosen is the first one at or
     * above this and the ramp descends 24 blocks per turn, so the floor lands in
     * {@code [54, 78)} and the interior ({@code floor + 7}) plus its shell stays inside the
     * Nurseries band ({@code y < 96}).
     *
     * <p>The {@code + LANDING_HEIGHT} is load-bearing, not padding. A landing chamber is
     * carved around every ramp axis at each tier boundary -- radius 11, {@code y} in
     * {@code [48, 54)} for this one -- and {@link ColonyNoise#shaftState} outranks every
     * chamber, so a corridor whose forced-solid walkway fell inside that band had the floor
     * cut out from under it for the 1-2 blocks where the landing disc overhangs the ramp's
     * annulus. The probe caught it as a chamber with 113 standable floor blocks and 0 of
     * them reachable; the room was fine, the doorway opened onto a five-block drop into the
     * landing.
     */
    public static final int NURSERY_FLOOR_MIN_Y = MIN_Y + TIER_HEIGHT + LANDING_HEIGHT;

    /**
     * Widest a chamber's carve can reach from its centre; used to prune the per-column
     * search. As with the throne it is the corridor, not the dome, that sets it.
     */
    public static final double NURSERY_MAX_REACH = Math.max(
            NURSERY_RADIUS + NURSERY_SHELL_THICKNESS,
            NURSERY_APPROACH_DISTANCE - NURSERY_CORRIDOR_START + NURSERY_CORRIDOR_HALF_WIDTH) + 1.0;

    // --- decoration inside the chamber, replacing the per-tier chances above ---
    //
    // Egg Cluster has NO per-tier ambient chance any more (the old
    // EGG_CLUSTER_CHANCE_BY_TIER = {0, 0.060, 0, 0} is gone): eggs are brood, and brood
    // belongs in a brood room. They now appear only here and in the throne chamber, which
    // is what makes finding one mean something.

    public static final double NURSERY_BROOD_COMB_CHANCE = 0.320;
    public static final double NURSERY_ROYAL_COMB_CHANCE = 0.012;
    public static final double NURSERY_EGG_CLUSTER_CHANCE = 0.140;
    public static final double NURSERY_RESIN_WEEP_CHANCE = 0.020;

    /** Larvae seeded on a nursery chamber's floor when its chunk generates. */
    public static final int NURSERY_LARVAE_MIN = 2;
    public static final int NURSERY_LARVAE_MAX = 4;

    // ------------------------------------------------------------------
    // Fungus Garden chambers (Ep2 D2) -- the colony's farm rooms, cloned end to end from
    // the nursery chamber above: a room hung off a connectivity ramp at a fixed offset,
    // floor set to the exact Y that ramp's walkway reaches at the approach bearing. Tier
    // band = Fungal Gardens (tier 2), one per 96-block cell like the nursery.
    //
    // Wall/dome/shell/corridor numbers are cloned VERBATIM from the nursery's own
    // constants -- only what structurally depends on the bigger radius is recomputed:
    //
    //   GARDEN_APPROACH_DISTANCE must satisfy two constraints at once. The lower bound is
    //   the same one NURSERY_APPROACH_DISTANCE's javadoc derives: it must exceed
    //   {@code GARDEN_RADIUS + GARDEN_SHELL_THICKNESS + SHAFT_MAX_REACH} = 8 + 2 + 13.1 =
    //   23.1, so the helicoid never intrudes into the room. The upper bound comes from
    //   NURSERY_SPACING's own derivation re-run for GARDEN_SPACING = 96: a chamber's centre
    //   lands within {@code SHAFT_SPACING/2 + SHAFT_JITTER/2 + APPROACH_DISTANCE} of its
    //   cell centre, two neighbouring shaft axes are at least
    //   {@code GARDEN_SPACING - SHAFT_JITTER} = 80 apart (same arithmetic the nursery
    //   comment uses), and the worst case (both chambers' approach directions pointing at
    //   each other) puts two neighbouring garden centres
    //   {@code 80 - 2*APPROACH_DISTANCE} apart -- which has to clear the required 32-block
    //   minimum (matching the nursery's own guarantee), i.e. {@code APPROACH_DISTANCE <=
    //   24}. The two bounds (23.1, 24] leave almost no room: 24.0 is the only reasonable
    //   value, and it happens to equal NURSERY_APPROACH_DISTANCE even though
    //   GARDEN_RADIUS is bigger. {@link com.nogal.formicary.worldgen.NoiseProbe}'s garden
    //   section measures the real minimum and asserts it, rather than trusting this algebra
    //   alone -- the nursery section of this file makes the same "measure, do not guess"
    //   argument about a rejected 48-block cell.
    // ------------------------------------------------------------------

    /** One garden chamber per this many blocks on each axis. */
    public static final int GARDEN_SPACING = 96;

    /** Interior radius: 8 gives a 16-block-wide room, against the nursery's 12. */
    public static final double GARDEN_RADIUS = 8.0;

    /** Cloned verbatim from {@link #NURSERY_WALL_HEIGHT}. */
    public static final int GARDEN_WALL_HEIGHT = NURSERY_WALL_HEIGHT;

    /** Cloned verbatim from {@link #NURSERY_DOME_HEIGHT}. Interior clearance is the sum: 7. */
    public static final int GARDEN_DOME_HEIGHT = NURSERY_DOME_HEIGHT;

    /** Cloned verbatim from {@link #NURSERY_SHELL_THICKNESS}. */
    public static final double GARDEN_SHELL_THICKNESS = NURSERY_SHELL_THICKNESS;

    /** See the section javadoc above for why 24.0 is the only value that satisfies both bounds. */
    public static final double GARDEN_APPROACH_DISTANCE = 24.0;

    /** Cloned verbatim from {@link #NURSERY_CORRIDOR_HALF_WIDTH}; 3-block-wide passage. */
    public static final double GARDEN_CORRIDOR_HALF_WIDTH = NURSERY_CORRIDOR_HALF_WIDTH;

    /** Cloned verbatim from {@link #NURSERY_CORRIDOR_HEIGHT}. */
    public static final int GARDEN_CORRIDOR_HEIGHT = NURSERY_CORRIDOR_HEIGHT;

    /** Cloned verbatim from {@link #NURSERY_CORRIDOR_START}. */
    public static final double GARDEN_CORRIDOR_START = NURSERY_CORRIDOR_START;

    /** Where it stops -- just inside the chamber's shell, which is already air. */
    public static final double GARDEN_CORRIDOR_END = GARDEN_APPROACH_DISTANCE - GARDEN_RADIUS + 2.0;

    /**
     * Lowest floor the chamber will sit at -- tier 2 (Fungal Gardens) rather than the
     * nursery's tier 1, same landing-clearance reasoning as {@link #NURSERY_FLOOR_MIN_Y}
     * (see its javadoc): the {@code + LANDING_HEIGHT} keeps a corridor from opening onto
     * the landing disc's drop where it overhangs the ramp's annulus at the tier boundary.
     * The ramp turn chosen is the first one at or above this, and the ramp descends 24
     * blocks per turn, so the floor lands in {@code [102, 126)} -- the interior
     * ({@code floor + 7}) plus its shell stays inside the Fungal Gardens band
     * ({@code y < 144}) with 8+ blocks to spare even at the top of that range.
     */
    public static final int GARDEN_FLOOR_MIN_Y = MIN_Y + 2 * TIER_HEIGHT + LANDING_HEIGHT;

    /**
     * Widest a chamber's carve can reach from its centre; used to prune the per-column
     * search. As with the nursery it is the corridor, not the dome, that sets it.
     */
    public static final double GARDEN_MAX_REACH = Math.max(
            GARDEN_RADIUS + GARDEN_SHELL_THICKNESS,
            GARDEN_APPROACH_DISTANCE - GARDEN_CORRIDOR_START + GARDEN_CORRIDOR_HALF_WIDTH) + 1.0;

    // --- floor decoration inside the chamber (ColonyChunkGenerator#decorateGardenFloor) ---
    //
    // Three mutually exclusive floor overlays, rarer checked first (the same convention
    // decorateSurface uses: royal comb before brood comb, resin weep before resin block).
    // The crop is planted at FungalSporeCropBlock.MAX_AGE unconditionally -- see
    // ColonyChunkGenerator's javadoc on the planting call for why: LIGHT_BY_AGE only
    // reaches the crop's own canSurvive bar (8) at that one age, and the dimension has no
    // skylight to make up the difference at any other age.

    public static final double GARDEN_SPORE_CROP_CHANCE = 0.12;
    public static final double GARDEN_FUNGAL_BLOOM_CHANCE = 0.15;
    public static final double GARDEN_FUNGAL_CARPET_CHANCE = 0.50;

    // ------------------------------------------------------------------
    // Mob spawning at chunk generation (see ColonyChunkGenerator#spawnOriginalMobs)
    //
    // Play-test round 1: "the colony felt too empty". Two changes, one cause each.
    //
    // DENSITY. The old loop drew groups from the biome's weighted spawn list with vanilla's
    // geometric "while (random < creatureProbability)" gate, which at the biomes' 0.10-0.20
    // probabilities means 0.11-0.25 groups per chunk per tier. NoiseProbe simulates both
    // schemes against the same air field and the same floor-search loss: the old one placed
    // 1.66 ants per chunk across the whole four-tier column, these numbers place 3.95 --
    // 2.38x, inside the 2-3x the play-test asked for. Stating them as clusters per chunk
    // rather than as a probability whose expectation is p/(1-p) is the point of the rewrite:
    // the old knob could not be reasoned about without doing that algebra first.
    //
    // COMPOSITION. A vanilla spawn group is one SpawnerData, hence one caste, so the colony
    // arrived as pure-worker or pure-soldier knots. A cluster here is mixed by construction
    // at 3-4 workers to 1 soldier -- a foraging party with an escort, which is what an ant
    // colony looks like. That is why generation-time seeding no longer reads the biome
    // spawn lists at all: a weighted list can only express a per-draw probability, never
    // "these castes, together, in this ratio". The biome lists stay for runtime respawn
    // (see ColonyChunkGenerator#spawnOriginalMobs for what that is actually worth here).
    // ------------------------------------------------------------------

    /**
     * Expected number of mixed ant clusters seeded per chunk, per tier. Fractional: the
     * whole part is always placed and the remainder is a probability.
     */
    public static final double[] SPAWN_CLUSTERS_PER_CHUNK_BY_TIER = {0.15, 0.32, 0.26, 0.22};

    /** Workers per cluster, inclusive range. Tier 0 (Royal Depths) fields none. */
    public static final int[] CLUSTER_WORKERS_MIN_BY_TIER = {0, 3, 3, 3};
    public static final int[] CLUSTER_WORKERS_MAX_BY_TIER = {0, 4, 4, 4};

    /**
     * Soldiers per cluster, inclusive range. One per party keeps the ratio at the 3:1 -- 4:1
     * the brief asks for; the Royal Depths stay soldier-only, which M4a chose deliberately
     * (a boss-guarding tier with no foragers in it) and nothing in this round disturbs.
     */
    public static final int[] CLUSTER_SOLDIERS_MIN_BY_TIER = {2, 1, 1, 1};
    public static final int[] CLUSTER_SOLDIERS_MAX_BY_TIER = {4, 1, 1, 1};

    /** Attempts made per cluster member, per chunk, to find a floor to stand it on. */
    public static final int SPAWN_FLOOR_ATTEMPTS = 12;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * A fractional count made whole: the integer part always, the remainder as a chance.
     *
     * <p>Lives here rather than in {@link ColonyChunkGenerator} so {@link NoiseProbe} can
     * measure the spawn density with the arithmetic under test rather than a re-derivation
     * of it -- the probe runs without the game bootstrapped, and merely <em>touching</em>
     * {@code ColonyChunkGenerator} loads {@code ChunkGenerator}, whose static initialiser
     * reaches {@code BuiltInRegistries} and throws "Not bootstrapped".
     */
    public static int rollCount(double expected, RandomSource random) {
        int whole = (int) expected;
        return random.nextDouble() < expected - whole ? whole + 1 : whole;
    }

    /** An inclusive integer range roll; {@code min == max} costs no random draw. */
    public static int between(int min, int max, RandomSource random) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

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
