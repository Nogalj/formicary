package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.item.ModToolTiers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the Ep2 play-test revision (round 2, WP-R3 item 2): the Mandible
 * Pickaxe now digs shovel-family blocks too, not just pickaxe-family ones.
 *
 * <p>{@code ModItems#MANDIBLE_PICKAXE} moved from {@code PickaxeItem} (which is exactly
 * {@code DiggerItem} + {@code BlockTags.MINEABLE_WITH_PICKAXE}, verified in the decompiled
 * sources) to a plain {@code DiggerItem} registered over
 * {@code ModBlockTags.MINEABLE_WITH_MANDIBLE_PICKAXE}, the union of vanilla's pickaxe and
 * shovel mineable tags. {@code Item#getDestroySpeed}/{@code isCorrectToolForDrops} both read
 * the tier's rule straight off the {@code DataComponents.TOOL} component built by
 * {@code Tier#createToolProperties} (verified in {@code reference/}), so calling those two
 * {@code ItemStack} methods directly is a synchronous, deterministic way to prove the tag
 * swap actually widened what counts as "correct" -- no swing timing or block-hardness
 * arithmetic involved.
 *
 * <p><b>Sand, not gravel.</b> Gravel's own loot table has an unconditional (even at
 * Fortune 0) chance of dropping Flint instead of Gravel, which would make an item-entity
 * drop assertion flaky for a reason that has nothing to do with this feature. Sand's table
 * is a plain, unconditional self-drop, so it isolates the tag-composition change cleanly
 * while still exercising the real per-player break path.
 *
 * <p>The drop half uses {@code Block#playerDestroy} directly -- the same player-mining
 * idiom {@link ChitinGateGameTests} already established (capture the block's state, clear
 * it to air, then call {@code playerDestroy} with the captured state and the tool stack) --
 * rather than {@code GameTestHelper#destroyBlock}, which hardcodes {@code dropBlock=false}
 * and would skip the loot table entirely (banked in {@code docs/gotchas/gametest.md}).
 */
@GameTestHolder(Formicary.MODID)
public class MandiblePickaxeGameTests {
    private static final BlockPos STAND_POS = new BlockPos(2, 2, 2);

    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void unenchanted_mandible_pickaxe_gets_full_speed_and_drops_on_a_shovel_block(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = new ItemStack(ModItems.MANDIBLE_PICKAXE.get());
        BlockState sand = Blocks.SAND.defaultBlockState();

        helper.assertValueEqual(pickaxe.getDestroySpeed(sand), ModToolTiers.SPEED,
                "mandible pickaxe destroy speed on a shovel-family block (sand) should match "
                        + "the chitin tier's full speed, not the 1.0F default");
        helper.assertTrue(pickaxe.isCorrectToolForDrops(sand),
                "mandible pickaxe should now be the correct tool for drops on a "
                        + "shovel-family block (sand)");

        BlockPos absPos = helper.absolutePos(STAND_POS);
        helper.setBlock(STAND_POS, sand.getBlock());
        helper.setBlock(STAND_POS, Blocks.AIR);

        sand.getBlock().playerDestroy(helper.getLevel(), player, absPos, sand, null, pickaxe);

        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.SAND));
    }
}
