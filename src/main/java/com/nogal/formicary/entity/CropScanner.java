package com.nogal.formicary.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * The tick budget for a tamed worker's crop search (spec section 4: "throttle its
 * scanning -- don't scan the full radius every tick").
 *
 * <p>A full patrol area is {@code (2r+1)^2} block columns -- 1089 of them at the spec's
 * ~16-block radius -- and reading all of them, several Y levels deep, once per tick would
 * be tens of thousands of {@code getBlockState} calls a second per ant. Instead the
 * scanner keeps a persistent cursor into that grid and each call advances it by
 * {@link #columnsPerScan()} columns only, wrapping round to column 0 at the end. Work is
 * therefore constant per call and a whole sweep is spread over
 * {@code ceil(columns / budget)} calls.
 *
 * <p>The cursor deliberately does <em>not</em> reset when a crop is found: the next search
 * carries on from where this one stopped, so a worker standing in a big field works its
 * way around the field instead of re-reading the same corner every time.
 *
 * <p>No entity, no level mutation and no randomness -- a GameTest drives this directly.
 */
public class CropScanner {
    private final int radius;
    private final int columnsPerScan;
    private final int verticalReach;

    private int cursor;

    /**
     * @param radius         patrol radius in blocks around the anchor
     * @param columnsPerScan how many columns a single {@link #scan} call may read
     * @param verticalReach  how far above and below the anchor's Y each column is read
     */
    public CropScanner(int radius, int columnsPerScan, int verticalReach) {
        if (radius < 0 || columnsPerScan < 1 || verticalReach < 0) {
            throw new IllegalArgumentException("radius/verticalReach must be >= 0 and columnsPerScan >= 1");
        }
        this.radius = radius;
        this.columnsPerScan = columnsPerScan;
        this.verticalReach = verticalReach;
    }

    /** Total columns in the patrol area -- {@code (2r+1)^2}. */
    public int columnCount() {
        int width = this.radius * 2 + 1;
        return width * width;
    }

    /** How many columns one {@link #scan} call reads. */
    public int columnsPerScan() {
        return this.columnsPerScan;
    }

    /** How many {@link #scan} calls a full sweep of the patrol area takes. */
    public int scansPerSweep() {
        return (this.columnCount() + this.columnsPerScan - 1) / this.columnsPerScan;
    }

    /** Where the cursor currently sits, in {@code [0, columnCount())}. Test seam. */
    public int cursor() {
        return this.cursor;
    }

    /** Sends the cursor back to the start -- used when the worker's anchor changes. */
    public void reset() {
        this.cursor = 0;
    }

    /**
     * Reads the next {@link #columnsPerScan()} columns around {@code anchor} and returns
     * the first ripe crop found, or {@code null} if this slice of the sweep held none.
     *
     * <p>Each column is read top-down so a worker under a two-level garden takes the upper
     * crop first rather than tunnelling to the lowest one.
     */
    @Nullable
    public BlockPos scan(BlockGetter level, BlockPos anchor) {
        int width = this.radius * 2 + 1;
        int total = width * width;
        for (int step = 0; step < this.columnsPerScan; step++) {
            int column = this.cursor;
            this.cursor = (this.cursor + 1) % total;

            int dx = column % width - this.radius;
            int dz = column / width - this.radius;
            for (int dy = this.verticalReach; dy >= -this.verticalReach; dy--) {
                BlockPos pos = anchor.offset(dx, dy, dz);
                if (CropHarvest.isHarvestable(level.getBlockState(pos))) {
                    return pos;
                }
            }
        }
        return null;
    }
}
