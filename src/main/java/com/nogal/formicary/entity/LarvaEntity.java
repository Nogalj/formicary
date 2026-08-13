package com.nogal.formicary.entity;

import com.nogal.formicary.item.ModItems;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Nursery-tier brood (spec section 3): a tiny, harmless grub that wriggles in place
 * near brood comb. Sneak-right-clicking it with an empty hand captures it as a larva
 * item -- the colony's export good, and the M6 taming loop's starting point.
 *
 * <p>M3a scope: the capture interaction only. Nursery-only natural spawning is M4's
 * data-driven biome work; placing the captured item back down is M6's.
 */
public class LarvaEntity extends PathfinderMob {
    public LarvaEntity(EntityType<? extends LarvaEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.12);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        WaterAvoidingRandomStrollGoal stroll = new WaterAvoidingRandomStrollGoal(this, 0.4);
        stroll.setInterval(400);
        this.goalSelector.addGoal(5, stroll);
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // Passive: no target selectors registered, a larva never fights back or flees.
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!this.level().isClientSide) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.5F);
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.LARVA.get()));
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
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
    // No ambient sound: a passive grub wriggling in place should read as silent, not
    // chattering like the worker/soldier.

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }

    @Override
    protected float getSoundVolume() {
        return 0.25F;
    }
}
