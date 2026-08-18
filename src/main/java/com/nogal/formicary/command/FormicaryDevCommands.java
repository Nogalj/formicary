package com.nogal.formicary.command;

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
 * thrown ender pearl, a descent, and (for a throne chamber) a 224-block search grid. Without
 * this, every visual check costs a play session; with it a screenshot of the queen's chamber
 * is two commands. Task A2's shot-list autopilot drives these same seams from a JSON file.
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
                                .then(Commands.literal("throne")
                                        .executes(context -> locate(context.getSource(), Chamber.THRONE)))
                                .then(Commands.literal("nursery")
                                        .executes(context -> locate(context.getSource(), Chamber.NURSERY))))
                        .then(Commands.literal("tp")
                                .then(Commands.literal("throne")
                                        .executes(context -> teleport(context.getSource(), Chamber.THRONE)))
                                .then(Commands.literal("nursery")
                                        .executes(context -> teleport(context.getSource(), Chamber.NURSERY))))
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

    /** The two chamber kinds the cell math can answer for. Garden/larder arrive in D4. */
    private enum Chamber {
        THRONE("throne chamber"),
        NURSERY("nursery chamber");

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
        BlockPos stand = found.stand();
        // Force the destination chunk through generation before the move. teleportTo does
        // add a POST_TELEPORT ticket, but an ungenerated chunk reads as air all the way down
        // (banked in docs/gotchas/events-portals.md), and a dev command that drops you into
        // an unbuilt column looks exactly like a broken chamber.
        colony.getChunk(SectionPos.blockToSectionCoord(stand.getX()), SectionPos.blockToSectionCoord(stand.getZ()));
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
        if (chamber == Chamber.THRONE) {
            ColonyNoise.Throne throne = noise.nearestThrone(x, z);
            // The chamber's centre column IS the dais, so its floor is the plinth's top --
            // the exact block ColonyChunkGenerator seats the queen on.
            BlockPos stand = new BlockPos((int) Math.round(throne.centreX()),
                    throne.floorY() + THRONE_DAIS_HEIGHT + 1, (int) Math.round(throne.centreZ()));
            return new Found(chamber, stand, Math.hypot(x - throne.centreX(), z - throne.centreZ()));
        }
        ColonyNoise.Nursery nursery = noise.nearestNursery(x, z);
        // A nursery's floor slab is at floorY, forced solid by the same function that carved
        // the room, so floorY + 1 is standable without reading a block.
        BlockPos stand = new BlockPos((int) Math.round(nursery.centreX()), nursery.floorY() + 1,
                (int) Math.round(nursery.centreZ()));
        return new Found(chamber, stand, Math.hypot(x - nursery.centreX(), z - nursery.centreZ()));
    }

    // ------------------------------------------------------------------
    // state
    // ------------------------------------------------------------------

    private static int state(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        boolean inColony = player.level().dimension() == ModWorldgen.FORMICARY_LEVEL;

        source.sendSuccess(() -> Component.literal(String.format("pos %d %d %d in %s",
                pos.getX(), pos.getY(), pos.getZ(), player.level().dimension().location())), true);
        source.sendSuccess(() -> Component.literal(String.format("tier %d (%s)%s",
                tierIndex(pos.getY()), TIER_NAMES[tierIndex(pos.getY())],
                inColony ? "" : " -- band for this Y, you are not in the colony")), true);

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
