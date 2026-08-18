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
 * Ender ant: the worker's body plan on longer legs, in near-black chitin with purple
 * accents (spec section 5, "ant body plan, near-black chitin + purple accents").
 *
 * <p>Hand-translated from {@code assets-src/models.py}'s {@code ENDER_ANT} spec -- that
 * python spec is the source of truth for every number below and it paints the matching
 * 64x64 atlas. Keep the two in step: change the spec, re-run it, then mirror the change
 * here.
 *
 * <p>The only geometry difference from {@link WorkerAntModel} is the legs: 4 units long
 * instead of 3, with the body root lifted 0.5 (20.0 rather than 20.5) so the feet still
 * reach the ground. Every <em>parent-relative</em> offset is therefore identical to the
 * worker's -- the spec shifts the leg poses by the same 0.5 as the body -- which is why
 * this file looks like a copy with two numbers changed. It is deliberately a sibling class
 * rather than a subclass: {@code SoldierAntModel} set that precedent, and the models here
 * are hand translations of a python spec, so a class that inherited half its numbers from
 * another mob's translation would have no single spec to check against.
 *
 * <p>Same 1.21 rules as {@link WorkerAntModel} (see {@code docs/gotchas/entity-models.md}):
 * int-colour {@code renderToBuffer}, {@code ResourceLocation.fromNamespaceAndPath},
 * {@code LayerDefinition.create(mesh, 64, 64)} matching the painted atlas, and rest poses
 * written absolutely every frame in {@link #setupAnim}.
 */
public class EnderAntModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "ender_ant"), "main");

    /** Rest rotations emitted by {@link #createBodyLayer()}; right side, mirrored for left. */
    private static final float REST_LEG_Z = 0.8378F;
    private static final float REST_LEG_Y_FRONT = -0.5236F;
    private static final float REST_LEG_Y_HIND = 0.5236F;
    private static final float REST_ANTENNA_X = 0.5236F;
    private static final float REST_ANTENNA_Z = 0.2618F;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private final ModelPart body;
    public final ModelPart head;
    private final ModelPart antennaRight;
    private final ModelPart antennaLeft;
    private final ModelPart gaster;
    private final ModelPart[] legs;

    private static final float[] LEG_REST_Y = {
            REST_LEG_Y_FRONT, 0.0F, REST_LEG_Y_HIND,
            -REST_LEG_Y_FRONT, 0.0F, -REST_LEG_Y_HIND
    };
    private static final float[] LEG_REST_Z = {
            REST_LEG_Z, REST_LEG_Z, REST_LEG_Z,
            -REST_LEG_Z, -REST_LEG_Z, -REST_LEG_Z
    };
    /** +1 for the right-side legs, -1 for the left, so the lift mirrors correctly. */
    private static final float[] LEG_SIDE = { 1.0F, 1.0F, 1.0F, -1.0F, -1.0F, -1.0F };
    /** Alternating-tripod gait, the same one the worker walks. */
    private static final float[] LEG_PHASE = {
            0.0F, (float) Math.PI, 0.0F,
            (float) Math.PI, 0.0F, (float) Math.PI
    };

    public EnderAntModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.antennaRight = this.head.getChild("antenna_r");
        this.antennaLeft = this.head.getChild("antenna_l");
        this.gaster = this.body.getChild("gaster");
        this.legs = new ModelPart[] {
                this.body.getChild("leg_r1"),
                this.body.getChild("leg_r2"),
                this.body.getChild("leg_r3"),
                this.body.getChild("leg_l1"),
                this.body.getChild("leg_l2"),
                this.body.getChild("leg_l3")
        };
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 20.0, not the worker's 20.5: the extra leg unit lifts the whole body half a unit.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 20.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 19).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(34, 0).addBox(-2.0F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(34, 0).mirror().addBox(1.0F, 0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(0.0F, -0.5F, -2.0F));

        head.addOrReplaceChild("antenna_r",
                CubeListBuilder.create()
                        .texOffs(42, 0).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.5F, -1.5F, -3.5F, REST_ANTENNA_X, 0.0F, -REST_ANTENNA_Z));

        head.addOrReplaceChild("antenna_l",
                CubeListBuilder.create()
                        .texOffs(42, 0).mirror().addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(1.5F, -1.5F, -3.5F, REST_ANTENNA_X, 0.0F, REST_ANTENNA_Z));

        body.addOrReplaceChild("gaster",
                CubeListBuilder.create()
                        .texOffs(24, 0).addBox(-1.0F, 0.5F, 1.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 8).addBox(-2.5F, -1.5F, 3.5F, 5.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.0F, 0.0F));

        // 1x4x1 rather than the worker's 1x3x1 -- "slightly taller legs".
        CubeListBuilder legRight = CubeListBuilder.create()
                .texOffs(48, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F));
        CubeListBuilder legLeft = CubeListBuilder.create()
                .texOffs(48, 0).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false);

        body.addOrReplaceChild("leg_r1", legRight,
                PartPose.offsetAndRotation(-2.0F, 1.4F, -1.5F, 0.0F, REST_LEG_Y_FRONT, REST_LEG_Z));
        body.addOrReplaceChild("leg_r2", legRight,
                PartPose.offsetAndRotation(-2.0F, 1.4F, 0.0F, 0.0F, 0.0F, REST_LEG_Z));
        body.addOrReplaceChild("leg_r3", legRight,
                PartPose.offsetAndRotation(-2.0F, 1.4F, 1.5F, 0.0F, REST_LEG_Y_HIND, REST_LEG_Z));
        body.addOrReplaceChild("leg_l1", legLeft,
                PartPose.offsetAndRotation(2.0F, 1.4F, -1.5F, 0.0F, -REST_LEG_Y_FRONT, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l2", legLeft,
                PartPose.offsetAndRotation(2.0F, 1.4F, 0.0F, 0.0F, 0.0F, -REST_LEG_Z));
        body.addOrReplaceChild("leg_l3", legLeft,
                PartPose.offsetAndRotation(2.0F, 1.4F, 1.5F, 0.0F, -REST_LEG_Y_HIND, -REST_LEG_Z));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        // Every rotation below is written absolutely, never read back and adjusted.
        this.head.yRot = netHeadYaw * DEG_TO_RAD;
        this.head.xRot = headPitch * DEG_TO_RAD;

        // Idle antenna bob -- two slightly different periods so they never look locked.
        float bob = Mth.cos(ageInTicks * 0.12F) * 0.13F;
        float sway = Mth.sin(ageInTicks * 0.09F) * 0.07F;
        this.antennaRight.xRot = REST_ANTENNA_X + bob;
        this.antennaRight.zRot = -REST_ANTENNA_Z - sway;
        this.antennaLeft.xRot = REST_ANTENNA_X - bob;
        this.antennaLeft.zRot = REST_ANTENNA_Z + sway;

        // The gaster counter-swings a little as the ant walks.
        this.gaster.yRot = Mth.cos(limbSwing * 0.7F) * 0.10F * limbSwingAmount;

        for (int i = 0; i < this.legs.length; i++) {
            // 1.55 against the worker's 1.4: same scuttle, a touch faster. The stride
            // amplitude is unchanged, so the longer legs cover more ground per step rather
            // than sawing harder -- which is what makes it read as quicker than a worker
            // without also reading as panicked.
            float cycle = limbSwing * 1.55F + LEG_PHASE[i];
            this.legs[i].xRot = Mth.cos(cycle) * limbSwingAmount * 0.55F;
            this.legs[i].yRot = LEG_REST_Y[i];
            this.legs[i].zRot = LEG_REST_Z[i] + LEG_SIDE[i] * Mth.sin(cycle) * limbSwingAmount * 0.18F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color) {
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
