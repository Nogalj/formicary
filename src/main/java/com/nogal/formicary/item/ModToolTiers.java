package com.nogal.formicary.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * The Chitin tool tier (Ep2 task H1): fast, mid-durability, repaired with chitin.
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
 * <p>Stats are the task brief's exactly (durability 400, speed 7.5, attack damage bonus
 * 2.0, enchantability 18, repaired by {@code formicary:chitin} -- the same item
 * {@code ModArmorMaterials.CHITIN} repairs with). The brief does not specify a mining
 * level, so {@code getIncorrectBlocksForDrops()} borrows {@code
 * BlockTags.INCORRECT_FOR_IRON_TOOL} -- the vanilla tag matching Iron's own mining level,
 * chosen because the attack damage bonus here (2.0) is Iron's exactly (see
 * {@code Tiers.IRON} in the decompiled sources); reusing an existing vanilla tag avoids
 * inventing a new mining-level tier the spec never asked for.
 */
public final class ModToolTiers {
    public static final int DURABILITY = 400;
    public static final float SPEED = 7.5F;
    public static final float ATTACK_DAMAGE_BONUS = 2.0F;
    public static final int ENCHANTMENT_VALUE = 18;

    public static final SimpleTier CHITIN = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            DURABILITY,
            SPEED,
            ATTACK_DAMAGE_BONUS,
            ENCHANTMENT_VALUE,
            () -> Ingredient.of(ModItems.CHITIN.get()));

    private ModToolTiers() {
    }
}
