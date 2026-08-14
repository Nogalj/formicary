package com.nogal.formicary.worldgen;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FLOOR_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MIN_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.TIER_COUNT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierIndex;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMaxY;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierMinY;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;

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
            membranes(noise);
            connectivity(noise);
            thrones(noise);
        }
        if (what.equals("membrane")) {
            membranes(noise);
        }
        if (what.equals("throne")) {
            thrones(noise);
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
        field(noise, "membrane (v > t, 2D)", false, (x, y, z) -> noise.probeMembrane(x, z));
    }

    /** Candidate {@code MEMBRANE_THRESHOLD} values the sweep below reports on. */
    private static final double[] MEMBRANE_THRESHOLD_LADDER = {-0.10, 0.00, 0.10, 0.20, 0.30, 0.40, 0.50};

    /**
     * The M5 acceptance criterion for the exit: from anywhere under the Upper Galleries
     * ceiling, how far is the nearest <i>visible</i> Daylight Membrane?
     *
     * <p>Two numbers matter and neither is the raw field coverage. Visible coverage is the
     * fraction of ceiling columns that actually turn into membrane, which the patch field
     * alone badly overstates: only about an eighth of the ceiling in this dimension has air
     * under it at all, and a patch anywhere else is a decoration nobody ever sees. Distance
     * is a multi-source BFS over <b>every</b> column -- straight-line-ish (Manhattan), not a
     * walk -- reported over the exposed columns only. Straight line rather than a path
     * because the spec's "one patch reachable within ~40-60 blocks of any point" is a
     * spatial-density statement; the ceiling's exposed columns are islands in plan view and
     * measuring 4-connectivity between them says nothing about whether a player can get
     * there (they walk on the floor, tens of blocks below).
     *
     * <p>Every candidate threshold is reported in one pass so the live constant can be
     * chosen against measured numbers rather than nudged and re-run.
     */
    private static void membranes(ColonyNoise noise) {
        int span = SAMPLE_RADIUS * 2;
        boolean[] exposed = new boolean[span * span];
        double[] field = new double[span * span];
        ColonyNoise.Shaft[] cached = null;
        ColonyNoise.Throne[] cachedThrones = null;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;

        int exposedCount = 0;
        for (int ix = 0; ix < span; ix++) {
            int x = ix - SAMPLE_RADIUS;
            for (int iz = 0; iz < span; iz++) {
                int z = iz - SAMPLE_RADIUS;
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                    cached = noise.shaftsNear(chunkX << 4, chunkZ << 4);
                    cachedThrones = noise.thronesNear(chunkX << 4, chunkZ << 4);
                    cachedChunkX = chunkX;
                    cachedChunkZ = chunkZ;
                }
                ColonyNoise.Shaft[] col = noise.shaftsForColumn(cached, x, z);
                ColonyNoise.Throne[] tcol = noise.thronesForColumn(cachedThrones, x, z);
                int i = ix * span + iz;
                exposed[i] = noise.isAir(col, tcol, x, CEILING_BOTTOM - 1, z);
                field[i] = noise.probeMembrane(x, z);
                if (exposed[i]) {
                    exposedCount++;
                }
            }
        }

        System.out.printf(Locale.ROOT, "%ndaylight membranes over %dx%d blocks (scale %.4f):%n",
                span, span, ColonyGeneratorTunables.MEMBRANE_XZ_SCALE);
        System.out.printf(Locale.ROOT, "  ceiling with air under it: %5.2f%% of columns%n",
                100.0 * exposedCount / (span * span));
        System.out.println("  threshold  fieldCover  visibleCover   dist-to-nearest (blocks, over exposed columns)");
        for (double threshold : MEMBRANE_THRESHOLD_LADDER) {
            membraneRow(span, exposed, exposedCount, field, threshold);
        }
        System.out.printf(Locale.ROOT, "  live MEMBRANE_THRESHOLD = %.2f%n",
                ColonyGeneratorTunables.MEMBRANE_THRESHOLD);
    }

    private static void membraneRow(int span, boolean[] exposed, int exposedCount, double[] field, double threshold) {
        int[] dist = new int[span * span];
        Arrays.fill(dist, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        int fieldCount = 0;
        int patchCount = 0;
        for (int i = 0; i < dist.length; i++) {
            if (field[i] > threshold) {
                fieldCount++;
                if (exposed[i]) {
                    patchCount++;
                    dist[i] = 0;
                    queue.add(i);
                }
            }
        }
        if (patchCount == 0) {
            System.out.printf(Locale.ROOT, "  %8.2f  %9.2f%%  %11.2f%%   (no visible patch in sample)%n",
                    threshold, 100.0 * fieldCount / dist.length, 0.0);
            return;
        }

        while (!queue.isEmpty()) {
            int i = queue.poll();
            int ix = i / span;
            int iz = i % span;
            for (int d = 0; d < 4; d++) {
                int nx = ix + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int nz = iz + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= span || nz >= span) {
                    continue;
                }
                int j = nx * span + nz;
                if (dist[j] >= 0) {
                    continue;
                }
                dist[j] = dist[i] + 1;
                queue.add(j);
            }
        }

        int[] sorted = new int[exposedCount];
        int n = 0;
        long sum = 0;
        for (int i = 0; i < dist.length; i++) {
            if (exposed[i]) {
                sorted[n++] = dist[i];
                sum += dist[i];
            }
        }
        Arrays.sort(sorted, 0, n);
        System.out.printf(Locale.ROOT,
                "  %8.2f  %9.2f%%  %11.2f%%   mean %5.1f  median %3d  p95 %3d  max %3d%n",
                threshold, 100.0 * fieldCount / dist.length, 100.0 * patchCount / dist.length,
                (double) sum / n, sorted[n / 2], sorted[(int) (n * 0.95)], sorted[n - 1]);
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

        ColonyNoise.Shaft[] cached = null;
        ColonyNoise.Throne[] cachedThrones = null;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;

        for (int x = -SAMPLE_RADIUS; x < SAMPLE_RADIUS; x++) {
            for (int z = -SAMPLE_RADIUS; z < SAMPLE_RADIUS; z++) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                    cached = noise.shaftsNear(chunkX << 4, chunkZ << 4);
                    cachedThrones = noise.thronesNear(chunkX << 4, chunkZ << 4);
                    cachedChunkX = chunkX;
                    cachedChunkZ = chunkZ;
                }
                ColonyNoise.Shaft[] col = noise.shaftsForColumn(cached, x, z);
                ColonyNoise.Throne[] tcol = noise.thronesForColumn(cachedThrones, x, z);
                boolean belowAir = false;
                for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
                    int tier = tierIndex(y);
                    total[tier]++;
                    boolean isAir = noise.isAir(col, tcol, x, y, z);
                    if (isAir) {
                        air[tier]++;
                        if (noise.shaftState(col, x, y, z) == ColonyNoise.SHAFT_AIR) {
                            shaft[tier]++;
                        } else if (noise.isTunnelCarved(x, y, z)) {
                            tunnel[tier]++;
                        } else {
                            chamber[tier]++;
                        }
                        if (!belowAir && y + 1 < MIN_Y + HEIGHT && noise.isAir(col, tcol, x, y + 1, z)) {
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
        ColonyNoise.Shaft[] cached = null;
        ColonyNoise.Throne[] cachedThrones = null;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        for (int ix = 0; ix < SLAB; ix++) {
            for (int iz = 0; iz < SLAB; iz++) {
                int x = ix - half;
                int z = iz - half;
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                    cached = noise.shaftsNear(chunkX << 4, chunkZ << 4);
                    cachedThrones = noise.thronesNear(chunkX << 4, chunkZ << 4);
                    cachedChunkX = chunkX;
                    cachedChunkZ = chunkZ;
                }
                ColonyNoise.Shaft[] col = noise.shaftsForColumn(cached, x, z);
                ColonyNoise.Throne[] tcol = noise.thronesForColumn(cachedThrones, x, z);
                for (int y = 0; y < HEIGHT; y++) {
                    air[index(ix, y, iz)] = noise.isAir(col, tcol, x, MIN_Y + y, z);
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
        int centreX = (int) Math.round(throne.centreX());
        int centreZ = (int) Math.round(throne.centreZ());
        int axisX = (int) Math.round(throne.axisX());
        int axisZ = (int) Math.round(throne.axisZ());
        int minX = Math.min(centreX, axisX) - 24;
        int minZ = Math.min(centreZ, axisZ) - 24;
        int span = Math.max(Math.max(centreX, axisX) - minX, Math.max(centreZ, axisZ) - minZ) + 25;

        boolean[] air = new boolean[span * span * HEIGHT];
        for (int ix = 0; ix < span; ix++) {
            int x = minX + ix;
            for (int iz = 0; iz < span; iz++) {
                int z = minZ + iz;
                ColonyNoise.Shaft[] col = noise.shaftsForColumn(
                        noise.shaftsNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
                ColonyNoise.Throne[] tcol = noise.thronesForColumn(
                        noise.thronesNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
                for (int y = 0; y < HEIGHT; y++) {
                    air[(ix * HEIGHT + y) * span + iz] = noise.isAir(col, tcol, x, MIN_Y + y, z);
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
                if (Math.hypot(x - throne.axisX(), z - throne.axisZ()) > ColonyGeneratorTunables.SHAFT_MAX_REACH) {
                    continue;
                }
                ColonyNoise.Shaft[] col = noise.shaftsForColumn(
                        noise.shaftsNear(x - Math.floorMod(x, 16), z - Math.floorMod(z, 16)), x, z);
                for (int y = 1; y < HEIGHT - 1; y++) {
                    int i = (ix * HEIGHT + y) * span + iz;
                    if (standable[i] && !seen[i]
                            && noise.shaftState(col, x, MIN_Y + y, z) == ColonyNoise.SHAFT_AIR) {
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

        int daisTop = throne.floorY() + ColonyGeneratorTunables.THRONE_DAIS_HEIGHT + 1;
        int daisIndex = ((centreX - minX) * HEIGHT + (daisTop - MIN_Y)) * span + (centreZ - minZ);
        boolean reached = seen[daisIndex];

        // Count only the room's own Y band, so the number is about the chamber rather than
        // about every noise pocket that happens to share its footprint.
        int roomFloors = 0;
        int roomReached = 0;
        int interiorTop = throne.floorY() + ColonyGeneratorTunables.THRONE_WALL_HEIGHT
                + ColonyGeneratorTunables.THRONE_DOME_HEIGHT;
        for (int ix = 0; ix < span; ix++) {
            int x = minX + ix;
            for (int iz = 0; iz < span; iz++) {
                int z = minZ + iz;
                if (Math.hypot(x - throne.centreX(), z - throne.centreZ()) > ColonyGeneratorTunables.THRONE_RADIUS) {
                    continue;
                }
                for (int y = throne.floorY() + 1; y <= interiorTop; y++) {
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
                "  nearest chamber: centre (%d, %d) floor y=%d, dais top y=%d%n"
                        + "  ramp walkway seeds: %d; standable floors in the room's own band: %d, reached: %d%n",
                centreX, centreZ, throne.floorY(), daisTop, seeds, roomFloors, roomReached);
        System.out.println(reached
                ? "  PASS: the queen's dais is walkable from the connectivity ramp (edges are symmetric, so also back out)."
                : "  FAIL: the throne chamber does not join the ramp on foot.");
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
                ColonyNoise.Shaft[] col = noise.shaftsForColumn(noise.shaftsNear(x & ~15, z & ~15), x, z);
                ColonyNoise.Throne[] tcol = noise.thronesForColumn(noise.thronesNear(x & ~15, z & ~15), x, z);
                if (!noise.isAir(col, tcol, x, y, z)) {
                    row.append(noise.shaftState(col, x, y, z) == ColonyNoise.SHAFT_SOLID ? '_' : glyph(tierIndex(y)));
                } else {
                    row.append(noise.shaftState(col, x, y, z) == ColonyNoise.SHAFT_AIR ? 'o' : ' ');
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
                ColonyNoise.Shaft[] col = noise.shaftsForColumn(noise.shaftsNear(x & ~15, z & ~15), x, z);
                ColonyNoise.Throne[] tcol = noise.thronesForColumn(noise.thronesNear(x & ~15, z & ~15), x, z);
                if (!noise.isAir(col, tcol, x, y, z)) {
                    row.append(noise.shaftState(col, x, y, z) == ColonyNoise.SHAFT_SOLID ? '_' : '#');
                } else {
                    row.append(noise.shaftState(col, x, y, z) == ColonyNoise.SHAFT_AIR ? 'o' : ' ');
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
