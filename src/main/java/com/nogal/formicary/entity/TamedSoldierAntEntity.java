package com.nogal.formicary.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The other half of the diet fork (spec section 4): a larva fed raw meat becomes a
 * wolf-style bodyguard instead of a farmhand.
 *
 * <p>Two modes, toggled by sneak-right-clicking it:
 * <ul>
 *   <li><b>Escort</b> (default) -- follows its owner, answers whatever hurts them, and
 *       attacks whatever they attack.</li>
 *   <li><b>Guard post</b> -- holds the ground it was standing on and engages anything
 *       inside {@link GuardPostTargetGoal#GUARD_RADIUS} that has taken aim at the owner or
 *       at the ant itself.</li>
 * </ul>
 *
 * <p>Vanilla's sit/stay ({@code orderedToSit}) is deliberately <em>not</em> what backs
 * guard-post mode. {@code OwnerHurtByTargetGoal} and {@code OwnerHurtTargetGoal} both
 * refuse to run while a tamable is ordered to sit, so a "sitting" guard would be a guard
 * that does not guard -- the exact opposite of what the spec asks a guard post for.
 */
public class TamedSoldierAntEntity extends TamableAnimal implements TamedAnt {
    private static final String TAG_STATIONED = "Stationed";
    private static final String TAG_GUARD_POST = "GuardPost";

    /** Play-test round 2: "I am against a wall this tick". See {@link AntClimbing}. */
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
            SynchedEntityData.defineId(TamedSoldierAntEntity.class, EntityDataSerializers.BOOLEAN);

    private boolean stationed;

    @Nullable
    private BlockPos guardPost;

    public TamedSoldierAntEntity(EntityType<? extends TamedSoldierAntEntity> entityType, Level level) {
        super(entityType, level);
        this.setTame(true, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReturnToGuardPostGoal(this, 1.0));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 10.0F, 2.0F) {
            @Override
            public boolean canUse() {
                return !TamedSoldierAntEntity.this.isStationed() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !TamedSoldierAntEntity.this.isStationed() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !TamedSoldierAntEntity.this.isStationed() && super.canUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Personal retaliation. TamableAnimal.canAttack already refuses the owner, so a
        // misplaced swing from you never turns your own guard on you.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Escort duties: both stand down at a guard post, where holding the ground beats
        // chasing an attacker across the map.
        this.targetSelector.addGoal(2, new OwnerHurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !TamedSoldierAntEntity.this.isStationed() && super.canUse();
            }
        });
        this.targetSelector.addGoal(3, new OwnerHurtTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !TamedSoldierAntEntity.this.isStationed() && super.canUse();
            }
        });
        this.targetSelector.addGoal(4, new GuardPostTargetGoal(this));
    }

    // ------------------------------------------------------------ climbing --

    /** @see AntClimbing */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return AntClimbing.navigation(this, level);
    }

    /** @see AntClimbing */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CLIMBING, false);
    }

    /**
     * Round 5, item 1: gated rather than bare {@code horizontalCollision}, exactly as on the
     * tamed worker -- an escorting soldier that brushes a wall should not walk up it.
     * @see AntClimbing#tamedClimbFlag
     */
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_CLIMBING, AntClimbing.tamedClimbFlag(this));
        }
    }

    /** @see AntClimbing */
    @Override
    public boolean onClimbable() {
        return this.entityData.get(DATA_CLIMBING);
    }

    // ------------------------------------------------------------ stations --

    /** Whether this soldier is holding a guard post rather than escorting its owner. */
    public boolean isStationed() {
        return this.stationed;
    }

    /** The block it was standing on when stationed, or {@code null} while escorting. */
    @Nullable
    public BlockPos getGuardPost() {
        return this.guardPost;
    }

    /** Flips between escort and guard-post mode, planting the post where it stands. */
    public void toggleStationed() {
        this.setStationed(!this.stationed);
    }

    public void setStationed(boolean stationed) {
        this.stationed = stationed;
        this.guardPost = stationed ? this.blockPosition() : null;
        if (stationed) {
            this.getNavigation().stop();
        }
    }

    // -------------------------------------------------------------- entity --

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // M8: see TamedWorkerAntEntity.mobInteract for why this outranks the sneak toggle.
        if (FungalBloomFeeding.tryFeed(this, player, hand)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!player.isShiftKeyDown() || !this.isOwnedByUuid(player)) {
            return super.mobInteract(player, hand);
        }
        if (!this.level().isClientSide) {
            this.toggleStationed();
            this.playSound(this.stationed ? SoundEvents.BEEHIVE_ENTER : SoundEvents.BEEHIVE_EXIT, 0.7F, 1.1F);
            // Play-test round 1, spec item 1: "you cannot tell what state they are" --
            // actionbar overlay (not chat) matches vanilla's own way of surfacing
            // transient state without spamming chat. Guarded to the server-side branch
            // above so the client-side call of this same method (mobInteract runs on
            // both sides) never double-sends it.
            player.displayClientMessage(Component.translatable(this.stationed
                    ? "entity.formicary.tamed_soldier_ant.state.guard_post"
                    : "entity.formicary.tamed_soldier_ant.state.escort"), true);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** UUID comparison, not {@code isOwnedBy}: a GameTest's mock player is never in the level. */
    public boolean isOwnedByUuid(Player player) {
        return player.getUUID().equals(this.getOwnerUUID());
    }

    /** Never turn on another of the owner's ants, whichever goal proposed it. */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !(target instanceof TamedAnt);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean(TAG_STATIONED, this.stationed);
        if (this.guardPost != null) {
            compound.putLong(TAG_GUARD_POST, this.guardPost.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.stationed = compound.getBoolean(TAG_STATIONED);
        this.guardPost = compound.contains(TAG_GUARD_POST)
                ? BlockPos.of(compound.getLong(TAG_GUARD_POST))
                : null;
    }

    /** Tamed ants are yours: they never wander off or despawn. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Not a breeding animal -- the only way to get one is the taming loop. */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    /**
     * Play-test round 1, spec item 2: "tamed soldier = soldier". See {@code
     * TamedWorkerAntEntity#getBaseExperienceReward()} for why this has to be an override of
     * {@code Animal}'s method rather than a {@code Mob#xpReward} field assignment.
     */
    @Override
    protected int getBaseExperienceReward() {
        return SoldierAntEntity.XP_REWARD;
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
