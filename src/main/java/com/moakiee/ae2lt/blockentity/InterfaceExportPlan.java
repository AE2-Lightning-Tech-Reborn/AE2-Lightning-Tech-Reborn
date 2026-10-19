package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportConfigEntry;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportTransferState;

/** Per-target export order and shared-input-slot fairness; never owns transferred stacks. */
final class InterfaceExportPlan {
    private final List<ExportConfigEntry> entries;
    private final ExportTransferState[] transfers;
    private final boolean[] yieldRefill;
    private final int[] order;
    private final int[] representative;
    private final int[] nextDuplicate;
    private final int[] previousKey;
    private final int[] nextKey;
    private int firstKey = -1;
    private int lastKey = -1;
    private final int[] inputSlots;
    private final int[] claimedSlots;
    private final ExportDrainObservation[] drains;
    private IItemHandler observedHandler;
    private boolean orderDirty;

    InterfaceExportPlan(List<ExportConfigEntry> entries, ExportTransferState[] transfers) {
        this.entries = entries;
        this.transfers = transfers;
        boolean unlimited = false;
        boolean distinct = false;
        for (var entry : entries) {
            unlimited |= entry.key() instanceof AEItemKey && entry.maxAmount() == Long.MAX_VALUE;
            distinct |= !entry.key().equals(entries.get(0).key());
        }
        yieldRefill = unlimited && distinct ? new boolean[entries.size()] : null;
        if (yieldRefill == null) {
            order = representative = nextDuplicate = previousKey = nextKey = inputSlots = claimedSlots = null;
            drains = null;
            return;
        }
        int size = entries.size();
        order = new int[size];
        representative = new int[size];
        nextDuplicate = new int[size];
        java.util.Arrays.fill(nextDuplicate, -1);
        inputSlots = new int[size];
        claimedSlots = new int[size];
        drains = new ExportDrainObservation[size];
        var groups = new LinkedHashMap<AEKey, List<Integer>>();
        for (int i = 0; i < size; i++) groups.computeIfAbsent(entries.get(i).key(), ignored -> new ArrayList<>()).add(i);
        previousKey = new int[size];
        nextKey = new int[size];
        java.util.Arrays.fill(nextKey, -1);
        int position = 0;
        for (var indices : groups.values()) {
            int key = indices.get(0);
            previousKey[key] = lastKey;
            if (lastKey < 0) firstKey = key;
            else nextKey[lastKey] = key;
            lastKey = key;
            int previous = -1;
            for (int index : indices) {
                order[position++] = index;
                representative[index] = indices.get(0);
                if (previous >= 0) nextDuplicate[previous] = index;
                previous = index;
            }
        }
    }

    List<ExportConfigEntry> entries() { return entries; }
    ExportTransferState[] transfers() { return transfers; }
    boolean shouldYield(int index) { return yieldRefill != null && yieldRefill[index]; }

    int indexAt(int offset) {
        return order == null ? offset : order[offset];
    }

    void transferred(int index) {
        if (order == null) return;
        int key = representative[index];
        // Maintain recency directly; duplicate configuration slots share one turn.
        // The flattened pass order remains unchanged until the next prepare().
        if (key == lastKey) return;
        int before = previousKey[key], after = nextKey[key];
        if (before < 0) firstKey = after;
        else nextKey[before] = after;
        previousKey[after] = before;
        nextKey[lastKey] = key;
        previousKey[key] = lastKey;
        nextKey[key] = -1;
        lastKey = key;
        orderDirty = true;
    }

    private void prepareOrder() {
        if (!orderDirty) return;
        int position = 0;
        for (int key = firstKey; key >= 0; key = nextKey[key]) {
            for (int index = key; index >= 0; index = nextDuplicate[index]) order[position++] = index;
        }
        orderDirty = false;
    }

    void transferred(int index, @Nullable IItemHandler handler, long inserted, long now) {
        transferred(index);
        if (drains == null || handler == null) return;
        int canonical = representative[index];
        int slot = inputSlot(handler, canonical);
        if (slot < 0 || !(entries.get(canonical).key() instanceof AEItemKey key)) return;
        var stack = handler.getStackInSlot(slot);
        if (!key.matches(stack)) return;
        var observation = drain(canonical, slot);
        observation.inserted(stack.getCount(), inserted, now);
    }

