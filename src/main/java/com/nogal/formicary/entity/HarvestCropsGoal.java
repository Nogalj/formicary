package com.nogal.formicary.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Work mode's cutting half (spec section 4): find the nearest ripe crop inside the patrol
 * radius, walk to it, harvest and replant it -- once. The load then goes home
 * ({@link DepositToChestGoal} outranks this goal and needs nothing but a non-empty pack),
 * and the next crop is chosen on the next trip.
 *
 * <p>{@link CropScanner} sweeps the whole patrol area on every call rather than a slice of
 * it. That is affordable because of where the call happens: the scan runs inside
 * {@link #canUse()}, and once this goal or the higher-priority deposit goal is running
 * {@code GoalSelector} stops asking, so under the one-crop loop a full sweep is paid for
 * about once per round trip instead of continuously.
 */
public class HarvestCropsGoal extends Goal {
    /** How close the worker has to be before it can cut: 2 blocks, squared. */
    private static final double REACH_SQR = 4.0;

    /** Give up on an unreachable crop rather than standing on a wall forever. */
    private static final int APPROACH_TIMEOUT_TICKS = 200;

    /** Ticks between path recalculations while walking to the crop. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final TamedWorkerAntEntity ant;
    private final double speedModifier;
    private final CropScanner scanner;

    private int approachTicks;
    private int repathTicks;

    @Nullable
    private BlockPos target;

    public HarvestCropsGoal(TamedWorkerAntEntity ant, double speedModifier) {
        this.ant = ant;
        this.speedModifier = speedModifier;
        this.scanner = new CropScanner(TamedWorkerAntEntity.PATROL_RADIUS,
                TamedWorkerAntEntity.PATROL_VERTICAL_REACH);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** The scanner backing this goal -- exposed so its choice can be asserted in a test. */
    public CropScanner getScanner() {
        return this.scanner;
    }

    @Override
    public boolean canUse() {
        BlockPos anchor = this.ant.getBoundChest();
        if (anchor == null || this.ant.isPackFull() || !(this.ant.level() instanceof ServerLevel)) {
            return false;
        }
        this.target = this.scanner.scan(this.ant.level(), anchor);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null
                && this.ant.isBound()
                && !this.ant.isPackFull()
                && this.approachTicks < APPROACH_TIMEOUT_TICKS
                && CropHarvest.isHarvestable(this.ant.level().getBlockState(this.target));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.approachTicks = 0;
        this.repathTicks = 0;
        if (this.target != null) {
            this.ant.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(),
                    this.target.getZ() + 0.5, this.speedModifier);
        }
    }

    @Override
    public void tick() {
        if (this.target == null || !(this.ant.level() instanceof ServerLevel level)) {
            return;
        }
        this.approachTicks++;
        this.ant.getLookControl().setLookAt(this.target.getX() + 0.5, this.target.getY() + 0.5,
                this.target.getZ() + 0.5);

        if (this.ant.distanceToSqr(this.target.getX() + 0.5, this.target.getY() + 0.5,
                this.target.getZ() + 0.5) <= REACH_SQR) {
            this.ant.harvest(level, this.target);
            this.target = null;
            this.ant.getNavigation().stop();
            return;
        }

        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_INTERVAL_TICKS;
            this.ant.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(),
                    this.target.getZ() + 0.5, this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.ant.getNavigation().stop();
    }
}
