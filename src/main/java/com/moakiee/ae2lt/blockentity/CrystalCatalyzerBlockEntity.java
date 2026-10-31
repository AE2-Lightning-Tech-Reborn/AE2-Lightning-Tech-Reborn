package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.me.ManagedGridNode;
import appeng.me.helpers.BlockEntityNodeListener;

import com.moakiee.ae2lt.block.CrystalCatalyzerBlock;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.transfer.AdjacentItemAutoExportHelper;
import com.moakiee.ae2lt.logic.config.MemoryCardConfigSupport;
import com.moakiee.ae2lt.machine.common.GridRecipeMachineHost;
import com.moakiee.ae2lt.machine.common.LightningCollapseMatrixHost;
import com.moakiee.ae2lt.machine.common.MachineRedstoneControl;
import com.moakiee.ae2lt.machine.common.RedstoneControlledMachine;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerAutomationInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerFluidHandler;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerLogic;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerLockedRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeCandidate;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryEnergyStorage;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.util.NativeStackDropHelper;

public class CrystalCatalyzerBlockEntity extends AENetworkedBlockEntity
        implements IActionHost, RedstoneControlledMachine,
        LightningCollapseMatrixHost,
        GridRecipeMachineHost<CrystalCatalyzerLockedRecipe, CrystalCatalyzerRecipeCandidate> {

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_TANK = "Tank";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_LOCKED_RECIPE = "LockedRecipe";
    private static final String TAG_AUTO_EXPORT = "AutoExport";
    private static final String TAG_ALLOWED_OUTPUTS = "AllowedOutputs";
    private static final String TAG_MODE = "Mode";

    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int FLUID_TANK_CAPACITY_MB = 16_000;
    public static final int MATRIX_OUTPUT_MULTIPLIER = 8;
    /** Legacy default; explicit recipe fluids are also consumed once per cycle. */
    public static final int FIXED_FLUID_PER_CYCLE_MB = CrystalCatalyzerRecipe.DEFAULT_FLUID_PER_CYCLE_MB;

    public static FluidStack getFixedFluidPerCycle() {
        return CrystalCatalyzerRecipe.defaultFluidInput();
    }

    private Mode mode = Mode.CRYSTAL;

    private final CrystalCatalyzerInventory inventory =
            new CrystalCatalyzerInventory(this::onInventoryChanged, this::getMode, this::getCatalyzerSettings);
    private final CrystalCatalyzerAutomationInventory automationInventory =
            new CrystalCatalyzerAutomationInventory(inventory);
    private final NotifyingFluidTank tank =
            new NotifyingFluidTank(FLUID_TANK_CAPACITY_MB,
                    fluid -> !getCatalyzerSettings().waterOnly() || fluid.getFluid() == Fluids.WATER, this::onTankChanged);
    private final CrystalCatalyzerFluidHandler fluidHandler =
            new CrystalCatalyzerFluidHandler(tank);
    private final OverloadProcessingFactoryEnergyStorage energyStorage =
            new OverloadProcessingFactoryEnergyStorage(ENERGY_CAPACITY, this::onEnergyChanged);
    private final MachineRedstoneControl redstoneControl = new MachineRedstoneControl(
            ModBlocks.CRYSTAL_CATALYZER, getCatalyzerSettings().standalone() ? 0 : 1,
            () -> level != null && level.hasNeighborSignal(worldPosition), this::onUpgradesChanged);
    private final CrystalCatalyzerLogic logic;
    // Only the normal registered subtype implements FrequencyBindingHost.
    protected final FrequencyBindingHelper frequencyBinding = this instanceof FrequencyBindingHost host
            ? new FrequencyBindingHelper(host) : null;
    private int legacyUnboundFrequencyId = -1;

    private CrystalCatalyzerLockedRecipe lockedRecipe;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean autoExport;
    private EnumSet<RelativeSide> allowedOutputs = EnumSet.noneOf(RelativeSide.class);
    private final AdjacentItemAutoExportHelper.DirectionalTargetCache exportTargetCache =
            new AdjacentItemAutoExportHelper.DirectionalTargetCache();

    public CrystalCatalyzerBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.CRYSTAL_CATALYZER.get(), pos, blockState);
    }

    protected CrystalCatalyzerBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
            BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.logic = new CrystalCatalyzerLogic(this);
        if (!getCatalyzerSettings().standalone()) {
            getMainNode()
                    .setIdlePowerUsage(0)
                    .addService(IGridTickable.class, logic);
        }
    }

    @Override
    protected IManagedGridNode createMainNode() {
        if (!getCatalyzerSettings().standalone()) {
            return super.createMainNode();
        }
        // Retain the shared AE block/menu lifecycle, but never create a standalone grid node.
        return new ManagedGridNode(this, BlockEntityNodeListener.INSTANCE) {
            @Override
            public void create(Level level, BlockPos pos) {
            }

            @Override
            public void loadFromNBT(CompoundTag tag) {
                // Old standalone proxy data no longer represents a network node.
            }
        };
    }

    public com.moakiee.ae2lt.api.machine.CrystalCatalyzerSettings getCatalyzerSettings() {
        return com.moakiee.ae2lt.api.machine.CrystalCatalyzerSettings.DEFAULT;
    }

    /** An addon may translate its own retired recipe IDs without changing the shared recipe registry. */
    protected CrystalCatalyzerLockedRecipe migrateLockedRecipe(CrystalCatalyzerLockedRecipe recipe, CompoundTag data) {
        return recipe;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystalCatalyzerBlockEntity be) {
        if (!level.isClientSide()) {
            if (be.getCatalyzerSettings().standalone()) {
                be.logic.tickStandalone();
            } else if (be.frequencyBinding != null) {
                be.frequencyBinding.serverTick();
            }
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (frequencyBinding != null) frequencyBinding.onMainNodeStateChanged(reason);
    }

    public CrystalCatalyzerInventory getInventory() {
        return inventory;
    }

    @Override
    public IItemHandlerModifiable getMatrixInventory() {
        return inventory;
    }

    @Override
    public int getMatrixSlot() {
        return CrystalCatalyzerInventory.SLOT_MATRIX;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public NotifyingFluidTank getTank() {
        return tank;
    }

    public FluidStack getFluid() {
        return tank.getFluid().copy();
    }

    /** GUI 流体槽交互:光标容器 → tank。成功返回 true。 */
    public boolean tryInsertFluidFromCarried(Player player) {
        boolean changed = com.moakiee.ae2lt.logic.transfer.FluidTankInteractionHelper.insertFromCarried(player, tank);
        if (changed) {
            saveChanges();
        }
        return changed;
    }

    /** GUI 流体槽交互:tank → 光标容器。成功返回 true。 */
    public boolean tryExtractFluidToCarried(Player player) {
        boolean changed = com.moakiee.ae2lt.logic.transfer.FluidTankInteractionHelper.extractToCarried(player, tank);
        if (changed) {
            saveChanges();
        }
        return changed;
    }

    /** GUI 清空按钮:直接清空 tank(流体消失,不返还)。 */
    public void clearFluidTank() {
        if (tank.getFluid().isEmpty()) {
            return;
        }
        com.moakiee.ae2lt.logic.transfer.FluidTankInteractionHelper.clear(tank);
        saveChanges();
    }

    public IFluidHandler getFluidHandlerCapability(Direction side) {
        return fluidHandler;
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public OverloadProcessingFactoryEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public CrystalCatalyzerLogic getLogic() {
        return logic;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return redstoneControl.getUpgrades();
    }

    @Override
    public MachineRedstoneControl getRedstoneControl() {
        return redstoneControl;
    }

    public void onRedstoneStateChanged() {
        if (level == null || level.isClientSide()) return;
        if (!redstoneControl.isEnabled()) setWorking(false);
        logic.onStateChanged();
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(CrystalCatalyzerMenu.TYPE, player, locator);
    }

    @Override
    public void onReady() {
        super.onReady();
        if (frequencyBinding != null) frequencyBinding.onReady();
        if (getCatalyzerSettings().standalone() && legacyUnboundFrequencyId > 0 && level instanceof ServerLevel) {
            var manager = WirelessFrequencyManager.get();
            if (manager != null) {
                manager.unregisterDevice(legacyUnboundFrequencyId, level.dimension(), worldPosition);
            }
            legacyUnboundFrequencyId = -1;
        }
        inventory.setLevel(level);
        setWorking(lockedRecipe != null && redstoneControl.isEnabled());
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        inventory.setLevel(level);
        if (frequencyBinding != null) frequencyBinding.clearRemoved();
    }

    @Override
    public void setRemoved() {
        if (frequencyBinding != null) frequencyBinding.setRemoved();
        super.setRemoved();
        inventory.setLevel(null);
    }

    @Override
    public void onChunkUnloaded() {
        if (frequencyBinding != null) frequencyBinding.onChunkUnloaded();
        super.onChunkUnloaded();
    }

    public Optional<CrystalCatalyzerRecipeCandidate> findProcessableRecipe() {
        if (inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST).getCount()
                        < getCatalyzerSettings().minimumCatalysts()) {
            return Optional.empty();
        }

        Optional<CrystalCatalyzerRecipeCandidate> candidate = CrystalCatalyzerRecipeService.findRecipe(
                level, inventory, getMode(), tank.getFluid(), getCatalyzerSettings().waterOnly());
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        return canAcceptRecipeOutput(candidate.get()) ? candidate : Optional.empty();
    }

    public Mode getMode() {
        return getCatalyzerSettings().modeSwitching() ? mode : Mode.CRYSTAL;
    }

    /**
     * 切到下一个模式。切换会立即中断当前正在进行的配方
     * （因为新模式可能根本不认现槽内催化剂）。
     */
    public void cycleMode() {
        if (!getCatalyzerSettings().modeSwitching()) {
            return;
        }
        Mode previous = this.mode;
        this.mode = previous.next();
        abortProcessing();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private boolean hasEnoughFluid(FluidStack required) {
        if (getCatalyzerSettings().waterOnly() && required.getFluid() != Fluids.WATER) {
            return false;
        }
        FluidStack current = tank.getFluid();
        if (current.isEmpty()) {
            return false;
        }
        return FluidStack.isSameFluidSameComponents(current, required)
                && current.getAmount() >= required.getAmount();
    }

    private boolean canAcceptRecipeOutput(CrystalCatalyzerRecipeCandidate candidate) {
        return canAcceptRecipeOutput(
                getMachineOutput(candidate),
                getCurrentOutputMultiplier(candidate));
    }

    private ItemStack getMachineOutput(CrystalCatalyzerRecipeCandidate candidate) {
        return candidate.recipe().value().getOutputTemplate();
    }

    public boolean canAcceptLockedRecipeOutput(CrystalCatalyzerLockedRecipe lockedRecipe) {
        return !getLockedRecipeOutputStack(lockedRecipe).isEmpty();
    }

    public boolean canAdvanceLockedRecipe(CrystalCatalyzerLockedRecipe lockedRecipe) {
        if (getCatalyzerSettings().energyFree() || lockedRecipe.lightningCost() <= 0) {
            return hasEnoughFluid(lockedRecipe.fluidInput());
        }
        LightningKey lightningKey = LightningKey.of(lockedRecipe.lightningTier());
        long lightningCost = lockedRecipe.lightningCost();
        return hasEnoughFluid(lockedRecipe.fluidInput())
                && simulateLightningExtract(lightningKey, lightningCost) >= lightningCost;
    }

    private boolean canAcceptRecipeOutput(ItemStack template, int multiplier) {
        long outputCount = (long) template.getCount() * multiplier;
        if (outputCount <= 0 || outputCount > Integer.MAX_VALUE) {
            return false;
        }
        return inventory.canAcceptRecipeOutput(template.copyWithCount((int) outputCount));
    }

    private ItemStack getLockedRecipeOutputStack(CrystalCatalyzerLockedRecipe lockedRecipe) {
        ItemStack template = lockedRecipe.output();
        // Unscaled processing keeps the recipe's base yield, without parallel or matrix scaling.
        long outputCount = !getCatalyzerSettings().scaleOutput() ? template.getCount()
                : (long) template.getCount() * lockedRecipe.outputMultiplier();
        if (outputCount <= 0 || outputCount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }

        ItemStack resultStack = template.copyWithCount((int) outputCount);
        return inventory.canAcceptRecipeOutput(resultStack) ? resultStack : ItemStack.EMPTY;
    }

    /**
     * Matrix-only multiplier (no parallel). Used as a fallback when we don't yet know
     * which candidate will run (e.g. restoring a locked recipe from NBT at load time).
     */
    private int getCurrentOutputMultiplier() {
        return getCatalyzerSettings().scaleOutput() && inventory.hasLightningCollapseMatrix()
                ? MATRIX_OUTPUT_MULTIPLIER : 1;
    }

    /**
     * Full multiplier = parallel × matrix. Parallel is {@code amount / catalystCount},
     * capped so the batch fits the output slot's total capacity,
     * where {@code amount} is the current catalyst stack size and {@code catalystCount}
     * is the per-instance input declared by the recipe. Catalyst is <em>not</em>
     * consumed, so an existing parallel factor is snapshotted at lock time via
     * {@link CrystalCatalyzerLockedRecipe#outputMultiplier()} and stays stable across
     * the whole processing cycle.
     */
    private int getCurrentOutputMultiplier(CrystalCatalyzerRecipeCandidate candidate) {
        int matrix = getCatalyzerSettings().scaleOutput() && inventory.hasLightningCollapseMatrix()
                ? MATRIX_OUTPUT_MULTIPLIER : 1;
        int parallel = computeParallel(candidate);
        long multiplier = (long) Math.max(1, parallel) * matrix;
        return (int) Math.min(multiplier, Integer.MAX_VALUE);
    }

    private int computeParallel(CrystalCatalyzerRecipeCandidate candidate) {
        if (!getCatalyzerSettings().scaleOutput()) {
            return 1;
        }
        if (candidate == null) {
            return 1;
        }
        var recipe = candidate.recipe().value();
        int perInstance = recipe.catalystCount();
        if (perInstance <= 0) {
            return 1;
        }
        int amount = inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST).getCount();
        int matrix = inventory.hasLightningCollapseMatrix() ? MATRIX_OUTPUT_MULTIPLIER : 1;
        long outputPerParallel = (long) recipe.getOutputTemplate().getCount() * matrix;
        int capacityParallel = outputPerParallel > 0
                ? (int) (inventory.getSlotLimit(CrystalCatalyzerInventory.SLOT_OUTPUT) / outputPerParallel)
                : 0;
        // Bound the batch by the full slot capacity. Current contents still apply
        // backpressure, but a high-yield recipe must be able to start when empty.
        return Math.max(1, Math.min(amount / perInstance, capacityParallel));
    }

    @Override
    public boolean hasLockedRecipe() {
        return lockedRecipe != null;
    }

    @Override
    public Optional<CrystalCatalyzerLockedRecipe> getLockedRecipe() {
        return Optional.ofNullable(lockedRecipe);
    }

    @Override
    public Optional<CrystalCatalyzerLockedRecipe> lockCurrentRecipe() {
        if (lockedRecipe != null) {
            return Optional.of(lockedRecipe);
        }

        Optional<CrystalCatalyzerRecipeCandidate> candidate = findProcessableRecipe();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        if (!getCatalyzerSettings().scaleOutput()) {
            var holder = candidate.get().recipe();
            var recipe = holder.value();
            // Keep the shared recipe's cost metadata. This machine bypasses energy in its tick driver.
            lockedRecipe = new CrystalCatalyzerLockedRecipe(holder.id(), getMachineOutput(candidate.get()),
                    recipe.energyPerCycle(), 1, recipe.lightningCost(), recipe.lightningTier(), recipe.fluidInput());
        } else {
            lockedRecipe = CrystalCatalyzerLockedRecipe.fromCandidate(
                    candidate.get(), getCurrentOutputMultiplier(candidate.get()));
        }
        saveChanges();
        return Optional.of(lockedRecipe);
    }

    public void clearLockedRecipe() {
        boolean hadLockedRecipe = lockedRecipe != null;
        boolean hadProgress = consumedEnergy != 0L || processingTicksSpent != 0;
        if (!hadLockedRecipe && !hadProgress) {
            return;
        }
        lockedRecipe = null;
        resetProgressState();
        if (hadLockedRecipe && !hadProgress) {
            saveChanges();
        }
    }

    @Override
    public void abortProcessing() {
        clearLockedRecipe();
        setWorking(false);
    }

    @Override
    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    @Override
    public int getProcessingTicksSpent() {
        return processingTicksSpent;
    }

    public int getMinimumProcessTicks() {
        int fixed = getCatalyzerSettings().fixedProcessTicks();
        return fixed > 0 ? fixed : getMode().getMinProcessTicks();
    }

    public double getProgress() {
        if (lockedRecipe == null) {
            return 0.0D;
        }
        if (getCatalyzerSettings().energyFree() || lockedRecipe.totalEnergy() <= 0L) {
            return Math.min(1.0D,
                    (double) processingTicksSpent / (double) Math.max(1, getMinimumProcessTicks()));
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) lockedRecipe.totalEnergy());
    }

    private void addConsumedEnergy(long amount) {
        if (amount <= 0L) {
            return;
        }
        if (amount > Long.MAX_VALUE - consumedEnergy) {
            consumedEnergy = Long.MAX_VALUE;
        } else {
            consumedEnergy += amount;
        }
    }

    private void incrementProcessingTicksSpent() {
        processingTicksSpent++;
    }

    public void advanceEnergyFreeProcessingTick() {
        processingTicksSpent = Math.min(
                Math.max(1, getMinimumProcessTicks()),
                processingTicksSpent + 1);
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void resetProgressState() {
        boolean changed = consumedEnergy != 0L || processingTicksSpent != 0;
        consumedEnergy = 0L;
        processingTicksSpent = 0;
        if (changed) {
            saveChanges();
        }
    }

    @Override
    public boolean pushOutResult() {
        if (!hasAutoExportWork() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        return AdjacentItemAutoExportHelper.pushOutResult(
                this,
                getOrientation(),
                allowedOutputs,
                CrystalCatalyzerInventory.SLOT_OUTPUT,
                1,
                inventory::getStackInSlot,
                (slot, amount) -> inventory.extractItem(slot, amount, false),
                remainder -> {
                    ItemStack leftover = inventory.insertRecipeOutput(remainder, false);
                    if (!leftover.isEmpty() && level != null) {
                        NativeStackDropHelper.popResource(level, worldPosition, leftover);
                    }
                },
                direction -> exportTargetCache.resolve(serverLevel, worldPosition, direction));
    }

    @Override
    public boolean hasAutoExportWork() {
        return !allowedOutputs.isEmpty() && AdjacentItemAutoExportHelper.hasAnyOutput(
                autoExport,
                CrystalCatalyzerInventory.SLOT_OUTPUT,
                1,
                inventory::getStackInSlot);
    }

    public boolean isAutoExportEnabled() {
        return autoExport;
    }

    public void setAutoExportEnabled(boolean autoExport) {
        if (this.autoExport == autoExport) {
            return;
        }
        this.autoExport = autoExport;
        saveChanges();
        logic.onStateChanged();
    }

    public EnumSet<RelativeSide> getAllowedOutputs() {
        return allowedOutputs.isEmpty() ? EnumSet.noneOf(RelativeSide.class) : EnumSet.copyOf(allowedOutputs);
    }

    public void updateOutputSides(EnumSet<RelativeSide> allowedOutputs) {
        this.allowedOutputs = allowedOutputs.isEmpty()
                ? EnumSet.noneOf(RelativeSide.class)
                : EnumSet.copyOf(allowedOutputs);
        exportTargetCache.invalidate();
        saveChanges();
        logic.onStateChanged();
    }

    public long getAvailableHighVoltage() {
        return getCatalyzerSettings().energyFree() ? 0L : simulateLightningExtract(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE);
    }

    public long getAvailableExtremeHighVoltage() {
        return getCatalyzerSettings().energyFree() ? 0L : simulateLightningExtract(LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE);
    }

    private long simulateLightningExtract(LightningKey key, long amount) {
        if (amount <= 0L) return 0L;
        var grid = getMainNode().getGrid();
        if (grid == null) return 0L;
        return grid.getStorageService().getInventory()
                .extract(key, amount, Actionable.SIMULATE, IActionSource.ofMachine(this));
    }

    private long extractLightning(LightningKey key, long amount) {
        if (amount <= 0L) return 0L;
        var grid = getMainNode().getGrid();
        if (grid == null) return 0L;
        return grid.getStorageService().getInventory()
                .extract(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private long insertLightning(LightningKey key, long amount) {
        if (amount <= 0L) return 0L;
        var grid = getMainNode().getGrid();
        if (grid == null) return 0L;
        return grid.getStorageService().getInventory()
                .insert(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    public void onNeighborChanged(BlockPos changedPos) {
        if (redstoneControl.hasCard()) onRedstoneStateChanged();
        if (changedPos != null && worldPosition.distManhattan(changedPos) == 1) {
            exportTargetCache.invalidate();
        }
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        exportTargetCache.invalidate();
    }

    @Override
    public boolean completeLockedRecipe(
            CrystalCatalyzerLockedRecipe lockedRecipe,
            CrystalCatalyzerRecipeCandidate candidate) {
        ItemStack resultStack = getLockedRecipeOutputStack(lockedRecipe);
        if (resultStack.isEmpty()) {
            return false;
        }

        if (!lockedRecipe.matchesFluidInput(candidate.recipe().value())
                || !lockedRecipe.matchesHarvestOutput(candidate.recipe().value())
                || (getCatalyzerSettings().waterOnly() && !candidate.recipe().value().isWaterRecipe())) {
            return false;
        }
        FluidStack requiredFluid = lockedRecipe.fluidInput();
        FluidStack currentFluid = tank.getFluid();
        if (currentFluid.isEmpty()
                || !FluidStack.isSameFluidSameComponents(currentFluid, requiredFluid)
                || currentFluid.getAmount() < requiredFluid.getAmount()) {
            return false;
        }

        LightningKey lightningKey = null;
        long lightningCost = lockedRecipe.lightningCost();
        long extractedLightning = 0L;
        if (!getCatalyzerSettings().energyFree() && lightningCost > 0L) {
            lightningKey = LightningKey.of(lockedRecipe.lightningTier());
            if (simulateLightningExtract(lightningKey, lightningCost) < lightningCost) {
                return false;
            }

            extractedLightning = extractLightning(lightningKey, lightningCost);
            if (extractedLightning < lightningCost) {
                if (extractedLightning > 0L) {
                    insertLightning(lightningKey, extractedLightning);
                }
                return false;
            }
        }

        FluidStack drained = tank.drain(requiredFluid, FluidAction.EXECUTE);
        if (drained.getAmount() != requiredFluid.getAmount()) {
            if (!drained.isEmpty()) {
                tank.fill(drained, FluidAction.EXECUTE);
            }
            if (lightningKey != null) {
                insertLightning(lightningKey, extractedLightning);
            }
            return false;
        }

        ItemStack leftover = inventory.insertRecipeOutput(resultStack, false);
        if (!leftover.isEmpty()) {
            tank.fill(drained, FluidAction.EXECUTE);
            if (lightningKey != null) {
                insertLightning(lightningKey, extractedLightning);
            }
            return false;
        }

        clearLockedRecipe();
        pushOutResult();
        return true;
    }

    @Override
    public long getMachineStoredEnergy() {
        return energyStorage.getStoredEnergyLong();
    }

    @Override
    public IEnergyStorage getMachineEnergyStorage() {
        return energyStorage;
    }

    @Override
    public long getMachineEnergyCapacity() {
        return energyStorage.getCapacityLong();
    }

    @Override
    public int extractMachineEnergy(long amount) {
        return energyStorage.extractInternal(amount, false);
    }

    @Override
    public void onEnergyConsumed(int consumed) {
        addConsumedEnergy(consumed);
        incrementProcessingTicksSpent();
        saveChanges();
    }

    public boolean isWorking() {
        BlockState state = getBlockState();
        return state.hasProperty(CrystalCatalyzerBlock.WORKING)
                && state.getValue(CrystalCatalyzerBlock.WORKING);
    }

    @Override
    public void setWorking(boolean working) {
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof CrystalCatalyzerBlock
                    && level.getBlockEntity(worldPosition) == this
                    && state.hasProperty(CrystalCatalyzerBlock.WORKING)
                    && state.getValue(CrystalCatalyzerBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(CrystalCatalyzerBlock.WORKING, working), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        redstoneControl.save(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        data.put(TAG_TANK, tank.writeToNBT(registries, new CompoundTag()));
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        data.putBoolean(TAG_AUTO_EXPORT, autoExport);
        ListTag outputTags = new ListTag();
        for (var side : allowedOutputs) {
            outputTags.add(StringTag.valueOf(side.name()));
        }
        data.put(TAG_ALLOWED_OUTPUTS, outputTags);
        data.putString(TAG_MODE, getMode().getSerializedName());

        if (lockedRecipe != null) {
            data.put(TAG_LOCKED_RECIPE, lockedRecipe.toTag(registries));
        } else {
            data.remove(TAG_LOCKED_RECIPE);
        }
        if (frequencyBinding != null) {
            frequencyBinding.save(data);
        } else {
            data.remove(FrequencyBindingHelper.TAG_FREQUENCY_ID);
            data.remove("proxy");
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        redstoneControl.load(data, registries);
        inventory.loadFromTag(data, TAG_INVENTORY, registries);
        tank.readFromNBT(registries, data.getCompound(TAG_TANK));
        energyStorage.loadStoredEnergy(data.getLong(TAG_ENERGY));
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));
        if (frequencyBinding != null) {
            frequencyBinding.load(data);
        } else {
            legacyUnboundFrequencyId = data.getInt(FrequencyBindingHelper.TAG_FREQUENCY_ID);
        }
        autoExport = data.getBoolean(TAG_AUTO_EXPORT);
        allowedOutputs.clear();
        ListTag outputTags = data.getList(TAG_ALLOWED_OUTPUTS, Tag.TAG_STRING);
        for (int i = 0; i < outputTags.size(); i++) {
            try {
                allowedOutputs.add(RelativeSide.valueOf(outputTags.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (data.contains(TAG_MODE, Tag.TAG_STRING) && getCatalyzerSettings().modeSwitching()) {
            String modeName = data.getString(TAG_MODE);
            mode = Mode.CRYSTAL;
            for (Mode m : Mode.values()) {
                if (m.getSerializedName().equals(modeName)) {
                    mode = m;
                    break;
                }
            }
        } else {
            mode = Mode.CRYSTAL;
        }
        if (!getCatalyzerSettings().modeSwitching()) {
            mode = Mode.CRYSTAL;
        }
        if (data.contains(TAG_LOCKED_RECIPE, Tag.TAG_COMPOUND)) {
            lockedRecipe = CrystalCatalyzerLockedRecipe.fromTag(
                    data.getCompound(TAG_LOCKED_RECIPE),
                    registries,
                    getCurrentOutputMultiplier());
        } else {
            lockedRecipe = null;
        }
        if (lockedRecipe == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            lockedRecipe = migrateLockedRecipe(lockedRecipe, data);
            if (getCatalyzerSettings().energyFree()) consumedEnergy = 0L;
            consumedEnergy = Math.min(consumedEnergy, lockedRecipe.totalEnergy());
        }
        exportTargetCache.invalidate();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        for (int slot = CrystalCatalyzerInventory.SLOT_CATALYST;
             slot <= CrystalCatalyzerInventory.SLOT_MATRIX;
             slot++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(data, inventory.getStackInSlot(slot));
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        for (int slot = CrystalCatalyzerInventory.SLOT_CATALYST;
             slot <= CrystalCatalyzerInventory.SLOT_MATRIX;
             slot++) {
            ItemStack oldStack = inventory.getStackInSlot(slot);
            ItemStack newStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
            if (!ItemStack.matches(oldStack, newStack)) {
                inventory.setClientRenderStack(slot, newStack);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : getUpgrades()) {
            if (!upgrade.isEmpty()) NativeStackDropHelper.addDrops(drops, upgrade);
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                NativeStackDropHelper.addDrops(drops, stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        getUpgrades().clear();
        abortProcessing();
        inventory.clear();
        tank.setFluid(FluidStack.EMPTY);
        energyStorage.loadStoredEnergy(0L);
        autoExport = false;
        allowedOutputs.clear();
        exportTargetCache.invalidate();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.exportSettings(mode, builder, player);
        MemoryCardConfigSupport.exportAutoExportSettings(mode, builder, autoExport, allowedOutputs, tag -> {
            redstoneControl.writeSettings(tag);
            MemoryCardConfigSupport.writeEnum(tag, TAG_MODE, this.mode);
            if (frequencyBinding != null) {
                FrequencyBindingHelper.writeMemoryFrequency(tag, frequencyBinding.getFrequencyId());
            }
            MemoryCardConfigSupport.writeMatrixCount(tag, this);
        });
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @org.jetbrains.annotations.Nullable Player player) {
        super.importSettings(mode, input, player);
        MemoryCardConfigSupport.importAutoExportSettings(mode, input,
                v -> this.autoExport = v,
                sides -> this.allowedOutputs = sides,
                tag -> {
                    redstoneControl.readSettings(tag);
                    Mode importedMode = MemoryCardConfigSupport.readEnum(
                            tag, TAG_MODE, Mode.class, this.mode);
                    if (!getCatalyzerSettings().modeSwitching()) {
                        importedMode = Mode.CRYSTAL;
                    }
                    if (this.mode != importedMode) {
                        this.mode = importedMode;
                        abortProcessing();
                    }
                    if (frequencyBinding != null) {
                        FrequencyBindingHelper.importMemoryFrequency(tag, frequencyBinding::setFrequency, player, this);
                    }
                    MemoryCardConfigSupport.restoreMatrixCount(tag, player, this);
                },
                () -> {
                    exportTargetCache.invalidate();
                    saveChanges();
                    markForClientUpdate();
                    logic.onStateChanged();
                });
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return getCatalyzerSettings().standalone() ? Set.of() : EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return getCatalyzerSettings().standalone() ? AECableType.NONE : AECableType.SMART;
    }

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onTankChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onEnergyChanged() {
        saveChanges();
        logic.onStateChanged();
    }

    private void onUpgradesChanged() {
        saveChanges();
        onRedstoneStateChanged();
    }
}
