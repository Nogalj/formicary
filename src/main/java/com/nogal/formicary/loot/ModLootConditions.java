package com.nogal.formicary.loot;

import com.nogal.formicary.Formicary;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * This mod's loot condition types.
 *
 * <p>Registry key verified against {@code net.minecraft.core.registries.Registries}:
 * {@code LOOT_CONDITION_TYPE = createRegistryKey("loot_condition_type")}. Vanilla's own
 * conditions register a {@link LootItemConditionType} wrapping a {@code MapCodec} into
 * {@code BuiltInRegistries.LOOT_CONDITION_TYPE} (see {@code LootItemConditions}); a
 * {@link DeferredRegister} against the same key is the mod-side equivalent.
 */
public final class ModLootConditions {
    public static final DeferredRegister<LootItemConditionType> LOOT_CONDITION_TYPES =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Formicary.MODID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> WEARING_FULL_CHITIN =
            LOOT_CONDITION_TYPES.register(
                    "wearing_full_chitin",
                    () -> new LootItemConditionType(WearingFullChitinCondition.CODEC));

    private ModLootConditions() {
    }
}
