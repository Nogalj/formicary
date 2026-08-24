package com.nogal.formicary.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fungal Gardens' glowing plant. A {@link BushBlock} that will sit on top of
 * <b>any</b> block -- see {@link #mayPlaceOn}.
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

    /**
     * Anything with a block under it. Play-test round 9: the bloom used to accept only the
     * three tier soils, Anthill Soil and vanilla dirt, which made it useless as a build
     * material -- it is the mod's only light-emitting plant, and a decorator wants it on
     * stone, resin, brick and comb, not just on the dirt it grows out of in the wild.
     *
     * <p>The two exclusions are not restrictions on the ask, they are what "on a block"
     * already means: air leaves the plant floating with nothing under it, and a fluid
     * would have it standing on the surface of water. Everything solid is fair game.
     *
     * <p>Note this is only consulted when NeoForge's own {@code canSustainPlant} hook
     * returns a default {@code TriState} -- see {@code BushBlock.canSurvive}, which asks
     * the soil first and only falls through to here when it has no opinion. Nothing in
     * this mod overrides that hook, so in practice this decides every placement.
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