    void prepare(@Nullable IItemHandler handler, MEStorage me, IActionSource source, long now) {
        if (yieldRefill == null) return;
        prepareOrder();
        java.util.Arrays.fill(yieldRefill, false);
        java.util.Arrays.fill(inputSlots, -2);
        if (observedHandler != handler) {
            java.util.Arrays.fill(drains, null);
            observedHandler = handler;
        }
        if (handler == null) return;
        int claims = 0;
        for (int offset = 0; offset < entries.size(); offset++) {
            int index = indexAt(offset);
            if (representative[index] != index) continue;
            var transfer = transfers[index];
            if (!transfer.waitingForSpace || !(entries.get(index).key() instanceof AEItemKey waiting)) continue;
            int slot = inputSlot(handler, index);
            if (slot < 0) continue;
            var resident = handler.getStackInSlot(slot);
            if (waiting.matches(resident)) continue;
            if (resident.isEmpty()) {
                transfer.untilTick = Math.min(transfer.untilTick, now);
                continue;
            }
            boolean claimed = false;
            for (int claim = 0; claim < claims; claim++) claimed |= claimedSlots[claim] == slot;
            if (claimed) {
                transfer.untilTick = now + 1;
                continue;
            }
            int occupying = -1;
            for (int candidate = 0; candidate < entries.size(); candidate++) {
                if (representative[candidate] == candidate && entries.get(candidate).key() instanceof AEItemKey key
                        && key.matches(resident) && inputSlot(handler, candidate) == slot) {
                    occupying = candidate;
                    break;
                }
            }
            if (occupying < 0) continue;
            if (me.extract(waiting, 1, Actionable.SIMULATE, source) <= 0) {
                transfer.waitingForSpace = false;
                continue;
            }
            claimedSlots[claims++] = slot;
            // One live challenger is enough to drain this slot. Other rejected
            // keys share its next-tick observation without repeating ME probes.
            transfer.untilTick = now + 1;
            boolean yield = drain(occupying, slot).shouldYield(resident.getCount(), now);
            for (int duplicate = occupying; duplicate >= 0; duplicate = nextDuplicate[duplicate]) {
                yieldRefill[duplicate] = yield;
            }
            transfers[occupying].untilTick = Math.min(transfers[occupying].untilTick, now);
        }
    }

    private ExportDrainObservation drain(int index, int slot) {
        var result = drains[index];
        if (result == null || result.slot != slot) drains[index] = result = new ExportDrainObservation(slot);
        return result;
    }

    private int inputSlot(IItemHandler handler, int index) {
        int canonical = representative[index];
        if (inputSlots[canonical] == -2) {
            inputSlots[canonical] = entries.get(canonical).key() instanceof AEItemKey key
                    ? singleInputSlot(handler, key) : -1;
        }
        return inputSlots[canonical];
    }

    private static int singleInputSlot(IItemHandler handler, AEItemKey key) {
        int result = -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getSlotLimit(slot) > 0 && handler.isItemValid(slot, key.getReadOnlyStack())) {
                if (result >= 0) return -1;
                result = slot;
            }
        }
        return result;
    }

    static final class ExportDrainObservation {
        final int slot;
        private int count;
        private int quantum;
        private long lastProgress = Long.MIN_VALUE;
        private long period = 1;

        ExportDrainObservation(int slot) { this.slot = slot; }

        void inserted(int current, long inserted, long now) {
            if (lastProgress == Long.MIN_VALUE || current == inserted) lastProgress = now;
            count = current;
        }

        boolean shouldYield(int current, long now) {
            if (lastProgress == Long.MIN_VALUE || now < lastProgress) {
                lastProgress = now;
                quantum = 0;
            } else if (current < count) {
                int consumed = count - current;
                quantum = quantum == 0 ? consumed : gcd(quantum, consumed);
                period = Math.max(1, now - lastProgress);
                lastProgress = now;
            } else if (now - lastProgress > Math.max(2, period * 2)) {
                // A recipe/period change or coupled ingredient may need more input.
                // An unchanged count is not proof that the machine can drain this tail.
                quantum = 0;
            }
            count = current;
            // Preserve full-size transfers. A short tail may not satisfy the next
            // recipe batch, so let normal export refill it rather than forcing a turn.
            return quantum > 0 && current >= quantum;
        }

        private static int gcd(int left, int right) {
            while (right != 0) {
                int remainder = left % right;
                left = right;
                right = remainder;
            }
            return left;
        }
    }
}
