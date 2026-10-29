package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/**
 * Single-cell FE buffer/proxy in front of an ME storage delegate.
 *
 * <p>When a Flux Cell is installed, its internal {@code storedEnergy} is
 * authoritative for energy still in the cell. Extracted batches and rejected
 * refunds belong to the separate persistent escrow until delivered or returned.
 * The cell's ItemStack-data
 * persist is deferred to {@link #endTick(AEKey, IActionSource)} so that 64+ writes
 * during a single OVERLOAD tick collapse to a single ItemStack data-component
 * update.
 *
 * <p>When no cell is installed, NORMAL-mode distribution falls back to the
 * persistent {@link #escrow}; OVERLOAD mode short-circuits since
 * {@code BufferCapacity == 0} disables it at the host level.
 *
 * <p>If a per-tick distribution would otherwise exceed what the cell holds,
 * {@link #beginMemoryBatch} (NORMAL) and {@link #refillFromDelegateInline}
 * (OVERLOAD) refill the cell from the delegate <i>once</i>, amortizing the
 * cost across {@code 20 ticks} of expected consumption.
 */
public class BufferedMEStorage implements MEStorage {

    private static final int HISTORY_SIZE = 20;

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }

    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) return 0L;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private MEStorage delegate;
    @Nullable
    private final Supplier<MEStorage> cellSupplier;
    @Nullable
    private final Runnable cellPersistCallback;
    @Nullable
    private final long[] consumptionHistory;

    /** Persistent ownership of preload and refunds not currently held by an installed cell. */
    private EnergyEscrow escrow;
    private boolean ledgerOwned;
    private Runnable pendingPersist;
    private long lastNotifiedPendingEnergy;
    private boolean batchOnly;
    private boolean transferring;
    private int nextCostMultiplier = 1;
    private java.util.function.BooleanSupplier online = () -> true;
    private int historyPointer;
    private int costMultiplier = 1;

    public BufferedMEStorage(MEStorage delegate) {
        this(delegate, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier) {
        this(delegate, cellSupplier, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier,
                             @Nullable Runnable cellPersistCallback) {
        this(delegate, cellSupplier, cellPersistCallback, new EnergyEscrow(0, 0, () -> {}));
        ledgerOwned = false;
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier,
                             @Nullable Runnable cellPersistCallback, EnergyEscrow escrow) {
        this.escrow = escrow;
        this.ledgerOwned = true;
        this.delegate = delegate;
        this.cellSupplier = cellSupplier;
        this.cellPersistCallback = cellPersistCallback;
        // Only cell refill uses lookahead; endpoint batches never install a cell supplier.
        this.consumptionHistory = cellSupplier == null ? null : new long[HISTORY_SIZE];
    }

    public BufferedMEStorage(MEStorage delegate, Supplier<MEStorage> cellSupplier,
            Runnable persist, Runnable pendingPersist) {
        this(delegate, cellSupplier, persist, new EnergyEscrow(0, 0, () -> {}));
        this.pendingPersist = pendingPersist;
        ledgerOwned = false;
    }
    public BufferedMEStorage(MEStorage delegate, Supplier<MEStorage> cellSupplier,
            Runnable persist, Runnable pendingPersist, java.util.function.BooleanSupplier online) {
        this(delegate, cellSupplier, persist, pendingPersist);
        this.online = online;
    }
    public boolean isOnline() { return online.getAsBoolean(); }
    boolean beginTransfer() {
        if (transferring || !isOnline()) return false;
        transferring = true;
        return true;
    }
    void endTransfer() { transferring = false; costMultiplier = nextCostMultiplier; }
    long returnUndelivered(long amount, IActionSource source) {
        long units = Math.min(Math.max(0, amount), escrow.room() / costMultiplier);
        escrow.credit(units * costMultiplier);
        recordReturn(units * costMultiplier);
        return units;
    }
    void rebindDelegate(MEStorage storage) { delegate = storage; }
    public long getPendingEnergy() { return escrow.available(); }
    long getUncertainEnergy() { return escrow.uncertain(); }
    public void restorePendingEnergy(long value) { restoreSnapshot(value, 0); }
    void restoreSnapshot(long value, long uncertain) {
        if (ledgerOwned) return;
        escrow = new EnergyEscrow(Math.max(0, value), Math.max(0, uncertain), () -> {});
        batchOnly = false;
        lastNotifiedPendingEnergy = escrow.available();
    }
    boolean isLedgerOwned() { return ledgerOwned; }
    void attachLedger(EnergyEscrow destination) {
        escrow.transferTo(destination);
        escrow = destination;
        ledgerOwned = true;
    }
    public long takePendingEnergy() { return escrow.debit(escrow.available()); }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0L) {
            return delegate.extract(what, amount, mode, source);
        }
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null) return 0L;

        long needed = saturatingMul(amount, costMultiplier);

        // NORMAL batch path: beginMemoryBatch already pre-extracted `pulled` FE
        // from cell into escrow in one shot, so the per-target modulate-pass
        // extracts all hit escrow and never trigger a per-call
        // FluxCellInventory.saveChanges chain.
        if (batchOnly) {
            return extractFromBuffer(needed, mode) / costMultiplier;
        }

        return creditExtraction(extractRawWithScratch(feKey, needed, mode, source, false), mode);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!com.moakiee.ae2lt.logic.ResourceIngressPolicy.canAccept(what, isOnline())) return 0;
        if (!isFeKey(what) || amount <= 0L) {
            return delegate.insert(what, amount, mode, source);
        }
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null) return delegate.insert(what, amount, mode, source);

        long credit = Math.min(saturatingMul(amount, costMultiplier), escrow.room());
        credit -= credit % costMultiplier;
        if (mode == Actionable.MODULATE && credit > 0) {
            escrow.credit(credit);
            recordReturn(credit);
            if (!batchOnly) flush(feKey, source);
        }
        return credit / costMultiplier;
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    // ---- direct-send (OVERLOAD) -----------------------------------------

    /**
     * OVERLOAD path. Pull up to {@code amount} FE straight from the cell —
     * <b>no inline refill</b>. The caller in {@code sendToTargetRepeatedOptimistic}
     * is responsible for triggering refill on shortfall by computing a
     * single-call demand via {@code simulateReceive} and feeding it through
     * {@link #refillBudgetForDirectSend} → {@link #refillForDirectSend},
     * because {@code amount} here is the <i>remaining batch budget</i>
     * ({@code maxFe × remainingCalls × cost}), not a single call's demand.
     * Doing inline refill here would size the refill against the entire
     * remaining batch and pull the network dry / fill the cell to the brim
     * every call.
     */
    public long extractForDirectSend(long amount, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || amount <= 0L) return 0L;
        long needed = saturatingMul(amount, costMultiplier);
        if (batchOnly) return extractFromBuffer(needed, Actionable.MODULATE) / costMultiplier;
        return creditExtraction(extractRawWithScratch(feKey, needed, Actionable.MODULATE, source, true), Actionable.MODULATE);
    }

    public long returnFromDirectSend(long amount, IActionSource source) {
        if (amount <= 0) return 0;
        long units = Math.min(amount, escrow.room() / costMultiplier);
        long credit = units * costMultiplier;
        escrow.credit(credit);
        recordReturn(credit);
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (!batchOnly && feKey != null) flush(feKey, source);
        return units;
    }

    /**
     * @return the budget the OVERLOAD path is allowed to refill into the cell
     *         when its energy runs short — current per-call cost plus 19
     *         ticks of historical consumption (the legacy 20-tick lookahead).
     */
    public long refillBudgetForDirectSend(long currentDemand) {
        if (currentDemand <= 0L || resolveCell() == null) return 0L;
        long currentCost = saturatingMul(currentDemand, costMultiplier);
        return saturatingAdd(currentCost, recentConsumptionBeforeCurrentTick());
    }

    public void refillForDirectSend(long refillBudget, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        MEStorage cell = resolveCell();
        if (feKey == null || refillBudget <= 0L || cell == null) return;
        refillFromDelegateInline(cell, feKey, refillBudget, source);
    }

    // ---- batch (NORMAL) -------------------------------------------------

    /**
     * Pre-tick refill: ensure the cell holds at least {@code demand} FE so the
     * subsequent distribution loop can extract from cell without further
     * delegate calls. With no cell installed, falls back to the persistent
     * {@link #escrow} (one delegate.extract).
     *
     * @return amount actually available for distribution this tick (might be
     *         less than {@code demand} if neither cell nor delegate can cover
     *         it).
     */
    public long beginMemoryBatch(AEKey feKey, long demand, IActionSource source) {
        if (demand <= 0L || !isOnline()) return 0L;
        batchOnly = true;
        long retained = Math.min(demand, escrow.available());
        demand -= retained;
        demand = Math.min(demand, escrow.room());
        if (demand == 0) return retained;

        MEStorage cell = resolveCell();
        if (cell != null) {
            // Single cell.extract MODULATE for the entire NORMAL batch instead
            // of one per target. Saves N - 1 trips through
            // FluxCellInventory.saveChanges → callback → BE.saveChanges →
            // Level.blockEntityChanged → TickHandler.addCallable.
            long pulled = extractFromStorage(cell, feKey, demand, Actionable.MODULATE, source);
            escrow.credit(pulled);
            if (pulled < demand) {
                long shortfall = demand - pulled;
                long ahead = recentConsumptionBeforeCurrentTick();
                refillFromDelegateInline(cell, feKey, saturatingAdd(shortfall, ahead), source);
                long retry = extractFromStorage(cell, feKey, Math.min(shortfall, escrow.room()), Actionable.MODULATE, source);
                escrow.credit(retry);
            }
            return Math.min(retained + demand, escrow.available());
        }

        // No cell — pull straight from the ME network into escrow.
        long pulled = extractFromStorage(delegate, feKey, demand, Actionable.MODULATE, source);
        if (pulled > 0L) {
            escrow.credit(pulled);
        }
        return retained + pulled;
    }

    public long endBatch(AEKey feKey, IActionSource source) {
        try { return flush(feKey, source); }
        finally { batchOnly = false; }
    }

    public long flush(AEKey feKey, IActionSource source) {
        MEStorage cell = resolveCell();
        long returned = cell == null ? 0 : refundTo(cell, feKey, source);
        return saturatingAdd(returned, refundTo(delegate, feKey, source));
    }

    private long refundTo(MEStorage destination, AEKey feKey, IActionSource source) {
        return refundTo(destination, feKey, source, escrow.available());
    }

    private long refundTo(MEStorage destination, AEKey feKey, IActionSource source, long amount) {
        long offered = escrow.debit(amount);
        if (offered == 0) return 0;
        // Debit before invoking external storage: a reentrant flush must see no spendable copy.
        escrow.quarantine(offered);
        try {
            long returned = destination.insert(feKey, offered, Actionable.MODULATE, source);
            if (returned < 0 || returned > offered) {
                if (ledgerOwned) throw new IllegalStateException("Invalid FE refund receipt");
                return 0;
            }
            escrow.settle(offered, returned);
            return returned;
        } catch (RuntimeException | LinkageError uncertain) {
            if (ledgerOwned) throw new IllegalStateException("Unknown FE refund receipt", uncertain);
            com.moakiee.ae2lt.util.MixinReflectionSupport.logReflectionFailure("return FE (receipt unknown)", uncertain);
            return 0;
        }
    }

    /**
     * Tick-end synchronisation point. Attempts to return available {@link #escrow}
     * to the cell or delegate, retaining rejected amounts, then invokes the persist callback so the host
     * can write the cell's storedEnergy into the ItemStack data component.
     *
     * <p>The callback is responsible for both the cell's persist and the
     * host's setChanged; this class deliberately avoids touching either so
     * we don't double-write the ItemStack and don't couple to AppFlux types.
     */
    public long endTick(AEKey feKey, IActionSource source) {
        long flushed = escrow.available() > 0L ? flush(feKey, source) : 0L;
        if (!ledgerOwned && pendingPersist != null && lastNotifiedPendingEnergy != escrow.available()) {
            pendingPersist.run();
            lastNotifiedPendingEnergy = escrow.available();
        }
        if (cellPersistCallback != null && resolveCell() != null) {
            cellPersistCallback.run();
        }
        return flushed;
    }

    /**
     * Lifecycle cleanup (cell removed, mode switched, BE removed, chunk
     * unloaded). Refused refunds remain owned by the escrow; reset only batch state.
     */
    public long flushAll(AEKey feKey, IActionSource source) {
        long flushed = endTick(feKey, source);
        batchOnly = false;
        return flushed;
    }

    public void advanceHistory() {
        if (consumptionHistory == null) return;
        historyPointer = (historyPointer + 1) % consumptionHistory.length;
        consumptionHistory[historyPointer] = 0L;
    }

    public void setCostMultiplier(int multiplier) {
        nextCostMultiplier = Math.max(1, multiplier);
        if (!transferring) costMultiplier = nextCostMultiplier;
    }

    public long getBufferedEnergy() {
        MEStorage cell = resolveCell();
        if (cell != null) {
            AEKey feKey = AppFluxBridge.FE_KEY;
            if (feKey == null) return 0L;
            return saturatingAdd(escrow.available(), extractFromStorage(cell, feKey, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()));
        }
        return escrow.available();
    }

    /**
     * Compatibility guard: discarding owned FE is forbidden. Lifecycle cleanup must
     * use {@link #flushAll(AEKey, IActionSource)} and retain any refused balance.
     */
    public void clearBuffer() {
        if (escrow.available() != 0) throw new IllegalStateException("Cannot discard owned FE");
    }

    private long extractRawWithScratch(AEKey key, long needed, Actionable mode, IActionSource source, boolean direct) {
        long owned = Math.min(needed, escrow.available());
        if (mode == Actionable.MODULATE) escrow.debit(owned);
        long missing = Math.min(needed - owned, escrow.room());
        if (missing == 0) return owned;
        try {
            MEStorage cell = resolveCell();
            long pulled = cell != null
                    ? direct ? extractFromStorage(cell, key, missing, mode, source)
                             : extractThroughCell(cell, key, missing, mode, source)
                    : direct ? 0 : extractFromStorage(delegate, key, missing, mode, source);
            return owned + pulled;
        } catch (RuntimeException | LinkageError failure) {
            if (mode == Actionable.MODULATE) escrow.credit(owned);
            throw failure;
        }
    }

    private long creditExtraction(long raw, Actionable mode) {
        long credited = raw / costMultiplier;
        long remainder = raw % costMultiplier;
        if (mode == Actionable.MODULATE) {
            escrow.credit(remainder);
            recordConsumption(raw - remainder);
        }
        return credited;
    }

    public void quarantineDelivery(long amount) { escrow.quarantine(saturatingMul(amount, costMultiplier)); }
    public void settleDelivery(long offered, long accepted) {
        escrow.settle(saturatingMul(offered, costMultiplier), saturatingMul(accepted, costMultiplier));
        recordReturn(saturatingMul(offered - accepted, costMultiplier));
    }

    // ---- helpers --------------------------------------------------------

    /**
     * Cell-aware extract with inline refill on shortfall. Mirrors the AE2 ME
     * Chest pattern: whenever the cell can't satisfy a demand, top it up from
     * the delegate (ME network) once, then retry. Refill size includes the
     * 20-tick consumption lookahead so we minimise delegate calls during
     * sustained loads.
     */
    private long extractThroughCell(MEStorage cell, AEKey feKey, long needed, Actionable mode,
                                    IActionSource source) {
        if (mode == Actionable.SIMULATE) return extractFromStorage(cell, feKey, needed, mode, source);
        long pulled = extractFromStorage(cell, feKey, needed, mode, source);
        escrow.credit(pulled);
        if (pulled < needed) {
            long shortfall = needed - pulled;
            refillFromDelegateInline(cell, feKey, saturatingAdd(shortfall, recentConsumptionBeforeCurrentTick()), source);
            escrow.credit(extractFromStorage(cell, feKey, Math.min(shortfall, escrow.room()), mode, source));
        }
        return escrow.debit(needed);
    }

    /** Own every source withdrawal before calling the cell. Rejected cell writes stay recoverable. */
    private void refillFromDelegateInline(MEStorage cell, AEKey feKey, long request,
                                          IActionSource source) {
        if (request <= 0L || !isOnline()) return;
        long freeSpace = cell.insert(feKey, Long.MAX_VALUE, Actionable.SIMULATE, source);
        if (!isOnline()) return;
        long refill = Math.min(Math.min(request, freeSpace), escrow.room());
        if (refill <= 0L) return;
        long pulled = extractFromStorage(delegate, feKey, refill, Actionable.MODULATE, source);
        if (pulled <= 0L) return;
        escrow.credit(pulled);
        refundTo(cell, feKey, source, pulled);
    }

    private long extractFromBuffer(long needed, Actionable mode) {
        if (mode == Actionable.SIMULATE) {
            return Math.min(needed, escrow.available());
        }
        long extracted = Math.min(needed, escrow.available());
        extracted -= extracted % costMultiplier;
        escrow.debit(extracted);
        if (extracted > 0L) recordConsumption(extracted);
        return extracted;
    }

    private long extractFromStorage(MEStorage storage, AEKey key, long amount, Actionable mode, IActionSource source) {
        long receipt = storage.extract(key, amount, mode, source);
        if (receipt >= 0 && receipt <= amount) return receipt;
        com.moakiee.ae2lt.util.MixinReflectionSupport.logReflectionFailure("validate FE withdrawal",
                new IllegalStateException("Invalid receipt " + receipt + " for " + amount));
        return 0;
    }

    @Nullable
    private MEStorage resolveCell() {
        return cellSupplier != null ? cellSupplier.get() : null;
    }

    private long recentConsumptionBeforeCurrentTick() {
        if (consumptionHistory == null) return 0;
        long sum = 0L;
        for (int i = 0; i < consumptionHistory.length; i++) {
            if (i != historyPointer) {
                sum = saturatingAdd(sum, consumptionHistory[i]);
            }
        }
        return sum;
    }

    private void recordConsumption(long amount) {
        if (consumptionHistory == null) return;
        consumptionHistory[historyPointer] = saturatingAdd(consumptionHistory[historyPointer], amount);
    }

    private void recordReturn(long amount) {
        if (consumptionHistory == null) return;
        consumptionHistory[historyPointer] = Math.max(0L, consumptionHistory[historyPointer] - amount);
    }

    private boolean isFeKey(AEKey what) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        return feKey != null && feKey.equals(what);
    }
}
