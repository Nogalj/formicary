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
 * Worker ant: head (with mandibles and two antennae), thorax, petiole waist and
 * gaster, on six splayed legs.
 *
 * <p>Hand-translated from {@code assets-src/models.py} -- that python spec is the
 * source of truth for every number below and it paints the matching 64x64 atlas.
 * Keep the two in step: change the spec, re-run it, then mirror the change here.
 *
 * <p>1.21 rules this file obeys (see CLAUDE.md "Entity models"):
 * <ul>
 *   <li>{@code renderToBuffer} takes a single int colour, not four floats.</li>
 *   <li>{@code ResourceLocation.fromNamespaceAndPath}, never {@code new ResourceLocation}.</li>
 *   <li>{@code LayerDefinition.create(mesh, 64, 64)} matches the painted atlas.</li>
 *   <li>{@code setupAnim} writes every rest pose <em>absolutely</em> each frame --
 *       nothing resets a plain {@link EntityModel}'s parts between frames, so
 *       reading a part's current rotation back would accumulate drift.</li>
 * </ul>
 */
public class WorkerAntModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "worker_ant"), "main");

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
    /** Rest yaw per entry of {@link #legs}, in the same order. */
    private static final float[] LEG_REST_Y = {
            REST_LEG_Y_FRONT, 0.0F, REST_LEG_Y_HIND,
            -REST_LEG_Y_FRONT, 0.0F, -REST_LEG_Y_HIND
    };
    /** Rest splay per entry of {@link #legs}: right legs lean -X, left legs +X. */
    private static final float[] LEG_REST_Z = {
            REST_LEG_Z, REST_LEG_Z, REST_LEG_Z,
            -REST_LEG_Z, -REST_LEG_Z, -REST_LEG_Z
    };
    /** +1 for the right-side legs, -1 for the left, so the lift mirrors correctly. */
    private static final float[] LEG_SIDE = { 1.0F, 1.0F, 1.0F, -1.0F, -1.0F, -1.0F };
    /**
     * Alternating-tripod gait: {right-front, right-hind, left-mid} step together,
     * the other three are half a cycle out of phase.
     */
    private static final float[] LEG_PHASE = {
            0.0F, (float) Math.PI, 0.0F,
            (float) Math.PI, 0.0F, (float) Math.PI
    };

    public WorkerAntModel(ModelPart root) {
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

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 20.5F, 0.0F));

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

        CubeListBuilder legRight = CubeListBuilder.create()
                .texOffs(48, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F));
        CubeListBuilder legLeft = CubeListBuilder.create()
                .texOffs(48, 0).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false);

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
            // 1.4 rather than the quadruped-standard 0.6662: ants scuttle -- many
            // quick steps -- and at the old rate the legs visibly lagged the ground
            // speed (Logan's 2026-08-13 review).
            float cycle = limbSwing * 1.4F + LEG_PHASE[i];
            // cos drives the fore/aft stride, sin the lift a quarter-cycle later,
            // so each foot traces a circle rather than sawing back and forth.
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
