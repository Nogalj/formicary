package com.nogal.formicary.item;

import java.util.EnumMap;
import java.util.List;

import com.nogal.formicary.Formicary;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Chitin armor material.
 *
 * <p>In 1.21 armor materials are a REGISTRY ({@code Registries.ARMOR_MATERIAL}), not an
 * enum -- verified against the decompiled {@code ArmorMaterials}, which builds each
 * vanilla entry with {@code Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
 * ...)}. A {@link DeferredRegister} against the same registry key is the mod-side
 * equivalent, and {@code ArmorItem} takes a {@code Holder<ArmorMaterial>}, which
 * {@link DeferredHolder} is.
 *
 * <p>Defense values are iron's exactly (helmet 2 / chest 6 / leggings 5 / boots 2, per
 * {@code ArmorMaterials.IRON}); durability multiplier 15 and enchantability 9 likewise.
 * The equip sound is the turtle-shell one -- the most chitinous thing vanilla owns.
 *
 * <p>The {@code Layer} asset name drives the humanoid overlay lookup: {@code Layer}
 * resolves {@code <namespace>:textures/models/armor/<path>_layer_1.png} (outer: helmet,
 * chestplate, boots) and {@code _layer_2.png} (inner: leggings), so
 * {@code formicary:chitin} means
 * {@code assets/formicary/textures/models/armor/chitin_layer_{1,2}.png}.
 *
 * <p>Round-4 play-test revision: the repair ingredient changes from raw Chitin to
 * {@link ModItems#CHITIN_PLATE} -- the same swap the four armor recipes make in
 * {@code ModRecipeProvider#chitinArmorRecipes}, so repairing the set costs the same
 * crafting intermediate that built it.
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Formicary.MODID);

    /** Iron-grade protection. Tunable: bump these and the durability factor together. */
    public static final int DURABILITY_FACTOR = 15;

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CHITIN = ARMOR_MATERIALS.register(
            "chitin",
            () -> new ArmorMaterial(
                    defense(2, 6, 5, 2),
                    9,
                    SoundEvents.ARMOR_EQUIP_TURTLE,
                    () -> Ingredient.of(ModItems.CHITIN_PLATE.get()),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "chitin"))),
                    0.0F,
                    0.0F));

    private static EnumMap<ArmorItem.Type, Integer> defense(int helmet, int chestplate, int leggings, int boots) {
        EnumMap<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.HELMET, helmet);
        map.put(ArmorItem.Type.CHESTPLATE, chestplate);
        map.put(ArmorItem.Type.LEGGINGS, leggings);
        map.put(ArmorItem.Type.BOOTS, boots);
        // BODY is the animal-armor slot (wolf/horse); no chitin barding exists, but the
        // map is read with getOrDefault so leaving it out is safe.
        return map;
    }

    private ModArmorMaterials() {
    }
}
