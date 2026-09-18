package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.*;

import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import java.math.BigInteger;
import java.util.*;

public final class BigCraftingStatus {
    private BigCraftingStatus() {}

    public static CraftingStatus create(BigCraftingCpu cpu) {
        if (!cpu.isBusy()) return new CraftingStatus(true, 0, 0, 0, List.of(), false);
        var job = cpu.job();
        var active = new LinkedHashMap<AEKey, BigInteger>();
        var pending = new LinkedHashMap<AEKey, BigInteger>();
        for (int i = job.blockIndex; i < job.program.blocks().size() && !job.returning; i++) {
            var block = job.program.blocks().get(i);
            var into = i == job.blockIndex && job.readyAt >= 0 ? active : pending;
            for (var step : block.steps()) {
                var p = step.recipe();
                var n = step.copies().multiply(block.repetitions());
                into.merge(p.output(), p.exactOutputAmount().multiply(n), BigInteger::add);
                for (var o : p.byproducts())
                    into.merge(o.key(), o.exactAmount().multiply(n), BigInteger::add);
            }
        }
        var keys = new LinkedHashSet<AEKey>();
        keys.addAll(job.escrow.keySet());
        keys.addAll(active.keySet());
        keys.addAll(pending.keySet());
        var entries = new ArrayList<CraftingStatusEntry>();
        long serial = 0;
        for (var key : keys) {
            var s = job.escrow.getOrDefault(key, BigInteger.ZERO);
            var a = active.getOrDefault(key, BigInteger.ZERO);
            var p = pending.getOrDefault(key, BigInteger.ZERO);
            var entry =
                    new CraftingStatusEntry(
                            serial++,
                            key,
                            BigAmounts.project(s),
                            BigAmounts.project(a),
                            BigAmounts.project(p));
            ((BigStatusEntry) entry).ae2lt$amounts(new BigStatusEntry.Amounts(s, a, p));
            entries.add(entry);
        }
        // Progress measures committed certified batches; item counts stay exact in the table.
        return new CraftingStatus(
                true,
                job.elapsedTicks * 50_000_000L,
                job.program.blocks().size() - job.blockIndex,
                job.program.blocks().size(),
                entries,
                job.suspended);
    }
}
