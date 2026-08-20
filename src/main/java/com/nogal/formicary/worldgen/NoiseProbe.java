package com.nogal.formicary.worldgen;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FLOOR_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MEMBRANE_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierIndex;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMaxY;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMinY;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Headless developer tool: runs {@link ColonyNoise} -- the exact carve the game runs --
 * over a slab of the dimension and reports what it produced.
 *
 * <p>It exists because the acceptance bar for this dimension is how it <i>looks</i>, and
 * the constants in {@link ColonyGeneratorTunables} are only meaningful relative to the raw
 * amplitude of single-octave Perlin noise. Guessing a threshold can silently produce a
 * fully solid or fully hollow world; this prints air fractions per tier, walkable-floor
 * counts, a flood-fill connectivity check from top to bottom, and ASCII cross-sections, so
 * a retune can be checked in seconds without booting a client.
 *
 * <p>Not referenced by any game code. Run it from the repo root with JAVA_HOME on 21:
 * <pre>
 *   .\gradlew --init-script docs\noise-probe.init.gradle formicaryProbe
 *   .\gradlew --init-script docs\noise-probe.init.gradle formicaryProbe -PprobeSeed=42 -PprobeWhat=slices
 *   .\gradlew --init-script docs\noise-probe.init.gradle formicaryProbe -PprobeWhat=colony
 * </pre>
 *
 * <p>Since Ep2 the sections whose subject only exists inside a nest ({@link #nurseries},
 * {@link #gardens}, {@link #larders}, {@link #combPatches}, {@link #spawnDensity}) sample
 * around the colony nearest the origin rather than around the origin itself -- see
 * {@link #anchor}. Play-test round 2 added {@link #connectivity}, {@link #shafts} and the
 * ASCII slices to that list, because the connectivity ramps became colony infrastructure
 * too: out in the wilds there is no spine to measure.
 */
public final class NoiseProbe {

    private static final int SAMPLE_RADIUS = 96;

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1234567L;
        ColonyNoise noise = new ColonyNoise(new XoroshiroRandomSource(seed).forkPositional());

        System.out.println("== Formicary colony generator probe, seed " + seed + " ==");
        System.out.printf(Locale.ROOT, "layout: minY=%d height=%d carvable y=[%d,%d)%n",
                MIN_Y, HEIGHT, FLOOR_TOP, CEILING_BOTTOM);

        String what = args.length > 1 ? args[1] : "all";
        if (what.equals("all") || what.equals("stats")) {
            rawNoiseDistribution(noise);
            airFractions(noise, seed);
            palette(noise);
            membranes(noise);
            connectivity(noise);
            shafts(noise);
            colonies(noise);
            thrones(noise);
            nurseries(noise);
            gardens(noise);
            chamberSlots(noise);
            larders(noise);
            combPatches(noise);
            soilPockets(noise);
            spawnDensity(noise);
        }
        if (what.equals("colony")) {
            colonies(noise);
        }
        if (what.equals("membrane")) {
            membranes(noise);
        }
        if (what.equals("throne")) {
            thrones(noise);
        }
        if (what.equals("nursery")) {
            nurseries(noise);
        }
        if (what.equals("garden")) {
            gardens(noise);
        }
        if (what.equals("larder")) {
            larders(noise);
        }
        if (what.equals("slots")) {
            chamberSlots(noise);
        }
        if (what.equals("comb")) {
            combPatches(noise);
        }
        if (what.equals("pockets")) {
            soilPockets(noise);
        }
        if (what.equals("spawns")) {
            spawnDensity(noise);
        }
        if (what.equals("palette")) {
            palette(noise);
        }
        if (what.equals("shaft")) {
            shafts(noise);
        }
        if (what.equals("all") || what.equals("slices")) {
            // Slice straight through a ramp axis, so the connectivity spine shows up in
            // section rather than being missed between two shafts. Anchored on a COLONY
            // since play-test round 2: ramps are colony infrastructure now, and
            // `shaftsNear(0, 0)` out in the wilds legitimately returns nothing at all.
            ColonyNoise.Colony anchor = anchor(noise);
            int anchorX = (int) Math.round(anchor.centreX());
            int anchorZ = (int) Math.round(anchor.centreZ());
            ColonyNoise.Shaft[] near = noise.shaftsNear(anchorX, anchorZ);
            if (near.length == 0) {
                System.out.printf(Locale.ROOT,
                        "%nno realized ramp near the anchor colony at (%d, %d) -- skipping the slices%n",
                        anchorX, anchorZ);
            } else {
                ColonyNoise.Shaft closest = near[0];
                for (ColonyNoise.Shaft s : near) {
                    if (Math.hypot(s.axisX() - anchorX, s.axisZ() - anchorZ)
                            < Math.hypot(closest.axisX() - anchorX, closest.axisZ() - anchorZ)) {
                        closest = s;
                    }
                }
                System.out.printf(Locale.ROOT, "%nnearest ramp axis to the anchor colony: (%.1f, %.1f)%n",
                        closest.axisX(), closest.axisZ());
                crossSectionXY(noise, (int) Math.round(closest.axisZ()), (int) Math.round(closest.axisX()));
                // One plan slice per tier, at the middle of each band. Round 2 dropped y=168
                // along with the Upper Galleries; 120 / 72 / 24 are tiers 2 / 1 / 0.
                crossSectionXZ(noise, 120, anchorX, anchorZ);
                crossSectionXZ(noise, 72, anchorX, anchorZ);
                crossSectionXZ(noise, 24, anchorX, anchorZ);
            }
        }
    }

    private static final double[] PROBE_THRESHOLDS = {0.05, 0.10, 0.16, 0.20, 0.26, 0.30, 0.34, 0.40, 0.46, 0.55};

    /**
     * What each single-octave Perlin field actually spans, plus the fraction of space above
     * a ladder of candidate thresholds -- i.e. exactly the numbers the {@code *_THRESHOLD_*}
     * and {@code *_HALF_WIDTH_*} tunables need to be chosen from.
     */
    private static void rawNoiseDistribution(ColonyNoise noise) {
        field(noise, "tunnel (|v| < halfWidth)", true, (x, y, z) -> noise.probeTunnelA(x, y, z));
        field(noise, "chamberSmall (v > t)", false, (x, y, z) -> noise.probeChamberSmall(x, y, z));
        field(noise, "chamberLarge (v > t)", false, (x, y, z) -> noise.probeChamberLarge(x, y, z));
        field(noise, "accent (v > t)", false, (x, y, z) -> noise.probeAccent(x, y, z));
        // Retired as a gate in Ep2 (membrane is now the visibility mask alone); printed
        // only so a future pass that wants to ration exits again has the span in front of it.
        field(noise, "membrane 2D (gates nothing)", false, (x, y, z) -> noise.probeMembrane(x, z));
    }

    /**
     * The Ep2 acceptance criterion for the exit, and it is an <b>invariant</b> rather than a
     * measurement: every ceiling column with air beneath it carries a Daylight Membrane,
     * over the whole {@link #MEMBRANE_THICKNESS}-layer stack. "Visible roof = way out", with
     * no exceptions for the probe to average over.
     *
     * <p>It replaced a threshold sweep that reported, per candidate
     * {@code MEMBRANE_THRESHOLD}, the distance from an exposed ceiling point to the nearest
     * visible patch. That number was healthy (median 21-24 blocks) while play-test round 1
     * reported the exits as unfindable, which is the whole reason the tuning knob is gone:
     * a distance to a patch a player has no way to distinguish from plain cap measures
     * nothing they experience. What is worth printing now is the scarcity the visibility
     * mask still imposes on its own -- the membrane fraction of the ceiling -- and that is
     * reported as information, not as a gate.
     *
     * <p>The converse is checked too: a membrane over solid fabric would mean the mask had
     * stopped being the only rationing rule, which is the shape the next regression here
     * would take.
     */
    private static void membranes(ColonyNoise noise) {
        int span = SAMPLE_RADIUS * 2;
        int exposedCount = 0;
        int membraneCount = 0;
        int violations = 0;
        int strays = 0;

        for (int ix = 0; ix < span; ix++) {
            int x = ix - SAMPLE_RADIUS;
            for (int iz = 0; iz < span; iz++) {
                int z = iz - SAMPLE_RADIUS;
                Col col = col(noise, x, z);
                boolean exposed = air(noise, col, x, CEILING_BOTTOM - 1, z);
                boolean stacked = true;
                for (int layer = 0; layer < MEMBRANE_THICKNESS; layer++) {
                    stacked &= noise.isDaylightMembrane(col.field(), col.shafts(), col.thrones(), col.nurseries(),
                            col.gardens(), col.larders(), x, CEILING_BOTTOM + layer, z);
                }
                if (exposed) {
                    exposedCount++;
                    if (!stacked) {
                        violations++;
                    }
                } else if (stacked) {
                    strays++;
                }
                if (stacked) {
                    membraneCount++;
                }
            }
        }

        int columns = span * span;
        System.out.printf(Locale.ROOT, "%ndaylight membranes over %dx%d ceiling columns:%n", span, span);
        System.out.printf(Locale.ROOT, "  ceiling with air under it:      %6.2f%% of columns (%d)%n",
                100.0 * exposedCount / columns, exposedCount);
        System.out.printf(Locale.ROOT, "  membrane (all %d layers):        %6.2f%% of columns (%d)%n",
                MEMBRANE_THICKNESS, 100.0 * membraneCount / columns, membraneCount);
        System.out.printf(Locale.ROOT, "  exposed columns WITHOUT membrane: %d%n", violations);
        System.out.printf(Locale.ROOT, "  membrane over solid fabric:       %d%n", strays);
        System.out.println(violations == 0 && strays == 0
                ? "  PASS: every exposed ceiling column is an exit, and nothing else is."
                : "  FAIL: the ceiling and the exits disagree.");
    }

    private interface Field {
        double at(int x, int y, int z);
    }

    private static void field(ColonyNoise noise, String label, boolean twoSided, Field field) {
        int n = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sumAbs = 0.0;
        int[] over = new int[PROBE_THRESHOLDS.length];
        for (int x = -240; x < 240; x += 3) {
            for (int z = -240; z < 240; z += 3) {
                for (int y = FLOOR_TOP; y < CEILING_BOTTOM; y += 5) {
                    double v = field.at(x, y, z);
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                    sumAbs += Math.abs(v);
                    for (int i = 0; i < PROBE_THRESHOLDS.length; i++) {
                        if (twoSided ? Math.abs(v) < PROBE_THRESHOLDS[i] : v > PROBE_THRESHOLDS[i]) {
                            over[i]++;
                        }
                    }
                    n++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "%n%s: n=%d min=%.4f max=%.4f mean|v|=%.4f%n", label, n, min, max, sumAbs / n);
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < PROBE_THRESHOLDS.length; i++) {
            sb.append(String.format(Locale.ROOT, "%.2f->%5.2f%%  ", PROBE_THRESHOLDS[i], 100.0 * over[i] / n));
        }
        System.out.println(sb);
    }

    /** Per-tier air fraction, split by which mechanism carved it. */
    private static void airFractions(ColonyNoise noise, long seed) {
        long[] total = new long[TIER_COUNT];
        long[] air = new long[TIER_COUNT];
        long[] tunnel = new long[TIER_COUNT];
        long[] chamber = new long[TIER_COUNT];
        long[] shaft = new long[TIER_COUNT];
        long[] floors = new long[TIER_COUNT];

        for (int x = -SAMPLE_RADIUS; x < SAMPLE_RADIUS; x++) {
            for (int z = -SAMPLE_RADIUS; z < SAMPLE_RADIUS; z++) {
                Col col = col(noise, x, z);
                boolean belowAir = false;
                for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
                    int tier = tierIndex(y);
                    total[tier]++;
                    boolean isAir = air(noise, col, x, y, z);
                    if (isAir) {
                        air[tier]++;
                        if (noise.shaftState(col.shafts(), x, y, z) == ColonyNoise.SHAFT_AIR) {
                            shaft[tier]++;
                        } else if (noise.isTunnelCarved(x, y, z)) {
                            tunnel[tier]++;
                        } else {
                            chamber[tier]++;
                        }
                        if (!belowAir && y + 1 < MIN_Y + HEIGHT && air(noise, col, x, y + 1, z)) {
                            floors[tier]++;
                        }
                    }
                    belowAir = isAir;
                }
            }
        }

        System.out.printf(Locale.ROOT, "%nair fractions over %dx%d blocks (seed %d):%n",
                SAMPLE_RADIUS * 2, SAMPLE_RADIUS * 2, seed);
        for (int tier = TIER_COUNT - 1; tier >= 0; tier--) {
            System.out.printf(Locale.ROOT,
                    "  tier %d %-16s y[%3d,%3d)  air %5.2f%%  (tunnel %5.2f%% chamber %5.2f%% shaft %5.2f%%)  floors/col %.2f%n",
                    tier, tierName(tier), tierMinY(tier), tierMaxY(tier),
                    100.0 * air[tier] / total[tier],
                    100.0 * tunnel[tier] / total[tier],
                    100.0 * chamber[tier] / total[tier],
                    100.0 * shaft[tier] / total[tier],
                    (double) floors[tier] / (SAMPLE_RADIUS * SAMPLE_RADIUS * 4));
        }
    }

    private static final int SLAB = 128;

    /**
     * The acceptance criterion that actually matters: a player who cannot mine (the fabric
     * is gated behind full Chitin Armor) must be able to WALK from the top tier down to
     * the Royal Depths and back.
     *
     * <p>So this is not an air flood fill. It builds the graph of standable positions --
     * air at y and y+1 with something solid at y-1 -- and only connects neighbouring
     * columns when the standable heights differ by at most one block. That edge rule is
     * symmetric, so any path it finds is walkable in both directions by construction: a
     * one-block rise is a jump, a one-block fall is reversible. Anything steeper is
     * excluded even though a player could survive falling down it.
     *
     * <p><b>Anchored on a colony since play-test round 2</b>, for the same reason the chamber
     * sections were anchored in Ep2 and with the same care about what that does and does not
     * weaken. The claim being asserted is unchanged and is still geometric: <em>inside a
     * colony</em>, a player who cannot mine can walk the full 192 blocks from the Upper
     * Galleries to the Royal Depths and back. What changed underneath it is that the ramps
     * are now colony infrastructure ({@code ColonyNoise#isShaftRealized}), so a slab at the
     * origin -- 200+ blocks out in the wilds on essentially every seed -- contains no spine
     * at all and would measure the gate rather than the descent. That the wilds have no
     * vertical circulation is the deliberate design of item 5, not a regression: the
     * membrane, the arrival pockets and the worm tunnels are all ungated, so the exit is
     * still reachable from anywhere, and the walk to a colony is bounded by
     * {@link #colonyFindability}.
     */
    private static void connectivity(ColonyNoise noise) {
        int half = SLAB / 2;
        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        boolean[] air = new boolean[SLAB * SLAB * HEIGHT];
        for (int ix = 0; ix < SLAB; ix++) {
            for (int iz = 0; iz < SLAB; iz++) {
                int x = anchorX + ix - half;
                int z = anchorZ + iz - half;
                Col col = col(noise, x, z);
                for (int y = 0; y < HEIGHT; y++) {
                    air[index(ix, y, iz)] = air(noise, col, x, MIN_Y + y, z);
                }
            }
        }

        boolean[] standable = new boolean[air.length];
        long standCount = 0;
        for (int ix = 0; ix < SLAB; ix++) {
            for (int iz = 0; iz < SLAB; iz++) {
                for (int y = 1; y < HEIGHT - 1; y++) {
                    if (air[index(ix, y, iz)] && air[index(ix, y + 1, iz)] && !air[index(ix, y - 1, iz)]) {
                        standable[index(ix, y, iz)] = true;
                        standCount++;
                    }
                }
            }
        }

        // Seed from every standable spot in the top tier at once: the question is whether
        // the top tier as a whole connects down, not whether one arbitrary ledge does.
        boolean[] seen = new boolean[air.length];
        Deque<Integer> queue = new ArrayDeque<>();
        int topTierMin = tierMinY(TIER_COUNT - 1) - MIN_Y;
        for (int ix = 0; ix < SLAB; ix++) {
            for (int iz = 0; iz < SLAB; iz++) {
                for (int y = topTierMin; y < HEIGHT - 1; y++) {
                    int i = index(ix, y, iz);
                    if (standable[i] && !seen[i]) {
                        seen[i] = true;
                        queue.add(i);
                    }
                }
            }
        }

        long reached = 0;
        int deepest = HEIGHT;
        int[] reachedPerY = new int[HEIGHT];
        int[] standablePerY = new int[HEIGHT];
        for (int i = 0; i < standable.length; i++) {
            if (standable[i]) {
                standablePerY[(i / SLAB) % HEIGHT]++;
            }
        }
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            reached++;
            int iz = cur % SLAB;
            int y = (cur / SLAB) % HEIGHT;
            int ix = cur / (SLAB * HEIGHT);
            reachedPerY[y]++;
            deepest = Math.min(deepest, y);
            step(queue, seen, standable, ix + 1, y, iz);
            step(queue, seen, standable, ix - 1, y, iz);
            step(queue, seen, standable, ix, y, iz + 1);
            step(queue, seen, standable, ix, y, iz - 1);
        }

        System.out.println("\n  reachable standable floors per 8-block Y band (reached / total):");
        for (int band = HEIGHT / 8 - 1; band >= 0; band--) {
            int r = 0;
            int t = 0;
            for (int y = band * 8; y < band * 8 + 8; y++) {
                r += reachedPerY[y];
                t += standablePerY[y];
            }
            System.out.printf(Locale.ROOT, "    y%3d-%3d  %6d / %6d%n", MIN_Y + band * 8, MIN_Y + band * 8 + 7, r, t);
        }

        System.out.printf(Locale.ROOT,
                "%nwalkable connectivity (%dx%dx%d slab centred on the colony at (%d, %d),"
                        + " step height 1, symmetric):%n"
                        + "  standable positions: %d, reachable on foot from the top tier: %d (%.1f%%)%n"
                        + "  deepest standable Y reached = %d   (dimension floor cap top = %d, Royal Depths = y[%d,%d))%n",
                SLAB, SLAB, HEIGHT, anchorX, anchorZ, standCount, reached, 100.0 * reached / standCount,
                MIN_Y + deepest, FLOOR_TOP, tierMinY(0), tierMaxY(0));
        System.out.println(MIN_Y + deepest < tierMaxY(0)
                ? "  PASS: the Royal Depths are reachable on foot from the top tier (and back, edges are symmetric)."
                : "  FAIL: cannot walk from the top tier into the Royal Depths.");
    }

    /** Cell rings swept by {@link #shafts}: 25x25 shaft cells around the anchor colony. */
    private static final int SHAFT_CELL_SWEEP = 12;

    /**
     * The connectivity spine's own invariants, after play-test round 2 made ramps colony
     * infrastructure rather than a global grid.
     *
     * <p>Two assertions, and they pull in opposite directions on purpose -- which is what
     * makes the pair meaningful where either alone would not be:
     * <ol>
     *   <li><b>Every realized shaft is inside a colony</b> ({@code f >= }
     *       {@link ColonyGeneratorTunables#CHAMBER_ELIGIBILITY_MIN_F} at its axis). This is
     *       the gate itself, asserted as a geometric invariant rather than measured as a
     *       statistic: one ramp left standing in the wilds is a bug. Sampled through
     *       {@link ColonyNoise#shaftsNear}, the same seam generation reads, so the probe
     *       cannot pass on a truth the carve does not share.</li>
     *   <li><b>Every colony contains at least 3 realized shafts.</b> The counterweight, and
     *       the one that would actually fire if the gate were made too strict: a colony with
     *       one ramp is a colony where every chamber hangs off the same axis, and a colony
     *       with none has no vertical circulation at all -- the softlock the design notes
     *       rule out. Three is a floor, not a target; the real number is reported next to
     *       it.</li>
     * </ol>
     *
     * <p>The throne's own anchor is checked here too. {@code ColonyNoise#throneForCell}
     * deliberately does <em>not</em> gate on realization, on the argument that the ramp whose
     * cell holds the colony centre is always within 41.9 blocks of a point where the field is
     * 1.0. That argument is exactly the kind that stops being true after a retune of
     * {@code SHAFT_SPACING} or {@code COLONY_CORE_RADIUS}, so it is asserted rather than
     * trusted.
     */
    private static void shafts(ColonyNoise noise) {
        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        System.out.printf(Locale.ROOT,
                "%nconnectivity ramps (one per %d-block cell, realized only inside a colony, f >= %.2f):%n",
                ColonyGeneratorTunables.SHAFT_SPACING, ColonyGeneratorTunables.CHAMBER_ELIGIBILITY_MIN_F);

        // (1) Every shaft generation can see, over a wide sweep, is in a colony.
        int realized = 0;
        int cells = 0;
        int outsideColony = 0;
        double worstField = 1.0;
        int anchorCellX = Math.floorDiv(anchorX, ColonyGeneratorTunables.SHAFT_SPACING);
        int anchorCellZ = Math.floorDiv(anchorZ, ColonyGeneratorTunables.SHAFT_SPACING);
        for (int cx = -SHAFT_CELL_SWEEP; cx <= SHAFT_CELL_SWEEP; cx++) {
            for (int cz = -SHAFT_CELL_SWEEP; cz <= SHAFT_CELL_SWEEP; cz++) {
                int cellX = anchorCellX + cx;
                int cellZ = anchorCellZ + cz;
                cells++;
                for (ColonyNoise.Shaft shaft : noise.shaftsNear(cellX * ColonyGeneratorTunables.SHAFT_SPACING,
                        cellZ * ColonyGeneratorTunables.SHAFT_SPACING)) {
                    // shaftsNear returns the 3x3 ring, so a shaft is seen up to nine times;
                    // only count the one belonging to this cell to keep the ratio honest.
                    // An axis lands within SHAFT_JITTER/2 = 8 of its cell centre, so
                    // floorDiv recovers its cell exactly.
                    if (!ownsCell(shaft, cellX, cellZ)) {
                        continue;
                    }
                    realized++;
                    double field = noise.colonyField(shaft.axisX(), shaft.axisZ());
                    worstField = Math.min(worstField, field);
                    if (field < ColonyGeneratorTunables.CHAMBER_ELIGIBILITY_MIN_F) {
                        outsideColony++;
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  %d realized ramps over %d sampled cells (%.1f%%); lowest colony field at a realized axis %.3f%n",
                realized, cells, 100.0 * realized / cells, worstField);
        boolean pass = outsideColony == 0;
        System.out.println(pass
                ? "  PASS: every ramp the carve realizes stands inside a colony."
                : "  FAIL: " + outsideColony + " realized ramp(s) stand out in the wilds.");

        // (2) Every colony has enough of them, and the throne's anchor is one of them.
        int colonies = 0;
        int starved = 0;
        int fewest = Integer.MAX_VALUE;
        int thronesOnUnrealizedRamps = 0;
        for (int cx = -COLONY_CELL_SWEEP; cx <= COLONY_CELL_SWEEP; cx++) {
            for (int cz = -COLONY_CELL_SWEEP; cz <= COLONY_CELL_SWEEP; cz++) {
                ColonyNoise.Colony colony = noise.colonyCenterForCell(cx, cz);
                colonies++;
                int here = countRealizedShaftsIn(noise, colony);
                fewest = Math.min(fewest, here);
                if (here < 3) {
                    starved++;
                }
                ColonyNoise.Throne throne = noise.nearestThrone((int) Math.round(colony.centreX()),
                        (int) Math.round(colony.centreZ()));
                if (!noise.isShaftRealized(new ColonyNoise.Shaft(throne.axisX(), throne.axisZ(), 0.0))) {
                    thronesOnUnrealizedRamps++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  ramps per colony over %d colonies: fewest %d (required >= 3); thrones hanging off an"
                        + " unrealized ramp: %d%n",
                colonies, fewest, thronesOnUnrealizedRamps);
        boolean populated = starved == 0 && thronesOnUnrealizedRamps == 0;
        System.out.println(populated
                ? "  PASS: every colony digs at least 3 ramps, and every queen's chamber hangs off one of them."
                : "  FAIL: " + starved + " colony/colonies have fewer than 3 ramps, and "
                        + thronesOnUnrealizedRamps + " throne(s) hang off a ramp that is not there.");

        boolean landings = landings(noise);

        System.out.println(pass && populated && landings
                ? "  SHAFT SECTION: ALL PASS"
                : "  SHAFT SECTION: FAILED -- see the FAIL lines above.");
    }

    /**
     * The rounded landing disc (play-test round 2, item 6), measured rather than eyeballed.
     *
     * <p>Three things have to be true at once, and the first two pull against the third --
     * which is the whole reason the shape needed a probe rather than a look:
     * <ol>
     *   <li><b>It is domed.</b> Interior clearance at the centre exceeds clearance at the
     *       rim, or the "rounded" in the task is a comment rather than a carve.</li>
     *   <li><b>The ramp junction keeps its headroom.</b> Every column where the ramp's own
     *       annulus crosses the disc has at least {@link
     *       ColonyGeneratorTunables#RAMP_AIR_HEIGHT} of air above the floor. Taper the wall
     *       too aggressively and the landing becomes a place the spine gets shorter, which
     *       is the one thing it may not be.</li>
     *   <li><b>It is walkable from the ramp</b>, by the same symmetric one-block-step BFS
     *       every chamber gets -- seeded from the ramp walkway alone, so a PASS means the
     *       disc joins the spine rather than joining some noise pocket beside it.</li>
     * </ol>
     */
    private static boolean landings(ColonyNoise noise) {
        ColonyNoise.Colony anchor = anchor(noise);
        ColonyNoise.Shaft[] near = noise.shaftsNear((int) Math.round(anchor.centreX()),
                (int) Math.round(anchor.centreZ()));
        if (near.length == 0) {
            System.out.println("  FAIL: the anchor colony has no realized ramp to hang a landing on.");
            return false;
        }
        ColonyNoise.Shaft shaft = near[0];

        // (1) The profile itself, straight off the shared arithmetic the carve uses.
        StringBuilder profile = new StringBuilder();
        for (int h = 0; h < ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT; h++) {
            profile.append(String.format(Locale.ROOT, " h%d r=%.1f", h, ColonyNoise.landingRadius(h)));
        }
        System.out.printf(Locale.ROOT, "%n  landing profile (radius per air layer):%s%n", profile);

        boolean tapers = ColonyNoise.landingRadius(ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT - 1)
                < ColonyNoise.landingRadius(ColonyGeneratorTunables.LANDING_WALL_HEIGHT)
                && ColonyNoise.landingRadius(0)
                        < ColonyNoise.landingRadius(ColonyGeneratorTunables.LANDING_WALL_HEIGHT);
        System.out.println(tapers
                ? "  PASS: the profile tapers at both ends -- the silhouette is a dome, not a cylinder."
                : "  FAIL: the landing profile is flat; nothing was rounded.");

        boolean pass = tapers;
        for (int band = 1; band < TIER_COUNT; band++) {
            int boundary = MIN_Y + band * ColonyGeneratorTunables.TIER_HEIGHT;
            int centreX = (int) Math.round(shaft.axisX());
            int centreZ = (int) Math.round(shaft.axisZ());
            Col centre = col(noise, centreX, centreZ);
            int centreClearance = 0;
            while (air(noise, centre, centreX, boundary + centreClearance, centreZ)) {
                centreClearance++;
            }

            // Headroom across the ramp's annulus, measured as the property that actually
            // matters: every RAMP FLOOR block inside the landing's Y range still carries
            // RAMP_AIR_HEIGHT of air above it. Sampling raw clearance up from the boundary
            // measures nothing -- a column whose ramp floor happens to sit at the boundary
            // reads zero and is perfectly healthy.
            int floorsChecked = 0;
            int pinched = 0;
            for (int step = 0; step < 72; step++) {
                double bearing = step * Math.PI / 36.0;
                for (double radius = ColonyGeneratorTunables.RAMP_CENTER_RADIUS
                        - ColonyGeneratorTunables.RAMP_HALF_WIDTH;
                        radius <= ColonyGeneratorTunables.RAMP_CENTER_RADIUS
                                + ColonyGeneratorTunables.RAMP_HALF_WIDTH; radius += 0.5) {
                    int x = (int) Math.round(shaft.axisX() + Math.cos(bearing) * radius);
                    int z = (int) Math.round(shaft.axisZ() + Math.sin(bearing) * radius);
                    // Rounding to a block can push a sample off the annulus, and a column
                    // outside it reports the landing's own floor disc as SHAFT_SOLID -- which
                    // is a wall footing, not a walkway, and has no headroom requirement. Only
                    // columns provably inside the un-widened annulus are ramp.
                    double actual = Math.hypot(x - shaft.axisX(), z - shaft.axisZ());
                    if (actual < ColonyGeneratorTunables.RAMP_CENTER_RADIUS
                            - ColonyGeneratorTunables.RAMP_HALF_WIDTH
                            || actual > ColonyGeneratorTunables.RAMP_CENTER_RADIUS
                            + ColonyGeneratorTunables.RAMP_HALF_WIDTH) {
                        continue;
                    }
                    Col col = col(noise, x, z);
                    for (int y = boundary - ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT;
                            y < boundary + ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT; y++) {
                        // y == boundary - 1 is the landing's own floor disc, which is forced
                        // solid across the whole radius and is a floor to stand ON, not a
                        // walkway with a headroom contract -- the ramp's next turn routinely
                        // passes a block or two above it. Skipping that one layer costs a
                        // handful of samples out of 1300 and is what keeps this measuring
                        // the spine rather than the room.
                        if (y == boundary - 1
                                || noise.shaftState(col.shafts(), x, y, z) != ColonyNoise.SHAFT_SOLID) {
                            continue;
                        }
                        floorsChecked++;
                        for (int up = 1; up <= ColonyGeneratorTunables.RAMP_AIR_HEIGHT; up++) {
                            if (!air(noise, col, x, y + up, z)) {
                                pinched++;
                                break;
                            }
                        }
                    }
                }
            }

            boolean domed = centreClearance >= ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT;
            boolean headroom = pinched == 0;
            System.out.printf(Locale.ROOT,
                    "  landing at y=%3d: clearance at the centre %d (dome reaches %d); ramp floor blocks in the"
                            + " landing's range %d, of which pinched below %d air: %d%n",
                    boundary, centreClearance, ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT,
                    floorsChecked, ColonyGeneratorTunables.RAMP_AIR_HEIGHT, pinched);
            if (!domed) {
                System.out.println("  FAIL: the dome did not carve to its full height at the centre.");
            }
            if (!headroom) {
                System.out.println("  FAIL: the ramp lost headroom where it crosses this landing.");
            }
            pass &= domed && headroom;

            // Walkable from the ramp, by the shared chamber BFS.
            pass &= chamberWalk(noise, "landing", shaft.axisX(), shaft.axisZ(), shaft.axisX(), shaft.axisZ(),
                    boundary - 1, boundary, ColonyGeneratorTunables.LANDING_RADIUS,
                    boundary + ColonyGeneratorTunables.LANDING_INTERIOR_HEIGHT - 1, "the landing disc");
        }
        return pass;
    }

    /**
     * Whether a shaft is the one belonging to a given cell rather than a ring neighbour.
     * An axis lands within {@code SHAFT_JITTER/2} = 8 blocks of its cell centre, so
     * {@code floorDiv} recovers its cell exactly.
     */
    private static boolean ownsCell(ColonyNoise.Shaft shaft, int cellX, int cellZ) {
        return Math.floorDiv((int) Math.floor(shaft.axisX()), ColonyGeneratorTunables.SHAFT_SPACING) == cellX
                && Math.floorDiv((int) Math.floor(shaft.axisZ()), ColonyGeneratorTunables.SHAFT_SPACING) == cellZ;
    }

    /**
     * How many realized ramps stand inside one colony's outer radius.
     *
     * <p>Counted by sweeping the shaft cells that can reach that radius and asking
     * {@link ColonyNoise#isShaftRealized} directly, rather than by calling
     * {@code shaftsNear} at the centre -- that would only ever see a 3x3 ring, which is a
     * 144-block window inside a 300-block colony.
     */
    private static int countRealizedShaftsIn(ColonyNoise noise, ColonyNoise.Colony colony) {
        int reach = (int) Math.ceil(ColonyGeneratorTunables.COLONY_OUTER_RADIUS
                / ColonyGeneratorTunables.SHAFT_SPACING) + 1;
        int centreCellX = Math.floorDiv((int) Math.round(colony.centreX()), ColonyGeneratorTunables.SHAFT_SPACING);
        int centreCellZ = Math.floorDiv((int) Math.round(colony.centreZ()), ColonyGeneratorTunables.SHAFT_SPACING);
        int count = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int blockX = (centreCellX + dx) * ColonyGeneratorTunables.SHAFT_SPACING;
                int blockZ = (centreCellZ + dz) * ColonyGeneratorTunables.SHAFT_SPACING;
                for (ColonyNoise.Shaft shaft : noise.shaftsNear(blockX, blockZ)) {
                    // Own cell only, as in shafts(), and inside this colony's outer radius.
                    if (ownsCell(shaft, centreCellX + dx, centreCellZ + dz)
                            && Math.hypot(shaft.axisX() - colony.centreX(), shaft.axisZ() - colony.centreZ())
                                    <= ColonyGeneratorTunables.COLONY_OUTER_RADIUS) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // The colony field (Ep2 D1) -- the Thursday go/no-go gate
    // ------------------------------------------------------------------

    /** Cell rings swept by the separation and one-throne-per-colony checks: 13x13 colonies. */
    private static final int COLONY_CELL_SWEEP = 6;
    /** Deterministic sample points for the findability stat. */
    private static final int COLONY_FIND_SAMPLES = 64;
    /** Half-width of the box those points are drawn from, in blocks. */
    private static final int COLONY_FIND_SPREAD = 4000;
    /** Width of one ring of the density profile, and how far out the profile runs. */
    private static final int COLONY_RING_STEP = 25;
    private static final int COLONY_RING_MAX = 250;
    /** XZ / Y strides of the density-profile sampler. */
    private static final int COLONY_PROFILE_XZ_STEP = 2;
    private static final int COLONY_PROFILE_Y_STEP = 4;

    /**
     * The Ep2 acceptance criteria for the colony field, all six in one place.
     *
     * <p>Every one of them is an <b>invariant or a bound</b> rather than a taste judgement,
     * which is the only reason a headless tool can gate an architectural change to how the
     * world looks:
     * <ol>
     *   <li><b>One throne per colony.</b> Counted the way a player meets it -- every throne
     *       whose centre falls inside a colony's outer radius -- rather than by trusting
     *       that one cell produces one chamber. Two thrones in one colony would be two
     *       queens and, given the bar radius, potentially two boss bars.</li>
     *   <li><b>A monotone density profile.</b> "Dense cores, sparse wilds" is exactly the
     *       claim that the blob-chamber carve falls off with distance from a centre and is
     *       gone beyond the outer radius; anything else (a flat field, an inverted one, a
     *       field that never reaches zero) reproduces the mega-nest this package exists to
     *       end.</li>
     *   <li><b>Minimum centre separation.</b> The boss-bar invariant, measured. See
     *       {@link ColonyGeneratorTunables#COLONY_JITTER}.</li>
     *   <li><b>Findability.</b> The counterweight to (2): a world of dense cores is only an
     *       improvement if you can still find one. The 224-block throne grid was chosen from
     *       a measured "nearest queen 130-176 blocks"; 384 has to stay inside "committed
     *       exploration finds it", and the bound asserted is the worst case over deterministic
     *       sample points rather than an average.</li>
     *   <li><b>Core air fraction</b>, reported. This is the measurement that replaces the one
     *       the throne corridor's unforced floor used to rest on.</li>
     *   <li><b>Reachability, re-run under the field</b> for all four chamber kinds. The field
     *       changes what the noise carves around a chamber, so every walkability result from
     *       before it landed is stale evidence.</li>
     * </ol>
     */
    private static void colonies(ColonyNoise noise) {
        System.out.printf(Locale.ROOT,
                "%n== colony field: spacing %d, jitter %.0f, core r=%.0f, outer r=%.0f, chamber gate f>%.2f ==%n",
                ColonyGeneratorTunables.COLONY_SPACING, ColonyGeneratorTunables.COLONY_JITTER,
                ColonyGeneratorTunables.COLONY_CORE_RADIUS, ColonyGeneratorTunables.COLONY_OUTER_RADIUS,
                ColonyGeneratorTunables.CHAMBER_ELIGIBILITY_MIN_F);

        boolean green = colonySeparation(noise);
        green &= colonyFindability(noise);
        green &= colonyThrones(noise);
        green &= colonyChamberCensus(noise);
        green &= colonyDensityProfile(noise);
        green &= colonyChamberWalks(noise);

        System.out.println(green
                ? "\n  COLONY SECTION: ALL PASS"
                : "\n  COLONY SECTION: FAILED -- see the FAIL lines above.");
    }

    /** (3) No two colony centres are closer than {@code COLONY_SPACING - COLONY_JITTER}. */
    private static boolean colonySeparation(ColonyNoise noise) {
        double required = ColonyGeneratorTunables.COLONY_SPACING - ColonyGeneratorTunables.COLONY_JITTER;
        double minSeparation = Double.MAX_VALUE;
        int pairs = 0;
        for (int cx = -COLONY_CELL_SWEEP; cx <= COLONY_CELL_SWEEP; cx++) {
            for (int cz = -COLONY_CELL_SWEEP; cz <= COLONY_CELL_SWEEP; cz++) {
                ColonyNoise.Colony here = noise.colonyCenterForCell(cx, cz);
                // Every neighbour once: right, down, and both diagonals.
                int[][] offsets = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
                for (int[] offset : offsets) {
                    ColonyNoise.Colony other = noise.colonyCenterForCell(cx + offset[0], cz + offset[1]);
                    minSeparation = Math.min(minSeparation,
                            Math.hypot(here.centreX() - other.centreX(), here.centreZ() - other.centreZ()));
                    pairs++;
                }
            }
        }
        // Deliberately not quoting QueenAntEntity#BOSS_BAR_RADIUS_EXIT by symbol: this tool
        // runs without Bootstrap.bootStrap(), and merely referencing an entity class would
        // reach BuiltInRegistries and die "Not bootstrapped" (banked in
        // docs/gotchas/worldgen.md). The invariant it has to clear -- 288 comfortably above
        // twice that radius -- is derived in COLONY_JITTER's javadoc.
        System.out.printf(Locale.ROOT,
                "%n  centre separation over %d neighbouring pairs: minimum %.1f blocks (required >= %.0f"
                        + " = COLONY_SPACING - COLONY_JITTER, the boss-bar separation invariant)%n",
                pairs, minSeparation, required);
        boolean pass = minSeparation >= required;
        System.out.println(pass
                ? "  PASS: two simultaneous queen boss bars are geometrically impossible."
                : "  FAIL: two colonies came closer than the separation invariant allows.");
        return pass;
    }

    /**
     * (4) From an arbitrary point, how far is the nearest colony?
     *
     * <p>The bound is 290 blocks, which is the jittered grid's own worst case -- the far
     * corner of a cell whose centre jittered away from you, {@code (COLONY_SPACING/2)*sqrt(2)
     * + COLONY_JITTER/2} = {@code 160*sqrt(2) + 48} = 274 -- with a little air in it. The
     * points are drawn from a fixed seed rather than a lattice on purpose: a lattice with any
     * relationship to {@code COLONY_SPACING} would sample the same phase of every cell and
     * could miss the corner case entirely.
     *
     * <p>Round 2 recomputed it from the new spacing rather than leaving the old 340 in place.
     * A bound that no longer tracks its derivation is worse than no bound: it would have gone
     * on passing while saying nothing, and this is the check that a colony is findable at all.
     */
    private static boolean colonyFindability(ColonyNoise noise) {
        RandomSource points = new XoroshiroRandomSource(20260818L);
        double worst = 0.0;
        double sum = 0.0;
        int worstX = 0;
        int worstZ = 0;
        for (int i = 0; i < COLONY_FIND_SAMPLES; i++) {
            int x = points.nextInt(COLONY_FIND_SPREAD * 2) - COLONY_FIND_SPREAD;
            int z = points.nextInt(COLONY_FIND_SPREAD * 2) - COLONY_FIND_SPREAD;
            ColonyNoise.Colony nearest = noise.nearestColony(x, z);
            double distance = Math.hypot(x - nearest.centreX(), z - nearest.centreZ());
            sum += distance;
            if (distance > worst) {
                worst = distance;
                worstX = x;
                worstZ = z;
            }
        }
        double bound = 290.0;
        System.out.printf(Locale.ROOT,
                "%n  findability over %d sample points in +-%d blocks: mean %.1f, worst %.1f (at %d, %d),"
                        + " bound %.0f%n",
                COLONY_FIND_SAMPLES, COLONY_FIND_SPREAD, sum / COLONY_FIND_SAMPLES, worst, worstX, worstZ, bound);
        boolean pass = worst <= bound;
        System.out.println(pass
                ? "  PASS: a colony is always within committed-exploration range."
                : "  FAIL: a sample point was further from a colony than the bound allows.");
        return pass;
    }

    /** Chambers of one kind a colony must hold for the nest to read as a nest. */
    private static final int CENSUS_MIN = 3;
    /** And the ceiling, above which a colony is a warren rather than a colony. */
    private static final int CENSUS_MAX = 9;

    /**
     * (5) How many nurseries, gardens and larders does one colony actually contain?
     *
     * <p>Added in play-test round 2, and it is the measurement the round's whole chamber-density
     * argument turns on. Before it existed the only available number was the per-cell gate rate
     * ("88 of 289 sampled cells generate a chamber"), which answers a question nobody has:
     * a player does not walk a grid of cells, they walk into a colony and count rooms. Reading
     * one off the other means multiplying by {@code (COLONY_SPACING / CHAMBER_SPACING)^2} and
     * hoping the sample straddled colonies evenly -- which is exactly the kind of derived
     * statistic that hid the fact that colonies already held about five of each kind while the
     * play-test was reporting chambers as rare.
     *
     * <p>Counted directly instead. Every in-colony chamber belongs to exactly one colony and
     * the assignment is unambiguous: the field is zero beyond
     * {@link ColonyGeneratorTunables#COLONY_OUTER_RADIUS} = 128 and two centres are at least
     * {@code COLONY_SPACING - COLONY_JITTER} = 224 apart, so the eligibility discs cannot
     * overlap. A chamber centre lands within
     * {@code SHAFT_SPACING/2 + SHAFT_JITTER/2 + APPROACH_DISTANCE} = 56 blocks of its cell
     * centre, so every chamber inside a colony has its cell centre within {@code 128 + 56} =
     * 184 blocks of that colony centre -- {@link #CENSUS_CELL_RING} = 2 rings of 96 reaches
     * 192 and covers it. The round-2 radius decision cut that margin from 24 blocks to 8, so
     * a third ring becomes necessary the moment the outer radius passes 136.
     *
     * <p>The band is asserted on the <b>mean</b> and the spread is printed. Holding every
     * individual colony to a floor would be asserting something the design does not promise --
     * a colony whose centre jittered next to a sparse patch of the ramp grid legitimately gets
     * fewer rooms -- while the mean is what "a colony carries four to six of each" actually
     * claims.
     */
    private static boolean colonyChamberCensus(ColonyNoise noise) {
        String[] kinds = {"nursery", "garden", "larder"};
        int[][] counts = new int[3][(2 * COLONY_CELL_SWEEP + 1) * (2 * COLONY_CELL_SWEEP + 1)];
        int colonies = 0;
        for (int cx = -COLONY_CELL_SWEEP; cx <= COLONY_CELL_SWEEP; cx++) {
            for (int cz = -COLONY_CELL_SWEEP; cz <= COLONY_CELL_SWEEP; cz++) {
                ColonyNoise.Colony colony = noise.colonyCenterForCell(cx, cz);
                int cellX = Math.floorDiv((int) Math.round(colony.centreX()),
                        ColonyGeneratorTunables.NURSERY_SPACING);
                int cellZ = Math.floorDiv((int) Math.round(colony.centreZ()),
                        ColonyGeneratorTunables.NURSERY_SPACING);
                for (int dx = -CENSUS_CELL_RING; dx <= CENSUS_CELL_RING; dx++) {
                    for (int dz = -CENSUS_CELL_RING; dz <= CENSUS_CELL_RING; dz++) {
                        int bx = (cellX + dx) * ColonyGeneratorTunables.NURSERY_SPACING;
                        int bz = (cellZ + dz) * ColonyGeneratorTunables.NURSERY_SPACING;
                        ColonyNoise.Nursery nursery = noise.nurseriesNear(bx, bz)[4];
                        if (nursery.inColony() && belongsTo(colony, nursery.centreX(), nursery.centreZ())) {
                            counts[0][colonies]++;
                        }
                        ColonyNoise.Garden garden = noise.gardensNear(bx, bz)[4];
                        if (garden.inColony() && belongsTo(colony, garden.centreX(), garden.centreZ())) {
                            counts[1][colonies]++;
                        }
                        ColonyNoise.Larder larder = noise.lardersNear(bx, bz)[4];
                        if (larder.inColony() && belongsTo(colony, larder.centreX(), larder.centreZ())) {
                            counts[2][colonies]++;
                        }
                    }
                }
                colonies++;
            }
        }

        System.out.printf(Locale.ROOT,
                "%n  chambers per colony over %d colonies (%d cells each within reach), acceptance band"
                        + " [%d, %d] on the mean:%n",
                colonies, (2 * CENSUS_CELL_RING + 1) * (2 * CENSUS_CELL_RING + 1), CENSUS_MIN, CENSUS_MAX);
        boolean pass = true;
        for (int kind = 0; kind < kinds.length; kind++) {
            int[] mine = Arrays.copyOf(counts[kind], colonies);
            Arrays.sort(mine);
            double mean = 0.0;
            for (int value : mine) {
                mean += value;
            }
            mean /= colonies;
            boolean ok = mean >= CENSUS_MIN && mean <= CENSUS_MAX;
            pass &= ok;
            System.out.printf(Locale.ROOT,
                    "    %-8s mean %4.2f   min %d  median %d  max %d   %s%n",
                    mine.length == 0 ? kinds[kind] : kinds[kind], mean, mine[0], mine[colonies / 2],
                    mine[colonies - 1], ok ? "" : "<-- OUTSIDE THE BAND");
        }
        System.out.println(pass
                ? "  PASS: a colony carries enough of every chamber kind to read as a working nest."
                : "  FAIL: a chamber kind's per-colony mean is outside [" + CENSUS_MIN + ", " + CENSUS_MAX + "].");
        return pass;
    }

    /**
     * Rings of 96-block chamber cells scanned around each colony centre by
     * {@link #colonyChamberCensus}. Two: see that method's own derivation ({@code 128 + 56}
     * = 184 blocks of reach against the 192 two rings cover).
     */
    private static final int CENSUS_CELL_RING = 2;

    /** Whether a chamber centre falls inside {@code colony}'s eligibility disc. */
    private static boolean belongsTo(ColonyNoise.Colony colony, double x, double z) {
        return Math.hypot(x - colony.centreX(), z - colony.centreZ())
                <= ColonyGeneratorTunables.COLONY_OUTER_RADIUS;
    }

    /** (1) Exactly one throne per colony, and it sits inside the core. */
    private static boolean colonyThrones(ColonyNoise noise) {
        int colonies = 0;
        int wrongCount = 0;
        int outsideCore = 0;
        double worstOffset = 0.0;
        for (int cx = -COLONY_CELL_SWEEP; cx <= COLONY_CELL_SWEEP; cx++) {
            for (int cz = -COLONY_CELL_SWEEP; cz <= COLONY_CELL_SWEEP; cz++) {
                ColonyNoise.Colony colony = noise.colonyCenterForCell(cx, cz);
                colonies++;
                int inThisColony = 0;
                for (ColonyNoise.Throne throne : noise.thronesNear((int) Math.round(colony.centreX()),
                        (int) Math.round(colony.centreZ()))) {
                    double offset = Math.hypot(throne.centreX() - colony.centreX(),
                            throne.centreZ() - colony.centreZ());
                    if (offset <= ColonyGeneratorTunables.COLONY_OUTER_RADIUS) {
                        inThisColony++;
                        worstOffset = Math.max(worstOffset, offset);
                        if (offset > ColonyGeneratorTunables.COLONY_CORE_RADIUS) {
                            outsideCore++;
                        }
                    }
                }
                if (inThisColony != 1) {
                    wrongCount++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "%n  thrones over %d colonies: %d colonies without exactly one, %d thrones outside their core;"
                        + " worst centre offset %.1f blocks (core r=%.0f)%n",
                colonies, wrongCount, outsideCore, worstOffset, ColonyGeneratorTunables.COLONY_CORE_RADIUS);
        boolean pass = wrongCount == 0 && outsideCore == 0;
        System.out.println(pass
                ? "  PASS: one throne per colony, every one of them inside the core."
                : "  FAIL: the throne grid and the colony grid disagree.");
        return pass;
    }

    /**
     * (2) and (5): the density profile in rings out from a colony centre, plus the core air
     * fraction.
     *
     * <p>"Chamber air" here is the blob carve specifically -- {@link
     * ColonyNoise#isChamberCarved} -- because that is the thing the field modulates. Total
     * air is reported alongside it and deliberately does <em>not</em> fall to zero: the worm
     * tunnels and the connectivity ramps are global by design, and a profile where total air
     * went to zero in the wilds would mean the no-softlock spine had been modulated too.
     *
     * <p>Columns whose nearest colony is <em>not</em> the anchor are excluded, and that is a
     * correctness fix rather than tidying. Two centres can be as close as 288 blocks, so a
     * ring at r=225 already reaches into a neighbour's core (288 - 100 = 188 blocks out) and
     * the profile turns back up -- seed 42 measured 0.647% chamber air in the r225-249 ring
     * against 0.000% at r150. Restricting to the anchor's own Voronoi cell makes this the
     * falloff of <em>one</em> colony, which is the thing being asserted.
     */
    private static boolean colonyDensityProfile(ColonyNoise noise) {
        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        int rings = COLONY_RING_MAX / COLONY_RING_STEP;
        long[] samples = new long[rings];
        long[] chamberAir = new long[rings];
        long[] anyAir = new long[rings];
        long[] coreByTier = new long[TIER_COUNT];
        long[] coreTotalByTier = new long[TIER_COUNT];

        for (int dx = -COLONY_RING_MAX; dx <= COLONY_RING_MAX; dx += COLONY_PROFILE_XZ_STEP) {
            for (int dz = -COLONY_RING_MAX; dz <= COLONY_RING_MAX; dz += COLONY_PROFILE_XZ_STEP) {
                double distance = Math.hypot(dx, dz);
                int ring = (int) (distance / COLONY_RING_STEP);
                if (ring >= rings) {
                    continue;
                }
                int x = anchorX + dx;
                int z = anchorZ + dz;
                ColonyNoise.Colony owner = noise.nearestColony(x, z);
                if (owner.centreX() != anchor.centreX() || owner.centreZ() != anchor.centreZ()) {
                    continue;
                }
                Col col = col(noise, x, z);
                for (int y = FLOOR_TOP; y < CEILING_BOTTOM; y += COLONY_PROFILE_Y_STEP) {
                    samples[ring]++;
                    if (noise.isChamberCarved(col.field(), x, y, z)) {
                        chamberAir[ring]++;
                    }
                    boolean isAir = air(noise, col, x, y, z);
                    if (isAir) {
                        anyAir[ring]++;
                    }
                    if (distance < ColonyGeneratorTunables.COLONY_CORE_RADIUS) {
                        int tier = tierIndex(y);
                        coreTotalByTier[tier]++;
                        if (isAir) {
                            coreByTier[tier]++;
                        }
                    }
                }
            }
        }

        System.out.printf(Locale.ROOT,
                "%n  density profile out from the colony at (%d, %d), %d-block rings"
                        + " (columns nearer another colony are excluded):%n",
                anchorX, anchorZ, COLONY_RING_STEP);
        System.out.println("    ring        samples  chamberAir%   anyAir%   meanF");
        double[] chamberFraction = new double[rings];
        for (int ring = 0; ring < rings; ring++) {
            chamberFraction[ring] = samples[ring] == 0 ? 0.0 : (double) chamberAir[ring] / samples[ring];
            double mid = (ring + 0.5) * COLONY_RING_STEP;
            System.out.printf(Locale.ROOT, "    r%3d-%3d  %9d  %10.3f%%  %7.3f%%  %6.3f%n",
                    ring * COLONY_RING_STEP, (ring + 1) * COLONY_RING_STEP - 1, samples[ring],
                    100.0 * chamberFraction[ring],
                    samples[ring] == 0 ? 0.0 : 100.0 * anyAir[ring] / samples[ring],
                    ColonyGeneratorTunables.colonyFalloff(mid));
        }

        int coreRings = (int) Math.ceil(ColonyGeneratorTunables.COLONY_CORE_RADIUS / COLONY_RING_STEP);
        int outerRing = (int) Math.ceil(ColonyGeneratorTunables.COLONY_OUTER_RADIUS / COLONY_RING_STEP);
        long coreSamples = 0;
        long coreChamber = 0;
        for (int ring = 0; ring < coreRings; ring++) {
            coreSamples += samples[ring];
            coreChamber += chamberAir[ring];
        }
        double coreValue = coreSamples == 0 ? 0.0 : (double) coreChamber / coreSamples;

        boolean monotone = true;
        for (int ring = coreRings; ring + 1 < rings; ring++) {
            if (chamberFraction[ring + 1] > chamberFraction[ring]) {
                monotone = false;
            }
        }
        boolean fallsOff = chamberFraction[coreRings] < coreValue;
        long beyondSamples = 0;
        long beyondChamber = 0;
        for (int ring = outerRing; ring < rings; ring++) {
            beyondSamples += samples[ring];
            beyondChamber += chamberAir[ring];
        }
        double beyondValue = beyondSamples == 0 ? 0.0 : (double) beyondChamber / beyondSamples;

        System.out.printf(Locale.ROOT,
                "  core (r<%.0f) chamber air %.3f%%; beyond the outer radius %.3f%% = %.1f%% of it%n",
                ColonyGeneratorTunables.COLONY_CORE_RADIUS, 100.0 * coreValue, 100.0 * beyondValue,
                coreValue == 0.0 ? 0.0 : 100.0 * beyondValue / coreValue);
        boolean pass = coreValue > 0.0 && monotone && fallsOff && beyondValue < 0.10 * coreValue;
        System.out.println(pass
                ? "  PASS: density is flat across the core, falls monotonically through the ring, and is under"
                        + " a tenth of the core value beyond the outer radius."
                : "  FAIL: the density profile is not the falloff the field claims"
                        + " (core>0 " + (coreValue > 0.0) + ", monotone " + monotone
                        + ", falls " + fallsOff + ").");

        // (5) The measurement the forced throne-corridor floor rests on.
        System.out.println("  core air fraction by tier (this is what the 17% Royal Depths figure becomes"
                + " inside a colony):");
        for (int tier = TIER_COUNT - 1; tier >= 0; tier--) {
            System.out.printf(Locale.ROOT, "    tier %d %-16s %6.2f%% air%n", tier, tierName(tier),
                    coreTotalByTier[tier] == 0 ? 0.0 : 100.0 * coreByTier[tier] / coreTotalByTier[tier]);
        }
        return pass;
    }

    /** (6) Reachability re-run under the field, for all four chamber kinds. */
    private static boolean colonyChamberWalks(ColonyNoise noise) {
        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        System.out.printf(Locale.ROOT, "%n  chamber reachability under the field, colony at (%d, %d):%n",
                anchorX, anchorZ);

        ColonyNoise.Throne throne = noise.nearestThrone(anchorX, anchorZ);
        boolean pass = chamberWalk(noise, "throne", throne.centreX(), throne.centreZ(), throne.axisX(),
                throne.axisZ(), throne.floorY(), throne.floorY() + ColonyGeneratorTunables.THRONE_DAIS_HEIGHT + 1,
                ColonyGeneratorTunables.THRONE_RADIUS,
                throne.floorY() + ColonyGeneratorTunables.THRONE_WALL_HEIGHT
                        + ColonyGeneratorTunables.THRONE_DOME_HEIGHT,
                "the queen's dais");

        ColonyNoise.Nursery nursery = noise.nearestNursery(anchorX, anchorZ);
        pass &= chamberWalk(noise, "nursery", nursery.centreX(), nursery.centreZ(), nursery.axisX(),
                nursery.axisZ(), nursery.floorY(), nursery.floorY() + 1, ColonyGeneratorTunables.NURSERY_RADIUS,
                nursery.floorY() + ColonyGeneratorTunables.NURSERY_WALL_HEIGHT
                        + ColonyGeneratorTunables.NURSERY_DOME_HEIGHT,
                "the brood floor");

        ColonyNoise.Garden garden = noise.nearestGarden(anchorX, anchorZ);
        pass &= chamberWalk(noise, "garden", garden.centreX(), garden.centreZ(), garden.axisX(),
                garden.axisZ(), garden.floorY(), garden.floorY() + 1, ColonyGeneratorTunables.GARDEN_RADIUS,
                garden.floorY() + ColonyGeneratorTunables.GARDEN_WALL_HEIGHT
                        + ColonyGeneratorTunables.GARDEN_DOME_HEIGHT,
                "the garden floor");

        ColonyNoise.Larder larder = noise.nearestLarder(anchorX, anchorZ);
        pass &= chamberWalk(noise, "larder", larder.centreX(), larder.centreZ(), larder.axisX(),
                larder.axisZ(), larder.floorY(), larder.floorY() + 1, ColonyGeneratorTunables.LARDER_RADIUS,
                larder.floorY() + ColonyGeneratorTunables.LARDER_WALL_HEIGHT
                        + ColonyGeneratorTunables.LARDER_DOME_HEIGHT,
                "the larder floor");

        System.out.println(pass
                ? "  PASS: all four chamber kinds join the connectivity spine on foot under the colony field."
                : "  FAIL: a chamber kind stopped being reachable under the colony field.");
        return pass;
    }

    /**
     * M7's acceptance criterion: the queen's chamber has to be somewhere a player can
     * <i>walk into</i>, and the boss is the one piece of content that is unrecoverable if
     * it generates sealed.
     *
     * <p>Reports every chamber in the 3x3 cells around the origin (position, floor Y, the
     * ramp it hangs off, distance from spawn), then runs the same symmetric walkability BFS
     * {@link #connectivity} uses over a box containing one chamber and its ramp -- seeded
     * from the ramp's walkway only. If the dais top is reached, the room is enterable on
     * foot from the connectivity spine, and the spine is already known to reach the surface.
     */
    private static void thrones(ColonyNoise noise) {
        System.out.printf(Locale.ROOT,
                "%nthrone chambers (one per %d-block COLONY cell, radius %.0f, interior %d tall):%n",
                ColonyGeneratorTunables.COLONY_SPACING, ColonyGeneratorTunables.THRONE_RADIUS,
                ColonyGeneratorTunables.THRONE_WALL_HEIGHT + ColonyGeneratorTunables.THRONE_DOME_HEIGHT);

        ColonyNoise.Throne[] near = noise.thronesNear(0, 0);
        ColonyNoise.Throne closest = near[0];
        for (ColonyNoise.Throne throne : near) {
            double distance = Math.hypot(throne.centreX(), throne.centreZ());
            System.out.printf(Locale.ROOT,
                    "  centre (%7.1f, %7.1f)  floorY %3d  ramp axis (%7.1f, %7.1f)  %6.1f blocks from origin%n",
                    throne.centreX(), throne.centreZ(), throne.floorY(), throne.axisX(), throne.axisZ(), distance);
            if (distance < Math.hypot(closest.centreX(), closest.centreZ())) {
                closest = throne;
            }
        }
        throneBand(noise);
        throneWalk(noise, closest);
    }

    /**
     * The throne's band containment -- added in play-test round 2, and added because its
     * absence was caught rather than because it was tidy.
     *
     * <p>The nursery, garden and larder sections have each asserted "this room sits wholly
     * inside its tier" since they were written. The throne never did: {@link #thrones} checked
     * only that its dais is walkable from the ramp. That gap was measured, not supposed --
     * a trial run of the rejected {@code TIER_HEIGHT = 32} dial put throne floors in
     * {@code [8, 32]} against a band ceiling of 32, so every one of them punched up into the
     * Nurseries, and this section stayed green throughout while the other three went red.
     *
     * <p>The throne is the <em>binding</em> case for {@link ColonyGeneratorTunables#TIER_HEIGHT}
     * (it is the tallest room in the dimension, and its one block of margin is what makes 48 a
     * floor -- see {@code CHAMBER_FLOOR_MIN_Y_BY_TIER}'s javadoc), so it was exactly the wrong
     * one to leave unchecked. Same shape as the other three: floor minus shell above the
     * band's bottom, shell top below the next boundary.
     */
    private static boolean throneBand(ColonyNoise noise) {
        int shell = (int) Math.ceil(ColonyGeneratorTunables.THRONE_SHELL_THICKNESS);
        int roomTop = ColonyGeneratorTunables.THRONE_WALL_HEIGHT
                + ColonyGeneratorTunables.THRONE_DOME_HEIGHT + shell;
        int inBand = 0;
        int total = 0;
        int minFloor = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        for (int cx = -COLONY_CELL_SWEEP; cx <= COLONY_CELL_SWEEP; cx++) {
            for (int cz = -COLONY_CELL_SWEEP; cz <= COLONY_CELL_SWEEP; cz++) {
                ColonyNoise.Colony colony = noise.colonyCenterForCell(cx, cz);
                ColonyNoise.Throne throne = noise.nearestThrone((int) Math.round(colony.centreX()),
                        (int) Math.round(colony.centreZ()));
                int top = throne.floorY() + roomTop;
                total++;
                minFloor = Math.min(minFloor, throne.floorY());
                maxTop = Math.max(maxTop, top);
                if (throne.floorY() >= ColonyGeneratorTunables.CHAMBER_FLOOR_MIN_Y_BY_TIER[0]
                        && throne.floorY() - shell >= tierMinY(0) && top < tierMaxY(0)) {
                    inBand++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  %d thrones sampled: floor y in [%d, %d], highest shell top y=%d; %d of %d wholly inside "
                        + "the Royal Depths band y[%d,%d)  (margin to the boundary: %d)%n",
                total, minFloor, maxTop - roomTop, maxTop, inBand, total, tierMinY(0), tierMaxY(0),
                tierMaxY(0) - maxTop);
        boolean pass = inBand == total;
        System.out.println(pass
                ? "  PASS: every sampled throne sits wholly inside the Royal Depths."
                : "  FAIL: a throne crosses a tier boundary -- the tallest room in the dimension "
                        + "does not fit its band.");
        return pass;
    }

    private static void throneWalk(ColonyNoise noise, ColonyNoise.Throne throne) {
        chamberWalk(noise, "chamber", throne.centreX(), throne.centreZ(), throne.axisX(), throne.axisZ(),
                throne.floorY(), throne.floorY() + ColonyGeneratorTunables.THRONE_DAIS_HEIGHT + 1,
                ColonyGeneratorTunables.THRONE_RADIUS,
                throne.floorY() + ColonyGeneratorTunables.THRONE_WALL_HEIGHT
                        + ColonyGeneratorTunables.THRONE_DOME_HEIGHT,
                "the queen's dais");
    }

    /**
     * The shared acceptance test for both kinds of carved chamber: seeded from the
     * connectivity ramp's own walkway, does the symmetric one-block-step walk reach the spot
     * inside the room that matters?
     *
     * <p>Both chambers hang off a ramp by the same construction, so they get the same check;
     * only the target differs (the queen's plinth for a throne, the brood floor for a
     * nursery). Seeding from the ramp walkway alone -- rather than from any standable block
     * near the room -- is what makes a PASS mean "joins the spine", which is the property
     * that matters, since the spine is already known to reach the surface.
     */
    private static boolean chamberWalk(ColonyNoise noise, String label, double centreXd, double centreZd,
            double axisXd, double axisZd, int floorY, int targetY, double roomRadius, int interiorTop,
            String targetLabel) {
        int centreX = (int) Math.round(centreXd);
        int centreZ = (int) Math.round(centreZd);
        int axisX = (int) Math.round(axisXd);
        int axisZ = (int) Math.round(axisZd);
        int minX = Math.min(centreX, axisX) - 24;
        int minZ = Math.min(centreZ, axisZ) - 24;
        int span = Math.max(Math.max(centreX, axisX) - minX, Math.max(centreZ, axisZ) - minZ) + 25;

        boolean[] air = new boolean[span * span * HEIGHT];
        for (int ix = 0; ix < span; ix++) {
            int x = minX + ix;
            for (int iz = 0; iz < span; iz++) {
                int z = minZ + iz;
                Col col = col(noise, x, z);
                for (int y = 0; y < HEIGHT; y++) {
                    air[(ix * HEIGHT + y) * span + iz] = air(noise, col, x, MIN_Y + y, z);
                }
            }
        }

        boolean[] standable = new boolean[air.length];
        for (int ix = 0; ix < span; ix++) {
            for (int iz = 0; iz < span; iz++) {
                for (int y = 1; y < HEIGHT - 1; y++) {
                    int i = (ix * HEIGHT + y) * span + iz;
                    if (air[i] && air[i + span] && !air[i - span]) {
                        standable[i] = true;
                    }
                }
            }
        }

        // Seed from the ramp's own walkway only: the question is whether the chamber joins
        // the connectivity spine, not whether it joins some nearby noise pocket.
        boolean[] seen = new boolean[air.length];
        Deque<Integer> queue = new ArrayDeque<>();
        int seeds = 0;
        for (int ix = 0; ix < span; ix++) {
            int x = minX + ix;
            for (int iz = 0; iz < span; iz++) {
                int z = minZ + iz;
                if (Math.hypot(x - axisXd, z - axisZd) > ColonyGeneratorTunables.SHAFT_MAX_REACH) {
                    continue;
                }
                ColonyNoise.Shaft[] shafts = noise.shaftsForColumn(
                        noise.shaftsNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
                for (int y = 1; y < HEIGHT - 1; y++) {
                    int i = (ix * HEIGHT + y) * span + iz;
                    if (standable[i] && !seen[i]
                            && noise.shaftState(shafts, x, MIN_Y + y, z) == ColonyNoise.SHAFT_AIR) {
                        seen[i] = true;
                        queue.add(i);
                        seeds++;
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int iz = cur % span;
            int y = (cur / span) % HEIGHT;
            int ix = cur / (span * HEIGHT);
            throneStep(queue, seen, standable, span, ix + 1, y, iz);
            throneStep(queue, seen, standable, span, ix - 1, y, iz);
            throneStep(queue, seen, standable, span, ix, y, iz + 1);
            throneStep(queue, seen, standable, span, ix, y, iz - 1);
        }

        int targetIndex = ((centreX - minX) * HEIGHT + (targetY - MIN_Y)) * span + (centreZ - minZ);
        boolean reached = seen[targetIndex];

        // Count only the room's own Y band, so the number is about the chamber rather than
        // about every noise pocket that happens to share its footprint.
        int roomFloors = 0;
        int roomReached = 0;
        for (int ix = 0; ix < span; ix++) {
            int x = minX + ix;
            for (int iz = 0; iz < span; iz++) {
                int z = minZ + iz;
                if (Math.hypot(x - centreXd, z - centreZd) > roomRadius) {
                    continue;
                }
                for (int y = floorY + 1; y <= interiorTop; y++) {
                    int i = (ix * HEIGHT + (y - MIN_Y)) * span + iz;
                    if (standable[i]) {
                        roomFloors++;
                        if (seen[i]) {
                            roomReached++;
                        }
                    }
                }
            }
        }

        System.out.printf(Locale.ROOT,
                "  nearest %s: centre (%d, %d) floor y=%d, target y=%d%n"
                        + "  ramp walkway seeds: %d; standable floors in the room's own band: %d, reached: %d%n",
                label, centreX, centreZ, floorY, targetY, seeds, roomFloors, roomReached);
        System.out.println(reached
                ? "  PASS: " + targetLabel
                        + " is walkable from the connectivity ramp (edges are symmetric, so also back out)."
                : "  FAIL: the " + label + " does not join the ramp on foot.");
        return reached;
    }

    private static void throneStep(Deque<Integer> queue, boolean[] seen, boolean[] standable, int span,
            int ix, int y, int iz) {
        if (ix < 0 || iz < 0 || ix >= span || iz >= span) {
            return;
        }
        for (int dy = -1; dy <= 1; dy++) {
            int ny = y + dy;
            if (ny < 1 || ny >= HEIGHT - 1) {
                continue;
            }
            int i = (ix * HEIGHT + ny) * span + iz;
            if (standable[i] && !seen[i]) {
                seen[i] = true;
                queue.add(i);
            }
        }
    }

    /** Walk into a neighbouring column, allowing at most a one-block rise or fall. */
    private static void step(Deque<Integer> queue, boolean[] seen, boolean[] standable, int ix, int y, int iz) {
        if (ix < 0 || iz < 0 || ix >= SLAB || iz >= SLAB) {
            return;
        }
        for (int dy = -1; dy <= 1; dy++) {
            int ny = y + dy;
            if (ny < 0 || ny >= HEIGHT) {
                continue;
            }
            int i = index(ix, ny, iz);
            if (standable[i] && !seen[i]) {
                seen[i] = true;
                queue.add(i);
            }
        }
    }

    private static int index(int ix, int y, int iz) {
        return (ix * HEIGHT + y) * SLAB + iz;
    }

    // ------------------------------------------------------------------
    // Play-test round 1 sections
    // ------------------------------------------------------------------

    /**
     * Everything {@link ColonyNoise#isAir} needs about one column, resolved once: the five
     * feature arrays plus the colony field, which is XZ-only and so is a per-column constant
     * exactly like they are.
     */
    private record Col(double field, ColonyNoise.Shaft[] shafts, ColonyNoise.Throne[] thrones,
            ColonyNoise.Nursery[] nurseries, ColonyNoise.Garden[] gardens, ColonyNoise.Larder[] larders) {
    }

    private static Col col(ColonyNoise noise, int x, int z) {
        int chunkX = x - Math.floorMod(x, 16);
        int chunkZ = z - Math.floorMod(z, 16);
        return new Col(noise.colonyField(x, z),
                noise.shaftsForColumn(noise.shaftsNear(chunkX, chunkZ), x, z),
                noise.thronesForColumn(noise.thronesNear(chunkX, chunkZ), x, z),
                noise.nurseriesForColumn(noise.nurseriesNear(chunkX, chunkZ), x, z),
                noise.gardensForColumn(noise.gardensNear(chunkX, chunkZ), x, z),
                noise.lardersForColumn(noise.lardersNear(chunkX, chunkZ), x, z));
    }

    private static boolean air(ColonyNoise noise, Col col, int x, int y, int z) {
        return noise.isAir(col.field(), col.shafts(), col.thrones(), col.nurseries(), col.gardens(), col.larders(),
                x, y, z);
    }

    /**
     * The colony centre nearest the origin -- the probe's anchor for every section whose
     * subject only exists inside a nest.
     *
     * <p>Sampling at (0, 0) used to be arbitrary-but-fine, because the dimension was uniform.
     * Since the colony field it is a specific and misleading choice: on a 384-block grid the
     * origin is over 200 blocks from the nearest centre on essentially every seed, i.e. out
     * in the wilds, where there are no chambers, no comb and few ants by design. A section
     * that reported "0 of 9 chambers walkable" from there would be measuring the gate rather
     * than the thing it was written to check.
     */
    private static ColonyNoise.Colony anchor(ColonyNoise noise) {
        return noise.nearestColony(0.0, 0.0);
    }

    /**
     * Nursery chambers: how many, where, and does one join the ramp on foot?
     *
     * <p>Frequency is reported against the throne chamber rather than in the abstract,
     * because "considerably more frequent than the throne" is the actual requirement. The
     * walkability check is the same one the throne gets -- it is the only thing standing
     * between "a brood room" and "2-4 larvae sealed in a bubble of soil".
     */
    private static void nurseries(ColonyNoise noise) {
        System.out.printf(Locale.ROOT,
                "%nnursery chambers (one per %d-block cell, radius %.0f, interior %d tall):%n",
                ColonyGeneratorTunables.NURSERY_SPACING, ColonyGeneratorTunables.NURSERY_RADIUS,
                ColonyGeneratorTunables.NURSERY_WALL_HEIGHT + ColonyGeneratorTunables.NURSERY_DOME_HEIGHT);

        double perThrone = Math.pow((double) ColonyGeneratorTunables.COLONY_SPACING
                / ColonyGeneratorTunables.NURSERY_SPACING, 2.0);
        System.out.printf(Locale.ROOT,
                "  grid density: %.2f cells per 1000x1000 blocks, i.e. %.1f cells per throne chamber"
                        + " (before the colony gate)%n",
                1.0e6 / Math.pow(ColonyGeneratorTunables.NURSERY_SPACING, 2.0), perThrone);

        // Every chamber in a wide sample, checked against the band it must stay inside.
        int cells = 8;
        int inBand = 0;
        int total = 0;
        int generated = 0;
        int minFloor = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int shell = (int) Math.ceil(ColonyGeneratorTunables.NURSERY_SHELL_THICKNESS);
        for (int cx = -cells; cx <= cells; cx++) {
            for (int cz = -cells; cz <= cells; cz++) {
                ColonyNoise.Nursery[] near = noise.nurseriesNear(cx * ColonyGeneratorTunables.NURSERY_SPACING,
                        cz * ColonyGeneratorTunables.NURSERY_SPACING);
                ColonyNoise.Nursery nursery = near[4];
                int top = nursery.floorY() + ColonyGeneratorTunables.NURSERY_WALL_HEIGHT
                        + ColonyGeneratorTunables.NURSERY_DOME_HEIGHT + shell;
                total++;
                if (nursery.inColony()) {
                    generated++;
                }
                minFloor = Math.min(minFloor, nursery.floorY());
                maxTop = Math.max(maxTop, top);
                if (nursery.floorY() - shell >= tierMinY(1) && top < tierMaxY(1)) {
                    inBand++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  colony gate: %d of %d sampled cells actually generate a chamber (%.1f%%), so the real density"
                        + " is %.2f per 1000x1000 blocks%n",
                generated, total, 100.0 * generated / total,
                1.0e6 / Math.pow(ColonyGeneratorTunables.NURSERY_SPACING, 2.0) * generated / total);
        System.out.printf(Locale.ROOT,
                "  %d chambers sampled: floor y in [%d, %d], highest shell top y=%d; %d of %d wholly inside "
                        + "the Nurseries band y[%d,%d)%n",
                total, minFloor, maxTop - ColonyGeneratorTunables.NURSERY_WALL_HEIGHT
                        - ColonyGeneratorTunables.NURSERY_DOME_HEIGHT - shell,
                maxTop, inBand, total, tierMinY(1), tierMaxY(1));
        System.out.println(inBand == total
                ? "  PASS: every sampled chamber sits wholly inside the Nurseries tier."
                : "  FAIL: a chamber crosses a tier boundary.");

        // Anchored on a colony, not the origin: since Ep2 a chamber outside one does not
        // generate at all, so the 3x3 around (0, 0) would be nine rooms that are not there.
        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        ColonyNoise.Nursery[] near = noise.nurseriesNear(anchorX, anchorZ);
        int eligible = 0;
        for (ColonyNoise.Nursery nursery : near) {
            double distance = Math.hypot(nursery.centreX() - anchorX, nursery.centreZ() - anchorZ);
            System.out.printf(Locale.ROOT,
                    "  centre (%7.1f, %7.1f)  floorY %3d  ramp axis (%7.1f, %7.1f)  %6.1f from the colony centre"
                            + "  f=%.2f%s%n",
                    nursery.centreX(), nursery.centreZ(), nursery.floorY(), nursery.axisX(), nursery.axisZ(),
                    distance, noise.colonyField(nursery.centreX(), nursery.centreZ()),
                    nursery.inColony() ? "" : "  (gated out)");
            if (nursery.inColony()) {
                eligible++;
            }
        }

        int reached = 0;
        for (ColonyNoise.Nursery nursery : near) {
            if (!nursery.inColony()) {
                continue;
            }
            if (chamberWalk(noise, "nursery", nursery.centreX(), nursery.centreZ(), nursery.axisX(),
                    nursery.axisZ(), nursery.floorY(), nursery.floorY() + 1, ColonyGeneratorTunables.NURSERY_RADIUS,
                    nursery.floorY() + ColonyGeneratorTunables.NURSERY_WALL_HEIGHT
                            + ColonyGeneratorTunables.NURSERY_DOME_HEIGHT,
                    "the brood floor")) {
                reached++;
            }
        }
        System.out.printf(Locale.ROOT,
                "  %d of %d in-colony chambers around the anchor colony are walkable from their ramp%n",
                reached, eligible);
        System.out.println(reached == eligible
                ? "  PASS: every chamber that generates joins its ramp on foot."
                : "  FAIL: " + (eligible - reached) + " generated chamber(s) do not join their ramp.");
    }

    /**
     * Fungus Garden chambers (D2): how many, where, does one join the ramp on foot, and
     * how close can two neighbouring chambers get?
     *
     * <p>The overlap check is the geometric counterpart of the algebra
     * {@code ColonyGeneratorTunables}' garden section derives (matching the nursery's own
     * "worst case, not average case" argument): every pair of chambers in adjacent cells,
     * over a wide sweep, is checked against the 32-block minimum separation the section
     * concludes is achievable -- {@code WorldgenGameTests#nursery_chambers_never_overlap}
     * holds nursery chambers to the same standard, a single counterexample anywhere is a
     * bug, not a statistic.
     */
    private static void gardens(ColonyNoise noise) {
        System.out.printf(Locale.ROOT,
                "%nfungus garden chambers (one per %d-block cell, radius %.0f, interior %d tall):%n",
                ColonyGeneratorTunables.GARDEN_SPACING, ColonyGeneratorTunables.GARDEN_RADIUS,
                ColonyGeneratorTunables.GARDEN_WALL_HEIGHT + ColonyGeneratorTunables.GARDEN_DOME_HEIGHT);

        double perNursery = Math.pow((double) ColonyGeneratorTunables.NURSERY_SPACING
                / ColonyGeneratorTunables.GARDEN_SPACING, 2.0);
        System.out.printf(Locale.ROOT,
                "  grid density: %.2f cells per 1000x1000 blocks, i.e. %.1fx the nursery cells"
                        + " (before the colony gate)%n",
                1.0e6 / Math.pow(ColonyGeneratorTunables.GARDEN_SPACING, 2.0), perNursery);

        // Band containment, exactly like nurseries()'s own sweep.
        int cells = 8;
        int inBand = 0;
        int total = 0;
        int generated = 0;
        int minFloor = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int shell = (int) Math.ceil(ColonyGeneratorTunables.GARDEN_SHELL_THICKNESS);
        for (int cx = -cells; cx <= cells; cx++) {
            for (int cz = -cells; cz <= cells; cz++) {
                ColonyNoise.Garden[] near = noise.gardensNear(cx * ColonyGeneratorTunables.GARDEN_SPACING,
                        cz * ColonyGeneratorTunables.GARDEN_SPACING);
                ColonyNoise.Garden garden = near[4];
                int top = garden.floorY() + ColonyGeneratorTunables.GARDEN_WALL_HEIGHT
                        + ColonyGeneratorTunables.GARDEN_DOME_HEIGHT + shell;
                total++;
                if (garden.inColony()) {
                    generated++;
                }
                minFloor = Math.min(minFloor, garden.floorY());
                maxTop = Math.max(maxTop, top);
                if (garden.floorY() - shell >= tierMinY(2) && top < tierMaxY(2)) {
                    inBand++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  colony gate: %d of %d sampled cells actually generate a chamber (%.1f%%)%n",
                generated, total, 100.0 * generated / total);
        System.out.printf(Locale.ROOT,
                "  %d chambers sampled: floor y in [%d, %d], highest shell top y=%d; %d of %d wholly inside "
                        + "the Fungal Gardens band y[%d,%d)%n",
                total, minFloor, maxTop - ColonyGeneratorTunables.GARDEN_WALL_HEIGHT
                        - ColonyGeneratorTunables.GARDEN_DOME_HEIGHT - shell,
                maxTop, inBand, total, tierMinY(2), tierMaxY(2));
        System.out.println(inBand == total
                ? "  PASS: every sampled chamber sits wholly inside the Fungal Gardens tier."
                : "  FAIL: a chamber crosses a tier boundary.");

        // Overlap: every neighbouring pair against the 32-block minimum required separation.
        double requiredSeparation = 32.0;
        double minSeparation = Double.MAX_VALUE;
        int pairs = 0;
        int violations = 0;
        for (int cx = -cells; cx < cells; cx++) {
            for (int cz = -cells; cz < cells; cz++) {
                ColonyNoise.Garden here = noise.gardensNear(cx * ColonyGeneratorTunables.GARDEN_SPACING,
                        cz * ColonyGeneratorTunables.GARDEN_SPACING)[4];
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        ColonyNoise.Garden other = noise.gardensNear(
                                (cx + dx) * ColonyGeneratorTunables.GARDEN_SPACING,
                                (cz + dz) * ColonyGeneratorTunables.GARDEN_SPACING)[4];
                        double distance =
                                Math.hypot(here.centreX() - other.centreX(), here.centreZ() - other.centreZ());
                        minSeparation = Math.min(minSeparation, distance);
                        pairs++;
                        if (distance < requiredSeparation) {
                            violations++;
                        }
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  neighbouring-cell separation over %d pairs: minimum %.1f blocks (required >= %.0f)%n",
                pairs, minSeparation, requiredSeparation);
        System.out.println(violations == 0
                ? "  PASS: every sampled pair of garden chambers clears the required separation."
                : "  FAIL: " + violations + " pair(s) came in closer than " + requiredSeparation + " blocks.");

        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        ColonyNoise.Garden[] near = noise.gardensNear(anchorX, anchorZ);
        int eligible = 0;
        for (ColonyNoise.Garden garden : near) {
            double distance = Math.hypot(garden.centreX() - anchorX, garden.centreZ() - anchorZ);
            System.out.printf(Locale.ROOT,
                    "  centre (%7.1f, %7.1f)  floorY %3d  ramp axis (%7.1f, %7.1f)  %6.1f from the colony centre"
                            + "  f=%.2f%s%n",
                    garden.centreX(), garden.centreZ(), garden.floorY(), garden.axisX(), garden.axisZ(), distance,
                    noise.colonyField(garden.centreX(), garden.centreZ()),
                    garden.inColony() ? "" : "  (gated out)");
            if (garden.inColony()) {
                eligible++;
            }
        }

        int reached = 0;
        for (ColonyNoise.Garden garden : near) {
            if (!garden.inColony()) {
                continue;
            }
            if (chamberWalk(noise, "garden", garden.centreX(), garden.centreZ(), garden.axisX(),
                    garden.axisZ(), garden.floorY(), garden.floorY() + 1, ColonyGeneratorTunables.GARDEN_RADIUS,
                    garden.floorY() + ColonyGeneratorTunables.GARDEN_WALL_HEIGHT
                            + ColonyGeneratorTunables.GARDEN_DOME_HEIGHT,
                    "the garden floor")) {
                reached++;
            }
        }
        System.out.printf(Locale.ROOT,
                "  %d of %d in-colony chambers around the anchor colony are walkable from their ramp%n",
                reached, eligible);
        System.out.println(reached == eligible
                ? "  PASS: every chamber that generates joins its ramp on foot."
                : "  FAIL: " + (eligible - reached) + " generated chamber(s) do not join their ramp.");

        larderWalksByTier(noise);
    }

    /**
     * The walkability check again, but deliberately spread across all four tiers.
     *
     * <p>The anchor-colony sweep above is a handful of rooms and their tiers are whatever the
     * seed picked, so on its own it can leave a whole band unexercised -- and the bands are
     * exactly what changed this round. This one hunts for in-colony larders of each tier
     * across a wide sweep and walks the first {@link #WALKS_PER_TIER} of each, so a tier whose
     * floor minimum or landing clearance is wrong cannot hide behind three tiers that are
     * right. The per-tier population found is printed whether or not it gets walked: a tier
     * with no in-colony larders anywhere in the sweep would be a finding in itself.
     */
    private static void larderWalksByTier(ColonyNoise noise) {
        int wide = 16;
        List<List<ColonyNoise.Larder>> byTier = new ArrayList<>();
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            byTier.add(new ArrayList<>());
        }
        for (int cx = -wide; cx <= wide; cx++) {
            for (int cz = -wide; cz <= wide; cz++) {
                ColonyNoise.Larder larder = noise.lardersNear(cx * ColonyGeneratorTunables.LARDER_SPACING,
                        cz * ColonyGeneratorTunables.LARDER_SPACING)[4];
                if (larder.inColony()) {
                    byTier.get(larder.tier()).add(larder);
                }
            }
        }

        System.out.printf(Locale.ROOT,
                "%n  walkability across the tiers (%dx%d cells, walking up to %d in-colony larders per tier):%n",
                2 * wide + 1, 2 * wide + 1, WALKS_PER_TIER);
        int walked = 0;
        int walkable = 0;
        int emptyTiers = 0;
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            List<ColonyNoise.Larder> found = byTier.get(tier);
            System.out.printf(Locale.ROOT, "  tier %d (%s): %d in-colony larders found, walking %d%n",
                    tier, tierName(tier), found.size(), Math.min(WALKS_PER_TIER, found.size()));
            if (found.isEmpty()) {
                emptyTiers++;
                continue;
            }
            for (int i = 0; i < Math.min(WALKS_PER_TIER, found.size()); i++) {
                ColonyNoise.Larder larder = found.get(i);
                walked++;
                if (chamberWalk(noise, "larder", larder.centreX(), larder.centreZ(), larder.axisX(),
                        larder.axisZ(), larder.floorY(), larder.floorY() + 1,
                        ColonyGeneratorTunables.LARDER_RADIUS,
                        larder.floorY() + ColonyGeneratorTunables.LARDER_WALL_HEIGHT
                                + ColonyGeneratorTunables.LARDER_DOME_HEIGHT,
                        "the larder floor")) {
                    walkable++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "  %d of %d walked larders join their ramp on foot%n", walkable, walked);
        System.out.println(walkable == walked && emptyTiers == 0
                ? "  PASS: a larder joins its ramp on foot in every one of the four tiers."
                : emptyTiers > 0
                        ? "  FAIL: " + emptyTiers + " tier(s) produced no in-colony larder to walk."
                        : "  FAIL: " + (walked - walkable) + " larder(s) do not join their ramp.");
    }

    /** In-colony larders walked per tier by {@link #larderWalksByTier}. */
    private static final int WALKS_PER_TIER = 2;

    /**
     * The same-cell slot layout (play-test round 2, item 7).
     *
     * <p>The nursery, garden and larder of one 96-block cell share a cell centre, therefore an
     * anchor ramp, therefore a 24-block approach circle. Until this round their three approach
     * bearings were three separate positional-stream draws and the three rooms landed within
     * about 1.5 blocks of each other in XZ -- see {@code ColonyNoise#chamberBaseBearing} for
     * why "three streams" was not "three directions". They are now placed at fixed slots 120
     * degrees apart, and this is the measurement that the layout, not the seed, is what keeps
     * them apart.
     *
     * <p>Run over every cell in the sweep whether or not the colony gate lets its rooms
     * generate: the slot geometry is a property of the cell, and a bound measured only inside
     * colonies would silently move the day {@code CHAMBER_ELIGIBILITY_MIN_F} is retuned.
     */
    private static void chamberSlots(ColonyNoise noise) {
        System.out.printf(Locale.ROOT,
                "%nsame-cell chamber slots (nursery / garden / larder, %.0f degrees apart, +-%.0f of jitter):%n",
                Math.toDegrees(ColonyGeneratorTunables.CHAMBER_SLOT_STEP),
                Math.toDegrees(ColonyGeneratorTunables.CHAMBER_SLOT_JITTER));

        int cells = 8;
        double minSeparation = Double.MAX_VALUE;
        double minAngle = Double.MAX_VALUE;
        int pairs = 0;
        int violations = 0;
        int belowRequired = 0;
        for (int cx = -cells; cx <= cells; cx++) {
            for (int cz = -cells; cz <= cells; cz++) {
                ColonyNoise.Nursery nursery =
                        noise.nurseriesNear(cx * ColonyGeneratorTunables.NURSERY_SPACING,
                                cz * ColonyGeneratorTunables.NURSERY_SPACING)[4];
                ColonyNoise.Garden garden = noise.gardensNear(cx * ColonyGeneratorTunables.GARDEN_SPACING,
                        cz * ColonyGeneratorTunables.GARDEN_SPACING)[4];
                ColonyNoise.Larder larder = noise.lardersNear(cx * ColonyGeneratorTunables.LARDER_SPACING,
                        cz * ColonyGeneratorTunables.LARDER_SPACING)[4];
                double[][] centres = {
                    {nursery.centreX(), nursery.centreZ()},
                    {garden.centreX(), garden.centreZ()},
                    {larder.centreX(), larder.centreZ()},
                };
                double[][] dirs = {
                    {nursery.dirX(), nursery.dirZ()},
                    {garden.dirX(), garden.dirZ()},
                    {larder.dirX(), larder.dirZ()},
                };
                for (int a = 0; a < 3; a++) {
                    for (int b = a + 1; b < 3; b++) {
                        double distance = Math.hypot(centres[a][0] - centres[b][0], centres[a][1] - centres[b][1]);
                        double dot = dirs[a][0] * dirs[b][0] + dirs[a][1] * dirs[b][1];
                        minAngle = Math.min(minAngle, Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
                        minSeparation = Math.min(minSeparation, distance);
                        pairs++;
                        if (distance < ColonyGeneratorTunables.CHAMBER_SLOT_MIN_SEPARATION - 1.0e-9) {
                            violations++;
                        }
                        if (distance < ColonyGeneratorTunables.CHAMBER_SLOT_REQUIRED_SEPARATION) {
                            belowRequired++;
                        }
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  %d same-cell pairs over %dx%d cells: closest %.2f blocks apart (derived worst case %.2f),"
                        + " narrowest bearing gap %.1f degrees (worst case %.1f)%n",
                pairs, 2 * cells + 1, 2 * cells + 1, minSeparation,
                ColonyGeneratorTunables.CHAMBER_SLOT_MIN_SEPARATION, Math.toDegrees(minAngle),
                Math.toDegrees(ColonyGeneratorTunables.CHAMBER_SLOT_STEP
                        - 2.0 * ColonyGeneratorTunables.CHAMBER_SLOT_JITTER));
        System.out.println(violations == 0
                ? "  PASS: every same-cell chamber pair clears the derived worst-case bound."
                : "  FAIL: " + violations + " same-cell pair(s) came in under the derived worst-case bound.");
        System.out.printf(Locale.ROOT,
                "  required for two rooms of the largest kind (radius + shell + corridor, doubled): %.1f blocks;"
                        + " margin at the measured minimum %.2f%n",
                ColonyGeneratorTunables.CHAMBER_SLOT_REQUIRED_SEPARATION,
                minSeparation - ColonyGeneratorTunables.CHAMBER_SLOT_REQUIRED_SEPARATION);
        System.out.println(belowRequired == 0
                ? "  PASS: no same-cell pair of chambers can touch."
                : "  FAIL: " + belowRequired + " same-cell pair(s) are close enough for their shells to meet.");
    }

    /**
     * Larder chambers (D3): how many, where, does one join the ramp on foot, do neighbours
     * ever overlap, and does every generated larder actually carry its guaranteed
     * Provision Comb?
     *
     * <p>The overlap bound here is the literal "the two shells can't touch" geometry
     * ({@code 2 * (LARDER_RADIUS + LARDER_SHELL_THICKNESS)}) rather than the garden
     * section's 32-block safety margin -- the task only asks for "no overlap", and this is
     * the same standard {@code WorldgenGameTests#nursery_chambers_never_overlap} already
     * holds nursery chambers to.
     *
     * <p>The comb check reads {@link ColonyNoise.Larder#combX()}/{@code combZ()} back out of
     * the SAME chamber object the generator would build, and asks
     * {@link ColonyNoise#larderState} whether each slot is solid and
     * {@link ColonyNoise#isInLarderCorridor} whether it fell in the doorway -- i.e. that
     * every position {@code ColonyChunkGenerator#buildSurface}'s force-write pass targets
     * actually lands inside this larder's own forced-solid shell, never in open air (its
     * own room, its own corridor) or outside the shell entirely. That is the geometric half
     * of "5-7 slots, none in the doorway"; the write itself is unconditional (see
     * {@code ColonyChunkGenerator}), so a PASS here is the guarantee.
     */
    private static void larders(ColonyNoise noise) {
        System.out.printf(Locale.ROOT,
                "%nlarder chambers (one per %d-block cell, radius %.0f, interior %d tall):%n",
                ColonyGeneratorTunables.LARDER_SPACING, ColonyGeneratorTunables.LARDER_RADIUS,
                ColonyGeneratorTunables.LARDER_WALL_HEIGHT + ColonyGeneratorTunables.LARDER_DOME_HEIGHT);

        double perNursery = Math.pow((double) ColonyGeneratorTunables.NURSERY_SPACING
                / ColonyGeneratorTunables.LARDER_SPACING, 2.0);
        System.out.printf(Locale.ROOT,
                "  grid density: %.2f cells per 1000x1000 blocks, i.e. %.1fx the nursery cells"
                        + " (before the colony gate; the comb line below reports how many generate)%n",
                1.0e6 / Math.pow(ColonyGeneratorTunables.LARDER_SPACING, 2.0), perNursery);

        // Band containment -- against the tier each larder PICKED, not one fixed band.
        int cells = 8;
        int inBand = 0;
        int total = 0;
        int shell = (int) Math.ceil(ColonyGeneratorTunables.LARDER_SHELL_THICKNESS);
        int combChecked = 0;
        int combSolid = 0;
        int combInColony = 0;
        int combCountViolations = 0;
        int combDoorwayViolations = 0;
        double combDoorwayMinClearanceDeg = Double.MAX_VALUE;
        int[] perTier = new int[TIER_COUNT];
        int[] perTierInColony = new int[TIER_COUNT];
        int[] perTierMinFloor = new int[TIER_COUNT];
        int[] perTierMaxTop = new int[TIER_COUNT];
        Arrays.fill(perTierMinFloor, Integer.MAX_VALUE);
        Arrays.fill(perTierMaxTop, Integer.MIN_VALUE);
        for (int cx = -cells; cx <= cells; cx++) {
            for (int cz = -cells; cz <= cells; cz++) {
                ColonyNoise.Larder[] near = noise.lardersNear(cx * ColonyGeneratorTunables.LARDER_SPACING,
                        cz * ColonyGeneratorTunables.LARDER_SPACING);
                ColonyNoise.Larder larder = near[4];
                int tier = larder.tier();
                int top = larder.floorY() + ColonyGeneratorTunables.LARDER_WALL_HEIGHT
                        + ColonyGeneratorTunables.LARDER_DOME_HEIGHT + shell;
                total++;
                perTier[tier]++;
                perTierMinFloor[tier] = Math.min(perTierMinFloor[tier], larder.floorY());
                perTierMaxTop[tier] = Math.max(perTierMaxTop[tier], top);
                if (larder.floorY() >= ColonyGeneratorTunables.CHAMBER_FLOOR_MIN_Y_BY_TIER[tier]
                        && larder.floorY() - shell >= tierMinY(tier) && top < tierMaxY(tier)) {
                    inBand++;
                }

                // Only chambers that generate: larderState answers NONE for a gated-out one,
                // and ColonyChunkGenerator skips its force-write pass for the same reason,
                // so counting them here would be a failure invented by the probe.
                if (!larder.inColony()) {
                    continue;
                }
                perTierInColony[tier]++;
                combInColony++;
                ColonyNoise.Larder[] justThis = {larder};
                int[] combX = larder.combX();
                int[] combZ = larder.combZ();
                if (combX.length < ColonyGeneratorTunables.LARDER_PROVISION_COMB_MIN
                        || combX.length > ColonyGeneratorTunables.LARDER_PROVISION_COMB_MAX) {
                    combCountViolations++;
                }
                // The bearing the corridor punches through the shell on, seen from the
                // centre -- the same "approach + PI" ColonyNoise#larderForCell derives it
                // from, recovered here from the stored direction vector rather than a
                // second copy of the angle itself.
                double doorwayBearing = Math.atan2(larder.dirZ(), larder.dirX()) + Math.PI;
                for (int i = 0; i < combX.length; i++) {
                    combChecked++;
                    combSolid += noise.larderState(justThis, combX[i], larder.combY(), combZ[i])
                            == ColonyNoise.LARDER_SOLID ? 1 : 0;
                    if (ColonyNoise.isInLarderCorridor(larder, combX[i], combZ[i])) {
                        combDoorwayViolations++;
                    }
                    double slotBearing =
                            Math.atan2(combZ[i] - larder.centreZ(), combX[i] - larder.centreX());
                    double delta = Math.abs(Math.IEEEremainder(slotBearing - doorwayBearing, 2.0 * Math.PI));
                    combDoorwayMinClearanceDeg = Math.min(combDoorwayMinClearanceDeg, Math.toDegrees(delta));
                }
            }
        }
        System.out.printf(Locale.ROOT, "  %d chambers sampled, tier pick spread over %d tiers:%n",
                total, TIER_COUNT);
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            System.out.printf(Locale.ROOT,
                    "    tier %d (%s, y[%d,%d), floor min %d): %3d picked (%4.1f%%), %3d in colony,"
                            + " floors from %d, highest shell top %d%n",
                    tier, tierName(tier), tierMinY(tier), tierMaxY(tier),
                    ColonyGeneratorTunables.CHAMBER_FLOOR_MIN_Y_BY_TIER[tier], perTier[tier],
                    100.0 * perTier[tier] / total, perTierInColony[tier],
                    perTier[tier] == 0 ? -1 : perTierMinFloor[tier],
                    perTier[tier] == 0 ? -1 : perTierMaxTop[tier]);
        }
        System.out.printf(Locale.ROOT,
                "  %d of %d sit wholly inside the band they picked%n", inBand, total);
        System.out.println(inBand == total
                ? "  PASS: every sampled chamber sits wholly inside its own picked tier."
                : "  FAIL: a chamber crosses the boundary of the tier it picked.");

        System.out.printf(Locale.ROOT,
                "  Provision Comb: [%d, %d] slots per larder, %d in-colony chambers of %d sampled, %d slots total,"
                        + " %d land solid, closest slot to a doorway %.1f degrees%n",
                ColonyGeneratorTunables.LARDER_PROVISION_COMB_MIN, ColonyGeneratorTunables.LARDER_PROVISION_COMB_MAX,
                combInColony, total, combChecked, combSolid,
                combInColony == 0 ? -1.0 : combDoorwayMinClearanceDeg);
        System.out.println(combSolid == combChecked
                ? "  PASS: every sampled larder's Provision Comb slots are inside its own solid shell."
                : "  FAIL: a Provision Comb slot missed the shell -- it would land in open air.");
        System.out.println(combCountViolations == 0
                ? "  PASS: every sampled larder's Provision Comb count is in ["
                        + ColonyGeneratorTunables.LARDER_PROVISION_COMB_MIN + ", "
                        + ColonyGeneratorTunables.LARDER_PROVISION_COMB_MAX + "]."
                : "  FAIL: " + combCountViolations + " larder(s) drew a Provision Comb count outside ["
                        + ColonyGeneratorTunables.LARDER_PROVISION_COMB_MIN + ", "
                        + ColonyGeneratorTunables.LARDER_PROVISION_COMB_MAX + "].");
        System.out.println(combDoorwayViolations == 0
                ? "  PASS: no sampled Provision Comb slot lands in its larder's own doorway."
                : "  FAIL: " + combDoorwayViolations + " Provision Comb slot(s) landed inside the doorway.");

        // Overlap: the literal "shells don't touch" bound.
        double requiredSeparation = 2.0 * (ColonyGeneratorTunables.LARDER_RADIUS
                + ColonyGeneratorTunables.LARDER_SHELL_THICKNESS);
        double minSeparation = Double.MAX_VALUE;
        int pairs = 0;
        int violations = 0;
        for (int cx = -cells; cx < cells; cx++) {
            for (int cz = -cells; cz < cells; cz++) {
                ColonyNoise.Larder here = noise.lardersNear(cx * ColonyGeneratorTunables.LARDER_SPACING,
                        cz * ColonyGeneratorTunables.LARDER_SPACING)[4];
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        ColonyNoise.Larder other = noise.lardersNear(
                                (cx + dx) * ColonyGeneratorTunables.LARDER_SPACING,
                                (cz + dz) * ColonyGeneratorTunables.LARDER_SPACING)[4];
                        double distance =
                                Math.hypot(here.centreX() - other.centreX(), here.centreZ() - other.centreZ());
                        minSeparation = Math.min(minSeparation, distance);
                        pairs++;
                        if (distance < requiredSeparation) {
                            violations++;
                        }
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  neighbouring-cell separation over %d pairs: minimum %.1f blocks (required >= %.1f)%n",
                pairs, minSeparation, requiredSeparation);
        System.out.println(violations == 0
                ? "  PASS: no two sampled larder chambers overlap."
                : "  FAIL: " + violations + " pair(s) overlap.");

        throneClearance(noise);

        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        ColonyNoise.Larder[] near = noise.lardersNear(anchorX, anchorZ);
        int eligible = 0;
        for (ColonyNoise.Larder larder : near) {
            double distance = Math.hypot(larder.centreX() - anchorX, larder.centreZ() - anchorZ);
            System.out.printf(Locale.ROOT,
                    "  centre (%7.1f, %7.1f)  floorY %3d  ramp axis (%7.1f, %7.1f)  %6.1f from the colony centre"
                            + "  f=%.2f%s%n",
                    larder.centreX(), larder.centreZ(), larder.floorY(), larder.axisX(), larder.axisZ(), distance,
                    noise.colonyField(larder.centreX(), larder.centreZ()),
                    larder.inColony() ? "" : "  (gated out)");
            if (larder.inColony()) {
                eligible++;
            }
        }

        int reached = 0;
        for (ColonyNoise.Larder larder : near) {
            if (!larder.inColony()) {
                continue;
            }
            if (chamberWalk(noise, "larder", larder.centreX(), larder.centreZ(), larder.axisX(),
                    larder.axisZ(), larder.floorY(), larder.floorY() + 1, ColonyGeneratorTunables.LARDER_RADIUS,
                    larder.floorY() + ColonyGeneratorTunables.LARDER_WALL_HEIGHT
                            + ColonyGeneratorTunables.LARDER_DOME_HEIGHT,
                    "the larder floor")) {
                reached++;
            }
        }
        System.out.printf(Locale.ROOT,
                "  %d of %d in-colony chambers around the anchor colony are walkable from their ramp%n",
                reached, eligible);
        System.out.println(reached == eligible
                ? "  PASS: every chamber that generates joins its ramp on foot."
                : "  FAIL: " + (eligible - reached) + " generated chamber(s) do not join their ramp.");
    }

    /**
     * The one collision the 120-degree slots cannot arrange: a tier-0 larder against the
     * queen's throne (play-test round 2, item 7).
     *
     * <p>The throne is not on the 96-block grid, has no slot, and lives in the Royal Depths,
     * which until this round no other room could reach. Two things are checked, and they are
     * not the same question. The first is the guard the generator implements: every tier-0
     * larder stands at least {@link ColonyGeneratorTunables#THRONE_LARDER_CLEARANCE} from its
     * colony's throne centre. The second is what the guard is <em>for</em>: that no tier-0
     * larder's carve reaches into a throne's room or its approach corridor at all -- asked of
     * {@code larderState} and {@code throneState} themselves, block by block, because the
     * throne outranks the larder in {@link ColonyNoise#isAir} and a position they claim
     * differently is a position where the larder loses (a sealed room or a corridor with its
     * doorway walled off, exactly the failure the 48-block nursery cell produced).
     *
     * <p>Keeping both matters. The first would still pass if the envelopes were derived wrong;
     * the second is the only one that reads the real shapes, and it is what caught the earlier
     * ramp-cell guard letting thrones one cell away through. The re-tier count is printed
     * because a gate that fires on everything would satisfy both checks and quietly delete
     * tier-0 larders from the world.
     *
     * <p>Swept much wider than the band checks above: the pairing needs a throne and a tier-0
     * larder in the same neighbourhood, and thrones are one per 384 blocks.
     */
    private static void throneClearance(ColonyNoise noise) {
        int wide = 24;
        int shell = (int) Math.ceil(ColonyGeneratorTunables.LARDER_SHELL_THICKNESS);
        double roomsRequired = ColonyGeneratorTunables.THRONE_RADIUS
                + ColonyGeneratorTunables.THRONE_SHELL_THICKNESS
                + ColonyGeneratorTunables.LARDER_RADIUS + ColonyGeneratorTunables.LARDER_SHELL_THICKNESS;
        double carveReach = ColonyGeneratorTunables.THRONE_MAX_REACH + ColonyGeneratorTunables.LARDER_MAX_REACH;

        int tierZero = 0;
        int drewTierZero = 0;
        int reTiered = 0;
        int tooCloseToThrone = 0;
        int roomsTooClose = 0;
        int scanned = 0;
        int clashing = 0;
        int clashBlocks = 0;
        double minClearance = Double.MAX_VALUE;
        double minRoomGap = Double.MAX_VALUE;
        for (int cx = -wide; cx <= wide; cx++) {
            for (int cz = -wide; cz <= wide; cz++) {
                ColonyNoise.Larder larder = noise.lardersNear(cx * ColonyGeneratorTunables.LARDER_SPACING,
                        cz * ColonyGeneratorTunables.LARDER_SPACING)[4];
                if (larder.tierDraw() == 0) {
                    drewTierZero++;
                    if (larder.tier() != 0) {
                        reTiered++;
                    }
                }
                if (larder.tier() != 0) {
                    continue;
                }
                tierZero++;
                int centreX = (int) Math.round(larder.centreX());
                int centreZ = (int) Math.round(larder.centreZ());
                ColonyNoise.Throne[] thrones = noise.thronesNear(centreX, centreZ);
                boolean clashesHere = false;
                for (ColonyNoise.Throne throne : thrones) {
                    double gap = Math.hypot(throne.centreX() - larder.centreX(),
                            throne.centreZ() - larder.centreZ());
                    minClearance = Math.min(minClearance, gap);
                    if (gap < ColonyGeneratorTunables.THRONE_LARDER_CLEARANCE) {
                        tooCloseToThrone++;
                    }
                    // Only pairs whose Y ranges actually meet can collide at all.
                    int larderBottom = larder.floorY() - shell;
                    int larderTop = larder.floorY() + ColonyGeneratorTunables.LARDER_WALL_HEIGHT
                            + ColonyGeneratorTunables.LARDER_DOME_HEIGHT + shell;
                    int throneBottom = throne.floorY() - 2;
                    int throneTop = throne.floorY() + ColonyGeneratorTunables.THRONE_WALL_HEIGHT
                            + ColonyGeneratorTunables.THRONE_DOME_HEIGHT + 2;
                    if (larderTop < throneBottom || throneTop < larderBottom) {
                        continue;
                    }
                    minRoomGap = Math.min(minRoomGap, gap);
                    if (gap < roomsRequired) {
                        roomsTooClose++;
                    }
                    if (gap <= carveReach) {
                        scanned++;
                        int blocks = carveConflict(noise, larder, throne);
                        clashBlocks += blocks;
                        clashesHere |= blocks > 0;
                        if (blocks > 0) {
                            // Named, not just counted: a clash is a geometry bug, and the
                            // shaft-cell offset between the two anchors is the whole diagnosis.
                            System.out.printf(Locale.ROOT,
                                    "    CLASH: larder cell (%d, %d) floor %d on ramp cell (%d, %d)"
                                            + " vs throne floor %d on ramp cell (%d, %d)"
                                            + " -- ramp offset (%d, %d), centres %.1f apart,"
                                            + " %d block(s) they disagree about%n",
                                    cx, cz, larder.floorY(), shaftCellOf(larder.axisX()),
                                    shaftCellOf(larder.axisZ()), throne.floorY(), shaftCellOf(throne.axisX()),
                                    shaftCellOf(throne.axisZ()),
                                    shaftCellOf(throne.axisX()) - shaftCellOf(larder.axisX()),
                                    shaftCellOf(throne.axisZ()) - shaftCellOf(larder.axisZ()), gap, blocks);
                        }
                    }
                }
                if (clashesHere) {
                    clashing++;
                }
            }
        }

        System.out.printf(Locale.ROOT,
                "  throne clearance over %dx%d cells: %d cells drew tier 0, %d of them re-tiered by the gate"
                        + " (%.1f%%), leaving %d tier-0 larders%n",
                2 * wide + 1, 2 * wide + 1, drewTierZero, reTiered,
                drewTierZero == 0 ? 0.0 : 100.0 * reTiered / drewTierZero, tierZero);
        System.out.println(reTiered > 0 && tierZero > 0
                ? "  PASS: the gate fires, and leaves tier-0 larders in the world."
                : "  FAIL: the gate " + (reTiered == 0 ? "never fires" : "re-tiered every tier-0 larder")
                        + " -- one of the two is broken.");
        System.out.printf(Locale.ROOT,
                "  closest surviving tier-0 larder to any throne centre: %.1f blocks"
                        + " (envelopes %.1f + %.1f + %.1f margin = %.1f required); %d under it%n",
                minClearance == Double.MAX_VALUE ? -1.0 : minClearance,
                ColonyGeneratorTunables.THRONE_CARVE_ENVELOPE, ColonyGeneratorTunables.LARDER_CARVE_ENVELOPE,
                ColonyGeneratorTunables.THRONE_LARDER_MARGIN, ColonyGeneratorTunables.THRONE_LARDER_CLEARANCE,
                tooCloseToThrone);
        System.out.println(tooCloseToThrone == 0
                ? "  PASS: every tier-0 larder clears its colony's throne by the full envelope bound."
                : "  FAIL: " + tooCloseToThrone + " tier-0 larder(s) stand inside a throne's exclusion ball.");
        System.out.printf(Locale.ROOT,
                "  closest tier-0 larder to a throne whose Y range it shares: %.1f blocks centre to centre"
                        + " (two rooms need %.1f); %d pair(s) under that%n",
                minRoomGap == Double.MAX_VALUE ? -1.0 : minRoomGap, roomsRequired, roomsTooClose);
        System.out.printf(Locale.ROOT,
                "  %d larder/throne pair(s) close enough for their carves to meet were scanned block by block:"
                        + " %d block(s) where one wants air and the other solid, across %d larder(s)%n",
                scanned, clashBlocks, clashing);
        System.out.println(clashBlocks == 0
                ? "  PASS: no tier-0 larder reaches into a throne room or its corridor."
                : "  FAIL: a tier-0 larder overlaps a throne's carve -- the throne outranks it, so that larder"
                        + " loses room or doorway.");
    }

    /**
     * The shaft cell a ramp axis belongs to. An axis lands within {@code SHAFT_JITTER/2} = 8
     * of its cell's centre at {@code cell*48 + 24}, so it never leaves its own cell and the
     * cell is recoverable from the coordinate alone.
     */
    private static int shaftCellOf(double axis) {
        return Math.floorDiv((int) Math.floor(axis), ColonyGeneratorTunables.SHAFT_SPACING);
    }

    /**
     * How many blocks one larder and one throne <b>disagree</b> about.
     *
     * <p>Scanned rather than reasoned about: the corridors are thin, they radiate from two
     * different ramp axes, and "do these two shapes touch" is not something the centre-to-centre
     * distance answers. The box is the larder's own -- {@code LARDER_MAX_REACH} covers its dome,
     * its shell and its whole corridor back to within a block of its axis -- because the
     * question is which of the larder's blocks the throne takes away.
     *
     * <p>Only real conflicts count. The throne outranks the larder in
     * {@link ColonyNoise#isAir}, so a position both claim matters exactly when they claim it
     * for <em>different</em> things: the larder's air answered solid by a throne shell (a
     * room or doorway sealed) or the larder's shell answered air by a throne interior (a wall
     * punched open into the queen's room). Two shells meeting is two solid blocks agreeing and
     * is not a defect -- counting it would report a failure the world does not have.
     */
    private static int carveConflict(ColonyNoise noise, ColonyNoise.Larder larder, ColonyNoise.Throne throne) {
        ColonyNoise.Larder[] justLarder = {larder};
        ColonyNoise.Throne[] justThrone = {throne};
        int reach = (int) Math.ceil(ColonyGeneratorTunables.LARDER_MAX_REACH) + 1;
        int centreX = (int) Math.round(larder.centreX());
        int centreZ = (int) Math.round(larder.centreZ());
        int shell = (int) Math.ceil(ColonyGeneratorTunables.LARDER_SHELL_THICKNESS);
        int bottom = larder.floorY() - shell;
        int top = larder.floorY() + ColonyGeneratorTunables.LARDER_WALL_HEIGHT
                + ColonyGeneratorTunables.LARDER_DOME_HEIGHT + shell;
        int conflicts = 0;
        for (int x = centreX - reach; x <= centreX + reach; x++) {
            for (int z = centreZ - reach; z <= centreZ + reach; z++) {
                for (int y = bottom; y <= top; y++) {
                    int larderSays = noise.larderState(justLarder, x, y, z);
                    if (larderSays == ColonyNoise.LARDER_NONE) {
                        continue;
                    }
                    int throneSays = noise.throneState(justThrone, x, y, z);
                    if (throneSays == ColonyNoise.THRONE_NONE) {
                        continue;
                    }
                    boolean larderAir = larderSays == ColonyNoise.LARDER_AIR;
                    boolean throneAir = throneSays == ColonyNoise.THRONE_AIR;
                    if (larderAir != throneAir) {
                        conflicts++;
                    }
                }
            }
        }
        return conflicts;
    }

    /**
     * Comb: how much of it there is, and how big a piece of it is.
     *
     * <p>The whole point of the patch field is that both numbers move in opposite directions
     * -- less comb overall, in bigger contiguous pieces -- so reporting either alone would be
     * useless. Comb blocks are found the way {@code buildSurface} finds them (a solid block
     * with a deep enough air run against it, then the patch gate, then the per-block roll)
     * and then grouped by 6-connectivity, so "patch size" is the number of comb blocks a
     * player would see joined up on a wall.
     */
    private static void combPatches(ColonyNoise noise) {
        int span = 96;
        int half = span / 2;
        // Anchored on a colony core: comb's patch threshold is lerped to "never" as the
        // colony field falls, so a window at the origin would report zero everywhere and
        // say nothing at all about patch SIZE, which is what this section exists for.
        ColonyNoise.Colony anchor = anchor(noise);
        int anchorX = (int) Math.round(anchor.centreX());
        int anchorZ = (int) Math.round(anchor.centreZ());
        System.out.printf(Locale.ROOT,
                "%ncomb patches over %dx%d blocks centred on the colony at (%d, %d) (patch scale %.2f/%.2f):%n",
                span, span, anchorX, anchorZ, ColonyGeneratorTunables.COMB_PATCH_XZ_SCALE,
                ColonyGeneratorTunables.COMB_PATCH_Y_SCALE);
        // "seenSize" is the block-weighted mean: the size of the patch the average comb
        // block belongs to, which is what a player standing in front of a wall actually
        // experiences. The plain mean is dragged down by the long tail of one-block clips
        // where a blob only grazes a surface.
        System.out.println(
                "  tier  roomySurface   comb  per1000  patches  meanSize  seenSize  maxSize   (old flat chance)");

        RandomSource rolls = new XoroshiroRandomSource(99887766L);
        for (int tier = 1; tier >= 0; tier--) {
            int bandMin = Math.max(tierMinY(tier), FLOOR_TOP);
            int bandMax = Math.min(tierMaxY(tier), CEILING_BOTTOM);
            int bandHeight = bandMax - bandMin;
            boolean[] isAir = new boolean[span * span * bandHeight];
            double[] fields = new double[span * span];
            for (int ix = 0; ix < span; ix++) {
                int x = anchorX + ix - half;
                for (int iz = 0; iz < span; iz++) {
                    int z = anchorZ + iz - half;
                    Col col = col(noise, x, z);
                    fields[ix * span + iz] = col.field();
                    for (int y = bandMin; y < bandMax; y++) {
                        isAir[(ix * bandHeight + (y - bandMin)) * span + iz] = air(noise, col, x, y, z);
                    }
                }
            }

            boolean[] comb = new boolean[isAir.length];
            long roomySurface = 0;
            long combCount = 0;
            for (int ix = 1; ix < span - 1; ix++) {
                for (int iz = 1; iz < span - 1; iz++) {
                    for (int y = 1; y < bandHeight - 1; y++) {
                        int i = (ix * bandHeight + y) * span + iz;
                        if (isAir[i] || !roomySurface(isAir, span, bandHeight, ix, y, iz)) {
                            continue;
                        }
                        roomySurface++;
                        double field = fields[ix * span + iz];
                        if (!noise.isCombPatch(field, anchorX + ix - half, bandMin + y, anchorZ + iz - half)) {
                            continue;
                        }
                        double roll = rolls.nextDouble();
                        if (roll < (ColonyGeneratorTunables.ROYAL_COMB_CHANCE_BY_TIER[tier]
                                + ColonyGeneratorTunables.BROOD_COMB_CHANCE_BY_TIER[tier]) * field) {
                            comb[i] = true;
                            combCount++;
                        }
                    }
                }
            }

            int patches = 0;
            long sizeSum = 0;
            long sizeSquareSum = 0;
            int maxSize = 0;
            boolean[] seen = new boolean[comb.length];
            Deque<Integer> queue = new ArrayDeque<>();
            for (int start = 0; start < comb.length; start++) {
                if (!comb[start] || seen[start]) {
                    continue;
                }
                seen[start] = true;
                queue.add(start);
                int size = 0;
                while (!queue.isEmpty()) {
                    int cur = queue.poll();
                    size++;
                    int iz = cur % span;
                    int y = (cur / span) % bandHeight;
                    int ix = cur / (span * bandHeight);
                    // 26-connectivity, not 6: comb clings to a jagged noise wall, where two
                    // blocks that share only an edge still read as one patch. Face
                    // connectivity measured the same geometry at a third of the size.
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                combNeighbour(queue, seen, comb, span, bandHeight, ix + dx, y + dy, iz + dz);
                            }
                        }
                    }
                }
                patches++;
                sizeSum += size;
                sizeSquareSum += (long) size * size;
                maxSize = Math.max(maxSize, size);
            }

            System.out.printf(Locale.ROOT,
                    "  %4d  %12d  %5d  %7.1f  %7d  %8.1f  %9.1f  %7d   %.1f per 1000%n",
                    tier, roomySurface, combCount,
                    roomySurface == 0 ? 0.0 : 1000.0 * combCount / roomySurface,
                    patches, patches == 0 ? 0.0 : (double) sizeSum / patches,
                    sizeSum == 0 ? 0.0 : (double) sizeSquareSum / sizeSum, maxSize,
                    1000.0 * OLD_FLAT_COMB_CHANCE_BY_TIER[tier]);
        }
    }

    /**
     * Soil pockets (play-test round 2, item 8): do they exist at all, and does every claimed
     * block stay out of a chamber's own interior?
     *
     * <p>Sampled over a plain grid of chunks around the origin rather than anchored on a
     * colony the way {@link #nurseries}/{@link #gardens}/{@link #larders}/{@link
     * #combPatches} are (see the class javadoc for why those anchor there) -- pockets are
     * deliberately not colony-field-gated, so the origin is exactly as fair a sample as
     * anywhere else, and a 25x25-chunk grid around it still crosses real shafts and chamber
     * shells often enough to exercise the exclusion this section checks.
     */
    private static void soilPockets(ColonyNoise noise) {
        System.out.printf(Locale.ROOT,
                "%nsoil pockets (%d-%d per chunk, %d-%d blocks each, dirt/gravel/sand weights %d/%d/%d):%n",
                ColonyGeneratorTunables.SOIL_POCKETS_MIN_PER_CHUNK, ColonyGeneratorTunables.SOIL_POCKETS_MAX_PER_CHUNK,
                ColonyGeneratorTunables.SOIL_POCKET_MIN_SIZE, ColonyGeneratorTunables.SOIL_POCKET_MAX_SIZE,
                ColonyGeneratorTunables.SOIL_POCKET_DIRT_WEIGHT, ColonyGeneratorTunables.SOIL_POCKET_GRAVEL_WEIGHT,
                ColonyGeneratorTunables.SOIL_POCKET_SAND_WEIGHT);

        int chunkRadius = 12;
        int chunksSampled = 0;
        int emptyChunks = 0;
        int pocketsFound = 0;
        long blocksFound = 0;
        int interiorViolations = 0;
        int[] materialCounts = new int[3];

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int minX = cx * 16;
                int minZ = cz * 16;
                ColonyNoise.Shaft[] shafts = noise.shaftsNear(minX, minZ);
                ColonyNoise.Throne[] thrones = noise.thronesNear(minX, minZ);
                ColonyNoise.Nursery[] nurseries = noise.nurseriesNear(minX, minZ);
                ColonyNoise.Garden[] gardens = noise.gardensNear(minX, minZ);
                ColonyNoise.Larder[] larders = noise.lardersNear(minX, minZ);
                ColonyNoise.SoilPocket[] pockets =
                        noise.soilPocketsForChunk(shafts, thrones, nurseries, gardens, larders, minX, minZ);
                chunksSampled++;
                if (pockets.length == 0) {
                    emptyChunks++;
                    continue;
                }
                for (ColonyNoise.SoilPocket pocket : pockets) {
                    pocketsFound++;
                    materialCounts[pocket.material()]++;
                    for (ColonyNoise.PocketBlock block : pocket.blocks()) {
                        blocksFound++;
                        ColonyNoise.Throne[] columnThrones = noise.thronesForColumn(thrones, block.x(), block.z());
                        ColonyNoise.Nursery[] columnNurseries =
                                noise.nurseriesForColumn(nurseries, block.x(), block.z());
                        ColonyNoise.Garden[] columnGardens = noise.gardensForColumn(gardens, block.x(), block.z());
                        ColonyNoise.Larder[] columnLarders = noise.lardersForColumn(larders, block.x(), block.z());
                        if (noise.isInThroneRoom(columnThrones, block.x(), block.y(), block.z())
                                || noise.isInNurseryRoom(columnNurseries, block.x(), block.y(), block.z())
                                || noise.isInGardenRoom(columnGardens, block.x(), block.y(), block.z())
                                || noise.isInLarderRoom(columnLarders, block.x(), block.y(), block.z())) {
                            interiorViolations++;
                        }
                    }
                }
            }
        }

        System.out.printf(Locale.ROOT,
                "  %d chunks sampled (%d rolled zero eligible pockets), %d pockets, %d blocks total"
                        + " (dirt %d, gravel %d, sand %d)%n",
                chunksSampled, emptyChunks, pocketsFound, blocksFound, materialCounts[ColonyNoise.SOIL_MATERIAL_DIRT],
                materialCounts[ColonyNoise.SOIL_MATERIAL_GRAVEL], materialCounts[ColonyNoise.SOIL_MATERIAL_SAND]);
        System.out.println(pocketsFound > 0
                ? "  PASS: pockets exist over the sampled chunks."
                : "  FAIL: no pocket generated anywhere in the sample.");
        System.out.printf(Locale.ROOT, "  %d of %d pocket blocks landed inside a chamber's own interior%n",
                interiorViolations, blocksFound);
        System.out.println(interiorViolations == 0
                ? "  PASS: no pocket block ever lands inside a chamber interior."
                : "  FAIL: " + interiorViolations + " pocket block(s) intruded on a chamber's interior.");
    }

    /** The pre-round-1 flat per-block comb chances, kept only as the comparison baseline. */
    private static final double[] OLD_FLAT_COMB_CHANCE_BY_TIER = {0.014, 0.178, 0.0, 0.0};

    /** {@code decorateSurface}'s test: a solid block with a deep enough air run against it. */
    private static boolean roomySurface(boolean[] isAir, int span, int bandHeight, int ix, int y, int iz) {
        int deepest = 0;
        for (int d = 0; d < 6; d++) {
            int nx = ix + (d == 0 ? 1 : d == 1 ? -1 : 0);
            int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
            int nz = iz + (d == 4 ? 1 : d == 5 ? -1 : 0);
            deepest = Math.max(deepest, airRun(isAir, span, bandHeight, nx, ny, nz));
        }
        return deepest >= ColonyGeneratorTunables.ROOMY_CLEARANCE + 2;
    }

    /** Height of the contiguous air run a position belongs to, 0 when solid. */
    private static int airRun(boolean[] isAir, int span, int bandHeight, int ix, int y, int iz) {
        if (ix < 0 || iz < 0 || ix >= span || y < 0 || y >= bandHeight
                || !isAir[(ix * bandHeight + y) * span + iz]) {
            return 0;
        }
        int low = y;
        while (low > 0 && isAir[(ix * bandHeight + (low - 1)) * span + iz]) {
            low--;
        }
        int high = y;
        while (high < bandHeight - 1 && isAir[(ix * bandHeight + (high + 1)) * span + iz]) {
            high++;
        }
        return high - low + 1;
    }

    private static void combNeighbour(Deque<Integer> queue, boolean[] seen, boolean[] comb, int span,
            int bandHeight, int ix, int y, int iz) {
        if (ix < 0 || iz < 0 || ix >= span || y < 0 || y >= bandHeight) {
            return;
        }
        int i = (ix * bandHeight + y) * span + iz;
        if (comb[i] && !seen[i]) {
            seen[i] = true;
            queue.add(i);
        }
    }

    /**
     * How many ants a chunk actually ends up with.
     *
     * <p>Runs {@code ColonyChunkGenerator#spawnTier}'s own arithmetic --
     * {@link ColonyGeneratorTunables#rollCount} and {@link ColonyGeneratorTunables#between},
     * the real methods, not a copy -- against the pure air field, so the number is
     * placements rather than intentions: a member that cannot find a floor in
     * {@code SPAWN_FLOOR_ATTEMPTS} tries is counted as lost exactly as it would be in game.
     *
     * <p>Ep2 anchors the sampled box on a colony and runs it twice: once at full colony
     * density ({@code f = 1}, which is the number the play-test-round-1 retune was tuned
     * against and the one comparable to the legacy baseline below), and once with the real
     * per-chunk field, which is what an explorer crossing a colony actually meets. The
     * second is strictly the first scaled by the mean field over the box -- {@code rollCount}
     * has expectation {@code expected * f} exactly -- so the pair also serves as a check
     * that the modulation is doing arithmetic rather than something surprising.
     */
    private static void spawnDensity(ColonyNoise noise) {
        int chunks = 16;
        ColonyNoise.Colony anchor = anchor(noise);
        int baseX = ((int) Math.round(anchor.centreX()) - chunks * 8) & ~15;
        int baseZ = ((int) Math.round(anchor.centreZ()) - chunks * 8) & ~15;
        RandomSource random = new XoroshiroRandomSource(20260815L);
        System.out.printf(Locale.ROOT,
                "%nchunk-generation spawn density over %d chunks centred on the colony at (%d, %d):%n",
                chunks * chunks, Math.round(anchor.centreX()), Math.round(anchor.centreZ()));
        System.out.println("  tier  clusters/chunk  workers/chunk  soldiers/chunk  ants/chunk  lost%");

        double[] antsPerTier = new double[TIER_COUNT];
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            int bandMin = tierMinY(tier);
            int bandMax = tierMaxY(tier);
            long clusters = 0;
            long wanted = 0;
            long workers = 0;
            long soldiers = 0;
            for (int cx = 0; cx < chunks; cx++) {
                for (int cz = 0; cz < chunks; cz++) {
                    int originX = baseX + cx * 16;
                    int originZ = baseZ + cz * 16;
                    for (int c = ColonyGeneratorTunables.rollCount(
                            ColonyGeneratorTunables.SPAWN_CLUSTERS_PER_CHUNK_BY_TIER[tier], random);
                            c > 0; c--) {
                        clusters++;
                        int wantSoldiers = ColonyGeneratorTunables.between(
                                ColonyGeneratorTunables.CLUSTER_SOLDIERS_MIN_BY_TIER[tier],
                                ColonyGeneratorTunables.CLUSTER_SOLDIERS_MAX_BY_TIER[tier], random);
                        int wantWorkers = ColonyGeneratorTunables.between(
                                ColonyGeneratorTunables.CLUSTER_WORKERS_MIN_BY_TIER[tier],
                                ColonyGeneratorTunables.CLUSTER_WORKERS_MAX_BY_TIER[tier], random);
                        wanted += wantSoldiers + wantWorkers;
                        int x = originX + random.nextInt(16);
                        int z = originZ + random.nextInt(16);
                        for (int member = 0; member < wantSoldiers + wantWorkers; member++) {
                            boolean placed = false;
                            for (int attempt = 0;
                                    !placed && attempt < ColonyGeneratorTunables.SPAWN_FLOOR_ATTEMPTS; attempt++) {
                                placed = hasFloor(noise, x, z, bandMin, bandMax, random);
                                x = Math.max(originX, Math.min(originX + 15, x + random.nextInt(7) - 3));
                                z = Math.max(originZ, Math.min(originZ + 15, z + random.nextInt(7) - 3));
                            }
                            if (placed) {
                                if (member < wantSoldiers) {
                                    soldiers++;
                                } else {
                                    workers++;
                                }
                            }
                        }
                    }
                }
            }
            double perChunk = 1.0 / (chunks * chunks);
            antsPerTier[tier] = (workers + soldiers) * perChunk;
            System.out.printf(Locale.ROOT, "  %4d  %14.2f  %13.2f  %14.2f  %10.2f  %4.1f%%%n",
                    tier, clusters * perChunk, workers * perChunk, soldiers * perChunk, antsPerTier[tier],
                    wanted == 0 ? 0.0 : 100.0 * (wanted - workers - soldiers) / wanted);
        }
        double total = 0.0;
        for (double ants : antsPerTier) {
            total += ants;
        }
        double legacy = legacySpawnDensity(noise, chunks, baseX, baseZ);
        System.out.printf(Locale.ROOT,
                "  all tiers at full colony density: %.2f ants per chunk against the pre-round-1 scheme's %.2f"
                        + "  =  %.2fx%n",
                total, legacy, total / legacy);

        double fieldSum = 0.0;
        for (int cx = 0; cx < chunks; cx++) {
            for (int cz = 0; cz < chunks; cz++) {
                fieldSum += noise.colonyField(baseX + cx * 16 + 8, baseZ + cz * 16 + 8);
            }
        }
        double meanField = fieldSum / (chunks * chunks);
        System.out.printf(Locale.ROOT,
                "  colony field over the same box: mean f = %.3f, so %.2f ants per chunk field-weighted%n",
                meanField, total * meanField);
    }

    // The pre-round-1 spawning parameters, read out of the biome JSONs. Kept ONLY as
    // the baseline the density change is measured against -- nothing in the mod reads them.
    // Indexed by tier; the weighted lists are {weight, minCount, maxCount} per caste, and
    // the larva row of the Nurseries is excluded because the comparison is about ants.
    private static final double[] LEGACY_CREATURE_PROBABILITY = {0.10, 0.20, 0.16};
    private static final int[][][] LEGACY_SPAWNERS = {
        {{10, 1, 3}},                        // Royal Depths:    soldier only
        {{8, 2, 3}, {12, 2, 3}, {6, 1, 3}},  // Nurseries:       worker, soldier, LARVA
        {{12, 2, 4}, {5, 1, 2}},             // Fungal Gardens:  worker, soldier
    };
    /** Index into a tier's {@link #LEGACY_SPAWNERS} row that is the larva, or -1. */
    private static final int[] LEGACY_LARVA_ROW = {-1, 2, -1};

    /**
     * The same simulation run against the scheme this round replaced, so the multiplier is
     * measured rather than derived. Same floor search, same loss model, same sample -- which
     * since Ep2 means the same colony-anchored box, so the two are still measured over
     * identical terrain.
     */
    private static double legacySpawnDensity(ColonyNoise noise, int chunks, int baseX, int baseZ) {
        RandomSource random = new XoroshiroRandomSource(20260815L);
        long ants = 0;
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            int bandMin = tierMinY(tier);
            int bandMax = tierMaxY(tier);
            int totalWeight = 0;
            for (int[] spawner : LEGACY_SPAWNERS[tier]) {
                totalWeight += spawner[0];
            }
            for (int cx = 0; cx < chunks; cx++) {
                for (int cz = 0; cz < chunks; cz++) {
                    int originX = baseX + cx * 16;
                    int originZ = baseZ + cz * 16;
                    while (random.nextFloat() < LEGACY_CREATURE_PROBABILITY[tier]) {
                        int pick = random.nextInt(totalWeight);
                        int row = 0;
                        for (int running = 0; row < LEGACY_SPAWNERS[tier].length; row++) {
                            running += LEGACY_SPAWNERS[tier][row][0];
                            if (pick < running) {
                                break;
                            }
                        }
                        int[] spawner = LEGACY_SPAWNERS[tier][Math.min(row, LEGACY_SPAWNERS[tier].length - 1)];
                        int groupSize = ColonyGeneratorTunables.between(spawner[1], spawner[2], random);
                        int x = originX + random.nextInt(16);
                        int z = originZ + random.nextInt(16);
                        for (int member = 0; member < groupSize; member++) {
                            boolean placed = false;
                            for (int attempt = 0;
                                    !placed && attempt < ColonyGeneratorTunables.SPAWN_FLOOR_ATTEMPTS; attempt++) {
                                placed = hasFloor(noise, x, z, bandMin, bandMax, random);
                                x = Math.max(originX, Math.min(originX + 15, x + random.nextInt(7) - 3));
                                z = Math.max(originZ, Math.min(originZ + 15, z + random.nextInt(7) - 3));
                            }
                            if (placed && row != LEGACY_LARVA_ROW[tier]) {
                                ants++;
                            }
                        }
                    }
                }
            }
        }
        return (double) ants / (chunks * chunks);
    }

    /** {@code ColonyChunkGenerator#findFloor} against the pure field: air, air, solid. */
    private static boolean hasFloor(ColonyNoise noise, int x, int z, int bandMin, int bandMax,
            RandomSource random) {
        Col col = col(noise, x, z);
        int start = bandMin + random.nextInt(bandMax - bandMin);
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? start : bandMax - 1;
            for (int y = from; y > bandMin; y--) {
                if (air(noise, col, x, y, z) && air(noise, col, x, y + 1, z) && !air(noise, col, x, y - 1, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Per-tier fabric mix: the number behind "the Royal Depths must not read like the
     * Nurseries". Sampled over solid blocks only, since the fabric of a carved-away block is
     * not something anyone sees.
     */
    private static void palette(ColonyNoise noise) {
        System.out.printf(Locale.ROOT, "%nsolid palette by tier (over %dx%d blocks):%n",
                SAMPLE_RADIUS, SAMPLE_RADIUS);
        System.out.println("  tier                packedSoil  amberEarth  deepLoam  hardenedSoil  resinBlock");
        long[][] counts = new long[TIER_COUNT][5];
        int half = SAMPLE_RADIUS / 2;
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                Col col = col(noise, x, z);
                for (int y = FLOOR_TOP; y < CEILING_BOTTOM; y++) {
                    if (!air(noise, col, x, y, z)) {
                        counts[tierIndex(y)][noise.fabricKind(x, y, z)]++;
                    }
                }
            }
        }
        for (int tier = TIER_COUNT - 1; tier >= 0; tier--) {
            long total = 0;
            for (long count : counts[tier]) {
                total += count;
            }
            System.out.printf(Locale.ROOT, "  %d %-16s %9.1f%% %10.1f%% %8.1f%% %12.1f%% %11.1f%%%n",
                    tier, tierName(tier),
                    100.0 * counts[tier][ColonyNoise.FABRIC_PACKED_SOIL] / total,
                    100.0 * counts[tier][ColonyNoise.FABRIC_AMBER_EARTH] / total,
                    100.0 * counts[tier][ColonyNoise.FABRIC_DEEP_LOAM] / total,
                    100.0 * counts[tier][ColonyNoise.FABRIC_HARDENED_SOIL] / total,
                    100.0 * counts[tier][ColonyNoise.FABRIC_RESIN_BLOCK] / total);
        }
    }

    /**
     * Vertical slice: X across, Y up. Solid is drawn with the tier's glyph, noise-carved air
     * is blank, and ramp air is {@code o} so the connectivity spine is visible as a spiral.
     */
    private static void crossSectionXY(ColonyNoise noise, int z, int centreX) {
        System.out.printf(Locale.ROOT,
                "%nvertical slice at z=%d, x in [%d,%d), y top-down ('o' = ramp air, '_' = ramp floor):%n",
                z, centreX - 24, centreX + 24);
        for (int y = MIN_Y + HEIGHT - 1; y >= MIN_Y; y--) {
            StringBuilder row = new StringBuilder();
            for (int x = centreX - 24; x < centreX + 24; x++) {
                Col col = col(noise, x, z);
                if (!air(noise, col, x, y, z)) {
                    row.append(noise.shaftState(col.shafts(), x, y, z) == ColonyNoise.SHAFT_SOLID
                            ? '_' : glyph(tierIndex(y)));
                } else {
                    row.append(noise.shaftState(col.shafts(), x, y, z) == ColonyNoise.SHAFT_AIR ? 'o' : ' ');
                }
            }
            System.out.printf(Locale.ROOT, "y%3d |%s|%n", y, row);
        }
    }

    /**
     * Horizontal slice at one Y -- shows tunnel widths and chamber spans in plan view.
     *
     * <p>Centred on a caller-supplied point since play-test round 2 rather than on the
     * origin: with ramps and chambers both gated on the colony field, a plan view of the
     * wilds is a page of worm tunnels that says nothing about either.
     */
    private static void crossSectionXZ(ColonyNoise noise, int y, int centreX, int centreZ) {
        System.out.printf(Locale.ROOT, "%nplan slice at y=%d (tier %d %s), x in [%d,%d), z in [%d,%d):%n",
                y, tierIndex(y), tierName(tierIndex(y)), centreX - 60, centreX + 60, centreZ - 60, centreZ + 60);
        for (int z = centreZ - 60; z < centreZ + 60; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = centreX - 60; x < centreX + 60; x++) {
                Col col = col(noise, x, z);
                if (!air(noise, col, x, y, z)) {
                    row.append(noise.shaftState(col.shafts(), x, y, z) == ColonyNoise.SHAFT_SOLID ? '_' : '#');
                } else {
                    row.append(noise.shaftState(col.shafts(), x, y, z) == ColonyNoise.SHAFT_AIR ? 'o' : ' ');
                }
            }
            System.out.printf(Locale.ROOT, "z%4d |%s|%n", z, row);
        }
    }

    private static char glyph(int tier) {
        return switch (tier) {
            case 3 -> '.';
            case 2 -> ':';
            case 1 -> '=';
            default -> '#';
        };
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case 2 -> "FungalGardens";
            case 1 -> "Nurseries";
            default -> "RoyalDepths";
        };
    }

    private NoiseProbe() {
    }
}
