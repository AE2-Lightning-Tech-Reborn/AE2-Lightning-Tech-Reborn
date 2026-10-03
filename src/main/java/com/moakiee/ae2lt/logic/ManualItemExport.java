package com.moakiee.ae2lt.logic;

import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.machine.common.ManualInputTransfer;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;

/** Item-only manual export; uses the machine's existing directional target cache. */
public final class ManualItemExport {
    private ManualItemExport() {}

    public static long push(IActionHost host, boolean enabled, BlockOrientation orientation,
            Set<RelativeSide> sides, LargeStackItemHandler inventory, int firstOutput, int outputCount,
            AdjacentItemAutoExportHelper.TargetResolver resolver, ManualInputTransfer.Budget budget) {
        if (!enabled || orientation == null || sides.isEmpty()) return 0;
        long total = 0;
        var source = IActionSource.ofMachine(host);
        for (int slot = firstOutput; slot < firstOutput + outputCount; slot++) {
            for (var side : sides) {
                if (inventory.getStackInSlot(slot).isEmpty()) break;
                if (!budget.take()) return total;
                var target = resolver.resolve(orientation.getSide(side));
                if (target == null) continue;
                total += inventory.exportOutput(slot, stack -> {
                    var key = AEItemKey.of(stack);
                    return key == null ? 0 : target.insert(key, stack.getCount(), Actionable.MODULATE, source);
                });
            }
        }
        return total;
    }
}
