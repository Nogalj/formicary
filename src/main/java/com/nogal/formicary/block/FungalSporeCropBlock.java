package com.nogal.formicary.block;

import com.mojang.serialization.MapCodec;
import com.nogal.formicary.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;

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
 *
 * <h2>Play-test round 5, item 5: this is a cave fungus -- it needs no light, anywhere</h2>
 *
 * <p>{@link #canSurvive} and {@link #randomTick} both drop the light gate {@link CropBlock}
 * bakes in, so placement, survival and growth all work at light 0. This applies in the
 * overworld too, not just the colony -- one rule, and thematically the right one for a
 * fungus. Both had to be reimplemented rather than overridden, each verified against the
 * decompiled 1.21 sources:
 * <ul>
 *   <li>{@link #canSurvive} -- the light requirement is not in {@code BushBlock}'s own
 *       {@code canSurvive} (soil-{@code TriState} short-circuit, then {@code
 *       this.mayPlaceOn}); {@code CropBlock.canSurvive} adds {@code hasSufficientLight(...)
 *       &amp;&amp;} on top of a {@code super.canSurvive(...)} call to that {@code
 *       BushBlock} version. Since this class's superclass is {@code CropBlock}, {@code
 *       super.canSurvive(...)} can only reach {@code CropBlock}'s own light-gated copy --
 *       Java has no way to call an ancestor two levels up -- so dropping the gate means
 *       reimplementing {@code BushBlock.canSurvive}'s body here directly (still routed
 *       through {@link #mayPlaceOn}, so the widened dirt-or-farmland placement above is
 *       untouched).</li>
 *   <li>{@link #randomTick} -- same shape of problem: {@code CropBlock.randomTick} gates
 *       the whole growth attempt behind {@code getRawBrightness(pos, 0) >= 9} before ever
 *       reaching {@code getGrowthSpeed}. This reimplements that method's body verbatim
 *       (loaded-area check, age/max-age, the {@code static} {@code getGrowthSpeed} --
 *       unaffected either way, since it is a soil-fertility bonus with no light term of
 *       its own -- and the same {@code CommonHooks} grow-event pair) minus the brightness
 *       check, with the existing 50% coin flip (see above, "roughly half wheat's") kept
 *       exactly as it was.</li>
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

    /**
     * {@code BushBlock.canSurvive}'s own body (soil {@code TriState} short-circuit, then
     * {@link #mayPlaceOn}), reimplemented here rather than reached via {@code super} --
     * see the class javadoc's "cave fungus" section for why {@code super.canSurvive(...)}
     * cannot be used to drop {@code CropBlock}'s added light gate.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        TriState soilDecision = belowState.canSustainPlant(level, below, Direction.UP, state);
        if (!soilDecision.isDefault()) {
            return soilDecision.isTrue();
        }
        return this.mayPlaceOn(belowState, level, below);
    }

    /**
     * {@code CropBlock.randomTick}'s own body, reimplemented with the {@code
     * getRawBrightness(pos, 0) >= 9} gate removed -- see the class javadoc's "cave fungus"
     * section. The 50% coin flip (roughly halving wheat's growth rate, per the class
     * javadoc above) still gates the whole attempt exactly as it did before.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) != 0) {
            return;
        }
        if (!level.isAreaLoaded(pos, 1)) {
            return;
        }
        int age = this.getAge(state);
        if (age >= this.getMaxAge()) {
            return;
        }
        float growthSpeed = getGrowthSpeed(state, level, pos);
        if (CommonHooks.canCropGrow(level, pos, state, random.nextInt((int) (25.0F / growthSpeed) + 1) == 0)) {
            level.setBlock(pos, this.getStateForAge(age + 1), 2);
            CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }

    /** Adds THIS class's {@link #AGE} (0-4), not {@code CropBlock}'s own 0-7 field -- see the class javadoc. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
