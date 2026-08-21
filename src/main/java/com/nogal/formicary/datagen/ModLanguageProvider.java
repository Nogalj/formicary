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
        addBlock(ModBlocks.RESIN_BLOCK, "Resin Block");
        addBlock(ModBlocks.AMBER_GLASS, "Amber Glass");
        addBlock(ModBlocks.FUNGAL_BLOOM, "Fungal Bloom");
        addBlock(ModBlocks.FUNGAL_CARPET, "Fungal Carpet");
        addBlock(ModBlocks.BROOD_COMB, "Brood Comb");
        addBlock(ModBlocks.ROYAL_COMB, "Royal Comb");
        addBlock(ModBlocks.PROVISION_COMB, "Provision Comb");
        addBlock(ModBlocks.EGG_CLUSTER, "Egg Cluster");
        addBlock(ModBlocks.DAYLIGHT_MEMBRANE, "Daylight Membrane");
        addBlock(ModBlocks.ANTHILL_SOIL, "Anthill Soil");
        addBlock(ModBlocks.ANTHILL_CORE, "Anthill Core");
        addBlock(ModBlocks.QUEENS_CREST, "Queen's Crest");

        // Ep2 task H3: decorative families.
        addBlock(ModBlocks.PACKED_SOIL_BRICKS, "Packed Soil Bricks");
        addBlock(ModBlocks.PACKED_SOIL_BRICK_STAIRS, "Packed Soil Brick Stairs");
        addBlock(ModBlocks.PACKED_SOIL_BRICK_SLAB, "Packed Soil Brick Slab");
        addBlock(ModBlocks.HARDENED_SOIL_TILES, "Hardened Soil Tiles");
        addBlock(ModBlocks.HARDENED_SOIL_TILE_STAIRS, "Hardened Soil Tile Stairs");
        addBlock(ModBlocks.HARDENED_SOIL_TILE_SLAB, "Hardened Soil Tile Slab");
        addBlock(ModBlocks.POLISHED_RESIN, "Polished Resin");
        addBlock(ModBlocks.POLISHED_RESIN_STAIRS, "Polished Resin Stairs");
        addBlock(ModBlocks.POLISHED_RESIN_SLAB, "Polished Resin Slab");

        addItem(ModItems.RESIN, "Resin");
        addItem(ModItems.CHITIN, "Chitin");
        addItem(ModItems.CHITIN_PLATE, "Chitin Plate");
        addItem(ModItems.LARVA, "Larva");
        addItem(ModItems.ROYAL_JELLY, "Royal Jelly");
        addItem(ModItems.SCENT_GLAND, "Scent Gland");
        addItem(ModItems.TRAIL_PHEROMONE, "Trail Pheromone");
        // Shown above the hotbar when a pheromone is used with nothing recorded to retrace.
        add("item.formicary.trail_pheromone.no_trail", "No trail to follow yet.");

        addItem(ModItems.ROYAL_PHEROMONE_GLAND, "Royal Pheromone Gland");
        addItem(ModItems.PHEROMONE_HORN, "Pheromone Horn");
        // Ep2: a blast with nowhere to put an ally charges no cooldown, so without this the
        // horn just looks broken.
        add("item.formicary.pheromone_horn.no_room", "The colony has no room to answer");

        addItem(ModItems.HONEYED_COMB, "Honeyed Comb");
        addItem(ModItems.FUNGAL_STEW, "Fungal Stew");
        addItem(ModItems.ROYAL_JELLY_TREAT, "Royal Jelly Treat");

        addItem(ModItems.FUNGAL_SPORES, "Fungal Spores");

        addItem(ModItems.CHITIN_HELMET, "Chitin Helmet");
        addItem(ModItems.CHITIN_CHESTPLATE, "Chitin Chestplate");
        addItem(ModItems.CHITIN_LEGGINGS, "Chitin Leggings");
        addItem(ModItems.CHITIN_BOOTS, "Chitin Boots");

        // Ep2 play-test revision (WP-1 item 2): replaces the five-tool Ep2 H1 set.
        addItem(ModItems.MANDIBLE_PICKAXE, "Mandible Pickaxe");
        addItem(ModItems.PINCER_SWORD, "Pincer Sword");

        addEntityType(ModEntities.WORKER_ANT, "Worker Ant");
        addItem(ModEntities.WORKER_ANT_SPAWN_EGG, "Worker Ant Spawn Egg");
        addEntityType(ModEntities.SOLDIER_ANT, "Soldier Ant");
        addItem(ModEntities.SOLDIER_ANT_SPAWN_EGG, "Soldier Ant Spawn Egg");
        addEntityType(ModEntities.LARVA, "Larva");
        addItem(ModEntities.LARVA_SPAWN_EGG, "Larva Spawn Egg");

        // M6: no spawn eggs for these two -- an egg-spawned tamed ant would have no owner.
        addEntityType(ModEntities.TAMED_WORKER_ANT, "Tamed Worker Ant");
        addEntityType(ModEntities.TAMED_SOLDIER_ANT, "Tamed Soldier Ant");

        // Play-test round 1, spec item 1: actionbar feedback on every command-state cycle.
        add("entity.formicary.tamed_worker_ant.state.following", "Following");
        add("entity.formicary.tamed_worker_ant.state.harvesting", "Harvesting");
        // Ep2: the sneak-click is a toggle now, so the "I looked and found nothing" answer
        // needs a line of its own -- otherwise the click is indistinguishable from a no-op.
        add("entity.formicary.tamed_worker_ant.state.no_chest", "No storage nearby");
        add("entity.formicary.tamed_soldier_ant.state.escort", "Escort");
        // Play-test round 2, spec item 3: the label only. "Guard Post" named the mechanism;
        // "Staying" names what the player just told the ant to do, and it reads as the
        // opposite of the worker's "Following". The key, the flag and the behaviour behind
        // it are all unchanged.
        add("entity.formicary.tamed_soldier_ant.state.guard_post", "Staying");

        // This name is also what the boss bar reads (ServerBossEvent takes getDisplayName()).
        addEntityType(ModEntities.QUEEN_ANT, "The Queen");
        // Play-test round 1, spec item 5: reverses the M7-era "no spawn egg" call.
        addItem(ModEntities.QUEEN_ANT_SPAWN_EGG, "Queen Ant Spawn Egg");

        addEntityType(ModEntities.ENDER_ANT, "Ender Ant");
        // Round-4 play-test revision, item 5: reverses the Ep2-era "no spawn egg on
        // purpose" call -- see ModEntities.ENDER_ANT_SPAWN_EGG.
        addItem(ModEntities.ENDER_ANT_SPAWN_EGG, "Ender Ant Spawn Egg");

        // Ep2, task F2. A projectile has a name because a death message can quote one.
        addEntityType(ModEntities.ACID_SPIT, "Acid Spit");

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

        // Ep2 task H2: same four-key shape as the disguise potion above.
        add("item.minecraft.potion.effect.hardened_chitin", "Potion of Hardened Chitin");
        add("item.minecraft.splash_potion.effect.hardened_chitin", "Splash Potion of Hardened Chitin");
        add("item.minecraft.lingering_potion.effect.hardened_chitin", "Lingering Potion of Hardened Chitin");
        add("item.minecraft.tipped_arrow.effect.hardened_chitin", "Arrow of Hardened Chitin");

        // M8: the formicary advancement tab -- root plus six beats.
        add("advancements.formicary.root.title", "Formicary");
        add("advancements.formicary.root.description", "Delve into the ant colony");
        add("advancements.formicary.enter_dimension.title", "Into the Colony");
        add("advancements.formicary.enter_dimension.description", "Enter the Formicary dimension");
        add("advancements.formicary.brew_disguise.title", "Wearing Their Scent");
        add("advancements.formicary.brew_disguise.description", "Brew a Potion of Pheromonal Disguise");
        add("advancements.formicary.capture_larva.title", "Grub Snatcher");
        add("advancements.formicary.capture_larva.description", "Capture a larva");
        add("advancements.formicary.defeat_queen.title", "Regicide");
        add("advancements.formicary.defeat_queen.description", "Defeat the Queen");
        add("advancements.formicary.first_harvest.title", "Ant Farm");
        add("advancements.formicary.first_harvest.description", "Have a tamed worker deliver its first harvest");
        add("advancements.formicary.raise_both_castes.title", "Two Castes, One Colony");
        add("advancements.formicary.raise_both_castes.description", "Raise both a worker and a soldier from larvae");
    }
}
