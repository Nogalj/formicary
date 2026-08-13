package com.nogal.formicary.datagen;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.item.ModItems;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * en_us display names for the M1 block set, the resin item, the M2 worker ant and its
 * spawn egg, and the Formicary creative tab.
 */
public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, Formicary.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        addBlock(ModBlocks.PACKED_SOIL, "Packed Soil");
        addBlock(ModBlocks.AMBER_EARTH, "Amber Earth");
        addBlock(ModBlocks.DEEP_LOAM, "Deep Loam");
        addBlock(ModBlocks.HARDENED_SOIL, "Hardened Soil");
        addBlock(ModBlocks.RESIN_WEEP, "Resin Weep");
        addBlock(ModBlocks.RESIN_BLOCK, "Resin Block");
        addBlock(ModBlocks.AMBER_GLASS, "Amber Glass");
        addBlock(ModBlocks.FUNGAL_BLOOM, "Fungal Bloom");
        addBlock(ModBlocks.FUNGAL_CARPET, "Fungal Carpet");
        addBlock(ModBlocks.BROOD_COMB, "Brood Comb");
        addBlock(ModBlocks.ROYAL_COMB, "Royal Comb");
        addBlock(ModBlocks.EGG_CLUSTER, "Egg Cluster");
        addBlock(ModBlocks.DAYLIGHT_MEMBRANE, "Daylight Membrane");
        addBlock(ModBlocks.ANTHILL_SOIL, "Anthill Soil");
        addBlock(ModBlocks.ANTHILL_CORE, "Anthill Core");
        addBlock(ModBlocks.QUEENS_CREST, "Queen's Crest");

        addItem(ModItems.RESIN, "Resin");
        addItem(ModItems.CHITIN, "Chitin");
        addItem(ModItems.LARVA, "Larva");

        addItem(ModItems.CHITIN_HELMET, "Chitin Helmet");
        addItem(ModItems.CHITIN_CHESTPLATE, "Chitin Chestplate");
        addItem(ModItems.CHITIN_LEGGINGS, "Chitin Leggings");
        addItem(ModItems.CHITIN_BOOTS, "Chitin Boots");

        addEntityType(ModEntities.WORKER_ANT, "Worker Ant");
        addItem(ModEntities.WORKER_ANT_SPAWN_EGG, "Worker Ant Spawn Egg");
        addEntityType(ModEntities.SOLDIER_ANT, "Soldier Ant");
        addItem(ModEntities.SOLDIER_ANT_SPAWN_EGG, "Soldier Ant Spawn Egg");
        addEntityType(ModEntities.LARVA, "Larva");
        addItem(ModEntities.LARVA_SPAWN_EGG, "Larva Spawn Egg");

        add("itemGroup.formicary.formicary", "Formicary");
    }
}
