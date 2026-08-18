package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
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

    /**
     * Eating a mature Fungal Bloom grants Night Vision (spec section 5/8).
     * {@code saturationModifier}, not raw saturation -- {@code FoodProperties.Builder}
     * derives the stored {@code saturation} field from it ({@code
     * FoodConstants.saturationByModifier}), the same way vanilla's own foods are built
     * (e.g. the apple's nutrition 4 / modifier 0.3).
     *
     * <p>Play-test round 1: 600 ticks (~30s, M8's original spec reading) dropped to 200
     * (10s) -- the owner's explicit ask, a quick peek rather than a standing buff. Tunable.
     */
    private static final int FUNGAL_BLOOM_NIGHT_VISION_TICKS = 200;

    private static final FoodProperties FUNGAL_BLOOM_FOOD = new FoodProperties.Builder()
            .nutrition(2)
            .saturationModifier(0.3F)
            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, FUNGAL_BLOOM_NIGHT_VISION_TICKS), 1.0F)
            .build();

    /** D1: quick, good food (spec section 8) -- {@code .fast()} halves the vanilla eat time. */
    private static final FoodProperties HONEYED_COMB_FOOD = new FoodProperties.Builder()
            .nutrition(4)
            .saturationModifier(0.6F)
            .fast()
            .build();

    /**
     * D1: bowl food from Fungus Garden crops (spec section 8). {@code usingConvertsTo}
     * is the verified {@code FoodProperties.Builder} method (see {@code reference/}) that
     * gives vanilla's Mushroom/Rabbit Stew their "returns a bowl" behaviour -- the same
     * shape this reuses. Night Vision duration matches {@link #FUNGAL_BLOOM_NIGHT_VISION_TICKS}
     * exactly: that is the play-test round 1 retuned fungus precedent (10s), not the
     * spec's original 30s reading, and the task brief asks for the same 10s here.
     */
    private static final FoodProperties FUNGAL_STEW_FOOD = new FoodProperties.Builder()
            .nutrition(8)
            .saturationModifier(0.6F)
            .usingConvertsTo(Items.BOWL)
            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, FUNGAL_BLOOM_NIGHT_VISION_TICKS), 1.0F)
            .build();

    /** D1: brief Absorption I (20s) -- jelly + comb, always edible even at full hunger. */
    private static final int ROYAL_JELLY_TREAT_ABSORPTION_TICKS = 400;

    private static final FoodProperties ROYAL_JELLY_TREAT_FOOD = new FoodProperties.Builder()
            .nutrition(4)
            .saturationModifier(0.6F)
            .alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, ROYAL_JELLY_TREAT_ABSORPTION_TICKS, 0), 1.0F)
            .build();

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

    // --- D1: colony foods (spec section 8) ---

    public static final DeferredItem<Item> HONEYED_COMB = ITEMS.registerItem("honeyed_comb",
            props -> new Item(props.food(HONEYED_COMB_FOOD)));
    public static final DeferredItem<Item> FUNGAL_STEW = ITEMS.registerItem("fungal_stew",
            props -> new Item(props.food(FUNGAL_STEW_FOOD).stacksTo(1)));
    public static final DeferredItem<Item> ROYAL_JELLY_TREAT = ITEMS.registerItem("royal_jelly_treat",
            props -> new Item(props.food(ROYAL_JELLY_TREAT_FOOD)));

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

    // --- Ep2 H1: Chitin tool set. Per-tool base attack damage/speed are not part of the
    // Tier interface (they're chosen per Item type, the way vanilla's Items.java does it
    // per tool), and the task brief only specifies the shared Tier stats -- so these five
    // borrow Iron's own base damage/attack-speed constants exactly (verified in the
    // decompiled Items.java), the same precedent ModArmorMaterials sets by matching Iron's
    // defense values where the spec is silent. ---

    private static final float SWORD_BASE_DAMAGE = 3.0F;
    private static final float SWORD_ATTACK_SPEED = -2.4F;
    private static final float PICKAXE_BASE_DAMAGE = 1.0F;
    private static final float PICKAXE_ATTACK_SPEED = -2.8F;
    private static final float AXE_BASE_DAMAGE = 6.0F;
    private static final float AXE_ATTACK_SPEED = -3.1F;
    private static final float SHOVEL_BASE_DAMAGE = 1.5F;
    private static final float SHOVEL_ATTACK_SPEED = -3.0F;
    private static final float HOE_BASE_DAMAGE = -2.0F;
    private static final float HOE_ATTACK_SPEED = -1.0F;

    public static final DeferredItem<SwordItem> CHITIN_SWORD = ITEMS.registerItem("chitin_sword",
            props -> new SwordItem(ModToolTiers.CHITIN,
                    props.attributes(SwordItem.createAttributes(ModToolTiers.CHITIN, SWORD_BASE_DAMAGE, SWORD_ATTACK_SPEED))));
    public static final DeferredItem<PickaxeItem> CHITIN_PICKAXE = ITEMS.registerItem("chitin_pickaxe",
            props -> new PickaxeItem(ModToolTiers.CHITIN,
                    props.attributes(PickaxeItem.createAttributes(ModToolTiers.CHITIN, PICKAXE_BASE_DAMAGE, PICKAXE_ATTACK_SPEED))));
    public static final DeferredItem<AxeItem> CHITIN_AXE = ITEMS.registerItem("chitin_axe",
            props -> new AxeItem(ModToolTiers.CHITIN,
                    props.attributes(AxeItem.createAttributes(ModToolTiers.CHITIN, AXE_BASE_DAMAGE, AXE_ATTACK_SPEED))));
    public static final DeferredItem<ShovelItem> CHITIN_SHOVEL = ITEMS.registerItem("chitin_shovel",
            props -> new ShovelItem(ModToolTiers.CHITIN,
                    props.attributes(ShovelItem.createAttributes(ModToolTiers.CHITIN, SHOVEL_BASE_DAMAGE, SHOVEL_ATTACK_SPEED))));
    public static final DeferredItem<HoeItem> CHITIN_HOE = ITEMS.registerItem("chitin_hoe",
            props -> new HoeItem(ModToolTiers.CHITIN,
                    props.attributes(HoeItem.createAttributes(ModToolTiers.CHITIN, HOE_BASE_DAMAGE, HOE_ATTACK_SPEED))));

    public static final DeferredItem<BlockItem> PACKED_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_SOIL);
    public static final DeferredItem<BlockItem> AMBER_EARTH = ITEMS.registerSimpleBlockItem(ModBlocks.AMBER_EARTH);
    public static final DeferredItem<BlockItem> DEEP_LOAM = ITEMS.registerSimpleBlockItem(ModBlocks.DEEP_LOAM);
    public static final DeferredItem<BlockItem> HARDENED_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.HARDENED_SOIL);
    public static final DeferredItem<BlockItem> RESIN_WEEP = ITEMS.registerSimpleBlockItem(ModBlocks.RESIN_WEEP);
    public static final DeferredItem<BlockItem> RESIN_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RESIN_BLOCK);
    public static final DeferredItem<BlockItem> AMBER_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.AMBER_GLASS);
    // M8: edible -- see FUNGAL_BLOOM_FOOD above.
    public static final DeferredItem<BlockItem> FUNGAL_BLOOM = ITEMS.registerSimpleBlockItem(
            ModBlocks.FUNGAL_BLOOM, new Item.Properties().food(FUNGAL_BLOOM_FOOD));
    public static final DeferredItem<BlockItem> FUNGAL_CARPET = ITEMS.registerSimpleBlockItem(ModBlocks.FUNGAL_CARPET);
    /**
     * M8: the Fungal Spore crop's seed item. An {@code ItemNameBlockItem}, not
     * {@code registerSimpleBlockItem}, because the item's name ("fungal_spores") differs
     * from the block it places ("fungal_spore_crop") -- the same shape vanilla's own
     * {@code wheat_seeds} uses for {@code Blocks.WHEAT}.
     */
    public static final DeferredItem<ItemNameBlockItem> FUNGAL_SPORES = ITEMS.registerItem("fungal_spores",
            props -> new ItemNameBlockItem(ModBlocks.FUNGAL_SPORE_CROP.get(), props));
    public static final DeferredItem<BlockItem> BROOD_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.BROOD_COMB);
    public static final DeferredItem<BlockItem> ROYAL_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.ROYAL_COMB);
    public static final DeferredItem<BlockItem> PROVISION_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.PROVISION_COMB);
    public static final DeferredItem<BlockItem> EGG_CLUSTER = ITEMS.registerSimpleBlockItem(ModBlocks.EGG_CLUSTER);
    public static final DeferredItem<BlockItem> DAYLIGHT_MEMBRANE = ITEMS.registerSimpleBlockItem(ModBlocks.DAYLIGHT_MEMBRANE);
    public static final DeferredItem<BlockItem> ANTHILL_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.ANTHILL_SOIL);
    public static final DeferredItem<BlockItem> ANTHILL_CORE = ITEMS.registerSimpleBlockItem(ModBlocks.ANTHILL_CORE);
    public static final DeferredItem<BlockItem> QUEENS_CREST = ITEMS.registerSimpleBlockItem(ModBlocks.QUEENS_CREST);

    // --- Ep2 task H3: decorative families. ---
    public static final DeferredItem<BlockItem> PACKED_SOIL_BRICKS =
            ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_SOIL_BRICKS);
    public static final DeferredItem<BlockItem> PACKED_SOIL_BRICK_STAIRS =
            ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_SOIL_BRICK_STAIRS);
    public static final DeferredItem<BlockItem> PACKED_SOIL_BRICK_SLAB =
            ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_SOIL_BRICK_SLAB);
    public static final DeferredItem<BlockItem> PACKED_SOIL_BRICK_WALL =
            ITEMS.registerSimpleBlockItem(ModBlocks.PACKED_SOIL_BRICK_WALL);
    public static final DeferredItem<BlockItem> HARDENED_SOIL_TILES =
            ITEMS.registerSimpleBlockItem(ModBlocks.HARDENED_SOIL_TILES);
    public static final DeferredItem<BlockItem> HARDENED_SOIL_TILE_STAIRS =
            ITEMS.registerSimpleBlockItem(ModBlocks.HARDENED_SOIL_TILE_STAIRS);
    public static final DeferredItem<BlockItem> HARDENED_SOIL_TILE_SLAB =
            ITEMS.registerSimpleBlockItem(ModBlocks.HARDENED_SOIL_TILE_SLAB);
    public static final DeferredItem<BlockItem> POLISHED_RESIN =
            ITEMS.registerSimpleBlockItem(ModBlocks.POLISHED_RESIN);
    public static final DeferredItem<BlockItem> POLISHED_RESIN_STAIRS =
            ITEMS.registerSimpleBlockItem(ModBlocks.POLISHED_RESIN_STAIRS);
    public static final DeferredItem<BlockItem> POLISHED_RESIN_SLAB =
            ITEMS.registerSimpleBlockItem(ModBlocks.POLISHED_RESIN_SLAB);

    private ModItems() {
    }
}
