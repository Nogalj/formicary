package com.nogal.formicary.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * The Royal tool tier (round-4 play-test revision, item 2): a boss-gated tier a notch
 * above netherite, repaired with Chitin Plate.
 *
 * <p>1.21 tools are keyed by a {@link net.minecraft.world.item.Tier} instance, not an
 * enum -- verified against the decompiled {@code Tier}/{@code Tiers}/{@code TieredItem}
 * sources, where every vanilla tool constructor (e.g. {@code PickaxeItem(Tier,
 * Item.Properties)}) takes the interface. {@link SimpleTier} is NeoForge's own helper
 * implementation for exactly this -- "Helper class to define a custom tier", verified in
 * the decompiled {@code net.neoforged.neoforge.common.SimpleTier} -- so no anonymous
 * {@code Tier} subclass is needed, the same shortcut {@code ModArmorMaterials} takes by
 * reusing {@code ArmorMaterial} directly rather than hand-rolling a registry entry type.
 *
 * <p>Originally this class held a Chitin tier (durability 400, speed 7.5, attack damage
 * bonus 2.0, enchantability 18, repaired with raw chitin), reached the moment a player
 * had any chitin at all. Round-4 replaces it with Royal: the Pincer Sword and Mandible
 * Pickaxe are no longer early/mid-game tools, they are the two end-goal crafts gated on
 * a Queen's Crest (see {@code ModRecipeProvider#chitinToolRecipes}) -- one queen kill
 * per tool, farmable at end-game since queens are one per colony and colonies are
 * infinite. Stats are set a deliberate notch above vanilla's own best tier, verified
 * against the decompiled {@code Tiers.NETHERITE} (durability 2031, speed 9.0F, attack
 * damage bonus 4.0F, enchantability 15): durability 2300 (+269), speed 10.0F (+1.0),
 * attack damage bonus 4.0F (unchanged -- the sword's own base damage is what carries the
 * weapon buff instead, see {@code ModItems#SWORD_BASE_DAMAGE}), enchantability 18
 * (+3, matching this mod's other top-end gear rather than netherite's own value). The
 * mining-level tag is {@code BlockTags.INCORRECT_FOR_NETHERITE_TOOL} -- one step beyond
 * the old tier's Iron-borrowed {@code INCORRECT_FOR_IRON_TOOL}, since a tier this far
 * above netherite should mine everything netherite can.
 */
public final class ModToolTiers {
    public static final int DURABILITY = 2300;
    public static final float SPEED = 10.0F;
    public static final float ATTACK_DAMAGE_BONUS = 4.0F;
    public static final int ENCHANTMENT_VALUE = 18;

    public static final SimpleTier ROYAL = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            DURABILITY,
            SPEED,
            ATTACK_DAMAGE_BONUS,
            ENCHANTMENT_VALUE,
            () -> Ingredient.of(ModItems.CHITIN_PLATE.get()));

    private ModToolTiers() {
    }
}
