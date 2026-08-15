package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.WorkerAntModel;
import com.nogal.formicary.entity.TamedWorkerAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The tamed worker reuses the wild worker's baked model -- it is the same animal, just
 * yours. {@code WorkerAntModel} is generic over the entity type, so the layer definition
 * registered for the wild worker bakes for this renderer too and no second mesh is needed.
 *
 * <p>Play-test round 1, spec item 3: the owner wants tamed ants visibly distinguishable
 * from wild ones, so this renderer now points at its own atlas ({@link #TEXTURE}) rather
 * than the wild worker's -- same geometry/UV layout, painted with a gold antenna-tip
 * marker in place of the wild amber. See {@code assets-src/models.py}'s
 * {@code TAMED_ANTENNA_TIP}.
 */
public class TamedWorkerAntRenderer
        extends MobRenderer<TamedWorkerAntEntity, WorkerAntModel<TamedWorkerAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/tamed_worker_ant.png");

    public TamedWorkerAntRenderer(EntityRendererProvider.Context context) {
        super(context, new WorkerAntModel<>(context.bakeLayer(WorkerAntModel.LAYER_LOCATION)), 0.4F);
        this.addLayer(new WorkerAntCarriedItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(TamedWorkerAntEntity entity) {
        return TEXTURE;
    }
}
