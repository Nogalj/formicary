package com.nogal.formicary.client;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nogal.formicary.Formicary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Drives the dev client through a scripted list of camera positions and screenshots them.
 *
 * <p>Point of the thing: a screenshot of the colony used to cost a play session -- launch,
 * load, fly, frame, F2, quit -- which is why the visual passes in this repo have been so
 * expensive. Drop a {@code run/shotlist.json} next to the client, run
 * {@code .\gradlew runClient}, and the same set of frames comes out reproducibly with no
 * hands on the keyboard. Format and usage: {@code docs/dev-tools.md}.
 *
 * <p>Client-only twice over: the whole class lives under {@code client/} and its
 * {@link EventBusSubscriber} carries {@code value = Dist.CLIENT}, so a dedicated server
 * never loads it and never registers a listener for it. Same shape as
 * {@link ModClientEvents}.
 *
 * <h2>Why a tick state machine rather than a thread</h2>
 * Everything here has to happen on the client thread in a defined order: the tp is a packet,
 * the wait is measured in ticks of chunk loading, and {@code Screenshot.grab} reads the main
 * render target. A background thread would have to marshal all three back anyway, and the
 * intermediate states ("waiting for chunks", "shot taken, settling") are exactly the states
 * a tick counter expresses for free.
 *
 * <p>{@code ClientTickEvent} is an abstract Pre/Post pair in 1.21 -- there is no bare
 * {@code ClientTickEvent} to subscribe to, the same shape the banked {@code PlayerTickEvent}
 * rule describes (verified in
 * {@code reference/net/neoforged/neoforge/client/event/ClientTickEvent.java}). Post is the
 * right half: the frame the screenshot captures is the one the tick just finished.
 *
 * <h2>Failure behaviour</h2>
 * A shot list that does not exist, does not parse, or is empty disables the autopilot for
 * the rest of the session with one log line. It never throws into the client's tick loop --
 * a broken screenshot script must not be able to take the game down with it. Chunks that
 * have not arrived by the deadline are photographed anyway: a frame of grey fog is a
 * diagnostic, a hang is not.
 */
