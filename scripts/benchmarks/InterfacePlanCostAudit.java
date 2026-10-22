package com.moakiee.ae2lt.blockentity;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.IItemHandler;

/** Isolates live export-plan preparation; this is not a server MSPT benchmark. */
public final class InterfacePlanCostAudit {
    public static void main(String[] args) {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        measure(3, false);
        measure(3, true);
        measure(27, true);
    }

    private static void measure(int keys, boolean components) {
        var entries = new ArrayList<OverloadedInterfaceBlockEntity.ExportConfigEntry>();
        var transfers = new OverloadedInterfaceBlockEntity.ExportTransferState[keys];
        var stacks = new ItemStack[keys];
        var items = List.of(Items.IRON_ORE, Items.GOLD_ORE, Items.COPPER_ORE);
        for (int i = 0; i < keys; i++) {
            stacks[i] = new ItemStack(items.get(i % items.size()), 4096);
            if (components) stacks[i].set(DataComponents.CUSTOM_NAME, Component.literal("ore-" + i));
            entries.add(new OverloadedInterfaceBlockEntity.ExportConfigEntry(AEItemKey.of(stacks[i]), Long.MAX_VALUE));
            transfers[i] = new OverloadedInterfaceBlockEntity.ExportTransferState();
            transfers[i].rejected(0, OverloadedInterfaceBlockEntity.IOSpeedMode.FAST);
        }
        var plan = new InterfaceExportPlan(entries, transfers);
        var handler = new ResidentHandler();
        var source = IActionSource.empty();
        var storage = new MEStorage() {
            public Component getDescription() { return Component.literal("audit supply"); }
            public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
                if (mode != Actionable.SIMULATE) throw new AssertionError("preparation transferred items");
                return amount;
            }
        };
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        int calls = 100_000;
        long tick = 0;
        for (; tick < calls; tick++) {
            handler.stack = stacks[(int) ((tick / 8) % keys)];
            plan.prepare(handler, storage, source, tick);
        }
        for (int round = 0; round < 3; round++) {
            long before = bean.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            for (int call = 0; call < calls; call++, tick++) {
                // Reuse fixture stacks: measured allocations come from preparation.
                handler.stack = stacks[(int) ((tick / 8) % keys)];
                plan.prepare(handler, storage, source, tick);
            }
            long elapsed = System.nanoTime() - start;
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            System.out.printf("interface-plan-cost keys=%d components=%s round=%d ns/call=%d bytes/call=%.2f%n",
                    keys, components, round, elapsed / calls, allocated / (double) calls);
        }
    }

    private static final class ResidentHandler implements IItemHandler {
        ItemStack stack;
        public int getSlots() { return 1; }
        public ItemStack getStackInSlot(int slot) { return stack; }
        public int getSlotLimit(int slot) { return 4096; }
        public boolean isItemValid(int slot, ItemStack value) { return true; }
        public ItemStack insertItem(int slot, ItemStack value, boolean simulate) { throw new AssertionError(); }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { throw new AssertionError(); }
    }
}
