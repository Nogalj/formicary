package com.nogal.formicary.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * Picks the crop a tamed worker cuts next: the <em>nearest</em> ripe one to its bound
 * chest, anywhere inside the patrol area.
 *
 * <p>Ep2 replaced a tick-budgeted cursor with this flat sweep, and the reason is the
 * one-crop shuttle loop it now serves. The old scanner read a 24-column slice per call and
 * remembered where it stopped, which kept per-tick cost constant while a worker cut its way
 * around a field without ever walking home. Under the shuttle rule a scan happens once per
 * <em>trip</em> -- the worker cuts one crop, walks to the chest, empties out, and only then
 * looks again -- so the search runs at deposit-trip cadence, not tick cadence, and the
 * budget bought nothing but a wrong answer: an incremental cursor returns the first ripe
 * crop it happens to land on, which is routinely across the field from a worker standing at
 * its chest. {@code (2*16+1)^2 * 7 = 7623} block reads once every few seconds is cheap;
 * making it cheaper before anything measures it is not worth the arithmetic.
 *
 * <p>"Nearest" is measured from the anchor rather than from the ant, and those are the same
 * point at the only moment that matters: the shuttle always chooses its next crop just
 * after a deposit, standing at the chest.
 *
 * <p>No entity, no level mutation, no randomness and no state at all -- a GameTest drives
 * this directly.
 */
public class CropScanner {
    private final int radius;
    private final int verticalReach;

    /**
     * @param radius        patrol radius in blocks around the anchor
     * @param verticalReach how far above and below the anchor's Y each column is read
     */
    public CropScanner(int radius, int verticalReach) {
        if (radius < 0 || verticalReach < 0) {
            throw new IllegalArgumentException("radius/verticalReach must be >= 0");
        }
        this.radius = radius;
        this.verticalReach = verticalReach;
    }

    /** Total columns in the patrol area -- {@code (2r+1)^2}. */
    public int columnCount() {
        int width = this.radius * 2 + 1;
        return width * width;
    }

    /**
     * Reads every column around {@code anchor} and returns the ripe crop closest to it, or
     * {@code null} if the patrol area holds none.
     *
     * <p>Each column is read top-down and contributes at most its topmost ripe crop, so a
     * worker under a two-level garden takes the upper plant rather than tunnelling to the
     * lowest one. Between columns the tie-break is squared distance to the anchor, and an
     * exact tie keeps the first column read -- deterministic either way, which is what lets
     * a test assert on the choice.
     */
    @Nullable
    public BlockPos scan(BlockGetter level, BlockPos anchor) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dz = -this.radius; dz <= this.radius; dz++) {
            for (int dx = -this.radius; dx <= this.radius; dx++) {
                for (int dy = this.verticalReach; dy >= -this.verticalReach; dy--) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    if (!CropHarvest.isHarvestable(level.getBlockState(pos))) {
                        continue;
                    }
                    double distance = pos.distSqr(anchor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                    break;
                }
            }
        }
        return best;
    }
}
