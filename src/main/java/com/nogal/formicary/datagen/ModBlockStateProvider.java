package com.nogal.formicary.datagen;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Block states + block models for all 16 M1 blocks. Plain full blocks get the default
 * cube-all model; Amber Glass/Fungal Bloom/Fungal Carpet get their vanilla model
 * templates (block/cube_all + translucent, block/cross + cutout, block/carpet) --
 * verified against decompiled {@code ModelProvider.java} (cross/carpet helpers) and
 * {@code ModelBuilder.java} ({@code renderType(String)} parses the string as a
 * {@link net.minecraft.resources.ResourceLocation}, so "cutout"/"translucent" resolve
 * to the vanilla {@code minecraft:cutout} / {@code minecraft:translucent} render types).
 */
public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, Formicary.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // Plain full cube-all blocks.
        simpleBlock(ModBlocks.PACKED_SOIL.get());
        simpleBlock(ModBlocks.AMBER_EARTH.get());
        simpleBlock(ModBlocks.DEEP_LOAM.get());
        simpleBlock(ModBlocks.HARDENED_SOIL.get());
        simpleBlock(ModBlocks.RESIN_WEEP.get());
        simpleBlock(ModBlocks.RESIN_BLOCK.get());
        simpleBlock(ModBlocks.BROOD_COMB.get());
        simpleBlock(ModBlocks.ROYAL_COMB.get());
        simpleBlock(ModBlocks.EGG_CLUSTER.get());
        simpleBlock(ModBlocks.DAYLIGHT_MEMBRANE.get());
        simpleBlock(ModBlocks.ANTHILL_SOIL.get());
        simpleBlock(ModBlocks.ANTHILL_CORE.get());
        simpleBlock(ModBlocks.QUEENS_CREST.get());

        // Translucent glass.
        simpleBlock(
                ModBlocks.AMBER_GLASS.get(),
                models().cubeAll("amber_glass", blockTexture(ModBlocks.AMBER_GLASS.get())).renderType("translucent"));

        // Cross-model glowing plant, cutout render type.
        simpleBlock(
                ModBlocks.FUNGAL_BLOOM.get(),
                models().cross("fungal_bloom", blockTexture(ModBlocks.FUNGAL_BLOOM.get())).renderType("cutout"));

        // Carpet model.
        simpleBlock(
                ModBlocks.FUNGAL_CARPET.get(),
                models().carpet("fungal_carpet", blockTexture(ModBlocks.FUNGAL_CARPET.get())));
    }
}
