package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlockTags;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The fabric mining gate (spec section 5): the dimension's tier soils and Hardened Soil
 * are effectively unmineable unless the player is wearing all four Chitin Armor pieces
 * OR holding the Mandible Pickaxe in the main hand (Ep2 play-test revision, WP-1 item 3
 * -- the pickaxe bypass applies regardless of armor).
 *
 * <p>Deliberately not an absolute block -- a player trapped in the fabric without the set
 * (or the pickaxe) can still dig out, just 25x slower and with nothing to show for it.
 * The "nothing to show for it" half lives in the loot table condition
 * ({@code loot/WearingFullChitinCondition}, which OR's in the same pickaxe bypass); this
 * class owns the speed half and the {@link #hasFullSet(Player)} check both halves share.
 *
 * <p>Event name verified against the decompiled 21.0.167 sources:
 * {@code net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed}, with
 * {@code getOriginalSpeed()} / {@code setNewSpeed(float)}.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ChitinArmor {
    /** How much slower the fabric mines without the set. Tunable. */
    public static final float UNGATED_SPEED_DIVISOR = 25.0F;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** True only when every one of the four chitin pieces is worn in its own slot. */
    public static boolean hasFullSet(Player player) {
        if (player == null) {
            return false;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.is(pieceFor(slot))) {
                return false;
            }
        }
        return true;
    }

    private static Item pieceFor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ModItems.CHITIN_HELMET.get();
            case CHEST -> ModItems.CHITIN_CHESTPLATE.get();
            case LEGS -> ModItems.CHITIN_LEGGINGS.get();
            default -> ModItems.CHITIN_BOOTS.get();
        };
    }

    @SubscribeEvent
    public static void onFabricBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!event.getState().is(ModBlockTags.COLONY_FABRIC)) {
            return;
        }
        if (hasFullSet(event.getEntity()) || holdsMandiblePickaxe(event.getEntity())) {
            // Full set, or the Mandible Pickaxe alone (Ep2 play-test revision, WP-1 item
            // 3): normal speed, event untouched.
            return;
        }
        // Scale whatever speed the pipeline has arrived at, not the original, so this
        // composes with other handlers instead of stomping them. The event ctor seeds
        // newSpeed with originalSpeed, so a lone handler still sees original / 25.
        event.setNewSpeed(event.getNewSpeed() / UNGATED_SPEED_DIVISOR);
    }

    /** True when the player's main-hand stack is the Mandible Pickaxe, armor aside. */
    private static boolean holdsMandiblePickaxe(Player player) {
        return player != null && player.getMainHandItem().is(ModItems.MANDIBLE_PICKAXE.get());
    }

    private ChitinArmor() {
    }
}
