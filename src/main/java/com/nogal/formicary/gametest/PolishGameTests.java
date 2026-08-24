package com.nogal.formicary.gametest;

import java.util.List;
import java.util.Optional;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.advancement.CasteGrownTrigger;
import com.nogal.formicary.advancement.ModCriteriaTriggers;
import com.nogal.formicary.block.FungalSporeCropBlock;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.TamedWorkerAntEntity;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for M8 Polish: the Fungal Spore crop's harvest loop and the Fungal
 * Bloom's ant-feed use. Follows {@code TamingGameTests}' established conventions -- see its
 * class javadoc for the two harness limits (a bare, never-added {@code Player} from
 * {@code makeMockPlayer}; {@code absolutePos(0,0,0)} is the structure block, so a
 * template's floor sits at relative y=1).
 */
@GameTestHolder(Formicary.MODID)
public class PolishGameTests {
    private static final int STAND_Y = 2;

    /**
     * The M6 farm test's pattern (see {@code TamingGameTests
     * #bound_worker_harvests_replants_and_deposits}), extended to the mod's own crop:
     * planted on plain {@code DIRT} rather than farmland, which is only true because
     * {@link FungalSporeCropBlock#mayPlaceOn} was widened back from {@code CropBlock}'s
     * farmland-only default (spec section 5: "spores plant... on dirt"). A mature (age
     * {@link FungalSporeCropBlock#MAX_AGE}) crop is harvested, replanted at age 0, and its
     * bloom lands in the bound chest -- proving {@code ModBlockTagsProvider}'s {@code
     * harvestable_crops} addition and {@code CropHarvest}'s generic maturity/seed logic
     * both recognise this block with no code change of their own.
     *
     * <p>Deliberately NOT asserted: a "spare" spore in the chest. The mature loot table
     * rolls 1-2 {@code fungal_spores} (see {@code ModBlockLootSubProvider
     * #fungalSporeCropTable}), and {@code takeSeed} always spends exactly one on the
     * replant -- so a 1-roll leaves nothing spare, a 2-roll leaves one. A first draft of
     * this test asserted a spare deterministically and failed about half the time in
     * practice; it is the same class of loot-table-nondeterminism trap {@code
     * TamingGameTests#bound_worker_harvests_replants_and_deposits}'s javadoc documents for
     * wheat (there, a 9% chance of *zero* seeds; here, a 50% chance of *no spare* after the
     * replant is paid for).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", skyAccess = true, timeoutTicks = 600)
    public static void bound_worker_harvests_and_replants_the_fungal_spore_crop(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, STAND_Y, 4);
        BlockPos crop = new BlockPos(6, STAND_Y, 4);

        helper.setBlock(chest, Blocks.CHEST);
        // Plain dirt, not farmland -- the point of this test is the widened mayPlaceOn.
        helper.setBlock(crop.below(), Blocks.DIRT);
        helper.setBlock(crop, ModBlocks.FUNGAL_SPORE_CROP.get().defaultBlockState()
                .setValue(FungalSporeCropBlock.AGE, FungalSporeCropBlock.MAX_AGE));

        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(4, STAND_Y, 4));
        worker.tame(keeper);
        worker.bindTo(helper.absolutePos(chest));

        helper.succeedWhen(() -> {
            helper.assertBlockState(crop,
                    state -> state.is(ModBlocks.FUNGAL_SPORE_CROP.get())
                            && state.getValue(FungalSporeCropBlock.AGE) == 0,
                    () -> "expected a freshly replanted fungal spore crop at " + crop + ", found "
                            + helper.getBlockState(crop));
            Container container = HopperBlockEntity.getContainerAt(helper.getLevel(), helper.absolutePos(chest));
            helper.assertTrue(container != null, "the bound chest should still be a container");
            // The bloom is unconditional in the mature loot pool -- deterministic, unlike
            // the spore count (see the method javadoc for why that one isn't asserted).
            helper.assertTrue(container.countItem(ModItems.FUNGAL_BLOOM.get()) > 0,
                    "the worker should have deposited the fungal bloom cut from the mature crop");
        });
    }

    /**
     * Play-test round 9: the Fungal Bloom sits on ANY block, not just soil.
     *
     * <p>It used to accept only the three tier soils, Anthill Soil and vanilla dirt, which
     * made the mod's one light-emitting plant useless as a build material. This walks a
     * deliberately unsoil-like set -- stone, glass, planks, and two of the mod's own
     * crafted blocks -- and checks the bloom both places AND survives on each.
     *
     * <p>Asserting {@code canSurvive} as well as presence is the point: {@code setBlock}
     * will happily place a block that a neighbour update then pops off a tick later, so
     * presence alone would pass even with the old soil-only rule still in force.
     *
     * <p>The air case is the other half of the contract. "Any block" still means there has
     * to BE a block -- a bloom whose support is air must not survive, or it floats.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "farm_platform", timeoutTicks = 200)
    public static void fungal_bloom_places_on_any_block(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, STAND_Y, 2);
        BlockState bloom = ModBlocks.FUNGAL_BLOOM.get().defaultBlockState();

        List<Block> grounds = List.of(
                Blocks.STONE, Blocks.GLASS, Blocks.OAK_PLANKS,
                ModBlocks.RESIN_BLOCK.get(), ModBlocks.BROOD_COMB.get());

        for (Block ground : grounds) {
            helper.setBlock(at, Blocks.AIR);
            helper.setBlock(at.below(), ground);
            helper.setBlock(at, bloom);
            helper.assertBlockPresent(ModBlocks.FUNGAL_BLOOM.get(), at);
            helper.assertTrue(bloom.canSurvive(helper.getLevel(), helper.absolutePos(at)),
                    "a fungal bloom should survive on " + ground.getName().getString());
        }

        helper.setBlock(at, Blocks.AIR);
        helper.setBlock(at.below(), Blocks.AIR);
        helper.assertFalse(bloom.canSurvive(helper.getLevel(), helper.absolutePos(at)),
                "a fungal bloom must not survive with air beneath it");

        helper.succeed();
    }

    /**
     * Spec section 5/8: a Fungal Bloom in hand heals a tamed ant ~4 HP and is consumed; a
     * full-health ant is a no-op (nothing healed, nothing spent).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void fungal_bloom_heals_a_hurt_tamed_worker_and_is_a_no_op_at_full_health(GameTestHelper helper) {
        Player keeper = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedWorkerAntEntity worker = helper.spawn(ModEntities.TAMED_WORKER_ANT.get(),
                new BlockPos(2, STAND_Y, 2));
        worker.tame(keeper);
        worker.setHealth(Math.max(1.0F, worker.getMaxHealth() - 6.0F));
        float hurtHealth = worker.getHealth();
        keeper.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FUNGAL_BLOOM.get(), 2));

        worker.interact(keeper, InteractionHand.MAIN_HAND);

        helper.assertTrue(worker.getHealth() > hurtHealth, "a fungal bloom should heal a hurt worker");
        helper.assertValueEqual(keeper.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1,
                "feeding should consume exactly one fungal bloom");

        worker.setHealth(worker.getMaxHealth());
        worker.interact(keeper, InteractionHand.MAIN_HAND);

        helper.assertValueEqual(worker.getHealth(), worker.getMaxHealth(),
                "health must stay capped, not overheal past max");
        helper.assertValueEqual(keeper.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1,
                "a full-health ant must not consume the item -- spec: 'no-op at full health'");
        helper.succeed();
    }

    /**
     * The custom criterion triggers' own matching logic (spec section 8: "if assertable in
     * a GameTest"). This is as far as this harness can honestly reach:
     * {@link CasteGrownTrigger.TriggerInstance#matches} is a pure function, checkable
     * directly, and is the entire reason "raise both castes" needs two criteria rather than
     * one on a single trigger. What it cannot prove headlessly is a full award -- that needs
     * a real {@code ServerPlayer} with {@code PlayerAdvancements} listening, and {@code
     * GameTestHelper.makeMockPlayer} returns a bare {@code Player} with no {@code
     * getAdvancements()} at all (the one alternative, {@code makeMockServerPlayerInLevel()},
     * is {@code @Deprecated(forRemoval = true)} and adds a real player to the level via the
     * player list -- heavier machinery than anything else in this test suite reaches for).
     * {@link ModCriteriaTriggers#CASTE_GROWN} and {@link ModCriteriaTriggers#FIRST_HARVEST}
     * are exercised for real in play via {@code LarvaEntity#growInto} and {@code
     * DepositToChestGoal}, which this file's other test and the M6 harvest test already
     * drive end to end (minus the final advancement award, for the reason above).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void caste_grown_criterion_matches_only_its_own_caste(GameTestHelper helper) {
        CasteGrownTrigger.TriggerInstance workerOnly =
                new CasteGrownTrigger.TriggerInstance(Optional.empty(), Optional.of(CasteGrownTrigger.Caste.WORKER));
        helper.assertTrue(workerOnly.matches(CasteGrownTrigger.Caste.WORKER),
                "a worker-only criterion matches a worker");
        helper.assertFalse(workerOnly.matches(CasteGrownTrigger.Caste.SOLDIER),
                "a worker-only criterion must not match a soldier");

        CasteGrownTrigger.TriggerInstance either =
                new CasteGrownTrigger.TriggerInstance(Optional.empty(), Optional.empty());
        helper.assertTrue(either.matches(CasteGrownTrigger.Caste.WORKER),
                "an unfiltered criterion matches either caste (unused by the shipped advancement)");
        helper.assertTrue(either.matches(CasteGrownTrigger.Caste.SOLDIER),
                "an unfiltered criterion matches either caste (unused by the shipped advancement)");
        helper.succeed();
    }
}
