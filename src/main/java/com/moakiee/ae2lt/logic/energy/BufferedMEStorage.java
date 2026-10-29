package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.logic.ResourceIngressPolicy;
import net.minecraft.network.chat.Component;
import com.moakiee.ae2lt.util.MixinReflectionSupport;

/**
 * Single-cell FE buffer/proxy in front of an ME storage delegate.
 *
 * <p>Mirrors AE2's {@code MEChestBlockEntity} architecture: when a Flux Cell
 * is installed, the cell IS the buffer. Every {@link #extract}/{@link #insert}
 * mutates the cell directly; the cell's internal {@code storedEnergy}
 * counter is the canonical source of truth. The cell's ItemStack-data
 * persist is deferred to {@link #endTick(AEKey, IActionSource)} so that 64+ writes
 * during a single OVERLOAD tick collapse to a single ItemStack NBT
 * update.
 *
 * <p>When no cell is installed, NORMAL-mode distribution falls back to the
 * transient {@link #feBuffer}; OVERLOAD mode short-circuits since
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
    private final long[] consumptionHistory = new long[HISTORY_SIZE];

    /** Owned raw FE: batch preloads, fractional fees and confirmed rejected refunds. */
    private long feBuffer;
    private boolean batchOnly;
    private int historyPointer;
    private int costMultiplier = 1;
    private int transferDepth;
    private int nextCostMultiplier = 1;
    @Nullable
    private final Runnable pendingPersistCallback;
    private long lastNotifiedPendingEnergy;
    private final java.util.function.BooleanSupplier onlineSupplier;

    public BufferedMEStorage(MEStorage delegate) {
        this(delegate, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier) {
        this(delegate, cellSupplier, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier,
                             @Nullable Runnable cellPersistCallback) {
        this(delegate, cellSupplier, cellPersistCallback, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier,
                             @Nullable Runnable cellPersistCallback, @Nullable Runnable pendingPersistCallback) {
        this(delegate, cellSupplier, cellPersistCallback, pendingPersistCallback, () -> true);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier,
                             @Nullable Runnable cellPersistCallback, @Nullable Runnable pendingPersistCallback,
                             java.util.function.BooleanSupplier onlineSupplier) {
        this.delegate = delegate;
        this.cellSupplier = cellSupplier;
        this.cellPersistCallback = cellPersistCallback;
        this.pendingPersistCallback = pendingPersistCallback;
        this.onlineSupplier = java.util.Objects.requireNonNull(onlineSupplier);
    }

    void rebindDelegate(MEStorage delegate) {
        // Grid replacement must not abandon locally owned return/remainder energy.
        this.delegate = delegate;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0L) {
            return delegate.extract(what, amount, mode, source);
        }
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null) return 0L;

        long needed = saturatingMul(amount, costMultiplier);

        // NORMAL batch path: beginMemoryBatch already pre-extracted `pulled` FE
        // from cell into feBuffer in one shot, so the per-target modulate-pass
        // extracts all hit feBuffer and never trigger a per-call
        // FluxCellInventory.saveChanges chain.
        if (batchOnly) {
            return extractFromBuffer(needed, mode) / costMultiplier;
        }

        return creditWholeUnits(extractRawWithScratch(feKey, needed, mode, source, false), mode);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!ResourceIngressPolicy.canAccept(what, onlineSupplier.getAsBoolean())) return 0L;
        return insertOwned(what, amount, mode, source);
    }

    /** Confirmed undelivered transfer funds remain owned even if a callback disconnects the host. */
    long returnUndelivered(long amount, IActionSource source) {
        if (amount <= 0L) return 0L;
        // This is a confirmed refund of funds already withdrawn by this
        // buffer. It must remain recoverable even if the grid disappeared
        // while the target callback was running.
        long units = Math.min(amount, (Long.MAX_VALUE - feBuffer) / costMultiplier);
        long credit = units * costMultiplier;
        feBuffer = saturatingAdd(feBuffer, credit);
        recordReturn(credit);
        return units;
    }

    private long insertOwned(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0L) {
            return delegate.insert(what, amount, mode, source);
        }
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null) return delegate.insert(what, amount, mode, source);

        long units = Math.min(amount, (Long.MAX_VALUE - feBuffer) / costMultiplier);
        long credit = units * costMultiplier;
        long accepted;

        // NORMAL batch path: leftover from undelivered demand goes back to
        // feBuffer; endBatch will flush it back to cell or delegate in a
        // single call. Skips a per-target cell.insert + saveChanges chain.
        // recordReturn rebalances the history bucket: extractFromBuffer
        // already recorded `extracted` as consumed in this tick, but the
        // leftover never actually left the system, so we cancel that slice.
        if (batchOnly) {
            if (mode == Actionable.MODULATE && credit > 0L) {
                feBuffer = saturatingAdd(feBuffer, credit);
                recordReturn(credit);
            }
            return units;
        }

        if (mode == Actionable.MODULATE) {
            feBuffer = saturatingAdd(feBuffer, credit);
            recordReturn(credit);
            flush(feKey, source);
            return units;
        }
        MEStorage cell = resolveCell();
        if (cell != null) {
            long intoCell = insertIntoStorage(cell, feKey, credit, mode, source);
            long overflow = credit - intoCell;
            long intoDelegate = overflow > 0L ? insertIntoStorage(delegate, feKey, overflow, mode, source) : 0L;
            accepted = saturatingAdd(intoCell, intoDelegate);
        } else if (feBuffer > 0L) {
            accepted = credit;
        } else {
            accepted = insertIntoStorage(delegate, feKey, credit, mode, source);
        }

        return accepted / costMultiplier;
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
        return creditWholeUnits(extractRawWithScratch(feKey, needed, Actionable.MODULATE, source, true),
                Actionable.MODULATE);
    }

    public long returnFromDirectSend(long amount, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || amount <= 0L) return 0L;
        long units = Math.min(amount, (Long.MAX_VALUE - feBuffer) / costMultiplier);
        long credit = units * costMultiplier;
        feBuffer = saturatingAdd(feBuffer, credit);
        recordReturn(credit);
        if (!batchOnly) flush(feKey, source);
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
        if (!isOnline()) return;
        AEKey feKey = AppFluxBridge.FE_KEY;
        MEStorage cell = resolveCell();
        if (feKey == null || refillBudget <= 0L || cell == null) return;
        refillFromDelegateInline(cell, feKey, refillBudget, source);
    }

    // ---- batch (NORMAL) -------------------------------------------------

    /**
     * Pre-tick refill: ensure the cell holds at least {@code demand} FE so the
     * subsequent distribution loop can extract from cell without further
     * delegate calls. With no cell installed, falls back to the transient
     * {@link #feBuffer} (one delegate.extract).
     *
     * @return amount actually available for distribution this tick (might be
     *         less than {@code demand} if neither cell nor delegate can cover
     *         it).
     */
    public long beginMemoryBatch(AEKey feKey, long demand, IActionSource source) {
        if (demand <= 0L || !onlineSupplier.getAsBoolean()) return 0L;
        batchOnly = true;
        long available = Math.min(demand, feBuffer);
        long missing = demand - available;
        if (missing == 0L) return available;

        MEStorage cell = resolveCell();
        if (cell != null) {
            // Single cell.extract MODULATE for the entire NORMAL batch instead
            // of one per target. Saves N - 1 trips through
            // FluxCellInventory.saveChanges → callback → BE.saveChanges →
            // Level.blockEntityChanged → TickHandler.addCallable.
            long pulled = extractFromStorage(cell, feKey, missing, Actionable.MODULATE, source);
            feBuffer = saturatingAdd(feBuffer, pulled);
            if (pulled < missing) {
                long shortfall = missing - pulled;
                long ahead = recentConsumptionBeforeCurrentTick();
                long ideal = saturatingAdd(shortfall, ahead);
                refillFromDelegateInline(cell, feKey, ideal, source);
                long retry = extractFromStorage(cell, feKey, shortfall, Actionable.MODULATE, source);
                pulled += retry;
                feBuffer = saturatingAdd(feBuffer, retry);
            }
            return Math.min(demand, feBuffer);
        }

        // No cell — pull straight from the ME network into feBuffer.
        long pulled = extractFromStorage(delegate, feKey, missing, Actionable.MODULATE, source);
        if (pulled > 0L) {
            feBuffer = saturatingAdd(feBuffer, pulled);
        }
        return available + pulled;
    }

    public long endBatch(AEKey feKey, IActionSource source) {
        try {
            if (feBuffer <= 0L) return 0L;

            // Anything left in feBuffer after the modulate pass is FE that
            // beginMemoryBatch over-pulled (target rejected its slice mid-loop
            // or demand shrank). Return it to the cell — or, if no cell is
            // installed, to the delegate — in a single insert call.
            return flush(feKey, source);
        } finally {
            batchOnly = false;
        }
    }

    public long flush(AEKey feKey, IActionSource source) {
        if (feBuffer <= 0L) return 0L;
        // Used only by lifecycle paths (endTick / flushAll). When a cell is
        // installed we prefer to keep the FE in the cell rather than dump it
        // back to the network so the player's installed disk reflects reality.
        MEStorage cell = resolveCell();
        long intoCell = 0L;
        if (cell != null) {
            intoCell = flushToStorage(cell, feKey, source);
        }
        if (feBuffer <= 0L) return intoCell;
        long returned = flushToStorage(delegate, feKey, source);
        return intoCell + returned;
    }

    private long flushToStorage(MEStorage target, AEKey feKey, IActionSource source) {
        long attempt = feBuffer;
        feBuffer = 0L;
        try {
            long returned = insertIntoStorage(target, feKey, attempt, Actionable.MODULATE, source);
            feBuffer = saturatingAdd(feBuffer, attempt - returned);
            return returned;
        } catch (RuntimeException | LinkageError uncertain) {
            // Receipt is unknowable even for a refund. Never replay a credit
            // that may already have been stored before this exception.
            MixinReflectionSupport.logReflectionFailure("return FE to storage " + target.getClass().getName()
                    + " (receipt unknown; in-flight refund will not be retried)", uncertain);
            return 0L;
        }
    }

    /**
     * Tick-end synchronisation point. Flushes any orphan {@link #feBuffer}
     * back to the delegate (defensive — should already be empty if
     * {@link #endBatch} ran), then invokes the persist callback so the host
     * can write the cell's storedEnergy into the ItemStack NBT.
     *
     * <p>The callback is responsible for both the cell's persist and the
     * host's setChanged; this class deliberately avoids touching either so
     * we don't double-write the ItemStack and don't couple to AppFlux types.
     */
    public long endTick(AEKey feKey, IActionSource source) {
        long flushed = feBuffer > 0L ? flush(feKey, source) : 0L;
        if (cellPersistCallback != null && resolveCell() != null) {
            cellPersistCallback.run();
        }
        if (pendingPersistCallback != null && lastNotifiedPendingEnergy != feBuffer) {
            pendingPersistCallback.run();
            lastNotifiedPendingEnergy = feBuffer;
        }
        return flushed;
    }

    /**
     * Lifecycle cleanup (cell removed, mode switched, BE removed, chunk
     * unloaded). Unreturned energy stays owned by this buffer; refusal is not
     * permission to discard it. Hosts must retain the buffer until drained.
     */
    public long flushAll(AEKey feKey, IActionSource source) {
        return endTick(feKey, source);
    }

    public void advanceHistory() {
        historyPointer = (historyPointer + 1) % consumptionHistory.length;
        consumptionHistory[historyPointer] = 0L;
    }

    public void setCostMultiplier(int multiplier) {
        nextCostMultiplier = Math.max(1, multiplier);
        if (transferDepth == 0) costMultiplier = nextCostMultiplier;
    }

    // Pin the cost of withdrawn FE through target callbacks and the matching refund.
    boolean isOnline() {
        return onlineSupplier.getAsBoolean();
    }

    boolean beginTransfer() {
        // Foreign callbacks must not recursively enter a transaction on the same funds.
        if (transferDepth != 0 || !onlineSupplier.getAsBoolean()) return false;
        transferDepth = 1;
        return true;
    }

    void endTransfer() {
        if (transferDepth != 0) {
            transferDepth = 0;
            costMultiplier = nextCostMultiplier;
        }
    }

    long getPendingEnergy() { return feBuffer; }

    /** Transfer the locally owned refund to a dismantle item without replaying it. */
    long takePendingEnergy() {
        long pending = feBuffer;
        feBuffer = 0L;
        lastNotifiedPendingEnergy = 0L;
        return pending;
    }

    void restorePendingEnergy(long amount) {
        feBuffer = Math.max(0L, amount);
        batchOnly = false;
        lastNotifiedPendingEnergy = feBuffer;
    }

    public long getBufferedEnergy() {
        MEStorage cell = resolveCell();
        if (cell != null) {
            AEKey feKey = AppFluxBridge.FE_KEY;
            if (feKey == null) return feBuffer;
            return saturatingAdd(feBuffer,
                    extractFromStorage(cell, feKey, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()));
        }
        return feBuffer;
    }

    /**
     * Clears the transient preload buffer WITHOUT returning FE to the delegate.
     * Prefer {@link
     * #flushAll(AEKey, IActionSource)} for lifecycle cleanup.
     */
    public void clearBuffer() {
        feBuffer = 0L;
    }

    // ---- helpers --------------------------------------------------------

    private long extractRawWithScratch(AEKey feKey, long needed, Actionable mode, IActionSource source,
                                      boolean direct) {
        long owned = Math.min(needed, feBuffer);
        if (mode == Actionable.MODULATE) feBuffer -= owned;
        long missing = needed - owned;
        if (missing == 0L) return owned;
        try {
            MEStorage cell = resolveCell();
            long pulled = cell != null
                    ? direct ? extractFromStorage(cell, feKey, missing, mode, source)
                             : extractThroughCell(cell, feKey, missing, mode, source)
                    : direct ? 0L : extractFromStorage(delegate, feKey, missing, mode, source);
            return owned + pulled;
        } catch (RuntimeException | LinkageError failure) {
            // Our scratch portion has not entered a foreign API and is safe to restore.
            if (mode == Actionable.MODULATE) feBuffer = saturatingAdd(feBuffer, owned);
            throw failure;
        }
    }

    /**
     * Cell-aware extract with inline refill on shortfall. Mirrors the AE2 ME
     * Chest pattern: whenever the cell can't satisfy a demand, top it up from
     * the delegate (ME network) once, then retry. Refill size includes the
     * 20-tick consumption lookahead so we minimise delegate calls during
     * sustained loads.
     */
    private long extractThroughCell(MEStorage cell, AEKey feKey, long needed, Actionable mode,
                                    IActionSource source) {
        long extracted = extractFromStorage(cell, feKey, needed, mode, source);
        if (extracted >= needed) {
            return extracted;
        }

        // Only MODULATE is allowed to refill — SIMULATE must remain side-effect-free.
        if (mode == Actionable.MODULATE) {
            long shortfall = needed - extracted;
            long ahead = recentConsumptionBeforeCurrentTick();
            long ideal = saturatingAdd(shortfall, ahead);
            try {
                refillFromDelegateInline(cell, feKey, ideal, source);
                long retry = extractFromStorage(cell, feKey, shortfall, Actionable.MODULATE, source);
                extracted += retry;
            } catch (RuntimeException | LinkageError failure) {
                // This part has not entered a target API; we still own it.
                feBuffer = saturatingAdd(feBuffer, extracted);
                throw failure;
            }
        }
        return extracted;
    }

    private long creditWholeUnits(long extracted, Actionable mode) {
        long credited = extracted / costMultiplier;
        if (mode == Actionable.MODULATE && extracted > 0L) {
            long consumed = credited * costMultiplier;
            feBuffer = saturatingAdd(feBuffer, extracted - consumed);
            if (consumed > 0L) recordConsumption(consumed);
        }
        return credited;
    }

    /**
     * Pull up to {@code request} FE from the delegate into the cell, capped
     * at the cell's current free space. If the cell rejects part of the
     * delivery (race), the rejected amount goes back to the delegate so we
     * never silently void energy.
     */
    private void refillFromDelegateInline(MEStorage cell, AEKey feKey, long request,
                                          IActionSource source) {
        if (request <= 0L || !isOnline()) return;
        long freeSpace = insertIntoStorage(cell, feKey, Long.MAX_VALUE, Actionable.SIMULATE, source);
        // Capacity simulation is foreign code and can detach the host too.
        if (!isOnline()) return;
        long refill = Math.min(request, freeSpace);
        if (refill <= 0L) return;
        long pulled = extractFromStorage(delegate, feKey, refill, Actionable.MODULATE, source);
        if (pulled <= 0L) return;
        // The full attempt is in-flight; only a confirmed rejection can return.
        long inserted = insertIntoStorage(cell, feKey, pulled, Actionable.MODULATE, source);
        long rejected = pulled - inserted;
        feBuffer = saturatingAdd(feBuffer, rejected);
        if (rejected > 0L) {
            feBuffer -= rejected;
            long returned = insertIntoStorage(delegate, feKey, rejected, Actionable.MODULATE, source);
            feBuffer = saturatingAdd(feBuffer, rejected - returned);
        }
    }

    private long extractFromBuffer(long needed, Actionable mode) {
        if (mode == Actionable.SIMULATE) {
            return Math.min(needed, feBuffer);
        }
        // A fractional fee is not delivered energy. Leave it in the buffer.
        long extracted = (Math.min(needed, feBuffer) / costMultiplier) * costMultiplier;
        feBuffer -= extracted;
        if (extracted > 0L) recordConsumption(extracted);
        return extracted;
    }

    private long extractFromStorage(MEStorage storage, AEKey key, long amount, Actionable mode,
                                    IActionSource source) {
        long receipt = storage.extract(key, amount, mode, source);
        if (receipt >= 0L && receipt <= amount) return receipt;
        logMalformedReceipt(storage, "extract", amount, receipt);
        // An invalid receipt cannot authorize delivery. The foreign withdrawal is unknown.
        return 0L;
    }

    private long insertIntoStorage(MEStorage storage, AEKey key, long amount, Actionable mode,
                                   IActionSource source) {
        long receipt = storage.insert(key, amount, mode, source);
        if (receipt >= 0L && receipt <= amount) return receipt;
        logMalformedReceipt(storage, "insert", amount, receipt);
        // Like an exception after credit, a malformed modulate receipt cannot be replayed.
        // Simulation is not permission to mutate, so invalid capacity means no capacity.
        return mode == Actionable.MODULATE ? amount : 0L;
    }

    private static void logMalformedReceipt(MEStorage storage, String operation, long amount, long receipt) {
        MixinReflectionSupport.logReflectionFailure("validate FE " + operation + " receipt from "
                        + storage.getClass().getName(),
                new IllegalStateException("Storage reported " + receipt + " for request " + amount
                        + "; ownership is unknown, no replay or extra delivery is authorized"));
    }

    @Nullable
    private MEStorage resolveCell() {
        return cellSupplier != null ? cellSupplier.get() : null;
    }

    private long recentConsumptionBeforeCurrentTick() {
        long sum = 0L;
        for (int i = 0; i < consumptionHistory.length; i++) {
            if (i != historyPointer) {
                sum = saturatingAdd(sum, consumptionHistory[i]);
            }
        }
        return sum;
    }

    private void recordConsumption(long amount) {
        consumptionHistory[historyPointer] = saturatingAdd(consumptionHistory[historyPointer], amount);
    }

    private void recordReturn(long amount) {
        consumptionHistory[historyPointer] = Math.max(0L, consumptionHistory[historyPointer] - amount);
    }

    private boolean isFeKey(AEKey what) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        return feKey != null && feKey.equals(what);
    }
}
