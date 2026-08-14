package com.nogal.formicary.item;

import com.nogal.formicary.portal.ModAttachments;
import com.nogal.formicary.portal.TrailPath;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Trail Pheromone (spec section 2): lights up the route the player themselves walked.
 *
 * <p>All the state lives on the player's {@link TrailPath} attachment; this item is just
 * the trigger. It refuses -- without being consumed -- when there is nothing recorded to
 * retrace, which is what happens if it is used outside the colony, since path recording is
 * gated on the dimension.
 */
public class TrailPheromoneItem extends Item {

    public TrailPheromoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            // Client copy: swing the arm and let the server decide. Doing anything else here
            // would be guessing at server-only state (the path attachment is never synced).
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        TrailPath path = serverPlayer.getData(ModAttachments.TRAIL_PATH);
        if (!path.light()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.formicary.trail_pheromone.no_trail"), true);
            return InteractionResultHolder.fail(stack);
        }

        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BEEHIVE_ENTER, SoundSource.PLAYERS,
                0.8F, 1.4F);
        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }
}
