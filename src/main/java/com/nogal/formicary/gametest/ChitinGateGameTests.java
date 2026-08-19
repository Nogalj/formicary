package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.item.ChitinArmor;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the Ep2 play-test revision to the fabric mining gate
 * ({@code docs/superpowers/plans/2026-08-19-ep2-playtest-revisions.md}, WP-1 item 3):
 * holding the Mandible Pickaxe in the main hand counts as gated-open regardless of armor,
 * on top of the pre-existing full-chitin-set bypass in {@link ChitinArmor} and
 * {@code loot.WearingFullChitinCondition}. No GameTest previously covered the gate at all
 * (grepped this package for {@code wearing_full_chitin} / {@code COLONY_FABRIC} /
 * {@code BreakSpeed} before adding these -- zero hits), so this file is the first
 * coverage for both the pre-existing full-set bypass and the new pickaxe bypass, added in
 * the same pass.
 *
 * <p>The break-speed tests call {@link ChitinArmor#onFabricBreakSpeed} directly against a
 * hand-built {@code PlayerEvent.BreakSpeed} rather than posting it through the event bus
 * -- the same call-the-handler-directly shape {@code ColonyAngerGameTests} uses for
 * {@code BlockEvent.BreakEvent} -- which isolates the assertion to the handler under
 * test. The drop tests instead call {@code Block#playerDestroy} directly: verified
 * against the decompiled {@code ServerPlayerGameMode#destroyBlock} in {@code reference/},
 * that is the exact call the real per-player break path makes ({@code
 * block.playerDestroy(level, player, pos, state, blockEntity, heldItemCopy)}), and it is
 * the only way to populate the loot context's TOOL param with a specific item --
 * {@code level.destroyBlock(pos, true)} (what {@code LootXpGameTests} uses for its own
 * block-break tests) always passes {@code ItemStack.EMPTY} as the tool (verified in the
 * decompiled {@code Level#destroyBlock}), which would make a tool-conditioned assertion
 * here pass for the wrong reason.
 */
@GameTestHolder(Formicary.MODID)
public class ChitinGateGameTests {
    private static final BlockPos STAND_POS = new BlockPos(2, 2, 2);

    // ------------------------------------------------------------- break speed --

    /** Control: neither the full set nor the pickaxe -- the gate still applies. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void no_armor_no_pickaxe_break_speed_stays_gated(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState fabric = ModBlocks.PACKED_SOIL.get().defaultBlockState();

        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(player, fabric, 1.0F, null);
        ChitinArmor.onFabricBreakSpeed(event);

        helper.assertValueEqual(event.getNewSpeed(), 1.0F / ChitinArmor.UNGATED_SPEED_DIVISOR,
                "break speed with no set and no pickaxe");
        helper.succeed();
    }

    /** Ep2 play-test revision: the Mandible Pickaxe alone bypasses the slowdown. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void mandible_pickaxe_bypasses_break_speed_without_armor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MANDIBLE_PICKAXE.get()));
        BlockState fabric = ModBlocks.PACKED_SOIL.get().defaultBlockState();

        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(player, fabric, 1.0F, null);
        ChitinArmor.onFabricBreakSpeed(event);

        helper.assertValueEqual(event.getNewSpeed(), 1.0F,
                "break speed should be untouched while holding the Mandible Pickaxe");
        helper.succeed();
    }

    // ------------------------------------------------------------------- drops --

    /** Control: neither the full set nor the pickaxe -- the block still drops nothing. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void no_armor_no_pickaxe_break_drops_nothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState fabric = ModBlocks.PACKED_SOIL.get().defaultBlockState();
        BlockPos absPos = helper.absolutePos(STAND_POS);
        helper.setBlock(STAND_POS, fabric.getBlock());
        helper.setBlock(STAND_POS, Blocks.AIR);

        fabric.getBlock().playerDestroy(helper.getLevel(), player, absPos, fabric, null, ItemStack.EMPTY);

        helper.succeedWhen(() -> helper.assertItemEntityNotPresent(ModItems.PACKED_SOIL.get()));
    }

    /**
     * The task brief's required case: no armor at all, Mandible Pickaxe in hand -> a
     * colony_fabric block still yields its full self-drop.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void mandible_pickaxe_without_armor_still_drops_full_fabric_block(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = new ItemStack(ModItems.MANDIBLE_PICKAXE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        BlockState fabric = ModBlocks.PACKED_SOIL.get().defaultBlockState();
        BlockPos absPos = helper.absolutePos(STAND_POS);
        helper.setBlock(STAND_POS, fabric.getBlock());
        helper.setBlock(STAND_POS, Blocks.AIR);

        fabric.getBlock().playerDestroy(helper.getLevel(), player, absPos, fabric, null, pickaxe);

        helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.PACKED_SOIL.get()));
    }
}
