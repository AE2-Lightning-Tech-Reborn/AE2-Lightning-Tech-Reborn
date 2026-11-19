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
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessEnergyBoundaryGameTests {
    private static final String SLOW_DISPATCH_BATCH = "energy_boundary_slowsimulationdefersworkbutbothreceiverseventuallygetpower";
    private static WirelessEnergyEndpoints previousSlowTestEndpoints;

    @BeforeBatch(batch = SLOW_DISPATCH_BATCH)
    public static void isolateSlowDispatch(ServerLevel level) {
        var server = level.getServer();
        previousSlowTestEndpoints = WirelessEnergyEndpoints.get(server);
        server.overworld().getDataStorage().set("ae2lt_wireless_energy_endpoints", new WirelessEnergyEndpoints());
        WirelessEnergyEndpoints.stop();
    }

    @AfterBatch(batch = SLOW_DISPATCH_BATCH)
    public static void restoreSlowDispatch(ServerLevel level) {
        level.getServer().overworld().getDataStorage().set("ae2lt_wireless_energy_endpoints", previousSlowTestEndpoints);
        WirelessEnergyEndpoints.stop();
        previousSlowTestEndpoints = null;
    }

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

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_mekupgrade", timeoutTicks = 120)
    public static void mekanismTierInstallerPreservesWirelessFe(GameTestHelper h) {
        ready(h, f -> {
            var level = h.getLevel();
            var machine = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:enrichment_chamber"));
            var factory = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_enriching_factory"));
            var installer = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:basic_tier_installer"));
            h.assertTrue(machine != net.minecraft.world.level.block.Blocks.AIR
                    && factory != net.minecraft.world.level.block.Blocks.AIR
                    && installer != net.minecraft.world.item.Items.AIR, "Mekanism upgrade fixture missing");
            level.setBlockAndUpdate(f.pos, machine.defaultBlockState());
            // Raw block placement does not apply the item's default side-configuration components.
            var tile = (mekanism.common.tile.prefab.TileEntityConfigurableMachine) level.getBlockEntity(f.pos);
            tile.getConfig().getConfig(mekanism.common.lib.transmitter.TransmissionType.ENERGY).setDataType(
                    mekanism.common.tile.component.config.DataType.INPUT, mekanism.api.RelativeSide.FRONT);
            level.invalidateCapabilities(f.pos);
            h.runAfterDelay(2, () -> {
                var face = f.endpoints.findInputFace(level, f.pos, Direction.NORTH);
                h.assertTrue(face != null, "Mekanism machine lacks FE input");
                h.assertTrue(f.endpoints.setBound(f.player, f.frequency, level, f.pos, face, true).equals("connected"), "Mekanism bind failed");
                f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(installer));
                var hit = new net.minecraft.world.phys.BlockHitResult(f.pos.getCenter(), Direction.NORTH, f.pos, false);
                installer.useOn(new net.minecraft.world.item.context.UseOnContext(f.player,
                        net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                h.assertTrue(level.getBlockState(f.pos).is(factory), "native Mekanism installer did not upgrade the machine");
                f.fill(100000);
                h.runAfterDelay(5, () -> {
                    h.assertTrue(f.endpoints.isBound(level, f.pos, f.frequency), "Mekanism factory upgrade disconnected FE");
                    var view = f.endpoints.inspectTarget(level, f.pos, f.player.getUUID());
                    h.assertTrue(view.face() == face && view.totalDelivered() > 0
                            && view.block().equals("mekanism:basic_enriching_factory"), "upgraded factory did not inherit and receive FE");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_air", timeoutTicks = 120)
    public static void airDisconnectsAtEndOfTickWithoutController(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            f.controller.setFrequency(-1);
            h.getLevel().removeBlock(f.pos, false);
            h.runAfterDelay(2, () -> {
                h.assertTrue(!f.endpoints.isBound(h.getLevel(), f.pos, f.frequency), "air retained a binding without an active controller");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_stone", timeoutTicks = 120)
    public static void nonFeReplacementDisconnectsAfterGrace(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            h.getLevel().setBlockAndUpdate(f.pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            h.runAfterDelay(5, () -> h.assertTrue(f.endpoints.isBound(h.getLevel(), f.pos, f.frequency), "missing capability skipped grace"));
            h.runAfterDelay(24, () -> {
                h.assertTrue(!f.endpoints.isBound(h.getLevel(), f.pos, f.frequency), "non-FE replacement retained a binding");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_missingfe", timeoutTicks = 120)
    public static void repeatedInvalidationsDoNotExtendMissingFeDeadline(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            f.machine.inputReady = false;
            h.getLevel().invalidateCapabilities(f.pos);
            for (int delay : new int[] { 5, 10, 15 }) {
                h.runAfterDelay(delay, () -> h.getLevel().invalidateCapabilities(f.pos));
            }
            h.runAfterDelay(24, () -> {
                h.assertTrue(!f.endpoints.isBound(h.getLevel(), f.pos, f.frequency), "invalidation extended a missing-FE deadline");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_recoverfe", timeoutTicks = 120)
    public static void delayedCapabilityRecoversWithoutProbeOrStaleDeadline(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var id = f.endpoints.inspectTarget(h.getLevel(), f.pos, f.player.getUUID()).id();
            f.machine.inputReady = false;
            h.getLevel().invalidateCapabilities(f.pos);
            h.runAfterDelay(10, () -> {
                f.machine.inputReady = true;
                h.getLevel().invalidateCapabilities(f.pos);
                f.fill(100000);
            });
            h.runAfterDelay(26, () -> {
                var view = f.endpoints.inspectTarget(h.getLevel(), f.pos, f.player.getUUID());
                h.assertTrue(view != null && view.id().equals(id) && f.machine.received > 0,
                        "delayed capability lost its binding or stale deadline removed it");
                h.assertTrue(WirelessEnergyAdmissions.get(h.getLevel().getServer()).inspect(
                        h.getLevel(), f.pos, f.player.getUUID()).offered() == 0, "recovery repeated automatic admission");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_notaccepting", timeoutTicks = 120)
    public static void presentButDisabledInputRetainsBinding(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            f.machine.accepting = false;
            h.getLevel().invalidateCapabilities(f.pos);
            f.fill(100000);
            h.runAfterDelay(26, () -> {
                h.assertTrue(f.endpoints.isBound(h.getLevel(), f.pos, f.frequency)
                        && f.machine.received == 0, "disabled input disconnected or received FE");
                f.machine.accepting = true;
                h.getLevel().invalidateCapabilities(f.pos);
                h.runAfterDelay(25, () -> {
                    h.assertTrue(f.machine.received > 0, "enabled input did not resume");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_listener", timeoutTicks = 120)
    public static void idleBindingDoesNotPollCapabilityProvider(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var queries = new java.util.concurrent.atomic.AtomicInteger();
            f.machine.duringCapability = queries::incrementAndGet;
            h.runAfterDelay(3, () -> {
                int before = queries.get();
                h.runAfterDelay(25, () -> {
                    h.assertTrue(queries.get() == before, "unchanged endpoint polled its capability provider");
                    h.getLevel().invalidateCapabilities(f.pos);
                    h.runAfterDelay(2, () -> {
                        h.assertTrue(queries.get() == before + 1, "capability event did not trigger exactly one fresh lookup");
                        h.succeed();
                    });
                });
            });
        });
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

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_rejectingprefixleavessurplusforlaterendpoint", timeoutTicks = 120)
    public static void rejectingPrefixLeavesSurplusForLaterEndpoint(GameTestHelper h) {
        redistribution(h, machine -> machine.actualLimit = 0, 0, WirelessEnergyEndpoints.State.BLOCKED);
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_partialprefixleavessurplusforlaterendpoint", timeoutTicks = 120)
    public static void partialPrefixLeavesSurplusForLaterEndpoint(GameTestHelper h) {
        redistribution(h, machine -> machine.actualLimit = 30, 30, WirelessEnergyEndpoints.State.ACTIVE);
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_stalecapabilityprefixleavessurplusforlaterendpoint", timeoutTicks = 120)
    public static void staleCapabilityPrefixLeavesSurplusForLaterEndpoint(GameTestHelper h) {
        redistribution(h, machine -> {
            machine.energy = machine.makeStorage();
            h.getLevel().invalidateCapabilities(machine.getBlockPos());
        }, 0, WirelessEnergyEndpoints.State.BLOCKED);
    }

    /**
     * A capability provider that throws during an endpoint's visit must fault
     * only that endpoint and leave the batch budget to later endpoints. With
     * the per-endpoint invalidation-backed capability cache the provider chain
     * runs at visit resolution, so the throw is armed before the tick.
     */
    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_capabilitylookupfailureisisolatedandlaterendpointreceives", timeoutTicks = 120)
    public static void capabilityLookupFailureIsIsolatedAndLaterEndpointReceives(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var other = bindAnother(h, f);
            f.machine.receiveLimit = 100;
            other.receiveLimit = 100;
            f.machine.duringCapability = () -> {
                throw new IllegalStateException("intentional capability lookup failure");
            };
            f.fill(WirelessEnergySettings.reserveFe.get() + 100L);
            f.tick(h);
            var view = f.endpoints.inspectTarget(h.getLevel(), f.machine.getBlockPos(), f.player.getUUID());
            h.assertTrue(view != null && view.state() == WirelessEnergyEndpoints.State.FAULTED,
                    "unexpected prefix state: " + view);
            h.runAfterDelay(5, () -> {
                h.assertTrue(f.machine.received == 0 && other.received == 100
                        && f.stored() == WirelessEnergySettings.reserveFe.get(),
                        "capability lookup failure consumed FE or starved the later endpoint");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_throwingprefixquarantinesonlyitsofferandlaterendpointreceives", timeoutTicks = 120)
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
            var view = f.endpoints.inspectTarget(h.getLevel(), prefix.get().getBlockPos(), f.player.getUUID());
            h.assertTrue(view != null && view.state() == WirelessEnergyEndpoints.State.FAULTED, "throwing prefix was not faulted");
            h.runAfterDelay(5, () -> {
                h.assertTrue(f.machine.received == 100 && other.received == 100 && escrow.uncertain() == 100
                        && escrow.available() == 0 && f.stored() == WirelessEnergySettings.reserveFe.get(),
                        "unknown receipt was replayed or blocked a later endpoint");
                h.succeed();
            });
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

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_batchusesonemesimulationwithdrawalandrefund", timeoutTicks = 120)
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

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_refundfailureisquarantinedandotherfrequenciesstilldispatch", timeoutTicks = 160)
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
            a.tick(h);
            awaitRefundFaults(h, fixtures, storages, providers, 0);
        }));
    }

    private static void awaitRefundFaults(GameTestHelper h, Fixture[] fixtures, BatchStorage[] storages,
                                          IStorageProvider[] providers, int ticks) {
        h.runAfterDelay(1, () -> {
            boolean retry = false;
            try {
                boolean complete = true;
                for (var f : fixtures) {
                    complete &= f.endpoints.inspect(f.frequency, f.player.getUUID()).getFirst().state()
                            == WirelessEnergyEndpoints.State.FAULTED;
                    h.assertTrue(f.machine.receiveCalls <= 1, "quarantined delivery was replayed");
                }
                if (!complete && ticks < 20) {
                    awaitRefundFaults(h, fixtures, storages, providers, ticks + 1);
                    retry = true;
                    return;
                }
                h.assertTrue(complete, "refund failure was not faulted within the work budget");
                for (int i = 0; i < fixtures.length; i++) {
                    var f = fixtures[i];
                    var escrow = f.controller.getMainNode().getGrid().getService(WirelessEnergyGridService.class)
                            .escrow(f.controller.getMainNode().getNode());
                    h.assertTrue(storages[i].refunds == 1 && f.machine.receiveCalls == 1 && escrow.uncertain() == 100
                            && escrow.available() == 0 && storages[i].fe == WirelessEnergySettings.reserveFe.get(),
                            "refund failure escaped or unresolved FE was replayed");
                }
                h.succeed();
            } finally {
                if (!retry) for (int i = 0; i < fixtures.length; i++) fixtures[i].controller.getMainNode().getGrid()
                        .getStorageService().removeGlobalStorageProvider(providers[i]);
            }
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_repeatedbindingandunbindingneverprobesorreusesoldgeneration", timeoutTicks = 120)
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

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_reservefloorkeepslastfeuntiloneextrafearrives", timeoutTicks = 120)
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

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_recursiveendpointtickfromreceivercannotduplicatedelivery", timeoutTicks = 120)
    public static void recursiveEndpointTickFromReceiverCannotDuplicateDelivery(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.afterReceive = () -> f.tick(h); f.tick(h);
            h.assertTrue(f.machine.receiveCalls == 1 && before - f.stored() == f.machine.received, "recursive send duplicated or lost FE"); h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_unbindinsidesimulationcancelsactualdelivery", timeoutTicks = 120)
    public static void unbindInsideSimulationCancelsActualDelivery(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.duringSimulation = () -> f.endpoints.setBound(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH, false);
            f.tick(h);
            h.assertTrue(f.machine.received == 0 && f.stored() == before, "removed endpoint received FE"); h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_rebindduringreceipt", timeoutTicks = 120)
    public static void rebindDuringEarlierReceiptInvalidatesThePreparedGeneration(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var other = bindAnother(h, f);
            f.machine.receiveLimit = other.receiveLimit = 100;
            var oldId = f.endpoints.inspectTarget(h.getLevel(), other.getBlockPos(), f.player.getUUID()).id();
            f.machine.afterReceive = () -> {
                f.machine.afterReceive = () -> {};
                h.assertTrue(f.endpoints.setBound(f.player, f.frequency, h.getLevel(), other.getBlockPos(),
                        Direction.NORTH, false).equals("disconnected"), "unbind failed");
                h.assertTrue(f.endpoints.setBound(f.player, f.frequency, h.getLevel(), other.getBlockPos(),
                        Direction.NORTH, true).equals("connected"), "rebind failed");
            };
            f.fill(100000);
            long before = f.stored();
            f.tick(h);
            var rebound = f.endpoints.inspectTarget(h.getLevel(), other.getBlockPos(), f.player.getUUID());
            h.assertTrue(rebound != null && !rebound.id().equals(oldId), "generation did not change");
            h.assertTrue(other.received == 0 && before - f.stored() == f.machine.received,
                    "prepared old generation received FE or refund was lost");
            h.runAfterDelay(5, () -> {
                h.assertTrue(other.received > 0 && before - f.stored() == f.machine.received + other.received,
                        "new generation failed to recover or FE was duplicated");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_coordinate_preparedreplacement", timeoutTicks = 120)
    public static void replacementDuringEarlierReceiptRejectsPreparedOldMachine(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var other = bindAnother(h, f);
            f.machine.receiveLimit = other.receiveLimit = 100;
            var replacement = new AtomicReference<WirelessEnergyTestMachine>();
            f.machine.afterReceive = () -> {
                f.machine.afterReceive = () -> {};
                h.getLevel().removeBlockEntity(other.getBlockPos());
                var next = new WirelessEnergyTestMachine(other.getBlockPos(), other.getBlockState());
                replacement.set(next);
                h.getLevel().getChunkAt(other.getBlockPos()).addAndRegisterBlockEntity(next);
            };
            f.fill(100000);
            long before = f.stored();
            f.tick(h);
            h.assertTrue(replacement.get() != null && other.received == 0 && replacement.get().received == 0
                    && before - f.stored() == f.machine.received, "prepared replacement received stale FE or lost its refund");
            h.runAfterDelay(5, () -> {
                h.assertTrue(f.endpoints.isBound(h.getLevel(), other.getBlockPos(), f.frequency)
                        && replacement.get().received > 0, "replacement did not resume through the coordinate binding");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_batchscratchreuse", timeoutTicks = 120)
    public static void distinctDemandsSurviveMultipleBatchesAndTicks(GameTestHelper h) {
        ready(h, f -> {
            var machines = new java.util.ArrayList<WirelessEnergyTestMachine>();
            machines.add(f.machine);
            f.machine.receiveLimit = 1;
            f.machine.drain = true;
            f.bind(h);
            for (int i = 1; i < 65; i++) {
                var pos = f.pos.offset(i % 8, 0, 2 + i / 8);
                h.getLevel().setBlockAndUpdate(pos, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
                var machine = (WirelessEnergyTestMachine) h.getLevel().getBlockEntity(pos);
                machine.receiveLimit = i + 1;
                machine.drain = true;
                f.player.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
                h.assertTrue(f.endpoints.setBound(f.player, f.frequency, h.getLevel(), pos,
                        Direction.NORTH, true).equals("connected"), "bind failed: " + i);
                machines.add(machine);
            }
            f.fill(1000000);
            long before = f.stored();
            h.runAfterDelay(12, () -> {
                long total = 0;
                for (int i = 0; i < machines.size(); i++) {
                    var machine = machines.get(i);
                    h.assertTrue(machine.receiveCalls > 0 && machine.received == (i + 1) * machine.receiveCalls,
                            "batch reused another target's demand or receipt: " + i);
                    total += machine.received;
                }
                h.assertTrue(before - f.stored() == total, "multi-batch FE conservation failed");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_capabilityswapinsidesimulationrefundsandnewcapabilityrecovers", timeoutTicks = 120)
    public static void capabilitySwapInsideSimulationRefundsAndNewCapabilityRecovers(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.duringSimulation = () -> {
                f.machine.duringSimulation = () -> {};
                f.machine.energy = f.machine.makeStorage(); h.getLevel().invalidateCapabilities(f.pos);
            };
            f.tick(h);
            h.assertTrue(f.machine.received == 0 && f.stored() == before, "stale capability received FE or refund lost");
            h.runAfterDelay(25, () -> { h.assertTrue(f.machine.received > 0, "new capability never resumed"); h.succeed(); });
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
                h.runAfterDelay(25, () -> {
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

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_throwingreceiverisquarantineduntilexplicitrebind", timeoutTicks = 120)
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
                f.fill(100000);
                f.endpoints.disconnect(f.player, f.frequency, row.id()); f.bind(h);
                h.runAfterDelay(2, () -> {
                    h.assertTrue(f.machine.received > received && escrow.uncertain() == received, "rebind replayed uncertain balance or failed to recover"); h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_fullmachinestartsreceivingagainafterplayerusesenergy", timeoutTicks = 120)
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

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_controllerfrequencychangeduringsimulationcannotpoweroldfrequency", timeoutTicks = 120)
    public static void controllerFrequencyChangeDuringSimulationCannotPowerOldFrequency(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); long before = f.stored();
            f.machine.duringSimulation = () -> f.controller.setFrequency(-1); f.tick(h);
            h.assertTrue(f.machine.received == 0 && f.stored() == before, "frequency change sent FE to stale endpoint");
            f.machine.duringSimulation = () -> {}; f.controller.setFrequency(f.frequency);
            h.runAfterDelay(5, () -> { h.assertTrue(f.machine.received > 0, "reconnecting controller failed to resume"); h.succeed(); });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_fullintreceiverdemandisdeliveredwithoutlegacyratecaps", timeoutTicks = 120)
    public static void fullIntReceiverDemandIsDeliveredWithoutLegacyRateCaps(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            // A long-sized ME batch must not truncate the FE capability's positive int maximum.
            f.machine.instantTrash = true;
            f.machine.receiveLimit = Integer.MAX_VALUE;
            f.machine.actualLimit = Integer.MAX_VALUE;
            var node = f.controller.getMainNode().getNode();
            var escrow = node.getGrid().getService(WirelessEnergyGridService.class).escrow(node);
            escrow.credit(Integer.MAX_VALUE);
            f.tick(h);
            h.assertTrue(f.machine.received == Integer.MAX_VALUE && f.machine.receiveCalls == 1,
                    "wireless output retained a rate cap or repeated the target callback");
            h.assertTrue(escrow.available() == 0 && escrow.uncertain() == 0, "large transfer lost or duplicated FE");
            h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "normal_energy_hosts", timeoutTicks = 120)
    public static void ordinarySupplyAndWirelessInterfaceStillUseNormalBatches(GameTestHelper h) {
        var cpos = new BlockPos(2, 2, 2);
        h.setBlock(cpos, AEBlocks.CONTROLLER.block());
        h.setBlock(cpos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(cpos.west(), ModBlocks.OVERLOADED_POWER_SUPPLY.get());
        h.setBlock(cpos.east(), ModBlocks.OVERLOADED_INTERFACE.get());
        h.setBlock(cpos.south(4), WirelessEnergyTestMachine.BLOCK);
        h.setBlock(cpos.south(4).east(2), WirelessEnergyTestMachine.BLOCK);
        var supply = (com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity) h.getBlockEntity(cpos.west());
        var iface = (com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity) h.getBlockEntity(cpos.east());
        var first = (WirelessEnergyTestMachine) h.getBlockEntity(cpos.south(4));
        var second = (WirelessEnergyTestMachine) h.getBlockEntity(cpos.south(4).east(2));
        iface.getInterfaceLogic().getUpgrades().setItemDirect(0,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:induction_card"))));
        iface.setInterfaceMode(com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
        h.assertTrue(supply.addOrUpdateConnection(h.getLevel().dimension(), first.getBlockPos(), Direction.NORTH), "supply bind");
        h.assertTrue(iface.addOrUpdateConnection(new com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.WirelessConnection(
                h.getLevel().dimension(), second.getBlockPos(), Direction.NORTH)), "interface bind");
        h.runAfterDelay(35, () -> {
            for (var node : new appeng.api.networking.IGridNode[] { supply.getMainNode().getNode(), iface.getMainNode().getNode() }) {
                h.assertTrue(node != null && node.isActive(), "normal host inactive");
                node.getGrid().getService(WirelessEnergyGridService.class).escrow(node).credit(100000);
            }
            h.runAfterDelay(30, () -> {
                h.assertTrue(first.received == 100000 && second.received == 100000,
                        "shared NORMAL engine regressed supply/interface delivery: " + first.received + "/" + second.received);
                h.assertTrue(supply.getMode() == com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity.PowerMode.NORMAL,
                        "fixture unexpectedly entered OVERLOAD");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = SLOW_DISPATCH_BATCH, timeoutTicks = 140)
    public static void slowSimulationDefersWorkButBothReceiversEventuallyGetPower(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h);
            var other = bindAnother(h, f);
            for (var machine : new WirelessEnergyTestMachine[] { f.machine, other }) {
                machine.duringSimulation = () -> {
                    long started = System.nanoTime();
                    while (System.nanoTime() - started < 2_000_000) Thread.onSpinWait();
                };
            }
            var node = f.controller.getMainNode().getNode();
            node.getGrid().getService(WirelessEnergyGridService.class).escrow(node)
                    .credit(200000L + WirelessEnergySettings.reserveFe.get());
            double old = WirelessEnergySettings.tickBudgetMillis.get();
            try {
                WirelessEnergySettings.tickBudgetMillis.set(.1);
                f.tick(h);
                h.assertTrue(f.machine.receiveCalls + other.receiveCalls == 1,
                        "an expired budget starved all delivery or kept calling receivers");
            } finally { WirelessEnergySettings.tickBudgetMillis.set(old); }
            h.runAfterDelay(10, () -> {
                h.assertTrue(f.machine.received == 100000 && other.received == 100000,
                        "deferred receiver starved or FE was lost");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_fullreceiverbacksoffanddemandresumeswithintwentyticks", timeoutTicks = 300)
    public static void fullReceiverBacksOffAndDemandResumesWithinTwentyTicks(GameTestHelper h) {
        ready(h, f -> {
            f.machine.energy.prime(100000);
            f.bind(h); f.fill(1000000);
            h.runAfterDelay(190, () -> {
                int simulations = f.machine.simulations;
                f.machine.energy.prime(0);
                h.runAfterDelay(25, () -> {
                    h.assertTrue(f.machine.received == 100000, "sleeping receiver failed to recover");
                    h.assertTrue(simulations < 35, "full receiver was polled every tick");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_fallbackbudgetdoesnotthrottlewirelessfe", timeoutTicks = 120)
    public static void fallbackBudgetDoesNotThrottleWirelessFe(GameTestHelper h) {
        ready(h, f -> {
            f.bind(h); f.fill(100000); int old = WirelessEnergySettings.networkFe.get();
            try {
                WirelessEnergySettings.networkFe.set(0); f.tick(h);
                h.assertTrue(f.machine.received > 4096, "fallback budget still throttles wireless output");
            } finally { WirelessEnergySettings.networkFe.set(old); }
            h.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "energy_boundary_overworldsourcepowersnethermachineandownerdisconnectsfromoverworld", timeoutTicks = 160)
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

    @GameTest(template = "wireless_io_empty", batch = "large_cluster_limits", timeoutTicks = 1600)
    public static void oversizedClusterIsAtomicAndExactLimitHonorsEndpointCapacity(GameTestHelper h) {
        ready(h, f -> {
            var level = h.getLevel(); var origin = new BlockPos(8192, 64, 8192);
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                level.setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, true);
                level.getChunk((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z);
            }
            for (int i = 0; i < 8193; i++) level.setBlock(origin.offset(i % 32, i / 1024, i / 32 % 32), WirelessEnergyTestMachine.BLOCK.defaultBlockState(), 18);
            f.player.setPos(origin.getX(), origin.getY(), origin.getZ() - 1);
            var server = level.getServer();
            var storage = server.overworld().getDataStorage();
            var admissions = WirelessEnergyAdmissions.get(server);
            // Exhausting this history must not disable automatic admission in later test batches.
            storage.set("ae2lt_wireless_energy_admissions", new WirelessEnergyAdmissions());
            WirelessEnergyAdmissions.stop();
            bulk(h, f, origin, rejected -> {
                h.assertTrue(rejected.error().equals("cluster_limit") && rejected.changed() == 0
                        && f.endpoints.inspect(f.frequency, f.player.getUUID()).isEmpty(), "oversized cluster partially applied: " + rejected);
                level.removeBlock(origin.above(8), false);
                bulk(h, f, origin, accepted -> {
                    h.assertTrue(accepted.error().isEmpty() && accepted.changed() > 256
                            && accepted.skippedReasons().getOrDefault("limit", 0) == 8192 - accepted.changed(), "exact cluster limit or endpoint cap incorrect: " + accepted);
                    h.assertTrue(f.endpoints.inspect(f.frequency, f.player.getUUID()).size() == accepted.changed(), "batch count differs from registry");
                    for (int i = 0; i < 8193; i++) level.removeBlock(origin.offset(i % 32, i / 1024, i / 32 % 32), false);
                    for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) level.setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, false);
                    storage.set("ae2lt_wireless_energy_admissions", admissions);
                    WirelessEnergyAdmissions.stop();
                    h.succeed();
                });
            });
        });
    }
    private static void bulk(GameTestHelper h, Fixture f, BlockPos origin,
                             java.util.function.Consumer<WirelessEnergyEndpoints.BulkResult> completed) {
        var result = new java.util.concurrent.atomic.AtomicReference<WirelessEnergyEndpoints.BulkResult>();
        var queued = f.endpoints.toggleConnected(f.player, f.frequency, h.getLevel(), origin, Direction.NORTH, result::set);
        h.assertTrue(queued.error().equals("bulk_queued"), "bulk request rejected: " + queued);
        awaitBulk(h, result, completed);
    }
    private static void awaitBulk(GameTestHelper h,
            java.util.concurrent.atomic.AtomicReference<WirelessEnergyEndpoints.BulkResult> result,
            java.util.function.Consumer<WirelessEnergyEndpoints.BulkResult> completed) {
        h.runAfterDelay(1, () -> {
            if (result.get() == null) awaitBulk(h, result, completed);
            else completed.accept(result.get());
        });
    }
}
