package com.moakiee.ae2lt.machine.common;

import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import net.minecraft.world.item.ItemStack;

/** A bounded, one-shot operation. Each machine owns its cooldown, including across menu reopenings. */
public final class ManualInputTransfer {
    public static final int COOLDOWN_TICKS = 4;
    public static final int EXPORT_ATTEMPTS = 12;
    private long lastRequest = Long.MIN_VALUE;
    private boolean running;

    public record Result(boolean accepted, long moved, long exported, int remainingSlots) {}

    @FunctionalInterface
    public interface Exporter {
        long export(Budget budget);
    }

    public static final class Budget {
        private int remaining = EXPORT_ATTEMPTS;

        public boolean take() {
            if (remaining == 0) return false;
            remaining--;
            return true;
        }

        public boolean hasRemaining() { return remaining > 0; }
    }

    public Result execute(long gameTime, LargeStackItemHandler inventory,
            int firstInput, int inputCount, int firstOutput, int outputCount,
            Exporter exporter, Runnable cancelProcessing) {
        if (running || (lastRequest != Long.MIN_VALUE && gameTime >= lastRequest
                && gameTime - lastRequest < COOLDOWN_TICKS)) {
            return new Result(false, 0, 0, 0);
        }
        lastRequest = gameTime;
        running = true;
        long[] totals = new long[2];
        Budget budget = new Budget();
        try {
            inventory.batchChanges(() -> {
                // Capture the click's inputs before calling external storage.
                ItemStack[] inputs = new ItemStack[inputCount];
                for (int i = 0; i < inputCount; i++) inputs[i] = inventory.getStackInSlot(firstInput + i).copy();
                totals[1] += exporter.export(budget);
                for (int i = 0; i < inputCount; i++) {
                    ItemStack snapshot = inputs[i];
                    int remaining = snapshot.getCount();
                    while (remaining > 0) {
                        ItemStack current = inventory.getStackInSlot(firstInput + i);
                        if (!ItemStack.isSameItemSameComponents(snapshot, current)) break;
                        int moved = 0;
                        for (int pass = 0; pass < 2; pass++) {
                            for (int output = firstOutput; output < firstOutput + outputCount; output++) {
                                if (inventory.getStackInSlot(output).isEmpty() != (pass == 1)) continue;
                                int amount = inventory.moveToOutput(firstInput + i, output, remaining);
                                remaining -= amount;
                                moved += amount;
                            }
                        }
                        if (moved > 0 && totals[0] == 0) cancelProcessing.run();
                        totals[0] += moved;
                        long exported = budget.hasRemaining() ? exporter.export(budget) : 0;
                        totals[1] += exported;
                        // No unbounded retry when a receiver only accepts tiny amounts.
                        if (remaining == 0 || exported == 0) break;
                    }
                }
            });
            int remainingSlots = 0;
            for (int i = 0; i < inputCount; i++) {
                if (!inventory.getStackInSlot(firstInput + i).isEmpty()) remainingSlots++;
            }
            return new Result(true, totals[0], totals[1], remainingSlots);
        } finally {
            running = false;
        }
    }
}
