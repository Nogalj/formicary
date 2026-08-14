package com.nogal.formicary.item;

import java.util.ArrayList;
import java.util.List;

import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.SoldierAntEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Pheromone Horn (spec section 3): crafted from the Royal Pheromone Gland the queen
 * drops, it calls two soldier ants to fight for you for a minute.
 *
 * <p>Not consumed -- it goes on the 1.21 item cooldown instead
 * ({@code Player#getCooldowns().addCooldown}), so the limit on it is rhythm rather than
 * inventory. The cooldown is only charged when the summon actually landed; a blast in a
 * space too tight to fit an ant costs nothing.
 *
 * <p>The allies are ordinary {@link SoldierAntEntity} instances carrying a summoner UUID
 * and an expiry (see {@code SoldierAntEntity#summonFor}). Everything that makes them yours
 * rather than the colony's follows from that one flag -- including the spec's requirement
 * that their fights never anger the nest, which
 * {@code ColonyAnger#isColonyAnt} delivers by no longer answering for them.
 */
public class PheromoneHornItem extends Item {

    /** Allies per blow. Tunable per spec ("2 allied soldier ants"). */
    public static final int SUMMON_COUNT = 2;

    /** How long an ally lasts: 1200 ticks = 60 seconds. Tunable. */
    public static final int SUMMON_LIFETIME_TICKS = 1200;

    /** Cooldown between blows: 2400 ticks = 120 seconds. Tunable. */
    public static final int COOLDOWN_TICKS = 2400;

    /** How far out an ally is placed, and how far the fallback search will wander. */
    private static final double SUMMON_RADIUS = 2.0;

    public PheromoneHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            // Client copy: swing the arm, let the server do the work.
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        List<SoldierAntEntity> summoned = summon(serverLevel, player);
        if (summoned.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.BEEHIVE_EXIT, SoundSource.PLAYERS, 1.2F, 0.6F);
        // Deliberately not consumed: the horn is reusable, the cooldown is the cost.
        return InteractionResultHolder.success(stack);
    }

    /**
     * Places {@link #SUMMON_COUNT} allied soldiers around {@code summoner} and returns the
     * ones that fit.
     *
     * <p>Public and static so a GameTest can call it with a mock player: the item's own
     * {@code use} is reachable from a test too, but this is the half that has anything to
     * assert about, and keeping it separate means the test does not have to fake an
     * {@code InteractionHand} to get at it.
     */
    public static List<SoldierAntEntity> summon(ServerLevel level, Player summoner) {
        List<SoldierAntEntity> summoned = new ArrayList<>(SUMMON_COUNT);
        for (int i = 0; i < SUMMON_COUNT; i++) {
            double angle = (Math.PI * 2.0 / SUMMON_COUNT) * i + summoner.getYRot() * Math.PI / 180.0;
            Vec3 spot = findSpot(level, summoner, angle);
            if (spot == null) {
                continue;
            }
            SoldierAntEntity ally = ModEntities.SOLDIER_ANT.get().create(level);
            if (ally == null) {
                continue;
            }
            ally.moveTo(spot.x, spot.y, spot.z, summoner.getYRot(), 0.0F);
            ally.finalizeSpawn(level, level.getCurrentDifficultyAt(ally.blockPosition()),
                    MobSpawnType.MOB_SUMMONED, null);
            ally.summonFor(summoner, SUMMON_LIFETIME_TICKS);
            level.addFreshEntity(ally);
            level.sendParticles(ParticleTypes.FALLING_HONEY, spot.x, spot.y + 0.4, spot.z, 24, 0.35, 0.3, 0.35, 0.01);
            summoned.add(ally);
        }
        return summoned;
    }

    /**
     * A spot on the ring at {@code angle} the ant actually fits in, falling back to the
     * summoner's own position -- which is guaranteed to be somewhere a body already stands.
     */
    private static Vec3 findSpot(ServerLevel level, Player summoner, double angle) {
        Vec3 base = summoner.position();
        Vec3 candidate = base.add(Math.cos(angle) * SUMMON_RADIUS, 0.0, Math.sin(angle) * SUMMON_RADIUS);
        if (level.noCollision(ModEntities.SOLDIER_ANT.get().getSpawnAABB(candidate.x, candidate.y, candidate.z))) {
            return candidate;
        }
        if (level.noCollision(ModEntities.SOLDIER_ANT.get().getSpawnAABB(base.x, base.y, base.z))) {
            return base;
        }
        return null;
    }
}
