package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.TamedSoldierAntEntity;
import com.nogal.formicary.entity.WorkerAntEntity;
import com.nogal.formicary.item.ModItems;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
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

    // ----------------------------------------------------------------- provision comb --

    /**
     * Provision Comb's loot always includes at least one of the colony foods.
     *
     * <p><b>Retargeted in play-test round 2, and deliberately not weakened.</b> The
     * assertion used to be "always at least one ender pearl", which was Ep2's exit-economy
     * affordability floor. Round 2 moved that floor to the ender ant (a guaranteed pearl per
     * kill) and demoted the comb's pearl to a 5% bonus, so the old assertion is now a
     * statement about a thing the design deliberately stopped guaranteeing -- asserting it
     * would pin the behaviour this round exists to replace. The invariant underneath it
     * survives intact and is what is asserted here instead: <em>breaking a Provision Comb
     * always yields something</em>. The food pool carries that now -- 1-2 rolls over three
     * weighted entries with no {@code empty} among them -- so this is exactly as
     * deterministic as the pearl assertion was, on the item that took over the job.
     *
     * <p>Broken with {@code level.destroyBlock(pos, true)}, exactly like
     * {@link #egg_cluster_break_pops_experience_and_no_items} -- {@code
     * GameTestHelper#destroyBlock} hardcodes {@code dropBlock=false} and would skip the
     * loot table entirely (banked in {@code docs/gotchas/gametest.md}), making this test
     * pass for the wrong reason (nothing dropping at all).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void provision_comb_break_always_drops_colony_food(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, STAND_Y, 2);
        helper.setBlock(pos, ModBlocks.PROVISION_COMB.get());

        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getLevel()
                    .getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0));
            helper.assertTrue(drops.stream().anyMatch(drop -> isColonyFood(drop.getItem())),
                    "breaking a Provision Comb should always drop at least one colony food, got "
                            + drops.stream().map(drop -> drop.getItem().getItem().toString()).toList());
        });
    }

    /**
     * The shape of the WP-R3-expanded Provision Comb table, rolled rather than broken.
     *
     * <p><b>Retargeted again in play-test round 2's follow-up revision (WP-R3 item 1),
     * deliberately not weakened.</b> The table's shape changed twice now: pearls went from
     * unconditional to a 5% bonus (round 2), and the separate ore pool -- which had a
     * 25-of-85 {@code empty} entry and only paid out ~71% of the time -- was replaced by one
     * merged sundries pool with no {@code empty} entry at all, so it now pays out on every
     * roll, same as the food pool. That "always pays out" property is what survives here in
     * place of the old payout-band assertion: it is no longer probabilistic, so it is
     * asserted the same deterministic way the food pool's payout already was, not weakened
     * into a band. A single world break still cannot see any of this (the pool split, the weights,
     * or the pearl rate), and breaking 400 blocks in a shared 5x5 arena would trip the
     * radius-search contamination trap banked in {@code docs/gotchas/gametest.md} -- so this
     * still asks the real registered loot table directly, with a fixed {@code RandomSource}.
     *
     * <p>What is still genuinely statistical is the sundries pool's <em>internal</em>
     * weighting, so two new checks replace the old ore-payout band, both with margins many
     * standard deviations wide the same way the pearl check already was:
     * <ul>
     *   <li><b>Coal</b> (weight 16 of 113, the pool's single heaviest entry) should turn up
     *       often. With the pool rolling 1 or 2 times (50/50) the per-break chance of at
     *       least one coal is ~20.3%; at n = 400 that is a mean of ~81 with SD ~8.0, so the
     *       [40, 160] band asserted below sits ~5.1 SD below and ~9.8 SD above the mean.</li>
     *   <li><b>A rare entry</b> (Diamond, Emerald, or any of the four loose leather armor
     *       pieces -- combined weight 6 of 113, the pool's lightest tier) should still turn
     *       up sometimes but stay uncommon. Per-break chance ~7.8%; at n = 400 that is a
     *       mean of ~31 with SD ~5.4, so [1, 133] sits comfortably inside both directions
     *       (chance of zero hits in 400 breaks is astronomically small, ~6.5e-15) while still
     *       leaving a wide margin against the "uncommon" upper bound.</li>
     * </ul>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void provision_comb_pearls_are_a_bonus_and_its_sundries_pool_always_pays_out(GameTestHelper helper) {
        LootTable table = helper.getLevel().getServer().reloadableRegistries()
                .getLootTable(ModBlocks.PROVISION_COMB.get().getLootTable());
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, STAND_Y, 2))))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.BLOCK_STATE,
                        ModBlocks.PROVISION_COMB.get().defaultBlockState())
                .create(LootContextParamSets.BLOCK);

        RandomSource random = new XoroshiroRandomSource(20260819L);
        int rolls = 400;
        int withPearl = 0;
        int withFood = 0;
        int withSundries = 0;
        int withCommonSundries = 0;
        int withRareSundries = 0;
        for (int roll = 0; roll < rolls; roll++) {
            boolean pearl = false;
            boolean food = false;
            boolean sundries = false;
            boolean commonSundries = false;
            boolean rareSundries = false;
            for (ItemStack stack : table.getRandomItems(params, random)) {
                pearl |= stack.is(Items.ENDER_PEARL);
                food |= isColonyFood(stack);
                sundries |= isLarderSundries(stack);
                commonSundries |= stack.is(Items.COAL);
                rareSundries |= isRareLarderSundries(stack);
            }
            withPearl += pearl ? 1 : 0;
            withFood += food ? 1 : 0;
            withSundries += sundries ? 1 : 0;
            withCommonSundries += commonSundries ? 1 : 0;
            withRareSundries += rareSundries ? 1 : 0;
        }

        helper.assertTrue(withFood == rolls,
                "every Provision Comb roll should yield colony food, got " + withFood + " of " + rolls);
        helper.assertTrue(withSundries == rolls,
                "the sundries pool has no empty entry and should pay out on every roll, got "
                        + withSundries + " of " + rolls);
        helper.assertTrue(withPearl < rolls / 5,
                "ender pearls should be a rare bonus (nominally 5%), got " + withPearl + " of " + rolls);
        helper.assertTrue(withPearl > 0,
                "ender pearls should still be possible, got none in " + rolls + " rolls");
        helper.assertTrue(withCommonSundries > rolls / 10 && withCommonSundries < rolls * 2 / 5,
                "coal is the sundries pool's heaviest entry and should show up often (nominally ~20%), got "
                        + withCommonSundries + " of " + rolls);
        helper.assertTrue(withRareSundries > 0,
                "the sundries pool's rarest entries (diamond/emerald/leather armor) should still be "
                        + "possible, got none in " + rolls + " rolls");
        helper.assertTrue(withRareSundries < rolls / 3,
                "the sundries pool's rarest entries should stay uncommon (nominally ~7.8%), got "
                        + withRareSundries + " of " + rolls);
        helper.succeed();
    }

    /** The three colony foods the larder's unconditional pool draws from. */
    private static boolean isColonyFood(ItemStack stack) {
        return stack.is(ModItems.HONEYED_COMB.get())
                || stack.is(ModItems.FUNGAL_STEW.get())
                || stack.is(ModItems.ROYAL_JELLY_TREAT.get());
    }

    /**
     * Any of the 18 vanilla materials the larder's WP-R3 sundries pool draws from (the
     * merged replacement for round 2's separate, sometimes-empty ore pool).
     */
    private static boolean isLarderSundries(ItemStack stack) {
        return stack.is(Items.STICK) || stack.is(Items.FLINT) || stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.BONE) || stack.is(Items.STRING) || stack.is(Items.LEATHER)
                || stack.is(Items.FEATHER) || stack.is(Items.COAL) || stack.is(Items.RAW_COPPER)
                || stack.is(Items.RAW_IRON) || stack.is(Items.RAW_GOLD) || stack.is(Items.LAPIS_LAZULI)
                || stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.LEATHER_HELMET)
                || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS)
                || stack.is(Items.LEATHER_BOOTS);
    }

    /**
     * The sundries pool's lightest weighted tier (1 each, 6 of 113 total): Diamond,
     * Emerald, and the four loose leather armor pieces.
     */
    private static boolean isRareLarderSundries(ItemStack stack) {
        return stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.LEATHER_HELMET)
                || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS)
                || stack.is(Items.LEATHER_BOOTS);
    }
}
