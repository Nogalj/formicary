package com.nogal.formicary;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.item.ModCreativeModeTabs;
import com.nogal.formicary.item.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Formicary.MODID)
public class Formicary {
    public static final String MODID = "formicary";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Formicary(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        LOGGER.info("Formicary loading -- the colony stirs.");
    }
}
