package com.nogal.formicary.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fungal Gardens' glowing plant. A {@link BushBlock} restricted to placement on the
 * three tier soils, Anthill Soil, and vanilla dirt -- see {@link #mayPlaceOn}.
 *
 * <p>Signature verified against the decompiled 1.21 sources: {@code BushBlock.mayPlaceOn}
 * is {@code protected boolean mayPlaceOn(BlockState, BlockGetter, BlockPos)}, and
 * {@code BushBlock.codec()} is abstract, so this subclass must supply its own
 * {@link MapCodec} via {@link Block#simpleCodec}.
 */
public class FungalBloomBlock extends BushBlock {
    public static final MapCodec<FungalBloomBlock> CODEC = simpleCodec(FungalBloomBlock::new);

    public FungalBloomBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FungalBloomBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModBlocks.PACKED_SOIL.get())
                || state.is(ModBlocks.AMBER_EARTH.get())
                || state.is(ModBlocks.DEEP_LOAM.get())
                || state.is(ModBlocks.ANTHILL_SOIL.get())
                || state.is(Blocks.DIRT);
    }
}
