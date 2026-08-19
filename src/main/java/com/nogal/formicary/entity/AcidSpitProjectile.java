package com.nogal.formicary.entity;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The queen's ranged answer to a player who will not come close (Ep2, task F2).
 *
 * <p>Structurally a copy of vanilla's {@code LlamaSpit} -- verified line by line against
 * {@code reference/net/minecraft/world/entity/projectile/LlamaSpit.java}, which is the
 * only ballistic {@code Projectile} in the game that is neither an arrow nor an item, and
 * therefore the one worth copying. The three deliberate departures are documented on the
 * methods that make them: it refuses to hit the colony, it lands a poison rider, and it
 * expires.
 *
 * <p><b>No lingering pool.</b> A hit is one instant of damage at the point of impact and
 * nothing else -- no {@code AreaEffectCloud}, no block change. That is a design decision,
 * not an omission: an acid pool would turn her chamber into a hazard the player has to
 * re-path around for the rest of the fight, which is a different (and much cheaper) fight
 * than the one the phase machinery is building.
 */
public class AcidSpitProjectile extends Projectile {
    /** Damage on a direct hit. Tunable. (Ep2 play-test revision: 4.0 -> 2.0, less lethal.) */
    public static final float IMPACT_DAMAGE = 2.0F;

    /** Poison duration on a direct hit, in ticks (3 seconds). Tunable. */
    public static final int POISON_TICKS = 60;

    /** Poison amplifier: 0 = Poison I. Tunable. */
    public static final int POISON_AMPLIFIER = 0;

    /**
     * Launch speed. Fast enough that her 16-block maximum range is roughly a second of
     * flight, which is the window a player gets to break line of sight.
     */
    public static final float LAUNCH_VELOCITY = 1.6F;

    /**
     * Launch inaccuracy, in the same units {@code Projectile#shoot} takes. Vanilla's llama
     * uses 10; she is a boss and this is her only ranged option, so 4 -- enough spread that
     * standing still is not literally free, not enough that the attack reads as broken.
     */
    public static final float LAUNCH_INACCURACY = 4.0F;

    /**
     * A blob that hits nothing at all is removed after this many ticks.
     *
     * <p>{@code LlamaSpit} has no such cap: it only discards inside a solid block or in
     * water, so one fired out over a gap flies until the chunk unloads. Hers is fired
     * repeatedly for the length of a boss fight, in a chamber with open galleries above it,
     * so "eventually something stops it" is not a lifetime.
     */
    public static final int MAX_LIFE_TICKS = 100;

    private int life;

    public AcidSpitProjectile(EntityType<? extends AcidSpitProjectile> entityType, Level level) {
        super(entityType, level);
    }

    /** Spawns one at {@code shooter}'s mouth height, unaimed -- the caller shoots it. */
    public AcidSpitProjectile(Level level, LivingEntity shooter) {
        this(ModEntities.ACID_SPIT.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.2, shooter.getZ());
    }

    /** A shade under {@code LlamaSpit}'s 0.06: her spit carries further before it drops. */
    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    /**
     * {@code LlamaSpit#tick} verbatim, plus the lifetime cap.
     *
     * <p>The {@code onProjectileImpact} guard is vanilla's own (NeoForge patches it in
     * there), and the banked rule about it applies: cancelling that event suppresses
     * {@link #onHit} but does <em>not</em> stop the entity, which is why the miss branch
     * below still has to run and why {@link #MAX_LIFE_TICKS} is the real backstop.
     */
    @Override
    public void tick() {
        super.tick();
        if (++this.life >= MAX_LIFE_TICKS) {
            this.discard();
            return;
        }

        Vec3 movement = this.getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS
                && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
        }

        double x = this.getX() + movement.x;
        double y = this.getY() + movement.y;
        double z = this.getZ() + movement.z;
        this.updateRotation();
        if (this.level().getBlockStates(this.getBoundingBox())
                .noneMatch(BlockBehaviour.BlockStateBase::isAir)) {
            this.discard();
        } else if (this.isInWaterOrBubble()) {
            // Acid and water: the same rule vanilla's spit follows, and it reads as a
            // counter rather than as a quirk.
            this.discard();
        } else {
            this.setDeltaMovement(movement.scale(0.99));
            this.applyGravity();
            this.setPos(x, y, z);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * The colony is immune to its own queen.
     *
     * <p>Without this, a burst that just placed three soldiers on a ring between her and
     * the player turns her only ranged attack into a way of killing her own reinforcements
     * -- and, because a soldier's injury runs through
     * {@code ColonyAngerEvents.onColonyAntHurt}, into a way of angering the nest at nobody.
     * Tamed ants are spared too: the player's escort should not be the thing that eats a
     * shot aimed at the player.
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (ColonyAnger.isColonyAnt(target) || ColonyAnger.isTamedAnt(target)) {
            return false;
        }
        return super.canHitEntity(target);
    }

    /**
     * Four damage and a short Poison, then gone.
     *
     * <p>The damage source is {@code mobProjectile(this, owner)}, which credits the queen
     * as the attacker and this blob as the direct entity -- so a death message names her,
     * and {@code ColonyAnger.offenderOf} still resolves nobody (neither is a player), which
     * is what keeps the colony from somehow being angered by its own queen's attack.
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity shooter = this.getOwner() instanceof LivingEntity living ? living : null;
        Entity victim = result.getEntity();
        DamageSource source = this.damageSources().mobProjectile(this, shooter);
        if (victim.hurt(source, IMPACT_DAMAGE) && victim instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_AMPLIFIER));
        }
        splash(serverLevel);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            splash(serverLevel);
        }
        this.discard();
    }

    /** Particles only -- see the class javadoc on why there is no pool left behind. */
    private void splash(ServerLevel level) {
        level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY(), this.getZ(),
                12, 0.2, 0.2, 0.2, 0.05);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /**
     * The launch puff, drawn client-side from the spawn packet's own velocity -- vanilla's
     * trick for giving a projectile a muzzle flash without a second packet.
     */
    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        double xa = packet.getXa();
        double ya = packet.getYa();
        double za = packet.getZa();
        for (int i = 0; i < 5; i++) {
            double spread = 0.4 + 0.1 * i;
            this.level().addParticle(ParticleTypes.ITEM_SLIME, this.getX(), this.getY(), this.getZ(),
                    xa * spread, ya, za * spread);
        }
        this.setDeltaMovement(xa, ya, za);
    }
}
