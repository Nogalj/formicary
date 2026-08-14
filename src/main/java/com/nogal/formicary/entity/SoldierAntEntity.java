package com.nogal.formicary.entity;

import java.util.UUID;

import javax.annotation.Nullable;

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
    private static final String TAG_SUMMONER = "Summoner";
    private static final String TAG_SUMMON_EXPIRY = "SummonExpiry";

    private int remainingAngerTime;

    @Nullable
    private UUID angerTarget;

    /** Set only on a soldier summoned by a Pheromone Horn (M7). See {@link #isAllied()}. */
    @Nullable
    private UUID summoner;

    /** Game time at which a summoned soldier disperses. Meaningless while wild. */
    private long summonExpiry;

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
        this.goalSelector.addGoal(1, new AlliedFollowSummonerGoal(this, 1.15));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Personal retaliation: anything that hits this soldier specifically.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Colony anger: whoever provoked the colony anywhere inside the radius.
        this.targetSelector.addGoal(2, new ColonyAngerTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !SoldierAntEntity.this.isAllied() && super.canUse();
            }
        });
        // Deep-tier hostility: no provocation needed in the Nurseries/Royal Depths (M4b).
        // Lower priority than colony anger, so an already-angry soldier keeps its offender.
        this.targetSelector.addGoal(3, new DeepTierHostilityGoal(this) {
            @Override
            public boolean canUse() {
                return !SoldierAntEntity.this.isAllied() && super.canUse();
            }
        });
        // Tamed ants of a player this soldier is already hostile to (M6). Lowest of the
        // four: given a choice between the trespasser and the trespasser's ant, take the
        // trespasser.
        this.targetSelector.addGoal(4, new TamedAntTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !SoldierAntEntity.this.isAllied() && super.canUse();
            }
        });
        // A horn-summoned ally fights for its summoner instead of for the colony (M7).
        this.targetSelector.addGoal(5, new AlliedSoldierTargetGoal(this));
    }

    // ------------------------------------------------------------- summoned --

    /**
     * Turns this soldier into {@code summoner}'s ally for {@code lifetimeTicks}.
     *
     * <p>Being allied is a mode, not a subclass, exactly as the spec asks ("allied soldiers
     * reuse the soldier entity with an owner/summoned flag"). It gates four things:
     * {@link ColonyAnger#isColonyAnt} stops answering for it, the three colony target goals
     * above stand down, {@link AlliedSoldierTargetGoal} takes over, and
     * {@link #customServerAiStep()} disperses it when the timer runs out.
     */
    public void summonFor(Player summoner, int lifetimeTicks) {
        this.summoner = summoner.getUUID();
        this.summonExpiry = this.level().getGameTime() + lifetimeTicks;
    }

    /** Whether this soldier is a Pheromone Horn summon rather than one of the colony's. */
    public boolean isAllied() {
        return this.summoner != null;
    }

    @Nullable
    public UUID getSummonerUUID() {
        return this.summoner;
    }

    /**
     * UUID comparison rather than resolving the player: {@code level.getPlayerByUUID} is
     * always null for a {@code GameTestHelper} mock player (it is never added to the level).
     */
    public boolean isSummonedBy(Player player) {
        return player.getUUID().equals(this.summoner);
    }

    /** Game time at which this summon disperses. */
    public long getSummonExpiry() {
        return this.summonExpiry;
    }

    /** Disperses a summon: amber puff, then gone. Public so a test can drive it directly. */
    public void disperse() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FALLING_HONEY, this.getX(), this.getY() + 0.3, this.getZ(),
                    18, 0.3, 0.2, 0.3, 0.01);
        }
        this.discard();
    }

    /** An ally never turns on the player who summoned it, whichever goal proposed it. */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isAllied() && target instanceof Player player && this.isSummonedBy(player)) {
            return false;
        }
        return super.canAttack(target);
    }

    /**
     * Whether provoked colony anger is currently pointed at {@code player} specifically.
     *
     * <p>Deliberately not {@code NeutralMob#isAngryAt}: that one also answers true for
     * <em>every</em> player once the {@code universalAnger} game rule is on and no offender
     * is recorded, which would make one player's trespass turn soldiers on a bystander's
     * tamed ants. The colony's anger always has a named offender (see the class javadoc),
     * so the honest question is whether this player is that offender.
     */
    public boolean isAngryAtPlayer(Player player) {
        return this.isAngry() && player.getUUID().equals(this.angerTarget);
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

    // ---------------------------------------------------------- persistence --

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
