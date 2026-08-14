package com.nogal.formicary.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Wolf-style ally targeting for a Pheromone Horn summon (spec section 3): defend the
 * summoner, and attack what the summoner attacks.
 *
 * <p>Vanilla's {@code OwnerHurtByTargetGoal} / {@code OwnerHurtTargetGoal} do exactly this
 * but are typed against {@code TamableAnimal}, which a wild {@link SoldierAntEntity} is not
 * -- and making it one for the sake of a 60-second summon would drag in taming, sitting,
 * breeding and an owner NBT round-trip for a mob that is deleted before any of it matters.
 * So this is the same two rules written once, against a summoner {@link Player} resolved
 * through the level.
 *
 * <p>It reads {@code getLastHurtByMob} / {@code getLastHurtMob} with their timestamps the
 * way the vanilla pair does, so an ally does not re-adopt a grudge the summoner has already
 * finished with, and it never proposes another of the same summoner's allies.
 */
public class AlliedSoldierTargetGoal extends Goal {
    private final SoldierAntEntity soldier;

    @Nullable
    private LivingEntity candidate;

    /** Snapshot of the summoner's hurt timestamp, so one grudge is only adopted once. */
    private int timestamp;

    public AlliedSoldierTargetGoal(SoldierAntEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.soldier.isAllied()) {
            return false;
        }
        Player summoner = this.resolveSummoner();
        if (summoner == null) {
            return false;
        }

        LivingEntity attacker = summoner.getLastHurtByMob();
        LivingEntity victim = summoner.getLastHurtMob();
        int stamp;
        if (attacker != null && summoner.getLastHurtByMobTimestamp() != this.timestamp) {
            this.candidate = attacker;
            stamp = summoner.getLastHurtByMobTimestamp();
        } else if (victim != null && summoner.getLastHurtMobTimestamp() != this.timestamp) {
            this.candidate = victim;
            stamp = summoner.getLastHurtMobTimestamp();
        } else {
            return false;
        }

        if (!this.isEngageable(this.candidate, summoner)) {
            this.candidate = null;
            return false;
        }
        this.timestamp = stamp;
        return true;
    }

    @Override
    public void start() {
        this.soldier.setTarget(this.candidate);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.soldier.getTarget();
        return this.soldier.isAllied() && target != null && target.isAlive()
                && this.soldier.canAttack(target);
    }

    /**
     * Never the summoner, never another summon of theirs, never a tamed ant of theirs, and
     * never something already dead.
     */
    private boolean isEngageable(@Nullable LivingEntity target, Player summoner) {
        if (target == null || !target.isAlive() || target == summoner) {
            return false;
        }
        if (target instanceof SoldierAntEntity other && other.isSummonedBy(summoner)) {
            return false;
        }
        if (target instanceof TamedAnt) {
            return false;
        }
        return this.soldier.canAttack(target);
    }

    @Nullable
    private Player resolveSummoner() {
        java.util.UUID id = this.soldier.getSummonerUUID();
        return id == null ? null : this.soldier.level().getPlayerByUUID(id);
    }
}
