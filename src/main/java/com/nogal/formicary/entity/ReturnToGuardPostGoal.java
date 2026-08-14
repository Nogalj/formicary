package com.nogal.formicary.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a stationed tamed soldier on the spot it was planted (spec section 4: "stays where
 * it is").
 *
 * <p>Deliberately a leash rather than a freeze: the guard may step off its post to swing at
 * something -- that is the whole point of a guard -- but as soon as the fight is over it
 * walks back. The tolerance is a few blocks so it is not visibly twitching back to a single
 * block while circling a target.
 */
public class ReturnToGuardPostGoal extends Goal {
    /** How far the guard may drift before it walks back, squared. */
    private static final double LEASH_SQR = 12.0;

    /** Close enough to count as "back on post", squared. */
    private static final double ARRIVED_SQR = 2.0;

    /** Ticks between path recalculations on the way back. */
    private static final int REPATH_INTERVAL_TICKS = 20;

    private final TamedSoldierAntEntity ant;
    private final double speedModifier;

    private int repathTicks;

    public ReturnToGuardPostGoal(TamedSoldierAntEntity ant, double speedModifier) {
        this.ant = ant;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos post = this.ant.getGuardPost();
        return this.ant.isStationed()
                && post != null
                && this.ant.getTarget() == null
                && this.distanceToPostSqr(post) > LEASH_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos post = this.ant.getGuardPost();
        return this.ant.isStationed()
                && post != null
                && this.ant.getTarget() == null
                && this.distanceToPostSqr(post) > ARRIVED_SQR;
    }

    @Override
    public void start() {
        this.repathTicks = 0;
    }

    @Override
    public void tick() {
        BlockPos post = this.ant.getGuardPost();
        if (post == null) {
            return;
        }
        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_INTERVAL_TICKS;
            this.ant.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5,
                    this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.ant.getNavigation().stop();
    }

    private double distanceToPostSqr(BlockPos post) {
        return this.ant.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
    }
}
