package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * M1 items: the resin resource item, plus a {@code BlockItem} for every M1 block via
 * {@link DeferredRegister.Items#registerSimpleBlockItem(net.minecraft.core.Holder)}.
 * M3a adds chitin (worker/soldier loot) and the larva item (the capture interaction's
 * export good). M3b adds the four-piece Chitin Armor set, which is what gates mining the
 * dimension's fabric. M4b adds the Scent Gland (worker/soldier loot, brewed into
 * Pheromonal Disguise). M5 adds the Trail Pheromone, the breadcrumb consumable, which is
 * the mod's first crafted item. M6 turns the larva into a placeable {@link LarvaItem} and
 * adds Royal Jelly, the food that raises a placed larva as a worker.
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Formicary.MODID);

    public static final DeferredItem<Item> RESIN = ITEMS.registerSimpleItem("resin");
    public static final DeferredItem<Item> CHITIN = ITEMS.registerSimpleItem("chitin");
    public static final DeferredItem<Item> SCENT_GLAND = ITEMS.registerSimpleItem("scent_gland");

    /** M6: the captured grub. Right-click a block face to set it down (see {@link LarvaItem}). */
    public static final DeferredItem<LarvaItem> LARVA = ITEMS.registerItem("larva", LarvaItem::new);

    /**
     * M6: what a placed larva is fed to raise it as a worker. A plain item -- the spec
     * gives it no use of its own, and it is creative-only until M7 adds its survival
     * sources (the queen's chamber).
     */
    public static final DeferredItem<Item> ROYAL_JELLY = ITEMS.registerSimpleItem("royal_jelly");

    /** M5: lights up the player's own recorded route back out of the colony. */
    public static final DeferredItem<TrailPheromoneItem> TRAIL_PHEROMONE =
            ITEMS.registerItem("trail_pheromone", TrailPheromoneItem::new);

    /**
     * M7: the queen's guaranteed drop. A plain item -- its only use is the Pheromone Horn
     * recipe, and the fact that the sole source is her corpse is what makes the horn a
     * post-boss tool rather than a consumable.
     */
    public static final DeferredItem<Item> ROYAL_PHEROMONE_GLAND =
            ITEMS.registerSimpleItem("royal_pheromone_gland");

    /** M7: reusable summon, two allied soldiers a blow, on a long cooldown. */
    public static final DeferredItem<PheromoneHornItem> PHEROMONE_HORN =
            ITEMS.registerItem("pheromone_horn", PheromoneHornItem::new);

    // --- Chitin Armor (spec section 5). No recipes until M8: creative-only for now. ---

    public static final DeferredItem<ArmorItem> CHITIN_HELMET = armorPiece("chitin_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorItem> CHITIN_CHESTPLATE = armorPiece("chitin_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorItem> CHITIN_LEGGINGS = armorPiece("chitin_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorItem> CHITIN_BOOTS = armorPiece("chitin_boots", ArmorItem.Type.BOOTS);

    private static DeferredItem<ArmorItem> armorPiece(String name, ArmorItem.Type type) {
        return ITEMS.registerItem(name,
                props -> new ArmorItem(ModArmorMaterials.CHITIN, type,
                        props.durability(type.getDurability(ModArmorMaterials.DURABILITY_FACTOR))));
    }

    public static final DeferredItem<BlockItem> PACKED_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_SOIL);
    public static final DeferredItem<BlockItem> AMBER_EARTH = ITEMS.registerSimpleBlockItem(ModBlocks.AMBER_EARTH);
    public static final DeferredItem<BlockItem> DEEP_LOAM = ITEMS.registerSimpleBlockItem(ModBlocks.DEEP_LOAM);
    public static final DeferredItem<BlockItem> HARDENED_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.HARDENED_SOIL);
    public static final DeferredItem<BlockItem> RESIN_WEEP = ITEMS.registerSimpleBlockItem(ModBlocks.RESIN_WEEP);
    public static final DeferredItem<BlockItem> RESIN_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RESIN_BLOCK);
    public static final DeferredItem<BlockItem> AMBER_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.AMBER_GLASS);
    public static final DeferredItem<BlockItem> FUNGAL_BLOOM = ITEMS.registerSimpleBlockItem(ModBlocks.FUNGAL_BLOOM);
    public static final DeferredItem<BlockItem> FUNGAL_CARPET = ITEMS.registerSimpleBlockItem(ModBlocks.FUNGAL_CARPET);
    public static final DeferredItem<BlockItem> BROOD_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.BROOD_COMB);
    public static final DeferredItem<BlockItem> ROYAL_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.ROYAL_COMB);
    public static final DeferredItem<BlockItem> EGG_CLUSTER = ITEMS.registerSimpleBlockItem(ModBlocks.EGG_CLUSTER);
    public static final DeferredItem<BlockItem> DAYLIGHT_MEMBRANE = ITEMS.registerSimpleBlockItem(ModBlocks.DAYLIGHT_MEMBRANE);
    public static final DeferredItem<BlockItem> ANTHILL_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.ANTHILL_SOIL);
    public static final DeferredItem<BlockItem> ANTHILL_CORE = ITEMS.registerSimpleBlockItem(ModBlocks.ANTHILL_CORE);
    public static final DeferredItem<BlockItem> QUEENS_CREST = ITEMS.registerSimpleBlockItem(ModBlocks.QUEENS_CREST);

    private ModItems() {
    }
}
