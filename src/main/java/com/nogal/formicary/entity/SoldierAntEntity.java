package com.nogal.formicary.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The colony's aggro responder (spec section 3): larger and armored, wanders near
 * chamber entrances, and swarms whoever angers the colony in melee.
 *
 * <p>M3a scope: only the retaliate-when-hit target goal is wired up. The colony-wide
 * anger system (M3b) replaces/augments this so soldiers respond to ANY offender the
 * colony is angry at, not just whoever last hit them personally.
 */
public class SoldierAntEntity extends PathfinderMob {
    public SoldierAntEntity(EntityType<? extends SoldierAntEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // M3b: colony anger system hooks in here -- for now a soldier only retaliates
        // against whatever last hurt it, via the vanilla HurtByTargetGoal below.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /**
     * Colony inhabitants stay put, the way every vanilla {@code Animal} does -- see
     * {@link WorkerAntEntity#removeWhenFarAway} for the full reasoning.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // -------------------------------------------------------------- sounds --

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }
}
