# Formicary gotchas -- entity models, rendering, art pipeline

Moved verbatim from CLAUDE.md ("Entity models -- art pipeline" section + the held-item
banked rule) by the 2026-08-14 /tidy-claude-md restructure; entries keep their original
`verified:` dates. Open this before ANY model/render work.

## Art pipeline

Programmer art first: the no-Blockbench pipeline from ModTest (`assets-src/models.py`
there) is the reference approach -- one python spec per model drives both the texture
painting (PIL) and orthographic QA previews (`assets-src/previews/`), then the same
numbers are hand-translated into `LayerDefinition` Java. Box-UV face rects in ModTest's
script are verified against decompiled `ModelPart.java` -- trust it over memory.
This repo now has its own `assets-src/models.py` (worker ant, M2) -- add new mobs as
another spec dict there rather than starting a new script. Run
`python assets-src\models.py` to regenerate textures + previews after edits; its
preview renderer is a real orthographic projection (rotations applied Rz*Ry*Rx, faces
depth-sorted), so what the contact sheet shows is what the game draws.
Entity textures go to `src/main/resources/assets/formicary/textures/entity/`.
`D:\MyProjects\ModTest\src\main\java\com\nogal\modtest\client\model\TarantulaModel.java`
is a known-correct 1.21 entity model reference.

## 1.21 entity-model rules (all verified in ModTest)

1. **`renderToBuffer` takes an int colour, not four floats.** `Model` declares
   `renderToBuffer(PoseStack, VertexConsumer, int, int, int color)` and `ModelPart.render`
   has only `(PoseStack, VertexConsumer, int, int)` / `(..., int color)` overloads.
2. **`new ResourceLocation(...)` is gone** -- use `ResourceLocation.fromNamespaceAndPath(ns, path)`.
3. `LayerDefinition.create(mesh, W, H)` bakes the texture resolution -- it must match the
   painted texture or UVs are wrong.
4. In `setupAnim`, write rest poses **absolutely** every frame. Reading a part's current
   `zRot` to use as a rest value accumulates drift, because nothing resets a plain
   `EntityModel`'s parts between frames (only `HierarchicalModel.animate()` does).

## Rendering

- **A plain `MobRenderer` draws NOTHING for a mob's main-hand item.** Vanilla's
  `ItemInHandLayer` requires the model to implement `ArmedModel`, which no custom
  non-humanoid does. Copy `FoxHeldItemLayer` instead: a `RenderLayer` that parks the
  PoseStack on the head pivot, follows the head rotation, then calls
  `context.getItemInHandRenderer().renderItem(entity, stack, ItemDisplayContext.GROUND,
  false, poseStack, buffer, light)`. See `client/renderer/WorkerAntCarriedItemLayer.java`.
  (`verified: 2026-08-13`)

- **A renderer can only see SYNCHED entity state -- a plain field is always the default on
  the client.** `getTextureLocation`, `RenderLayer`s and `setupAnim` all run client-side,
  where a server-written field like a `@Nullable UUID summoner` is simply `null`. Deriving
  a texture from one gives a mob that looks right in a GameTest (server) and never changes
  in-game (client), with no error anywhere. Fix: hold the flag in an
  `EntityDataAccessor<Boolean>` defined in `defineSynchedData`, and make the accessor the
  SINGLE source of truth (`isX()` returns `entityData.get(...)`) rather than a second copy
  kept in step by hand -- `entityData.set` is visible to the server's own `get`
  immediately, so server callers lose nothing. Route every writer, including
  `readAdditionalSaveData`, through one setter or a reloaded entity renders wrong.
  (Hit 2026-08-23 giving Pheromone-Horn allies the tamed soldier's yellow-tipped texture:
  `SoldierAntEntity#isAllied` was `summoner != null`, so the renderer could never see it.)
  (`verified: 2026-08-23`)
