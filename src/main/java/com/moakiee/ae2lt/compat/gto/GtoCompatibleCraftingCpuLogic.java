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
    private final KeyCounter eaepManualWaiting = new KeyCounter();

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
    public boolean completeEaepVirtualTask(appeng.api.crafting.IPatternDetails pattern) {
        return usesLt() ? lt().completeEaepVirtualTask(pattern)
                : GtoEaepVirtualCpu.completeLastTask(delegate, pattern);
    }
    @Override protected void postChange(AEKey key) { cluster.markDirty(); }

    public boolean usesLt() { return ltCpu != null; }
    public java.util.Map<AEKey, Long> eaepManualWaitingSnapshot() {
        if (!usesLt() || !hasJob()) return java.util.Map.of();
        var snapshot = new java.util.HashMap<AEKey, Long>();
        for (var entry : eaepManualWaiting) {
            long remaining = Math.min(entry.getLongValue(), lt().getWaitingFor(entry.getKey()));
            if (remaining > 0) snapshot.put(entry.getKey(), remaining);
        }
        return java.util.Map.copyOf(snapshot);
    }
    public void setEaepManualWaiting(KeyCounter missing) {
        eaepManualWaiting.clear();
        if (missing != null) eaepManualWaiting.addAll(missing);
    }
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
        // EAEP's ordinary CPU hooks target a job class removed by GTO. LT already owns the
        // force-start/manual-missing protocol, including admission, save/reload and refunds.
        if (com.moakiee.ae2lt.integration.eaep.EaepForcedCraftingPlanAccess.isForced(plan)) return true;
        return plan.patternTimes().keySet().stream().anyMatch(pattern ->
                CraftingPatternDelegates.forProviderLookup(pattern) instanceof OverloadedProviderOnlyPatternDetails
                        || GtoUselessDynamicPatterns.isDynamic(pattern));
    }

    @Override public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
            @Nullable ICraftingRequester requester) {
        if (hasJob() || (usesLt() && lt().hasPersistentState())) return CraftingSubmitResult.CPU_BUSY;
        if (!delegate.getInventory().list.isEmpty()) return CraftingSubmitResult.CPU_BUSY;
        if (requiresLtDispatcher(plan)) {
            createLt();
            var result = lt().trySubmitJob(grid, plan, src, requester);
            if (result.successful()) {
                var forced = com.moakiee.ae2lt.integration.eaep.EaepForcedCraftingPlanAccess.read(plan);
                setEaepManualWaiting(forced == null ? null : forced.manualMissing());
            }
            return result;
        }
        ltCpu = null;
        eaepManualWaiting.clear();
        return delegate.trySubmitJob(grid, plan, src, requester);
    }
    @Override public void tickCraftingLogic(IEnergyService energy, CraftingService service) {
        if (usesLt()) lt().tickCraftingLogic(energy, service);
        else if (!paused) delegate.tickCraftingLogic(energy, service);
    }
    @Override public long insert(AEKey key, long amount, Actionable mode) {
        if (!usesLt()) return delegate.insert(key, amount, mode);
        long before = lt().getWaitingFor(key);
        long accepted = lt().insert(key, amount, mode);
        if (mode == Actionable.MODULATE && eaepManualWaiting.get(key) > 0) {
            long consumed = Math.max(0, before - lt().getWaitingFor(key));
            eaepManualWaiting.set(key, Math.max(0, eaepManualWaiting.get(key) - consumed));
        }
        return accepted;
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
            var manual = new net.minecraft.nbt.ListTag();
            eaepManualWaitingSnapshot().forEach((key, amount) -> manual.add(GenericStack.writeTag(new GenericStack(key, amount))));
            state.put("eaepManualWaiting", manual);
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
            eaepManualWaiting.clear();
            var manual = data.getCompound(LT_STATE).getList("eaepManualWaiting", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < manual.size(); i++) {
                var stack = GenericStack.readTag(manual.getCompound(i));
                if (stack != null && stack.amount() > 0) eaepManualWaiting.add(stack.what(), stack.amount());
            }
        } else {
            ltCpu = null;
            eaepManualWaiting.clear();
            delegate.readFromNBT(data);
            paused = data.getBoolean("ae2ltPaused");
        }
    }
}
