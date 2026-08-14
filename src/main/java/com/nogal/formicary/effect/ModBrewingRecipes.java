package com.nogal.formicary.effect;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.item.ModItems;

import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Wires Awkward Potion + Scent Gland -> Pheromonal Disguise into the brewing stand.
 *
 * <p>{@code RegisterBrewingRecipesEvent} is fired by {@code PotionBrewing.bootstrap} via
 * {@code net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(...)} -- verified against
 * the decompiled {@code PotionBrewing.java} -- so, like {@code ColonyAngerEvents}, this
 * belongs on the GAME bus, not the mod bus.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ModBrewingRecipes {
    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(Potions.AWKWARD, ModItems.SCENT_GLAND.get(), ModPotions.PHEROMONAL_DISGUISE);
    }

    private ModBrewingRecipes() {
    }
}
