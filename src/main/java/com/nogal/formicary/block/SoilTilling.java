package com.nogal.formicary.block;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Play-test round 5, item 5: a hoe tills the three soft tier soils -- Packed Soil, Amber
 * Earth, Deep Loam -- into vanilla {@code minecraft:farmland}. Hardened Soil is
 * deliberately excluded (it is the dimension's "stone", not its "dirt"), and so are the
 * player-crafted brick/tile/resin families (a build a player made, not colony fabric).
 *
 * <p>This is the pure decision seam behind {@link TillingEvents}' {@code
 * BlockEvent.BlockToolModificationEvent} handler -- kept as a static function, not inlined
 * into the handler, so a GameTest can drive it directly ({@code
 * PlantingGameTests}) instead of routing through {@code GameTestHelper.makeMockPlayer} and
 * a simulated {@code UseOnContext}, which the project's GameTest conventions (see
 * {@code AntClimbing#tamedClimbFlag} for the precedent) treat as a limited harness for
 * exactly this kind of pure per-block decision.
 *
 * <p>Vanilla's own live tilling path was read out of the decompiled 21.0.167 sources
 * before writing this, not assumed from the (deprecated, patched-out) {@code
 * HoeItem.TILLABLES} map: {@code IBlockExtension#getToolModifiedState}'s real {@code
 * HOE_TILL} branch checks only "is the block above air" -- it does NOT also require the
 * clicked face to be non-DOWN the way the dead {@code TILLABLES} entry's {@code
 * onlyIfAirAbove} predicate does. This mirrors that live behaviour: air-above only.
 */
public final class SoilTilling {

    /**
     * @param state          the block state a hoe was used on
     * @param spaceAboveIsAir whether the block directly above {@code state} is air --
     *                        vanilla's own tilling rule, mirrored here rather than
     *                        reinvented (see the class javadoc)
     * @return farmland's default state if {@code state} is one of the three tillable
     *         soils and the space above is air; {@code null} otherwise (no tool
     *         modification happens)
     */
    @Nullable
    public static BlockState tilledState(BlockState state, boolean spaceAboveIsAir) {
        if (!spaceAboveIsAir) {
            return null;
        }
        return isTillableSoil(state.getBlock()) ? Blocks.FARMLAND.defaultBlockState() : null;
    }

    /**
     * Exactly the three soft tier soils -- NOT {@link ModBlocks#HARDENED_SOIL} (the
     * dimension's stone-equivalent) and NOT the brick/tile/resin building-block families
     * made from harvested fabric.
     */
    public static boolean isTillableSoil(Block block) {
        return block == ModBlocks.PACKED_SOIL.get()
                || block == ModBlocks.AMBER_EARTH.get()
                || block == ModBlocks.DEEP_LOAM.get();
    }

    private SoilTilling() {
    }
}
