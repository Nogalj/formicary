package com.nogal.formicary.entity;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Work mode's third leg (play-test round 1, spec item 2): a harvesting worker also
 * collects loose item drops lying on the ground nearby, carries them visibly (the
 * carried-item render layer already exists -- {@code WorkerAntCarriedItemLayer} pattern)
 * and delivers them home through the same {@link DepositToChestGoal} trip the crop
 * harvest already uses.
 *
 * <p>Ranked below {@link HarvestCropsGoal} on purpose ({@code goalSelector} priority 3
 * against harvest's 2). {@code WrappedGoal.canBeReplacedBy} only lets a goal with a
 * strictly <em>lower</em> priority number interrupt one that is already running (verified
 * in {@code GoalSelector.java}/{@code WrappedGoal.java}), so a crop always wins a tick
 * where both a ripe crop and a stray item are available, and a harvest that becomes
 * possible mid-approach to a drop preempts this goal outright rather than the two fighting
 * over the {@code MOVE}/{@code LOOK} flags. {@link DepositToChestGoal} outranks both (1),
 * so a full pack is always emptied before either resumes.
 *
 * <p>Items still inside their vanilla pickup delay -- a player's own fresh toss, or a
 * death drop -- are skipped, the same filter {@link RelocateItemGoal} already uses for the
 * wild worker: a tamed worker should not be seen snatching something out from under a
 * player (or a wild worker) that only just set it down.
 */
public class CollectDroppedItemsGoal extends Goal {
    /** How close the worker has to be before it can pick up: 1.5 blocks, squared. */
    private static final double PICKUP_RANGE_SQR = 2.25;

    /**
     * How far the goal will close on its own once the path is spent: 3 blocks, squared.
     *
     * <p>Bounds the last-stretch nudge in {@link #tick()} to the gap a finished path can
     * actually leave (at most about 2.2 blocks -- see there), so the nudge stays a final step
     * and never becomes a substitute for pathfinding that could steer the ant off a ledge
     * toward a drop it has no route to.
     */
    private static final double NUDGE_RANGE_SQR = 9.0;

    /** How far above/below the worker the search box reaches. */
    private static final double SEARCH_VERTICAL_REACH = 4.0;

    /** Give up on an unreachable drop rather than pushing at a wall forever. */
    private static final int APPROACH_TIMEOUT_TICKS = 200;

    /** Ticks between path recalculations while walking to the drop. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final TamedWorkerAntEntity ant;
    private final double speedModifier;

    private int approachTicks;
    private int repathTicks;

    @Nullable
    private ItemEntity target;

    public CollectDroppedItemsGoal(TamedWorkerAntEntity ant, double speedModifier) {
        this.ant = ant;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.ant.isBound() || this.ant.isPackFull() || !(this.ant.level() instanceof ServerLevel)) {
            return false;
        }
        this.target = this.findNearbyDrop();
        return this.target != null;
    }

    @Nullable
    private ItemEntity findNearbyDrop() {
        List<ItemEntity> drops = this.ant.level().getEntitiesOfClass(
                ItemEntity.class,
                this.ant.getBoundingBox().inflate(TamedWorkerAntEntity.GROUND_PICKUP_RADIUS,
                        SEARCH_VERTICAL_REACH, TamedWorkerAntEntity.GROUND_PICKUP_RADIUS),
                drop -> drop.isAlive() && !drop.hasPickUpDelay() && !drop.getItem().isEmpty());
        ItemEntity closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity drop : drops) {
            double distance = this.ant.distanceToSqr(drop);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = drop;
            }
        }
        return closest;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null
                && this.target.isAlive()
                && !this.target.getItem().isEmpty()
                && this.ant.isBound()
                && !this.ant.isPackFull()
                && this.approachTicks < APPROACH_TIMEOUT_TICKS;
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
            this.ant.getNavigation().moveTo(this.target, this.speedModifier);
        }
    }

    @Override
    public void tick() {
        if (this.target == null || !(this.ant.level() instanceof ServerLevel level)) {
            return;
        }
        this.approachTicks++;
        this.ant.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.ant.distanceToSqr(this.target) <= PICKUP_RANGE_SQR) {
            this.ant.pickUpGroundItem(level, this.target);
            this.target = null;
            this.ant.getNavigation().stop();
            return;
        }

        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_INTERVAL_TICKS;
            this.ant.getNavigation().moveTo(this.target, this.speedModifier);
        }

        // The walk and the pickup used to be settled on different terms, and the gap between
        // them was a permanent deadlock. `PathNavigation.moveTo(Entity, speed)` paths to the
        // drop's BLOCK with accuracy 1, and `followThePath` retires the final node once the
        // ant is within `maxDistanceToWaypoint` of it (bbWidth/2 = 0.45 for this 0.9-wide
        // mob) -- so "arrived" can legitimately leave the ant a whole block short, up to
        // about 2.2 away. PICKUP_RANGE_SQR is 1.5. Land in that band and nothing above ever
        // moves the ant again: `moveTo` opens with `if (this.isDone()) return false;` without
        // touching the mob, so the repath above is a no-op and APPROACH_TIMEOUT_TICKS only
        // restarts the same stalemate. Whether a given drop lands in the band is luck --
        // vanilla's `ItemEntity(Level, x, y, z, ItemStack)` gives every drop a random +-0.1
        // horizontal kick -- which is exactly why it read as a flake.
        //
        // So close the last stretch with the move control: the same primitive
        // `PathNavigation.tick()` itself uses to push the mob at its next waypoint, aimed at
        // the drop rather than at a node that stops short of it. The approach timeout still
        // bounds a drop that genuinely cannot be reached. (`docs/gotchas/entity-ai.md`.)
        if (this.ant.getNavigation().isDone()
                && this.ant.distanceToSqr(this.target) <= NUDGE_RANGE_SQR) {
            this.ant.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(),
                    this.target.getZ(), this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.ant.getNavigation().stop();
    }
}
