package com.nogal.formicary.block;

import com.mojang.serialization.MapCodec;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The Fungal Spore crop (spec section 5 / M8): spores plant in the overworld as a
 * slow-growing, self-lit crop. Mature (age {@link #MAX_AGE}) safely drops a Fungal Bloom
 * item plus spores -- see {@code ModBlockLootSubProvider#fungalSporeCropTable}. The mature
 * block state is tagged into {@code formicary:harvestable_crops}, and
 * {@code entity.CropHarvest} needs no code change to farm it: {@code isMature} already
 * understands any {@link CropBlock} via {@code isMaxAge}, and {@code takeSeed} identifies
 * the seed generically as a {@code BlockItem} that places this block ({@code
 * ModItems#FUNGAL_SPORES} is an {@code ItemNameBlockItem} pointing here).
 *
 * <p>Subclassing {@link CropBlock} gets ripeness, replant-at-default-state and bonemeal
 * for free, but three of its defaults had to be overridden, each verified against the
 * decompiled 1.21 sources:
 * <ul>
 *   <li>{@link #getAgeProperty()} / {@link #getMaxAge()} -- five stages ({@code AGE_4},
 *       0-4), not wheat's eight (spec: "~4-5 ages").</li>
 *   <li>{@link #mayPlaceOn} -- {@code CropBlock} narrows {@code BushBlock}'s default
 *       ({@code state.is(BlockTags.DIRT) || state instanceof FarmBlock}) down to farmland
 *       only. This override restores the wider rule so the crop plants on dirt (spec:
 *       "spores plant... on dirt"), keeping farmland too so it composes with a real farm.
 *       No change to {@code canSurvive} is needed: {@code CropBlock#canSurvive} delegates
 *       to {@code BushBlock#canSurvive} via {@code super}, which calls {@code
 *       this.mayPlaceOn} -- a virtual dispatch that already lands here.</li>
 *   <li>{@link #randomTick} -- {@code CropBlock.getGrowthSpeed} is {@code static}, so a
 *       subclass cannot override it virtually; {@code CropBlock}'s own {@code randomTick}
 *       always calls its own copy. {@code BeetrootBlock} is vanilla's proof of the actual
 *       technique for retuning a subclass's pace: gate the {@code super.randomTick} call
 *       behind an extra coin flip. A 50% gate here roughly halves wheat's growth rate
 *       (spec: "slow-growing... roughly half wheat's").</li>
 *   <li>{@link #createBlockStateDefinition} -- {@code CropBlock}'s own override adds its
 *       <em>own static</em> {@code AGE} field (the 0-7 one) to the state definition; a
 *       plain field reference inside an inherited method is not virtual, so leaving this
 *       unoverridden built a block whose real state definition carried wheat's 8-value
 *       property while every other method here asked for the 5-value one via {@code
 *       getAgeProperty()} -- caught by {@code runData} failing registration with "Cannot
 *       get property ... does not exist in Block". {@code BeetrootBlock} re-overrides this
 *       too, for the identical reason.</li>
 * </ul>
 */
public class FungalSporeCropBlock extends CropBlock {
    public static final MapCodec<FungalSporeCropBlock> CODEC = simpleCodec(FungalSporeCropBlock::new);

    /** Five stages (0-4), not wheat's eight -- spec: "~4-5 ages". */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    public static final int MAX_AGE = 4;

    /**
     * Self-lit, rising with age (spec section 5): a bare sprout is barely a candle, the
     * mature bloom glows like the wild {@code fungal_bloom} block does (light 10).
     */
    private static final int[] LIGHT_BY_AGE = {2, 4, 5, 7, 8};

    public FungalSporeCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Light level for {@code state}'s age -- wired into the block's Properties at registration. */
    public static int lightForAge(BlockState state) {
        return LIGHT_BY_AGE[state.getValue(AGE)];
    }

    @Override
    public MapCodec<? extends FungalSporeCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    /**
     * Only feeds {@code CropBlock#getCloneItemStack} (pick-block in creative) -- the
     * worker's actual harvest wiring never calls this. {@code CropHarvest.takeSeed}
     * identifies the seed generically off the loot drops themselves ("a {@code BlockItem}
     * that places this block"), which is what makes the harvester need no code change at
     * all for a modded crop.
     */
    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.FUNGAL_SPORES.get();
    }

    /** Re-widens placement back to dirt-tag-or-farmland -- see the class javadoc. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.getBlock() instanceof FarmBlock;
    }

    /** Halves the growth roll rather than trying to override the static getGrowthSpeed. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            super.randomTick(state, level, pos, random);
        }
    }

    /** Adds THIS class's {@link #AGE} (0-4), not {@code CropBlock}'s own 0-7 field -- see the class javadoc. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
