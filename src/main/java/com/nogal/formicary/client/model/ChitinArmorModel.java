package com.nogal.formicary.client.model;

import com.nogal.formicary.Formicary;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Chitin Helmet's armor model: the vanilla outer-armor humanoid plus two
 * elbowed antennae parented to the head.
 *
 * <p>Round 7 of the Ep2 play-test revisions (2026-08-21). Logan asked for "the
 * helmet to have antennae"; antennae cannot be painted. Armor layers are flat
 * textures stretched over the vanilla {@link HumanoidModel} boxes, so a painted
 * antenna is a squiggle on the surface of the head cube. Real ones need real
 * geometry, which is what this class adds.
 *
 * <p><b>How it gets used.</b> {@code HumanoidArmorLayer#renderArmorPiece} calls
 * {@code ClientHooks.getArmorModel(...)}, which routes to
 * {@code IClientItemExtensions#getGenericArmorModel}, whose default body calls
 * {@code getHumanoidArmorModel(LivingEntity, ItemStack, EquipmentSlot,
 * HumanoidModel)} and -- crucially -- then calls
 * {@code ClientHooks.copyModelProperties(original, replacement)} for us. That
 * copies the parent model's part poses AND the per-slot visibility flags the
 * layer had already set on the original, so overriding the humanoid hook alone
 * is enough; see {@code ChitinArmorClientExtensions}. Every signature above was
 * re-extracted from {@code neoforge-21.0.167-sources.jar} into {@code
 * reference/} before this was written (the class was missing from the partial
 * extraction -- see {@code docs/gotchas/reference-extraction.md}), not recalled.
 *
 * <p><b>Helmet only.</b> The extension is registered on {@code CHITIN_HELMET}
 * and nothing else. The other three pieces need no extra geometry, and handing
 * them this model would actively break them: {@code
 * HumanoidArmorLayer#usesInnerModel} is true for {@code EquipmentSlot.LEGS}, so
 * the leggings are drawn with the INNER model, baked at {@code
 * LayerDefinitions.INNER_ARMOR_DEFORMATION} (0.5F). This layer is baked at
 * OUTER (1.0F), so returning it for the legs would visibly fatten them. Slots
 * we do not register simply keep vanilla's own {@code HumanoidArmorModel}.
 *
 * <p><b>UV.</b> The antenna cubes live in the same 64x32 armor texture the rest
 * of the helmet reads, in the one region no humanoid box touches. The constants
 * below are one source of truth in two files: {@code assets-src/blocks.py}
 * declares them as {@code ANTENNA_SCAPE_UV} / {@code ANTENNA_FUNICLE_UV},
 * asserts the region really is free against a table of every humanoid rect, and
 * paints them. Change one side and you must change the other.
 *
 * <p>1.21 rules this file obeys (see CLAUDE.md "Entity models"):
 * {@code ResourceLocation.fromNamespaceAndPath}, never {@code new
 * ResourceLocation}; and {@code LayerDefinition.create(mesh, 64, 32)} matches
 * the painted armor texture's resolution. Nothing here overrides {@code
 * setupAnim} -- the armor layer poses this model by copying the parent's part
 * rotations, so the antennae inherit head yaw and pitch for free by being
 * children of the head part.
 */
public class ChitinArmorModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Formicary.MODID, "chitin_armor"), "main");

    /** Vanilla's {@code LayerDefinitions.OUTER_ARMOR_DEFORMATION}, matched exactly. */
    private static final CubeDeformation OUTER_ARMOR = new CubeDeformation(1.0F);

    // --- Antenna UV. Mirrors ANTENNA_SCAPE_UV / ANTENNA_SCAPE_LEN and
    // --- ANTENNA_FUNICLE_UV / ANTENNA_FUNICLE_LEN in assets-src/blocks.py.
    private static final int SCAPE_U = 56;
    private static final int SCAPE_V = 16;
    private static final float SCAPE_LEN = 5.0F;
    private static final int FUNICLE_U = 56;
    private static final int FUNICLE_V = 22;
    private static final float FUNICLE_LEN = 4.0F;

    // --- Antenna pose. The head cube spans y -8..0 (MC model space is +Y DOWN),
    // --- z -4..4 front to back, so the scape roots just inside the helmet's
    // --- front-top corner. A NEGATIVE xRot sweeps a part's far end toward +Z,
    // --- which is backwards -- the same sign convention HumanoidModel uses when
    // --- it writes head.xRot from headPitch. The funicle's positive xRot bends
    // --- the elbow back toward vertical, which is what makes the pair read as an
    // --- ant's elbowed antenna rather than two straight spikes.
    // SCAPE_Y sits just inside the INFLATED helmet shell, not the bare head
    // cube: the outer armor deformation grows the head box by 1 on every side,
    // so its top surface is at y = -9, and rooting the scape at the bare cube's
    // -8 would bury a third of it inside the helmet before it ever emerged.
    private static final float SCAPE_X = 2.0F;
    private static final float SCAPE_Y = -8.5F;
    private static final float SCAPE_Z = -3.0F;
    private static final float SCAPE_X_ROT = -0.9F;
    private static final float SCAPE_Z_ROT = 0.25F;
    private static final float FUNICLE_X_ROT = 0.5F;

    public ChitinArmorModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidArmorModel.createBodyLayer(OUTER_ARMOR);
        PartDefinition head = mesh.getRoot().getChild("head");
        addAntenna(head, "right_antenna", -1.0F);
        addAntenna(head, "left_antenna", 1.0F);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /**
     * One antenna: a scape parented to the head and a funicle parented to the
     * scape's far end, so the whole thing folds with head rotation.
     *
     * <p>Both cubes are built with {@link CubeDeformation#NONE}, NOT the outer
     * armor's 1.0F. Inflating a 1x1 stick by 1 on every side would turn it into
     * a 3x3 post; the deformation exists to lift the armor clear of the player's
     * skin, and geometry that is not sitting on the skin does not want it.
     *
     * @param side -1 for the right antenna, +1 for the left (which mirrors the
     *             cube so its texture is not painted back to front).
     */
    private static void addAntenna(PartDefinition head, String name, float side) {
        boolean mirror = side > 0.0F;
        PartDefinition scape = head.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(SCAPE_U, SCAPE_V).mirror(mirror)
                        .addBox(-0.5F, -SCAPE_LEN, -0.5F, 1.0F, SCAPE_LEN, 1.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(side * SCAPE_X, SCAPE_Y, SCAPE_Z,
                        SCAPE_X_ROT, 0.0F, side * SCAPE_Z_ROT));
        scape.addOrReplaceChild(
                name + "_funicle",
                CubeListBuilder.create().texOffs(FUNICLE_U, FUNICLE_V).mirror(mirror)
                        .addBox(-0.5F, -FUNICLE_LEN, -0.5F, 1.0F, FUNICLE_LEN, 1.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.0F, -SCAPE_LEN, 0.0F,
                        FUNICLE_X_ROT, 0.0F, 0.0F));
    }
}
