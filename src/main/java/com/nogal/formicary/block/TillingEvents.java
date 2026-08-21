package com.nogal.formicary.block;

import com.nogal.formicary.Formicary;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Play-test round 5, item 5: lets a hoe till the colony's soft soils into farmland.
 *
 * <p>Event shape verified against the decompiled 21.0.167 sources
 * (see {@code SoilTilling}'s javadoc for the exact reading). The live call path is:
 * {@code HoeItem#useOn} calls {@code BlockState#getToolModifiedState}, an
 * {@code IBlockStateExtension} default that calls {@code EventHooks#onToolUse}, which
 * constructs a {@link BlockEvent.BlockToolModificationEvent}, posts it to
 * {@code NeoForge.EVENT_BUS} (the GAME bus -- matching {@code PortalEvents}'
 * {@code @EventBusSubscriber} pattern below) and, if not cancelled, returns
 * {@code event.getFinalState()}. Only if that state differs from the original does the
 * event route win over {@code IBlockExtension}'s own vanilla-only default (which knows
 * nothing about this mod's blocks) -- so calling {@link BlockEvent.BlockToolModificationEvent#setFinalState}
 * is both necessary and sufficient here; this handler never touches the level itself.
 * {@code HoeItem#useOn} is the one that actually calls {@code Level#setBlock} with
 * whatever final state won, plays the till sound, and damages the tool -- none of that is
 * this mod's job to duplicate.
 *
 * <p>Vanilla farmland reverting to dirt (trampled, or its own moisture/light upkeep) is
 * accepted as-is in the colony -- see {@code docs/DECISIONS.md}'s "Play-test round 5"
 * entry: dirt has been a worldgen material in the fabric since round 2, so a stray dirt
 * block from a trampled farm patch is not a new kind of mess.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TillingEvents {

    @SubscribeEvent
    public static void onHoeTill(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.HOE_TILL) {
            return;
        }
        // Mirrors the live vanilla check exactly (see SoilTilling's javadoc): only
        // whether the block above is air, nothing about the clicked face.
        boolean spaceAboveIsAir = event.getLevel().getBlockState(event.getPos().above()).isAir();
        BlockState tilled = SoilTilling.tilledState(event.getState(), spaceAboveIsAir);
        if (tilled != null) {
            event.setFinalState(tilled);
        }
    }

    private TillingEvents() {
    }
}
