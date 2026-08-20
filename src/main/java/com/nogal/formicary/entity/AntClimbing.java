package com.nogal.formicary.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;

/**
 * Play-test round 2, spec item 2: <b>ants climb walls, the way a spider does.</b> One home
 * for the explanation, because the pattern has to be repeated verbatim in five entity
 * classes -- {@link WorkerAntEntity}, {@link SoldierAntEntity}, {@link EnderAntEntity},
 * {@link TamedWorkerAntEntity} and {@link TamedSoldierAntEntity} -- and none of them share a
 * superclass that could hold it (the two tamed castes descend from {@code TamableAnimal},
 * the three wild ones from {@code PathfinderMob}, and the tamed variants are <em>not</em>
 * subclasses of their wild namesakes; checked, not assumed).
 *
 * <p>Copied from the decompiled {@code Spider} in {@code reference/}, which is three parts
 * and needs all three:
 * <ol>
 *   <li>{@link #navigation} -- {@code createNavigation} returns a
 *       {@link WallClimberNavigation}. This is the half that makes climbing <em>useful</em>
 *       rather than accidental: it lets the pathfinder answer with a route the mob can only
 *       take by going up a wall, and when no path exists at all its {@code tick} pushes the
 *       mob at the target with the move control instead of giving up.</li>
 *   <li>A synched flag set from {@code horizontalCollision} in {@code tick()}, server side
 *       only -- i.e. "I walked into something this tick".</li>
 *   <li>{@code onClimbable()} returning that flag, which is what actually converts the
 *       collision into upward movement in {@code LivingEntity.travel}.</li>
 * </ol>
 *
 * <p>The flag has to be <em>synched</em>: {@code onClimbable} is read on both sides, and a
 * server-only field would leave the client rendering an ant that walks into a wall while the
 * server has it halfway up. Spider packs it into bit 0 of a shared {@code BYTE} accessor
 * because its byte historically carried more than one flag; there is only one flag here, so
 * each ant defines a plain {@code BOOLEAN} accessor of its own instead -- same synchronised
 * state, no bit twiddling, and it matches how {@code LarvaEntity} already carries its own
 * one-bit state.
 *
 * <p><b>Deliberately not applied to {@link QueenAntEntity} or {@link LarvaEntity}</b> -- see
 * {@code docs/DECISIONS.md}. The queen's fight is designed around a ground phase in a walled
 * arena, and a boss that can leave it is a different fight; a larva does not walk at all.
 */
public final class AntClimbing {
    /**
     * The navigation every climbing ant is built with. Called from {@code Mob}'s constructor
     * (through {@code createNavigation}), so it must not touch subclass state.
     */
    public static PathNavigation navigation(Mob ant, Level level) {
        return new WallClimberNavigation(ant, level);
    }

    private AntClimbing() {
    }
}
