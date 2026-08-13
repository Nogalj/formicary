package com.nogal.formicary.entity;

import com.nogal.formicary.colony.ColonyAnger;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;

/**
 * A worker's panic run (spec section 3: "workers flee when angered").
 *
 * <p>Vanilla's {@link AvoidEntityGoal} already does the pathing -- pick a spot away from
 * the threat, walk, sprint once inside 7 blocks -- so this only adds the gate: it may
 * only run while the worker is in its flee window, and it will only run from the player
 * who actually provoked the colony.
 */
public class FleeOffenderGoal extends AvoidEntityGoal<Player> {
    private final WorkerAntEntity worker;

    public FleeOffenderGoal(WorkerAntEntity worker) {
        // (mob, class, maxDistance, walkSpeed, sprintSpeed, predicateOnAvoidEntity)
        super(worker, Player.class, (float) ColonyAnger.ANGER_RADIUS, 1.2, 1.6,
                living -> living instanceof Player player
                        && !ColonyAnger.isDisguised(player)
                        && worker.isFleeingFrom(player));
        this.worker = worker;
    }

    @Override
    public boolean canUse() {
        return this.worker.isFleeing() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.worker.isFleeing() && super.canContinueToUse();
    }
}
