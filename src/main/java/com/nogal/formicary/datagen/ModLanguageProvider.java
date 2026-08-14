package com.nogal.formicary.datagen;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.effect.ModMobEffects;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.item.ModItems;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * en_us display names for the M1 block set, the resin item, the M2 worker ant and its
 * spawn egg, and the Formicary creative tab. M4b adds the Scent Gland item, the
 * Pheromonal Disguise effect, and its potion's container-variant names.
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
        addBlock(ModBlocks.FUNGAL_SPORE, "Fungal Spore");

        addItem(ModItems.RESIN, "Resin");
        addItem(ModItems.CHITIN, "Chitin");
        addItem(ModItems.LARVA, "Larva");
        addItem(ModItems.ROYAL_JELLY, "Royal Jelly");
        addItem(ModItems.SCENT_GLAND, "Scent Gland");
        addItem(ModItems.TRAIL_PHEROMONE, "Trail Pheromone");
        // Shown above the hotbar when a pheromone is used with nothing recorded to retrace.
        add("item.formicary.trail_pheromone.no_trail", "No trail to follow yet.");

        addItem(ModItems.ROYAL_PHEROMONE_GLAND, "Royal Pheromone Gland");
        addItem(ModItems.PHEROMONE_HORN, "Pheromone Horn");

        addItem(ModItems.CHITIN_HELMET, "Chitin Helmet");
        addItem(ModItems.CHITIN_CHESTPLATE, "Chitin Chestplate");
        addItem(ModItems.CHITIN_LEGGINGS, "Chitin Leggings");
        addItem(ModItems.FUNGAL_SPORE, "Fungal Spore");
        addItem(ModItems.CHITIN_BOOTS, "Chitin Boots");

        addEntityType(ModEntities.WORKER_ANT, "Worker Ant");
        addItem(ModEntities.WORKER_ANT_SPAWN_EGG, "Worker Ant Spawn Egg");
        addEntityType(ModEntities.SOLDIER_ANT, "Soldier Ant");
        addItem(ModEntities.SOLDIER_ANT_SPAWN_EGG, "Soldier Ant Spawn Egg");
        addEntityType(ModEntities.LARVA, "Larva");
        addItem(ModEntities.LARVA_SPAWN_EGG, "Larva Spawn Egg");

        // M6: no spawn eggs for these two -- an egg-spawned tamed ant would have no owner.
        addEntityType(ModEntities.TAMED_WORKER_ANT, "Tamed Worker Ant");
        addEntityType(ModEntities.TAMED_SOLDIER_ANT, "Tamed Soldier Ant");

        // M7: no spawn egg either -- the only queen in a world is the one her chamber
        // generated. This name is also what the boss bar reads (ServerBossEvent takes
        // getDisplayName()).
        addEntityType(ModEntities.QUEEN_ANT, "The Queen");

        add("itemGroup.formicary.formicary", "Formicary");

        addEffect(ModMobEffects.PHEROMONAL_DISGUISE, "Pheromonal Disguise");

        // Potion.getName() builds the key as <item descriptionId> + ".effect." + <path>
        // (verified against PotionItem.getName and the decompiled Potion.java) -- these
        // four cover the potion itself plus the splash/lingering/tipped-arrow variants
        // that vanilla's generic container-brewing mixes produce for free.
        add("item.minecraft.potion.effect.pheromonal_disguise", "Potion of Pheromonal Disguise");
        add("item.minecraft.splash_potion.effect.pheromonal_disguise", "Splash Potion of Pheromonal Disguise");
        add("item.minecraft.lingering_potion.effect.pheromonal_disguise", "Lingering Potion of Pheromonal Disguise");
        add("item.minecraft.tipped_arrow.effect.pheromonal_disguise", "Arrow of Pheromonal Disguise");
        add("advancements.formicary.root.title", "Formicary");
        add("advancements.formicary.root.description", "Discover the colony beneath the savanna.");
        add("advancements.formicary.enter_dimension.title", "Down the Tunnel");
        add("advancements.formicary.enter_dimension.description", "Enter the Formicary dimension for the first time.");
        add("advancements.formicary.brew_disguise.title", "Smells Like One of Them");
        add("advancements.formicary.brew_disguise.description", "Brew a Potion of Pheromonal Disguise.");
        add("advancements.formicary.capture_larva.title", "Found the Brood");
        add("advancements.formicary.capture_larva.description", "Pick up a larva from the Nurseries.");
        add("advancements.formicary.defeat_queen.title", "Regicide");
        add("advancements.formicary.defeat_queen.description", "Defeat the Queen Ant.");
        add("advancements.formicary.first_harvest.title", "A Good Worker");
        add("advancements.formicary.first_harvest.description", "Your tamed worker ant deposits its first harvest.");
        add("advancements.formicary.raise_both_castes.title", "Colony of Your Own");
        add("advancements.formicary.raise_both_castes.description", "Raise both a worker ant and a soldier ant.");
    }
}
