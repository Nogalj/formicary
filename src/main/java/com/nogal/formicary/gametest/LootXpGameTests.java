package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.TamedSoldierAntEntity;
import com.nogal.formicary.entity.WorkerAntEntity;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for play-test round 1's loot + XP package (see {@code
 * docs/DECISIONS.md}, "Play-test round 1"): chitin becoming soldier-only, every caste
 * granting XP -- including the {@code TamableAnimal}/{@code Animal} override trap the tamed
 * castes hit -- and Egg Cluster's switch to an XP-only, no-item break.
 *
 * <p>Every kill here uses a single, deliberately oversized hit ({@code 1000.0F} against
 * castes with at most 24 max health) so the invulnerability-window trap {@code
 * QueenGameTests} and {@code TamingGameTests} already document (a follow-up hit no bigger
 * than the last one is a silent no-op) never comes up: there is only one hit.
 */
@GameTestHolder(Formicary.MODID)
public class LootXpGameTests {
    private static final int STAND_Y = 2;

    // ------------------------------------------------------------------ chitin --

    /**
     * Spec item 1: chitin is soldier-only now. Deterministic, not probabilistic -- the
     * worker's loot table has no chitin pool left at all (see {@code
     * ModEntityLootSubProvider}), so a single kill is enough to prove it never appears,
     * unlike the Scent Gland pool this test does not touch.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void worker_ant_death_no_longer_drops_chitin(GameTestHelper helper) {
        Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerAntEntity worker = helper.spawn(ModEntities.WORKER_ANT.get(), new BlockPos(2, STAND_Y, 2));

        worker.hurt(helper.getLevel().damageSources().playerAttack(killer), 1000.0F);

        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(ModEntities.WORKER_ANT.get());
            helper.assertItemEntityNotPresent(ModItems.CHITIN.get());
        });
    }

    /**
     * Spec item 1 (soldier stays a chitin source) and spec item 2 (soldiers grant more XP
     * than a worker) together: the soldier's chitin pool is unconditional -- {@code
     * chitinTable}'s pool has a constant roll and no {@code randomChance} on the item itself
     * -- so both this test's assertions are deterministic, not "usually happens".
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void soldier_ant_death_drops_chitin_and_its_xp_reward(GameTestHelper helper) {
        Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
        SoldierAntEntity soldier = helper.spawn(ModEntities.SOLDIER_ANT.get(), new BlockPos(2, STAND_Y, 2));

        soldier.hurt(helper.getLevel().damageSources().playerAttack(killer), 1000.0F);

        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(ModEntities.SOLDIER_ANT.get());
            helper.assertItemEntityPresent(ModItems.CHITIN.get());
            helper.assertTrue(
                    helper.getEntities(EntityType.EXPERIENCE_ORB).stream()
                            .anyMatch(orb -> orb.getValue() == SoldierAntEntity.XP_REWARD),
                    "expected an experience orb worth " + SoldierAntEntity.XP_REWARD
                            + " (SoldierAntEntity.XP_REWARD) from a wild soldier's death");
        });
    }

    // ----------------------------------------------------------------- xp trap --

    /**
     * The exact trap the brief calls out by name. {@code TamedSoldierAntEntity extends
     * TamableAnimal}, and {@code TamableAnimal}'s ancestor {@code Animal} overrides {@code
     * getBaseExperienceReward()} to a flat {@code 1 + random.nextInt(3)}, completely
     * ignoring {@code Mob#xpReward} -- verified in {@code Animal.java}. A naive fix that
     * just set {@code this.xpReward} in the constructor (the pattern every wild caste in
     * this file uses) would compile clean and silently cap a tamed soldier's death at 1-3
     * XP forever.
     *
     * <p>{@code SoldierAntEntity#XP_REWARD} (7) is deliberately outside that 1-3 fallback
     * range, so this assertion can only pass if {@link
     * TamedSoldierAntEntity#getBaseExperienceReward()}'s override is actually the method
     * that runs -- an orb worth exactly 7 is not something the ignored-field fallback can
     * ever produce.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void tamed_soldier_ant_death_awards_the_wild_soldiers_xp(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedSoldierAntEntity soldier = helper.spawn(ModEntities.TAMED_SOLDIER_ANT.get(),
                new BlockPos(2, STAND_Y, 2));
        soldier.tame(owner);

        soldier.hurt(helper.getLevel().damageSources().playerAttack(owner), 1000.0F);

        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(ModEntities.TAMED_SOLDIER_ANT.get());
            helper.assertTrue(
                    helper.getEntities(EntityType.EXPERIENCE_ORB).stream()
                            .anyMatch(orb -> orb.getValue() == SoldierAntEntity.XP_REWARD),
                    "a tamed soldier must award the same XP as its wild counterpart (expected "
                            + SoldierAntEntity.XP_REWARD + ") -- if this regresses, check that "
                            + "getBaseExperienceReward() is still overridden rather than relying "
                            + "on Mob#xpReward, which Animal's own override ignores");
        });
    }

    // -------------------------------------------------------------- egg cluster --

    /**
     * Spec item 3: Egg Cluster drops no items and pops 3-7 XP on a normal break.
     *
     * <p>{@code ModBlocks.EGG_CLUSTER} is a {@code DropExperienceBlock}, whose XP is
     * awarded through a NeoForge {@code BlockDropsEvent} fired from {@code
     * Block.dropResources} -- entirely separate from the loot table this test also proves
     * stays empty. That is why this breaks the block with {@code level.destroyBlock(pos,
     * true)} rather than {@code GameTestHelper.destroyBlock(pos)}: the helper's shortcut
     * hardcodes {@code dropBlock=false} and would skip both the loot table and the XP event
     * entirely, making this test pass for the wrong reason (nothing happening at all).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void egg_cluster_break_pops_experience_and_no_items(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, STAND_Y, 2);
        helper.setBlock(pos, ModBlocks.EGG_CLUSTER.get());

        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

        helper.succeedWhen(() -> {
            helper.assertEntityPresent(EntityType.EXPERIENCE_ORB);
            helper.assertEntityNotPresent(EntityType.ITEM);
        });
    }
}
