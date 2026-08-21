package com.nogal.formicary.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nogal.formicary.block.ModBlockTags;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
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
 *       bound to and runs a one-crop shuttle: cut the nearest ripe crop
 *       ({@link HarvestCropsGoal}), replant it, walk the load back to the chest
 *       ({@link DepositToChestGoal}), then go again. Ep2 deliberately slowed this down --
 *       clearing a whole field on one trip made a single ant worth more than a farm.</li>
 * </ul>
 * Sneak-right-clicking the ant toggles between the two: a bound worker goes back to follow
 * mode, and a following worker takes the nearest storage block it can see as its anchor
 * (see {@link #bindNearestDeposit}). Right-clicking a chest while it follows you binds it
 * to that chest specifically (see {@link #bindNearestFollower}).
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

    /** How far from the player a chest click reaches to find a follower to bind. */
    public static final double BIND_RANGE = 8.0;

    /**
     * How far a sneak-click on a <em>following</em> worker looks for something to bind to.
     * Matches {@link #PATROL_RADIUS}: the storage it picks is the centre of the field it
     * will then patrol, so anything it can be bound to is somewhere it could already work.
     */
    public static final int DEPOSIT_SEARCH_RADIUS = 16;

    /**
     * Play-test round 1, spec item 2: how far a harvesting worker looks for a loose item
     * drop lying on the ground. Spec asks for "~8-12 blocks"; 10 sits in the middle.
     */
    public static final double GROUND_PICKUP_RADIUS = 10.0;

    private static final String TAG_BOUND_CHEST = "BoundChest";
    private static final String TAG_INVENTORY = "Pack";

    /**
     * The stack drawn in the mandibles. Synched rather than derived on the client, because
     * the pack itself is server-only state.
     */
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED =
            SynchedEntityData.defineId(TamedWorkerAntEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Play-test round 2: "I am against a wall this tick". See {@link AntClimbing}. */
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
            SynchedEntityData.defineId(TamedWorkerAntEntity.class, EntityDataSerializers.BOOLEAN);

    private final SimpleContainer pack = new SimpleContainer(INVENTORY_SIZE);

    @Nullable
    private BlockPos boundChest;

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
        builder.define(DATA_CLIMBING, false);
    }

    /** @see AntClimbing */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return AntClimbing.navigation(this, level);
    }

    /** @see AntClimbing */
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_CLIMBING, this.horizontalCollision);
        }
    }

    /** @see AntClimbing */
    @Override
    public boolean onClimbable() {
        return this.entityData.get(DATA_CLIMBING);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Deposit outranks harvest and pickup: whatever is in the pack has to be emptied
        // before more is cut or collected. Play-test round 2 -- the ordering alone was not
        // enough, because back then this goal sat out its retry cooldown after every single
        // delivery and nothing outranked the harvest during that window; the binding half of
        // the rule lives in HarvestCropsGoal.canUse (an empty pack, not a not-full one).
        // Round 4 (item 8) then removed the window itself: the cooldown is failure backoff
        // now, so a successful delivery arms nothing and the next trip starts at once. The
        // round-2 rule is kept regardless -- it is what makes the shuttle one crop per trip,
        // rather than merely what closed that particular window.
        this.goalSelector.addGoal(1, new DepositToChestGoal(this, 1.0));
        this.goalSelector.addGoal(2, new HarvestCropsGoal(this, 1.0));
        // Play-test round 1, spec item 2: ground pickup ranks below the crop harvest
        // (a strictly higher priority NUMBER) so it can never hold the MOVE/LOOK flags
        // against a harvest that wants them -- WrappedGoal.canBeReplacedBy only lets a
        // *lower*-numbered goal preempt a running one (verified in GoalSelector.java /
        // WrappedGoal.java), so a crop always wins a tick where both are available, and a
        // harvest that becomes possible mid-approach to a drop preempts this goal outright.
        this.goalSelector.addGoal(3, new CollectDroppedItemsGoal(this, 1.0));
        // Follow only applies in follow mode -- a bound worker has a job.
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.1, 10.0F, 2.0F) {
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
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.9));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.1F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
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
            // Play-test round 1, spec item 1: tell the owner the new state. This call site
            // is already server-only (TamedAntEvents only reaches here from a ServerLevel),
            // and a no-op on the bare Player a GameTest drives this through directly.
            owner.displayClientMessage(Component.translatable("entity.formicary.tamed_worker_ant.state.harvesting"),
                    true);
        }
        return best;
    }

    /**
     * The ant-side half of binding, and the mirror image of {@link #bindNearestFollower}:
     * instead of a clicked chest looking for a worker, a sneak-clicked <em>worker</em> looks
     * for a chest. It is what makes the sneak-click a real toggle -- the click that unbinds a
     * working ant now puts a following one to work rather than doing nothing.
     *
     * <p>A static seam for the same reason its sibling is one: the mock player a GameTest can
     * build is never added to the level, so a test cannot drive the decision through a real
     * right-click and has to call the decision itself.
     *
     * @return the block the worker bound to, or {@code null} when nothing in range qualified
     *         (in which case the worker is left following, exactly as it was)
     */
    @Nullable
    public static BlockPos bindNearestDeposit(ServerLevel level, TamedWorkerAntEntity worker, Player owner) {
        BlockPos found = findNearestDeposit(level, worker.blockPosition());
        if (found == null) {
            owner.displayClientMessage(
                    Component.translatable("entity.formicary.tamed_worker_ant.state.no_chest"), true);
            return null;
        }
        worker.bindTo(found);
        level.playSound(null, found, SoundEvents.BEEHIVE_WORK, SoundSource.NEUTRAL, 0.7F, 1.3F);
        owner.displayClientMessage(
                Component.translatable("entity.formicary.tamed_worker_ant.state.harvesting"), true);
        return found;
    }

    /**
     * The nearest {@link ModBlockTags#WORKER_DEPOSITS} block to {@code origin} inside
     * {@link #DEPOSIT_SEARCH_RADIUS}, or {@code null} if the box holds none.
     *
     * <p>A flat sweep of the {@code 33^3} box. That is 35937 block reads, which would be
     * absurd on a tick but is nothing at all at interaction frequency -- this runs once per
     * sneak-click, so a cheaper spiral would buy nothing and cost clarity.
     *
     * <p>{@code betweenClosed} hands out one reused {@code MutableBlockPos} cursor (verified
     * in the decompiled {@code BlockPos}), so the copy has to be taken <em>before</em> the
     * reduction -- holding onto a candidate across iterations otherwise means holding onto a
     * position that keeps moving.
     */
    @Nullable
    public static BlockPos findNearestDeposit(BlockGetter level, BlockPos origin) {
        return BlockPos.betweenClosedStream(
                        origin.offset(-DEPOSIT_SEARCH_RADIUS, -DEPOSIT_SEARCH_RADIUS, -DEPOSIT_SEARCH_RADIUS),
                        origin.offset(DEPOSIT_SEARCH_RADIUS, DEPOSIT_SEARCH_RADIUS, DEPOSIT_SEARCH_RADIUS))
                .filter(pos -> isDepositBlock(level.getBlockState(pos)))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                .orElse(null);
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

    /**
     * Cuts the crop at {@code pos}, replants it and pockets the drops.
     *
     * <p>The drops are computed with {@code Block.getDrops} and the block is then removed
     * with {@code destroyBlock(pos, false)} -- deliberately <em>not</em> the
     * "destroy with drops, then vacuum the item entities" route. Loot never becomes a
     * physical item that can roll into a hole, bounce off a hopper or be picked up by
     * someone else mid-flight, so what the worker banks is exactly what the loot table
     * rolled. The {@code false} keeps the break particles and sound without the drops.
     *
     * <p>Ep2: the replant is <em>guaranteed</em>, not conditional on the loot roll. A seed
     * is still taken out of the drops whenever the roll produced one -- that is what makes
     * the replant paid for rather than conjured, and it is why a bumper harvest yields one
     * fewer seed than a player's would -- but a roll that produced none (wheat rolls zero
     * seeds about 9% of the time; see {@code CropHarvest#takeSeed}) no longer leaves the
     * tile bare. Conceptually the seed never left the ground: an ant that tills a row and
     * silently loses one plant in eleven is a farm that decays, which is exactly the
     * failure a player cannot see happening until the field is gone.
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
        BlockState replant = CropHarvest.replantState(state);
        if (level.getBlockState(pos).isAir() && replant.canSurvive(level, pos)) {
            level.setBlock(pos, replant, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.7F, 1.0F);
        } else if (!seed.isEmpty()) {
            // Nowhere to put it back -- carry the seed home rather than lose it.
            drops.add(seed);
        }

        for (ItemStack stack : drops) {
            this.store(level, stack);
        }
        this.syncCarriedItem();
        return true;
    }

    /**
     * Play-test round 1, spec item 2: takes an item entity's whole stack off the ground and
     * pockets it through {@link #store} -- the same "nothing it picks up is ever destroyed"
     * contract the crop harvest already keeps, so an overflow pops back onto the floor
     * rather than vanishing. Mirrors {@code WorkerAntEntity.pickUpCarriedItem}'s vanilla
     * bookkeeping ({@code onItemPickup} + {@code take} + {@code discard}) so the pickup
     * animation and pickup statistics behave the same way the wild worker's do.
     */
    public void pickUpGroundItem(ServerLevel level, ItemEntity itemEntity) {
        if (itemEntity.getItem().isEmpty()) {
            return;
        }
        ItemStack stack = itemEntity.getItem().copy();
        this.onItemPickup(itemEntity);
        this.store(level, stack);
        this.take(itemEntity, stack.getCount());
        itemEntity.discard();
        this.syncCarriedItem();
        this.playSound(SoundEvents.ITEM_PICKUP, 0.25F, 1.6F);
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
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.isBound()) {
                this.unbind();
                this.playSound(SoundEvents.BEEHIVE_EXIT, 0.7F, 1.2F);
                // Play-test round 1, spec item 1: "you cannot tell what state they are" --
                // actionbar overlay (not chat) matches vanilla's own way of surfacing
                // transient state. Guarded to the server-side branch above so the
                // client-side call of this same method (mobInteract runs on both sides)
                // never double-sends it.
                player.displayClientMessage(
                        Component.translatable("entity.formicary.tamed_worker_ant.state.following"), true);
            } else if (bindNearestDeposit(serverLevel, this, player) == null) {
                // Nothing worth binding to: stay in follow mode, but still answer the click
                // so it is not passed on to the held item. The "no storage nearby" line was
                // already sent by the seam.
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

    /**
     * Play-test round 1, spec item 2: "tamed worker = worker". {@code TamableAnimal}'s
     * ancestor {@code Animal} already overrides this method to return a flat {@code 1 +
     * random.nextInt(3)}, completely ignoring {@code Mob#xpReward} -- so matching the wild
     * worker's reward needs this override, not a field assignment in the constructor (which
     * would compile fine and silently do nothing; verified against {@code Animal.java} in
     * {@code reference/}).
     */
    @Override
    protected int getBaseExperienceReward() {
        return WorkerAntEntity.XP_REWARD;
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
