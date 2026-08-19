package com.nogal.formicary.loot;

import java.util.Set;

import com.mojang.serialization.MapCodec;
import com.nogal.formicary.item.ChitinArmor;
import com.nogal.formicary.item.ModItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * Loot condition {@code formicary:wearing_full_chitin}: passes when the entity that
 * caused the loot roll is a player wearing all four Chitin Armor pieces, OR the loot
 * context's TOOL is the Mandible Pickaxe (Ep2 play-test revision, WP-1 item 3 -- the same
 * pickaxe-bypasses-the-gate rule {@code ChitinArmor#onFabricBreakSpeed} applies to break
 * speed). The registered id keeps its original name -- renaming it would ripple through
 * every generated loot-table JSON that references it for no behavioural gain, and this
 * javadoc is the source of truth for what it actually tests now: full armor OR the
 * pickaxe, not armor alone. Wrapping the fabric blocks' self-drop pool in this condition
 * is the "and no drops" half of the mining gate (spec section 5).
 *
 * <p>Which context param carries the breaker was verified against
 * {@code LootContextParamSets.BLOCK}, which declares {@code THIS_ENTITY} as an OPTIONAL
 * member (alongside required {@code BLOCK_STATE} / {@code ORIGIN} / {@code TOOL}) --
 * hence {@code getParamOrNull} rather than {@code getParam} for both params, so a
 * TNT-triggered or piston-triggered break simply fails the condition rather than
 * crashing.
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
        return Set.of(LootContextParams.THIS_ENTITY, LootContextParams.TOOL);
    }

    @Override
    public boolean test(LootContext context) {
        if (context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player player
                && ChitinArmor.hasFullSet(player)) {
            return true;
        }
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        return tool != null && tool.is(ModItems.MANDIBLE_PICKAXE.get());
    }

    /** Datagen entry point: {@code .when(WearingFullChitinCondition.wearingFullChitin())}. */
    public static LootItemCondition.Builder wearingFullChitin() {
        return () -> INSTANCE;
    }
}
