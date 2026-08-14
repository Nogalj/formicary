package com.nogal.formicary.entity;

import javax.annotation.Nullable;

import com.nogal.formicary.item.ModItemTags;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
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
 * <p>M6 gives the larva a second life as a <em>placed</em> larva: put the item down in the
 * overworld ({@code item.LarvaItem}) and it comes back as this same entity carrying the
 * {@link #isPlaced()} flag. A placed larva stops wandering (the wriggle is animation, not
 * AI, so it keeps that), and becomes feedable -- and what you feed it decides which caste
 * it grows into:
 * <ul>
 *   <li>Royal Jelly -&gt; {@link TamedWorkerAntEntity}</li>
 *   <li>anything in {@code #formicary:raw_ant_food} -&gt; {@link TamedSoldierAntEntity}</li>
 * </ul>
 * Either way the feeding player becomes the owner, and growth is instant.
 *
 * <p>Feeding is gated on the placed flag on purpose: a larva still in the colony's nursery
 * is the colony's brood, not yours. You capture it first, then it is yours to raise.
 * Capture, though, works on both -- a placed larva picked back up is just an item again,
 * which is the symmetric (and un-lose-able) reading of "place it down".
 */
public class LarvaEntity extends PathfinderMob {
    private static final String TAG_PLACED = "Placed";

    private static final EntityDataAccessor<Boolean> DATA_PLACED =
            SynchedEntityData.defineId(LarvaEntity.class, EntityDataSerializers.BOOLEAN);

    public LarvaEntity(EntityType<? extends LarvaEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.12);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PLACED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // A placed larva sits where it was put. Gating the stroll goal rather than setting
        // NoAI keeps gravity, water escape and the client-side wriggle all working.
        WaterAvoidingRandomStrollGoal stroll = new WaterAvoidingRandomStrollGoal(this, 0.4) {
            @Override
            public boolean canUse() {
                return !LarvaEntity.this.isPlaced() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !LarvaEntity.this.isPlaced() && super.canContinueToUse();
            }
        };
        stroll.setInterval(400);
        this.goalSelector.addGoal(5, stroll);
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // Passive: no target selectors registered, a larva never fights back or flees.
    }

    // -------------------------------------------------------------- placed --

    /** Whether this larva was set down by a player rather than born in the nursery. */
    public boolean isPlaced() {
        return this.entityData.get(DATA_PLACED);
    }

    public void setPlaced(boolean placed) {
        this.entityData.set(DATA_PLACED, placed);
    }

    // ------------------------------------------------------------ the fork --

    /**
     * Which caste {@code food} raises a placed larva into, or {@code null} if it is not
     * larva food at all.
     *
     * <p>Static and public because this <em>is</em> the diet rule the spec cares about;
     * a test asserting it should read the rule, not an entity's side effects.
     */
    @Nullable
    public static EntityType<? extends TamableAnimal> casteFor(ItemStack food) {
        if (food.is(ModItems.ROYAL_JELLY.get())) {
            return ModEntities.TAMED_WORKER_ANT.get();
        }
        if (food.is(ModItemTags.RAW_ANT_FOOD)) {
            return ModEntities.TAMED_SOLDIER_ANT.get();
        }
        return null;
    }

    /**
     * Replaces this larva with a freshly owned ant of {@code type} at the same spot.
     *
     * @return the grown ant, or {@code null} if the entity type refused to build one
     */
    @Nullable
    public TamableAnimal growInto(ServerLevel level, EntityType<? extends TamableAnimal> type, Player owner) {
        TamableAnimal grown = type.create(level);
        if (grown == null) {
            return null;
        }
        grown.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        grown.setPersistenceRequired();
        grown.tame(owner);
        level.addFreshEntity(grown);

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.3, this.getZ(),
                14, 0.35, 0.35, 0.35, 0.02);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEEHIVE_EXIT,
                SoundSource.NEUTRAL, 0.8F, 1.1F);
        this.discard();
        return grown;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (hand == InteractionHand.MAIN_HAND && this.isPlaced() && !player.isShiftKeyDown()) {
            EntityType<? extends TamableAnimal> caste = casteFor(held);
            if (caste != null) {
                if (this.level() instanceof ServerLevel level) {
                    if (this.growInto(level, caste, player) != null) {
                        held.consume(1, player);
                    }
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && held.isEmpty()) {
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

    // ---------------------------------------------------------- persistence --

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean(TAG_PLACED, this.isPlaced());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setPlaced(compound.getBoolean(TAG_PLACED));
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
