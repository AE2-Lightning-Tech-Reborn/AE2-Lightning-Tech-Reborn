package com.moakiee.ae2lt.client.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import com.moakiee.ae2lt.mixin.client.UpgradesPanelAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/** Resolves shared provider panels without discarding either mod's upgrade slots or tooltips. */
public final class ProviderPanelMerge {
    private ProviderPanelMerge() {
    }

    /** Null leaves AE2's normal duplicate-id check in charge. */
    @Nullable
    public static ICompositeWidget merge(String id, ICompositeWidget previous, ICompositeWidget incoming) {
        if ("upgrades".equals(id) && previous instanceof UpgradesPanel && incoming instanceof UpgradesPanel) {
            if (previous == incoming) {
                return incoming;
            }
            var first = (UpgradesPanelAccessor) previous;
            var second = (UpgradesPanelAccessor) incoming;
            return new UpgradesPanel(
                    mergeSlots(first.ae2lt$getSlots(), second.ae2lt$getSlots()),
                    mergeTooltips(first.ae2lt$getTooltipSupplier(), second.ae2lt$getTooltipSupplier()));
        }
        // The menu owns a single toolbox inventory. Keep the incoming, already-sized panel.
        // A subclass may carry extra behavior, so do not replace arbitrary toolbox widgets.
        if ("toolbox".equals(id) && previous != null
                && previous.getClass() == ToolboxPanel.class && incoming.getClass() == ToolboxPanel.class) {
            return incoming;
        }
        return null;
    }

    static List<Slot> mergeSlots(List<Slot> first, List<Slot> second) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Slot, Boolean>());
        var result = new ArrayList<Slot>(first.size() + second.size());
        for (var slots : List.of(first, second)) {
            for (var slot : slots) {
                if (seen.add(slot)) {
                    result.add(slot);
                }
            }
        }
        return List.copyOf(result);
    }

    static Supplier<List<Component>> mergeTooltips(
            Supplier<List<Component>> first, Supplier<List<Component>> second) {
        return () -> {
            var lines = new LinkedHashSet<>(first.get());
            lines.addAll(second.get());
            return List.copyOf(lines);
        };
    }
}
