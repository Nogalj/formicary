package com.nogal.formicary.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.nogal.formicary.block.ModBlockTags;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
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
 *
 * <p>Play-test round 4 (item 6) pushed on both halves of that split at once and neither
 * needed restructuring, which is the payoff. On the data side the tag now also pulls in the
 * NeoForge convention tag {@code c:crops}, so a modded crop that already tags itself
 * conventionally is farmed with no opt-in at all. On the code side there are exactly two new
 * rules, both about the <em>shape</em> of a block rather than about any particular block:
 * something with no age property is ripe by existence ({@link #isMature}) and is harvested
 * without a replant ({@link #isReplantable}), which is what pumpkin and melon need; and a
 * stem is never harvestable whatever a tag says ({@link #isStem}), which is what opening
 * candidacy to a tag this mod does not own needs.
 */
public final class CropHarvest {
    /**
     * Whether this block is one the worker may take right now: not a stem, in the tag
     * <em>and</em> ripe.
     *
     * <p>The stem guard runs <b>first and unconditionally</b>, which is the one place this
     * class does not let data have the last word. Play-test round 4 (item 6) opened candidacy
     * up to the NeoForge convention tag {@code c:crops} so that modded crops join with no
     * Java change, and the contents of that tag are by definition outside this mod's control:
     * a mod (or a pack) that tags {@code pumpkin_stem} conventionally would otherwise have
     * every worker in the world mowing down the stems that grow the fruit. See
     * {@link #isStem}.
     */
    public static boolean isHarvestable(BlockState state) {
        return !isStem(state) && state.is(ModBlockTags.HARVESTABLE_CROPS) && isMature(state);
    }

    /**
     * Whether {@code state} is the vine half of a pumpkin/melon patch rather than the fruit:
     * {@link StemBlock} while it is still growing, {@link AttachedStemBlock} once it has a
     * fruit on it.
     *
     * <p>Never harvestable, at any age, whatever tag it carries. Cutting a stem is the one
     * move that makes a patch <em>stop</em> producing, so it is the exact opposite of what
     * the harvest-and-replant contract promises everywhere else -- and note that an attached
     * stem carries no {@code age} property at all, so without this guard the ageless rule in
     * {@link #isMature} would read it as permanently ripe.
     *
     * <p>Public because it is the guard a GameTest asserts on directly: the tag check in
     * {@link #isHarvestable} short-circuits for a stem that nothing has tagged, so asserting
     * only {@code isHarvestable(stem) == false} would pass with the guard deleted.
     */
    public static boolean isStem(BlockState state) {
        return state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock;
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
     *
     * <p><b>A tagged block with no {@code age} property at all is ripe by existence</b>
     * (play-test round 4, item 6). Pumpkin and melon do not grow in place -- the stem grows
     * them, fully formed, in one tick -- so there is no ripeness to read: the fruit either is
     * there or is not. Before this rule they were simply never mature and a worker walked
     * past them forever. It is deliberately a rule about the <em>shape</em> of the block
     * rather than a list of two blocks, so a modded fruit that works the same way needs no
     * code either; {@link #isHarvestable}'s tag gate is what keeps it from meaning "every
     * ageless block in the world".
     */
    public static boolean isMature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        IntegerProperty age = ageProperty(state);
        if (age == null) {
            return true;
        }
        int max = Integer.MIN_VALUE;
        for (int value : age.getPossibleValues()) {
            max = Math.max(max, value);
        }
        return state.getValue(age) >= max;
    }

    /**
     * Whether harvesting {@code state} should put a fresh plant back and pay for it out of
     * the drops -- true for anything that grows through an age sequence, false for the
     * ageless fruit {@link #isMature} now admits.
     *
     * <p>Play-test round 4, item 6. Both halves of the replant would be wrong for a pumpkin.
     * Putting the block back would give a worker an infinite pumpkin farm out of one fruit,
     * because the <em>stem</em> is what regrows it and the stem is still standing; and
     * {@code takeSeed} would pull the pumpkin itself out of the drops as its own "seed",
     * so the worker would bank nothing at all. Break-and-bank is the whole harvest for
     * these: cut the fruit, carry it home, let the vine do its job.
     *
     * <p>One predicate rather than {@code instanceof} checks scattered through
     * {@link TamedWorkerAntEntity#harvest} -- the age-crop path has to stay behaviourally
     * identical to what it was, and a single branch is much easier to keep that way.
     */
    public static boolean isReplantable(BlockState state) {
        return state.getBlock() instanceof CropBlock || ageProperty(state) != null;
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
