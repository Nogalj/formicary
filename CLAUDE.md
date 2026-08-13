# CLAUDE.md -- Formicary

A NeoForge Minecraft mod: an ant-colony dimension entered by throwing an ender pearl at a
savanna anthill; four ant mobs; a larva-raising loop that yields crop-harvesting worker
ants. Spec: `D:\MyProjects\_notes\formicary-build-prompt.md`. Decisions log: `docs/DECISIONS.md`.

This file tells Claude Code how the repo is wired and the correctness rules to follow.
Keep it updated: every time a version-specific mistake is caught, bank the fix here as a rule.

## Project facts

- Loader: **NeoForge** | MC: **1.21** | NeoForge: **21.0.167** | Java: **21** (Gradle toolchain)
- Mod id: `formicary` | Main class: `com.nogal.formicary.Formicary` (annotated `@Mod`)
- Mappings: Mojang official (mojmap) at runtime + ParchmentMC `2024.11.10` parameter names/Javadoc
- Metadata is GENERATED, not hand-written: the `generateModMetadata` task expands
  `${mod_id}` / `${mod_name}` / `${mod_version}` etc. from `gradle.properties` into
  `src/main/templates/META-INF/neoforge.mods.toml`. To change mod identity, edit
  `gradle.properties` -- do NOT hardcode values into the toml.

## Build / run (PowerShell, from repo root)

CLI sessions must set `$env:JAVA_HOME = "C:\Users\Family\.jdks\jdk-21.0.11+10"` first --
the shell default is JVM 8 and Gradle refuses to launch (`verified: 2026-08-01`).

- Build + compile-check:   `.\gradlew build`
- Run client:              `.\gradlew runClient`
- Run dedicated server:    `.\gradlew runServer`
- Generate data (JSON):    `.\gradlew runData`   (outputs to `src/generated/resources`)
- Headless GameTests:      `.\gradlew runGameTestServer`

GameTests are enabled in the dev runs via `neoforge.enabledGameTestNamespaces` (derived
from `project.mod_id` in `build.gradle`, so the namespace is `formicary`). After ANY edit,
run `.\gradlew build` and fix every compile error before claiming a task is done -- the
compiler is the source of truth, not training memory.

GameTest facts (verified 2026-08-01 in ModTest, same pins): there is NO built-in empty
structure template -- `@GameTest(template=...)` needs a real `.nbt` at
`data/formicary/structure/<name>.nbt` (ModTest generates its 5x3x5 `platform.nbt` with a
python NBT writer -- copy that approach). Annotate the test class
`@GameTestHolder(Formicary.MODID)` and methods `@PrefixGameTestTemplate(false)` or the
template resolves under the wrong namespace/class-name prefix. GameTestServer runs at
NORMAL difficulty (Monsters safe).
1.21 datapack folders: `loot_table` (singular) and `neoforge/biome_modifier`.

## Ground truth -- do NOT trust training memory for API signatures

Minecraft/NeoForge APIs change every version, and training data blends Forge/Fabric/
NeoForge across many versions. Before using any MC/NeoForge symbol, verify against:

1. The decompiled, Parchment-named sources in `reference/` (gitignored; copied from
   ModTest's extraction -- identical version pins). Grep real signatures there.
2. Official docs: https://docs.neoforged.net/docs/ (set the version switcher to 1.21).
3. The official 1.21 MDK: https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle

If a signature cannot be verified, say so -- never invent one.

## Hard rules (footguns)

- Mappings are Mojang official at runtime. NEVER use old Forge SRG names (`func_*`, `field_*`).
- Register all content with **DeferredRegister** (see `Formicary.java` for the pattern) and
  register each DeferredRegister to the mod event bus in the constructor.
- **World/Level is NOT thread-safe.** Any world modification off the main thread must be
  marshalled onto the server thread: `server.execute(...)` or an event's `enqueueWork(...)`.
- Respect sides: keep client-only code (rendering, `Minecraft.getInstance()`, screens) out
  of common/server paths -- gate it with `Dist.CLIENT`.
- Prefer **NeoForge events/hooks** over Mixins. Only reach for a Mixin when no event exists,
  and flag it for review.
- Prefer **data-driven content** (JSON via datagen / `runData`) over hardcoded Java.
- Item data uses the **1.21 Data Components** system, not pre-1.21 NBT.

## Entity models -- art pipeline

Programmer art first: the no-Blockbench pipeline from ModTest (`assets-src/models.py`
there) is the reference approach -- one python spec per model drives both the texture
painting (PIL) and orthographic QA previews (`assets-src/previews/`), then the same
numbers are hand-translated into `LayerDefinition` Java. Box-UV face rects in ModTest's
script are verified against decompiled `ModelPart.java` -- trust it over memory.
Run `python assets-src\models.py` to regenerate textures + previews after edits.
Entity textures go to `src/main/resources/assets/formicary/textures/entity/`.
`D:\MyProjects\ModTest\src\main\java\com\nogal\modtest\client\model\TarantulaModel.java`
is a known-correct 1.21 entity model reference.

1.21 entity-model rules (all verified in ModTest):

1. **`renderToBuffer` takes an int colour, not four floats.** `Model` declares
   `renderToBuffer(PoseStack, VertexConsumer, int, int, int color)` and `ModelPart.render`
   has only `(PoseStack, VertexConsumer, int, int)` / `(..., int color)` overloads.
2. **`new ResourceLocation(...)` is gone** -- use `ResourceLocation.fromNamespaceAndPath(ns, path)`.
3. `LayerDefinition.create(mesh, W, H)` bakes the texture resolution -- it must match the
   painted texture or UVs are wrong.
4. In `setupAnim`, write rest poses **absolutely** every frame. Reading a part's current
   `zRot` to use as a rest value accumulates drift, because nothing resets a plain
   `EntityModel`'s parts between frames (only `HierarchicalModel.animate()` does).

## Banked rules (caught during this build)

- **Datagen `BlockLootSubProvider.getKnownBlocks()` defaults to EVERY block in the game**
  (`BuiltInRegistries.BLOCK`) -- leave it unoverridden and runData throws "Missing
  loottable" for all vanilla blocks. Always override it to return this mod's
  `ModBlocks.BLOCKS.getEntries()`. (`verified: 2026-08-13`)
- **Live texture/JSON iteration without restarting the client:** edit assets ->
  `.\gradlew processResources` -> F3+T in the running client (dev runs read
  `build/resources/main`). Java class changes still need a client restart.
  (`verified: 2026-08-13`)

## Workflow

- Conventional commits, one per logical step. Commit as you go; never leave finished work
  uncommitted. Never tag or push unless explicitly asked.
- Work milestone by milestone (M0-M8 in the spec); don't start M(n+1) with M(n) broken.
