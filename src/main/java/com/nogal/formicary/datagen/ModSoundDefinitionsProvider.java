package com.nogal.formicary.datagen;

import java.util.function.Supplier;

import com.nogal.formicary.Formicary;
import com.nogal.formicary.sound.ModSounds;

import net.minecraft.data.PackOutput;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;

/**
 * Generates {@code assets/formicary/sounds.json} -- the single file that decides what every
 * mod sound event in {@link ModSounds} actually plays.
 *
 * <p><b>This is the file Logan edits to add his own audio.</b> Every entry below is
 * currently a {@code type: "event"} redirect: the mod's event resolves to the vanilla sound
 * event the mob used to call directly, so the game sounds exactly as it did before the
 * {@link ModSounds} indirection landed, with zero {@code .ogg} files in the jar. To swap in
 * a real file, change one line from
 *
 * <pre>{@code   .with(sound(vanilla(SoundEvents.SPIDER_STEP), SoundDefinition.SoundType.EVENT))}</pre>
 *
 * to
 *
 * <pre>{@code   .with(sound("formicary:entity/worker_ant/ambient"))}</pre>
 *
 * drop the file at {@code src/main/resources/assets/formicary/sounds/entity/worker_ant/ambient.ogg}
 * and run {@code .\gradlew runData}. Full walkthrough: {@code docs/SOUNDS.md}.
 *
 * <p><b>Why EVENT and not SOUND.</b> {@code SoundDefinitionsProvider#validate} runs
 * {@code helper.exists(name, PackType.CLIENT_RESOURCES, ".ogg", "sounds")} on every
 * {@code SOUND}-type entry and throws
 * {@code IllegalStateException("Found invalid sound events: ...")} when the file is absent
 * -- so a placeholder pointing at a file that does not exist yet would hard-fail
 * {@code runData}. {@code EVENT}-type entries are validated against
 * {@code BuiltInRegistries.SOUND_EVENT} instead (same class, {@code validateEvent}), which
 * is why this scaffolding survives having no audio at all.
 *
 * <p><b>Serialization note.</b> {@code SoundDefinition.Sound#serialize} strips the
 * {@code minecraft:} namespace via {@code stripMcPrefix}, so these come out as
 * {@code {"name": "entity.spider.step", "type": "event"}} -- a bare path, which the client
 * re-parses back into the {@code minecraft} namespace. Mod-namespaced names keep their
 * prefix.
 */
public class ModSoundDefinitionsProvider extends SoundDefinitionsProvider {
    public ModSoundDefinitionsProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Formicary.MODID, existingFileHelper);
    }

    @Override
    public void registerSounds() {
        // ---------------------------------------------------------- worker ant --
        borrow(ModSounds.WORKER_ANT_AMBIENT, SoundEvents.SPIDER_STEP, "worker_ant.ambient");
        borrow(ModSounds.WORKER_ANT_HURT, SoundEvents.SPIDER_HURT, "worker_ant.hurt");
        borrow(ModSounds.WORKER_ANT_DEATH, SoundEvents.SPIDER_DEATH, "worker_ant.death");

        // --------------------------------------------------------- soldier ant --
        borrow(ModSounds.SOLDIER_ANT_AMBIENT, SoundEvents.SPIDER_AMBIENT, "soldier_ant.ambient");
        borrow(ModSounds.SOLDIER_ANT_HURT, SoundEvents.SPIDER_HURT, "soldier_ant.hurt");
        borrow(ModSounds.SOLDIER_ANT_DEATH, SoundEvents.SPIDER_DEATH, "soldier_ant.death");

        // --------------------------------------------------------------- larva --
        borrow(ModSounds.LARVA_HURT, SoundEvents.SLIME_SQUISH_SMALL, "larva.hurt");
        borrow(ModSounds.LARVA_DEATH, SoundEvents.SLIME_SQUISH_SMALL, "larva.death");

        // ----------------------------------------------------------- ender ant --
        borrow(ModSounds.ENDER_ANT_AMBIENT, SoundEvents.ENDERMAN_AMBIENT, "ender_ant.ambient");
        borrow(ModSounds.ENDER_ANT_HURT, SoundEvents.ENDERMAN_HURT, "ender_ant.hurt");
        borrow(ModSounds.ENDER_ANT_DEATH, SoundEvents.ENDERMAN_DEATH, "ender_ant.death");
        borrow(ModSounds.ENDER_ANT_TELEPORT, SoundEvents.ENDERMAN_TELEPORT, "ender_ant.teleport");

        // --------------------------------------------------------------- queen --
        borrow(ModSounds.QUEEN_AMBIENT, SoundEvents.SPIDER_AMBIENT, "queen.ambient");
        borrow(ModSounds.QUEEN_HURT, SoundEvents.SPIDER_HURT, "queen.hurt");
        borrow(ModSounds.QUEEN_DEATH, SoundEvents.SPIDER_DEATH, "queen.death");
        borrow(ModSounds.QUEEN_ROAR, SoundEvents.WARDEN_ROAR, "queen.roar");
        borrow(ModSounds.QUEEN_ACID_SPIT, SoundEvents.LLAMA_SPIT, "queen.acid_spit");
        borrow(ModSounds.QUEEN_BURROW, SoundEvents.ROOTED_DIRT_BREAK, "queen.burrow");
        // GENERIC_EXPLODE is one of the few SoundEvents fields that is a
        // Holder.Reference<SoundEvent> rather than a bare SoundEvent (it is registered with
        // registerForHolder), so it needs .value() to reach the event itself.
        borrow(ModSounds.QUEEN_SLAM, SoundEvents.GENERIC_EXPLODE.value(), "queen.slam");
    }

    /**
     * Defines {@code event} as a redirect to {@code vanilla}, subtitled
     * {@code subtitles.formicary.<subtitleKey>}.
     *
     * <p>Replace a call to this with a plain {@code add(event, definition().subtitle(...)
     * .with(sound("formicary:entity/<mob>/<voice>")))} once the real file exists.
     */
    private void borrow(Supplier<SoundEvent> event, SoundEvent vanilla, String subtitleKey) {
        // getLocation() is the event's *sound* location, which for every vanilla constant
        // borrowed here is also its registry key -- SoundEvents.register(String name) is
        // register(name, name) -> createVariableRangeEvent(location). The registry key is
        // what SoundDefinitionsProvider#validateEvent looks up.
        add(event, definition()
                .subtitle("subtitles.formicary." + subtitleKey)
                .with(sound(vanilla.getLocation(), SoundDefinition.SoundType.EVENT)));
    }
}
