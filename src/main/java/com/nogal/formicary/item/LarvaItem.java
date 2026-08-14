package com.nogal.formicary.item;

import com.nogal.formicary.entity.LarvaEntity;
import com.nogal.formicary.entity.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The captured larva, and the first step of the taming loop (spec section 4): "in the
 * overworld, place the larva".
 *
 * <p>Placing it spawns the wild {@code LarvaEntity} carrying its
 * {@link LarvaEntity#isPlaced() placed} flag, which is what stops it wandering off and what
 * makes it feedable. Everything after that -- the diet fork, the ownership -- is an entity
 * interaction, so this item is only ever responsible for putting the grub on the ground.
 */
public class LarvaItem extends Item {
    public LarvaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos placeAt = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(placeAt).isAir()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }

        LarvaEntity larva = ModEntities.LARVA.get().create(serverLevel);
        if (larva == null) {
            return InteractionResult.FAIL;
        }
        larva.moveTo(placeAt.getX() + 0.5, placeAt.getY(), placeAt.getZ() + 0.5,
                serverLevel.getRandom().nextFloat() * 360.0F, 0.0F);
        larva.setPlaced(true);
        // A grub you deliberately set down should still be there tomorrow, whatever the
        // mob cap has been doing in the meantime.
        larva.setPersistenceRequired();
        serverLevel.addFreshEntity(larva);

        Player player = context.getPlayer();
        serverLevel.playSound(null, placeAt, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.6F, 1.3F);
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }
}
