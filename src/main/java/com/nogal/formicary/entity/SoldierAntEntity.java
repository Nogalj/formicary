package com.nogal.formicary.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
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
 * <p>M3b makes it the carrier of colony anger. It implements vanilla's
 * {@link NeutralMob}, which is a genuine fit: the state a provoked soldier needs is
 * exactly "anger ticks left + who I'm angry at", and the interface already supplies the
 * NBT round-trip ({@code AngerTime} / {@code AngryAt}), {@link #isAngry()},
 * {@link #isAngryAt}, {@link #stopBeingAngry()} and forgive-on-death semantics.
 *
 * <p>The one vanilla default deliberately NOT used is
 * {@code NeutralMob#updatePersistentAnger}: it re-points the persistent anger target at
 * whatever the mob's current target happens to be, so a stray skeleton arrow would
 * install a mob UUID as "the offender" and, through
 * {@link ColonyAngerTargetGoal}, flip a soldier into colony-anger mode over something
 * the colony never cared about. The countdown in {@link #customServerAiStep()} is a
 * plain decrement instead, and the anger target is only ever set by
 * {@link #angerAt(Player)} -- so it is always a player.
 */
public class SoldierAntEntity extends PathfinderMob implements NeutralMob {
    private int remainingAngerTime;

    @Nullable
    private UUID angerTarget;

    public SoldierAntEntity(EntityType<? extends SoldierAntEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                // Follow range is what TargetGoal measures its search and its
                // give-up distance with, so it has to match the anger radius or an
                // angered soldier would forget a target that is still well inside it.
                .add(Attributes.FOLLOW_RANGE, ColonyAnger.ANGER_RADIUS);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Personal retaliation: anything that hits this soldier specifically.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Colony anger: whoever provoked the colony anywhere inside the radius.
        this.targetSelector.addGoal(2, new ColonyAngerTargetGoal(this));
    }

    // --------------------------------------------------------------- anger --

    /**
     * Marks this soldier hostile to {@code offender} for
     * {@link ColonyAnger#SOLDIER_ANGER_TICKS}. Called by
     * {@code ColonyAnger.provoke} for every soldier inside the radius.
     */
    public void angerAt(Player offender) {
        this.setPersistentAngerTarget(offender.getUUID());
        this.startPersistentAngerTimer();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!this.level().isClientSide && this.remainingAngerTime > 0) {
            this.remainingAngerTime--;
            if (this.remainingAngerTime == 0) {
                this.stopBeingAngry();
            }
        }
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int remainingPersistentAngerTime) {
        this.remainingAngerTime = remainingPersistentAngerTime;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.angerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID persistentAngerTarget) {
        this.angerTarget = persistentAngerTarget;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(ColonyAnger.SOLDIER_ANGER_TICKS);
    }

    // ---------------------------------------------------------- persistence --

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.addPersistentAngerSaveData(compound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.readPersistentAngerSaveData(this.level(), compound);
    }

    /**
     * Colony inhabitants stay put, the way every vanilla {@code Animal} does -- see
     * {@link WorkerAntEntity#removeWhenFarAway} for the full reasoning.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Test seam: the resolved player this soldier is currently angry at, if any. */
    @Nullable
    public Player getAngerTargetPlayer() {
        if (this.angerTarget == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(this.angerTarget) instanceof Player player ? player : null;
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
