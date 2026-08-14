package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.WorkerAntModel;
import com.nogal.formicary.entity.TamedWorkerAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The tamed worker reuses the wild worker's baked model and texture -- it is the same
 * animal, just yours. {@code WorkerAntModel} is generic over the entity type, so the layer
 * definition registered for the wild worker bakes for this renderer too and no second mesh
 * or atlas is needed.
 */
public class TamedWorkerAntRenderer
        extends MobRenderer<TamedWorkerAntEntity, WorkerAntModel<TamedWorkerAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/worker_ant.png");

    public TamedWorkerAntRenderer(EntityRendererProvider.Context context) {
        super(context, new WorkerAntModel<>(context.bakeLayer(WorkerAntModel.LAYER_LOCATION)), 0.4F);
        this.addLayer(new WorkerAntCarriedItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(TamedWorkerAntEntity entity) {
        return TEXTURE;
    }
}
