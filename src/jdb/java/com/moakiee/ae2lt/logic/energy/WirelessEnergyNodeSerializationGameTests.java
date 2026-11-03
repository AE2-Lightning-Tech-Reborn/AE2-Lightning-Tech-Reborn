package com.moakiee.ae2lt.logic.energy;

import appeng.api.networking.IManagedGridNode;
import appeng.me.ManagedGridNode;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessEnergyNodeSerializationGameTests {
    private static final String CLAIM = "ae2ltEnergyClaim";
    private static final String TICK_BUDGET = "ae2ltEnergyTickBudget";

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty")
    public static void ordinaryAeNodeSerializesOffThreadWithoutCreatingEnergyState(GameTestHelper h) {
        var managed = node(h, null);
        try {
            var data = offThread(() -> {
                try {
                    WirelessEnergyLedger.get(h.getLevel().getServer());
                    throw new AssertionError("ledger thread confinement was removed");
                } catch (IllegalStateException expected) {
                    // The serializer must work without relaxing the ledger's thread guard.
                }
                return save(managed);
            });
            var fields = data.getCompound("proxy");
            h.assertTrue(data.contains("proxy"), "AE2 skipped the live node");
            h.assertTrue(!fields.contains(CLAIM) && !fields.contains(TICK_BUDGET),
                    "async serialization created energy ownership or a migration budget");
            h.assertTrue(data.equals(offThread(() -> save(managed))), "unchanged node produced unstable async NBT");
        } finally {
            managed.destroy();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty")
    public static void asyncNodeSnapshotRestoresExistingFeAeAndUncertainBalances(GameTestHelper h) {
        var original = node(h, null);
        IManagedGridNode restored = null;
        try {
            var service = original.getGrid().getService(WirelessEnergyGridService.class);
            var fe = service.escrow(original.getNode());
            var ae = service.aeBalance(original.getNode());
            fe.credit(89);
            fe.quarantine(17);
            ae.convert(fe, 8, .5);
            ae.beginWithdrawal(13);
            var expectedClaim = save(original).getCompound("proxy").getUUID(CLAIM);
            var snapshot = offThread(() -> save(original));
            h.assertTrue(snapshot.getCompound("proxy").getUUID(CLAIM).equals(expectedClaim),
                    "async serialization lost or replaced the node's claim");
            h.assertTrue(!snapshot.getCompound("proxy").contains(TICK_BUDGET),
                    "async snapshot included a transient migration budget");
            original.destroy();
            restored = node(h, snapshot);
            var restoredService = restored.getGrid().getService(WirelessEnergyGridService.class);
            h.assertTrue(restoredService.escrow(restored.getNode()) == fe
                    && restoredService.aeBalance(restored.getNode()) == ae, "restore changed energy ownership");
            h.assertTrue(fe.available() == 81 && fe.uncertain() == 17
                    && ae.available() == 4 && ae.pendingFeWithdrawal() == 13,
                    "async save/restore lost, duplicated or settled energy");
            ae.completeWithdrawal(fe, 0);
            ae.extract(4);
            fe.debit(81);
            fe.settle(17, 17);
        } finally {
            original.destroy();
            if (restored != null) restored.destroy();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty")
    public static void asyncSavePreservesSameTickSplitMergeEnergyAndVisitBudgets(GameTestHelper h) {
        var managed = node(h, null);
        try {
            var node = managed.getNode();
            var source = new WirelessEnergyGridService();
            source.addNode(node, null);
            long tick = h.getLevel().getServer().getTickCount();
            h.assertTrue(source.reserveAe(tick, 60, 100, 3, false) == 60, "initial reservation failed");
            var migration = new CompoundTag();
            source.saveNodeData(node, migration);
            h.assertTrue(migration.hasUUID(TICK_BUDGET), "main-thread save omitted migration lineage");
            var alias = migration.getUUID(TICK_BUDGET);
            var lineage = WirelessEnergyLedger.get(h.getLevel().getServer()).transferredBudget(tick, alias);
            offThread(() -> {
                for (int i = 0; i < 64; i++) {
                    var snapshot = new CompoundTag();
                    source.saveNodeData(node, snapshot);
                    if (snapshot.contains(TICK_BUDGET)) throw new AssertionError("async save exported a budget");
                }
                return null;
            });
            h.assertTrue(WirelessEnergyLedger.get(h.getLevel().getServer()).transferredBudget(tick, alias) == lineage,
                    "async serialization changed registered migration lineage");
            source.removeNode(node);
            var left = new WirelessEnergyGridService();
            var right = new WirelessEnergyGridService();
            left.addNode(node, migration);
            right.addNode(node, migration);
            h.assertTrue(left.reserve(tick, 100, 100, 3) == 0, "split bypassed topology pause");
            h.assertTrue(left.reserveAe(tick, 15, 100, 3, false) == 15, "left split lost remaining budget");
            h.assertTrue(right.reserveAe(tick, 10, 100, 3, false) == 10, "split budgets were not shared");
            var leftData = new CompoundTag();
            var rightData = new CompoundTag();
            left.saveNodeData(node, leftData);
            right.saveNodeData(node, rightData);
            var merged = new WirelessEnergyGridService();
            merged.addNode(node, leftData);
            merged.addNode(node, rightData);
            h.assertTrue(merged.previewAe(tick, 100, 100, 3, false) == 0, "merge reset the spent visit budget");
            h.assertTrue(merged.previewAe(tick, 100, 100, 10, false) == 15,
                    "split/merge reset or double-counted the spent FE budget");
            h.assertTrue(merged.reserve(tick + 1, 100, 100, 3) == 100, "next tick did not replenish the budget");
        } finally {
            managed.destroy();
        }
        h.succeed();
    }

    private static IManagedGridNode node(GameTestHelper h, CompoundTag data) {
        var managed = new ManagedGridNode(new Object(), (owner, node) -> {});
        managed.setTagName("proxy");
        if (data != null) managed.loadFromNBT(data);
        managed.create(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)));
        return managed;
    }

    private static CompoundTag save(IManagedGridNode managed) {
        var data = new CompoundTag();
        managed.saveToNBT(data);
        return data;
    }

    private static <T> T offThread(Callable<T> action) {
        var task = new FutureTask<>(action);
        var worker = new Thread(task, "AE2LT node serialization regression");
        worker.setDaemon(true);
        worker.start();
        try {
            return task.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Off-thread node serialization failed", e);
        } finally {
            task.cancel(true);
        }
    }
}
