package com.nogal.formicary.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.nogal.formicary.ModSoundEvents;
import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class WorkerAntEntity extends PathfinderMob implements CarriesItem {

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28);
    }

    private static final String TAG_FLEE_TIME = "FleeTime";
    private static final String TAG_FLEEING_FROM = "FleeingFrom";

    private int fleeTicks;

    @Nullable
    private UUID fleeFrom;

    public WorkerAntEntity(EntityType<? extends WorkerAntEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FleeOffenderGoal(this));
        this.goalSelector.addGoal(2, new RelocateItemGoal(this, 1.1, 8.0));
        this.goalSelector.addGoal(3, new IdleAtFungusGoal(this, 1.0, 8));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public void startFleeingFrom(Player offender) {
        this.fleeTicks = ColonyAnger.WORKER_FLEE_TICKS;
        this.fleeFrom = offender.getUUID();
    }

    public boolean isFleeing() {
        return this.fleeTicks > 0;
    }

    public int getFleeTicks() {
        return this.fleeTicks;
    }

    public boolean isFleeingFrom(LivingEntity entity) {
        return this.isFleeing() && (this.fleeFrom == null || this.fleeFrom.equals(entity.getUUID()));
    }

    public void stopFleeing() {
        this.fleeTicks = 0;
        this.fleeFrom = null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!this.level().isClientSide && this.fleeTicks > 0) {
            this.fleeTicks--;
            if (this.fleeTicks == 0) {
                this.fleeFrom = null;
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt(TAG_FLEE_TIME, this.fleeTicks);
        if (this.fleeFrom != null) {
            compound.putUUID(TAG_FLEEING_FROM, this.fleeFrom);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.fleeTicks = compound.getInt(TAG_FLEE_TIME);
        this.fleeFrom = compound.hasUUID(TAG_FLEEING_FROM) ? compound.getUUID(TAG_FLEEING_FROM) : null;
    }

    @Override
    public ItemStack getCarriedItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public boolean isCarrying() {
        return !this.getCarriedItem().isEmpty();
    }

    public void pickUpCarriedItem(ItemEntity itemEntity) {
        if (this.isCarrying() || itemEntity.getItem().isEmpty()) {
            return;
        }
        ItemStack stack = itemEntity.getItem().copy();
        this.onItemPickup(itemEntity);
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        this.take(itemEntity, stack.getCount());
        itemEntity.discard();
        this.playSound(SoundEvents.ITEM_PICKUP, 0.25F, 1.6F);
    }

    public void dropCarriedItem() {
        ItemStack stack = this.getCarriedItem();
        if (stack.isEmpty()) {
            return;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!this.level().isClientSide) {
            ItemEntity dropped = new ItemEntity(this.level(), this.getX(), this.getY() + 0.2, this.getZ(), stack);
            dropped.setDeltaMovement(Vec3.ZERO);
            dropped.setPickUpDelay(20);
            this.level().addFreshEntity(dropped);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSoundEvents.WORKER_AMBIENT_CLICK.get();
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
        return 0.4F;
    }
}
