package com.moakiee.ae2lt.compat.gto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.util.Platform;
import appeng.crafting.execution.CraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.*;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Ordinary plans use the unchanged GTO implementation. Plans containing LT overload patterns
 * use LT's existing dispatcher, which owns their fuzzy-return accounting. This keeps GTO's
 * native CPU and optional NeoECO wrapper without binding to randomized GTO implementation names.
 */
public final class GtoCompatibleCraftingCpuLogic extends CraftingCpuLogic {
    private static final String LT_STATE = "ae2ltGtoOverloadCpu";
    private final CraftingCpuLogic delegate;
    private boolean paused;
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private @Nullable TimeWheelCraftingCPU ltCpu;

    public GtoCompatibleCraftingCpuLogic(CraftingCPUCluster cluster) {
        this(cluster, cluster.craftingLogic instanceof GtoCompatibleCraftingCpuLogic previous
                ? previous.nativeDelegate() : cluster.craftingLogic);
    }

    public GtoCompatibleCraftingCpuLogic(CraftingCPUCluster cluster, CraftingCpuLogic delegate) {
        super(cluster);
        this.delegate = java.util.Objects.requireNonNull(delegate, "native CPU logic");
        if (delegate instanceof GtoCompatibleCraftingCpuLogic)
            throw new IllegalArgumentException("CPU bridge already installed");
    }

    public CraftingCpuLogic nativeDelegate() { return delegate; }
    @Override protected void postChange(AEKey key) { cluster.markDirty(); }

    public boolean usesLt() { return ltCpu != null; }
    private Ae2LtTimeWheelCraftingCpuLogic lt() { return ltCpu.getCraftingLogic(); }

    private void createLt() {
        if (ltCpu != null) return;
        var host = new TimeWheelCraftingCpuHost() {
            public boolean isCpuActive() { return cluster.isActive(); }
            public ICraftingCPU getCraftingLinkOwner(TimeWheelCraftingCPU cpu) { return cluster; }
            public IGrid getGrid() { return cluster.getGrid(); }
            public IActionSource getActionSource() { return cluster.getSrc(); }
            public Level getCpuLevel() { return cluster.getNode() == null ? null : cluster.getLevel(); }
            public void markCpuDirty() {
                cluster.updateOutput(ltCpu.getDisplayedOutput());
                cluster.markDirty();
            }
            public Component getCpuDisplayName() { return cluster.getName(); }
            public CpuSelectionMode getSelectionMode() { return cluster.getSelectionMode(); }
        };
        // Retain the physical CPU's storage and operation limits; this is not an extra virtual CPU.
        long operations = Math.max(1L, (long) cluster.getCoProcessors() + 1);
        ltCpu = new TimeWheelCraftingCPU(host, cluster.getAvailableStorage(), cluster.getCoProcessors(), operations, false);
        listeners.forEach(lt()::addListener);
    }

    public static boolean requiresLtDispatcher(ICraftingPlan plan) {
        return plan.patternTimes().keySet().stream().anyMatch(pattern ->
                CraftingPatternDelegates.forProviderLookup(pattern) instanceof OverloadedProviderOnlyPatternDetails);
    }

