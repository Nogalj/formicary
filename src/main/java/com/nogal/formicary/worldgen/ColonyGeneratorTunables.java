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
 *   2 = Fungal Gardens   y  96 - 143   (the top tier; its last 6 blocks are the cap)
 * </pre>
 * The player enters at the top and descends, so the array order reads "deepest first".
 *
 * <h2>Why three tiers of 48 rather than four of 32 (play-test round 2)</h2>
 * The round-2 review asked for a shallower dimension -- "too deep to traverse". The obvious
 * dial, {@link #TIER_HEIGHT} 48 -&gt; 32, is <b>arithmetically impossible</b> and was measured
 * to be so before this change landed. A chamber's floor is the first ramp turn at or above
 * its tier's floor minimum, so a band has to hold three things end to end:
 * <pre>
 *   TIER_HEIGHT  &gt;  LANDING_INTERIOR_HEIGHT + floor(RAMP_PERIOD) + tallest room's shell top
 *                =  8                       + 24                 + 15  (the throne)
 *                =  47      i.e. TIER_HEIGHT &gt;= 48
 * </pre>
 * 48 is therefore the exact floor the current ramp/landing/boss-arena arithmetic admits, and
 * the shipped value was already sitting on it with one block to spare. At 32 the band has
 * {@code 32 - 8 - 24 = 0} blocks left for a room before one exists at all; {@link NoiseProbe}
 * measured 101 of 289 nursery chambers crossing their boundary, and tier 2's rooms punching
 * through the ceiling cap.
 *
 * <p>Steepening the coil to make 32 fit would need {@link #RAMP_RADIANS_PER_BLOCK} 0.26 -&gt;
 * 0.698, which drops the walked grade from 0.51 to 0.19 blocks of descent per block walked --
 * i.e. it would <em>lengthen</em> the on-foot descent, worsening the exact complaint that
 * motivated the change. Dropping a tier instead leaves every intra-tier bound byte-identical
 * (the arithmetic above is untouched) and shortens a full descent by 25% at the same grade.
 */
public final class ColonyGeneratorTunables {

    // ------------------------------------------------------------------
    // Vertical layout
    // ------------------------------------------------------------------

    /** Dimension floor. Must be a multiple of 16 (DimensionType codec enforces it). */
    public static final int MIN_Y = 0;
    /**
     * Dimension height. Must be a multiple of 16. Three 48-block tiers.
     *
     * <p>Play-test round 2 took this from 192 to 144 by dropping a tier rather than by
     * shortening one -- see the class javadoc for the measurement that ruled the other way
     * out. The Upper Galleries band is the one that went: the ceiling cap and its daylight
     * membranes now sit directly on top of the Fungal Gardens.
     */
    public static final int HEIGHT = 144;
    /**
     * Blocks per tier band. {@code HEIGHT / TIER_COUNT}.
     *
     * <p><b>48 is a hard floor, not a preference.</b> See the class javadoc: it is
     * {@code LANDING_INTERIOR_HEIGHT + floor(RAMP_PERIOD) + the throne's shell top} rounded
     * up, and anything smaller cannot hold a chamber whose floor is chosen by ramp turn.
     */
    public static final int TIER_HEIGHT = 48;
    public static final int TIER_COUNT = 3;

    /** Solid Hardened Soil cap at the bottom -- never carved, so there is no exposed void. */
    public static final int FLOOR_THICKNESS = 5;
    /** Solid Packed Soil cap at the top, apart from the M5 membrane patches below. */
    public static final int CEILING_THICKNESS = 6;

    /** Lowest carvable Y. */
    public static final int FLOOR_TOP = MIN_Y + FLOOR_THICKNESS;
    /** First Y of the ceiling cap; carving stops below this. */
    public static final int CEILING_BOTTOM = MIN_Y + HEIGHT - CEILING_THICKNESS;

    /**
     * Where {@code getSpawnHeight} points -- inside the top tier. M5 owns real entry.
     *
     * <p>Round 2 re-derived it rather than re-typing it: 128 sits the same 32 blocks above its
     * tier's floor ({@code tierMinY(2)} = 96) and the same 10 blocks below
     * {@link #CEILING_BOTTOM} that 176 did inside the retired Upper Galleries.
     */
    public static final int SPAWN_HEIGHT = 128;

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
     * scales above is about a 3-block bore. Spec: widest in the Fungal Gardens.
     *
     * <p>The Upper Galleries' own 0.070 -- the narrowest bore in the dimension -- went with
     * that tier in round 2. The player now arrives into the Fungal Gardens' 0.115 instead,
     * which is the widest, so first contact reads as open gallery rather than as a crawl.
     */
    public static final double[] TUNNEL_HALF_WIDTH_BY_TIER = {0.085, 0.095, 0.115};

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
    public static final double[] CHAMBER_SMALL_THRESHOLD_BY_TIER = {0.50, 0.32, 0.36};
    /** Carve where the large-blob field exceeds this. Lower = more/bigger cathedral rooms. */
    public static final double[] CHAMBER_LARGE_THRESHOLD_BY_TIER = {0.34, 0.55, 0.50};

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
     *
     * <p><b>Rounded in play-test round 2</b> (it used to be a flat cylinder: one radius at
     * every height, a square rim top and bottom). The three parts of the new silhouette each
     * have a constraint behind them:
     * <ul>
     *   <li>The <b>floor disc stays flat</b> at the full radius. It is where the ramp's
     *       walkway crosses the landing, so doming or dishing it would put a step in the
     *       spine -- the one thing this whole section exists not to do.</li>
     *   <li>The <b>ceiling domes</b>, rising {@link #LANDING_DOME_RISE} at the centre. That
     *       is what makes the landing read as a room rather than as a wide bit of corridor.
     *       It is also the constant the chamber floor minimums are derived from -- see
     *       {@link #NURSERY_FLOOR_MIN_Y} -- because a taller landing reaches further up into
     *       the tier above its boundary.</li>
     *   <li>The <b>wall tapers</b>: {@link #LANDING_WALL_HEIGHT} of straight wall, then the
     *       dome's curve above it, and one block of fillet at the very bottom so the rim
     *       meets the floor on a curve. The taper is what stops the shape reading as a
     *       cylinder with a lid.</li>
     * </ul>
     * The rim keeps 5 air blocks of headroom and the centre 8, so headroom everywhere the
     * ramp's annulus crosses the disc stays comfortably above {@link #RAMP_AIR_HEIGHT}.
     */
    public static final double LANDING_RADIUS = 11.0;
    /** Air blocks above the landing's floor disc at its RIM -- the shortest the room gets. */
    public static final int LANDING_HEIGHT = 6;
    /** How much taller the dome's centre is than {@link #LANDING_HEIGHT}. */
    public static final int LANDING_DOME_RISE = 2;
    /** Straight wall before the dome's curve starts, in air blocks above the floor disc. */
    public static final int LANDING_WALL_HEIGHT = 3;
    /**
     * The tallest the landing ever gets, at its centre. This -- not {@link #LANDING_HEIGHT}
     * -- is what every chamber floor minimum has to clear, since {@code shaftState} outranks
     * every chamber and a corridor whose walkway fell inside the dome would have its floor
     * cut out from under it.
     */
    public static final int LANDING_INTERIOR_HEIGHT = LANDING_HEIGHT + LANDING_DOME_RISE;

    /** Widest a shaft's carve can reach from its axis; used to prune the per-column search. */
    public static final double SHAFT_MAX_REACH =
            Math.max(RAMP_CENTER_RADIUS + RAMP_HALF_WIDTH + WALL_JITTER_AMOUNT, LANDING_RADIUS + WALL_JITTER_AMOUNT)
                    + 1.0;

    // ------------------------------------------------------------------
    // Chamber placement, shared by every room that hangs off a ramp
    //
    // Two things every chamber kind needs and none of them owns: how low its floor may sit
    // inside a given tier, and where around its anchor ramp it is allowed to hang.
    // ------------------------------------------------------------------

    /**
     * The lowest floor a chamber may take in each tier, indexed by {@link #tierIndex(int)}.
     *
     * <p>One formula for all four bands: the tier's own floor plus
     * {@link #LANDING_INTERIOR_HEIGHT}. The landing-clearance term is the load-bearing half
     * and it is derived rather than padded -- a landing chamber is carved around every ramp
     * axis at each tier boundary, {@link ColonyNoise#shaftState} outranks every chamber, and a
     * corridor whose forced-solid walkway fell inside that landing has its floor cut out from
     * under it for the blocks where the landing disc overhangs the ramp's annulus. The probe
     * originally caught exactly that as a room with 113 standable floor blocks and 0 of them
     * reachable: the room was fine, the doorway opened onto a five-block drop.
     *
     * <p>Tier 0 is in the array for completeness and comes out at the value
     * {@link #THRONE_FLOOR_MIN_Y} has always had by hand. There is no landing at
     * {@code y = MIN_Y} (the bands loop in {@code shaftState} starts at 1), so what the term
     * buys down there is clearance above {@link #FLOOR_TOP} instead: a floor at 8 puts the
     * shell's underside at 6, one block above the uncarvable floor cap. Play-test round 2
     * gave the tier its second tenant -- a larder may now pick any tier -- so the number stops
     * being the throne's private constant and becomes the band's.
     *
     * <p><b>The fit, written out</b> (round 2 -- this is the arithmetic that ruled out a
     * 32-block tier, so it belongs next to the constant rather than in a commit message). A
     * chamber's floor is the first ramp turn at or above the value here, and the ramp descends
     * {@link #RAMP_RADIANS_PER_BLOCK}'s {@code 2*PI/0.26} = 24.17 blocks per turn, so a floor
     * lands anywhere in {@code [min, min + 24]}. Adding the room's own shell top:
     * <pre>
     *   throne   floor in [ 8,  32], + WALL 6 + DOME 7 + shell 2 = 15  -&gt; top &lt;=  47  &lt; 48
     *   nursery  floor in [56,  80], + WALL 3 + DOME 4 + shell 2 =  9  -&gt; top &lt;=  89  &lt; 96
     *   garden   floor in [104,128], + WALL 3 + DOME 4 + shell 2 =  9  -&gt; top &lt;= 137  &lt; 144
     * </pre>
     * The throne is the binding case at one block of margin, which is what makes
     * {@link #TIER_HEIGHT} = 48 a floor rather than a choice. Tier 2 has a second, tighter
     * ceiling since round 2 made it the top band: its highest shell block at 137 sits one
     * below {@link #CEILING_BOTTOM} = 138, so a garden or larder never intrudes into the cap.
     * {@link NoiseProbe} asserts all three bands (the throne's was added in round 2 -- it was
     * the one kind whose containment nothing checked).
     *
     * <p>Declared here, above every consumer, because Java initialises static fields in
     * textual order: {@link #THRONE_FLOOR_MIN_Y} and friends read out of this array and would
     * see zeroes if it sat further down the file.
     */
    public static final int[] CHAMBER_FLOOR_MIN_Y_BY_TIER = {
        MIN_Y + 0 * TIER_HEIGHT + LANDING_INTERIOR_HEIGHT,
        MIN_Y + 1 * TIER_HEIGHT + LANDING_INTERIOR_HEIGHT,
        MIN_Y + 2 * TIER_HEIGHT + LANDING_INTERIOR_HEIGHT,
    };

    /**
     * Angle between two neighbouring chamber slots around a shared anchor ramp.
     *
     * <p>The nursery, garden and larder grids all have the same 96-block cell with the same
     * {@code cell*96 + 48} centre, so for one cell all three resolve the <em>same</em> anchor
     * ramp and hang at the same 24-block approach distance. Until play-test round 2 their
     * approach bearings were three independent draws off three positional streams, and the
     * three rooms were kept apart only by their disjoint Y bands -- see
     * {@link ColonyNoise#larderForCell} for what that turned out to be worth once a larder was
     * allowed to leave its band. They are now placed at explicit slots
     * {@code 0 / 2pi/3 / 4pi/3} off one shared per-cell base bearing, so the separation is a
     * property of the layout rather than of the seed.
     */
    public static final double CHAMBER_SLOT_STEP = 2.0 * Math.PI / 3.0;

    /**
     * How far a chamber may wander off its slot, each way.
     *
     * <p>Purely so a colony does not read as three rooms bolted to a compass rose; nothing
     * depends on it being non-zero. 20 degrees is what the worst-case separation below can
     * afford twice over -- two rooms can rotate toward each other, so the guaranteed angular
     * gap is {@code CHAMBER_SLOT_STEP - 2 * this} = 80 degrees, not 100.
     */
    public static final double CHAMBER_SLOT_JITTER = Math.toRadians(20.0);

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
    public static final double[] HARDENED_ACCENT_THRESHOLD_BY_TIER = {-0.26, 0.34, 0.36};
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

    // Ep2 play-test round 2: there are no ambient fungal chances any more. The former
    // FUNGAL_CARPET_CHANCE_BY_TIER / FUNGAL_BLOOM_CHANCE_BY_TIER pair sprayed carpet and
    // bloom across every roomy floor in three of the four tiers, which made the Fungus
    // Garden chambers -- the rooms the fungus is supposed to BE -- indistinguishable from
    // the corridor outside them. Fungus is now farmed content: it exists in a garden
    // chamber (see the GARDEN_* floor chances below) and nowhere else in the carve. Same
    // argument the comb-patch field made in round 1, applied to the other growth.

    /** Royal Depths' sparse amber accents: Resin Block exposed in a wall. */
    public static final double[] RESIN_BLOCK_CHANCE_BY_TIER = {0.020, 0.000, 0.000};

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
    public static final double[] COMB_PATCH_THRESHOLD_BY_TIER = {0.55, 0.30, 9.00};

    /**
     * Brood Comb density <em>inside</em> a patch -- no longer a global per-block chance. It
     * is high because a sparse patch is just speckle with extra steps: what makes a patch
     * read as one thing is that nearly every eligible block in it is comb, with the ragged
     * edge coming from the field's own boundary rather than from holes punched through the
     * middle. Measured totals over roomy surfaces (probe, 96x96, seed 1234567): Nurseries
     * 142 per 1000 against the old flat 178, in patches averaging 7.8 blocks; Royal Depths
     * 10 per 1000 against the old 14.
     */
    public static final double[] BROOD_COMB_CHANCE_BY_TIER = {0.820, 0.900, 0.000};
    /** Royal Comb -- rare, and only inside a comb patch, where brood comb already belongs. */
    public static final double[] ROYAL_COMB_CHANCE_BY_TIER = {0.030, 0.045, 0.000};

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
    // "somewhere legal in the top tier": it is the near side of a guaranteed exit,
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
     * here for a level that already has solid ground beneath it. 132 is one block under the
     * highest floor the band can hold a whole pocket at ({@code ENTRY_SCAN_TOP -
     * ENTRY_CARVE_HEIGHT + 1} = 136 - 4 + 1 = 133), which keeps the fallback search a
     * two-sided one: mostly downward, with a rung above it still available.
     *
     * <p>Round 2 re-derived it off the new {@link #CEILING_BOTTOM} = 138 rather than leaving
     * the old 180 to be clamped. {@code findCarvableFloor} does clamp it -- it takes
     * {@code Math.min(ENTRY_CARVE_PREFERRED_Y, highest)} -- so a stale value would have
     * degraded silently into "always start at the very top rung", quietly deleting the
     * two-sided search this constant exists to create.
     */
    public static final int ENTRY_CARVE_PREFERRED_Y = 132;

    /** Half-width of the carved pocket in X/Z; 2 gives a 5x5 footprint. */
    public static final int ENTRY_CARVE_RADIUS = 2;

    /** Air blocks carved above the pocket floor. */
    public static final int ENTRY_CARVE_HEIGHT = 4;

    // ------------------------------------------------------------------
    // The colony field (Ep2 D1) -- the layer that sits ABOVE everything below it
    //
    // Before this, the dimension was one continuous mega-nest: every tunable below was a
    // constant over the whole world, so 4000 blocks of travel looked exactly like the first
    // 40. The field turns that into dense colony cores with sparse wilds between them --
    // a single scalar f(x, z) in [0, 1] that MODULATES the existing carve rather than
    // replacing it. Nothing here carves anything on its own.
    //
    // What f modulates (see ColonyNoise and ColonyChunkGenerator for the sites):
    //   * blob-chamber small/large thresholds and the comb-patch threshold, lerped from
    //     NEVER_THRESHOLD at f = 0 to their per-tier value at f = 1;
    //   * floor/wall decoration chances and generation-time ant-cluster density, multiplied;
    //   * nursery / fungus garden / larder eligibility, gated at CHAMBER_ELIGIBILITY_MIN_F.
    //
    // What it deliberately does NOT touch: the worm tunnels, the helicoid ramps and their
    // landings, the ceiling membrane and the arrival pockets. Those four are the mod's
    // no-softlock guarantees -- you can always walk the spine top to bottom, you can always
    // see and reach an exit -- and a density field that could switch them off would be a
    // field that can strand a player between colonies.
    // ------------------------------------------------------------------

    /**
     * One colony per this many blocks on each axis. Also the throne chamber's cell: since
     * Ep2 there is exactly one throne, and one queen, per colony by construction.
     *
     * <p><b>Round 2 compaction: 384 -&gt; 320.</b> The play-test asked for colonies that were
     * less spread out and for chambers that were less rare, and the second is why the spacing
     * moved rather than only the radii. Chambers sit on a fixed 96-block grid, so the number a
     * colony can hold is {@code (eligible area) / 96^2} -- shrinking the radii alone cuts that
     * hard (measured: 4.9 nurseries per colony down to 1.3). Pulling the spacing in at the
     * same time raises the cells available per colony to {@code (320/96)^2} = 11.1, which is
     * what pays for the smaller radii. {@link NoiseProbe}'s per-colony chamber census is the
     * judge of the result, not this paragraph.
     */
    public static final int COLONY_SPACING = 320;

    /**
     * Seed-jitter of a colony centre inside its cell, in blocks (TOTAL spread, so a centre
     * lands within {@code COLONY_JITTER / 2} = 48 of its cell centre -- the same convention
     * {@link #SHAFT_JITTER} uses).
     *
     * <p><b>Bounded by an invariant, not by taste.</b> Two neighbouring centres are at least
     * {@code COLONY_SPACING - COLONY_JITTER} = <b>224</b> blocks apart, and that minimum has
     * to stay above {@code 2 *} {@code QueenAntEntity#BOSS_BAR_RADIUS_EXIT} (2 x 28 = 56):
     * a player can only ever be inside two queens' bar radii at once if two thrones are
     * closer than that, so 224 > 56 is what makes two simultaneous boss bars impossible --
     * the symptom play-test round 1 actually reported. {@link NoiseProbe}'s colony section
     * measures the real minimum separation across seeds rather than trusting this algebra.
     *
     * <p>Play-test round 2 pulled {@link #COLONY_SPACING} in from 384 to 320, which took this
     * floor from 288 to 224. The jitter itself is unchanged, and the invariant keeps four
     * times the headroom it needs -- the binding constraint on the spacing is findability and
     * chamber density, never the boss bar.
     */
    public static final double COLONY_JITTER = 96.0;

    /**
     * Inside this radius of a colony centre the field is a flat 1.0 -- full density.
     *
     * <p>Round 2 compaction: 100 -&gt; 80, alongside {@link #COLONY_SPACING} 384 -&gt; 320.
     * The floor under it is the throne, which hangs off the ramp cell containing the colony
     * centre and so lands at most {@code 24*sqrt(2) + SHAFT_JITTER/2 + THRONE_APPROACH_DISTANCE}
     * = 76 blocks out (measured worst case across the probe's sweep: 62.4). A core smaller
     * than that would put a queen's chamber outside her own colony's full-density zone, so
     * 80 is the tightest round value that still contains every throne.
     */
    public static final double COLONY_CORE_RADIUS = 80.0;

    /**
     * By this radius the field has fallen to 0.0 -- wilds: worm tunnels and ramps only.
     *
     * <p>Round 2 compaction: 150 -&gt; 128. This is the hard outer bound on where any chamber
     * can generate -- {@link #CHAMBER_ELIGIBILITY_MIN_F} can only ever cut inside it -- and so
     * it, <b>not the gate</b>, is what caps how many rooms a colony can hold. The disc holds
     * {@code pi * R^2 / NURSERY_SPACING^2} chamber cells, which at 128 is 5.6, and the ~0.8
     * realization factor (a chamber centre hangs 24 blocks off its ramp axis, and that axis
     * must clear the same gate) brings the ceiling to about 4.5 rooms of each kind.
     *
     * <p>It landed at 128 rather than 112 because 112 could not feed the census: measured at
     * an unchanged gate, 112 gave 2.92-3.16 rooms per kind per colony and 128 gives 3.83-3.91,
     * clearing the probe's [3, 9] floor with margin on every seed. 138 was tried and rejected
     * -- it reaches 4.31-4.44, inside the nominal 4-6 target, but with centres 320 apart it
     * leaves only {@code 320 - 2*138} = 44 blocks of true wilds between neighbouring colonies,
     * which erodes the discrete-colony feel the whole field exists to create. At 128 that gap
     * is 64 blocks, and the compaction against the original 150 is real.
     */
    public static final double COLONY_OUTER_RADIUS = 128.0;

    /**
     * Nursery, fungus garden and larder chambers generate only where the field at their own
     * centre is above this.
     *
     * <p>0.15 puts the cut at about 103 blocks from a colony centre (solving the falloff for
     * f = 0.15), i.e. deliberately out in the ring rather than core-only. Nurseries are the
     * only larva source and arrival XZ is uncorrelated with colonies -- portals are 1:1 with
     * the anthill thrown at -- so confining chambers to the cores would land half of all first
     * entries in empty tunnels with nothing findable. This is the named tunable for the
     * near-arrival experience.
     *
     * <p><b>Round 2 loosened it from 0.2, and measured that this is a nearly dead lever.</b>
     * The reason is worth writing down, because it will otherwise be reached for again. The
     * falloff is a smoothstep over {@code [COLONY_CORE_RADIUS, COLONY_OUTER_RADIUS]} and it is
     * flat at both ends, so the whole span from {@code f = 0.2} to {@code f = 0} is a thin
     * annulus at the outer edge -- at the 112 outer radius this was first measured against,
     * radius 100.7 out to 112, eleven blocks wide, and dropping the gate to 0.15 bought two of
     * them. Per-colony chamber census at that radius (probe, seed 1234567, 169 colonies):
     * <pre>
     *   gate 0.20   nursery 3.01   garden 3.09   larder 2.99
     *   gate 0.15   nursery 3.11   garden 3.16   larder 3.05
     *   gate 0.01   nursery 3.45   garden 3.50   larder 3.37   &lt;- the lever's own ceiling
     * </pre>
     *
     * <p>So the gate cannot move the census much, and the number that can is
     * {@link #COLONY_OUTER_RADIUS}: holding this constant at 0.15 and taking the radius from
     * 112 to 128 moved the same census from 3.11 to 3.83. That is the comparison to remember
     * -- the disc's area is the budget ({@code pi * R^2 / NURSERY_SPACING^2} cells), and this
     * constant only shaves its rim.
     *
     * <p>Note the budget is independent of {@link #COLONY_SPACING}: spacing sets how often you
     * meet a colony, not how many rooms one holds. The only other lever is the chamber grid
     * itself, and round 2 measured shrinking that from 96 to 48 as collapsing cross-cell
     * separation from 46.3 blocks to 1.0 -- see {@code NURSERY_SPACING}. Design decisions,
     * not retunes.
     */
    public static final double CHAMBER_ELIGIBILITY_MIN_F = 0.15;

    /**
     * The larder's own, slightly looser floor (play-test round 2): 0.12 against the shared
     * 0.15, which is about 104 blocks from a colony centre against 103.
     *
     * <p>The larder is the one chamber kind that is pure reward -- no brood, no farm, no
     * queen, just a stocked room -- so it is the one whose scarcity was worth relaxing when
     * the play-test asked for more of them. It is a small relaxation on purpose: the ring is
     * already the outer edge of a colony, and this is the only chamber constant in the file
     * that differs from the shared gate, which is why it is named rather than inlined.
     *
     * <p>"Small" is now literal, and measured: one block of extra radius. See
     * {@link #CHAMBER_ELIGIBILITY_MIN_F} for why the whole lever is worth so little -- the
     * ring it moves inside is eleven blocks wide end to end. The gap is kept because the
     * <em>ordering</em> is the point (the larder is the loosest of the three by design), not
     * because the block it buys matters.
     *
     * <p>It does <b>not</b> put larders out in the wilds. A chamber also has to hang off a
     * realized ramp ({@code ColonyNoise#isChamberAnchored}), and ramps are gated at the
     * shared 0.15, so the extra band this opens up is only the sliver where a larder points
     * outward from a ramp that a colony did dig.
     */
    public static final double LARDER_ELIGIBILITY_MIN_F = 0.12;

    /**
     * Ender ants runtime-spawn where the field is <em>below</em> this -- i.e. out in the
     * wilds between colonies, not inside a working nest.
     *
     * <p>Consumed by a later package (the ender ant's {@code SpawnPlacements} predicate);
     * it lives here now because it is a property of the field, and the field is what this
     * package is. 0.35 sits above {@link #CHAMBER_ELIGIBILITY_MIN_F} on purpose: the two
     * bands overlap slightly, so the outer edge of a colony's chamber ring is also where
     * you first start meeting them.
     */
    public static final double ENDER_SPAWN_MAX_F = 0.35;

    /**
     * How many ender ants {@code ColonyChunkGenerator} seeds into each colony's fringe when
     * its chunks generate, inclusive range.
     *
     * <p>Four to six, not a dozen (Ep2 play-test round: bumped up from 2-3 to make the caste
     * more common): these are the ones that are guaranteed to still be there when a player
     * first walks the ring (they are the only persistence-required ender ants -- see
     * {@code EnderAntEntity}'s despawn-policy javadoc), and their job is to make the caste's
     * existence discoverable. The population you actually fight comes from runtime spawning
     * out in the dark wilds.
     */
    public static final int COLONY_ENDER_ANTS_MIN = 4;
    public static final int COLONY_ENDER_ANTS_MAX = 6;

    /**
     * The colony-field band the seeded ants are placed in -- the core's fringe.
     *
     * <p>Deliberately overlapping {@link #ENDER_SPAWN_MAX_F} (0.35) at its lower end and
     * reaching well inside it at its upper: the seeded few are the introduction, so they
     * belong where a player heading out from a colony first meets them, not out in the wilds
     * where the runtime spawns already are. Below 0.3 they would be indistinguishable from
     * ordinary wilds spawns; above 0.8 they would be standing in a working nest.
     */
    public static final double ENDER_SEED_MIN_F = 0.3;
    public static final double ENDER_SEED_MAX_F = 0.8;

    /**
     * The two radii {@link #ENDER_SEED_MAX_F} and {@link #ENDER_SEED_MIN_F} correspond to,
     * solved from {@link #colonyFalloff} rather than written down.
     *
     * <p>Inverting the falloff by hand would mean a second copy of the quintic that goes
     * stale the moment {@link #COLONY_CORE_RADIUS} or {@link #COLONY_OUTER_RADIUS} is
     * retuned. {@link #radiusForField} bisects the real function instead, which is exact to
     * well under a block and costs 64 evaluations once at class load.
     */
    public static final double ENDER_SEED_INNER_RADIUS = radiusForField(ENDER_SEED_MAX_F);
    public static final double ENDER_SEED_OUTER_RADIUS = radiusForField(ENDER_SEED_MIN_F);

    /**
     * The "never" sentinel the {@code *_THRESHOLD_BY_TIER} arrays already used for a tier
     * that grows no comb / no cathedral rooms: no single-octave Perlin value can exceed it
     * (measured span is {@code [-0.90, 0.91]}).
     *
     * <p>It is also the {@code f = 0} end of every threshold lerp, which is what makes the
     * wilds wilds -- at zero field the blob chambers and the comb patches are switched off
     * by exactly the mechanism the two dead tiers already used, rather than by a second,
     * special-cased branch.
     */
    public static final double NEVER_THRESHOLD = 9.00;

    /**
     * The colony density at {@code distance} from the nearest colony centre: 1.0 inside
     * {@link #COLONY_CORE_RADIUS}, smoothstepped to 0.0 by {@link #COLONY_OUTER_RADIUS}.
     *
     * <p>{@link Mth#smoothstep} is vanilla's quintic {@code 6t^5 - 15t^4 + 10t^3}, so the
     * falloff is flat at both ends -- no visible seam where a core stops being a core, and
     * no seam where the wilds begin either. Lives here rather than in {@link ColonyNoise}
     * so that anything registry-free (the probe, a spawn predicate) can ask what the field
     * would be at a distance without building a noise object.
     */
    public static double colonyFalloff(double distance) {
        double t = (distance - COLONY_CORE_RADIUS) / (COLONY_OUTER_RADIUS - COLONY_CORE_RADIUS);
        return 1.0 - Mth.smoothstep(Mth.clamp(t, 0.0, 1.0));
    }

    /**
     * The distance from a colony centre at which {@link #colonyFalloff} equals {@code field}.
     *
     * <p>Plain bisection, which is enough because the falloff is strictly decreasing over
     * {@code [COLONY_CORE_RADIUS, COLONY_OUTER_RADIUS]} (it is {@code 1 - smoothstep}, and
     * smoothstep is strictly increasing on the unit interval). {@code field} outside
     * {@code (0, 1)} simply converges on the matching endpoint. Declared below
     * {@code colonyFalloff} and read by the {@code ENDER_SEED_*_RADIUS} constants above,
     * which is legal in either order for a static <em>method</em> -- only static field
     * initialisers are order-sensitive.
     */
    private static double radiusForField(double field) {
        double inside = COLONY_CORE_RADIUS;
        double outside = COLONY_OUTER_RADIUS;
        for (int step = 0; step < 64; step++) {
            double mid = (inside + outside) * 0.5;
            if (colonyFalloff(mid) > field) {
                inside = mid;
            } else {
                outside = mid;
            }
        }
        return (inside + outside) * 0.5;
    }

    // ------------------------------------------------------------------
    // The queen's throne chamber (M7, re-anchored in Ep2) -- a domed room in the Royal
    // Depths, one per COLONY.
    //
    // Rarity used to be a grid of its own (one per 224-block cell). It is now the colony
    // cell: the chamber hangs off the connectivity ramp whose cell contains the COLONY
    // CENTRE, so "one queen per colony" is true by construction rather than by two grids
    // happening to have similar periods. THRONE_SPACING is gone rather than aliased --
    // a constant nothing reads is a constant that will be retuned by mistake.
    //
    // The chamber is still deliberately NOT placed at a free XZ. It hangs off that ramp at
    // a fixed offset, and its floor is set to the exact Y that ramp's walkway reaches at
    // the approach bearing -- which is what makes the approach corridor meet the spine at a
    // walkable height by construction rather than by luck. The floor-Y arithmetic is
    // ramp-relative and did not change with the re-anchor. See ColonyNoise#throneForCell.
    // ------------------------------------------------------------------

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
     *
     * <p>Round 2 stopped writing the 8 by hand: it is
     * {@link #CHAMBER_FLOOR_MIN_Y_BY_TIER}{@code [0]}, the same tier-floor-plus-landing-
     * clearance formula the other three bands use, and it has to stay that way now that a
     * larder can pick tier 0 too. The value is unchanged.
     */
    public static final int THRONE_FLOOR_MIN_Y = CHAMBER_FLOOR_MIN_Y_BY_TIER[0];

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
     * {@code [56, 80)} and the interior ({@code floor + 7}) plus its shell stays inside the
     * Nurseries band ({@code y < 96}).
     *
     * <p>The {@code + LANDING_INTERIOR_HEIGHT} is load-bearing, not padding. A landing
     * chamber is carved around every ramp axis at each tier boundary -- radius 11, {@code y}
     * in {@code [48, 56)} at its domed centre for this one -- and
     * {@link ColonyNoise#shaftState} outranks every chamber, so a corridor whose forced-solid
     * walkway fell inside that band had the floor cut out from under it for the 1-2 blocks
     * where the landing disc overhangs the ramp's annulus. The probe caught it as a chamber
     * with 113 standable floor blocks and 0 of them reachable; the room was fine, the doorway
     * opened onto a five-block drop into the landing.
     *
     * <p>Round 2 domed the landing, which is why this reads {@link #LANDING_INTERIOR_HEIGHT}
     * rather than {@link #LANDING_HEIGHT}: the rim is still 6 tall but the centre is 8, and
     * the corridor runs from 3 blocks out -- straight through where the dome is tallest. The
     * two blocks the floors moved up are exactly the two the ceiling gained. The arithmetic
     * itself now lives in {@link #CHAMBER_FLOOR_MIN_Y_BY_TIER}, one copy for all four bands.
     */
    public static final int NURSERY_FLOOR_MIN_Y = CHAMBER_FLOOR_MIN_Y_BY_TIER[1];

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

    /**
     * Larvae seeded on a nursery chamber's floor when its chunk generates.
     *
     * <p>Round 2 raises the floor from 2 to 3. The range is what a player finds in the one
     * room type that produces the taming loop's input at all, so its LOWER end is the number
     * that matters -- a 2-larva room is one failed taming attempt away from being a room with
     * nothing in it.
     */
    public static final int NURSERY_LARVAE_MIN = 3;
    public static final int NURSERY_LARVAE_MAX = 4;

    // --- the chamber's own staff (round 2) -------------------------------------
    //
    // Play-test round 2: the chambers were furnished but unstaffed -- a brood room with
    // nobody tending it, a farm with nobody working it. These are seeded the same way the
    // brood is (a pure function of the chamber, placed once by the chunk that holds its
    // centre column), NOT by raising SPAWN_CLUSTERS_PER_CHUNK_BY_TIER: cluster density is a
    // property of a chunk, and "every nursery has ants in it" is a property of a nursery.
    //
    // No per-instance persistence flag is needed or wanted, unlike the larvae and the seeded
    // ender ants above: WorkerAntEntity and SoldierAntEntity both override
    // removeWhenFarAway to false at class level, so every ant of those two castes in the
    // world is already unconditionally persistent. Calling setPersistenceRequired on top
    // would be a second, weaker statement of a guarantee the class already makes.

    /** Workers seeded on a nursery chamber's floor, inclusive range. */
    public static final int NURSERY_WORKERS_MIN = 2;
    public static final int NURSERY_WORKERS_MAX = 3;

    /** Soldiers guarding a nursery chamber, inclusive range. Brood is what gets guarded. */
    public static final int NURSERY_SOLDIERS_MIN = 1;
    public static final int NURSERY_SOLDIERS_MAX = 2;

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
     * (see its javadoc): the {@code + LANDING_INTERIOR_HEIGHT} keeps a corridor from opening
     * onto the landing disc's drop where it overhangs the ramp's annulus at the tier
     * boundary. The ramp turn chosen is the first one at or above this, and the ramp descends
     * 24 blocks per turn, so the floor lands in {@code [104, 128]}.
     *
     * <p>Round 2 made tier 2 the <b>top</b> tier, so the binding constraint here stopped being
     * the band and became the cap. The interior ({@code floor + 7}) tops out at 135 and the
     * shell ({@code floor + 9}) at 137, one block under {@link #CEILING_BOTTOM} = 138 -- so a
     * garden never intrudes into the ceiling cap, and {@link ColonyNoise#isAir}'s
     * {@code y >= CEILING_BOTTOM} clamp never has to truncate one. One block of margin is
     * thin, and it is the reason {@link NoiseProbe} measures the highest shell top rather than
     * trusting this paragraph.
     */
    public static final int GARDEN_FLOOR_MIN_Y = CHAMBER_FLOOR_MIN_Y_BY_TIER[2];

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

    // Round 2 raises all three together (0.12/0.15/0.50 -> 0.16/0.24/0.52). They are
    // cumulative bands on one roll, so the sum IS the floor coverage: 0.77 before, 0.92
    // now -- a garden reads as overgrown rather than as a room with some fungus in it.
    // That is also what pays for the ambient chances deleted above: the fungus a player
    // used to meet everywhere is now concentrated where it is farmed.

    public static final double GARDEN_SPORE_CROP_CHANCE = 0.16;
    public static final double GARDEN_FUNGAL_BLOOM_CHANCE = 0.24;
    public static final double GARDEN_FUNGAL_CARPET_CHANCE = 0.52;

    // --- the farm's own staff (round 2) -- see the nursery's own block above for why
    // this is chamber-anchored seeding rather than a bump to the per-chunk cluster
    // density, and for why no per-instance persistence flag is set. One soldier rather
    // than the nursery's one-or-two: a farm is worth guarding, brood is worth guarding
    // more.

    /** Workers seeded on a garden chamber's floor, inclusive range. */
    public static final int GARDEN_WORKERS_MIN = 2;
    public static final int GARDEN_WORKERS_MAX = 3;

    /** Soldiers escorting a garden chamber's workers, inclusive range. */
    public static final int GARDEN_SOLDIERS_MIN = 1;
    public static final int GARDEN_SOLDIERS_MAX = 1;

    // ------------------------------------------------------------------
    // Larder chambers (Ep2 D3) -- the colony's food storage rooms, cloned end to end from
    // the nursery/garden chambers above onto their own 96-block grid, into a tier the cell
    // picks for itself. Wall/dome/shell/corridor numbers are again cloned
    // VERBATIM from the nursery's own constants -- see the garden section above for why
    // that is the right default. LARDER_APPROACH_DISTANCE = 24.0 for the identical reason
    // GARDEN_APPROACH_DISTANCE is: it clears the lower bound
    // ({@code LARDER_RADIUS + LARDER_SHELL_THICKNESS + SHAFT_MAX_REACH} = 7 + 2 + 13.1 =
    // 22.1, with MORE margin than the garden's own 0.9 since the larder's radius is
    // smaller) with room to spare against the neighbouring-cell separation bound too.
    // ------------------------------------------------------------------

    /** One larder chamber per this many blocks on each axis. */
    public static final int LARDER_SPACING = 96;

    /** Interior radius: 7 gives a 14-block-wide room, between the nursery's 12 and garden's 16. */
    public static final double LARDER_RADIUS = 7.0;

    /** Cloned verbatim from {@link #NURSERY_WALL_HEIGHT}. */
    public static final int LARDER_WALL_HEIGHT = NURSERY_WALL_HEIGHT;

    /** Cloned verbatim from {@link #NURSERY_DOME_HEIGHT}. Interior clearance is the sum: 7. */
    public static final int LARDER_DOME_HEIGHT = NURSERY_DOME_HEIGHT;

    /** Cloned verbatim from {@link #NURSERY_SHELL_THICKNESS}. */
    public static final double LARDER_SHELL_THICKNESS = NURSERY_SHELL_THICKNESS;

    /** See the section javadoc above; the same 24.0 nursery/garden already use. */
    public static final double LARDER_APPROACH_DISTANCE = 24.0;

    /** Cloned verbatim from {@link #NURSERY_CORRIDOR_HALF_WIDTH}; 3-block-wide passage. */
    public static final double LARDER_CORRIDOR_HALF_WIDTH = NURSERY_CORRIDOR_HALF_WIDTH;

    /** Cloned verbatim from {@link #NURSERY_CORRIDOR_HEIGHT}. */
    public static final int LARDER_CORRIDOR_HEIGHT = NURSERY_CORRIDOR_HEIGHT;

    /** Cloned verbatim from {@link #NURSERY_CORRIDOR_START}. */
    public static final double LARDER_CORRIDOR_START = NURSERY_CORRIDOR_START;

    /** Where it stops -- just inside the chamber's shell, which is already air. */
    public static final double LARDER_CORRIDOR_END = LARDER_APPROACH_DISTANCE - LARDER_RADIUS + 2.0;

    /**
     * How many tiers a larder can be dug into -- all of them, since play-test round 2.
     *
     * <p>There is deliberately no {@code LARDER_FLOOR_MIN_Y} any more. A larder used to be
     * anchored to one hard number (the retired top tier's 152), which is why every larder in
     * the world was in the roof: food storage read as a property of one band rather than as a
     * thing colonies do. The floor minimum is now
     * {@link #CHAMBER_FLOOR_MIN_Y_BY_TIER}{@code [tier]} for a tier picked per cell, and the
     * per-tier band containment is what {@link NoiseProbe}'s larder section asserts instead of
     * a single band.
     *
     * <p>Uniform over {@code [0, TIER_COUNT)} -- {@code {0, 1, 2}} since round 2 dropped a
     * tier -- so about a third of larders land in each band. The one pick that is not free is
     * tier 0 on the ramp the colony's throne hangs off -- see
     * {@link ColonyNoise#larderForCell}.
     */
    public static final int LARDER_TIER_COUNT = TIER_COUNT;

    /**
     * Widest a chamber's carve can reach from its centre; used to prune the per-column
     * search. As with the nursery and garden it is the corridor, not the dome, that sets it.
     */
    public static final double LARDER_MAX_REACH = Math.max(
            LARDER_RADIUS + LARDER_SHELL_THICKNESS,
            LARDER_APPROACH_DISTANCE - LARDER_CORRIDOR_START + LARDER_CORRIDOR_HALF_WIDTH) + 1.0;

    // --- what the slot layout is worth, in blocks (shared by all three 96-grid kinds; it
    // lives at the bottom of the larder section only because Java initialises static fields
    // in textual order and this reads all three approach distances) ---

    /**
     * The closest two chambers sharing one cell -- and therefore one anchor ramp -- can
     * possibly come, centre to centre in XZ.
     *
     * <p>All three hang at the same approach distance {@code A} from the same axis, at slots
     * {@link #CHAMBER_SLOT_STEP} apart, each jittered by at most {@link #CHAMBER_SLOT_JITTER}.
     * Two of them can rotate <em>toward</em> each other, so the guaranteed angular gap is
     * {@code STEP - 2*JITTER} = 80 degrees (not 100 -- the jitter is spent twice), and the
     * chord across it is {@code 2*A*sin(40 deg)} = <b>30.85</b> blocks.
     *
     * <p>What that has to clear is {@link #CHAMBER_SLOT_REQUIRED_SEPARATION} = 22.0, so the
     * margin is <b>8.85 blocks</b>. {@link NoiseProbe}'s chamber-pair invariant measures the
     * real minimum over a wide sweep rather than trusting this algebra -- the same
     * "measure, do not guess" standard the nursery section's rejected 48-block cell set.
     */
    public static final double CHAMBER_SLOT_MIN_SEPARATION = 2.0
            * Math.min(NURSERY_APPROACH_DISTANCE, Math.min(GARDEN_APPROACH_DISTANCE, LARDER_APPROACH_DISTANCE))
            * Math.sin(0.5 * (CHAMBER_SLOT_STEP - 2.0 * CHAMBER_SLOT_JITTER));

    /**
     * What two same-cell chambers need between their centres: the largest of the three kinds
     * (the garden, radius 8) counted twice, shell and corridor half-width included.
     *
     * <p>Deliberately the conservative reading. Two rooms only need
     * {@code (R1+S1) + (R2+S2)} -- 19 for the worst real pair, garden and larder -- and the
     * corridors radiate from the shared axis at the same 80-degree gap, so they diverge rather
     * than converge as they leave it. Taking the biggest kind twice and adding both corridor
     * half-widths costs nothing here and means the bound cannot be invalidated by retuning
     * which kind is largest.
     */
    public static final double CHAMBER_SLOT_REQUIRED_SEPARATION =
            2.0 * (GARDEN_RADIUS + GARDEN_SHELL_THICKNESS + GARDEN_CORRIDOR_HALF_WIDTH);

    // --- keeping a tier-0 larder away from the queen (play-test round 2, item 7) ---
    //
    // The slots arrange the three rooms that share ONE ramp. The throne shares no grid with
    // them, has no slot, and until this round no other room could reach its tier -- so once a
    // larder could pick tier 0, nothing arranged the two at all. Measured, before this gate
    // existed: 3 larders on seed 1234567 and 10 on seed 987654321 (of ~600 tier-0 larders per
    // seed) overlapped a throne's carve, the worst pair 3.8 blocks centre to centre, up to
    // 832 blocks where one wanted air and the other solid. Since the throne outranks the
    // larder in ColonyNoise#isAir, every one of those is a larder that loses its room or its
    // doorway. Seed 42 was clean, which is the whole reason this is a derived bound and not a
    // three-seed spot check.

    /**
     * A ball around a throne's centre containing its entire carve.
     *
     * <p>Every part of the chamber -- dome, shell, and the approach corridor running back to
     * its ramp axis -- lies inside this radius of the centre. The dome and shell need
     * {@code THRONE_RADIUS + THRONE_SHELL_THICKNESS} = 16; the corridor is the larger term,
     * and it is bounded by {@code THRONE_APPROACH_DISTANCE + THRONE_CORRIDOR_HALF_WIDTH} =
     * 35.5, i.e. the whole ray from the centre to the axis plus the corridor's half-width.
     *
     * <p>Deliberately looser than {@link #THRONE_MAX_REACH}, which subtracts
     * {@code THRONE_CORRIDOR_START} because the corridor stops short of the axis. Dropping
     * that term costs three blocks and buys a bound that stays true for any corridor start,
     * including 0.
     */
    public static final double THRONE_CARVE_ENVELOPE = Math.max(
            THRONE_RADIUS + THRONE_SHELL_THICKNESS,
            THRONE_APPROACH_DISTANCE + THRONE_CORRIDOR_HALF_WIDTH);

    /** The same envelope for a larder: {@code max(7 + 2, 24 + 1)} = 25. */
    public static final double LARDER_CARVE_ENVELOPE = Math.max(
            LARDER_RADIUS + LARDER_SHELL_THICKNESS,
            LARDER_APPROACH_DISTANCE + LARDER_CORRIDOR_HALF_WIDTH);

    /** Blocks of solid fabric demanded between the two envelopes, so they never merely graze. */
    public static final double THRONE_LARDER_MARGIN = 2.0;

    /**
     * How far a tier-0 larder's centre must be from its colony's throne centre. Closer than
     * this and the cell's tier pick is bumped to 1 (see {@link ColonyNoise#larderForCell}).
     *
     * <p>Two envelopes plus a margin: {@code 35.5 + 25.0 + 2.0} = <b>62.5</b>. Because each
     * envelope contains its chamber's whole carve, two centres this far apart cannot share a
     * single block, whatever the seed did with the bearings, the shaft jitter or the colony
     * jitter. That is the property a neighbourhood guard on shaft cells could not have: cells
     * are a proxy for distance, and the measured near-misses at two cells' separation show the
     * proxy is not tight. This bound also survives a retune of any approach distance, room
     * radius, shell or corridor width, because it is computed from them.
     *
     * <p><b>It subsumes the same-shaft case</b> rather than sitting beside it. A larder
     * sharing the throne's ramp hangs 24 from that axis while the throne hangs 34 from it, so
     * the two centres are between 10 and 58 blocks apart -- always under 62.5. A separate
     * same-shaft test would be dead code, and is gone.
     */
    public static final double THRONE_LARDER_CLEARANCE =
            THRONE_CARVE_ENVELOPE + LARDER_CARVE_ENVELOPE + THRONE_LARDER_MARGIN;

    // --- wall decoration inside the chamber (ColonyChunkGenerator#decorateLarderSurface) ---
    //
    // Same two-entry, rarer-first shape decorateNurserySurface/decorateThroneSurface use.
    // Provision Comb is what makes this room worth finding: the affordability floor's own
    // pearls live inside it (see ModBlockLootSubProvider#provisionCombTable).

    /**
     * Provision Comb density on a larder's walls. Play-test round 2 drops it hard, 0.18 to
     * 0.035, and the reason is the loot table rather than the look: a larder was carrying
     * roughly thirty of these, and every one of them was an unconditional 1-2 ender pearls.
     * With the rework (see {@code ModBlockLootSubProvider#provisionCombTable}) each comb is
     * worth breaking, so the right number of them is small: about 4 rolled plus the 2
     * guaranteed, six per room.
     */
    public static final double LARDER_PROVISION_COMB_CHANCE = 0.035;
    public static final double LARDER_BROOD_COMB_CHANCE = 0.50;

    // --- the guaranteed minimum (spec: "a deterministic minimum of 2 per larder from the
    // chamber's own seeded random") -- see ColonyNoise.Larder's own javadoc for why this
    // has to be a dedicated seeded placement rather than a probabilistic top-up: the
    // per-column wall decorator that rolls LARDER_PROVISION_COMB_CHANCE above never sees
    // the whole room, so it has no way to count what it already placed. ---

    /** How many Provision Comb blocks every larder is guaranteed, independent of the roll. */
    public static final int LARDER_GUARANTEED_PROVISION_COMB = 2;

    /**
     * Distance from the chamber centre for a guaranteed comb slot: one block outside
     * {@link #LARDER_RADIUS}, so it always lands inside the forced-solid shell
     * ({@code (RADIUS, RADIUS + SHELL_THICKNESS]} = {@code (7, 9]}) and never punches a
     * hole in the room's own air, even after the +-0.5 rounding two independent integer
     * coordinates from a non-integer centre can introduce.
     */
    public static final double LARDER_COMB_RADIUS = LARDER_RADIUS + 1.0;

    /**
     * Height above the floor for a guaranteed comb slot: comfortably inside
     * {@code [1, LARDER_WALL_HEIGHT]} so it sits on the cylindrical wall, never under the
     * dome's curve or in the floor slab.
     */
    public static final int LARDER_COMB_HEIGHT = 2;

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
    public static final double[] SPAWN_CLUSTERS_PER_CHUNK_BY_TIER = {0.15, 0.32, 0.26};

    /** Workers per cluster, inclusive range. Tier 0 (Royal Depths) fields none. */
    public static final int[] CLUSTER_WORKERS_MIN_BY_TIER = {0, 3, 3};
    public static final int[] CLUSTER_WORKERS_MAX_BY_TIER = {0, 4, 4};

    /**
     * Soldiers per cluster, inclusive range. One per party keeps the ratio at the 3:1 -- 4:1
     * the brief asks for; the Royal Depths stay soldier-only, which M4a chose deliberately
     * (a boss-guarding tier with no foragers in it) and nothing in this round disturbs.
     */
    public static final int[] CLUSTER_SOLDIERS_MIN_BY_TIER = {2, 1, 1};
    public static final int[] CLUSTER_SOLDIERS_MAX_BY_TIER = {4, 1, 1};

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

    /** Bottom-up tier index for a block Y: 0 = Royal Depths ... 2 = Fungal Gardens. */
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
