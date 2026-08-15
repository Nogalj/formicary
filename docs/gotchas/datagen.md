# Formicary gotchas -- datagen (loot tables, recipes, advancement criteria)

Moved verbatim from CLAUDE.md "Banked rules" by the 2026-08-14 /tidy-claude-md
restructure; each entry keeps its original `verified:` date. Routed by the symptom
index in CLAUDE.md -- when a new datagen/loot/recipe/advancement rule is caught, bank
it here and add a symptom line to that index.

- **Datagen `BlockLootSubProvider.getKnownBlocks()` defaults to EVERY block in the game**
  (`BuiltInRegistries.BLOCK`) -- leave it unoverridden and runData throws "Missing
  loottable" for all vanilla blocks. Always override it to return this mod's
  `ModBlocks.BLOCKS.getEntries()`. (`verified: 2026-08-13`)
- **Custom loot conditions:** register a `LootItemConditionType(MapCodec<...>)` into
  `Registries.LOOT_CONDITION_TYPE` via `DeferredRegister`; copy vanilla's
  `LootItemKilledByPlayerCondition` shape (stateless singleton + `MapCodec.unit`). The
  player who broke a block arrives as `LootContextParams.THIS_ENTITY`, which
  `LootContextParamSets.BLOCK` declares **optional** -- use `getParamOrNull`, and declare
  it in `getReferencedContextParams()` or datagen validation rejects the table.
  (`verified: 2026-08-13`)
- **`EntityLootSubProvider.getKnownEntityTypes()` defaults to EVERY entity type in the
  game** (`BuiltInRegistries.ENTITY_TYPE`) -- the same trap as
  `BlockLootSubProvider.getKnownBlocks()` (above), and it throws "Missing loottable" for
  every vanilla mob if left unoverridden. Always override it to return just this mod's
  `EntityType`s. `EntityType#getDefaultLootTable()` derives the table id as
  `entities/<namespace>/<path>` automatically, so `add(EntityType, builder)` (no explicit
  key) is enough. (`verified: 2026-08-13`)
- **`RecipeProvider`'s hook is `protected void buildRecipes(RecipeOutput)`** (the
  `(RecipeOutput, HolderLookup.Provider)` overload just delegates), and its constructor takes
  the `CompletableFuture<HolderLookup.Provider>` from `GatherDataEvent#getLookupProvider`,
  not a resolved provider. Output folders are `data/<ns>/recipe/` (singular, like
  `loot_table`) and `data/<ns>/advancement/recipes/<category>/`. (`verified: 2026-08-13`)
- **`ShapedRecipeBuilder.pattern`'s empty-slot character is a literal space (`' '`), not
  `#`** -- only `' '` is special-cased (and reserved: `.define(' ', ...)` throws); any other
  character, `#` included, must be `.define`d or `runData` fails with "Pattern references
  symbol '#' but it's not defined in the key". (`verified: 2026-08-14`)
- **A custom advancement `CriterionTrigger` registers into `Registries.TRIGGER_TYPE`
  (`BuiltInRegistries.TRIGGER_TYPES`) via `DeferredRegister`** -- the same shape
  `ModLootConditions` already uses for `Registries.LOOT_CONDITION_TYPE`. Vanilla's own
  `CriteriaTriggers` registers each trigger the same way with a bare `new` instead of a
  deferred constructor reference. A trigger class extends `SimpleCriterionTrigger<T>` (T a
  record implementing `SimpleCriterionTrigger.SimpleInstance`, at minimum an `Optional
  <ContextAwarePredicate> player()`); firing is `myTrigger.get().trigger(serverPlayer, ...)`
  from the gameplay code that earns it. `Advancement`'s own codec rejects an empty criteria
  map, so even a root advancement needs at least one -- vanilla's `story/root` uses
  `PlayerTrigger.TriggerInstance.tick()`, a criterion granted the moment the player exists,
  for exactly this reason. (`verified: 2026-08-14`)
