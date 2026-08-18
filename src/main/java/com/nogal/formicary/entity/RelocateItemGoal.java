package com.nogal.formicary.entity;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ambient colony tidying: every half-minute or so a worker notices a loose item drop,
 * walks over, hoists it, carries it around for a few seconds and then sets it down again.
 *
 * <p>The goal is deliberately conservative about items: it only ever moves a stack from
 * one patch of floor to another. {@link #stop()} always drops what is carried, whichever
 * way the goal ends, and {@link WorkerAntEntity#pickUpCarriedItem} marks the main-hand
 * slot as a guaranteed drop so a worker killed mid-errand returns the stack too.
 *
 * <p>Not modelled on {@code Mob.canPickUpLoot}/{@code Mob.aiStep}'s automatic looting
 * sweep on purpose: that sweep would have the ant hoover up anything it happened to walk
 * past, which is a very different (and much noisier) behaviour than the spec's occasional
 * relocation errand.
 */
public class RelocateItemGoal extends Goal {
    private static final int COOLDOWN_MIN_TICKS = 600;      // 30s
    private static final int COOLDOWN_SPREAD_TICKS = 600;   // .. up to 60s
    private static final int APPROACH_TIMEOUT_TICKS = 200;
    private static final int CARRY_TICKS = 140;             // ~7s of ferrying
    private static final double PICKUP_RANGE_SQR = 2.25;    // 1.5 blocks
    private static final double NUDGE_RANGE_SQR = 9.0;      // last-stretch close: 3 blocks
    private static final int WANDER_RADIUS = 10;
    private static final int WANDER_VERTICAL = 7;

    private final WorkerAntEntity ant;
    private final double speedModifier;
    private final double searchRadius;

    private int cooldown;
    private int approachTicks;
    private int carryTicks;
    private int repathTicks;
    @Nullable
    private ItemEntity target;

    public RelocateItemGoal(WorkerAntEntity ant, double speedModifier, double searchRadius) {
        this.ant = ant;
        this.speedModifier = speedModifier;
        this.searchRadius = searchRadius;
        this.cooldown = ant.getRandom().nextInt(COOLDOWN_SPREAD_TICKS);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * Zeroes the between-errands cooldown. The constructor seeds it randomly (up to 30s of
     * {@code canUse} calls) so a chamber full of workers does not tidy in lockstep -- which
     * also makes "when does the next errand start" unpinnable for a test. Exposed the same
     * way {@link HarvestCropsGoal#getScanner()} is: it only collapses the wait; the errand
     * itself still runs through {@code canUse}/{@link #start()}/{@link #tick()} unchanged.
     */
    public void skipCooldown() {
        this.cooldown = 0;
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.ant.isCarrying()) {
            return false;
        }
        this.target = this.findNearbyDrop();
        return this.target != null;
    }

    @Nullable
    private ItemEntity findNearbyDrop() {
        List<ItemEntity> drops = this.ant.level().getEntitiesOfClass(
                ItemEntity.class,
                this.ant.getBoundingBox().inflate(this.searchRadius, 4.0, this.searchRadius),
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
    public void start() {
        this.approachTicks = 0;
        this.carryTicks = 0;
        this.repathTicks = 0;
        if (this.target != null) {
            this.ant.getNavigation().moveTo(this.target, this.speedModifier);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.ant.isCarrying()) {
            return this.carryTicks < CARRY_TICKS;
        }
        return this.target != null
                && this.target.isAlive()
                && !this.target.getItem().isEmpty()
                && this.approachTicks < APPROACH_TIMEOUT_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.ant.isCarrying()) {
            this.carryTicks++;
            if (this.ant.getNavigation().isDone()) {
                Vec3 wander = DefaultRandomPos.getPos(this.ant, WANDER_RADIUS, WANDER_VERTICAL);
                if (wander != null) {
                    this.ant.getNavigation().moveTo(wander.x, wander.y, wander.z, this.speedModifier);
                }
            }
            return;
        }

        if (this.target == null) {
            return;
        }
        this.approachTicks++;
        this.ant.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.ant.distanceToSqr(this.target) <= PICKUP_RANGE_SQR) {
            this.ant.pickUpCarriedItem(this.target);
            this.target = null;
            this.ant.getNavigation().stop();
            return;
        }

        if (this.repathTicks-- <= 0) {
            this.repathTicks = 10;
            this.ant.getNavigation().moveTo(this.target, this.speedModifier);
        }

        // The walk and the pickup are settled on different terms, and the gap between them
        // used to be a permanent deadlock -- the same one fixed in CollectDroppedItemsGoal
        // (2026-08-18, mechanism in docs/gotchas/entity-ai.md). `moveTo(Entity, speed)`
        // paths to the drop's BLOCK with accuracy 1, and `followThePath` retires the final
        // node from bbWidth/2 = 0.45 out, so "arrived" can legitimately leave the ant up to
        // ~2.2 blocks from a drop PICKUP_RANGE_SQR wants within 1.5. Land in that band and
        // nothing above ever moves the ant again: `moveTo(Path, speed)` opens with
        // `if (this.isDone()) return false;` without touching the mob, so the repath is a
        // no-op and the ant stands beside the drop until APPROACH_TIMEOUT_TICKS scraps the
        // errand. In play that read as the ant idly changing its mind about a stray item,
        // which is why it was never filed as a bug.
        //
        // Close the last stretch with the move control -- the same primitive
        // `PathNavigation.tick()` itself uses to push the mob at its next waypoint -- bounded
        // by NUDGE_RANGE_SQR so it stays a final step and never a pathfinding substitute
        // that could walk the ant off a ledge toward a drop it has no route to.
        if (this.ant.getNavigation().isDone()
                && this.ant.distanceToSqr(this.target) <= NUDGE_RANGE_SQR) {
            this.ant.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(),
                    this.target.getZ(), this.speedModifier);
        }
    }

    @Override
    public void stop() {
        // Whatever ended the errand -- timeout, carry expiry, a higher-priority goal --
        // the ant never keeps the stack.
        this.ant.dropCarriedItem();
        this.ant.getNavigation().stop();
        this.target = null;
        this.cooldown = COOLDOWN_MIN_TICKS + this.ant.getRandom().nextInt(COOLDOWN_SPREAD_TICKS);
    }
}
