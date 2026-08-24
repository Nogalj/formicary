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

    /**
     * Play-test round 9: a Pheromone Horn summon wears the tamed soldier's yellow-tipped
     * atlas, so the two allies you called are distinguishable at a glance from the colony
     * soldiers they are fighting -- which is the whole point at the moment you blow it.
     *
     * <p>Same file {@link TamedSoldierAntRenderer} uses. No second layer definition is
     * needed because a horn ally is a {@link SoldierAntEntity} in allied MODE, not a
     * different entity, and the tamed soldier already reuses this exact baked model -- the
     * yellow tips live entirely in the texture.
     */
    private static final ResourceLocation ALLIED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "textures/entity/tamed_soldier_ant.png");

    public SoldierAntRenderer(EntityRendererProvider.Context context) {
        super(context, new SoldierAntModel<>(context.bakeLayer(SoldierAntModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierAntEntity entity) {
        // isAllied() reads a SYNCHED flag, which is the only reason this works here --
        // the summoner UUID it used to be derived from is server-side only.
        return entity.isAllied() ? ALLIED_TEXTURE : TEXTURE;
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
