package com.nogal.formicary.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.SoldierAntModel;
import com.nogal.formicary.entity.TamedSoldierAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The tamed soldier reuses the wild soldier's baked model, but NOT its texture any more
 * -- see {@link #TEXTURE} and the class javadoc on {@link TamedWorkerAntRenderer} for why
 * a second layer definition still isn't needed even though the atlas now differs.
 */
public class TamedSoldierAntRenderer
        extends MobRenderer<TamedSoldierAntEntity, SoldierAntModel<TamedSoldierAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/tamed_soldier_ant.png");

    public TamedSoldierAntRenderer(EntityRendererProvider.Context context) {
        super(context, new SoldierAntModel<>(context.bakeLayer(SoldierAntModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TamedSoldierAntEntity entity) {
        return TEXTURE;
    }

    /** Matches {@link SoldierAntRenderer#scale} -- "tamed soldier should match." */
    @Override
    protected void scale(TamedSoldierAntEntity entity, PoseStack poseStack, float partialTickTime) {
        float s = SoldierAntModel.RENDER_SCALE;
        poseStack.scale(s, s, s);
    }
}
