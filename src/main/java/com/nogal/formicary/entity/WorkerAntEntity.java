package com.nogal.formicary.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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

/**
 * The colony's ambient worker (spec section 3). Cat-sized, harmless: it wanders the
 * tunnels, lingers at fungus and occasionally relocates a loose item drop.
 *
 * <p>Anger/flee behaviour and the chitin drop belong to M3 and are deliberately absent.
 *
 * <p>Item handling: a worker carries at most one stack, held in its main hand so the
 * renderer's carried-item layer can draw it. Nothing it picks up is ever destroyed --
 * {@link RelocateItemGoal} always ends by calling {@link #dropCarriedItem()}, and the
 * main-hand slot is given a guaranteed drop chance so {@code Mob.dropCustomDeathLoot}
 * spits the stack back out if the ant is killed mid-carry.
 */
public class WorkerAntEntity extends PathfinderMob {
    public WorkerAntEntity(EntityType<? extends WorkerAntEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Mob.createMobAttributes() already supplies FOLLOW_RANGE 16.
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RelocateItemGoal(this, 1.1, 8.0));
        this.goalSelector.addGoal(3, new IdleAtFungusGoal(this, 1.0, 8));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------ carrying --

    /** The stack this worker is currently ferrying, or empty. */
    public ItemStack getCarriedItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public boolean isCarrying() {
        return !this.getCarriedItem().isEmpty();
    }

    /**
     * Takes the whole stack off the ground. Mirrors vanilla's {@code Fox.pickUpItem}
     * bookkeeping ({@code onItemPickup} + {@code take} + {@code discard}) so the pickup
     * animation and statistics behave, but keeps the entire stack rather than one item.
     */
    public void pickUpCarriedItem(ItemEntity itemEntity) {
        if (this.isCarrying() || itemEntity.getItem().isEmpty()) {
            return;
        }
        ItemStack stack = itemEntity.getItem().copy();
        this.onItemPickup(itemEntity);
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        // 2.0F drop chance => Mob.dropCustomDeathLoot always returns it on death.
        this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        this.take(itemEntity, stack.getCount());
        itemEntity.discard();
        this.playSound(SoundEvents.ITEM_PICKUP, 0.25F, 1.6F);
    }

    /** Puts the carried stack back on the ground where the ant stands. Never destroys it. */
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

    /**
     * Colony inhabitants stay put, the way every vanilla {@code Animal} does. Left at
     * {@code Mob}'s default a worker would evaporate past 128 blocks -- and, once
     * {@code noActionTime} built up while it idled, randomly past 32 -- which is wrong
     * for a resident and makes it look broken while testing from a spawn egg.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // -------------------------------------------------------------- sounds --

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_STEP;
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
