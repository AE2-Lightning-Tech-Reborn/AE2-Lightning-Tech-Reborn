package com.moakiee.ae2lt.compat.gto;

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
import com.gtocore.api.ae2.crafting.OptimizedCraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.*;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Ordinary plans use the unchanged GTO implementation. Plans containing LT overload patterns
 * use LT's existing dispatcher, which owns their fuzzy-return accounting. This keeps GTO's
 * optimized CPU type (including its pause/status UI contract) without modifying GTO classes.
 */
public final class GtoCompatibleCraftingCpuLogic extends OptimizedCraftingCpuLogic {
    private static final String LT_STATE = "ae2ltGtoOverloadCpu";
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private @Nullable TimeWheelCraftingCPU ltCpu;

    public GtoCompatibleCraftingCpuLogic(CraftingCPUCluster cluster) { super(cluster); }

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
        if (!super.getInventory().list.isEmpty()) return CraftingSubmitResult.CPU_BUSY;
        if (requiresLtDispatcher(plan)) {
            createLt();
            return lt().trySubmitJob(grid, plan, src, requester);
        }
        ltCpu = null;
        return super.trySubmitJob(grid, plan, src, requester);
    }
    @Override public void tickCraftingLogic(IEnergyService energy, CraftingService service) {
        if (usesLt()) lt().tickCraftingLogic(energy, service);
        else super.tickCraftingLogic(energy, service);
    }
    @Override public long insert(AEKey key, long amount, Actionable mode) {
        return usesLt() ? lt().insert(key, amount, mode) : super.insert(key, amount, mode);
    }
    @Override public void cancel() { if (usesLt()) lt().cancel(); else super.cancel(); }
    @Override public boolean hasJob() { return usesLt() ? lt().hasPersistentState() : super.hasJob(); }
    @Override public GenericStack getFinalJobOutput() { return usesLt() ? lt().getFinalJobOutput() : super.getFinalJobOutput(); }
    @Override public ElapsedTimeTracker getElapsedTimeTracker() { return usesLt() ? lt().getElapsedTimeTracker() : super.getElapsedTimeTracker(); }
    @Override public ICraftingLink getLastLink() { return usesLt() ? lt().getLastLink() : super.getLastLink(); }
    @Override public ListCraftingInventory getInventory() { return usesLt() ? lt().getInventory() : super.getInventory(); }
    @Override public long getLastModifiedOnTick() { return usesLt() ? lt().getLastModifiedOnTick() : super.getLastModifiedOnTick(); }
    @Override public long getStored(AEKey key) { return usesLt() ? lt().getStored(key) : super.getStored(key); }
    @Override public long getWaitingFor(AEKey key) { return usesLt() ? lt().getWaitingFor(key) : super.getWaitingFor(key); }
    @Override public void getAllWaitingFor(Set<AEKey> keys) { if (usesLt()) lt().getAllWaitingFor(keys); else super.getAllWaitingFor(keys); }
    @Override public long getPendingOutputs(AEKey key) { return usesLt() ? lt().getPendingOutputs(key) : super.getPendingOutputs(key); }
    @Override public void getAllItems(KeyCounter out) { if (usesLt()) lt().getAllItems(out); else super.getAllItems(out); }
    @Override public boolean isCantStoreItems() { return usesLt() ? lt().isCantStoreItems() : super.isCantStoreItems(); }
    @Override public boolean isPaused() { return usesLt() ? lt().isJobSuspended() : super.isPaused(); }
    @Override public void setPaused(boolean paused) { if (usesLt()) lt().setJobSuspended(paused); else super.setPaused(paused); }
    @Override public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener); super.addListener(listener); if (usesLt()) lt().addListener(listener);
    }
    @Override public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener); super.removeListener(listener); if (usesLt()) lt().removeListener(listener);
    }
    public long getRemainingOutputAmount() { return usesLt() ? lt().getRemainingOutputAmount() : GtoCpuJobAccess.remaining(this); }
    @Override public void writeToNBT(CompoundTag data) {
        if (usesLt()) {
            data.remove("job"); data.remove("inventory");
            var state = new CompoundTag();
            lt().writeToNBT(state, cluster.getLevel().registryAccess());
            data.put(LT_STATE, state);
        } else {
            data.remove(LT_STATE);
            super.writeToNBT(data);
        }
    }
    @Override public void readFromNBT(CompoundTag data) {
        if (data.contains(LT_STATE)) {
            createLt();
            lt().readFromNBT(data.getCompound(LT_STATE), cluster.getLevel().registryAccess());
        } else {
            ltCpu = null;
            super.readFromNBT(data);
        }
    }
}
