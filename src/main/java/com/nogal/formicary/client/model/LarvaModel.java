package com.nogal.formicary.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nogal.formicary.Formicary;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Larva: a tiny legless, antenna-less grub -- three tapering segments (head, mid,
 * tail) that wriggle side to side in place.
 *
 * <p>Hand-translated from {@code assets-src/models.py}'s {@code LARVA} spec -- that
 * python spec is the source of truth for every number below and it paints the
 * matching 32x16 atlas. Keep the two in step: change the spec, re-run it, then mirror
 * the change here.
 *
 * <p>{@code mid} is the root; {@code head} and {@code tail} are its children so
 * {@link #setupAnim} can flex them independently for the wriggle. See the same 1.21
 * rules documented on {@link WorkerAntModel} (absolute rest poses every frame, etc).
 */
public class LarvaModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "larva"), "main");

    private final ModelPart mid;
    private final ModelPart head;
    private final ModelPart tail;

    public LarvaModel(ModelPart root) {
        this.mid = root.getChild("mid");
        this.head = this.mid.getChild("head");
        this.tail = this.mid.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition mid = root.addOrReplaceChild("mid",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -1.5F, -1.5F, 4.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 22.5F, 0.0F));

        mid.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 6).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -1.5F));

        mid.addOrReplaceChild("tail",
                CubeListBuilder.create()
                        .texOffs(10, 6).addBox(-1.5F, -1.0F, 0.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.5F, 1.5F));

        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // Slow sinusoidal side-to-side flex, stronger while moving. Written absolutely
        // every frame -- nothing resets a plain EntityModel's parts between frames.
        float amount = 1.0F + limbSwingAmount * 2.0F;
        this.head.yRot = Mth.sin(ageInTicks * 0.1F) * 0.18F * amount;
        this.tail.yRot = Mth.sin(ageInTicks * 0.1F + (float) Math.PI * 0.6F) * 0.14F * amount;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color) {
        this.mid.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
