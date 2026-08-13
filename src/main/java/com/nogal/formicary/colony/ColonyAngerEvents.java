package com.nogal.formicary.colony;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlockTags;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The two triggers that anger the colony, on the NeoForge game bus.
 *
 * <p>Event names verified against the decompiled 21.0.167 sources rather than memory --
 * NeoForge reworked the damage pipeline for 1.21 and
 * {@code net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent} is the
 * hook that fires before mitigation (the old {@code LivingHurtEvent} is gone;
 * {@code LivingDamageEvent} is now an abstract Pre/Post pair fired later in the
 * sequence). {@code BlockEvent.BreakEvent} lives in
 * {@code net.neoforged.neoforge.event.level}.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ColonyAngerEvents {
    /** Trigger (a): a player harms any ant of the colony. */
    @SubscribeEvent
    public static void onColonyAntHurt(LivingIncomingDamageEvent event) {
        if (!ColonyAnger.isColonyAnt(event.getEntity())) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Player offender = ColonyAnger.offenderOf(event.getSource());
        if (offender == null) {
            return;
        }
        ColonyAnger.provoke(level, event.getEntity().position(), offender);
    }

    /** Trigger (b): a player breaks a block tagged {@code #formicary:hive}. */
    @SubscribeEvent
    public static void onHiveBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            // Something already refused the break, so no structure was actually lost.
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!event.getState().is(ModBlockTags.HIVE)) {
            return;
        }
        ColonyAnger.provoke(level, Vec3.atCenterOf(event.getPos()), event.getPlayer());
    }

    private ColonyAngerEvents() {
    }
}
