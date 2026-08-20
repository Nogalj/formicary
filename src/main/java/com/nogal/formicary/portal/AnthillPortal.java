package com.nogal.formicary.portal;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ARRIVAL_CONNECTOR_MAX_REACH;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENTRY_CARVE_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENTRY_CARVE_PREFERRED_Y;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENTRY_CARVE_RADIUS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENTRY_SCAN_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.ENTRY_SCAN_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.MEMBRANE_THICKNESS;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.entryPocketUnderCap;

import java.util.Set;

import javax.annotation.Nullable;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.block.ModBlocks;
import com.nogal.formicary.worldgen.ColonyChunkGenerator;
import com.nogal.formicary.worldgen.ColonyNoise;
import com.nogal.formicary.worldgen.ModWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Both directions of the ender-pearl portal (spec section 2).
 *
 * <p>In: a pearl that lands on an anthill sends the thrower to the same XZ inside the
 * colony -- coordinate scale 1, so distant anthills open onto distant parts of one
 * infinite colony. Out: a pearl that lands on a Daylight Membrane sends them back to the
 * anthill they came in through.
 *
 * <p>Split deliberately into small pure-ish helpers ({@link #findCoreNear},
 * {@link #isMembrane}, {@link #findFloorInBand}) and the two teleport routines that use
 * them, because {@code GameTestServer} never loads this mod's dimension (it bakes
 * {@code WorldPresets.FLAT} into an empty {@code LevelStem} registry -- the same
 * limitation M4a and M4b hit). The helpers are therefore the part a headless test can
 * actually exercise, and they are where all the arithmetic lives; the teleports
 * themselves are verified on {@code runServer}.
 */
public final class AnthillPortal {

    // -------------------------------------------------------------- tunables --

    /**
     * How far from an Anthill Core a pearl may land and still count as hitting the anthill,
     * as a Chebyshev radius (i.e. a cube). Spec: "the impact block is the core or any block
     * within 2 blocks of a core".
     *
     * <p>The anthill template is built around this number: with the core at its centre, a
     * radius-2 cube covers every block of the raised mound, so there is no part of the
     * visible structure that silently swallows a pearl. See {@code assets-src/structures.py}.
     */
    public static final int CORE_SEARCH_RADIUS = 2;

    /** Ring radii tried, in order, when looking for clear ground beside the exit anthill. */
    private static final int[] EXIT_RING_RADII = {4, 5, 6};

    /** Offsets around a ring, as (dx, dz) pairs scaled by the radius: 8 compass points. */
    private static final int[][] EXIT_RING_DIRECTIONS = {
        {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };

    /** Returned by {@link #findFloorInBand} when the band holds nowhere to stand. */
    public static final int NO_FLOOR = Integer.MIN_VALUE;

    // -------------------------------------------------------- impact detection --

    /**
     * The Anthill Core within {@link #CORE_SEARCH_RADIUS} (Chebyshev) of {@code impact}, or
     * {@code null} if the pearl did not land on an anthill.
     *
     * <p>Returns the <i>nearest</i> core rather than the first found, so that two anthills
     * generated close together each send the player to their own XZ.
     */
    @Nullable
    public static BlockPos findCoreNear(BlockGetter level, BlockPos impact) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -CORE_SEARCH_RADIUS; dx <= CORE_SEARCH_RADIUS; dx++) {
            for (int dy = -CORE_SEARCH_RADIUS; dy <= CORE_SEARCH_RADIUS; dy++) {
                for (int dz = -CORE_SEARCH_RADIUS; dz <= CORE_SEARCH_RADIUS; dz++) {
                    cursor.set(impact.getX() + dx, impact.getY() + dy, impact.getZ() + dz);
                    if (!level.getBlockState(cursor).is(ModBlocks.ANTHILL_CORE.get())) {
                        continue;
                    }
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** Whether {@code pos} holds a Daylight Membrane -- the exit block. */
    public static boolean isMembrane(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.DAYLIGHT_MEMBRANE.get());
    }

    // ------------------------------------------------------------ safe footing --

    /**
     * The highest Y in {@code [bandBottom, bandTop)} where a player can stand: air at
     * {@code y} and {@code y + 1}, with something that blocks motion at {@code y - 1}.
     *
     * <p>This is the whole "never suffocate, never void-drop" guarantee in one function --
     * the two-block air requirement is what stops a player being sealed into soil, and the
     * solid block below is what stops them arriving over a shaft.
     *
     * @return the Y to stand on, or {@link #NO_FLOOR}
     */
    public static int findFloorInBand(BlockGetter level, int x, int z, int bandBottom, int bandTop) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = bandTop - 1; y >= bandBottom; y--) {
            if (!level.getBlockState(cursor.set(x, y, z)).isAir()) {
                continue;
            }
            if (!level.getBlockState(cursor.set(x, y + 1, z)).isAir()) {
                continue;
            }
            if (level.getBlockState(cursor.set(x, y - 1, z)).blocksMotion()) {
                return y;
            }
        }
        return NO_FLOOR;
    }

    /**
     * Finds -- or, failing that, digs -- somewhere safe to put an arriving player at
     * {@code (x, z)} in the colony, and opens the exit above it.
     *
     * <p>The carve only ever <b>removes</b> solid blocks, and only from a Y where there was
     * already something solid to stand on. That is deliberate: the dimension's walkable
     * spine is a helicoid ramp whose floor is forced solid precisely so a chamber cannot
     * swallow it (see {@code ColonyNoise#shaftState}), and dropping a 5x5 slab of soil into
     * an arbitrary Y would be the one thing in the mod that can put blocks back into that
     * carve. The single exception is patching holes in the pocket's own floor, which can
     * at worst raise a ramp walkway by one block -- still passable, since the ramp is
     * carved three blocks tall.
     *
     * <p>Ep2 adds {@link #openMembraneColumn}, which is subject to the same rule and keeps
     * it: it removes fabric between the pocket and the cap, and inside the cap -- which is
     * never carvable, so never part of the spine -- it swaps solid for solid. Neither half
     * can put a block into carved air, so the shaft's precedence is untouched exactly as it
     * is by the pocket carve.
     *
     * <p>The caller must already have loaded the chunk.
     */
    public static BlockPos findOrCarveEntryPocket(ServerLevel colony, int x, int z) {
        int natural = findFloorInBand(colony, x, z, ENTRY_SCAN_BOTTOM, ENTRY_SCAN_TOP + 1);
        // entryPocketUnderCap, not "natural != NO_FLOOR": the band and the acceptance rule
        // are the same statement, and stating it once is what lets a headless check assert
        // the arithmetic without loading a level.
        BlockPos pocket = natural != NO_FLOOR && entryPocketUnderCap(natural)
                ? new BlockPos(x, natural, z)
                : carveEntryPocket(colony, x, z);
        openMembraneColumn(colony, pocket);
        carveArrivalConnector(colony, pocket);
        return pocket;
    }

    /**
     * Opens the way from the pocket to the traversable network -- the round-3 no-softlock
     * fix.
     *
     * <p>Before this, the pocket was joined to the world only where a worm tunnel happened
     * to clip its 5x5 box: measured over 256 arbitrary arrival columns per seed, three
     * arrivals in four were sealed in ({@code NoiseProbe#arrivals}, and see
     * {@code ColonyGeneratorTunables}' arrival-connector section for the numbers). The route
     * itself is {@link ColonyNoise#arrivalConnector}'s -- a pure function of the terrain, so
     * the probe measures the same passage this method digs -- and everything here is the
     * writing of it.
     *
     * <p>It obeys the same rule the pocket carve does, one notch stricter: it only
     * <b>removes</b> solid blocks, never where the connectivity spine forces them solid (the
     * route excludes those), and the only blocks it adds are the corridor's own walkway,
     * which the route has already made disjoint from the air it clears.
     *
     * <p>The corridor can run into a neighbouring chunk, so each one it touches is loaded
     * before it is written to -- the same reason {@link #enterColony} loads the pocket's own
     * chunk before reading a column out of it.
     */
    private static void carveArrivalConnector(ServerLevel colony, BlockPos pocket) {
        ChunkGenerator generator = colony.getChunkSource().getGenerator();
        if (!(generator instanceof ColonyChunkGenerator colonyGenerator)) {
            Formicary.LOGGER.warn("Formicary: {} is not using the colony generator ({}), so no arrival connector"
                    + " was carved -- the arrival may be sealed in",
                    ModWorldgen.FORMICARY_LEVEL.location(), generator.getClass().getName());
            return;
        }
        ColonyNoise noise = colonyGenerator.noise(colony.getChunkSource().randomState());
        ColonyNoise.ArrivalConnector connector =
                noise.arrivalConnector(pocket.getX(), pocket.getY(), pocket.getZ());
        if (!connector.joined()) {
            Formicary.LOGGER.warn("Formicary: found no worm tunnel or ramp within {} blocks of the arrival at"
                    + " {} {} {} -- nothing was carved", ARRIVAL_CONNECTOR_MAX_REACH,
                    pocket.getX(), pocket.getY(), pocket.getZ());
            return;
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState soil = ModBlocks.PACKED_SOIL.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ColonyNoise.PocketBlock block : connector.air()) {
            cursor.set(block.x(), block.y(), block.z());
            colony.getChunk(block.x() >> 4, block.z() >> 4);
            if (!colony.getBlockState(cursor).isAir()) {
                colony.setBlock(cursor.immutable(), air, Block.UPDATE_ALL);
            }
        }
        for (ColonyNoise.PocketBlock block : connector.floor()) {
            cursor.set(block.x(), block.y(), block.z());
            colony.getChunk(block.x() >> 4, block.z() >> 4);
            if (!colony.getBlockState(cursor).blocksMotion()) {
                colony.setBlock(cursor.immutable(), soil, Block.UPDATE_ALL);
            }
        }
        Formicary.LOGGER.info(
                "Formicary: carved an arrival connector from {} {} {} to the network at {} {} {}"
                        + " -- {} blocks cleared, {} laid, {} blocks of run",
                pocket.getX(), pocket.getY(), pocket.getZ(), connector.targetX(), connector.targetY(),
                connector.targetZ(), connector.air().length, connector.floor().length, connector.length());
    }

    /**
     * Punches the arrival's guaranteed way out: a one-block chimney from just above the
     * player's head up to the underside of the ceiling cap, capped with Daylight Membrane
     * for {@code MEMBRANE_THICKNESS} layers at the pocket's own XZ.
     *
     * <p>The generator already makes every <i>exposed</i> ceiling column a membrane, but an
     * arrival pocket is precisely the case that rule cannot reach: the pocket is often a
     * tunnel with solid fabric between it and the roof, so the nearest exit could be tens
     * of blocks of unlit gallery away with nothing pointing at it. Arriving under a lit
     * patch turns the way out from something a player has to discover into something they
     * are standing under, which is the whole of the Ep2 exits change on the portal side.
     *
     * <p>Runtime {@code setBlock} on the loaded chunk, the same path {@link
     * #carveEntryPocket} uses -- the pocket may have been carved a moment ago, so a
     * generator-side rule could not see it.
     */
    private static void openMembraneColumn(ServerLevel colony, BlockPos pocket) {
        int x = pocket.getX();
        int z = pocket.getZ();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState membrane = ModBlocks.DAYLIGHT_MEMBRANE.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // From the first block above the player's head (feet at floorY, head at floorY + 1)
        // to the last block under the cap. Removal only.
        int cleared = 0;
        for (int y = pocket.getY() + 2; y < CEILING_BOTTOM; y++) {
            if (!colony.getBlockState(cursor.set(x, y, z)).isAir()) {
                colony.setBlock(cursor.immutable(), air, Block.UPDATE_ALL);
                cleared++;
            }
        }
        for (int layer = 0; layer < MEMBRANE_THICKNESS; layer++) {
            cursor.set(x, CEILING_BOTTOM + layer, z);
            if (!colony.getBlockState(cursor).is(ModBlocks.DAYLIGHT_MEMBRANE.get())) {
                colony.setBlock(cursor.immutable(), membrane, Block.UPDATE_ALL);
            }
        }
        Formicary.LOGGER.info(
                "Formicary: opened an exit above the arrival pocket at {} {} {} -- cleared {} blocks, "
                        + "membrane at y {}..{}",
                x, pocket.getY(), z, cleared, CEILING_BOTTOM, CEILING_BOTTOM + MEMBRANE_THICKNESS - 1);
    }

    private static BlockPos carveEntryPocket(ServerLevel colony, int x, int z) {
        int floorY = chooseCarveFloor(colony, x, z);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState soil = ModBlocks.PACKED_SOIL.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -ENTRY_CARVE_RADIUS; dx <= ENTRY_CARVE_RADIUS; dx++) {
            for (int dz = -ENTRY_CARVE_RADIUS; dz <= ENTRY_CARVE_RADIUS; dz++) {
                if (!colony.getBlockState(cursor.set(x + dx, floorY - 1, z + dz)).blocksMotion()) {
                    colony.setBlock(cursor.immutable(), soil, Block.UPDATE_ALL);
                }
                for (int dy = 0; dy < ENTRY_CARVE_HEIGHT; dy++) {
                    cursor.set(x + dx, floorY + dy, z + dz);
                    if (!colony.getBlockState(cursor).isAir()) {
                        colony.setBlock(cursor.immutable(), air, Block.UPDATE_ALL);
                    }
                }
            }
        }
        Formicary.LOGGER.info("Formicary: carved an arrival pocket at {} {} {}", x, floorY, z);
        return new BlockPos(x, floorY, z);
    }

    /**
     * The Y to hollow the pocket out at: the first level at or below
     * {@code ENTRY_CARVE_PREFERRED_Y} with solid ground beneath it, then failing that the
     * first one above. Keeping the whole pocket inside
     * {@code [ENTRY_SCAN_BOTTOM, ENTRY_SCAN_TOP]} is what stops it punching through the
     * ceiling cap; since Ep2 pulled that band up against the cap it is also what keeps
     * every carved floor inside {@code entryPocketUnderCap}, exactly like an accepted
     * natural one -- every return here is bounded by the same two constants the predicate
     * is written from.
     */
    private static int chooseCarveFloor(ServerLevel colony, int x, int z) {
        int highest = ENTRY_SCAN_TOP - ENTRY_CARVE_HEIGHT + 1;
        int start = Math.min(ENTRY_CARVE_PREFERRED_Y, highest);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = start; y >= ENTRY_SCAN_BOTTOM; y--) {
            if (colony.getBlockState(cursor.set(x, y - 1, z)).blocksMotion()) {
                return y;
            }
        }
        for (int y = start + 1; y <= highest; y++) {
            if (colony.getBlockState(cursor.set(x, y - 1, z)).blocksMotion()) {
                return y;
            }
        }
        // A column with nothing solid anywhere in the band. carveEntryPocket lays its own
        // floor, so this is still safe -- it just has to lay all 25 blocks of it.
        return start;
    }

    /**
     * Somewhere safe to stand near {@code anchor} in an ordinary (heightmap-having) level:
     * a ring of clear ground beside the anthill first, then the anthill's own summit.
     *
     * <p>Ring before centre because the mound's summit is one block wide -- landing on it
     * is a slide off a cone, whereas the ring radii start just outside the structure's 7x7
     * footprint, on the savanna itself.
     */
    public static BlockPos findSafeStandNear(ServerLevel level, BlockPos anchor) {
        for (int radius : EXIT_RING_RADII) {
            for (int[] direction : EXIT_RING_DIRECTIONS) {
                BlockPos candidate = surfaceStand(level, anchor.getX() + direction[0] * radius,
                        anchor.getZ() + direction[1] * radius);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        BlockPos summit = surfaceStand(level, anchor.getX(), anchor.getZ());
        if (summit != null) {
            return summit;
        }
        // Nothing anywhere around passed the two-air check (a cave roof, a structure, deep
        // water). The heightmap position is still above every motion-blocking block in the
        // column, so it is the safest thing left.
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor);
    }

    @Nullable
    private static BlockPos surfaceStand(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (!level.getBlockState(cursor.set(x, y, z)).isAir()
                || !level.getBlockState(cursor.set(x, y + 1, z)).isAir()
                || !level.getBlockState(cursor.set(x, y - 1, z)).blocksMotion()) {
            return null;
        }
        return new BlockPos(x, y, z);
    }

    // ---------------------------------------------------------------- travel --

    /**
     * Sends {@code player} into the colony at the anthill's own XZ and records the anthill
     * as their way back.
     *
     * @return whether the teleport happened
     */
    public static boolean enterColony(ServerPlayer player, BlockPos core) {
        MinecraftServer server = player.server;
        ServerLevel colony = server.getLevel(ModWorldgen.FORMICARY_LEVEL);
        if (colony == null) {
            Formicary.LOGGER.warn("Formicary: an anthill was struck but {} is not loaded on this server",
                    ModWorldgen.FORMICARY_LEVEL.location());
            return false;
        }

        // Generate the destination chunk before asking for its blocks: the pocket search
        // reads ~40 blocks of column, and an ungenerated chunk reads as air all the way
        // down, which would look like a void drop.
        colony.getChunk(core.getX() >> 4, core.getZ() >> 4);
        BlockPos arrival = findOrCarveEntryPocket(colony, core.getX(), core.getZ());

        player.setData(ModAttachments.ENTRY_ANTHILL, core);
        player.getData(ModAttachments.TRAIL_PATH).clear();
        return teleport(player, colony, arrival);
    }

    /**
     * Sends {@code player} back to the anthill they came in through, or to the overworld
     * spawn if they have never used one (a membrane they carried out and placed themselves,
     * or a world where they arrived by command).
     *
     * @return whether the teleport happened
     */
    public static boolean exitColony(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }
        BlockPos anchor = player.getExistingData(ModAttachments.ENTRY_ANTHILL)
                .orElseGet(overworld::getSharedSpawnPos);

        overworld.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        BlockPos arrival = findSafeStandNear(overworld, anchor);

        player.getData(ModAttachments.TRAIL_PATH).clear();
        return teleport(player, overworld, arrival);
    }

    /**
     * The actual move, through the same path {@code /teleport} takes:
     * {@code ServerPlayer#teleportTo(ServerLevel, x, y, z, Set<RelativeMovement>, yaw,
     * pitch)}, which adds the {@code POST_TELEPORT} chunk ticket and then hands a
     * cross-dimension move to {@code changeDimension}. Verified in the decompiled sources
     * against {@code TeleportCommand}; the plainer {@code teleportTo(double, double,
     * double)} cannot cross dimensions at all.
     */
    private static boolean teleport(ServerPlayer player, ServerLevel destination, BlockPos pos) {
        boolean moved = player.teleportTo(destination, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        if (moved) {
            player.resetFallDistance();
            destination.playSound(null, pos, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return moved;
    }

    private AnthillPortal() {
    }
}
