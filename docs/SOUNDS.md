# Adding your own sounds

Every mob in Formicary has its own named sound events -- `formicary:entity.queen.roar` and
eighteen others. Each one is either a **real file** or a **redirect** to a vanilla sound, and
the mod started life as all redirects, which is why it sounded like something before any audio
existed. Replacing a redirect with your own recording is **one line of Java-adjacent config
plus one file** -- no new code, no recompile of anything you have to understand.

This document is the whole procedure.

> ## Read this first: three mobs are GENERATED
>
> The **worker ant, soldier ant and queen** now have real audio, and their `.ogg` files are
> **build output**. They are cut by `assets-src/sounds.py` from Logan's raw recordings in
> `assets-src/audio-src/`, the same way `blocks.py` generates every texture.
>
> **Do not hand-edit those files** -- the next run of the script overwrites them. To change
> how one of those three sounds, edit the `VOICES` table at the top of `assets-src/sounds.py`
> and re-run it:
>
> ```powershell
> uv run --with soundfile --with numpy python assets-src\sounds.py
> .\gradlew runData
> ```
>
> The table holds, per mob: which windows of the recording become the three ambient variants,
> the playback-rate offset for each, and which stretch the hurt and death are cut from. The
> constants below it control how far a hurt is pitched up and how far a death glides down.
> Everything in the rest of this document still applies to the mobs that are still redirects,
> and to adding a brand-new voice.
>
> If you record a **replacement** for one of the three, drop it in `assets-src/audio-src/`
> under the same name and re-run the script -- not into the resources tree.

---

## 1. The file format

Minecraft only plays **Ogg Vorbis** (`.ogg`). Not `.mp3`, not `.wav`. Any audio editor can
export it.

There is one gotcha that catches everyone, and it is not the format:

> **The file must be MONO, not stereo.**

Minecraft plays positional audio through OpenAL, and **OpenAL can only place a mono source in
3D space**. A stereo file already has two baked-in channels, so the engine has nothing to
reposition -- it just plays it at full volume, everywhere, no matter how far away the ant is.
A stereo queen roar sounds like it is inside your head from 200 blocks away.

### Converting to mono in Audacity (free)

1. Open your recording.
2. If the waveform shows **two** stacked channels, it is stereo. Fix it:
   **Tracks > Mix > Mix Stereo Down to Mono**. The two lanes collapse into one.
3. **File > Export > Export as OGG** (older Audacity), or **File > Export Audio...** and
   choose *Ogg Vorbis* in the format dropdown (Audacity 3.2+).
4. Quality 5-6 is plenty for a mob noise. Save it to the path in the table below.

Keep it short. Vanilla mob sounds are mostly well under two seconds.

---

## 2. Where the file goes

Everything lives under `src/main/resources/assets/formicary/sounds/`. Three mob folders are
there already (generated -- see the callout at the top); create any others you need.

The path is derived from the name you will write in step 3: `formicary:entity/queen/roar`
becomes `assets/formicary/sounds/` + `entity/queen/roar` + `.ogg`. **Slashes, not dots** --
dots would give you one flat file literally named `entity.queen.roar.ogg`.

Tamed ants deliberately share their wild caste's voice: the tamed worker uses the worker's
events, the tamed soldier uses the soldier's. One file covers both.

