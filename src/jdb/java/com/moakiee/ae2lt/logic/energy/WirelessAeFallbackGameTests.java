package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.service.EnergyService;
import appeng.me.storage.NetworkStorage;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessAeFallbackGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private record Fixture(AdvancedWirelessOverloadedControllerBlockEntity controller) {
        IGrid grid() { return controller.getMainNode().getGrid(); }
        EnergyService energy() { return (EnergyService) grid().getEnergyService(); }
        WirelessEnergyGridService service() { return grid().getService(WirelessEnergyGridService.class); }
        EnergyEscrow fe() { return service().escrow(controller.getMainNode().getNode()); }
        AeEnergyBalance ae() { return service().aeBalance(controller.getMainNode().getNode()); }
        void emptyAe() { energy().extractProviderPower(Double.MAX_VALUE, Actionable.MODULATE); }
        double request(double amount, Actionable mode) { return energy().extractAEPower(amount, mode, PowerMultiplier.ONE); }
        long storedFe() { return grid().getStorageService().getInventory().extract(AppFluxBridge.FE_KEY, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE); }
        void fillFe(long amount) { grid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, amount, Actionable.MODULATE, SOURCE); }
        Store mount(long amount) {
            var s = new Store(amount);
            ((NetworkStorage) grid().getStorageService().getInventory()).mount(Integer.MAX_VALUE, s);
            return s;
        }
    }
    private static Fixture fixture(GameTestHelper h) {
        return fixture(h, POS);
    }
    private static Fixture fixture(GameTestHelper h, BlockPos pos) {
        WirelessEnergySettings.runningNetworkFeFallback.set(true);
        h.setBlock(pos, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(pos.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) h.getBlockEntity(pos.west());
        drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("appflux:fe_1k_cell"))));
        var c = (AdvancedWirelessOverloadedControllerBlockEntity) h.getBlockEntity(pos);
        c.setInternalCurrentPower(1000000);
        return new Fixture(c);
    }
    private static void ready(GameTestHelper h, Fixture f, Runnable action) {
        h.runAfterDelay(60, () -> {
            h.assertTrue(f.controller.getMainNode().isActive() && f.energy().isNetworkPowered(), "fixture did not reach a running grid");
            action.run();
        });
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 1e-8, message + ": " + actual + " != " + expected);
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void normalAePrecedesRealFluxCellShortfall(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            f.fillFe(50000); long initial = f.storedFe();
            close(h, f.request(10, Actionable.MODULATE), 10, "normal AE missing");
            h.assertTrue(f.storedFe() == initial, "normal AE request spent FE");
            f.emptyAe(); double rate = PowerUnits.FE.convertTo(PowerUnits.AE, 1);
            close(h, f.request(123, Actionable.MODULATE), 123, "same-call FE shortfall missing");
            close(h, (initial - f.storedFe()) * rate, 123 + f.ae().available(), "real cell conversion not conserved");
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void repeatedSimulationDoesNotMutateLedgerInventoryOrBudget(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            f.fillFe(50000); f.emptyAe(); long beforeFe = f.storedFe();
            var ledger = WirelessEnergyLedger.get(h.getLevel().getServer());
            var before = ledger.save(new CompoundTag());
            for (int i = 0; i < 100; i++) close(h, f.request(.125, Actionable.SIMULATE), .125, "simulation unavailable");
            h.assertTrue(before.equals(ledger.save(new CompoundTag())) && f.storedFe() == beforeFe,
                    "simulation changed ownership");
            close(h, f.request(.125, Actionable.MODULATE), .125, "simulation spent real budget"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void fractionalRequestsAndMultiplierChargeOnlyOnce(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            f.fillFe(10000); f.emptyAe(); long before = f.storedFe();
            double old = PowerMultiplier.CONFIG.multiplier;
            try {
                PowerMultiplier.CONFIG.multiplier = 2;
                double returned = 0;
                for (int i = 0; i < 7; i++) returned += f.energy().extractAEPower(.03, Actionable.MODULATE, PowerMultiplier.CONFIG);
                close(h, returned, .21, "fractional request lost");
                close(h, (before - f.storedFe()) * PowerUnits.FE.convertTo(PowerUnits.AE, 1),
                        returned * 2 + f.ae().available(), "multiplier applied twice or remainder lost");
            } finally { PowerMultiplier.CONFIG.multiplier = old; }
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void maintenanceStaysPoweredUntilRealExhaustionThenCannotColdStart(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(100000); f.emptyAe();
            f.controller.getMainNode().setIdlePowerUsage(1000);
            f.energy().onServerEndTick();
            h.assertTrue(f.energy().isNetworkPowered() && store.fe < 100000, "maintenance dropped power before supplement");
            store.fe = 0; f.emptyAe(); f.energy().onServerEndTick();
            h.assertTrue(!f.energy().isNetworkPowered(), "real exhaustion did not turn network off");
            store.fe = 100000; int calls = store.extractCalls;
            close(h, f.request(1000, Actionable.MODULATE), 0, "cold grid restarted through FE");
            h.assertTrue(store.fe == 100000 && store.extractCalls == calls, "cold grid extracted FE"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void multipleControllersAndOutputShareOneBudget(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(POS.east(), ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        ready(h, f, () -> {
            var second = (AdvancedWirelessOverloadedControllerBlockEntity) h.getBlockEntity(POS.east());
            h.assertTrue(second.getMainNode().getGrid() == f.grid(), "controllers are not on one grid");
            var store = f.mount(100000); f.emptyAe(); int old = WirelessEnergySettings.networkFe.get();
            try {
                WirelessEnergySettings.networkFe.set(64);
                long reserved = f.service().maintenanceReserveFe();
                double total = f.request(1000, Actionable.MODULATE) + f.request(1000, Actionable.MODULATE);
                close(h, total, (64 - reserved) * PowerUnits.FE.convertTo(PowerUnits.AE, 1), "controllers duplicated budget");
                h.assertTrue(store.fe == 100000 - (64 - reserved), "FE extraction exceeded shared budget");
                h.assertTrue(f.service().reserve(h.getLevel().getServer().getTickCount(), 10, 64, 32) == 0, "external output obtained a second budget");
                f.energy().onServerEndTick();
                h.assertTrue(f.energy().isNetworkPowered() && store.fe >= 100000 - 64, "maintenance lost its shared allowance");
            } finally { WirelessEnergySettings.networkFe.set(old); }
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void storageCallbackCannotReenterFallback(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(10000); f.emptyAe(); double[] nested = {-1};
            store.onExtract = () -> nested[0] = f.request(20, Actionable.MODULATE);
            close(h, f.request(10, Actionable.MODULATE), 10, "outer fallback failed");
            close(h, nested[0], 0, "nested fallback spent FE");
            h.assertTrue(store.extractCalls == 1, "recursive extraction happened"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void storageCallbackCannotRecursivelyBorrowFromAnotherGrid(GameTestHelper h) {
        var f = fixture(h); var other = fixture(h, POS.south(4));
        ready(h, f, () -> {
            h.assertTrue(f.grid() != other.grid() && other.energy().isNetworkPowered(), "expected two independent running grids");
            var own = f.mount(1000); var foreign = other.mount(1000);
            f.emptyAe(); other.emptyAe(); double[] nested = {-1};
            own.onExtract = () -> nested[0] = other.request(10, Actionable.MODULATE);
            close(h, f.request(10, Actionable.MODULATE), 10, "outer grid payment failed");
            close(h, nested[0], 0, "storage callback recursively entered another grid fallback");
            h.assertTrue(foreign.fe == 1000 && foreign.extractCalls == 0, "nested call borrowed foreign FE"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void conversionRateChangeDuringWithdrawalRetainsOwnedFe(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(1000); f.emptyAe(); var fe = f.fe(); var ae = f.ae();
            double old = PowerUnits.FE.conversionRatio;
            try {
                store.onExtract = () -> PowerUnits.FE.conversionRatio = old * 2;
                close(h, f.request(10, Actionable.MODULATE), 0, "rate changed during an in-flight conversion");
                h.assertTrue(fe.available() == 1000 - store.fe && fe.available() > 0 && ae.available() == 0,
                        "rate change lost or revalued in-flight FE");
            } finally { PowerUnits.FE.conversionRatio = old; }
            store.onExtract = () -> {};
            close(h, f.request(10, Actionable.MODULATE), 10, "retained FE could not resume at stable rate");
            h.assertTrue(store.extractCalls == 1, "known retained FE extracted twice"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void partialReceiptCannotReportSimulatedPower(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(10000); store.limit = 3; f.emptyAe();
            close(h, f.request(100, Actionable.MODULATE), 3 * PowerUnits.FE.convertTo(PowerUnits.AE, 1), "returned simulated rather than real FE");
            h.assertTrue(store.fe == 9997 && f.ae().pendingFeWithdrawal() == 0, "partial receipt ownership wrong"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void enormousRequestStaysWithinSharedFeLimit(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(Long.MAX_VALUE); f.emptyAe();
            long limit = WirelessEnergySettings.networkFe.get();
            long reserved = f.service().maintenanceReserveFe();
            double paid = f.request(Double.MAX_VALUE, Actionable.MODULATE);
            close(h, paid, (limit - reserved) * PowerUnits.FE.convertTo(PowerUnits.AE, 1), "large request bypassed FE limit");
            h.assertTrue(store.fe == Long.MAX_VALUE - (limit - reserved) && f.ae().pendingFeWithdrawal() == 0,
                    "large request overflowed ownership or withdrawal"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void refusedRefundRetainsIntegerAndFractionalOwnership(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(0); store.reject = true; f.emptyAe(); f.fe().credit(100);
            // Remove real cell capacity, so every available refund route refuses.
            ((DriveBlockEntity) h.getBlockEntity(POS.west())).getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            double paid = f.request(.1, Actionable.MODULATE);
            close(h, paid, .1, "refused refund prevented current payment");
            double rate = PowerUnits.FE.convertTo(PowerUnits.AE, 1);
            close(h, f.fe().available() * rate + f.ae().available() + paid, 100 * rate, "refused refund lost energy");
            h.assertTrue(f.fe().available() == 99 && store.fe == 0, "unexpected refund destination");
            int refunds = store.insertCalls;
            close(h, f.request(.1, Actionable.MODULATE), .1, "known fractional AE unavailable");
            h.assertTrue(store.insertCalls == refunds, "zero-grant fractional payment retried storage IO");
            close(h, f.fe().available() * rate + f.ae().available() + paid + .1, 100 * rate, "second fractional payment not conserved");
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void throwingWithdrawalIsJournaledAndNotRetried(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(1000); f.emptyAe();
            store.onExtract = () -> { throw new IllegalStateException("test extraction receipt unavailable"); };
            close(h, f.request(10, Actionable.MODULATE), 0, "exception manufactured AE");
            long unknown = f.ae().pendingFeWithdrawal();
            h.assertTrue(unknown > 0 && f.fe().available() == 0, "unknown extraction was not isolated");
            close(h, f.request(10, Actionable.MODULATE), 0, "unresolved operation replayed");
            h.assertTrue(store.extractCalls == 1, "unknown receipt retried");
            var ledger = WirelessEnergyLedger.get(h.getLevel().getServer());
            var restored = WirelessEnergyLedger.load(ledger.save(new CompoundTag()));
            h.assertTrue(restored.inspect(null, true).stream().anyMatch(b -> b.unresolvedFeWithdrawal() == unknown), "unknown extraction lost on save/load");
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void throwingRefundCannotDebitUnreturnedAe(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(0); f.emptyAe(); f.fe().credit(100);
            ((DriveBlockEntity) h.getBlockEntity(POS.west())).getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            store.onInsert = () -> { throw new IllegalStateException("test refund receipt unavailable"); };
            close(h, f.request(.1, Actionable.MODULATE), 0, "throwing refund returned an uncommitted payment");
            double rate = PowerUnits.FE.convertTo(PowerUnits.AE, 1);
            close(h, f.ae().available(), rate, "converted AE was debited before callback completed");
            h.assertTrue(f.fe().available() == 0 && f.fe().uncertain() == 99 && store.fe == 99,
                    "unacknowledged refund was not isolated");
            store.onInsert = () -> {};
            close(h, f.request(.1, Actionable.MODULATE), .1, "known AE could not resume");
            close(h, store.fe * rate + f.ae().available() + .1, 100 * rate, "refund/payment not conserved");
            h.assertTrue(store.fe == 99 && store.extractCalls == 0 && f.fe().uncertain() == 99,
                    "uncertain refund was replayed"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void removalDuringExtractionKeepsFeWithOriginalClaim(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(1000); f.emptyAe(); var fe = f.fe(); var ae = f.ae();
            store.onExtract = () -> h.setBlock(POS, Blocks.AIR);
            close(h, f.energy().extractAEPower(10, Actionable.MODULATE, PowerMultiplier.ONE), 0, "removed source paid into stale grid");
            h.assertTrue(fe.available() == 1000 - store.fe && fe.available() > 0 && ae.available() == 0
                    && ae.pendingFeWithdrawal() == 0, "withdrawal lost its original node owner"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void physicalSplitMergePreservesFractionalClaimIdentities(GameTestHelper h) {
        WirelessEnergySettings.runningNetworkFeFallback.set(true);
        var bridge = POS.south(); var other = POS.south(2);
        h.setBlock(POS, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(bridge, AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(other, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.runAfterDelay(40, () -> {
            var left = ((AdvancedWirelessOverloadedControllerBlockEntity) h.getBlockEntity(POS)).getMainNode().getNode();
            var right = ((AdvancedWirelessOverloadedControllerBlockEntity) h.getBlockEntity(other)).getMainNode().getNode();
            var s = left.getGrid().getService(WirelessEnergyGridService.class);
            var a = s.aeBalance(left); var b = s.aeBalance(right);
            s.escrow(left).credit(1); a.convert(s.escrow(left), 1, .5); a.extract(.1);
            s.escrow(right).credit(1); b.convert(s.escrow(right), 1, .5); b.extract(.2);
            h.setBlock(bridge, Blocks.AIR);
            h.runAfterDelay(8, () -> {
                h.assertTrue(left.getGrid() != right.getGrid(), "grid did not split");
                h.assertTrue(left.getGrid().getService(WirelessEnergyGridService.class).aeBalance(left) == a
                        && right.getGrid().getService(WirelessEnergyGridService.class).aeBalance(right) == b, "split copied AE claim");
                h.setBlock(bridge, AEBlocks.CREATIVE_ENERGY_CELL.block());
                h.runAfterDelay(8, () -> {
                    h.assertTrue(left.getGrid() == right.getGrid(), "grid did not merge");
                    var merged = left.getGrid().getService(WirelessEnergyGridService.class);
                    h.assertTrue(merged.aeBalance(left) == a && merged.aeBalance(right) == b, "merge reassigned AE balances");
                    close(h, a.available(), .4, "left fractional AE changed"); close(h, b.available(), .3, "right fractional AE changed"); h.succeed();
                });
            });
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void removingEmptyNodeDoesNotTurnOffFeSustainedGrid(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(1000000); f.controller.getMainNode().setIdlePowerUsage(1000); f.emptyAe();
            var grid = f.grid(); h.setBlock(POS.west(), Blocks.AIR);
            h.assertTrue(f.grid() == grid && f.service().topologyPaused(h.getLevel().getServer().getTickCount()), "expected same-grid node removal");
            f.energy().onServerEndTick();
            h.assertTrue(f.energy().isNetworkPowered() && store.fe < 1000000, "node removal interrupted maintenance"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void ordinaryRequestsLeaveOneMaintenanceVisit(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(1000000); f.controller.getMainNode().setIdlePowerUsage(1000); f.emptyAe();
            int limit = WirelessEnergySettings.networkTargets.get(); double one = PowerUnits.FE.convertTo(PowerUnits.AE, 1);
            for (int i = 0; i < limit - 1; i++) close(h, f.request(one, Actionable.MODULATE), one, "ordinary allowance lost");
            close(h, f.request(one, Actionable.MODULATE), 0, "ordinary call took maintenance visit");
            f.energy().onServerEndTick();
            h.assertTrue(f.energy().isNetworkPowered() && store.extractCalls == limit, "maintenance visit was not retained");
            h.assertTrue(1000000 - store.fe <= WirelessEnergySettings.networkFe.get(), "priority bypassed hard budget"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void ordinaryRequestsLeaveMaintenanceFe(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(1000000); f.controller.getMainNode().setIdlePowerUsage(1000); f.emptyAe();
            double paid = f.request(Double.MAX_VALUE, Actionable.MODULATE);
            h.assertTrue(paid > 0, "ordinary request received nothing");
            f.energy().onServerEndTick();
            h.assertTrue(f.energy().isNetworkPowered() && 1000000 - store.fe <= WirelessEnergySettings.networkFe.get(),
                    "ordinary demand consumed maintenance FE or maintenance exceeded hard budget"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void physicalSplitMergeCannotRefreshSameTickBudget(GameTestHelper h) {
        var f = fixture(h); var bridge = POS.south(); var other = POS.south(2);
        h.setBlock(bridge, AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(other, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        ready(h, f, () -> {
            var left = f.controller.getMainNode().getNode();
            var right = ((AdvancedWirelessOverloadedControllerBlockEntity) h.getBlockEntity(other)).getMainNode().getNode();
            long tick = h.getLevel().getServer().getTickCount();
            close(h, f.service().reserveAe(tick, 60, 100, 10, true), 60, "initial budget unavailable");
            h.setBlock(bridge, Blocks.AIR);
            h.assertTrue(left.getGrid() != right.getGrid(), "grid did not split synchronously");
            var a = left.getGrid().getService(WirelessEnergyGridService.class);
            var b = right.getGrid().getService(WirelessEnergyGridService.class);
            close(h, a.reserveAe(tick, 20, 100, 10, true), 20, "remaining split budget missing");
            close(h, b.reserveAe(tick, 100, 100, 10, true), 20, "split copied budget");
            // A newly placed BE initializes its node next tick. Connect the existing
            // real nodes directly to exercise an actual merge before the tick ends.
            appeng.api.networking.GridHelper.createConnection(left, right);
            h.assertTrue(left.getGrid() == right.getGrid(), "grid did not merge synchronously");
            close(h, left.getGrid().getService(WirelessEnergyGridService.class).reserveAe(tick, 1, 100, 10, true),
                    0, "merge refreshed exhausted budget"); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void disablingExperimentNeverSpendsFeOrConvertedAe(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            f.fillFe(50000); f.emptyAe(); f.fe().credit(1); f.ae().convert(f.fe(), 1, .5);
            long before = f.storedFe(); boolean old = WirelessEnergySettings.runningNetworkFeFallback.get();
            try {
                WirelessEnergySettings.runningNetworkFeFallback.set(false);
                close(h, f.request(.125, Actionable.SIMULATE), 0, "disabled simulation advertised FE");
                close(h, f.request(.125, Actionable.MODULATE), 0, "disabled experiment paid AE");
                close(h, f.ae().available(), .5, "disabled experiment spent converted AE");
                h.assertTrue(f.storedFe() == before, "disabled experiment withdrew FE");
            } finally { WirelessEnergySettings.runningNetworkFeFallback.set(old); }
            close(h, f.request(.125, Actionable.MODULATE), .125, "reenabled experiment did not resume"); h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void zeroNewFeBudgetStillPaysAlreadyConvertedAeWithoutStorageCalls(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(100000); f.emptyAe(); f.fe().credit(1); f.ae().convert(f.fe(), 1, .5);
            int old = WirelessEnergySettings.networkFe.get();
            try {
                WirelessEnergySettings.networkFe.set(0);
                close(h, f.request(.125, Actionable.SIMULATE), .125, "simulation ignored owned converted AE");
                close(h, f.request(.125, Actionable.MODULATE), .125, "owned converted AE incorrectly needs a new FE budget");
                close(h, f.ae().available(), .375, "converted payment not charged exactly");
                h.assertTrue(store.extractCalls == 0 && store.insertCalls == 0 && store.fe == 100000, "zero grant performed storage IO");
            } finally { WirelessEnergySettings.networkFe.set(old); }
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void unresolvedJournalBlocksBothSimulationAndRealPayment(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(100000); f.emptyAe(); f.fe().credit(7); f.ae().beginWithdrawal(17);
            for (int i = 0; i < 100; i++) {
                close(h, f.request(.125, Actionable.SIMULATE), 0, "unresolved journal advertised power");
                close(h, f.request(.125, Actionable.MODULATE), 0, "unresolved journal replayed withdrawal");
            }
            h.assertTrue(store.extractCalls == 0 && store.insertCalls == 0 && f.fe().available() == 7
                    && f.ae().pendingFeWithdrawal() == 17, "unresolved journal or owned FE mutated"); h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void ordinaryAeRestorationStopsFeUseImmediately(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(100000); f.emptyAe();
            close(h, f.request(10, Actionable.MODULATE), 10, "FE did not initially supplement");
            int calls = store.extractCalls; long remaining = store.fe;
            f.controller.setInternalCurrentPower(100000);
            close(h, f.request(10, Actionable.MODULATE), 10, "ordinary AE restoration unavailable");
            h.assertTrue(store.extractCalls == calls && store.fe == remaining, "FE still withdrawn after ordinary AE returned"); h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 180)
    public static void topologyChangeDuringRefundRetainsConvertedButUnreturnedAe(GameTestHelper h) {
        var f = fixture(h);
        ready(h, f, () -> {
            var store = f.mount(0); f.emptyAe(); f.fe().credit(100);
            double rate = PowerUnits.FE.convertTo(PowerUnits.AE, 1);
            store.onInsert = () -> h.setBlock(POS.west(), Blocks.AIR);
            close(h, f.request(rate, Actionable.MODULATE), 0, "topology-changing refund paid stale AE");
            close(h, f.ae().available(), rate, "refund callback erased converted but unreturned AE");
            h.assertTrue(store.fe == 99 && f.fe().available() == 0 && f.fe().uncertain() == 0, "refund duplicated or lost FE ownership");
            store.onInsert = () -> {};
            close(h, f.request(rate, Actionable.MODULATE), rate, "stable grid could not reuse already converted AE");
            h.assertTrue(store.fe == 99 && store.extractCalls == 0, "retained AE payment withdrew FE again"); h.succeed();
        });
    }

    private static final class Store implements MEStorage {
        long fe, limit = Long.MAX_VALUE; int extractCalls, insertCalls; boolean reject;
        Runnable onExtract = () -> {};
        Runnable onInsert = () -> {};
        Store(long fe) { this.fe = fe; }
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!key.equals(AppFluxBridge.FE_KEY)) return 0;
            long n = Math.min(fe, Math.min(amount, limit));
            if (mode == Actionable.MODULATE) { fe -= n; extractCalls++; onExtract.run(); }
            return n;
        }
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE && key.equals(AppFluxBridge.FE_KEY)) insertCalls++;
            if (reject || !key.equals(AppFluxBridge.FE_KEY)) return 0;
            if (mode == Actionable.MODULATE) { fe += amount; onInsert.run(); }
            return amount;
        }
        public Component getDescription() { return Component.literal("FE fallback adversarial storage"); }
    }
}
