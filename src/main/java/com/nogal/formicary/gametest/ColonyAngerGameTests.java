package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.colony.ColonyAnger;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.WorkerAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the colony's neutral-aggro system (spec section 3).
 *
 * <p>Two of the three triggers are exercised through their real code path:
 * {@link #ant_hurt_by_player_angers_nearby_soldier} calls {@code LivingEntity.hurt}
 * with a genuine player-attack damage source, so vanilla itself fires
 * {@code LivingIncomingDamageEvent} and the handler is reached the same way it is in
 * play. The block-break tests cannot do the same: {@code BlockEvent.BreakEvent} is
 * fired by {@code ServerPlayerGameMode.destroyBlock}, which needs a real
 * {@code ServerPlayer} with a network connection, and {@code GameTestHelper}'s mock
 * player is a bare {@code Player} that is never added to the level. They therefore post
 * the event exactly as the server does and then clear the block -- which still covers
 * the handler, the tag lookup and the radius sweep, but NOT the assumption that vanilla
 * fires that event for a player break.
 */
@GameTestHolder(Formicary.MODID)
public class ColonyAngerGameTests {
    private static final BlockPos NEAR_BLOCK = new BlockPos(1, 1, 2);
    private static final BlockPos NEAR_ANT = new BlockPos(3, 1, 2);

    // long_platform is 48 long on X: a soldier at x=44 is 42 blocks from a trigger at
    // x=2, comfortably outside ColonyAnger.ANGER_RADIUS (24).
    private static final BlockPos LONG_TRIGGER_BLOCK = new BlockPos(1, 1, 2);
    private static final BlockPos LONG_NEAR_ANT = new BlockPos(3, 1, 2);
    private static final BlockPos LONG_FAR_ANT = new BlockPos(44, 1, 2);

    /** Breaking a hive-tagged block angers a soldier inside the radius. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void hive_break_angers_nearby_soldier(GameTestHelper helper) {
        SoldierAntEntity soldier = helper.spawn(ModEntities.SOLDIER_ANT.get(), NEAR_ANT);
        Player offender = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertFalse(soldier.isAngry(), "soldier should start neutral");

        breakAsPlayer(helper, NEAR_BLOCK, ModBlocks.BROOD_COMB.get(), offender);

        helper.assertTrue(soldier.isAngry(), "soldier should be angry after a hive block broke nearby");
        helper.assertValueEqual(soldier.getPersistentAngerTarget(), offender.getUUID(),
                "soldier's anger target");
        helper.assertValueEqual(soldier.getRemainingPersistentAngerTime(),
                ColonyAnger.SOLDIER_ANGER_TICKS, "soldier's anger timer");
        helper.succeed();
    }

    /** Same break; a soldier well beyond the radius stays neutral. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "long_platform")
    public static void distant_soldier_stays_neutral(GameTestHelper helper) {
        // The near soldier is a control: without it a handler that never fires at all
        // would pass this test vacuously.
        SoldierAntEntity near = helper.spawn(ModEntities.SOLDIER_ANT.get(), LONG_NEAR_ANT);
        SoldierAntEntity far = helper.spawn(ModEntities.SOLDIER_ANT.get(), LONG_FAR_ANT);
        Player offender = helper.makeMockPlayer(GameType.SURVIVAL);

        breakAsPlayer(helper, LONG_TRIGGER_BLOCK, ModBlocks.BROOD_COMB.get(), offender);

        helper.assertTrue(near.isAngry(), "soldier inside the radius should be angry");
        helper.assertFalse(far.isAngry(),
                "soldier " + (int) Math.sqrt(far.distanceToSqr(near)) + " blocks away should stay neutral");
        helper.succeed();
    }

    /** Foraging is not theft: Resin Weep is not hive-tagged, so nothing is provoked. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void resin_weep_break_does_not_anger(GameTestHelper helper) {
        SoldierAntEntity soldier = helper.spawn(ModEntities.SOLDIER_ANT.get(), NEAR_ANT);
        WorkerAntEntity worker = helper.spawn(ModEntities.WORKER_ANT.get(), NEAR_ANT);
        Player forager = helper.makeMockPlayer(GameType.SURVIVAL);

        breakAsPlayer(helper, NEAR_BLOCK, ModBlocks.RESIN_WEEP.get(), forager);

        helper.assertFalse(soldier.isAngry(), "harvesting resin must not anger a soldier");
        helper.assertFalse(worker.isFleeing(), "harvesting resin must not scare a worker");
        helper.succeed();
    }

    /** Trigger (a), through vanilla's own damage pipeline: hurting one ant rouses the rest. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void ant_hurt_by_player_angers_nearby_soldier(GameTestHelper helper) {
        WorkerAntEntity victim = helper.spawn(ModEntities.WORKER_ANT.get(), NEAR_BLOCK);
        SoldierAntEntity soldier = helper.spawn(ModEntities.SOLDIER_ANT.get(), NEAR_ANT);
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);

        victim.hurt(helper.getLevel().damageSources().playerAttack(attacker), 1.0F);

        helper.assertTrue(soldier.isAngry(), "soldier should answer for a hurt nestmate");
        helper.assertValueEqual(soldier.getPersistentAngerTarget(), attacker.getUUID(),
                "soldier's anger target");
        helper.assertTrue(victim.isFleeing(), "the hurt worker should be fleeing");
        helper.assertValueEqual(victim.getFleeTicks(), ColonyAnger.WORKER_FLEE_TICKS,
                "worker's flee timer");
        helper.succeed();
    }

    /**
     * Posts {@code BlockEvent.BreakEvent} for {@code block} at {@code relPos} on behalf of
     * {@code player}, the way {@code ServerPlayerGameMode.destroyBlock} does, then clears
     * the block.
     */
    private static void breakAsPlayer(GameTestHelper helper, BlockPos relPos, Block block, Player player) {
        helper.setBlock(relPos, block);
        BlockPos absPos = helper.absolutePos(relPos);
        BlockState state = helper.getLevel().getBlockState(absPos);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), absPos, state, player));
        helper.setBlock(relPos, Blocks.AIR);
    }
}