| Sound event | File (under `src/main/resources/assets/formicary/sounds/`) | Source |
|---|---|---|
| `formicary:entity.worker_ant.ambient` | `entity/worker_ant/ambient1..3.ogg` | **generated** (3 variants) |
| `formicary:entity.worker_ant.hurt` | `entity/worker_ant/hurt1..2.ogg` | **generated** (2 variants) |
| `formicary:entity.worker_ant.death` | `entity/worker_ant/death.ogg` | **generated** |
| `formicary:entity.soldier_ant.ambient` | `entity/soldier_ant/ambient1..3.ogg` | **generated** (3 variants) |
| `formicary:entity.soldier_ant.hurt` | `entity/soldier_ant/hurt1..2.ogg` | **generated** (2 variants) |
| `formicary:entity.soldier_ant.death` | `entity/soldier_ant/death.ogg` | **generated** |
| `formicary:entity.larva.hurt` | `entity/larva/hurt.ogg` | `entity.slime.squish_small` |
| `formicary:entity.larva.death` | `entity/larva/death.ogg` | `entity.slime.squish_small` |
| `formicary:entity.ender_ant.ambient` | `entity/ender_ant/ambient.ogg` | `entity.enderman.ambient` |
| `formicary:entity.ender_ant.hurt` | `entity/ender_ant/hurt.ogg` | `entity.enderman.hurt` |
| `formicary:entity.ender_ant.death` | `entity/ender_ant/death.ogg` | `entity.enderman.death` |
| `formicary:entity.ender_ant.teleport` | `entity/ender_ant/teleport.ogg` | `entity.enderman.teleport` |
| `formicary:entity.queen.ambient` | `entity/queen/ambient1..3.ogg` | **generated** (3 variants) |
| `formicary:entity.queen.hurt` | `entity/queen/hurt1..2.ogg` | **generated** (2 variants) |
| `formicary:entity.queen.death` | `entity/queen/death.ogg` | **generated** |
| `formicary:entity.queen.roar` | `entity/queen/roar.ogg` | `entity.warden.roar` |
| `formicary:entity.queen.acid_spit` | `entity/queen/acid_spit.ogg` | `entity.llama.spit` |
| `formicary:entity.queen.burrow` | `entity/queen/burrow.ogg` | `block.rooted_dirt.break` |
| `formicary:entity.queen.slam` | `entity/queen/slam.ogg` | `entity.generic.explode` |

Notes on the odd ones:

- **The larva has no `ambient`** on purpose. A grub wriggling in place should read as silent.
- **`queen.burrow` is played twice**: once when she goes under, once when she comes back up
  (quieter). One file covers both -- the volume and pitch at each call site do the rest.
- **`queen.ambient` doubles as her phase-change burst**, played louder and lower. Same idea.
- Sounds that are *interface feedback* rather than a mob's voice -- item pickup, the beehive
  chirps for binding a worker to a chest, planting a crop, the portal whoosh -- are still
  vanilla constants in Java and are **not** in this system. They are not mob voices.

---

## 3. The one-line swap

Open **`src/main/java/com/nogal/formicary/datagen/ModSoundDefinitionsProvider.java`**.

You will find a list of lines like this:

```java
borrow(ModSounds.QUEEN_ROAR, SoundEvents.WARDEN_ROAR, "queen.roar");
```

Replace that line with:

```java
add(ModSounds.QUEEN_ROAR, definition()
        .subtitle("subtitles.formicary.queen.roar")
        .with(sound("formicary:entity/queen/roar")));
```

That is the entire change. `borrow(...)` is just a shorthand for "point this event at a vanilla
one"; `add(...)` with a `sound("formicary:...")` points it at your file instead. Keep the
`.subtitle(...)` line exactly as it was -- that is the text shown when subtitles are on, and it
already says the right thing.

In practice the file has a shorter helper for exactly this, `voice(...)`, which builds the same
thing from a mob name and a list of files:

```java
voice(ModSounds.QUEEN_ROAR, "queen", "roar", "roar");
//    event                  folder   subtitle  file(s)
```

Listing more than one file is how a voice gets random variation -- see section 6.

Then regenerate `sounds.json`:

```powershell
$env:JAVA_HOME = "C:\Users\Family\.jdks\jdk-21.0.11+10"
.\gradlew runData
```

That rewrites `src/generated/resources/assets/formicary/sounds.json`. Commit both the new
`.ogg` and the regenerated JSON.

---

## 4. Why `runData` fails when the file is not there

This is the number-one confusion, so it gets its own section.

`runData` **verifies that every sound file you reference actually exists on disk.** If you do
the swap in step 3 but have not yet saved the `.ogg` -- or you typed the path with dots instead
of slashes, or put it in the wrong folder -- the build stops with:

```
[main/WARN] [ne.ne.ne.co.da.SoundDefinitionsProvider/]: Unable to find corresponding OGG file
'formicary:sounds/entity/queen/roar.ogg' for sound event 'entity.queen.roar'
...
Caused by: java.lang.IllegalStateException: Found invalid sound events: [formicary:entity.queen.roar]
```

(That is copied from a real failure, not paraphrased -- the `ne.ne.ne.co.da.` prefix is just
Gradle abbreviating `net.neoforged.neoforge.common.data`.)

That is not a bug. The `WARN` line above the exception tells you the **exact path it looked
for** -- compare it against where your file actually is and the mismatch is usually obvious.

