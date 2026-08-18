package com.nogal.formicary.client.renderer;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.EnderAntModel;
import com.nogal.formicary.entity.EnderAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Straight {@code MobRenderer} on the worker's pattern -- no extra {@code RenderLayer}.
 *
 * <p>The worker's one layer ({@code WorkerAntCarriedItemLayer}) exists because a worker
 * relocates item drops and a plain {@code MobRenderer} draws nothing for a mob's main hand;
 * an ender ant carries nothing. Its purple is painted into the atlas rather than added as
 * an emissive eyes layer on {@code EnderMan}'s pattern, which would need a second texture
 * and a second thing to keep in step with {@code assets-src/models.py} for an effect the
 * spec does not ask for (it asks for portal particles on teleport, which
 * {@code EnderAntEntity} sends server-side).
 */
public class EnderAntRenderer extends MobRenderer<EnderAntEntity, EnderAntModel<EnderAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/ender_ant.png");

    public EnderAntRenderer(EntityRendererProvider.Context context) {
        super(context, new EnderAntModel<>(context.bakeLayer(EnderAntModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(EnderAntEntity entity) {
        return TEXTURE;
    }
}
