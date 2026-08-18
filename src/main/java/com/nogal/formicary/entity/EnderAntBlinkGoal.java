package com.nogal.formicary.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The repositioning half of the ender ant's teleport (spec section 5, "teleports 8-16
 * blocks to close distance"): once a quarry has stayed at least
 * {@link #BLINK_MIN_DISTANCE} blocks away for {@link #BLINK_DELAY_TICKS} ticks, the ant
 * stops walking at it and blinks in.
 *
 * <p>The delay is what makes it read as an ambusher rather than a stutter: a target that is
 * briefly far away is one the melee goal is already closing on, and a mob that teleported
 * every time the gap opened would never appear to walk anywhere.
 *
 * <p><b>Why the timer is kept as a {@code tickCount} stamp rather than a counter.</b>
 * {@code Mob.serverAiStep} runs {@code goalSelector.tick()} -- and therefore every
 * non-running goal's {@code canUse()} -- only on alternating ticks
 * ({@code (tickCount + getId()) % 2}); the other tick gets {@code tickRunningGoals(false)}.
 * Counting calls here would silently mean 80 game ticks, not 40. Reading the mob's own
 * {@code tickCount} makes the threshold the number of ticks it says it is. (Banked in
 * {@code docs/gotchas/entity-ai.md}.)
 *
 * <p>The goal claims no {@link Goal.Flag}s. It is a one-shot: {@link #canContinueToUse()}
 * is always false, so it does the teleport in {@link #start()} and stands down in the same
 * tick, and taking {@code MOVE} for that single tick would only interrupt whatever
 * {@code MeleeAttackGoal} was doing to no purpose.
 */
public class EnderAntBlinkGoal extends Goal {

    /**
     * How far a target has to be before a blink is considered at all. 8 is the ant's own
     * {@link EnderAntEntity#TELEPORT_MIN_DISTANCE}: closer than one blink's length, and
     * teleporting could only overshoot.
     */
    public static final double BLINK_MIN_DISTANCE = EnderAntEntity.TELEPORT_MIN_DISTANCE;

    /** How long the gap has to hold, in game ticks. Two seconds. */
    public static final int BLINK_DELAY_TICKS = 40;

    /** {@link #farSince} value meaning "the gap is not currently open". */
    private static final int NOT_FAR = -1;

    private final EnderAntEntity ant;

    /** The ant's {@code tickCount} when the current far-away stretch started. */
    private int farSince = NOT_FAR;

    public EnderAntBlinkGoal(EnderAntEntity ant) {
        this.ant = ant;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.ant.getTarget();
        if (target == null || !target.isAlive()
                || this.ant.distanceToSqr(target) < BLINK_MIN_DISTANCE * BLINK_MIN_DISTANCE) {
            this.farSince = NOT_FAR;
            return false;
        }
        if (this.farSince == NOT_FAR) {
            this.farSince = this.ant.tickCount;
            return false;
        }
        return this.ant.tickCount - this.farSince >= BLINK_DELAY_TICKS;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = this.ant.getTarget();
        if (target != null) {
            this.ant.teleportTowards(target);
        }
        // Reset whether or not the blink found a spot: a failed one still spent its window,
        // and retrying every other tick in a sealed pocket would be a per-tick 16-candidate
        // search for as long as the target stayed visible.
        this.farSince = NOT_FAR;
    }

    /** Ticks the current far-away stretch has been open, or {@code -1} if it is not. Test seam. */
    public int getFarTicks() {
        return this.farSince == NOT_FAR ? -1 : this.ant.tickCount - this.farSince;
    }
}
