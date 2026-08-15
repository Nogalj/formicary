package com.nogal.formicary.gametest;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.SoldierAntEntity;
import com.nogal.formicary.entity.TamedSoldierAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless coverage for the play-test round 1 entity-visuals package. Model geometry and
 * texture art (the queen's mandibles, the tamed antenna-tip markers, the larva UV fix)
 * have no headless surface -- they're verified by regenerating {@code assets-src/models.py}
 * and reading the contact sheets, per that package's own instructions. The one part of
 * this package the GameTest harness CAN see is the soldier's enlarged hitbox, which is a
 * real gameplay value (collision, spawn placement) and not just a render-time effect.
 */
@GameTestHolder(Formicary.MODID)
public class EntityVisualsGameTests {
    private static final int STAND_Y = 2;

    /**
     * Play-test round 1, spec item 1: "resize the EntityType hitbox to match" the ~1.3x
     * render scale, "tamed soldier should match." Pins both {@code ModEntities.SOLDIER_ANT}
     * and {@code TAMED_SOLDIER_ANT} to the same 1.43x1.04 dimensions (1.1x0.8 scaled by
     * {@link com.nogal.formicary.client.model.SoldierAntModel#RENDER_SCALE}) so the two
     * can't quietly drift apart from each other or from the renderer's scale factor.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "platform")
    public static void soldier_and_tamed_soldier_share_the_enlarged_hitbox(GameTestHelper helper) {
        SoldierAntEntity wild = helper.spawn(ModEntities.SOLDIER_ANT.get(), new BlockPos(1, STAND_Y, 1));
        TamedSoldierAntEntity tamed = helper.spawn(ModEntities.TAMED_SOLDIER_ANT.get(),
                new BlockPos(3, STAND_Y, 3));

        helper.assertValueEqual(wild.getBbWidth(), 1.43F, "wild soldier hitbox width");
        helper.assertValueEqual(wild.getBbHeight(), 1.04F, "wild soldier hitbox height");
        helper.assertValueEqual(tamed.getBbWidth(), 1.43F, "tamed soldier hitbox width");
        helper.assertValueEqual(tamed.getBbHeight(), 1.04F, "tamed soldier hitbox height");
        helper.assertValueEqual(wild.getBbWidth(), tamed.getBbWidth(),
                "wild and tamed soldiers must share the same hitbox width");
        helper.assertValueEqual(wild.getBbHeight(), tamed.getBbHeight(),
                "wild and tamed soldiers must share the same hitbox height");
        helper.succeed();
    }
}
