package com.nogal.formicary;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nogal.formicary.advancement.ModCriteriaTriggers;
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
    public static final String MODID = "formicary";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Formicary(IEventBus modEventBus, ModContainer modContainer) {
        // ModEntities' static initialiser adds the spawn egg to ModItems.ITEMS, so it has
        // to be class-loaded before the item registry event fires -- touching ENTITY_TYPES
        // here does that.
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        // Armor materials must be on the bus before the items that hold a Holder of one;
        // ArmorItem only dereferences the holder lazily, but registering it here keeps
        // the dependency obvious.
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModLootConditions.LOOT_CONDITION_TYPES.register(modEventBus);
        // Pheromonal Disguise (M4b): the potion holds a MobEffectInstance of the effect,
        // so the effect registry needs to be on the bus first.
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        ModPotions.POTIONS.register(modEventBus);
        // The dimension JSON names our generator and biome source by type id, so their
        // MapCodecs must be in the registries before any world loads.
        ModWorldgen.CHUNK_GENERATORS.register(modEventBus);
        ModWorldgen.BIOME_SOURCES.register(modEventBus);
        // M5 portal state: the entry anthill a player came in through, and their breadcrumb
        // ring buffer. Attachment types are a real NeoForge registry, so they go on the bus
        // like everything else -- an unregistered type throws on first getData.
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        // M8: the two custom advancement criterion triggers (first harvest, caste grown).
        ModCriteriaTriggers.TRIGGER_TYPES.register(modEventBus);

        modEventBus.addListener(this::addCreative);

        LOGGER.info("Formicary loading -- the colony stirs.");
    }

    /** Puts our spawn eggs in the vanilla Spawn Eggs tab alongside the mod's own tab. */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModEntities.WORKER_ANT_SPAWN_EGG);
            event.accept(ModEntities.SOLDIER_ANT_SPAWN_EGG);
            event.accept(ModEntities.LARVA_SPAWN_EGG);
        }
    }
}