@EventBusSubscriber(modid = Formicary.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShotListAutopilot {

    /** The script, relative to the client's game directory ({@code run/}). */
    private static final String SHOT_LIST_FILE = "shotlist.json";

    /**
     * Where the script is moved when the run finishes.
     *
     * <p>Moved rather than deleted, and moved rather than flagged in memory: the next
     * {@code runClient} is a fresh JVM, so the only thing that can tell it the run already
     * happened is the file system. Keeping the contents around makes the rename readable
     * afterwards ("this is the list those PNGs came from") instead of leaving a hole.
     */
    private static final String DONE_FILE = "shotlist.done.json";

    /** Screenshots land in {@code run/screenshots/shotlist/}. */
    private static final String OUTPUT_SUBDIR = "shotlist";

    /** Ticks to wait for chunks after a move, when an entry does not say. */
    private static final int DEFAULT_WAIT_TICKS = 40;

    /**
     * Ticks between the player appearing in a level and the first move.
     *
     * <p>The client has a player and a level well before it has a rendered world around
     * them; jumping straight to shot one reliably photographs the inside of a grey box.
     */
    private static final int WARMUP_TICKS = 60;

    /** Ticks after a grab before the next entry starts. The write is on {@code Util.ioPool}. */
    private static final int SETTLE_TICKS = 10;

    /**
     * Ticks between the last grab and {@code Minecraft#stop}.
     *
     * <p>Longer than {@link #SETTLE_TICKS} on purpose. {@code Screenshot.grab} hands the
     * actual file write to {@code Util.ioPool()}, and shutdown only gives those executors a
     * few seconds; a whole second of slack costs nothing and makes "the last PNG is missing"
     * impossible.
     */
    private static final int SHUTDOWN_TICKS = 20;

    /** One line of the script. {@code command} is optional; the rest have defaults. */
    private record Shot(double x, double y, double z, float yaw, float pitch, int waitTicks, String label,
            @Nullable String command) {
    }

    private enum Stage {
        /** The script has not been looked for yet. */
        UNREAD,
        /** Script loaded, letting the world settle before the first move. */
        WARMING_UP,
        /** Moved to the current entry, counting down its wait. */
        WAITING,
        /** Shot taken, counting down before the next entry. */
        SETTLING,
        /** Every entry done; counting down before closing the client. */
        FINISHING,
        /** Nothing more to do this session. */
        OFF
    }

    private static Stage stage = Stage.UNREAD;
    private static List<Shot> shots = List.of();
    private static int index;
    private static int countdown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stage == Stage.OFF) {
            return;
        }
        // No level, no player, no connection: nothing to drive yet. Deliberately not a
        // one-shot arm -- the client sits on the title screen first, and this is what makes
        // "on client world load" mean the moment the player actually exists.
        if (minecraft.level == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }

        switch (stage) {
            case UNREAD -> begin(minecraft);
            case WARMING_UP -> {
                if (--countdown <= 0) {
                    startEntry(minecraft);
                }
            }
            case WAITING -> {
                if (--countdown <= 0) {
                    capture(minecraft);
                }
            }
            case SETTLING -> {
                if (--countdown <= 0) {
                    index++;
                    if (index < shots.size()) {
                        startEntry(minecraft);
                    } else {
                        retireShotList(minecraft);
                        stage = Stage.FINISHING;
                        countdown = SHUTDOWN_TICKS;
                    }
                }
            }
            case FINISHING -> {
                if (--countdown <= 0) {
                    stage = Stage.OFF;
                    // stop(), not close(). stop() only clears the running flag, which drops
                    // the client out of its main loop and lets Main.main run its normal
                    // shutdown -- disconnect, save, destroy(), System.exit(0) (verified in
                    // reference/net/minecraft/client/main/Main.java:230-237). close() is the
                    // second half of that sequence on its own: called from here it would
                    // free the window and the GL resources out from under a loop that is
                    // still running, which is how a run hangs instead of exiting.
                    Formicary.LOGGER.info("Shot list: done, closing the client");
                    minecraft.stop();
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Script
    // ------------------------------------------------------------------

    private static void begin(Minecraft minecraft) {
        File file = new File(minecraft.gameDirectory, SHOT_LIST_FILE);
        if (!file.isFile()) {
            stage = Stage.OFF;
            return;
        }
        List<Shot> parsed = parse(file);
        if (parsed == null || parsed.isEmpty()) {
            // Deliberately left in place rather than retired: an unparseable list is a typo
            // to fix and re-run, not a run that happened.
            Formicary.LOGGER.error("Shot list: {} has no usable entries -- autopilot disabled", file);
            stage = Stage.OFF;
            return;
        }
        shots = parsed;
        index = 0;
        stage = Stage.WARMING_UP;
        countdown = WARMUP_TICKS;
        Formicary.LOGGER.info("Shot list: {} entries loaded from {}", shots.size(), file);
    }

    /** Parsed entries, or null if anything at all was wrong with the file. */
    @Nullable
    private static List<Shot> parse(File file) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            List<Shot> parsed = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                JsonObject entry = array.get(i).getAsJsonObject();
                parsed.add(new Shot(
                        entry.get("x").getAsDouble(),
                        entry.get("y").getAsDouble(),
                        entry.get("z").getAsDouble(),
                        optionalFloat(entry, "yaw", 0.0F),
                        optionalFloat(entry, "pitch", 0.0F),
                        entry.has("waitTicks") ? entry.get("waitTicks").getAsInt() : DEFAULT_WAIT_TICKS,
                        safeLabel(entry.has("label") ? entry.get("label").getAsString() : "shot_" + i, i),
                        entry.has("command") ? entry.get("command").getAsString() : null));
            }
            return parsed;
        } catch (Exception exception) {
            // Every parse failure is one class of problem -- the file is not the shape the
            // doc describes -- and every one of them has to end the same way: say so, and
            // leave the client alone.
            Formicary.LOGGER.error("Shot list: could not read {} -- autopilot disabled", file, exception);
            return null;
        }
    }

    private static float optionalFloat(JsonObject entry, String name, float fallback) {
        return entry.has(name) ? entry.get(name).getAsFloat() : fallback;
    }

    /**
     * A label reduced to something that can only ever name a file inside the output
     * directory. The label comes from a hand-written JSON file and is pasted into a path, so
     * a stray {@code ..} or slash in it would write wherever it liked.
     */
    private static String safeLabel(String label, int fallbackIndex) {
        String cleaned = label.replaceAll("[^A-Za-z0-9._-]", "_").replace("..", "_");
        return cleaned.isEmpty() ? "shot_" + fallbackIndex : cleaned;
    }

    // ------------------------------------------------------------------
    // Entries
    // ------------------------------------------------------------------

    private static void startEntry(Minecraft minecraft) {
        Shot shot = shots.get(index);
        // Locale.ROOT: the command parser wants '.' as the decimal point, and the default
        // locale on a European machine formats 12.5 as "12,5" -- which parses as a different
        // number of arguments, not as a bad one.
        minecraft.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f",
                shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch()));
        if (shot.command() != null) {
            minecraft.player.connection.sendCommand(shot.command());
        }
        stage = Stage.WAITING;
        countdown = Math.max(1, shot.waitTicks());
        Formicary.LOGGER.info("Shot list: entry {}/{} '{}' at {} {} {}", index + 1, shots.size(), shot.label(),
                shot.x(), shot.y(), shot.z());
    }

    /**
     * One frame to {@code run/screenshots/shotlist/<label>.png}.
     *
     * <p>The directory has to exist first: {@code Screenshot._grab} only ever does
     * {@code mkdir()} on {@code screenshots/} itself, and {@code NativeImage#writeToFile}
     * opens the target without creating parents -- so a name with a subdirectory in it fails
     * on the io pool, well away from here, with nothing but a warn line (verified in
     * {@code reference/net/minecraft/client/Screenshot.java}).
     */
    private static void capture(Minecraft minecraft) {
        Shot shot = shots.get(index);
        File output = new File(minecraft.gameDirectory, Screenshot.SCREENSHOT_DIR + "/" + OUTPUT_SUBDIR);
        if (!output.isDirectory() && !output.mkdirs()) {
            Formicary.LOGGER.error("Shot list: could not create {}", output);
        }
        Screenshot.grab(minecraft.gameDirectory, OUTPUT_SUBDIR + "/" + shot.label() + ".png",
                minecraft.getMainRenderTarget(),
                message -> Formicary.LOGGER.info("Shot list: {}", message.getString()));
        stage = Stage.SETTLING;
        countdown = SETTLE_TICKS;
    }

    /** Renames the script so the next launch is a normal client, not a re-run. */
    private static void retireShotList(Minecraft minecraft) {
        Path source = new File(minecraft.gameDirectory, SHOT_LIST_FILE).toPath();
        Path target = new File(minecraft.gameDirectory, DONE_FILE).toPath();
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            Formicary.LOGGER.info("Shot list: retired {} -> {}", source, target);
        } catch (IOException exception) {
            // Not fatal to this run -- every shot is already on disk -- but the next launch
            // would replay the list, so it is worth being loud about.
            Formicary.LOGGER.error("Shot list: could not rename {} -- the next run will replay it", source, exception);
        }
    }

    private ShotListAutopilot() {
    }
}
