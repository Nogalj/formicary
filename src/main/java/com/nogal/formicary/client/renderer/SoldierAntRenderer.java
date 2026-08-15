package com.nogal.formicary.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nogal.formicary.Formicary;
import com.nogal.formicary.client.model.SoldierAntModel;
import com.nogal.formicary.entity.SoldierAntEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SoldierAntRenderer extends MobRenderer<SoldierAntEntity, SoldierAntModel<SoldierAntEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/soldier_ant.png");

    public SoldierAntRenderer(EntityRendererProvider.Context context) {
        super(context, new SoldierAntModel<>(context.bakeLayer(SoldierAntModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierAntEntity entity) {
        return TEXTURE;
    }

    /**
     * The vanilla renderer-scale idiom ({@code SlimeRenderer#scale} in {@code reference/}
     * is the verified example) -- a flat render-time scale, independent of the
     * {@code Attributes.SCALE} attribute. See {@link SoldierAntModel#RENDER_SCALE}.
     */
    @Override
    protected void scale(SoldierAntEntity entity, PoseStack poseStack, float partialTickTime) {
        float s = SoldierAntModel.RENDER_SCALE;
        poseStack.scale(s, s, s);
    }
}
