package com.nogal.formicary.block;

import com.mojang.serialization.MapCodec;

import com.nogal.formicary.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * M8: A slow-growing crop planted from a {@link FungalSporeItem}. Unlike vanilla wheat it
 * tolerates dim light (minimum 2 instead of 8) -- appropriate for a subterranean mushroom --
 * and can root on both Farmland and plain Dirt.
 *
 * <p>Uses a 4-stage age property (AGE_3: 0..3) so it grows noticeably slower than wheat's
 * 8 stages; the growth probability also scales with ambient light, giving players a reason to
 * place a few Fungal Bloom torches nearby.
 *
 * <p>Verified against the decompiled 1.21 CropBlock: {@link #getAgeProperty()} returns the
 * custom property, {@link #getMaxAge()} returns 3, and {@link #getStateForAge(int)} uses
 * {@link #defaultBlockState()} with the custom property.
 */
public class FungalSporeBlock extends CropBlock {
    public static final MapCodec<FungalSporeBlock> CODEC = simpleCodec(FungalSporeBlock::new);

    /** 4-stage growth: ages 0, 1, 2, 3.  Mature at age 3. */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    /** Minimum light for the crop to survive and the light-scale base. */
    private static final int MIN_LIGHT = 2;
    /** Maximum light the growth chance scales up to. */
    private static final int MAX_LIGHT = 8;

    public FungalSporeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    public MapCodec<? extends FungalSporeBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return 3;
    }

    @Override
    public BlockState getStateForAge(int age) {
        return this.defaultBlockState().setValue(AGE, age);
    }

    /**
     * Allow planting on Farmland (vanilla) and plain Dirt (the colony's soil equivalent).
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock
                || state.is(Blocks.DIRT)
                || state.is(ModBlocks.AMBER_EARTH.get())
                || state.is(ModBlocks.DEEP_LOAM.get())
                || state.is(ModBlocks.PACKED_SOIL.get());
    }

    /**
     * Lower the light floor to MIN_LIGHT -- fungal spores thrive in dim conditions.
     * The crop can survive at MIN_LIGHT even though it only grows quickly near MAX_LIGHT.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        net.neoforged.neoforge.common.util.TriState soilDecision =
                level.getBlockState(pos.below()).canSustainPlant(level, pos.below(),
                        net.minecraft.core.Direction.UP, state);
        if (!soilDecision.isDefault()) return soilDecision.isTrue();
        // Use a reduced light floor so the crop survives underground.
        return level.getRawBrightness(pos, 0) >= MIN_LIGHT && super.canSurvive(state, level, pos);
    }

    /**
     * Growth tick: scales probability with ambient light (more light = faster growth),
     * but remains slower than vanilla wheat overall (25/f divisor, where f includes the
     * light bonus rather than vanilla's farmland adjacency only).
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) return;
        int brightness = level.getRawBrightness(pos, 0);
        if (brightness < MIN_LIGHT) return;
        int age = this.getAge(state);
        if (age < this.getMaxAge()) {
            // Light bonus: scale from 0 at MIN_LIGHT to 1.0 at MAX_LIGHT and above.
            float lightBonus = Math.min(1.0F, (float)(brightness - MIN_LIGHT) / (MAX_LIGHT - MIN_LIGHT));
            // Growth speed: base 1.0 + light bonus (max 2.0), kept in the slow range.
            float growthSpeed = 1.0F + lightBonus;
            if (net.neoforged.neoforge.common.CommonHooks.canCropGrow(level, pos, state,
                    random.nextInt((int)(25.0F / growthSpeed) + 1) == 0)) {
                level.setBlock(pos, this.getStateForAge(age + 1), 2);
                net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level, pos, state);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    /** The seed item is the FungalSporeItem itself. */
    @Override
    protected net.minecraft.world.level.ItemLike getBaseSeedId() {
        return ModItems.FUNGAL_SPORE.get();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.FUNGAL_SPORE.get());
    }
}
