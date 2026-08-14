package com.nogal.formicary.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.nogal.formicary.ModSoundEvents;
import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

public class SoldierAntEntity extends PathfinderMob implements NeutralMob {

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, ColonyAnger.ANGER_RADIUS);
    }

    private static final String TAG_SUMMONER = "Summoner";
    private static final String TAG_SUMMON_EXPIRY = "SummonExpiry";

    private int remainingAngerTime;

    @Nullable
    private UUID angerTarget;

    @Nullable
    private UUID summoner;

    private long summonExpiry;

    public SoldierAntEntity(EntityType<? extends SoldierAntEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AlliedFollowSummonerGoal(this, 1.15));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new ColonyAngerTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !SoldierAntEntity.this.isAllied() && super.canUse();
            }
        });
        this.targetSelector.addGoal(3, new DeepTierHostilityGoal(this) {
            @Override
            public boolean canUse() {
                return !SoldierAntEntity.this.isAllied() && super.canUse();
            }
        });
        this.targetSelector.addGoal(4, new TamedAntTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !SoldierAntEntity.this.isAllied() && super.canUse();
            }
        });
        this.targetSelector.addGoal(5, new AlliedSoldierTargetGoal(this));
    }

    public void summonFor(Player summoner, int lifetimeTicks) {
        this.summoner = summoner.getUUID();
        this.summonExpiry = this.level().getGameTime() + lifetimeTicks;
    }

    public boolean isAllied() {
        return this.summoner != null;
    }

    @Nullable
    public UUID getSummonerUUID() {
        return this.summoner;
    }

    public boolean isSummonedBy(Player player) {
        return player.getUUID().equals(this.summoner);
    }

    public long getSummonExpiry() {
        return this.summonExpiry;
    }

    public void disperse() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FALLING_HONEY, this.getX(), this.getY() + 0.3, this.getZ(),
                    18, 0.3, 0.2, 0.3, 0.01);
        }
        this.discard();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isAllied() && target instanceof Player player && this.isSummonedBy(player)) {
            return false;
        }
        return super.canAttack(target);
    }

    public boolean isAngryAtPlayer(Player player) {
        return this.isAngry() && player.getUUID().equals(this.angerTarget);
    }

    public void angerAt(Player offender) {
        this.setPersistentAngerTarget(offender.getUUID());
        this.startPersistentAngerTimer();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.level().isClientSide) {
            return;
        }
        if (this.isAllied() && this.level().getGameTime() >= this.summonExpiry) {
            this.disperse();
            return;
        }
        if (this.remainingAngerTime > 0) {
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

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.addPersistentAngerSaveData(compound);
        if (this.summoner != null) {
            compound.putUUID(TAG_SUMMONER, this.summoner);
            compound.putLong(TAG_SUMMON_EXPIRY, this.summonExpiry);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.readPersistentAngerSaveData(this.level(), compound);
        if (compound.hasUUID(TAG_SUMMONER)) {
            this.summoner = compound.getUUID(TAG_SUMMONER);
            this.summonExpiry = compound.getLong(TAG_SUMMON_EXPIRY);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Nullable
    public Player getAngerTargetPlayer() {
        if (this.angerTarget == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(this.angerTarget) instanceof Player player ? player : null;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSoundEvents.SOLDIER_AMBIENT_CLICK.get();
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
