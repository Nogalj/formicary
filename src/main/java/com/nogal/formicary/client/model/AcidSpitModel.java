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
import net.minecraft.world.entity.Entity;

/**
 * The queen's acid spit (Ep2, task F2): a 4-cube core with four 2-cube buds budding off
 * its sides, so a glob in flight reads as splattering rather than as a tidy box.
 *
 * <p>Hand-translated from {@code assets-src/models.py}'s {@code ACID_SPIT} spec, which is
 * the source of truth for every number below and paints the matching 32x16 atlas.
 *
 * <p>All five cubes live on one part, unlike every mob model here. Nothing about a flying
 * blob articulates, and {@link #setupAnim} is empty for the same reason -- the only motion
 * it has is the yaw/pitch its renderer reads off the entity's own flight path.
 *
 * <p>The shape is symmetric in Y on purpose. {@code AcidSpitRenderer} extends
 * {@code EntityRenderer}, not {@code LivingEntityRenderer}, so it never applies the
 * {@code scale(-1, -1, 1)} flip every mob renderer does; a model identical top and bottom
 * comes out the same either way, which is what lets the python preview and the game agree
 * without the spec having to pick a handedness it cannot honour. Same source as
 * {@code LlamaSpitModel}, which is symmetric for exactly this reason.
 */
public class AcidSpitModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "acid_spit"), "main");

    private final ModelPart blob;

    public AcidSpitModel(ModelPart root) {
        this.blob = root.getChild("blob");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("blob",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 0).addBox(-4.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 0).addBox(2.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 0).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 0).addBox(-1.0F, -1.0F, 2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color) {
        this.blob.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
