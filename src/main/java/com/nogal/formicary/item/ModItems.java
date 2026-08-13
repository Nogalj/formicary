package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * M1 items: the resin resource item, plus a {@code BlockItem} for every M1 block via
 * {@link DeferredRegister.Items#registerSimpleBlockItem(net.minecraft.core.Holder)}.
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Formicary.MODID);

    public static final DeferredItem<Item> RESIN = ITEMS.registerSimpleItem("resin");

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
