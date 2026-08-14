package com.nogal.formicary.entity;

import com.nogal.formicary.item.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Fungal Bloom's ant-feed use (spec section 5/8, M8): right-clicking a tamed worker
 * or soldier with a Fungal Bloom item heals it a little, consuming the item.
 *
 * <p>Shared between {@link TamedWorkerAntEntity} and {@link TamedSoldierAntEntity} rather
 * than duplicated -- both extend {@code TamableAnimal} but not a common ant base class, so
 * a static helper called from each {@code mobInteract} is the seam. No ownership check,
 * matching vanilla's own tamed-animal feeding (anyone holding meat can heal a wolf).
 */
public final class FungalBloomFeeding {
    /** Spec: "heals it ~4 HP". */
    public static final float HEAL_AMOUNT = 4.0F;

    /**
     * Feeds {@code ant} from {@code player}'s {@code hand} if it is holding a Fungal
     * Bloom and the ant is not already at full health.
     *
     * @return {@code true} if the interaction was handled (the item matched), regardless
     *         of whether healing actually happened -- a full-health ant still "eats" the
     *         click so it does not fall through to an unrelated interaction, but per spec
     *         ("no-op at full health") nothing is healed, consumed, or played
     */
    public static boolean tryFeed(TamableAnimal ant, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.FUNGAL_BLOOM.get())) {
            return false;
        }
        if (ant.getHealth() >= ant.getMaxHealth()) {
            return true;
        }
        if (!ant.level().isClientSide && ant.level() instanceof ServerLevel level) {
            ant.heal(HEAL_AMOUNT);
            held.consume(1, player);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    ant.getX(), ant.getY() + ant.getBbHeight() * 0.5, ant.getZ(),
                    6, 0.3, 0.3, 0.3, 0.02);
            ant.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.2F);
        }
        return true;
    }

    private FungalBloomFeeding() {
    }
}
