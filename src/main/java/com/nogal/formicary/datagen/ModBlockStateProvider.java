package com.nogal.formicary.datagen;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.FungalSporeCropBlock;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
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
        // Combs get a hand-built cube whose top/bottom faces rotate the UV 90
        // degrees: cube_all orients caps differently from sides, so the comb's
        // offset rows ran a different way on floors than on walls (Logan's
        // 2026-08-13 review).
        combBlock(ModBlocks.BROOD_COMB.get(), "brood_comb");
        combBlock(ModBlocks.ROYAL_COMB.get(), "royal_comb");
        combBlock(ModBlocks.PROVISION_COMB.get(), "provision_comb");
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

    /**
     * Full cube whose UP/DOWN faces rotate their UVs 90 degrees so the comb's offset
     * cell rows flow the same way on floors/ceilings as on walls. cube_all cannot do
     * this -- caps and sides get different UV orientations by construction.
     */
    private void combBlock(Block block, String name) {
        var model = models().getBuilder(name)
                .texture("all", modLoc("block/" + name))
                .texture("particle", modLoc("block/" + name))
                .element()
                .from(0, 0, 0)
                .to(16, 16, 16)
                .allFaces((dir, face) -> {
                    face.texture("#all").cullface(dir);
                    if (dir == Direction.UP || dir == Direction.DOWN) {
                        face.rotation(ModelBuilder.FaceRotation.CLOCKWISE_90);
                    }
                })
                .end();
        simpleBlock(block, model);
    }
}
