package com.nogal.formicary;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.effect.ModMobEffects;
import com.nogal.formicary.effect.ModPotions;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.item.ModArmorMaterials;
import com.nogal.formicary.item.ModCreativeModeTabs;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.loot.ModLootConditions;
import com.nogal.formicary.portal.ModAttachments;
import com.nogal.formicary.worldgen.ModWorldgen;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(Formicary.MODID)
public class Formicary {

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModEntities.WORKER_ANT_SPAWN_EGG);
            event.accept(ModEntities.SOLDIER_ANT_SPAWN_EGG);
            event.accept(ModEntities.LARVA_SPAWN_EGG);
        }
    }

    public static final String MODID = "formicary";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Formicary(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModLootConditions.LOOT_CONDITION_TYPES.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        ModPotions.POTIONS.register(modEventBus);
        ModWorldgen.CHUNK_GENERATORS.register(modEventBus);
        ModWorldgen.BIOME_SOURCES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModSoundEvents.SOUND_EVENTS.register(modEventBus);
        ModAdvancementTriggers.TRIGGER_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        LOGGER.info(MODID);
    }
}
