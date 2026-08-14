package com.nogal.formicary.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * A Pheromone Horn summon keeps up with whoever blew the horn.
 *
 * <p>Without this an ally holds the ground it materialised on, which for a 60-second escort
 * means it is behind you for most of its life. Vanilla's {@code FollowOwnerGoal} is again
 * {@code TamableAnimal}-only (see {@link AlliedSoldierTargetGoal}), so this is the small
 * version: path to the summoner when they get far enough away, stop when close, and never
 * interrupt a fight -- it stands down while the soldier has a target.
 *
 * <p>No teleport-to-owner: allies live a minute, and a summon that blinks through a wall to
 * reach you would be a worse surprise than one that gets left behind.
 */
public class AlliedFollowSummonerGoal extends Goal {
    /** Start following past this distance from the summoner. */
    private static final double START_DISTANCE = 10.0;

    /** Stop once this close. */
    private static final double STOP_DISTANCE = 4.0;

    private final SoldierAntEntity soldier;
    private final double speedModifier;

    @Nullable
    private Player summoner;

    private int repathCooldown;

    public AlliedFollowSummonerGoal(SoldierAntEntity soldier, double speedModifier) {
        this.soldier = soldier;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.soldier.isAllied() || this.soldier.getTarget() != null) {
            return false;
        }
        Player candidate = this.resolveSummoner();
        if (candidate == null || candidate.isSpectator()) {
            return false;
        }
        if (this.soldier.distanceToSqr(candidate) < START_DISTANCE * START_DISTANCE) {
            return false;
        }
        this.summoner = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.summoner != null && this.soldier.isAllied() && this.soldier.getTarget() == null
                && !this.soldier.getNavigation().isDone()
                && this.soldier.distanceToSqr(this.summoner) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.summoner = null;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.summoner == null) {
            return;
        }
        this.soldier.getLookControl().setLookAt(this.summoner, 10.0F, this.soldier.getMaxHeadXRot());
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = this.adjustedTickDelay(10);
        this.soldier.getNavigation().moveTo(this.summoner, this.speedModifier);
    }

    @Nullable
    private Player resolveSummoner() {
        java.util.UUID id = this.soldier.getSummonerUUID();
        return id == null ? null : this.soldier.level().getPlayerByUUID(id);
    }
}
