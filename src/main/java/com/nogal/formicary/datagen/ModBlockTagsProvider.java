package com.nogal.formicary.datagen;

import java.util.concurrent.CompletableFuture;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlockTags;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Writes {@code data/formicary/tags/block/*.json} for the tags declared in
 * {@link ModBlockTags}.
 *
 * <p>The hive membership list is authoritative per spec section 5 -- Brood Comb, Royal
 * Comb, Egg Cluster, Daylight Membrane, Anthill Core. Nothing else belongs: Resin Weep
 * and the fungus blocks are harvestable without provoking the colony.
 */
public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Formicary.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModBlockTags.HIVE).add(
                ModBlocks.BROOD_COMB.get(),
                ModBlocks.ROYAL_COMB.get(),
                ModBlocks.EGG_CLUSTER.get(),
                ModBlocks.DAYLIGHT_MEMBRANE.get(),
                ModBlocks.ANTHILL_CORE.get());

        tag(ModBlockTags.COLONY_FABRIC).add(
                ModBlocks.PACKED_SOIL.get(),
                ModBlocks.AMBER_EARTH.get(),
                ModBlocks.DEEP_LOAM.get(),
                ModBlocks.HARDENED_SOIL.get());
    }
}