The redirects the mod ships with dodge this entirely: a redirect is checked against the *sound
registry* instead of the filesystem, which is why `runData` is green today with no audio files
at all.

**Order of operations: save the `.ogg` first, then edit the provider, then run `runData`.**

---

## 5. Hearing it

```powershell
$env:JAVA_HOME = "C:\Users\Family\.jdks\jdk-21.0.11+10"
.\gradlew runClient
```

Then in-game, to trigger a sound on demand without hunting down a queen:

```
/playsound formicary:entity.queen.roar master @s
```

`master` is the volume category and `@s` is you. Tab-completion lists every sound the game
knows about, so if your event does not appear there, `sounds.json` did not regenerate.

### Iterating without restarting

Re-exported the file and want to hear the new take?

1. Overwrite the `.ogg` in `src/main/resources/...`.
2. In another terminal: `.\gradlew processResources` (the dev client reads from
   `build/resources/main`, not from `src/`).
3. In the client, press **F3 + T** to reload resources.

No restart needed. This works for the audio file and for `sounds.json` itself. **Java changes
still need a client restart** -- but adding a sound is not a Java change, which is the whole
point of this setup.

---

## 6. Extras you may want later

All of these go inside the same `add(...)` call in `ModSoundDefinitionsProvider`.

**Random variations.** List several sounds in one definition and the game picks one at random
each time -- this is how vanilla keeps a mob's idle noise from getting repetitive, and it is
what the three ants use:

```java
voice(ModSounds.QUEEN_ROAR, "queen", "roar", "roar1", "roar2", "roar3");
```

which expands to:

```java
add(ModSounds.QUEEN_ROAR, definition()
        .subtitle("subtitles.formicary.queen.roar")
        .with(sound("formicary:entity/queen/roar1"),
              sound("formicary:entity/queen/roar2"),
              sound("formicary:entity/queen/roar3")));
```

Worth knowing: variation from *pitch alone* is weak. The generator makes the ants' three
ambients from three different windows of the same recording, so each one has genuinely
different content, and the pitch offset is only +-6% on top. Minecraft then applies its own
+-20% random pitch at playback, which is why baking in a large offset is counterproductive --
it just makes one variant sound like a different animal.

**Per-file tweaks.** Chain these onto any individual `sound(...)`:

| Method | Default | What it does |
|---|---|---|
| `.volume(0.8)` | `1.0` | Loudness of *this* file. Must be greater than 0. |
| `.pitch(1.2)` | `1.0` | Playback speed/pitch of this file. Must be greater than 0. |
| `.weight(3)` | `1` | How often this variation is picked, relative to the others. |
| `.attenuationDistance(32)` | `16` | Roughly how many blocks the sound carries before it fades out. Bigger = audible from further away. Good for the queen. |
| `.stream(true)` | `false` | Decode on the fly instead of loading whole. Only for long tracks (music), never for a mob noise. |

```java
.with(sound("formicary:entity/queen/roar").volume(1.0).attenuationDistance(48))
```

Note that these stack *on top of* the volume and pitch the Java call site already passes -- the
queen's roar is played at volume 2.5 and pitch 0.7 in `QueenAntEntity`, and that is unchanged
by anything here.

**Changing a subtitle.** The text lives in
`src/main/java/com/nogal/formicary/datagen/ModLanguageProvider.java`, keyed
`subtitles.formicary.<mob>.<voice>`. Edit it there, run `runData`, done.

---

## Where the pieces live

| File | What it is |
|---|---|
| `src/main/java/com/nogal/formicary/sound/ModSounds.java` | Declares the 19 sound events. Only touch this to add a *new* voice. |
| `src/main/java/com/nogal/formicary/datagen/ModSoundDefinitionsProvider.java` | **The file you edit.** Decides what each event plays. |
| `src/main/java/com/nogal/formicary/datagen/ModLanguageProvider.java` | Subtitle text. |
| `src/generated/resources/assets/formicary/sounds.json` | Generated -- never edit by hand; `runData` overwrites it. |
| `src/main/resources/assets/formicary/sounds/...` | Where `.ogg` files go. The three ant folders are GENERATED -- do not hand-edit. |
| `assets-src/audio-src/` | The raw recordings the generated sounds are cut from. Source of truth. |
| `assets-src/sounds.py` | Generates the three ants' 18 files. Edit its `VOICES` table to retune. |
