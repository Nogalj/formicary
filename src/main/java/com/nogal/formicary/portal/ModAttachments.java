package com.nogal.formicary.portal;

import java.util.function.Supplier;

import com.nogal.formicary.Formicary;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Per-player state the portal system keeps, as NeoForge data attachments.
 *
 * <p>Verified against the decompiled 21.0.167 sources: attachment types are a real
 * registry ({@code NeoForgeRegistries.Keys.ATTACHMENT_TYPES}, id
 * {@code neoforge:attachment_types}), so they go through a {@link DeferredRegister} like
 * everything else in this mod, and the holder API is
 * {@code getData}/{@code setData}/{@code getExistingData} on {@code IAttachmentHolder}.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Formicary.MODID);

    /**
     * The overworld position of the Anthill Core the player last came in through, which is
     * where a Daylight Membrane sends them back to.
     *
     * <p>Serialised with {@code BlockPos.CODEC} so it survives a relog, and
     * {@code copyOnDeath()} so it survives dying in the colony -- respawning without it
     * would strand the player's exit route, and dying down there is the expected outcome
     * of a bad run rather than an edge case.
     *
     * <p>The default value is only ever a placeholder: every read goes through
     * {@code getExistingData}, which returns empty rather than storing the default, so
     * "never entered an anthill" stays distinguishable from "entered one at 0,0,0".
     */
    public static final Supplier<AttachmentType<BlockPos>> ENTRY_ANTHILL = ATTACHMENT_TYPES.register(
            "entry_anthill",
            () -> AttachmentType.builder(() -> BlockPos.ZERO)
                    .serialize(BlockPos.CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * The player's breadcrumb ring buffer and any lit trail (see {@link TrailPath}).
     *
     * <p>Deliberately NOT serialised. The trail is a session aid for the descent you are
     * currently on; persisting it would mean logging back in to a two-minute-old route
     * through terrain you have already left, and the buffer is rebuilt within a few seconds
     * of walking anyway.
     */
    public static final Supplier<AttachmentType<TrailPath>> TRAIL_PATH = ATTACHMENT_TYPES.register(
            "trail_path",
            () -> AttachmentType.builder(TrailPath::new).build());

    private ModAttachments() {
    }
}
