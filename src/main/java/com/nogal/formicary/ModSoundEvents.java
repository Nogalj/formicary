package com.nogal.formicary;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.core.registries.Registries;

/**
 * M8: ambient sound events for the four ant mobs. Each fires from its entity's
 * getAmbientSound() override in place of the placeholder spider sounds.
 *
 * The corresponding entries in assets/formicary/sounds.json point at
 * placeholder audio paths -- no real audio files are included in M8 (programmer sound).
 */
public final class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Formicary.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WORKER_AMBIENT_CLICK =
            SOUND_EVENTS.register("entity.worker_ant.ambient",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "entity.worker_ant.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SOLDIER_AMBIENT_CLICK =
            SOUND_EVENTS.register("entity.soldier_ant.ambient",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "entity.soldier_ant.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LARVA_AMBIENT_SQUISH =
            SOUND_EVENTS.register("entity.larva.ambient",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "entity.larva.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> QUEEN_AMBIENT_DEEP =
            SOUND_EVENTS.register("entity.queen_ant.ambient",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "entity.queen_ant.ambient")));

    private ModSoundEvents() {
    }
}
