package com.nogal.formicary.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.nogal.formicary.colony.ColonyAnger;
import com.nogal.formicary.effect.ModMobEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.ai.goal.Goal;
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
 *   <li>a {@link ServerBossEvent} health bar, brought up by proximity
 *       ({@link #refreshBossBarAudience}, {@link #BOSS_BAR_RADIUS}) and torn down both by
 *       distance and by {@link #stopSeenByPlayer} -- {@code WitherBoss}'s plumbing minus
 *       its add-on-track half, which for a 16-chunk tracking range meant a bar 256 blocks
 *       from a chamber nobody had found;</li>
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
    private static final String TAG_UNLOCKED_MOVES = "UnlockedMoves";

    /** {@link #unlockedMoves} bit for the burrow slam. */
    public static final int UNLOCK_BURROW_SLAM = 1;

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

    /**
     * How close a player must be for her health bar to appear at all.
     *
     * <p>Her {@code clientTrackingRange} is 16 chunks, so before this the bar came up
     * roughly 256 blocks away -- a boss bar hanging over a player who is four tiers up,
     * has never seen the chamber, and cannot tell what it belongs to. 20 is a little
     * inside her 32-block {@code FOLLOW_RANGE}: by the time the bar exists she can see
     * you, which is the thing the bar is actually reporting.
     */
    public static final double BOSS_BAR_RADIUS = 20.0;

    /**
     * How far a player who already has the bar must retreat before it goes away.
     *
     * <p>The hysteresis half. Without it, standing on the boundary flickers the bar on and
     * off every refresh, which is worse than either state -- and 20/28 is wide enough that
     * a fight fought at the edge of her leash ({@link #LEASH_SNAP_DISTANCE}, 24) never
     * loses the bar mid-swing.
     */
    public static final double BOSS_BAR_RADIUS_EXIT = 28.0;

    /**
     * Ticks between audience refreshes. Ten: a player crossing the boundary at sprint speed
     * (~5.6 blocks/s) moves under 3 blocks in that window, and the 8-block hysteresis band
     * absorbs it, so the bar never appears late enough to notice.
     */
    private static final int BOSS_BAR_REFRESH_TICKS = 10;

    /** Pheromonal Disguise granted on her death: 3600 ticks = 3 minutes. Tunable. */
    public static final int GRACE_DISGUISE_TICKS = 3600;

    // ------------------------------------------------------------ acid spit --

    /**
     * Closest range she will spit from. Inside this she is already in melee, and a boss
     * that shoots you in the face while biting you is not a decision the player can read.
     * Tunable.
     */
    public static final double SPIT_MIN_RANGE = 6.0;

    /**
     * Furthest range she will spit from -- deliberately half her {@code FOLLOW_RANGE}, so
     * "she can see you" and "she can reach you" stay two different facts. Tunable.
     */
    public static final double SPIT_MAX_RANGE = 16.0;

    /** Ticks between spits: 4 seconds. Tunable. */
    public static final int SPIT_COOLDOWN_TICKS = 80;

    // ----------------------------------------------------------- burrow slam --

    /**
     * Health fraction that unlocks the burrow slam, for good. Latched exactly the way
     * {@link #PHASE_THRESHOLDS} are -- see {@link #unlockedMoves} -- so healing back over
     * it does not take the move away again. Tunable.
     */
    public static final float BURROW_UNLOCK_FRACTION = 0.60F;

    /**
     * Ticks she spends underground before erupting. Long enough that a player who is
     * paying attention can move; short enough that it is a threat rather than an
     * intermission. Tunable.
     */
    public static final int BURROW_TICKS = 30;

    /** Ticks between burrows: 12 seconds. Tunable. */
    public static final int BURROW_COOLDOWN_TICKS = 240;

    /** Closest range she will burrow from -- inside this she simply bites. Tunable. */
    public static final double BURROW_MIN_RANGE = 5.0;

    /** Furthest range she will burrow from. Tunable. */
    public static final double BURROW_MAX_RANGE = 14.0;

    /**
     * If the target has got further than this from the burrow point by the time she
     * erupts, the move is aborted. Comfortably past {@link #BURROW_MAX_RANGE} on purpose:
     * the abort is for a player who ran, not for one who backed up a step. Tunable.
     */
    public static final double BURROW_ABORT_DISTANCE = 20.0;

    /** Radius of the eruption's area damage, in blocks. Tunable. */
    public static final double SLAM_RADIUS = 4.0;

    /** Eruption damage. Tunable. */
    public static final float SLAM_DAMAGE = 6.0F;

    /** Upward impulse the eruption adds, in blocks/tick. Tunable. */
    public static final double SLAM_KNOCK_UP = 0.8;

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

    /**
     * Ticks left before she may spit again.
     *
     * <p>On the queen rather than on {@link AcidSpitGoal}, and that is the whole point of
     * the field: a {@code Goal} is constructed once but started and stopped constantly, and
     * a cooldown living in one would reset every time the goal lost its flags to
     * {@code MeleeAttackGoal} -- which is exactly when a player is closing distance, i.e.
     * exactly when a free extra spit is worth the most. Deliberately NOT persisted: a
     * relog's worth of grace on a 4-second timer is not worth a save-data field, and 0 (the
     * value a fresh queen loads with) is the same "ready" state she starts a fight in.
     */
    private int spitCooldown;

    /**
     * Bitmask of the fight moves her health has unlocked. Persisted and one-way, exactly
     * like {@link #firedPhases} -- the same discipline for the same reason: a boss whose
     * moveset flickers as she is healed and re-damaged is unreadable, and a relog must not
     * be able to take a move back.
     *
     * <p>A field of its own rather than more bits in {@code firedPhases}, because
     * {@link #getFiredPhaseCount()} counts that mask's bits and is the seam the burst tests
     * assert on; sharing the int would silently make "phases fired" mean something else.
     */
    private int unlockedMoves;

    /** Ticks left underground. Zero means she is above ground. Not persisted -- see {@link #beginBurrowSlam}. */
    private int burrowTicksLeft;

    /** Where she went down, so an aborted eruption can put her back. */
    @Nullable
    private Vec3 burrowOrigin;

    /** Ticks left before she may burrow again. Same rationale as {@link #spitCooldown}. */
    private int burrowCooldown;

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
        // The burrow outranks everything else she can do: it is a 30-tick commitment with
        // its own damage-immunity window, and a boss halfway into the ground must not have
        // her navigation taken back by a goal that wants her to walk somewhere.
        this.goalSelector.addGoal(1, new BurrowSlamGoal(this));
        // Above the melee goal on purpose: the two can never both apply (their ranges do
        // not overlap -- SPIT_MIN_RANGE is 6, a bite is ~3), so the ordering only decides
        // which one wins in the tick a target crosses the boundary, and a spit that is
        // already off cooldown should not be swallowed by a lunge that cannot land yet.
        this.goalSelector.addGoal(2, new AcidSpitGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
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
        if (this.tickCount % BOSS_BAR_REFRESH_TICKS == 0) {
            this.refreshBossBarAudience(level.players());
        }
        if (this.spitCooldown > 0) {
            this.spitCooldown--;
        }
        if (this.burrowCooldown > 0) {
            this.burrowCooldown--;
        }
        this.tickBurrow(level);

        // The leash's failsafe. MoveTowardsRestrictionGoal is the polite version and does
        // the work in practice; this catches the cases pathfinding cannot -- knocked
        // through a wall, or the chamber's mouth blocked.
        if (this.throneHome != null && this.distanceToSqr(Vec3.atBottomCenterOf(this.throneHome))
                > LEASH_SNAP_DISTANCE * LEASH_SNAP_DISTANCE) {
            this.teleportTo(this.throneHome.getX() + 0.5, this.throneHome.getY(), this.throneHome.getZ() + 0.5);
            this.getNavigation().stop();
        }

        this.checkPhaseThresholds(level);
        this.checkMoveUnlocks();
    }

    /**
     * The one-way latches that give her new moves as the fight goes on.
     *
     * <p>Deliberately the same shape as {@link #checkPhaseThresholds}: a health fraction, a
     * bit that is only ever set, and no polling anywhere else. Nothing in the fight is
     * allowed to ask "is she under 60%?" directly -- that question has a different answer
     * every time she is healed, and a boss whose moveset flickers cannot be learned.
     */
    private void checkMoveUnlocks() {
        float fraction = this.getHealth() / this.getMaxHealth();
        if ((this.unlockedMoves & UNLOCK_BURROW_SLAM) == 0 && fraction < BURROW_UNLOCK_FRACTION) {
            this.unlockedMoves |= UNLOCK_BURROW_SLAM;
        }
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

    // ------------------------------------------------------------- acid spit --

    /** Whether her spit is off cooldown. Read by {@link AcidSpitGoal}; a test seam. */
    public boolean isAcidSpitReady() {
        return this.spitCooldown <= 0;
    }

    /** Ticks until the next spit. Zero means ready. A readout for tests. */
    public int getAcidSpitCooldown() {
        return this.spitCooldown;
    }

    /**
     * Launches one {@link AcidSpitProjectile} at {@code target} and starts the cooldown.
     *
     * <p>The lead is vanilla's llama arithmetic (verified in {@code reference}'s
     * {@code Llama#performRangedAttack}): aim at a third of the target's height and add
     * {@code horizontal * 0.2} to the vertical component, which is the standing
     * approximation for the drop a 0.05-gravity projectile takes over that distance. Her
     * band tops out at {@link #SPIT_MAX_RANGE}, where that correction is still small enough
     * to be an arc rather than a mortar.
     *
     * <p>Public because {@link AcidSpitGoal} is the only caller and it is a separate class,
     * and because a test that wants to assert on the projectile rather than on the AI can
     * drive it directly.
     */
    public void spitAcidAt(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        AcidSpitProjectile spit = new AcidSpitProjectile(level, this);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333) - spit.getY();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        spit.shoot(dx, dy + horizontal * 0.2, dz,
                AcidSpitProjectile.LAUNCH_VELOCITY, AcidSpitProjectile.LAUNCH_INACCURACY);
        level.addFreshEntity(spit);
        this.playSound(SoundEvents.LLAMA_SPIT, 1.6F, 0.6F);
        this.spitCooldown = SPIT_COOLDOWN_TICKS;
    }

    /**
     * Her ranged attack (Ep2, task F2): live from the first tick of the fight, unlike the
     * burrow and the frenzy, which are unlocked by health latches.
     *
     * <p>A one-shot goal -- {@link #canContinueToUse()} is always false, so it fires once
     * and hands its flags straight back. Everything that persists between shots lives on
     * the queen ({@link QueenAntEntity#spitCooldown}), which is what lets the goal be
     * restarted freely by the goal selector without ever handing out a free shot.
     *
     * <p>It claims only {@code LOOK}. Taking {@code MOVE} would fight
     * {@code MeleeAttackGoal} for the navigation of a boss who is supposed to keep walking
     * at you while she spits.
     */
    public static class AcidSpitGoal extends Goal {
        private final QueenAntEntity queen;

        @Nullable
        private LivingEntity target;

        public AcidSpitGoal(QueenAntEntity queen) {
            this.queen = queen;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.target = null;
            if (!this.queen.isAcidSpitReady()) {
                return false;
            }
            LivingEntity candidate = this.queen.getTarget();
            if (candidate == null || !candidate.isAlive() || !this.queen.canAttack(candidate)) {
                return false;
            }
            double distanceSq = this.queen.distanceToSqr(candidate);
            if (distanceSq < SPIT_MIN_RANGE * SPIT_MIN_RANGE
                    || distanceSq > SPIT_MAX_RANGE * SPIT_MAX_RANGE) {
                return false;
            }
            // Last, because it is the expensive one: a raycast per candidate, and every
            // cheaper reason to say no has already run.
            if (!this.queen.getSensing().hasLineOfSight(candidate)) {
                return false;
            }
            this.target = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (this.target == null) {
                return;
            }
            this.queen.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            this.queen.spitAcidAt(this.target);
            this.target = null;
        }
    }

    // ----------------------------------------------------------- burrow slam --

    /** Whether her health has ever dropped under {@link #BURROW_UNLOCK_FRACTION}. */
    public boolean hasUnlockedBurrowSlam() {
        return (this.unlockedMoves & UNLOCK_BURROW_SLAM) != 0;
    }

    /** Whether she is underground right now. Read by {@link #hurt}; a test seam. */
    public boolean isBurrowed() {
        return this.burrowTicksLeft > 0;
    }

    /** Ticks until she may burrow again. Zero means ready. A readout for tests. */
    public int getBurrowCooldown() {
        return this.burrowCooldown;
    }

    /**
     * Takes her underground.
     *
     * <p>Nothing here is persisted, and that is the intended failure mode: a queen saved
     * mid-burrow loads above ground, cooled down, and simply starts the move again. The
     * alternative -- persisting a half-finished animation -- would have to answer what
     * happens when the target logged out during it, and "she surfaces" is the answer either
     * way.
     */
    public void beginBurrowSlam() {
        this.burrowTicksLeft = BURROW_TICKS;
        this.burrowOrigin = this.position();
        this.burrowCooldown = BURROW_COOLDOWN_TICKS;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setInvisible(true);
        this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 2.0F, 0.5F);
    }

    /**
     * One tick of the burrow: hold her still, kick up soil, and erupt when the timer runs
     * out.
     *
     * <p>Driven from {@link #customServerAiStep} rather than from {@link BurrowSlamGoal},
     * so that a goal losing its flags mid-move cannot strand her underground and immune
     * forever. Once she is down, she comes back up.
     */
    private void tickBurrow(ServerLevel level) {
        if (this.burrowTicksLeft <= 0) {
            return;
        }
        // She is under the floor: no drifting, no pathing, no being shoved.
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        BlockPos below = this.blockPosition().below();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
                this.getX(), this.getY() + 0.1, this.getZ(), 14, 0.9, 0.1, 0.9, 0.08);
        if (--this.burrowTicksLeft <= 0) {
            this.erupt(level);
        }
    }

    /**
     * The eruption, or the shrug.
     *
     * <p>The abort exists because the move's whole point is that it cannot be kited, and a
     * move that cannot be kited has to be able to miss for some other reason or it is just
     * an unavoidable 6 damage every 12 seconds. Three ways it misses, all checked at this
     * instant rather than when she went down: the target died, the target became
     * untargetable (a Pheromonal Disguise -- {@link #canAttack} is the seam
     * {@code ColonyAnger.colonyMayAttack} binds, which is what makes drinking the potion a
     * real answer to this move), or the target simply got a long way away.
     */
    private void erupt(ServerLevel level) {
        Vec3 origin = this.burrowOrigin != null ? this.burrowOrigin : this.position();
        this.burrowTicksLeft = 0;
        this.burrowOrigin = null;
        this.setInvisible(false);

        LivingEntity target = this.getTarget();
        boolean landed = target != null && target.isAlive() && this.canAttack(target)
                && target.distanceToSqr(origin) <= BURROW_ABORT_DISTANCE * BURROW_ABORT_DISTANCE;

        Vec3 surface = landed ? target.position() : origin;
        this.teleportTo(surface.x, surface.y, surface.z);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);

        if (landed) {
            this.burrowSlam(level, surface);
        } else {
            // Same soil, a quarter of the noise: she comes up where she went down and the
            // player gets to see that the dodge worked.
            level.sendParticles(ParticleTypes.EXPLOSION, surface.x, surface.y + 0.5, surface.z,
                    1, 0.0, 0.0, 0.0, 0.0);
            this.playSound(SoundEvents.ROOTED_DIRT_PLACE, 1.6F, 0.5F);
        }
    }

    /**
     * The eruption's area damage: everything living within {@link #SLAM_RADIUS} that is not
     * an ant takes {@link #SLAM_DAMAGE} and is thrown {@link #SLAM_KNOCK_UP} upward.
     *
     * <p>Ants are exempt -- wild, tamed and the queen herself. She has just summoned three
     * soldiers onto a ring around her; an AoE that killed them would make her own phase
     * bursts a liability, and hurting one would run
     * {@code ColonyAngerEvents.onColonyAntHurt} and anger the nest at nobody. Same rule the
     * acid spit's {@code canHitEntity} follows, for the same reason.
     *
     * <p>The knock-up is applied <em>after</em> {@code hurt}, because {@code hurt} sets
     * delta movement itself (she carries 1.5 {@code ATTACK_KNOCKBACK}) and doing it the
     * other way round would throw the impulse away. {@code hurtMarked} is what makes a
     * server-side velocity change reach the client at all.
     */
    private void burrowSlam(ServerLevel level, Vec3 centre) {
        AABB area = AABB.ofSize(centre, SLAM_RADIUS * 2.0, SLAM_RADIUS * 2.0, SLAM_RADIUS * 2.0);
        double radiusSq = SLAM_RADIUS * SLAM_RADIUS;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != this && candidate.isAlive()
                        && !ColonyAnger.isColonyAnt(candidate) && !ColonyAnger.isTamedAnt(candidate)
                        && candidate.distanceToSqr(centre) <= radiusSq)) {
            if (victim.hurt(level.damageSources().mobAttack(this), SLAM_DAMAGE)) {
                victim.push(0.0, SLAM_KNOCK_UP, 0.0);
                victim.hurtMarked = true;
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.5, centre.z,
                6, 1.5, 0.4, 1.5, 0.0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        level.getBlockState(BlockPos.containing(centre).below())),
                centre.x, centre.y + 0.2, centre.z, 60, 2.0, 0.3, 2.0, 0.3);
        this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.6F);
    }

    /**
     * Her second attack (Ep2, task F3): unlocked by
     * {@link QueenAntEntity#BURROW_UNLOCK_FRACTION}, so the first 40% of the fight is the
     * fight she has always had and the rest is not.
     *
     * <p>The goal is only the trigger and the movement lock. The 30 ticks underground and
     * the eruption are driven by {@link QueenAntEntity#tickBurrow}, so that this goal
     * losing its flags cannot leave a boss buried and damage-immune -- see that method.
     * {@link #canContinueToUse()} keeps the lock for exactly as long as she is down.
     */
    public static class BurrowSlamGoal extends Goal {
        private final QueenAntEntity queen;

        public BurrowSlamGoal(QueenAntEntity queen) {
            this.queen = queen;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.queen.hasUnlockedBurrowSlam() || this.queen.isBurrowed()
                    || this.queen.getBurrowCooldown() > 0) {
                return false;
            }
            LivingEntity target = this.queen.getTarget();
            if (target == null || !target.isAlive() || !this.queen.canAttack(target)) {
                return false;
            }
            double distanceSq = this.queen.distanceToSqr(target);
            return distanceSq >= BURROW_MIN_RANGE * BURROW_MIN_RANGE
                    && distanceSq <= BURROW_MAX_RANGE * BURROW_MAX_RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return this.queen.isBurrowed();
        }

        @Override
        public void start() {
            this.queen.beginBurrowSlam();
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
        // Emptied rather than left to drain: the bar itself is torn down when she is
        // actually removed a death animation later (removal -> broadcastRemoved ->
        // stopSeenByPlayer), so this is only what it reads in the meantime.
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

    /**
     * Brings the bar up for players inside {@link #BOSS_BAR_RADIUS} and drops it for those
     * past {@link #BOSS_BAR_RADIUS_EXIT}, leaving everyone in between exactly as they were.
     *
     * <p>This replaces the {@code WitherBoss} plumbing on the <em>adding</em> side.
     * {@code startSeenByPlayer} fires at the entity's client tracking range -- 16 chunks for
     * her, so the bar used to appear about 256 blocks out, four tiers above a chamber the
     * player has not found, attached to nothing they can see. Tracking range is a
     * networking decision and had no business being the UI one.
     *
     * <p>Public and list-taking for the same reason {@link #pheromoneBurst} and
     * {@link #grantDeathGrace} are: a {@code GameTestHelper} mock player is never added to
     * the level, so a test cannot reach this rule through {@code level.players()}.
     *
     * <p>Removal is deliberately <b>not</b> only here. This loop can only see players in the
     * level, so everything that takes a player out of it -- disconnect, dimension change,
     * the chunk unloading, and her own death -- is covered by {@link #stopSeenByPlayer}
     * instead: entity removal runs {@code ChunkMap.TrackedEntity#broadcastRemoved} ->
     * {@code ServerEntity#removePairing} -> {@code stopSeenByPlayer}, and a player leaving
     * runs the same path through {@code TrackedEntity#removePlayer} (verified in the
     * decompiled {@code ChunkMap} / {@code ServerEntity}).
     */
    public void refreshBossBarAudience(List<? extends ServerPlayer> candidates) {
        for (ServerPlayer player : candidates) {
            double distanceSq = player.distanceToSqr(this.position());
            if (this.bossEvent.getPlayers().contains(player)) {
                if (distanceSq > BOSS_BAR_RADIUS_EXIT * BOSS_BAR_RADIUS_EXIT) {
                    this.bossEvent.removePlayer(player);
                }
            } else if (distanceSq <= BOSS_BAR_RADIUS * BOSS_BAR_RADIUS) {
                this.bossEvent.addPlayer(player);
            }
        }
    }

    /** Who can currently see her bar. Unmodifiable; a test seam and a readout. */
    public Collection<ServerPlayer> getBossBarAudience() {
        return this.bossEvent.getPlayers();
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
        compound.putInt(TAG_UNLOCKED_MOVES, this.unlockedMoves);
        if (this.throneHome != null) {
            compound.putLong(TAG_HOME, this.throneHome.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.firedPhases = compound.getInt(TAG_FIRED_PHASES);
        this.unlockedMoves = compound.getInt(TAG_UNLOCKED_MOVES);
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

    /**
     * Nothing touches her while she is underground.
     *
     * <p>Deliberately independent of {@code invulnerableTime}: that field is a 10-tick
     * anti-multihit window whose early return is conditional on
     * {@code amount <= lastHurt} (the banked {@code hurt} rule), so a big enough swing goes
     * straight through it. The burrow is not an i-frame window, it is a statement that she
     * is not there -- 30 ticks in which a player who keeps swinging at the hole is wasting
     * the 30 ticks they had to move.
     *
     * <p>{@code BYPASSES_INVULNERABILITY} is still honoured, which is what keeps
     * {@code /kill} and the void working on her mid-move. A boss that a command cannot
     * remove is a bug report, not a mechanic.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isBurrowed() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
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
