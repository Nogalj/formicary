package com.nogal.formicary.datagen;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.FungalSporeCropBlock;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
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
        // Plain cube-all, no rotation: Ep2 task I2 re-diagnosis (2026-08-18) found
        // the 2026-08-13 fix's per-face CLOCKWISE_90 on UP/DOWN was itself the bug,
        // not the cure. Verified against BlockElement.uvsByFace in reference/: a
        // full cube's default UV already gives north/south walls U=world X, V=
        // world Y (flipped) -- identical axis roles to the floor's U=X, V=Z -- so
        // those walls needed ZERO rotation, and CLOCKWISE_90 broke the one pairing
        // that already matched. East/west walls map U to world Z instead, which is
        // a true transpose relative to the floor's V=Z, and ModelBuilder.FaceRotation
        // only has the four pure rotations (verified in ModelBuilder.java) -- none
        // of which can express a transpose, so no single rotation value reconciles
        // both wall pairings at once. The actual fix lives in the texture: comb()
        // in assets-src/blocks.py now shades its hollow cells with the same
        // x/y-symmetric L the capped cells already used, so the pattern is
        // transpose-invariant and neither pairing needs any rotation at all.
        simpleBlock(ModBlocks.BROOD_COMB.get());
        simpleBlock(ModBlocks.ROYAL_COMB.get());
        simpleBlock(ModBlocks.PROVISION_COMB.get());
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

        fungalSporeCropBlock();

        // Ep2 task H3: decorative families. Each base block is a plain cube-all (its
        // texture already carries the "cut" look -- mortar grid, tile grid, gloss line
        // -- so no hand-built model is needed, same as the combs above since Ep2 task
        // I2), and every stairs/slab/wall reuses that same single texture via the
        // stairsBlock/slabBlock/wallBlock overloads -- the "dedicated provider helpers"
        // the task brief points at.
        simpleBlock(ModBlocks.PACKED_SOIL_BRICKS.get());
        stairsBlock(ModBlocks.PACKED_SOIL_BRICK_STAIRS.get(), blockTexture(ModBlocks.PACKED_SOIL_BRICKS.get()));
        slabBlock(ModBlocks.PACKED_SOIL_BRICK_SLAB.get(), blockTexture(ModBlocks.PACKED_SOIL_BRICKS.get()),
                blockTexture(ModBlocks.PACKED_SOIL_BRICKS.get()));
        wallBlock(ModBlocks.PACKED_SOIL_BRICK_WALL.get(), blockTexture(ModBlocks.PACKED_SOIL_BRICKS.get()));

        simpleBlock(ModBlocks.HARDENED_SOIL_TILES.get());
        stairsBlock(ModBlocks.HARDENED_SOIL_TILE_STAIRS.get(), blockTexture(ModBlocks.HARDENED_SOIL_TILES.get()));
        slabBlock(ModBlocks.HARDENED_SOIL_TILE_SLAB.get(), blockTexture(ModBlocks.HARDENED_SOIL_TILES.get()),
                blockTexture(ModBlocks.HARDENED_SOIL_TILES.get()));

        simpleBlock(ModBlocks.POLISHED_RESIN.get());
        stairsBlock(ModBlocks.POLISHED_RESIN_STAIRS.get(), blockTexture(ModBlocks.POLISHED_RESIN.get()));
        slabBlock(ModBlocks.POLISHED_RESIN_SLAB.get(), blockTexture(ModBlocks.POLISHED_RESIN.get()),
                blockTexture(ModBlocks.POLISHED_RESIN.get()));
    }

    /**
     * M8: five age states (0-4) sharing three cross models -- the same fewer-models-
     * than-ages economy vanilla's {@code nether_wart} uses (4 ages, 3 models, ages 1-2
     * sharing the middle one). Age-to-model mapping verified by inspection of
     * {@code nether_wart.json}'s blockstate in the client-extra jar.
     */
    private void fungalSporeCropBlock() {
        Block crop = ModBlocks.FUNGAL_SPORE_CROP.get();
        BlockModelBuilder stage0 = models().crop("fungal_spore_crop_stage0",
                modLoc("block/fungal_spore_crop_stage0")).renderType("cutout");
        BlockModelBuilder stage1 = models().crop("fungal_spore_crop_stage1",
                modLoc("block/fungal_spore_crop_stage1")).renderType("cutout");
        BlockModelBuilder stage2 = models().crop("fungal_spore_crop_stage2",
                modLoc("block/fungal_spore_crop_stage2")).renderType("cutout");
        BlockModelBuilder[] modelByAge = {stage0, stage0, stage1, stage1, stage2};

        getVariantBuilder(crop).forAllStates(state -> new ConfiguredModel[] {
                new ConfiguredModel(modelByAge[state.getValue(FungalSporeCropBlock.AGE)])
        });
    }
}
