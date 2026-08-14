package com.nogal.formicary.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.nogal.formicary.block.ModBlockTags;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The crop rules a tamed worker's work mode is built on (spec section 4). Pure functions
 * of a {@link BlockState} and a drop list, so a GameTest can exercise every one of them
 * without an entity in the loop.
 *
 * <p>The split is deliberate: <em>candidacy</em> is data ({@link ModBlockTags#HARVESTABLE_CROPS}),
 * <em>ripeness</em> is code. That is what lets a modded crop opt in by joining the tag with
 * no Java change -- see {@link #isMature} for exactly what it has to look like.
 */
public final class CropHarvest {
    /**
     * Whether this block is one the worker may take right now: in the tag <em>and</em>
     * fully grown.
     */
    public static boolean isHarvestable(BlockState state) {
        return state.is(ModBlockTags.HARVESTABLE_CROPS) && isMature(state);
    }

    /**
     * Ripeness, worked out generically rather than per block type.
     *
     * <p>Vanilla's {@link CropBlock} (wheat, carrots, potatoes, beetroot) answers through
     * its own {@code isMaxAge}, which respects subclasses that shorten the age range --
     * {@code BeetrootBlock} maxes out at 3, not 7, and hardcoding {@code AGE_7} would leave
     * beetroot never harvestable. Everything else is read off an integer property literally
     * named {@code age}, which is how {@code NetherWartBlock} (AGE_3) qualifies with no
     * special case of its own, and how a modded crop qualifies too.
     */
    public static boolean isMature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        IntegerProperty age = ageProperty(state);
        if (age == null) {
            return false;
        }
        int max = Integer.MIN_VALUE;
        for (int value : age.getPossibleValues()) {
            max = Math.max(max, value);
        }
        return state.getValue(age) >= max;
    }

    /** The block state a harvested crop is replanted as: its own default, i.e. age 0. */
    public static BlockState replantState(BlockState harvested) {
        return harvested.getBlock().defaultBlockState();
    }

    /**
     * Pulls a single seed for {@code crop} out of {@code drops}, shrinking the stack it
     * came from, and returns it -- or {@link ItemStack#EMPTY} when the harvest yielded no
     * seed (a wheat break can roll zero seeds, and then the tile is simply left bare).
     *
     * <p>"The seed" is identified as a {@link BlockItem} that places this very block:
     * {@code wheat_seeds}, {@code carrot}, {@code potato}, {@code beetroot_seeds} and
     * {@code nether_wart} are all {@code ItemNameBlockItem}s pointing at their own crop, so
     * one rule covers the vanilla five and any modded crop that drops itself the same way.
     * Reading the drops rather than asking the block for a clone stack also guarantees the
     * replant is <em>paid for</em> out of the harvest instead of conjured.
     */
    public static ItemStack takeSeed(List<ItemStack> drops, Block crop) {
        for (ItemStack stack : drops) {
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == crop) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static IntegerProperty ageProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && "age".equals(integer.getName())) {
                return integer;
            }
        }
        return null;
    }

    private CropHarvest() {
    }
}
