# Formicary gotchas -- items, blocks, armor, attachments, containers, crops

Moved verbatim from CLAUDE.md "Banked rules" by the 2026-08-14 /tidy-claude-md
restructure, re-flowed 2026-08-15 to take in the play-test round 1 rules; each entry
keeps its original `verified:` date. Routed by the symptom index in CLAUDE.md.

- **1.21 armor materials are a REGISTRY, not an enum.** `Registries.ARMOR_MATERIAL`;
  vanilla builds each entry with `Registry.registerForHolder(...)` in `ArmorMaterials`,
  and `ArmorItem` takes a `Holder<ArmorMaterial>` -- so a `DeferredRegister` +
  `DeferredHolder` is the mod-side equivalent and no ordering dance is needed
  (`ArmorItem` memoises its attribute modifiers, so it never dereferences the holder
  during registration). The `ArmorMaterial.Layer` asset name resolves to
  `<ns>:textures/models/armor/<path>_layer_1.png` (outer: HEAD/CHEST/FEET) and
  `_layer_2.png` (inner: LEGS only -- `HumanoidArmorLayer.usesInnerModel` returns true
  just for `EquipmentSlot.LEGS`). Humanoid overlay UV rects are 64x32 with head at
  `texOffs(0,0) 8x8x8`, body `(16,16) 8x12x4`, arm `(40,16) 4x12x4`, leg `(0,16) 4x12x4`.
  (`verified: 2026-08-13`)
- **Data attachments are a real registry**, `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`, so
  `DeferredRegister.create(...)` + registering `AttachmentType.builder(...).build()` is the
  pattern. Three traps: `getData` *stores the default in the holder* and so can never mean
  "absent" -- use `getExistingData` for optional state; `copyOnDeath()` throws unless a
  serializer was set first, and without it a serialised attachment survives relogs but NOT
  respawns; and `serialize(Codec)` is the easy route (`BlockPos.CODEC` exists).
  (`verified: 2026-08-13`)
- **Container transfer: `HopperBlockEntity.getContainerAt(Level, BlockPos)` and
  `addItem(@Nullable Container source, Container dest, ItemStack, @Nullable Direction)` are
  the public entry points** (the first handles double chests via `ChestBlock.getContainer`).
  `tryMoveInItem` hands the destination the *same* `ItemStack` object when the target slot is
  empty and returns `ItemStack.EMPTY`, so the caller MUST write the returned remainder back
  into its own slot or the two containers alias one stack. (`verified: 2026-08-13`)
- **A `CropBlock` subclass needing a non-default age range must override
  `createBlockStateDefinition` too, not just `getAgeProperty`/`getMaxAge`.**
  `CropBlock.createBlockStateDefinition` does `builder.add(AGE)` -- a direct reference to
  `CropBlock`'s own *static* `AGE` field (`AGE_7`), not `this.getAgeProperty()`. A field
  reference inside an inherited, unoverridden method is not virtual, so skipping this
  override silently builds the block with wheat's 8-value property while every other method
  asks for the subclass's own one -- caught at registration with "Cannot get property ...
  does not exist in Block". `BeetrootBlock` re-overrides this for the identical reason, and
  is the pattern to copy wholesale (`getAgeProperty`, `getMaxAge`, `getBaseSeedId`,
  `randomTick`, `createBlockStateDefinition`, `getShape` if the age count changed the
  render height too). (`verified: 2026-08-14`)
- **`CropBlock.getGrowthSpeed` is `static`, so a subclass cannot override it virtually** --
  `CropBlock.randomTick` calls it unqualified, which resolves at compile time to
  `CropBlock`'s own copy regardless of the runtime type. To retune growth speed, override
  `randomTick` itself and gate the `super.randomTick(...)` call behind an extra random
  check -- `BeetrootBlock` is vanilla's own proof of this exact technique
  (`random.nextInt(3) != 0`). (`verified: 2026-08-14`)
- **`DropExperienceBlock` decouples a block's XP from its loot table entirely.** Its
  `getExpDrop()` is read by a NeoForge `BlockDropsEvent`, fired from
  `CommonHooks.handleBlockDrops` on every `Block.dropResources` call, independently of
  whatever the loot table drops as items -- so a block can have an empty (or silk-touch-
  only) loot table and still pop XP on every break. Silk Touch forfeits that XP
  unconditionally regardless of the block's own data: `Enchantments.java`'s `SILK_TOUCH`
  registration attaches a `BLOCK_EXPERIENCE` effect of
  `SetValue(LevelBasedValue.constant(0.0F))` to every silk-touched break in the game.
  `UniformInt.of(3, 7)` is the exact range vanilla constructs diamond/emerald ore with.
  (`verified: 2026-08-15`)
