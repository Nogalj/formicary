package com.nogal.formicary.item;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.effect.ModPotions;
import com.nogal.formicary.entity.ModEntities;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
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
                        output.accept(ModItems.ROYAL_JELLY.get());
                        output.accept(ModItems.SCENT_GLAND.get());
                        output.accept(ModItems.TRAIL_PHEROMONE.get());
                        output.accept(ModItems.ROYAL_PHEROMONE_GLAND.get());
                        output.accept(ModItems.PHEROMONE_HORN.get());
                        output.accept(ModItems.CHITIN_HELMET.get());
                        output.accept(ModItems.CHITIN_CHESTPLATE.get());
                        output.accept(ModItems.CHITIN_LEGGINGS.get());
                        output.accept(ModItems.CHITIN_BOOTS.get());
                        // Play-test round 1: the Pheromonal Disguise potion was brewable but
                        // absent from this tab -- PotionContents.createItemStack(Item, Holder
                        // <Potion>) is the same helper vanilla's own CreativeModeTabs uses to
                        // generate its potion rows (verified in reference/CreativeModeTabs.java's
                        // generatePotionEffectTypes), so a bare Items.POTION/SPLASH_POTION/
                        // LINGERING_POTION carrying our potion's PotionContents is a real,
                        // drinkable stack, not a decorative icon.
                        output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.PHEROMONAL_DISGUISE));
                        output.accept(
                                PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.PHEROMONAL_DISGUISE));
                        output.accept(PotionContents.createItemStack(Items.LINGERING_POTION,
                                ModPotions.PHEROMONAL_DISGUISE));
                        output.accept(ModEntities.WORKER_ANT_SPAWN_EGG.get());
                        output.accept(ModEntities.SOLDIER_ANT_SPAWN_EGG.get());
                        output.accept(ModEntities.LARVA_SPAWN_EGG.get());
                        output.accept(ModEntities.QUEEN_ANT_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
