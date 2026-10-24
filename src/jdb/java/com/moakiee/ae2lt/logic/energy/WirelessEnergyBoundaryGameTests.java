package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessEnergyBoundaryGameTests {
    record Fixture(AdvancedWirelessOverloadedControllerBlockEntity controller, ServerPlayer player,
                   int frequency, BlockPos pos, WirelessEnergyTestMachine machine, WirelessEnergyEndpoints endpoints) {
        void bind(GameTestHelper h) {
            h.assertTrue(endpoints.setBound(player, frequency, h.getLevel(), pos, Direction.NORTH, true).equals("connected"), "bind failed");
        }
        void tick(GameTestHelper h) { endpoints.tick(h.getLevel().getServer()); }
        long stored() { return controller.getMainNode().getGrid().getStorageService().getInventory()
                .extract(AppFluxBridge.FE_KEY, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()); }
        void fill(long amount) { controller.getMainNode().getGrid().getStorageService().getInventory()
                .insert(AppFluxBridge.FE_KEY, amount, Actionable.MODULATE, IActionSource.empty()); }
    }
    private static void ready(GameTestHelper h, Consumer<Fixture> action) {
        ready(h, new BlockPos(2, 2, 2), action);
    }
    private static void ready(GameTestHelper h, BlockPos p, Consumer<Fixture> action) {
        h.setBlock(p, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(p.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(p.west(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) h.getBlockEntity(p.west())).getInternalInventory().setItemDirect(0,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell"))));
        h.setBlock(p.south(3), WirelessEnergyTestMachine.BLOCK);
        var machine = (WirelessEnergyTestMachine) h.getBlockEntity(p.south(3));
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "BoundaryOwner"));
        var pos = h.absolutePos(p.south(3)); player.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
        var c = (AdvancedWirelessOverloadedControllerBlockEntity) h.getBlockEntity(p);
        int frequency = WirelessFrequencyManager.get().createFrequency(player, "Boundary", 0xffffff, FrequencySecurityLevel.PRIVATE, "").getId();
        c.setFrequency(frequency);
        var f = new Fixture(c, player, frequency, pos, machine, WirelessEnergyEndpoints.get(h.getLevel().getServer()));
        h.runAfterDelay(35, () -> action.accept(f));
    }

    private static WirelessEnergyTestMachine bindAnother(GameTestHelper h, Fixture f) {
        var pos = f.pos.east();
        h.getLevel().setBlockAndUpdate(pos, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
        var machine = (WirelessEnergyTestMachine) h.getLevel().getBlockEntity(pos);
        f.player.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
        h.assertTrue(f.endpoints.setBound(f.player, f.frequency, h.getLevel(), pos, Direction.NORTH, true).equals("connected"), "second bind failed");
        return machine;
    }

    private static void redistribution(GameTestHelper h, Consumer<WirelessEnergyTestMachine> configurePrefix,
                                       int prefixReceived, WirelessEnergyEndpoints.State prefixState) {
        ready(h, f -> {
            f.bind(h);
            var other = bindAnother(h, f);
            var prefix = new AtomicReference<WirelessEnergyTestMachine>();
            for (var machine : new WirelessEnergyTestMachine[] { f.machine, other }) {
                machine.receiveLimit = 100;
                machine.duringSimulation = () -> {
                    if (prefix.compareAndSet(null, machine)) configurePrefix.accept(machine);
                };
            }
            f.fill(WirelessEnergySettings.reserveFe.get() + 100L);
            f.tick(h);
            h.assertTrue(f.machine.received + other.received == 100 && f.stored() == WirelessEnergySettings.reserveFe.get(),
                    "available FE did not reach the later endpoint or reserve was spent");
            h.assertTrue(prefix.get().received == prefixReceived, "unexpected prefix receipt");
            var view = f.endpoints.inspectTarget(h.getLevel(), prefix.get().getBlockPos(), f.player.getUUID());
            h.assertTrue(view != null && view.state() == prefixState, "unexpected prefix state: " + view);
            h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void rejectingPrefixLeavesSurplusForLaterEndpoint(GameTestHelper h) {
        redistribution(h, machine -> machine.actualLimit = 0, 0, WirelessEnergyEndpoints.State.BLOCKED);
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void partialPrefixLeavesSurplusForLaterEndpoint(GameTestHelper h) {
        redistribution(h, machine -> machine.actualLimit = 30, 30, WirelessEnergyEndpoints.State.ACTIVE);
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void staleCapabilityPrefixLeavesSurplusForLaterEndpoint(GameTestHelper h) {
        redistribution(h, machine -> {
            machine.energy = machine.makeStorage();
            h.getLevel().invalidateCapabilities(machine.getBlockPos());
        }, 0, WirelessEnergyEndpoints.State.BLOCKED);
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void capabilityLookupFailureIsIsolatedAndLaterEndpointReceives(GameTestHelper h) {
        redistribution(h, machine -> machine.duringCapability = () -> {
            throw new IllegalStateException("intentional capability lookup failure");
        }, 0, WirelessEnergyEndpoints.State.FAULTED);
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void throwingPrefixQuarantinesOnlyItsOfferAndLaterEndpointReceives(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var other = bindAnother(h, f);
            var prefix = new AtomicReference<WirelessEnergyTestMachine>();
            for (var machine : new WirelessEnergyTestMachine[] { f.machine, other }) {
                machine.receiveLimit = 100;
                machine.duringSimulation = () -> {
                    if (prefix.compareAndSet(null, machine)) machine.afterReceive = () -> {
                        throw new IllegalStateException("intentional ambiguous batch receipt");
                    };
                };
            }
            f.fill(WirelessEnergySettings.reserveFe.get() + 200L);
            f.tick(h);
            var escrow = f.controller.getMainNode().getGrid().getService(WirelessEnergyGridService.class)
                    .escrow(f.controller.getMainNode().getNode());
            h.assertTrue(f.machine.received == 100 && other.received == 100 && escrow.uncertain() == 100
                    && escrow.available() == 0 && f.stored() == WirelessEnergySettings.reserveFe.get(),
                    "unknown receipt was replayed or blocked a later endpoint");
            var view = f.endpoints.inspectTarget(h.getLevel(), prefix.get().getBlockPos(), f.player.getUUID());
            h.assertTrue(view != null && view.state() == WirelessEnergyEndpoints.State.FAULTED, "throwing prefix was not faulted");
            h.succeed();
        });
    }

    private static final class BatchStorage implements MEStorage {
        long fe;
        int simulations, withdrawals, refunds;
        boolean throwRefund;
        BatchStorage(long fe) { this.fe = fe; }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!key.equals(AppFluxBridge.FE_KEY)) return 0;
            long taken = Math.min(fe, amount);
            if (mode == Actionable.MODULATE) { withdrawals++; fe -= taken; }
            else simulations++;
            return taken;
        }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!key.equals(AppFluxBridge.FE_KEY)) return 0;
            if (mode == Actionable.MODULATE) {
                refunds++;
                if (throwRefund) throw new IllegalStateException("intentional ambiguous batch refund");
                fe += amount;
            }
            return amount;
        }
        @Override public void getAvailableStacks(KeyCounter out) { out.add(AppFluxBridge.FE_KEY, fe); }
        @Override public Component getDescription() { return Component.literal("Batch test storage"); }
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void batchUsesOneMeSimulationWithdrawalAndRefund(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var other = bindAnother(h, f);
            f.machine.receiveLimit = other.receiveLimit = 100;
            f.machine.actualLimit = other.actualLimit = 30;
            long initial = WirelessEnergySettings.reserveFe.get() + 200L;
            var storage = new BatchStorage(initial);
            var service = f.controller.getMainNode().getGrid().getStorageService();
            IStorageProvider provider = mounts -> mounts.mount(storage, 100);
            service.addGlobalStorageProvider(provider);
            try {
                f.tick(h);
                h.assertTrue(f.machine.received == 30 && other.received == 30 && storage.fe == initial - 60,
                        "batch receipts or refund broke conservation");
                h.assertTrue(storage.simulations == 1 && storage.withdrawals == 1 && storage.refunds == 1,
                        "batch repeated ME IO: " + storage.simulations + "/" + storage.withdrawals + "/" + storage.refunds);
                h.succeed();
            } finally { service.removeGlobalStorageProvider(provider); }
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 160)
    public static void refundFailureIsQuarantinedAndOtherFrequenciesStillDispatch(GameTestHelper h) {
        ready(h, a -> ready(h, new BlockPos(10, 2, 2), b -> {
            var fixtures = new Fixture[] { a, b };
            var storages = new BatchStorage[2];
            var providers = new IStorageProvider[2];
            h.assertTrue(a.controller.getMainNode().getGrid() != b.controller.getMainNode().getGrid(), "refund fixtures share a grid");
            for (int i = 0; i < fixtures.length; i++) {
                var f = fixtures[i];
                f.bind(h); f.machine.receiveLimit = 100; f.machine.actualLimit = 0;
                var storage = new BatchStorage(WirelessEnergySettings.reserveFe.get() + 100L);
                storage.throwRefund = true; storages[i] = storage;
                providers[i] = mounts -> mounts.mount(storage, 100);
                f.controller.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(providers[i]);
            }
            try {
                a.tick(h);
                for (int i = 0; i < fixtures.length; i++) {
                    var f = fixtures[i];
                    var escrow = f.controller.getMainNode().getGrid().getService(WirelessEnergyGridService.class)
                            .escrow(f.controller.getMainNode().getNode());
                    h.assertTrue(storages[i].refunds == 1 && f.machine.receiveCalls == 1 && escrow.uncertain() == 100
                            && escrow.available() == 0 && storages[i].fe == WirelessEnergySettings.reserveFe.get(),
                            "refund failure escaped or unresolved FE was replayed");
                    h.assertTrue(f.endpoints.inspect(f.frequency, f.player.getUUID()).getFirst().state()
                            == WirelessEnergyEndpoints.State.FAULTED, "refund failure was not faulted");
                }
                h.succeed();
            } finally {
                for (int i = 0; i < fixtures.length; i++) fixtures[i].controller.getMainNode().getGrid()
                        .getStorageService().removeGlobalStorageProvider(providers[i]);
            }
        }));
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void repeatedBindingAndUnbindingNeverProbesOrReusesOldGeneration(GameTestHelper h) {
        ready(h, f -> {
            UUID previous = null;
            for (int i = 0; i < 200; i++) {
                f.bind(h); f.bind(h);
                var rows = f.endpoints.inspect(f.frequency, f.player.getUUID());
                h.assertTrue(rows.size() == 1 && !rows.getFirst().id().equals(previous), "duplicate or reused generation");
                if (previous != null) f.endpoints.disconnect(f.player, f.frequency, previous);
                h.assertTrue(f.endpoints.isBound(h.getLevel(), f.pos, f.frequency), "stale ID removed new endpoint");
                previous = rows.getFirst().id();
                f.endpoints.setBound(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH, false);
            }
            h.assertTrue(f.machine.simulations == 0 && f.machine.received == 0, "binding emitted probe FE"); h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void reserveFloorKeepsLastFeUntilOneExtraFeArrives(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(WirelessEnergySettings.reserveFe.get()); f.tick(h);
            h.assertTrue(f.machine.received == 0 && f.stored() == WirelessEnergySettings.reserveFe.get(), "reserve spent");
            f.fill(1);
            h.runAfterDelay(40, () -> {
                h.assertTrue(f.machine.received == 1 && f.stored() == WirelessEnergySettings.reserveFe.get(), "one-FE surplus lost or reserve violated: received=" + f.machine.received + ", stored=" + f.stored()); h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void recursiveEndpointTickFromReceiverCannotDuplicateDelivery(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.afterReceive = () -> f.tick(h); f.tick(h);
            h.assertTrue(f.machine.receiveCalls == 1 && before - f.stored() == f.machine.received, "recursive send duplicated or lost FE"); h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void unbindInsideSimulationCancelsActualDelivery(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.duringSimulation = () -> f.endpoints.setBound(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH, false);
            f.tick(h);
            h.assertTrue(f.machine.received == 0 && f.stored() == before, "removed endpoint received FE"); h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void capabilitySwapInsideSimulationRefundsAndNewCapabilityRecovers(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.duringSimulation = () -> {
                f.machine.duringSimulation = () -> {};
                f.machine.energy = f.machine.makeStorage(); h.getLevel().invalidateCapabilities(f.pos);
            };
            f.tick(h);
            h.assertTrue(f.machine.received == 0 && f.stored() == before, "stale capability received FE or refund lost");
            h.runAfterDelay(2, () -> { h.assertTrue(f.machine.received > 0, "new capability never resumed"); h.succeed(); });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_telemetry_expiry", timeoutTicks = 1400)
    public static void resumedEndpointRecreatesExpiredMeterWithoutOldReceipts(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(WirelessEnergySettings.reserveFe.get() + 50L); f.tick(h);
            h.assertTrue(f.machine.received == 50, "initial telemetry receipt failed");
            var node = f.controller.getMainNode().getNode();
            var endpoint = f.endpoints.inspect(f.frequency, f.player.getUUID()).getFirst();
            var key = new com.moakiee.ae2lt.logic.management.PowerTelemetry.Key(
                    com.moakiee.ae2lt.logic.management.PowerTelemetry.Kind.WIRELESS,
                    new com.moakiee.ae2lt.logic.management.PowerTelemetry.Target(
                            h.getLevel().dimension().location().toString(), f.pos, Direction.NORTH.ordinal()), endpoint.id());
            var old = com.moakiee.ae2lt.logic.management.PowerTelemetry.meters(node).get(key);
            h.assertTrue(old != null, "initial meter missing");
            h.runAfterDelay(1202, () -> {
                h.assertTrue(!com.moakiee.ae2lt.logic.management.PowerTelemetry.meters(node).containsKey(key), "idle meter did not expire");
                f.machine.energy = f.machine.makeStorage();
                h.getLevel().invalidateCapabilities(f.pos);
                f.fill(20); f.tick(h);
                h.runAfterDelay(2, () -> {
                    var current = com.moakiee.ae2lt.logic.management.PowerTelemetry.meters(node).get(key);
                    h.assertTrue(current != null && !current.id.equals(old.id), "resumed endpoint reused an expired meter");
                    h.assertTrue(f.machine.received == 70 && f.stored() == WirelessEnergySettings.reserveFe.get(), "resumed scratch lost or duplicated FE");
                    h.assertTrue(Math.abs(current.rates(h.getLevel().getServer().getTickCount())[4] * 1200 - 20) < .01,
                            "expired receipts leaked into the new history");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void throwingReceiverIsQuarantinedUntilExplicitRebind(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000);
            f.machine.afterReceive = () -> { throw new IllegalStateException("intentional ambiguous test receipt"); };
            f.tick(h);
            var row = f.endpoints.inspect(f.frequency, f.player.getUUID()).getFirst();
            var escrow = f.controller.getMainNode().getGrid().getService(WirelessEnergyGridService.class).escrow(f.controller.getMainNode().getNode());
            h.assertTrue(row.state() == WirelessEnergyEndpoints.State.FAULTED && escrow.uncertain() == f.machine.received, "unknown delivery not isolated");
            int received = f.machine.received; f.machine.afterReceive = () -> {};
            h.runAfterDelay(3, () -> {
                h.assertTrue(f.machine.received == received, "faulted endpoint retried");
                f.endpoints.disconnect(f.player, f.frequency, row.id()); f.bind(h);
                h.runAfterDelay(2, () -> {
                    h.assertTrue(f.machine.received > received && escrow.uncertain() == received, "rebind replayed uncertain balance or failed to recover"); h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void fullMachineStartsReceivingAgainAfterPlayerUsesEnergy(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(200000); f.machine.energy.prime(100000); f.tick(h);
            h.assertTrue(f.machine.received == 0, "full machine received power");
            f.machine.energy.prime(99000);
            h.runAfterDelay(40, () -> {
                h.assertTrue(f.machine.received == 1000 && f.machine.energy.getEnergyStored() == 100000, "demand did not resume or overfilled: received=" + f.machine.received + ", stored=" + f.machine.energy.getEnergyStored()); h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void controllerFrequencyChangeDuringSimulationCannotPowerOldFrequency(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.duringSimulation = () -> f.controller.setFrequency(-1); f.tick(h);
            h.assertTrue(f.machine.received == 0 && f.stored() == before, "frequency change sent FE to stale endpoint");
            f.machine.duringSimulation = () -> {}; f.controller.setFrequency(f.frequency);
            h.runAfterDelay(5, () -> { h.assertTrue(f.machine.received > 0, "reconnecting controller failed to resume"); h.succeed(); });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 120)
    public static void disablingOutputDoesNotProbeAndReenablingResumes(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); int old = WirelessEnergySettings.networkFe.get();
            try {
                WirelessEnergySettings.networkFe.set(0); f.tick(h);
                h.assertTrue(f.machine.simulations == 0 && f.machine.received == 0, "disabled output touched target");
            } finally { WirelessEnergySettings.networkFe.set(old); }
            h.runAfterDelay(40, () -> { h.assertTrue(f.machine.received > 0, "output did not resume after enabling"); h.succeed(); });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary", timeoutTicks = 160)
    public static void overworldSourcePowersNetherMachineAndOwnerDisconnectsFromOverworld(GameTestHelper h) {
        ready(h, f -> {
            var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(nether != null, "missing Nether fixture");
            var pos = new BlockPos(6144, 64, 6144);
            nether.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            nether.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            nether.setBlock(pos, WirelessEnergyTestMachine.BLOCK.defaultBlockState(), 18);
            var target = (WirelessEnergyTestMachine) nether.getBlockEntity(pos);
            var owner = FakePlayerFactory.get(nether, f.player.getGameProfile());
            owner.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
            h.assertTrue(f.endpoints.setBound(owner, f.frequency, nether, pos, Direction.NORTH, true).equals("connected"), "cross-dimension binding failed");
            var row = f.endpoints.inspect(f.frequency, f.player.getUUID()).getFirst();
            h.assertTrue(row.location().dimension().equals(nether.dimension()), "endpoint saved the player's original dimension");
            f.fill(50000);
            h.runAfterDelay(40, () -> {
                h.assertTrue(target.received > 0 && f.stored() >= WirelessEnergySettings.reserveFe.get(), "loaded Nether machine not powered or source reserve violated");
                h.assertTrue(f.endpoints.disconnect(f.player, f.frequency, row.id()).equals("disconnected"), "owner could not disconnect from source dimension");
                int received = target.received;
                h.runAfterDelay(3, () -> {
                    h.assertTrue(target.received == received, "cross-dimension disconnect did not stop output");
                    nether.removeBlock(pos, false); nether.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false); h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "large_cluster_limits", timeoutTicks = 200)
    public static void oversizedClusterIsAtomicAndExactLimitHonorsEndpointCapacity(GameTestHelper h) {
        ready(h, f -> {
            var level = h.getLevel(); var origin = new BlockPos(8192, 64, 8192);
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                level.setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, true);
                level.getChunk((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z);
            }
            for (int i = 0; i < 8193; i++) level.setBlock(origin.offset(i % 32, i / 1024, i / 32 % 32), WirelessEnergyTestMachine.BLOCK.defaultBlockState(), 18);
            f.player.setPos(origin.getX(), origin.getY(), origin.getZ() - 1);
            long start = System.nanoTime();
            var rejected = f.endpoints.toggleConnected(f.player, f.frequency, level, origin, Direction.NORTH);
            long rejectedNs = System.nanoTime() - start;
            h.assertTrue(rejected.error().equals("cluster_limit") && rejected.changed() == 0
                    && f.endpoints.inspect(f.frequency, f.player.getUUID()).isEmpty(), "oversized cluster partially applied: " + rejected);
            level.removeBlock(origin.above(8), false);
            start = System.nanoTime();
            var accepted = f.endpoints.toggleConnected(f.player, f.frequency, level, origin, Direction.NORTH);
            long acceptedNs = System.nanoTime() - start;
            h.assertTrue(accepted.error().isEmpty() && accepted.changed() == WirelessEnergySettings.endpointLimit.get()
                    && accepted.skippedReasons().getOrDefault("limit", 0) == 8192 - accepted.changed(), "exact cluster limit or endpoint cap incorrect: " + accepted);
            h.assertTrue(f.endpoints.inspect(f.frequency, f.player.getUUID()).size() == accepted.changed(), "batch count differs from registry");
            for (int i = 0; i < 8192; i++) level.removeBlock(origin.offset(i % 32, i / 1024, i / 32 % 32), false);
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) level.setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, false);
            System.out.println("FE_CLUSTER_LIMIT_PASS rejected8193Ms=" + rejectedNs / 1e6 + " accepted8192Ms=" + acceptedNs / 1e6);
            h.succeed();
        });
    }
}