    @Override public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
            @Nullable ICraftingRequester requester) {
        if (hasJob() || (usesLt() && lt().hasPersistentState())) return CraftingSubmitResult.CPU_BUSY;
        if (!delegate.getInventory().list.isEmpty()) return CraftingSubmitResult.CPU_BUSY;
        if (requiresLtDispatcher(plan)) {
            createLt();
            return lt().trySubmitJob(grid, plan, src, requester);
        }
        ltCpu = null;
        return delegate.trySubmitJob(grid, plan, src, requester);
    }
    @Override public void tickCraftingLogic(IEnergyService energy, CraftingService service) {
        if (usesLt()) lt().tickCraftingLogic(energy, service);
        else if (!paused) delegate.tickCraftingLogic(energy, service);
    }
    @Override public long insert(AEKey key, long amount, Actionable mode) {
        return usesLt() ? lt().insert(key, amount, mode) : delegate.insert(key, amount, mode);
    }
    @Override public void cancel() { if (usesLt()) lt().cancel(); else delegate.cancel(); }
    @Override public boolean hasJob() { return usesLt() ? lt().hasPersistentState() : delegate.hasJob(); }
    @Override public GenericStack getFinalJobOutput() { return usesLt() ? lt().getFinalJobOutput() : delegate.getFinalJobOutput(); }
    @Override public ElapsedTimeTracker getElapsedTimeTracker() { return usesLt() ? lt().getElapsedTimeTracker() : delegate.getElapsedTimeTracker(); }
    @Override public ICraftingLink getLastLink() { return usesLt() ? lt().getLastLink() : delegate.getLastLink(); }
    @Override public ListCraftingInventory getInventory() { return usesLt() ? lt().getInventory() : delegate.getInventory(); }
    @Override public long getLastModifiedOnTick() { return usesLt() ? lt().getLastModifiedOnTick() : delegate.getLastModifiedOnTick(); }
    @Override public long getStored(AEKey key) { return usesLt() ? lt().getStored(key) : delegate.getStored(key); }
    @Override public long getWaitingFor(AEKey key) { return usesLt() ? lt().getWaitingFor(key) : delegate.getWaitingFor(key); }
    @Override public void getAllWaitingFor(Set<AEKey> keys) { if (usesLt()) lt().getAllWaitingFor(keys); else delegate.getAllWaitingFor(keys); }
    @Override public long getPendingOutputs(AEKey key) { return usesLt() ? lt().getPendingOutputs(key) : delegate.getPendingOutputs(key); }
    @Override public void getAllItems(KeyCounter out) { if (usesLt()) lt().getAllItems(out); else delegate.getAllItems(out); }
    @Override public boolean isCantStoreItems() { return usesLt() ? lt().isCantStoreItems() : delegate.isCantStoreItems(); }
    public boolean isPaused() { return usesLt() ? lt().isJobSuspended() : paused; }
    public void setPaused(boolean paused) { if (usesLt()) lt().setJobSuspended(paused); else this.paused = paused; cluster.markDirty(); }
    @Override public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener); delegate.addListener(listener); if (usesLt()) lt().addListener(listener);
    }
    @Override public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener); delegate.removeListener(listener); if (usesLt()) lt().removeListener(listener);
    }
    public long getRemainingOutputAmount() { return usesLt() ? lt().getRemainingOutputAmount() : GtoCpuJobAccess.remaining(delegate); }

    /** Reclaim both LT inventories before AE2's breakCluster path drops its ordinary inventory. */
    public void dropStoredItemsForRemoval(Level level, BlockPos pos) {
        var cpu = ltCpu;
        if (cpu == null) return;
        var drops = new ArrayList<net.minecraft.world.item.ItemStack>();
        cpu.addRemovalDrops(level, pos, drops);
        // Spawning entities and destroying the remaining cluster blocks can reenter breakCluster.
        // Transfer ownership to the drop list before either can visit the same CPU again.
        cpu.clearRemovedContent();
        Platform.spawnDrops(level, pos, drops);
    }

    @Override public void writeToNBT(CompoundTag data) {
        if (usesLt()) {
            data.remove("job"); data.remove("inventory");
            var state = new CompoundTag();
            lt().writeToNBT(state, cluster.getLevel().registryAccess());
            data.put(LT_STATE, state);
        } else {
            data.remove(LT_STATE);
            delegate.writeToNBT(data);
            data.putBoolean("ae2ltPaused", paused);
        }
    }
    @Override public void readFromNBT(CompoundTag data) {
        if (data.contains(LT_STATE)) {
            createLt();
            lt().readFromNBT(data.getCompound(LT_STATE), cluster.getLevel().registryAccess());
        } else {
            ltCpu = null;
            delegate.readFromNBT(data);
            paused = data.getBoolean("ae2ltPaused");
        }
    }
}
