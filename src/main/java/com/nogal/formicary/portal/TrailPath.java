package com.nogal.formicary.portal;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

/**
 * A player's own breadcrumb trail through the colony (spec section 2).
 *
 * <p>The spec is explicit that finding the way out is the <b>breadcrumb model, not
 * pathfinding</b>: the route a Trail Pheromone lights up is the route the player actually
 * walked, so it is traversable by construction and costs nothing to compute. This class is
 * both halves of that -- the capped ring buffer that records the walk, and the frozen
 * snapshot + countdown that a used pheromone turns into particles.
 *
 * <p>Two properties are worth stating because they are the whole design:
 *
 * <ul>
 *   <li><b>Samples are spaced, not per-tick.</b> A position is only recorded once the
 *       player is {@link #MIN_SAMPLE_DISTANCE} blocks from the last sample, so standing
 *       still records nothing and {@link #CAPACITY} entries cover a real distance rather
 *       than twenty seconds of jitter.</li>
 *   <li><b>Using a pheromone takes a snapshot.</b> The displayed trail does not read the
 *       live buffer, because the player's next move after using one is to walk <i>back
 *       along it</i> -- which records new samples over the top of exactly the history they
 *       are trying to follow. Freezing the points at use time is what makes the trail
 *       survive being followed.</li>
 * </ul>
 *
 * <p>Held as a non-serialising data attachment (see {@link ModAttachments#TRAIL_PATH}), so
 * it is per-player, server-side, and disappears with the player object.
 */
public final class TrailPath {

    // -------------------------------------------------------------- tunables --

    /** How many positions the ring buffer holds before it starts overwriting. Tunable. */
    public static final int CAPACITY = 512;

    /** How far the player must move from the last sample before another is taken. Tunable. */
    public static final double MIN_SAMPLE_DISTANCE = 2.0;

    /** How long a used Trail Pheromone keeps drawing: 2400 ticks = 2 minutes. Tunable. */
    public static final int TRAIL_DURATION_TICKS = 2400;

    /** How many of the most recent samples a trail retraces. Tunable. */
    public static final int TRAIL_POINTS = 30;

    /** Ticks between particle refreshes while a trail is lit: 20 = once a second. Tunable. */
    public static final int TRAIL_REFRESH_TICKS = 20;

    // ------------------------------------------------------------ ring buffer --

    private final BlockPos[] buffer = new BlockPos[CAPACITY];

    /** Total samples ever recorded. The newest lives at {@code (recorded - 1) % CAPACITY}. */
    private int recorded;

    /** Frozen at the moment a pheromone is used; newest first. Empty when no trail is lit. */
    private List<BlockPos> litTrail = List.of();

    private int litTicksRemaining;

    /**
     * Records {@code pos} if it is at least {@link #MIN_SAMPLE_DISTANCE} from the last
     * sample.
     *
     * @return whether it was recorded
     */
    public boolean record(BlockPos pos) {
        BlockPos previous = newest();
        if (previous != null && previous.distSqr(pos) < MIN_SAMPLE_DISTANCE * MIN_SAMPLE_DISTANCE) {
            return false;
        }
        this.buffer[this.recorded % CAPACITY] = pos;
        this.recorded++;
        return true;
    }

    /** How many samples are retrievable -- the total, capped at {@link #CAPACITY}. */
    public int size() {
        return Math.min(this.recorded, CAPACITY);
    }

    /** The most recently recorded position, or {@code null} if nothing has been recorded. */
    @Nullable
    public BlockPos newest() {
        return this.recorded == 0 ? null : this.buffer[(this.recorded - 1) % CAPACITY];
    }

    /** Up to {@code limit} samples, newest first -- i.e. back the way the player came. */
    public List<BlockPos> newestFirst(int limit) {
        int available = Math.min(size(), limit);
        List<BlockPos> out = new ArrayList<>(available);
        for (int i = 0; i < available; i++) {
            out.add(this.buffer[Math.floorMod(this.recorded - 1 - i, CAPACITY)]);
        }
        return out;
    }

    /** Forgets everything, including any lit trail. Called when a player leaves the colony. */
    public void clear() {
        this.recorded = 0;
        this.litTrail = List.of();
        this.litTicksRemaining = 0;
    }

    // ------------------------------------------------------------- lit trail --

    /**
     * Freezes the most recent {@link #TRAIL_POINTS} samples and starts the countdown.
     *
     * @return {@code false} when there is nothing recorded to retrace, in which case no
     *         trail is lit and the caller must not consume the item
     */
    public boolean light() {
        List<BlockPos> points = newestFirst(TRAIL_POINTS);
        if (points.isEmpty()) {
            return false;
        }
        this.litTrail = points;
        this.litTicksRemaining = TRAIL_DURATION_TICKS;
        return true;
    }

    public boolean isLit() {
        return this.litTicksRemaining > 0;
    }

    public int litTicksRemaining() {
        return this.litTicksRemaining;
    }

    /** The frozen route, newest first. Empty unless {@link #isLit()}. */
    public List<BlockPos> litTrail() {
        return this.litTrail;
    }

    /**
     * Advances the countdown by one tick.
     *
     * @return whether particles should be drawn this tick -- true once every
     *         {@link #TRAIL_REFRESH_TICKS}, including the tick the trail is lit on
     */
    public boolean tickLitTrail() {
        if (this.litTicksRemaining <= 0) {
            return false;
        }
        boolean draw = (TRAIL_DURATION_TICKS - this.litTicksRemaining) % TRAIL_REFRESH_TICKS == 0;
        this.litTicksRemaining--;
        if (this.litTicksRemaining == 0) {
            this.litTrail = List.of();
        }
        return draw;
    }
}
