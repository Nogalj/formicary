package com.nogal.formicary.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

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
 *
 * <h2>Play-test round 5, item 1: the tamed castes climb only when they mean to</h2>
 *
 * <p>Spider's rule -- climb on <em>any</em> horizontal collision -- is right for a wild ant,
 * whose whole habitat is the vertical inside of a colony, and wrong for a tamed one, whose
 * job is a flat field in the overworld. Logan's fifth play-test caught the difference with a
 * screenshot: a loaded worker running an ordinary farm errand brushed a tree trunk, rose
 * 0.2/tick up the side of it, and wedged under the leaf canopy with its harvest still in the
 * pack. The wedge is stable -- {@code WallClimberNavigation}'s "no path, push at the target
 * with the move control" fallback keeps shoving the mob through the very wall it is pinned
 * against, which is banked in {@code docs/gotchas/gametest.md} from the round-3 cage test and
 * had until then only been seen in a test arena.
 *
 * <p>{@link #tamedClimbFlag} is the gate: a tamed worker or soldier raises the flag only when
 * it has walked into something <em>and</em> its current errand actually wants to go up, read
 * as "the active path's target is at least {@link #CLIMB_TRIGGER_HEIGHT} blocks above the
 * ant's own foot level". The number is 2 because 1 is not a climb -- {@code WalkNodeEvaluator}
 * already steps a walking mob up a single block, so anything a genuine ascent needs starts at
 * two. Flat farm errands therefore stop producing tree ascents, while a colony route that
 * genuinely climbs still reports a target far overhead for the whole walk and keeps climbing.
 *
 * <p>Two consequences worth stating outright rather than discovering later:
 * <ul>
 *   <li><b>No path means no climb.</b> When the pathfinder cannot answer at all, the
 *       navigation falls back to shoving the mob at its target -- and that shove into a
 *       nearby obstacle is precisely the tree-trunk case. Refusing the climb there is the
 *       point of the gate, not a casualty of it.</li>
 *   <li><b>An already-wedged ant comes back down.</b> Once it is above its target the height
 *       difference is negative, so the flag drops, so it falls -- the gate unsticks the
 *       geometry Logan photographed as well as preventing it.</li>
 * </ul>
 *
 * <p>Wild ants ({@link WorkerAntEntity}, {@link SoldierAntEntity}, {@link EnderAntEntity})
 * keep the unconditional Spider rule. Colony walls are their habitat and nothing they do is
 * an errand a player is waiting on.
 */
public final class AntClimbing {
    /**
     * How far above a tamed ant's foot level its path target has to be before a collision is
     * allowed to become a climb. Two, because a walking mob already handles one: a path
     * target exactly one block up is an ordinary step, not an ascent.
     */
    public static final int CLIMB_TRIGGER_HEIGHT = 2;

    /**
     * The navigation every climbing ant is built with. Called from {@code Mob}'s constructor
     * (through {@code createNavigation}), so it must not touch subclass state.
     */
    public static PathNavigation navigation(Mob ant, Level level) {
        return new WallClimberNavigation(ant, level);
    }

    /**
     * The gated climbing flag for a <b>tamed</b> ant -- what its {@code tick()} feeds to
     * {@code DATA_CLIMBING} in place of bare {@code horizontalCollision}. See the class
     * javadoc for why the two castes differ.
     *
     * @return {@code true} only when the ant walked into something this tick <em>and</em> it
     *         is currently following a path whose target sits at least
     *         {@link #CLIMB_TRIGGER_HEIGHT} blocks above its feet
     */
    public static boolean tamedClimbFlag(Mob ant) {
        if (!ant.horizontalCollision) {
            return false;
        }
        Path path = ant.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        // getTarget() is where the errand was aimed, which is the question being asked --
        // not getEndNode(), which is only as high as the walkable route managed to get and
        // would answer "flat" for exactly the ascent this gate exists to allow.
        return path.getTarget().getY() - Mth.floor(ant.getY()) >= CLIMB_TRIGGER_HEIGHT;
    }

    private AntClimbing() {
    }
}
