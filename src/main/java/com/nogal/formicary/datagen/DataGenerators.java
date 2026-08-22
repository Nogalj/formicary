package com.nogal.formicary.datagen;

import java.util.concurrent.CompletableFuture;

import com.nogal.formicary.Formicary;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Wires all M1 datagen providers into {@code runData}.
 *
 * <p>Verified against the decompiled sources: {@code GatherDataEvent} lives in
 * {@code net.neoforged.neoforge.data.event} (NeoForge, not vanilla), and the
 * mod-bus static-subscriber annotation is {@code net.neoforged.fml.common.EventBusSubscriber}
 * (bus = {@code EventBusSubscriber.Bus.MOD}), paired with
 * {@code net.neoforged.bus.api.SubscribeEvent} on the handler method.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(), new ModBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(output, existingFileHelper));
        ModBlockTagsProvider blockTags =
                new ModBlockTagsProvider(output, lookupProvider, existingFileHelper);
        generator.addProvider(event.includeServer(), blockTags);
        // Item tags depend on the block tag provider's contents future (ItemTagsProvider can
        // mirror a block tag), so the block provider has to be constructed first.
        generator.addProvider(event.includeServer(), new ModItemTagsProvider(output, lookupProvider,
                blockTags.contentsGetter(), existingFileHelper));
        generator.addProvider(event.includeServer(), new ModLootTableProvider(output, lookupProvider));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output, lookupProvider));
        generator.addProvider(event.includeServer(),
                new ModAdvancementProvider(output, lookupProvider, existingFileHelper));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output));
        // sounds.json: the mob-voice indirection layer. Client-side asset, like the models
        // and the lang file. See docs/SOUNDS.md.
        generator.addProvider(event.includeClient(),
                new ModSoundDefinitionsProvider(output, existingFileHelper));
    }

    private DataGenerators() {
    }
}
