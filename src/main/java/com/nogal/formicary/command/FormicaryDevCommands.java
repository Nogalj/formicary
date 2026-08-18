package com.nogal.formicary.command;

import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CEILING_BOTTOM;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.CHAMBER_ELIGIBILITY_MIN_F;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.FLOOR_TOP;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.SPAWN_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.THRONE_DAIS_HEIGHT;
import static com.nogal.formicary.worldgen.ColonyGeneratorTunables.tierIndex;

import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nogal.formicary.Formicary;
import com.nogal.formicary.entity.ModEntities;
import com.nogal.formicary.entity.QueenAntEntity;
import com.nogal.formicary.item.ModItems;
import com.nogal.formicary.worldgen.ColonyChunkGenerator;
import com.nogal.formicary.worldgen.ColonyNoise;
import com.nogal.formicary.worldgen.ModWorldgen;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /formicary dev ...} -- the maintainer's tooling for looking at this mod.
 *
 * <p>Everything the colony is worth seeing is buried in a 192-block-deep dimension behind a
 * thrown ender pearl and a descent -- and since the Ep2 colony field, inside one of the
 * cores on a 384-block grid rather than spread evenly through it. Without this, every visual
 * check costs a play session; with it a screenshot of the queen's chamber is two commands.
 * Task A2's shot-list autopilot drives these same seams from a JSON file.
 *
 * <p>Registered on the GAME bus by annotation, which is how every other game-bus handler in
 * this mod is wired ({@code PortalEvents}, {@code ColonyAngerEvents},
 * {@code TamedAntEvents}) -- {@code Formicary.java}'s constructor only ever touches the MOD
 * bus, so adding a listener there would have been the odd one out rather than the
 * convention. {@link RegisterCommandsEvent} fires on {@code NeoForge.EVENT_BUS} (verified in
 * {@code reference/net/neoforged/neoforge/event/RegisterCommandsEvent.java}).
 *
 * <p>Gated at permission level 2 on the {@code dev} node, so on a normal server nobody sees
 * it in tab-completion and nobody can run it.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class FormicaryDevCommands {

    /** Bottom-up, matching {@code ColonyGeneratorTunables}' tier indices. */
    private static final String[] TIER_NAMES = {"Royal Depths", "Nurseries", "Fungal Gardens", "Upper Galleries"};

    /** How far in front of the player {@code queenfight} puts the queen. */
    private static final double QUEENFIGHT_DISTANCE = 6.0;

    private static final int KIT_TRAIL_PHEROMONE = 8;
    private static final int KIT_ENDER_PEARLS = 4;
    private static final int KIT_FUNGAL_BLOOM = 16;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("formicary")
                .then(Commands.literal("dev")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("locate")
                                .then(Commands.literal("colony")
                                        .executes(context -> locate(context.getSource(), Chamber.COLONY)))
                                .then(Commands.literal("throne")
                                        .executes(context -> locate(context.getSource(), Chamber.THRONE)))
                                .then(Commands.literal("nursery")
                                        .executes(context -> locate(context.getSource(), Chamber.NURSERY)))
                                .then(Commands.literal("garden")
                                        .executes(context -> locate(context.getSource(), Chamber.GARDEN)))
                                .then(Commands.literal("larder")
                                        .executes(context -> locate(context.getSource(), Chamber.LARDER))))
                        .then(Commands.literal("tp")
                                .then(Commands.literal("colony")
                                        .executes(context -> teleport(context.getSource(), Chamber.COLONY)))
                                .then(Commands.literal("throne")
                                        .executes(context -> teleport(context.getSource(), Chamber.THRONE)))
                                .then(Commands.literal("nursery")
                                        .executes(context -> teleport(context.getSource(), Chamber.NURSERY)))
                                .then(Commands.literal("garden")
                                        .executes(context -> teleport(context.getSource(), Chamber.GARDEN)))
                                .then(Commands.literal("larder")
                                        .executes(context -> teleport(context.getSource(), Chamber.LARDER))))
                        .then(Commands.literal("state")
                                .executes(context -> state(context.getSource())))
                        .then(Commands.literal("kit")
                                .executes(context -> kit(context.getSource())))
                        .then(Commands.literal("queenfight")
                                .executes(context -> queenfight(context.getSource())))));
    }

    // ------------------------------------------------------------------
    // locate / tp
    // ------------------------------------------------------------------

    /**
     * The destinations the cell math can answer for.
     *
     * <p>{@link #COLONY} is the odd one out and deliberately so: it is not a room, it is the
     * centre of a colony's density field (Ep2). Everything else in this list exists because
     * of it -- one throne per colony, chambers only inside one -- so "where is the nearest
     * nest" is the first question a visual pass now asks.
     */
    private enum Chamber {
        COLONY("colony centre"),
        THRONE("throne chamber"),
        NURSERY("nursery chamber"),
        GARDEN("fungus garden chamber"),
        LARDER("larder chamber");

        private final String label;

        Chamber(String label) {
            this.label = label;
        }
    }

    /** A resolved chamber: where to stand in it, and how far away it is horizontally. */
    private record Found(Chamber chamber, BlockPos stand, double distance) {
    }

    private static int locate(CommandSourceStack source, Chamber chamber) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Found found = find(source, chamber, player);
        if (found == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "Nearest %s: %d %d %d (%.0f blocks away)",
                found.chamber().label, found.stand().getX(), found.stand().getY(), found.stand().getZ(),
                found.distance())), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int teleport(CommandSourceStack source, Chamber chamber) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel colony = colonyLevel(source);
        Found found = find(source, chamber, player);
        if (colony == null || found == null) {
            return 0;
        }
        BlockPos nominal = found.stand();
        // Force the destination chunk through generation before the move. teleportTo does
        // add a POST_TELEPORT ticket, but an ungenerated chunk reads as air all the way down
        // (banked in docs/gotchas/events-portals.md), and a dev command that drops you into
        // an unbuilt column looks exactly like a broken chamber.
        colony.getChunk(SectionPos.blockToSectionCoord(nominal.getX()),
                SectionPos.blockToSectionCoord(nominal.getZ()));
        // Only a colony centre needs the column read. Every other destination is a chamber
        // floor the generator forces solid, so its Y is known without looking; a colony
        // centre is just an XZ, and whatever the carve left there is usually solid fabric.
        BlockPos stand = found.chamber() == Chamber.COLONY ? standableY(colony, nominal) : nominal;
        // The Set<RelativeMovement> overload is the only entry point that can change
        // dimension (verified in reference/net/minecraft/server/level/ServerPlayer.java:1517
        // -- the plain teleportTo(double,double,double) cannot). An empty set means every
        // coordinate is absolute; the player keeps the way they were facing.
        player.teleportTo(colony, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(String.format("Teleported to the %s at %d %d %d",
                found.chamber().label, stand.getX(), stand.getY(), stand.getZ())), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The nearest chamber of {@code chamber}'s kind to the player's X/Z, or null (with the
     * failure already reported) if the colony's generator is not reachable.
     *
     * <p>Deliberately keyed on the player's X/Z whatever dimension they are standing in: the
     * colony's shape is a pure function of position, so asking from the overworld is a
     * preview of where you would come out, and asking from inside is the real answer.
     */
    @Nullable
    private static Found find(CommandSourceStack source, Chamber chamber, ServerPlayer player) {
        ColonyNoise noise = colonyNoise(source);
        if (noise == null) {
            return null;
        }
        int x = player.blockPosition().getX();
        int z = player.blockPosition().getZ();
        return switch (chamber) {
            case COLONY -> {
                // A colony centre is a point in a scalar field, not a carved room, so there
                // is no floor to derive: SPAWN_HEIGHT is a nominal Y that teleport() then
                // refines against the real column (see standableY).
                ColonyNoise.Colony colony = noise.nearestColony(x, z);
                BlockPos stand = new BlockPos((int) Math.round(colony.centreX()), SPAWN_HEIGHT,
                        (int) Math.round(colony.centreZ()));
                yield new Found(chamber, stand, Math.hypot(x - colony.centreX(), z - colony.centreZ()));
            }
            case THRONE -> {
                ColonyNoise.Throne throne = noise.nearestThrone(x, z);
                // The chamber's centre column IS the dais, so its floor is the plinth's
                // top -- the exact block ColonyChunkGenerator seats the queen on.
                BlockPos stand = new BlockPos((int) Math.round(throne.centreX()),
                        throne.floorY() + THRONE_DAIS_HEIGHT + 1, (int) Math.round(throne.centreZ()));
                yield new Found(chamber, stand, Math.hypot(x - throne.centreX(), z - throne.centreZ()));
            }
            case NURSERY -> {
                ColonyNoise.Nursery nursery = noise.nearestNursery(x, z);
                // A nursery's floor slab is at floorY, forced solid by the same function
                // that carved the room, so floorY + 1 is standable without reading a block.
                BlockPos stand = new BlockPos((int) Math.round(nursery.centreX()), nursery.floorY() + 1,
                        (int) Math.round(nursery.centreZ()));
                yield new Found(chamber, stand, Math.hypot(x - nursery.centreX(), z - nursery.centreZ()));
            }
            case GARDEN -> {
                // Same "floor is forced solid" guarantee the nursery case relies on --
                // ColonyNoise.gardenState forces the corridor/interior floor exactly the
                // way nurseryState does.
                ColonyNoise.Garden garden = noise.nearestGarden(x, z);
                BlockPos stand = new BlockPos((int) Math.round(garden.centreX()), garden.floorY() + 1,
                        (int) Math.round(garden.centreZ()));
                yield new Found(chamber, stand, Math.hypot(x - garden.centreX(), z - garden.centreZ()));
            }
            case LARDER -> {
                ColonyNoise.Larder larder = noise.nearestLarder(x, z);
                BlockPos stand = new BlockPos((int) Math.round(larder.centreX()), larder.floorY() + 1,
                        (int) Math.round(larder.centreZ()));
                yield new Found(chamber, stand, Math.hypot(x - larder.centreX(), z - larder.centreZ()));
            }
        };
    }

    // ------------------------------------------------------------------
    // state
    // ------------------------------------------------------------------

    /**
     * Everything the generator knows about where you are standing, as text.
     *
     * <p>The colony field line is the one that earns this command since Ep2. {@code f} is
     * the single number the whole layer reduces to -- what fraction of full colony density
     * this XZ carves at -- and it is otherwise completely invisible in game: a player (or a
     * maintainer taking a screenshot) cannot tell "sparse wilds, working as designed" from
     * "the carve broke" by looking. Printing it, the distance to the centre it came from,
     * and the two thresholds it is read against turns that into a readout.
     */
    private static int state(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        boolean inColony = player.level().dimension() == ModWorldgen.FORMICARY_LEVEL;

        source.sendSuccess(() -> Component.literal(String.format("pos %d %d %d in %s",
                pos.getX(), pos.getY(), pos.getZ(), player.level().dimension().location())), true);
        source.sendSuccess(() -> Component.literal(String.format("tier %d (%s)%s",
                tierIndex(pos.getY()), TIER_NAMES[tierIndex(pos.getY())],
                inColony ? "" : " -- band for this Y, you are not in the colony")), true);

        ColonyNoise noise = colonyNoise(source);
        if (noise == null) {
            return 0;
        }
        ColonyNoise.Colony centre = noise.nearestColony(pos.getX(), pos.getZ());
        double field = noise.colonyField(pos.getX(), pos.getZ());
        double toCentre = Math.hypot(pos.getX() - centre.centreX(), pos.getZ() - centre.centreZ());
        source.sendSuccess(() -> Component.literal(String.format(
                "colony f %.3f at %.0f blocks from the centre %d %d (%s)", field, toCentre,
                Math.round(centre.centreX()), Math.round(centre.centreZ()), zone(field))), true);

        Found throne = find(source, Chamber.THRONE, player);
        Found nursery = find(source, Chamber.NURSERY, player);
        if (throne == null || nursery == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "nearest throne %.0f blocks at %d %d %d", throne.distance(),
                throne.stand().getX(), throne.stand().getY(), throne.stand().getZ())), true);
        source.sendSuccess(() -> Component.literal(String.format(
                "nearest nursery %.0f blocks at %d %d %d", nursery.distance(),
                nursery.stand().getX(), nursery.stand().getY(), nursery.stand().getZ())), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Which side of the two field thresholds this {@code f} falls on, in words. */
    private static String zone(double field) {
        if (field >= 1.0) {
            return "colony core";
        }
        if (field > CHAMBER_ELIGIBILITY_MIN_F) {
            return "colony ring -- chambers still generate here";
        }
        if (field > 0.0) {
            return "outer falloff -- no chambers, thinning decoration";
        }
        return "wilds -- worm tunnels and ramps only";
    }

    // ------------------------------------------------------------------
    // kit
    // ------------------------------------------------------------------

    /**
     * Everything needed to survive a trip down and get back out: the armour set that gates
     * mining the fabric, breadcrumbs, pearls for the portal, and food that grants night
     * vision in a dimension with no sky.
     */
    private static int kit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        give(player, new ItemStack(ModItems.CHITIN_HELMET.get()));
        give(player, new ItemStack(ModItems.CHITIN_CHESTPLATE.get()));
        give(player, new ItemStack(ModItems.CHITIN_LEGGINGS.get()));
        give(player, new ItemStack(ModItems.CHITIN_BOOTS.get()));
        give(player, new ItemStack(ModItems.TRAIL_PHEROMONE.get(), KIT_TRAIL_PHEROMONE));
        give(player, new ItemStack(Items.ENDER_PEARL, KIT_ENDER_PEARLS));
        give(player, new ItemStack(ModItems.FUNGAL_BLOOM.get(), KIT_FUNGAL_BLOOM));
        source.sendSuccess(() -> Component.literal("Handed over the colony kit"), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Into the inventory, or onto the floor if it is full -- never silently swallowed. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------
    // queenfight
    // ------------------------------------------------------------------

    /**
     * Puts a queen {@link #QUEENFIGHT_DISTANCE} blocks in front of the player, wherever they
     * are standing.
     *
     * <p>{@code setThroneHome} is not optional here. Her leash, her
     * {@code MoveTowardsRestrictionGoal} and the hard teleport-home failsafe all read
     * {@code throneHome}, and the generator is the only other thing that ever sets it -- a
     * queen summoned without one has no home to path back to, so the arena half of the
     * fight simply would not run. Her home is set to where she lands, which makes the spot
     * you summoned her on the arena.
     */
    private static int queenfight(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        double yaw = Math.toRadians(player.getYRot());
        double x = player.getX() - Math.sin(yaw) * QUEENFIGHT_DISTANCE;
        double z = player.getZ() + Math.cos(yaw) * QUEENFIGHT_DISTANCE;
        double y = player.getY();

        QueenAntEntity queen = ModEntities.QUEEN_ANT.get().create(level);
        if (queen == null) {
            source.sendFailure(Component.literal("Could not create a queen"));
            return 0;
        }
        // Facing the player, not away from them.
        queen.moveTo(x, y, z, player.getYRot() + 180.0F, 0.0F);
        BlockPos home = queen.blockPosition();
        queen.finalizeSpawn(level, level.getCurrentDifficultyAt(home), MobSpawnType.COMMAND, null);
        queen.setThroneHome(home);
        level.addFreshEntity(queen);
        source.sendSuccess(() -> Component.literal(String.format("Summoned a queen at %d %d %d",
                home.getX(), home.getY(), home.getZ())), true);
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /**
     * The nearest spot in {@code nominal}'s column a player can stand, searching down from
     * the preferred Y and then up.
     *
     * <p>Reads real blocks rather than {@link ColonyNoise}, and that is the point: by the
     * time this runs the destination chunk has been generated, so the blocks are the ground
     * truth including anything the decoration pass wrote. If the column holds nothing
     * standable at all (a colony centre can land inside a solid pillar) the nominal Y comes
     * back unchanged -- the teleport is a dev command, and landing in fabric is a legible
     * failure, unlike silently landing somewhere else.
     */
    private static BlockPos standableY(ServerLevel colony, BlockPos nominal) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int best = Integer.MIN_VALUE;
        for (int y = FLOOR_TOP + 1; y <= CEILING_BOTTOM - 2; y++) {
            if (colony.getBlockState(cursor.set(nominal.getX(), y, nominal.getZ())).isAir()
                    && colony.getBlockState(cursor.set(nominal.getX(), y + 1, nominal.getZ())).isAir()
                    && !colony.getBlockState(cursor.set(nominal.getX(), y - 1, nominal.getZ())).isAir()
                    && (best == Integer.MIN_VALUE
                            || Math.abs(y - nominal.getY()) < Math.abs(best - nominal.getY()))) {
                best = y;
            }
        }
        return best == Integer.MIN_VALUE ? nominal : new BlockPos(nominal.getX(), best, nominal.getZ());
    }

    @Nullable
    private static ServerLevel colonyLevel(CommandSourceStack source) {
        ServerLevel colony = source.getServer().getLevel(ModWorldgen.FORMICARY_LEVEL);
        if (colony == null) {
            source.sendFailure(Component.literal("The Formicary dimension is not loaded"));
        }
        return colony;
    }

    /**
     * The live colony's shape functions, taken from the dimension's own generator rather
     * than rebuilt from the seed here. Answers derived from a second, independently seeded
     * copy would look entirely reasonable and point at terrain that was never generated.
     */
    @Nullable
    private static ColonyNoise colonyNoise(CommandSourceStack source) {
        ServerLevel colony = colonyLevel(source);
        if (colony == null) {
            return null;
        }
        ChunkGenerator generator = colony.getChunkSource().getGenerator();
        if (!(generator instanceof ColonyChunkGenerator colonyGenerator)) {
            source.sendFailure(Component.literal(
                    "The Formicary dimension is not using the colony generator: " + generator.getClass().getName()));
            return null;
        }
        return colonyGenerator.noise(colony.getChunkSource().randomState());
    }

    private FormicaryDevCommands() {
    }
}
