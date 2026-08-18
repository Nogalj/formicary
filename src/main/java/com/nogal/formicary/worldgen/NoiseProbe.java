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
import java.util.Deque;
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
 * </pre>
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
            thrones(noise);
            nurseries(noise);
            gardens(noise);
            combPatches(noise);
            spawnDensity(noise);
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
        if (what.equals("comb")) {
            combPatches(noise);
        }
        if (what.equals("spawns")) {
            spawnDensity(noise);
        }
        if (what.equals("palette")) {
            palette(noise);
        }
        if (what.equals("all") || what.equals("slices")) {
            // Slice straight through the ramp axis nearest the origin, so the connectivity
            // spine shows up in section rather than being missed between two shafts.
            ColonyNoise.Shaft[] near = noise.shaftsNear(0, 0);
            ColonyNoise.Shaft closest = near[0];
            for (ColonyNoise.Shaft s : near) {
                if (Math.hypot(s.axisX(), s.axisZ()) < Math.hypot(closest.axisX(), closest.axisZ())) {
                    closest = s;
                }
            }
            System.out.printf(Locale.ROOT, "%nnearest ramp axis: (%.1f, %.1f)%n", closest.axisX(), closest.axisZ());
            crossSectionXY(noise, (int) Math.round(closest.axisZ()), (int) Math.round(closest.axisX()));
            crossSectionXZ(noise, 168);
            crossSectionXZ(noise, 120);
            crossSectionXZ(noise, 72);
            crossSectionXZ(noise, 24);
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
                    stacked &= noise.isDaylightMembrane(col.shafts(), col.thrones(), col.nurseries(), col.gardens(),
                            x, CEILING_BOTTOM + layer, z);
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
     * is gated behind full Chitin Armor) must be able to WALK from the Upper Galleries to
     * the Royal Depths and back.
     *
     * <p>So this is not an air flood fill. It builds the graph of standable positions --
     * air at y and y+1 with something solid at y-1 -- and only connects neighbouring
     * columns when the standable heights differ by at most one block. That edge rule is
     * symmetric, so any path it finds is walkable in both directions by construction: a
     * one-block rise is a jump, a one-block fall is reversible. Anything steeper is
     * excluded even though a player could survive falling down it.
     */
    private static void connectivity(ColonyNoise noise) {
        int half = SLAB / 2;
        boolean[] air = new boolean[SLAB * SLAB * HEIGHT];
        for (int ix = 0; ix < SLAB; ix++) {
            for (int iz = 0; iz < SLAB; iz++) {
                int x = ix - half;
                int z = iz - half;
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
        // the Upper Galleries as a whole connect down, not whether one arbitrary ledge does.
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
                "%nwalkable connectivity (%dx%dx%d slab, step height 1, symmetric):%n"
                        + "  standable positions: %d, reachable on foot from the Upper Galleries: %d (%.1f%%)%n"
                        + "  deepest standable Y reached = %d   (dimension floor cap top = %d, Royal Depths = y[%d,%d))%n",
                SLAB, SLAB, HEIGHT, standCount, reached, 100.0 * reached / standCount,
                MIN_Y + deepest, FLOOR_TOP, tierMinY(0), tierMaxY(0));
        System.out.println(MIN_Y + deepest < tierMaxY(0)
                ? "  PASS: the Royal Depths are reachable on foot from the top tier (and back, edges are symmetric)."
                : "  FAIL: cannot walk from the Upper Galleries into the Royal Depths.");
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
                "%nthrone chambers (one per %d-block cell, radius %.0f, interior %d tall):%n",
                ColonyGeneratorTunables.THRONE_SPACING, ColonyGeneratorTunables.THRONE_RADIUS,
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
        throneWalk(noise, closest);
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

    /** The four per-column feature arrays {@link ColonyNoise#isAir} needs, resolved once. */
    private record Col(ColonyNoise.Shaft[] shafts, ColonyNoise.Throne[] thrones,
            ColonyNoise.Nursery[] nurseries, ColonyNoise.Garden[] gardens) {
    }

    private static Col col(ColonyNoise noise, int x, int z) {
        int chunkX = x - Math.floorMod(x, 16);
        int chunkZ = z - Math.floorMod(z, 16);
        return new Col(noise.shaftsForColumn(noise.shaftsNear(chunkX, chunkZ), x, z),
                noise.thronesForColumn(noise.thronesNear(chunkX, chunkZ), x, z),
                noise.nurseriesForColumn(noise.nurseriesNear(chunkX, chunkZ), x, z),
                noise.gardensForColumn(noise.gardensNear(chunkX, chunkZ), x, z));
    }

    private static boolean air(ColonyNoise noise, Col col, int x, int y, int z) {
        return noise.isAir(col.shafts(), col.thrones(), col.nurseries(), col.gardens(), x, y, z);
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

        double perThrone = Math.pow((double) ColonyGeneratorTunables.THRONE_SPACING
                / ColonyGeneratorTunables.NURSERY_SPACING, 2.0);
        System.out.printf(Locale.ROOT, "  density: %.2f per 1000x1000 blocks, i.e. %.1fx the throne chambers%n",
                1.0e6 / Math.pow(ColonyGeneratorTunables.NURSERY_SPACING, 2.0), perThrone);

        // Every chamber in a wide sample, checked against the band it must stay inside.
        int cells = 8;
        int inBand = 0;
        int total = 0;
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
                minFloor = Math.min(minFloor, nursery.floorY());
                maxTop = Math.max(maxTop, top);
                if (nursery.floorY() - shell >= tierMinY(1) && top < tierMaxY(1)) {
                    inBand++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  %d chambers sampled: floor y in [%d, %d], highest shell top y=%d; %d of %d wholly inside "
                        + "the Nurseries band y[%d,%d)%n",
                total, minFloor, maxTop - ColonyGeneratorTunables.NURSERY_WALL_HEIGHT
                        - ColonyGeneratorTunables.NURSERY_DOME_HEIGHT - shell,
                maxTop, inBand, total, tierMinY(1), tierMaxY(1));
        System.out.println(inBand == total
                ? "  PASS: every sampled chamber sits wholly inside the Nurseries tier."
                : "  FAIL: a chamber crosses a tier boundary.");

        ColonyNoise.Nursery[] near = noise.nurseriesNear(0, 0);
        ColonyNoise.Nursery closest = near[0];
        for (ColonyNoise.Nursery nursery : near) {
            double distance = Math.hypot(nursery.centreX(), nursery.centreZ());
            System.out.printf(Locale.ROOT,
                    "  centre (%7.1f, %7.1f)  floorY %3d  ramp axis (%7.1f, %7.1f)  %6.1f blocks from origin%n",
                    nursery.centreX(), nursery.centreZ(), nursery.floorY(), nursery.axisX(), nursery.axisZ(),
                    distance);
            if (distance < Math.hypot(closest.centreX(), closest.centreZ())) {
                closest = nursery;
            }
        }

        int reached = 0;
        for (ColonyNoise.Nursery nursery : near) {
            if (chamberWalk(noise, "nursery", nursery.centreX(), nursery.centreZ(), nursery.axisX(),
                    nursery.axisZ(), nursery.floorY(), nursery.floorY() + 1, ColonyGeneratorTunables.NURSERY_RADIUS,
                    nursery.floorY() + ColonyGeneratorTunables.NURSERY_WALL_HEIGHT
                            + ColonyGeneratorTunables.NURSERY_DOME_HEIGHT,
                    "the brood floor")) {
                reached++;
            }
        }
        System.out.printf(Locale.ROOT, "  %d of %d chambers around the origin are walkable from their ramp%n",
                reached, near.length);
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
        System.out.printf(Locale.ROOT, "  density: %.2f per 1000x1000 blocks, i.e. %.1fx the nursery chambers%n",
                1.0e6 / Math.pow(ColonyGeneratorTunables.GARDEN_SPACING, 2.0), perNursery);

        // Band containment, exactly like nurseries()'s own sweep.
        int cells = 8;
        int inBand = 0;
        int total = 0;
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
                minFloor = Math.min(minFloor, garden.floorY());
                maxTop = Math.max(maxTop, top);
                if (garden.floorY() - shell >= tierMinY(2) && top < tierMaxY(2)) {
                    inBand++;
                }
            }
        }
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

        ColonyNoise.Garden[] near = noise.gardensNear(0, 0);
        ColonyNoise.Garden closest = near[0];
        for (ColonyNoise.Garden garden : near) {
            double distance = Math.hypot(garden.centreX(), garden.centreZ());
            System.out.printf(Locale.ROOT,
                    "  centre (%7.1f, %7.1f)  floorY %3d  ramp axis (%7.1f, %7.1f)  %6.1f blocks from origin%n",
                    garden.centreX(), garden.centreZ(), garden.floorY(), garden.axisX(), garden.axisZ(), distance);
            if (distance < Math.hypot(closest.centreX(), closest.centreZ())) {
                closest = garden;
            }
        }

        int reached = 0;
        for (ColonyNoise.Garden garden : near) {
            if (chamberWalk(noise, "garden", garden.centreX(), garden.centreZ(), garden.axisX(),
                    garden.axisZ(), garden.floorY(), garden.floorY() + 1, ColonyGeneratorTunables.GARDEN_RADIUS,
                    garden.floorY() + ColonyGeneratorTunables.GARDEN_WALL_HEIGHT
                            + ColonyGeneratorTunables.GARDEN_DOME_HEIGHT,
                    "the garden floor")) {
                reached++;
            }
        }
        System.out.printf(Locale.ROOT, "  %d of %d chambers around the origin are walkable from their ramp%n",
                reached, near.length);
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
        System.out.printf(Locale.ROOT,
                "%ncomb patches over %dx%d blocks (patch scale %.2f/%.2f):%n",
                span, span, ColonyGeneratorTunables.COMB_PATCH_XZ_SCALE,
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
            for (int ix = 0; ix < span; ix++) {
                int x = ix - half;
                for (int iz = 0; iz < span; iz++) {
                    int z = iz - half;
                    Col col = col(noise, x, z);
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
                        if (!noise.isCombPatch(ix - half, bandMin + y, iz - half)) {
                            continue;
                        }
                        double roll = rolls.nextDouble();
                        if (roll < ColonyGeneratorTunables.ROYAL_COMB_CHANCE_BY_TIER[tier]
                                + ColonyGeneratorTunables.BROOD_COMB_CHANCE_BY_TIER[tier]) {
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
     */
    private static void spawnDensity(ColonyNoise noise) {
        int chunks = 16;
        RandomSource random = new XoroshiroRandomSource(20260815L);
        System.out.printf(Locale.ROOT, "%nchunk-generation spawn density over %d chunks:%n", chunks * chunks);
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
                    int originX = cx * 16;
                    int originZ = cz * 16;
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
        double legacy = legacySpawnDensity(noise, chunks);
        System.out.printf(Locale.ROOT,
                "  all tiers: %.2f ants per chunk against the pre-round-1 scheme's %.2f  =  %.2fx%n",
                total, legacy, total / legacy);
    }

    // The pre-round-1 spawning parameters, read out of the four biome JSONs. Kept ONLY as
    // the baseline the density change is measured against -- nothing in the mod reads them.
    // Indexed by tier; the weighted lists are {weight, minCount, maxCount} per caste, and
    // the larva row of the Nurseries is excluded because the comparison is about ants.
    private static final double[] LEGACY_CREATURE_PROBABILITY = {0.10, 0.20, 0.16, 0.12};
    private static final int[][][] LEGACY_SPAWNERS = {
        {{10, 1, 3}},                        // Royal Depths:    soldier only
        {{8, 2, 3}, {12, 2, 3}, {6, 1, 3}},  // Nurseries:       worker, soldier, LARVA
        {{12, 2, 4}, {5, 1, 2}},             // Fungal Gardens:  worker, soldier
        {{12, 2, 4}, {2, 1, 1}},             // Upper Galleries: worker, soldier
    };
    /** Index into a tier's {@link #LEGACY_SPAWNERS} row that is the larva, or -1. */
    private static final int[] LEGACY_LARVA_ROW = {-1, 2, -1, -1};

    /**
     * The same simulation run against the scheme this round replaced, so the multiplier is
     * measured rather than derived. Same floor search, same loss model, same sample.
     */
    private static double legacySpawnDensity(ColonyNoise noise, int chunks) {
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
                    int originX = cx * 16;
                    int originZ = cz * 16;
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

    /** Horizontal slice at one Y -- shows tunnel widths and chamber spans in plan view. */
    private static void crossSectionXZ(ColonyNoise noise, int y) {
        System.out.printf(Locale.ROOT, "%nplan slice at y=%d (tier %d %s), x/z in [-60,60):%n",
                y, tierIndex(y), tierName(tierIndex(y)));
        for (int z = -60; z < 60; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = -60; x < 60; x++) {
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
            case 3 -> "UpperGalleries";
            case 2 -> "FungalGardens";
            case 1 -> "Nurseries";
            default -> "RoyalDepths";
        };
    }

    private NoiseProbe() {
    }
}
