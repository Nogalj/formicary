package com.nogal.formicary.entity;

import com.nogal.formicary.Formicary;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The chest half of binding a tamed worker (spec section 4): "right-click a chest while it
 * follows -&gt; the worker binds to that chest and switches to work mode".
 *
 * <p>The event is deliberately <em>not</em> cancelled. Binding is a side effect of opening
 * your own chest, not a replacement for it -- swallowing the click would mean you could no
 * longer open a chest that a worker happens to be standing near, which is a far worse
 * failure than the occasional extra binding. Only an ant that is currently unbound is
 * eligible, so re-opening the chest afterwards does nothing.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TamedAntEvents {
    @SubscribeEvent
    public static void onChestRightClicked(PlayerInteractEvent.RightClickBlock event) {
        // The event fires once per hand; without this a two-handed click would run twice.
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!TamedWorkerAntEntity.isDepositBlock(level.getBlockState(event.getPos()))) {
            return;
        }
        TamedWorkerAntEntity.bindNearestFollower(level, event.getEntity(), event.getPos());
    }

    private TamedAntEvents() {
    }
}
