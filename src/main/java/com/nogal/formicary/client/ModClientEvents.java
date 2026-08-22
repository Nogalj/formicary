package com.nogal.formicary.client;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.AcidSpitModel;
import com.nogal.formicary.client.model.ChitinArmorModel;
import com.nogal.formicary.client.model.EnderAntModel;
import com.nogal.formicary.client.model.LarvaModel;
import com.nogal.formicary.client.model.QueenAntModel;
import com.nogal.formicary.client.model.SoldierAntModel;
import com.nogal.formicary.client.model.WorkerAntModel;
import com.nogal.formicary.client.renderer.AcidSpitRenderer;
import com.nogal.formicary.client.renderer.EnderAntRenderer;
import com.nogal.formicary.client.renderer.LarvaRenderer;
import com.nogal.formicary.client.renderer.QueenAntRenderer;
import com.nogal.formicary.client.renderer.SoldierAntRenderer;
import com.nogal.formicary.client.renderer.TamedSoldierAntRenderer;
import com.nogal.formicary.client.renderer.TamedWorkerAntRenderer;
import com.nogal.formicary.client.renderer.WorkerAntRenderer;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.item.ModItems;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only mod-bus handlers: bake the entity layer definitions, bind renderers,
 * and register per-item client extensions.
 * Gated with {@code value = Dist.CLIENT} so none of this is loaded on a dedicated server.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WorkerAntModel.LAYER_LOCATION, WorkerAntModel::createBodyLayer);
        event.registerLayerDefinition(SoldierAntModel.LAYER_LOCATION, SoldierAntModel::createBodyLayer);
        event.registerLayerDefinition(LarvaModel.LAYER_LOCATION, LarvaModel::createBodyLayer);
        event.registerLayerDefinition(QueenAntModel.LAYER_LOCATION, QueenAntModel::createBodyLayer);
        event.registerLayerDefinition(EnderAntModel.LAYER_LOCATION, EnderAntModel::createBodyLayer);
        event.registerLayerDefinition(AcidSpitModel.LAYER_LOCATION, AcidSpitModel::createBodyLayer);
        event.registerLayerDefinition(ChitinArmorModel.LAYER_LOCATION, ChitinArmorModel::createBodyLayer);
    }

    /**
     * Bind the Chitin Helmet's custom armor model (round 7: the antennae).
     * {@code RegisterClientExtensionsEvent} is a mod-bus, client-logical-side
     * event -- verified in
     * {@code reference/net/neoforged/neoforge/client/extensions/common/RegisterClientExtensionsEvent.java}
     * after re-extracting it from the NeoForge 21.0.167 sources jar. Helmet only:
     * see {@link ChitinArmorModel} for why the other three slots keep vanilla's
     * model.
     */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(ChitinArmorClientExtensions.INSTANCE, ModItems.CHITIN_HELMET.get());
    }

    /**
     * Bake (and, on every resource reload, re-bake) the helmet model. This fires
     * whenever the {@code EntityModelSet} behind it is rebuilt, which is why the
     * extension caches a model rather than baking one per frame.
     */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ChitinArmorClientExtensions.INSTANCE.rebake(event.getEntityModels());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WORKER_ANT.get(), WorkerAntRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLDIER_ANT.get(), SoldierAntRenderer::new);
        event.registerEntityRenderer(ModEntities.LARVA.get(), LarvaRenderer::new);
        event.registerEntityRenderer(ModEntities.TAMED_WORKER_ANT.get(), TamedWorkerAntRenderer::new);
        event.registerEntityRenderer(ModEntities.TAMED_SOLDIER_ANT.get(), TamedSoldierAntRenderer::new);
        event.registerEntityRenderer(ModEntities.QUEEN_ANT.get(), QueenAntRenderer::new);
        event.registerEntityRenderer(ModEntities.ENDER_ANT.get(), EnderAntRenderer::new);
        event.registerEntityRenderer(ModEntities.ACID_SPIT.get(), AcidSpitRenderer::new);
    }

    private ModClientEvents() {
    }
}
