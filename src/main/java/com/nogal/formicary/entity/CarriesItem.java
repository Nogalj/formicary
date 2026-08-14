package com.nogal.formicary.entity;

import net.minecraft.world.item.ItemStack;

/**
 * An ant whose current cargo should be drawn in its mandibles.
 *
 * <p>Exists so {@code client.renderer.WorkerAntCarriedItemLayer} can serve both the wild
 * worker (which ferries a single stack in its main hand) and the tamed worker (which fills
 * a nine-slot pack) without either entity's storage choice leaking into the renderer.
 */
public interface CarriesItem {
    /** The stack to draw, or {@link ItemStack#EMPTY} to draw nothing. */
    ItemStack getCarriedItem();
}
