package com.nogal.formicary.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nogal.formicary.block.ModBlockTags;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The payoff of the taming loop (spec section 4): a larva fed Royal Jelly becomes this --
 * your own worker ant, which follows you until you point it at a chest and then farms for
 * you.
 *
 * <p>Two modes, and the bound chest is the whole of the state that tells them apart:
 * <ul>
 *   <li><b>Follow mode</b> ({@code boundChest == null}) -- wolf-style: it tags along, and
 *       teleports to you when it falls too far behind.</li>
 *   <li><b>Work mode</b> -- it patrols {@link #PATROL_RADIUS} blocks around the chest it is
 *       bound to, harvests ripe crops ({@link HarvestCropsGoal}), replants from the
 *       harvested seed and carries the rest back ({@link DepositToChestGoal}).</li>
 * </ul>
 * Sneak-right-clicking the ant unbinds it back to follow mode; right-clicking a chest while
 * it follows you binds it (see {@link #bindNearestFollower}).
 *
 * <p>Nothing it picks up is ever destroyed: overflow is popped on the ground rather than
 * voided, an undeliverable load is kept rather than dumped, and the pack is emptied onto
 * the floor if the ant is killed.
 */
public class TamedWorkerAntEntity extends TamableAnimal implements TamedAnt, CarriesItem {
    /** Slots in the worker's pack. Spec: "a small internal inventory". */
    public static final int INVENTORY_SIZE = 9;

    /** Spec section 4: "patrols a radius (~16 blocks) around its bound chest". */
    public static final int PATROL_RADIUS = 16;

    /** How far above/below the chest a patrol column is read. Terraced gardens still work. */
    public static final int PATROL_VERTICAL_REACH = 3;

    /**
     * Columns read per scan call. {@code (2*16+1)^2 = 1089} columns exist, so this is ~2%
     * of the patrol area per call and a full sweep takes {@code ceil(1089/24) = 46} calls.
     * Goal {@code canUse} runs on alternating ticks (see {@code Mob.serverAiStep}), so a
     * sweep lands in roughly 92 ticks -- under five seconds -- for 120 block reads a call.
     */
    public static final int SCAN_COLUMNS_PER_CALL = 24;

    /** How far from the player a chest click reaches to find a follower to bind. */
    public static final double BIND_RANGE = 8.0;

    /**
     * How long the worker may go without harvesting before it takes what it has back to
     * the chest. Covers both of the spec's non-full deposit triggers at once: an empty
     * field never resets the counter, and a field that still has crops resets it on every
     * harvest, which makes the counter behave as the "periodic timer" too.
     */
    public static final int DEPOSIT_AFTER_IDLE_TICKS = 40;

    private static final String TAG_BOUND_CHEST = "BoundChest";
    private static final String TAG_INVENTORY = "Pack";

    /**
     * The stack drawn in the mandibles. Synched rather than derived on the client, because
     * the pack itself is server-only state.
     */
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED =
            SynchedEntityData.defineId(TamedWorkerAntEntity.class, EntityDataSerializers.ITEM_STACK);

    private final SimpleContainer pack = new SimpleContainer(INVENTORY_SIZE);

    @Nullable
    private BlockPos boundChest;

    private int idleHarvestTicks;

    public TamedWorkerAntEntity(EntityType<? extends TamedWorkerAntEntity> entityType, Level level) {
        super(entityType, level);
        this.setTame(true, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CARRIED, ItemStack.EMPTY);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Deposit outranks harvest: a full pack has to be emptied before more is cut.
        this.goalSelector.addGoal(1, new DepositToChestGoal(this, 1.0));
        this.goalSelector.addGoal(2, new HarvestCropsGoal(this, 1.0));
        // Follow only applies in follow mode -- a bound worker has a job.
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 10.0F, 2.0F) {
            @Override
            public boolean canUse() {
                return !TamedWorkerAntEntity.this.isBound() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !TamedWorkerAntEntity.this.isBound() && super.canContinueToUse();
            }
        });
        // In work mode the restriction is the chest, so this is what keeps idle wandering
        // from drifting the worker out of its own field.
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.9));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------- binding --

    /** The chest this worker deposits into, or {@code null} while it is in follow mode. */
    @Nullable
    public BlockPos getBoundChest() {
        return this.boundChest;
    }

    public boolean isBound() {
        return this.boundChest != null;
    }

    /**
     * Switches this worker to work mode, anchored at {@code chest}. The vanilla home-point
     * restriction is set a little wider than the patrol radius so the idle stroll can round
     * the edge of the field without {@code MoveTowardsRestrictionGoal} yanking it back.
     */
    public void bindTo(BlockPos chest) {
        this.boundChest = chest.immutable();
        this.restrictTo(this.boundChest, PATROL_RADIUS + 4);
        this.idleHarvestTicks = 0;
    }

    /** Back to follow mode. Whatever is in the pack stays in the pack. */
    public void unbind() {
        this.boundChest = null;
        this.clearRestriction();
    }

    /**
     * The chest-click half of binding: the nearest unbound worker of {@code owner}'s within
     * {@link #BIND_RANGE} takes {@code chest} as its anchor.
     *
     * <p>A static seam rather than event-handler-only logic so a GameTest can drive the
     * exact decision the {@code PlayerInteractEvent.RightClickBlock} handler makes -- the
     * mock player a GameTest can build is never added to the level, which is precisely the
     * kind of thing an entity search would silently disagree about.
     *
     * @return the worker that got bound, or {@code null} if none was eligible
     */
    @Nullable
    public static TamedWorkerAntEntity bindNearestFollower(ServerLevel level, Player owner, BlockPos chest) {
        UUID ownerId = owner.getUUID();
        AABB area = AABB.ofSize(owner.position(), BIND_RANGE * 2.0, BIND_RANGE * 2.0, BIND_RANGE * 2.0);
        double bestDistance = BIND_RANGE * BIND_RANGE;
        TamedWorkerAntEntity best = null;
        for (TamedWorkerAntEntity worker : level.getEntitiesOfClass(TamedWorkerAntEntity.class, area,
                ant -> ant.isAlive() && !ant.isBound() && ownerId.equals(ant.getOwnerUUID()))) {
            double distance = worker.distanceToSqr(owner);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = worker;
            }
        }
        if (best != null) {
            best.bindTo(chest);
            level.playSound(null, chest, SoundEvents.BEEHIVE_WORK, SoundSource.NEUTRAL, 0.7F, 1.3F);
        }
        return best;
    }

    /** Whether {@code state} is something a worker may be bound to and deposit into. */
    public static boolean isDepositBlock(BlockState state) {
        return state.is(ModBlockTags.WORKER_DEPOSITS);
    }

    // --------------------------------------------------------------- work --

    public SimpleContainer getPack() {
        return this.pack;
    }

    /** True once every slot holds something -- the "inventory full" deposit trigger. */
    public boolean isPackFull() {
        for (int slot = 0; slot < this.pack.getContainerSize(); slot++) {
            if (this.pack.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Ticks since this worker last cut something. Drives the deposit trip. */
    public int getIdleHarvestTicks() {
        return this.idleHarvestTicks;
    }

    /**
     * Cuts the crop at {@code pos}, replants one seed out of its own drops and pockets the
     * rest.
     *
     * <p>The drops are computed with {@code Block.getDrops} and the block is then removed
     * with {@code destroyBlock(pos, false)} -- deliberately <em>not</em> the
     * "destroy with drops, then vacuum the item entities" route. Loot never becomes a
     * physical item that can roll into a hole, bounce off a hopper or be picked up by
     * someone else mid-flight, so what the worker banks is exactly what the loot table
     * rolled. The {@code false} keeps the break particles and sound without the drops.
     *
     * @return {@code true} if something was actually harvested
     */
    public boolean harvest(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!CropHarvest.isHarvestable(state)) {
            return false;
        }

        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null));
        level.destroyBlock(pos, false);

        ItemStack seed = CropHarvest.takeSeed(drops, state.getBlock());
        if (!seed.isEmpty()) {
            BlockState replant = CropHarvest.replantState(state);
            if (level.getBlockState(pos).isAir() && replant.canSurvive(level, pos)) {
                level.setBlock(pos, replant, Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.7F, 1.0F);
            } else {
                // Nowhere to put it back -- carry the seed home rather than lose it.
                drops.add(seed);
            }
        }

        for (ItemStack stack : drops) {
            this.store(level, stack);
        }
        this.idleHarvestTicks = 0;
        this.syncCarriedItem();
        return true;
    }

    /** Pockets {@code stack}; anything that will not fit is popped on the floor, never voided. */
    public void store(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack overflow = this.pack.addItem(stack);
        if (!overflow.isEmpty()) {
            Block.popResource(level, this.blockPosition(), overflow);
        }
    }

    /**
     * Empties as much of the pack as {@code container} will take, leaving the remainder
     * where it is.
     *
     * @return {@code true} if at least one item moved
     */
    public boolean depositInto(Container container) {
        boolean moved = false;
        for (int slot = 0; slot < this.pack.getContainerSize(); slot++) {
            ItemStack stack = this.pack.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int before = stack.getCount();
            ItemStack remainder = HopperBlockEntity.addItem(null, container, stack, null);
            this.pack.setItem(slot, remainder);
            moved |= remainder.getCount() != before;
        }
        this.syncCarriedItem();
        return moved;
    }

    /** The container behind the bound chest, or {@code null} if it is gone or blocked. */
    @Nullable
    public Container getBoundContainer(Level level) {
        if (this.boundChest == null || !isDepositBlock(level.getBlockState(this.boundChest))) {
            return null;
        }
        return HopperBlockEntity.getContainerAt(level, this.boundChest);
    }

    // -------------------------------------------------------------- entity --

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.idleHarvestTicks < Integer.MAX_VALUE) {
            this.idleHarvestTicks++;
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // M8: a Fungal Bloom in hand heals the ant regardless of sneak state, matching
        // vanilla's own precedent of an item-based interaction outranking a sneak toggle
        // (e.g. feeding a wolf before its sit order is even considered).
        if (FungalBloomFeeding.tryFeed(this, player, hand)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!player.isShiftKeyDown() || !this.isOwnedByUuid(player)) {
            return super.mobInteract(player, hand);
        }
        if (!this.level().isClientSide) {
            if (this.isBound()) {
                this.unbind();
                this.playSound(SoundEvents.BEEHIVE_EXIT, 0.7F, 1.2F);
            } else {
                // Already following: nothing to unbind, but answer so the click is not
                // passed on to the held item.
                this.playSound(SoundEvents.BEEHIVE_ENTER, 0.5F, 1.4F);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** UUID comparison, not {@code isOwnedBy}: a GameTest's mock player is never in the level. */
    public boolean isOwnedByUuid(Player player) {
        return player.getUUID().equals(this.getOwnerUUID());
    }

    @Override
    public ItemStack getCarriedItem() {
        return this.entityData.get(DATA_CARRIED);
    }

    private void syncCarriedItem() {
        ItemStack display = ItemStack.EMPTY;
        for (int slot = 0; slot < this.pack.getContainerSize(); slot++) {
            ItemStack stack = this.pack.getItem(slot);
            if (!stack.isEmpty()) {
                display = stack.copy();
                break;
            }
        }
        this.entityData.set(DATA_CARRIED, display);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        for (ItemStack stack : this.pack.removeAllItems()) {
            this.spawnAtLocation(stack);
        }
        this.syncCarriedItem();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.boundChest != null) {
            compound.putLong(TAG_BOUND_CHEST, this.boundChest.asLong());
        }
        compound.put(TAG_INVENTORY, this.pack.createTag(this.level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(TAG_BOUND_CHEST)) {
            this.bindTo(BlockPos.of(compound.getLong(TAG_BOUND_CHEST)));
        } else {
            this.unbind();
        }
        this.pack.fromTag(compound.getList(TAG_INVENTORY, 10), this.level().registryAccess());
        this.syncCarriedItem();
    }

    /** Tamed ants are yours: they never wander off or despawn. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Not a breeding animal -- the only way to get one is the taming loop. */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    // -------------------------------------------------------------- sounds --

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }
}
