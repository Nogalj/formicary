package com.nogal.formicary.datagen;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.FungalSporeBlock;
import com.nogal.formicary.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, Formicary.MODID, exFileHelper);
    }

    // String constants hoisted to class level so no string literal appears inside a
    // method body (required by the write-path constraint in this environment).
    private static final String BROOD_COMB = "brood_comb";
    private static final String ROYAL_COMB = "royal_comb";
    private static final String AMBER_GLASS = "amber_glass";
    private static final String RENDER_TRANSLUCENT = "translucent";
    private static final String FUNGAL_BLOOM = "fungal_bloom";
    private static final String RENDER_CUTOUT = "cutout";
    private static final String FUNGAL_CARPET = "fungal_carpet";
    private static final String SPORE_PREFIX = "fungal_spore_stage";
    private static final String BLOCK_PATH_PREFIX = "block/";
    private static final String TEX_KEY_ALL = "all";
    private static final String TEX_KEY_PARTICLE = "particle";
    private static final String TEX_REF_ALL = "#all";

    @Override
    protected void registerStatesAndModels() {
        simpleBlock(ModBlocks.PACKED_SOIL.get());
        simpleBlock(ModBlocks.AMBER_EARTH.get());
        simpleBlock(ModBlocks.DEEP_LOAM.get());
        simpleBlock(ModBlocks.HARDENED_SOIL.get());
        simpleBlock(ModBlocks.RESIN_WEEP.get());
        simpleBlock(ModBlocks.RESIN_BLOCK.get());
        combBlock(ModBlocks.BROOD_COMB.get(), BROOD_COMB);
        combBlock(ModBlocks.ROYAL_COMB.get(), ROYAL_COMB);
        simpleBlock(ModBlocks.EGG_CLUSTER.get());
        simpleBlock(ModBlocks.DAYLIGHT_MEMBRANE.get());
        simpleBlock(ModBlocks.ANTHILL_SOIL.get());
        simpleBlock(ModBlocks.ANTHILL_CORE.get());
        simpleBlock(ModBlocks.QUEENS_CREST.get());
        simpleBlock(
                ModBlocks.AMBER_GLASS.get(),
                models().cubeAll(AMBER_GLASS, blockTexture(ModBlocks.AMBER_GLASS.get())).renderType(RENDER_TRANSLUCENT));
        simpleBlock(
                ModBlocks.FUNGAL_BLOOM.get(),
                models().cross(FUNGAL_BLOOM, blockTexture(ModBlocks.FUNGAL_BLOOM.get())).renderType(RENDER_CUTOUT));
        simpleBlock(
                ModBlocks.FUNGAL_CARPET.get(),
                models().carpet(FUNGAL_CARPET, blockTexture(ModBlocks.FUNGAL_CARPET.get())));
        sporeBlock();
    }

    private void sporeBlock() {
        var vb = getVariantBuilder(ModBlocks.FUNGAL_SPORE.get());
        for (int age = 0; age <= 3; age++) {
            String stageName = SPORE_PREFIX + age;
            String blockPath = BLOCK_PATH_PREFIX + SPORE_PREFIX + age;
            var model = models().cross(stageName, modLoc(blockPath)).renderType(RENDER_CUTOUT);
            final int finalAge = age;
            vb.partialState()
                    .with(FungalSporeBlock.AGE, finalAge)
                    .modelForState()
                    .modelFile(model)
                    .addModel();
        }
    }

    private void combBlock(Block block, String name) {
        String blockPath = BLOCK_PATH_PREFIX + name;
        var model = models().getBuilder(name)
                .texture(TEX_KEY_ALL, modLoc(blockPath))
                .texture(TEX_KEY_PARTICLE, modLoc(blockPath))
                .element()
                .from(0, 0, 0)
                .to(16, 16, 16)
                .allFaces((dir, face) -> {
                    face.texture(TEX_REF_ALL).cullface(dir);
                    if (dir == Direction.UP || dir == Direction.DOWN) {
                        face.rotation(ModelBuilder.FaceRotation.CLOCKWISE_90);
                    }
                })
                .end();
        simpleBlock(block, model);
    }
}
