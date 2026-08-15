package com.nogal.formicary.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nogal.formicary.colony.ColonyAnger;
import com.nogal.formicary.effect.ModMobEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The queen (spec section 3): the colony's boss, seated in her throne chamber at the
 * bottom of the Royal Depths.
 *
 * <p>She is a plain {@link PathfinderMob} rather than a {@code Monster}, matching the wild
 * castes. {@code Monster} would have brought {@code shouldDespawnInPeaceful() == true} with
 * it, and a boss that generates exactly once per chamber must not evaporate because someone
 * switched difficulty; it also drags in light-level {@code noActionTime} accounting that
 * means nothing in a dimension with no sky.
 *
 * <p>Three things make her a boss rather than a big soldier:
 * <ul>
 *   <li>a {@link ServerBossEvent} health bar, wired through
 *       {@link #startSeenByPlayer}/{@link #stopSeenByPlayer} the way {@code WitherBoss}
 *       does it;</li>
 *   <li>{@link #PHASE_THRESHOLDS}: crossing 75%, 50% and 25% health fires a pheromone
 *       burst once each -- soldier reinforcements plus Slowness on everyone close;</li>
 *   <li>{@link #die}: killing her grants every player nearby the Pheromonal Disguise and
 *       wipes the colony's anger at them, which is the fight's real reward. The Queen's
 *       Crest is a trophy; the safe walk out is the mechanic.</li>
 * </ul>
 *
 * <p>She never leaves her chamber: {@code restrictTo} plus
 * {@link MoveTowardsRestrictionGoal}, with a hard teleport home past
 * {@link #LEASH_SNAP_DISTANCE} as the failsafe. Her home is saved and reapplied on load --
 * vanilla's {@code Mob} does not persist its restriction.
 */
public class QueenAntEntity extends PathfinderMob {
    private static final String TAG_HOME = "ThroneHome";
    private static final String TAG_FIRED_PHASES = "FiredPhases";

    // ------------------------------------------------------------- tunables --

    /** Boss health. Tunable. */
    public static final double MAX_HEALTH = 200.0;

    /** Melee bite. Tunable. */
    public static final double ATTACK_DAMAGE = 10.0;

    /** Deliberately slow: her chamber is the arena, and she does not chase far. */
    public static final double MOVEMENT_SPEED = 0.22;

    /** How far from her throne she will wander before heading back. */
    public static final int HOME_RADIUS = 16;

    /** Past this she is teleported home outright -- the leash's failsafe. */
    public static final double LEASH_SNAP_DISTANCE = 24.0;

    /**
     * Health fractions that each fire one pheromone burst, highest first. Once fired, a
     * threshold never fires again, even if she is healed back above it.
     */
    public static final float[] PHASE_THRESHOLDS = {0.75F, 0.50F, 0.25F};

    /** Soldiers summoned per burst. Tunable. */
    public static final int BURST_SOLDIERS = 3;

    /** How far a burst reaches, in blocks. Tunable. */
    public static final double BURST_RADIUS = 12.0;

    /** Slowness duration in ticks (5 seconds), at amplifier 1 = Slowness II. Tunable. */
    public static final int BURST_SLOWNESS_TICKS = 100;
    public static final int BURST_SLOWNESS_AMPLIFIER = 1;

    /** Reinforcements appear this far out, at the chamber's edge rather than on top of you. */
    private static final double BURST_SPAWN_RADIUS = 9.0;

    /** How far her death grace reaches, in blocks. Tunable. */
    public static final double GRACE_RADIUS = 24.0;

    /** Pheromonal Disguise granted on her death: 3600 ticks = 3 minutes. Tunable. */
    public static final int GRACE_DISGUISE_TICKS = 3600;

    /**
     * XP reward on death (play-test round 1, spec item 2: "queen ~50-60, boss-tier,
     * wither-class"). Already matched {@code WitherBoss.xpReward} exactly before this round
     * (verified in {@code reference/}) -- unchanged, just promoted to a named constant like
     * every other tunable above. Tunable.
     */
    public static final int XP_REWARD = 50;

    // ----------------------------------------------------------------- state --

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);

    /** Bitmask over {@link #PHASE_THRESHOLDS}. Persisted, so a relog cannot re-trigger. */
    private int firedPhases;

    @Nullable
    private BlockPos throneHome;

    public QueenAntEntity(EntityType<? extends QueenAntEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = XP_REWARD;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                // She is the size of the room's centrepiece; being punted around it by
                // knockback would read as a large soldier rather than as a boss.
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                // Wide enough to notice a trespasser anywhere in a 28-block chamber.
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6, 200));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F, 0.4F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new QueenHostilityGoal(this));
    }

    // ------------------------------------------------------------------ home --

    /**
     * Plants her throne here. Called by the generator right after it places her, and
     * re-applied from NBT on load.
     */
    public void setThroneHome(BlockPos home) {
        this.throneHome = home;
        this.restrictTo(home, HOME_RADIUS);
    }

    @Nullable
    public BlockPos getThroneHome() {
        return this.throneHome;
    }

    // ----------------------------------------------------------------- phases --

    /** How many of {@link #PHASE_THRESHOLDS} have fired. Test seam. */
    public int getFiredPhaseCount() {
        return Integer.bitCount(this.firedPhases);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        // The leash's failsafe. MoveTowardsRestrictionGoal is the polite version and does
        // the work in practice; this catches the cases pathfinding cannot -- knocked
        // through a wall, or the chamber's mouth blocked.
        if (this.throneHome != null && this.distanceToSqr(Vec3.atBottomCenterOf(this.throneHome))
                > LEASH_SNAP_DISTANCE * LEASH_SNAP_DISTANCE) {
            this.teleportTo(this.throneHome.getX() + 0.5, this.throneHome.getY(), this.throneHome.getZ() + 0.5);
            this.getNavigation().stop();
        }

        this.checkPhaseThresholds(level);
    }

    private void checkPhaseThresholds(ServerLevel level) {
        float fraction = this.getHealth() / this.getMaxHealth();
        for (int i = 0; i < PHASE_THRESHOLDS.length; i++) {
            int bit = 1 << i;
            if ((this.firedPhases & bit) == 0 && fraction <= PHASE_THRESHOLDS[i]) {
                this.firedPhases |= bit;
                this.fireBurst(level);
            }
        }
    }

    /**
     * One pheromone burst: amber particles, Slowness on everyone close, and a wave of
     * colony soldiers already angry at whoever she is fighting.
     */
    public void fireBurst(ServerLevel level) {
        Vec3 origin = this.position();
        List<Player> caught = playersInRange(level, origin, BURST_RADIUS);
        addTargetIfPlayerInRange(caught, this.getTarget(), origin, BURST_RADIUS);
        pheromoneBurst(level, origin, caught);
        summonWave(level);
        this.playSound(SoundEvents.BEE_LOOP_AGGRESSIVE, 2.0F, 0.5F);
    }

    /**
     * The burst's effect on players, split out so a GameTest can drive it.
     *
     * <p>The split exists because {@code GameTestHelper}'s mock player is never added to the
     * level, so {@link #playersInRange}'s entity lookup can never see one -- the same
     * limitation {@code TamedWorkerAntEntity.bindNearestFollower} works around. Taking the
     * list as an argument keeps the rule ("every player within {@link #BURST_RADIUS}") in
     * one place while letting a test hand it a player it can actually assert on.
     */
    public static void pheromoneBurst(ServerLevel level, Vec3 origin, List<Player> caught) {
        level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 1.0, origin.z, 8, 2.5, 1.0, 2.5, 0.0);
        level.sendParticles(ParticleTypes.FALLING_HONEY, origin.x, origin.y + 1.5, origin.z, 140, 4.0, 1.5, 4.0, 0.02);
        for (Player player : caught) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    BURST_SLOWNESS_TICKS, BURST_SLOWNESS_AMPLIFIER));
        }
    }

    /**
     * Reinforcements: wild soldiers at the chamber's edge, already angry at her current
     * target. Wild ones on purpose -- they are the colony answering its queen, so they keep
     * every colony rule (a disguised player is still invisible to them, and killing one
     * still angers the nest).
     */
    private void summonWave(ServerLevel level) {
        Player offender = this.getTarget() instanceof Player player ? player : null;
        for (int i = 0; i < BURST_SOLDIERS; i++) {
            double angle = (Math.PI * 2.0 / BURST_SOLDIERS) * i + this.random.nextDouble() * 0.5;
            double x = this.getX() + Math.cos(angle) * BURST_SPAWN_RADIUS;
            double z = this.getZ() + Math.sin(angle) * BURST_SPAWN_RADIUS;
            SoldierAntEntity soldier = ModEntities.SOLDIER_ANT.get().create(level);
            if (soldier == null) {
                continue;
            }
            soldier.moveTo(x, this.getY(), z, this.random.nextFloat() * 360.0F, 0.0F);
            soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(soldier.blockPosition()),
                    MobSpawnType.REINFORCEMENT, null);
            if (offender != null) {
                soldier.angerAt(offender);
                soldier.setTarget(offender);
            }
            level.addFreshEntity(soldier);
            level.sendParticles(ParticleTypes.FALLING_HONEY, x, this.getY() + 0.5, z, 20, 0.4, 0.4, 0.4, 0.01);
        }
    }

    // ------------------------------------------------------------ death grace --

    /**
     * The spec's reward for the fight: "on her death, every player within ~24 blocks gains
     * Pheromonal Disguise for ~3 min and existing colony anger toward them is cleared -- a
     * safe walk out."
     *
     * <p>Her killer is credited <em>regardless of distance</em>. The radius is a rule about
     * bystanders -- who else the colony forgives on her account -- and reading it as a rule
     * about the killer too silently voided the whole reward for anyone who finished her with
     * a bow, or was knocked out of the chamber by the last phase burst. Play-test round 1
     * ("killing the queen didn't give me the effect") is the report this answers.
     */
    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bossEvent.setProgress(0.0F);
        Vec3 origin = this.position();
        List<Player> caught = playersInRange(level, origin, GRACE_RADIUS);
        // The killer counts even if the entity lookup missed them -- see pheromoneBurst --
        // and counts from any distance, unlike the bystanders around her.
        addPlayer(caught, killerOf(level, damageSource));
        grantDeathGrace(level, origin, caught);
    }

    /**
     * The player to credit with the killing blow: whoever swung or shot, or -- when one of
     * the player's own ants landed it -- that ant's owner.
     *
     * <p>The fallback is deliberately local rather than folded into
     * {@link ColonyAnger#offenderOf}: that helper's refusal to see through a mob is what
     * delivers "tamed ants' fights never anger the colony or strip your disguise" (spec
     * section 4), so teaching it about owners would break M6 to fix M7. Resolving the owner
     * goes through the player list, so this branch is unreachable from a GameTest (a mock
     * player is never added to the level) -- it is verified in {@code runClient}.
     */
    @Nullable
    private static Player killerOf(ServerLevel level, @Nullable DamageSource damageSource) {
        Player offender = ColonyAnger.offenderOf(damageSource);
        if (offender != null) {
            return offender;
        }
        if (damageSource != null && damageSource.getEntity() instanceof TamedAnt ant
                && ant.getOwnerUUID() != null) {
            return level.getEntity(ant.getOwnerUUID()) instanceof Player owner ? owner : null;
        }
        return null;
    }

    /**
     * Grants the grace to {@code caught} and calls the colony off them.
     *
     * <p>Public and list-taking for the same reason {@link #pheromoneBurst} is: a GameTest's
     * player is invisible to {@link #playersInRange}.
     *
     * <p>The colony is called off around {@code origin} <em>and</em> around each player the
     * grace reached, because {@link #die} no longer requires the killer to be inside
     * {@link #GRACE_RADIUS} of her -- the soldiers still chasing a killer who backed out of
     * the chamber are near that killer, not near her throne.
     */
    public static void grantDeathGrace(ServerLevel level, Vec3 origin, List<Player> caught) {
        if (caught.isEmpty()) {
            return;
        }
        for (Player player : caught) {
            player.addEffect(new MobEffectInstance(ModMobEffects.PHEROMONAL_DISGUISE, GRACE_DISGUISE_TICKS));
        }
        level.sendParticles(ParticleTypes.FALLING_HONEY, origin.x, origin.y + 1.0, origin.z, 200, 5.0, 2.0, 5.0, 0.01);

        callColonyOff(level, origin, caught);
        for (Player player : caught) {
            callColonyOff(level, player.position(), caught);
        }
    }

    /**
     * Calls every wild colony ant within {@link #GRACE_RADIUS} of {@code centre} off the
     * players in {@code caught}, immediately -- anger, current target and personal grudge
     * alike (see {@link SoldierAntEntity#forgive}).
     *
     * <p>Horn-summoned allies are skipped: {@code ColonyAnger.isColonyAnt} already excludes
     * them everywhere else, and an ally is the player's fighter rather than the colony's, so
     * the colony standing down has nothing to say about it.
     */
    private static void callColonyOff(ServerLevel level, Vec3 centre, List<Player> caught) {
        AABB area = AABB.ofSize(centre, GRACE_RADIUS * 2.0, GRACE_RADIUS * 2.0, GRACE_RADIUS * 2.0);
        for (SoldierAntEntity soldier : level.getEntitiesOfClass(SoldierAntEntity.class, area)) {
            if (soldier.isAllied()) {
                continue;
            }
            for (Player player : caught) {
                soldier.forgive(player);
            }
        }
        for (WorkerAntEntity worker : level.getEntitiesOfClass(WorkerAntEntity.class, area)) {
            for (Player player : caught) {
                if (worker.isFleeingFrom(player)) {
                    worker.stopFleeing();
                    break;
                }
            }
        }
    }

    /** Living, non-spectator players within {@code radius} of {@code origin}. */
    public static List<Player> playersInRange(ServerLevel level, Vec3 origin, double radius) {
        AABB area = AABB.ofSize(origin, radius * 2.0, radius * 2.0, radius * 2.0);
        return new ArrayList<>(level.getEntitiesOfClass(Player.class, area,
                player -> player.isAlive() && !player.isSpectator()
                        && player.distanceToSqr(origin) <= radius * radius));
    }

    private static void addTargetIfPlayerInRange(List<Player> caught, @Nullable Object candidate,
            Vec3 origin, double radius) {
        if (candidate instanceof Player player && player.distanceToSqr(origin) <= radius * radius) {
            addPlayer(caught, player);
        }
    }

    /** Adds {@code candidate} to {@code caught} if it is a living, non-spectator newcomer. */
    private static void addPlayer(List<Player> caught, @Nullable Player candidate) {
        if (candidate != null && candidate.isAlive() && !candidate.isSpectator()
                && !caught.contains(candidate)) {
            caught.add(candidate);
        }
    }

    // ------------------------------------------------------------- boss bar --

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    // ---------------------------------------------------------- persistence --

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt(TAG_FIRED_PHASES, this.firedPhases);
        if (this.throneHome != null) {
            compound.putLong(TAG_HOME, this.throneHome.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.firedPhases = compound.getInt(TAG_FIRED_PHASES);
        // Mob does not persist its restriction, so the leash has to be re-planted here or
        // a relog would set her free.
        if (compound.contains(TAG_HOME)) {
            this.setThroneHome(BlockPos.of(compound.getLong(TAG_HOME)));
        }
    }

    /** She belongs to her chamber and to nothing else; she never despawns. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Her leash is her chamber. A lead would fight the home restriction for control. */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /**
     * She is a wild colony ant, so the disguise binds her at the continuation layer too --
     * see {@link ColonyAnger#colonyMayAttack}. {@link QueenHostilityGoal} already documents
     * her as blind to a disguised player ("which is what lets a disguised player walk her
     * chamber to scout"); without this she would acquire nobody but never let go of anyone,
     * because {@code HurtByTargetGoal} re-checks only {@code canAttack}.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        return ColonyAnger.colonyMayAttack(target) && super.canAttack(target);
    }

    // -------------------------------------------------------------- sounds --

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEE_LOOP_AGGRESSIVE;
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
        return 1.2F;
    }

    /** Public, not protected: {@code LivingEntity#getVoicePitch} is public in 1.21. */
    @Override
    public float getVoicePitch() {
        return 0.5F;
    }
}
