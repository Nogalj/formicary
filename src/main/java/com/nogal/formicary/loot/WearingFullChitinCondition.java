package com.nogal.formicary.loot;

import java.util.Set;

import com.mojang.serialization.MapCodec;
import com.nogal.formicary.item.ChitinArmor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * Loot condition {@code formicary:wearing_full_chitin}: passes only when the entity that
 * caused the loot roll is a player wearing all four Chitin Armor pieces. Wrapping the
 * fabric blocks' self-drop pool in this is the "and no drops" half of the mining gate
 * (spec section 5).
 *
 * <p>Which context param carries the breaker was verified against
 * {@code LootContextParamSets.BLOCK}, which declares {@code THIS_ENTITY} as an OPTIONAL
 * member (alongside required {@code BLOCK_STATE} / {@code ORIGIN} / {@code TOOL}) --
 * hence {@code getParamOrNull} rather than {@code getParam}, and hence a TNT-triggered
 * or piston-triggered break simply fails the condition rather than crashing.
 *
 * <p>Shape copied from vanilla's {@code LootItemKilledByPlayerCondition}: a stateless
 * singleton behind {@code MapCodec.unit}, since the condition has no fields to serialise.
 */
public class WearingFullChitinCondition implements LootItemCondition {
    private static final WearingFullChitinCondition INSTANCE = new WearingFullChitinCondition();

    public static final MapCodec<WearingFullChitinCondition> CODEC = MapCodec.unit(INSTANCE);

    private WearingFullChitinCondition() {
    }

    @Override
    public LootItemConditionType getType() {
        return ModLootConditions.WEARING_FULL_CHITIN.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        return context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player player
                && ChitinArmor.hasFullSet(player);
    }

    /** Datagen entry point: {@code .when(WearingFullChitinCondition.wearingFullChitin())}. */
    public static LootItemCondition.Builder wearingFullChitin() {
        return () -> INSTANCE;
    }
}
