package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    private ModItems() {
    }

    private static DeferredItem<ArmorItem> armorPiece(String name, ArmorItem.Type type) {
        return ITEMS.registerItem(name,
                props -> new ArmorItem(ModArmorMaterials.CHITIN, type,
                        props.durability(type.getDurability(ModArmorMaterials.DURABILITY_FACTOR))));
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Formicary.MODID);

    public static final DeferredItem<Item> RESIN = ITEMS.registerSimpleItem("resin");
    public static final DeferredItem<Item> CHITIN = ITEMS.registerSimpleItem("chitin");
    public static final DeferredItem<Item> SCENT_GLAND = ITEMS.registerSimpleItem("scent_gland");

    public static final DeferredItem<LarvaItem> LARVA = ITEMS.registerItem("larva", LarvaItem::new);

    public static final DeferredItem<Item> ROYAL_JELLY = ITEMS.registerSimpleItem("royal_jelly");

    public static final DeferredItem<TrailPheromoneItem> TRAIL_PHEROMONE =
            ITEMS.registerItem("trail_pheromone", TrailPheromoneItem::new);

    public static final DeferredItem<Item> ROYAL_PHEROMONE_GLAND =
            ITEMS.registerSimpleItem("royal_pheromone_gland");

    public static final DeferredItem<PheromoneHornItem> PHEROMONE_HORN =
            ITEMS.registerItem("pheromone_horn", PheromoneHornItem::new);

    public static final DeferredItem<ArmorItem> CHITIN_HELMET = armorPiece("chitin_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorItem> CHITIN_CHESTPLATE = armorPiece("chitin_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorItem> CHITIN_LEGGINGS = armorPiece("chitin_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorItem> CHITIN_BOOTS = armorPiece("chitin_boots", ArmorItem.Type.BOOTS);

    public static final DeferredItem<BlockItem> PACKED_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_SOIL);
    public static final DeferredItem<BlockItem> AMBER_EARTH = ITEMS.registerSimpleBlockItem(ModBlocks.AMBER_EARTH);
    public static final DeferredItem<BlockItem> DEEP_LOAM = ITEMS.registerSimpleBlockItem(ModBlocks.DEEP_LOAM);
    public static final DeferredItem<BlockItem> HARDENED_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.HARDENED_SOIL);
    public static final DeferredItem<BlockItem> RESIN_WEEP = ITEMS.registerSimpleBlockItem(ModBlocks.RESIN_WEEP);
    public static final DeferredItem<BlockItem> RESIN_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RESIN_BLOCK);
    public static final DeferredItem<BlockItem> AMBER_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.AMBER_GLASS);

    public static final DeferredItem<FungalSporeItem> FUNGAL_SPORE =
            ITEMS.registerItem("fungal_spore", FungalSporeItem::new);

    public static final DeferredItem<BlockItem> FUNGAL_BLOOM = ITEMS.registerItem(
            "fungal_bloom",
            props -> new BlockItem(ModBlocks.FUNGAL_BLOOM.get(),
                    props.food(new FoodProperties.Builder()
                            .nutrition(2)
                            .saturationModifier(0.3F)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0), 1.0F)
                            .build())));

    public static final DeferredItem<BlockItem> FUNGAL_CARPET = ITEMS.registerSimpleBlockItem(ModBlocks.FUNGAL_CARPET);
    public static final DeferredItem<BlockItem> BROOD_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.BROOD_COMB);
    public static final DeferredItem<BlockItem> ROYAL_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.ROYAL_COMB);
    public static final DeferredItem<BlockItem> EGG_CLUSTER = ITEMS.registerSimpleBlockItem(ModBlocks.EGG_CLUSTER);
    public static final DeferredItem<BlockItem> DAYLIGHT_MEMBRANE = ITEMS.registerSimpleBlockItem(ModBlocks.DAYLIGHT_MEMBRANE);
    public static final DeferredItem<BlockItem> ANTHILL_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.ANTHILL_SOIL);
    public static final DeferredItem<BlockItem> ANTHILL_CORE = ITEMS.registerSimpleBlockItem(ModBlocks.ANTHILL_CORE);
    public static final DeferredItem<BlockItem> QUEENS_CREST = ITEMS.registerSimpleBlockItem(ModBlocks.QUEENS_CREST);
}
