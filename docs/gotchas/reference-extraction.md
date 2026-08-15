# Formicary gotchas -- the reference/ extraction

Moved verbatim from CLAUDE.md "Banked rules" by the 2026-08-14 /tidy-claude-md
restructure; the entry keeps its original `verified:` date.

Note: `reference/` is gitignored, so it exists only in the MAIN checkout
(`D:\MyProjects\formicary\reference`) -- worktrees under `.claude\worktrees\` do not
carry it. (`verified: 2026-08-14`)

- **`reference/` is a PARTIAL extraction** (it was seeded from ModTest's block/datagen-era
  copy). A missing class is not a missing API. Re-extract on demand from
  `build/moddev/artifacts/neoforge-21.0.167-sources.jar` with
  `[System.IO.Compression.ZipFile]::OpenRead(...)` filtered by package prefix -- M2 had to
  add `world/phys`, `client/renderer`, `world/item`, `core`, `util`, `com/mojang/math`,
  `world/level/EntityGetter.java`, `world/level/Level.java` and
  `world/entity/ai/navigation`. M3b had to add the whole of `tags/`,
  `data/tags/`, `world/level/storage/loot/` and `advancements/critereon/`, plus
  `server/level/ServerLevel.java`, `world/entity/ai/targeting/TargetingConditions.java`,
  `world/entity/player/Player.java`, `world/damagesource/DamageSources.java` and
  `net/neoforged/neoforge/common/NeoForge.java`. M3a had to add `world/InteractionResult.java`,
  `world/InteractionHand.java`, `world/item/ItemUtils.java`,
  `data/loot/EntityLootSubProvider.java` + `packs/VanillaEntityLoot.java`,
  `world/entity/EntityType.java`, `world/entity/player/Inventory.java`,
  `world/level/storage/loot/{parameters/LootContextParamSets,predicates/
  LootItemRandomChanceCondition,functions/EnchantedCountIncreaseFunction,
  functions/SetItemCountFunction,providers/number/UniformGenerator,
  entries/LootItem,LootPool,LootTable}.java` and `sounds/SoundSource.java`. M6 had to add
  `world/{Container,SimpleContainer,ContainerHelper,CompoundContainer,WorldlyContainer,
  Containers,ContainerListener}.java`,
  `world/level/block/entity/{ChestBlockEntity,BaseContainerBlockEntity,
  RandomizableContainerBlockEntity}.java`,
  `world/entity/{OwnableEntity}.java`, `world/entity/ai/goal/{GoalSelector,
  target/TargetGoal}.java` and `network/syncher/{EntityDataSerializers,
  SynchedEntityData}.java`. M7 had to add `world/BossEvent.java` (the top-level
  `net/minecraft/world/*.java` files are thin on the ground in this copy -- a sibling of an
  already-present class is no guarantee). (`verified: 2026-08-13`)
