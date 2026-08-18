package com.nogal.formicary.entity;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * The Ender Ant (spec section 5): the deep tiers' hostile caste, and the reason exit
 * pearls are renewable at all -- passing back out through a Daylight Membrane consumes a
 * thrown ender pearl, so a colony with no pearl source is a colony you can be stranded in.
 *
 * <p>It is a plain {@link PathfinderMob} rather than a {@code Monster}, for the reason
 * {@link QueenAntEntity}'s own javadoc records (QueenAntEntity.java, "She is a plain
 * PathfinderMob rather than a Monster"): {@code Monster} brings
 * {@code shouldDespawnInPeaceful() == true}, and the handful of ender ants seeded into each
 * colony at generation must survive someone switching the difficulty to Peaceful. Runtime
 * spawning is category-driven and skips {@code MobCategory.MONSTER} on Peaceful regardless,
 * which is the half of the rule that still holds without inheriting the despawn.
 *
 * <p>Despawn policy is deliberately two-tier, and it is the one place this caste breaks the
 * colony's "residents never despawn" convention on purpose:
 * <ul>
 *   <li>runtime-spawned ants keep {@code Mob}'s stock distance despawn -- this class does
 *       <em>not</em> override {@code removeWhenFarAway}, unlike every other ant here.
 *       Persistence-required hostiles would fill the ~70 MONSTER cap and permanently stop
 *       respawning, the same dead end the banked {@code MobCategory.CREATURE} rule in
 *       {@code docs/gotchas/worldgen.md} describes for the colony's own castes;</li>
 *   <li>the ants seeded per colony at chunk generation get {@code setPersistenceRequired()}
 *       from {@code ColonyChunkGenerator}, so they are still there when a player first
 *       walks into the fringe.</li>
 * </ul>
 *
 * <p>Teleport behaviour is adapted from {@code EnderMan} in {@code reference/} rather than
 * invented -- see {@link #blinkTo(double, double, double)} for the line-by-line
 * correspondence. (It is {@code blinkTo} and not {@code teleportTo} because {@code Entity}
 * already declares a public {@code teleportTo(double, double, double)}, and a private
 * override of it is a "weaker access privileges" compile error, not a shadow.)
 */
public class EnderAntEntity extends PathfinderMob {

    // ------------------------------------------------------------- tunables --

    /** Spec section 5: "~20 HP / 4 dmg (normal)". */
    public static final double MAX_HEALTH = 20.0;
    public static final double ATTACK_DAMAGE = 4.0;
    public static final double MOVEMENT_SPEED = 0.3;

    /**
     * How far it hunts, and -- because {@code TargetGoal#getFollowDistance} reads this same
     * attribute -- how far it will keep chasing before it gives up. The brief's "range ~12"
     * is expressed here rather than as a number inside the goal, because
     * {@code NearestAttackableTargetGoal}'s search box is derived from the attribute and a
     * literal in the goal would simply be ignored.
     */
    public static final double FOLLOW_RANGE = 12.0;

    /**
     * XP on death. A plain {@code Mob#xpReward} field is correct <em>here</em>: this is a
     * {@link PathfinderMob}, so {@code Mob.getBaseExperienceReward()} really does read the
     * field. The banked trap (a tamed caste's {@code Animal} ancestor overriding that method
     * to a flat 1-3 and ignoring the field) applies only to {@code TamableAnimal} subclasses
     * -- see {@link WorkerAntEntity#XP_REWARD}. {@code EnderAntGameTests} asserts the orb
     * total rather than trusting that distinction.
     *
     * <p>8 is above a worker's 3 and a soldier's 7, which is what the spec asks for ("XP
     * above a worker's") -- this is the deep tiers' hardest ordinary fight.
     */
    public static final int XP_REWARD = 8;

    /** Shortest blink, in blocks. Spec: "teleports 8-16 blocks". */
    public static final double TELEPORT_MIN_DISTANCE = 8.0;

    /** Longest blink, in blocks. */
    public static final double TELEPORT_MAX_DISTANCE = 16.0;

    /**
     * Vertical spread of a blink target before the ground-seek runs, in blocks.
     *
     * <p>Deliberately tiny next to {@code EnderMan#teleport}'s own {@code nextInt(64) - 32}.
     * An enderman teleports on the open surface where the ground-seek always terminates at
     * the same landscape; the colony is a 192-block stack of four 48-block tiers, and a
     * +-32 vertical throw would routinely put the target inside the solid fabric between two
     * galleries (a failed attempt) or drop the ant a whole tier out of the fight. The
     * ground-seek below still lets a blink fall down a shaft -- it just is not aimed there.
     */
    private static final int TELEPORT_VERTICAL_JITTER = 2;

    /**
     * Attempts per blink before giving up for this trigger. {@code EnderMan#hurt} does the
     * same thing with a bounded {@code for} loop over {@code teleport()}; underground, where
     * a random bearing frequently points into solid soil, a single attempt would make the
     * teleport look broken far more often than it is.
     */
    private static final int TELEPORT_ATTEMPTS = 16;

    /**
     * How much clear ground a homing blink leaves between itself and its quarry, in blocks.
     * Without it a blink aimed at a target exactly {@link #TELEPORT_MIN_DISTANCE} away would
     * try to land inside the player.
     */
    private static final double TELEPORT_APPROACH_GAP = 1.5;

    public EnderAntEntity(EntityType<? extends EnderAntEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = XP_REWARD;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Above the melee goal: a quarry that has stayed out of reach is exactly the case
        // where walking at it has already failed.
        this.goalSelector.addGoal(1, new EnderAntBlinkGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Personal retaliation, exactly as the soldier has it: something that shot the ant
        // from outside its hunting range still gets chased.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Hostile on sight inside FOLLOW_RANGE. mustSee = true matches "aggressive on sight";
        // the predicate is ColonyAnger.isValidTarget so the Pheromonal Disguise is honoured
        // at the ACQUISITION layer, and canAttack below honours it at the continuation layer.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                living -> living instanceof Player player && ColonyAnger.isValidTarget(player)));
    }

    /**
     * The disguise seam, the same one {@code SoldierAntEntity#canAttack} uses and for the
     * same reason: {@code canAttack(LivingEntity)} is the only hook vanilla re-reads while a
     * chase is <em>running</em> ({@code TargetGoal#canContinueToUse}) as well as while one is
     * being picked ({@code TargetingConditions#test}), so a rule installed only in a target
     * goal's predicate lets a mob keep chasing a player who has just become disguised. See
     * {@link ColonyAnger#colonyMayAttack} for the full argument.
     *
     * <p>An ender ant is not one of the colony's own -- it does not appear in
     * {@code ColonyAnger.isColonyAnt}, so hurting one raises no alarm and strips nobody's
     * disguise. It still respects the disguise, because from a player's side the effect has
     * to mean one thing everywhere in the dimension.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        return ColonyAnger.colonyMayAttack(target) && super.canAttack(target);
    }

    /**
     * Blinks away from whatever just landed a real hit.
     *
     * <p>Gated on {@code super.hurt} having returned {@code true}, which is the load-bearing
     * detail: {@code LivingEntity.hurt} returns early -- before recording anything -- when
     * {@code invulnerableTime > 10 && amount <= lastHurt}, so a follow-up tap inside the
     * invulnerability window is a no-op that would otherwise teleport the ant across the
     * room for free (banked in {@code docs/gotchas/entity-ai.md}).
     *
     * <p>{@code isAlive()} is checked as well so a killing blow does not teleport a corpse
     * out of its own drops.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean landed = super.hurt(source, amount);
        if (landed && !this.level().isClientSide() && this.isAlive()) {
            this.teleportRandomly();
        }
        return landed;
    }

    // ------------------------------------------------------------ teleport --

    /**
     * A blink to a random bearing {@link #TELEPORT_MIN_DISTANCE}-{@link
     * #TELEPORT_MAX_DISTANCE} blocks away. The "away" half of the spec's "teleports 8-16
     * blocks to close distance or when hurt".
     *
     * <p>Adapted from {@code EnderMan#teleport()}, which throws its target into a
     * {@code +-32} box on each axis. A box gives a distance distribution, not a distance;
     * this picks the bearing and the distance separately so "8 to 16 blocks" is a property of
     * every blink rather than of the average one.
     *
     * @return whether any of the {@link #TELEPORT_ATTEMPTS} candidates was usable
     */
    public boolean teleportRandomly() {
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            if (this.teleportAlong(this.random.nextDouble() * Math.PI * 2.0, this.blinkDistance())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A blink toward {@code target}, stopping {@link #TELEPORT_APPROACH_GAP} short of it.
     *
     * <p>Adapted from {@code EnderMan#teleportTowards(Entity)}: same "normalise the vector to
     * the quarry, step along it, ground-seek at the far end" shape, with the step length
     * drawn from this mob's own 8-16 range instead of the enderman's fixed 16, and clamped so
     * it can never arrive on top of the quarry. {@link EnderAntBlinkGoal} only calls this
     * when the target is already at least {@link EnderAntBlinkGoal#BLINK_MIN_DISTANCE} away,
     * which is what keeps the clamp from turning a blink into a shuffle.
     */
    public boolean teleportTowards(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) {
            return this.teleportRandomly();
        }
        double bearing = Math.atan2(dz, dx);
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            double distance = Math.min(this.blinkDistance(), horizontal - TELEPORT_APPROACH_GAP);
            if (distance <= 0.0) {
                return false;
            }
            if (this.teleportAlong(bearing, distance)) {
                return true;
            }
        }
        return false;
    }

    /** A blink length in {@code [TELEPORT_MIN_DISTANCE, TELEPORT_MAX_DISTANCE]}. */
    private double blinkDistance() {
        return TELEPORT_MIN_DISTANCE
                + this.random.nextDouble() * (TELEPORT_MAX_DISTANCE - TELEPORT_MIN_DISTANCE);
    }

    private boolean teleportAlong(double bearing, double distance) {
        double x = this.getX() + Math.cos(bearing) * distance;
        double z = this.getZ() + Math.sin(bearing) * distance;
        double y = this.getY() + this.random.nextInt(TELEPORT_VERTICAL_JITTER * 2 + 1) - TELEPORT_VERTICAL_JITTER;
        return this.blinkTo(x, y, z);
    }

    /**
     * The ground-seek teleport itself, adapted from {@code EnderMan#teleport(double, double,
     * double)} in {@code reference/} (EnderMan.java:308-335) statement for statement:
     * <ol>
     *   <li>walk a mutable position down from the candidate until the block there blocks
     *       motion or the world bottom is reached;</li>
     *   <li>refuse the candidate if what it landed on is not solid, or is water;</li>
     *   <li>give NeoForge's {@code EntityTeleportEvent.EnderEntity} a veto -- its javadoc is
     *       "fired before an Enderman or Shulker randomly teleports", i.e. it is the generic
     *       ender-teleport hook and it takes any {@code LivingEntity};</li>
     *   <li>hand the accepted position to {@code LivingEntity#randomTeleport}, which does its
     *       own downward walk and the collision/liquid check that actually moves the mob;</li>
     *   <li>emit {@code GameEvent.TELEPORT} and the teleport sound at both ends.</li>
     * </ol>
     * The one addition is the particles: vanilla's enderman spawns its portal particles
     * client-side from {@code aiStep} because it is always shedding them, whereas this mob
     * only sheds them when it blinks -- so they are sent explicitly from the server at the
     * position it left and the position it arrived at, which is what makes a blink readable
     * from either end.
     */
    private boolean blinkTo(double x, double y, double z) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        while (cursor.getY() > level.getMinBuildHeight() && !level.getBlockState(cursor).blocksMotion()) {
            cursor.move(Direction.DOWN);
        }

        BlockState landing = level.getBlockState(cursor);
        if (!landing.blocksMotion() || landing.getFluidState().is(FluidTags.WATER)) {
            return false;
        }

        net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderEntity event =
                net.neoforged.neoforge.event.EventHooks.onEnderTeleport(this, x, y, z);
        if (event.isCanceled()) {
            return false;
        }

        Vec3 from = this.position();
        if (!this.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
            return false;
        }

        level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(this));
        spawnBlinkParticles(level, from);
        spawnBlinkParticles(level, this.position());
        if (!this.isSilent()) {
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT,
                    this.getSoundSource(), 1.0F, 1.0F);
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        }
        return true;
    }

    private static void spawnBlinkParticles(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.3, at.z, 24, 0.3, 0.4, 0.3, 0.05);
    }

    // -------------------------------------------------------------- sounds --

    /**
     * Enderman voice rather than the colony's spider voice. The teleport sound is already
     * {@code ENDERMAN_TELEPORT} (it is the one vanilla event that means "something just
     * blinked"), and splitting the identity -- ant noises, ender teleport -- would make the
     * one audio cue that matters read as a different mob entirely.
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }
}
