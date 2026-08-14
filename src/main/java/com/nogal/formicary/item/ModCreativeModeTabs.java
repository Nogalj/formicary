package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.entity.ModEntities;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A single creative tab ("Formicary") holding every M1 block item + the resin item.
 * Icon is Anthill Core per spec.
 */
public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Formicary.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FORMICARY_TAB = CREATIVE_MODE_TABS.register(
            "formicary",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.formicary.formicary"))
                    .icon(() -> ModBlocks.ANTHILL_CORE.toStack())
                    .displayItems((parameters, output) -> {
                        ModBlocks.BLOCKS.getEntries().forEach(entry -> output.accept(entry.get()));
                        output.accept(ModItems.RESIN.get());
                        output.accept(ModItems.CHITIN.get());
                        output.accept(ModItems.LARVA.get());
                        output.accept(ModItems.SCENT_GLAND.get());
                        output.accept(ModItems.TRAIL_PHEROMONE.get());
                        output.accept(ModItems.CHITIN_HELMET.get());
                        output.accept(ModItems.CHITIN_CHESTPLATE.get());
                        output.accept(ModItems.CHITIN_LEGGINGS.get());
                        output.accept(ModItems.CHITIN_BOOTS.get());
                        output.accept(ModEntities.WORKER_ANT_SPAWN_EGG.get());
                        output.accept(ModEntities.SOLDIER_ANT_SPAWN_EGG.get());
                        output.accept(ModEntities.LARVA_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
