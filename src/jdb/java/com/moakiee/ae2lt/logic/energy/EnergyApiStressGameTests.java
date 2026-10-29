package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.SplittableRandom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import appeng.api.networking.IManagedGridNode;

/** Fault injection into the real production buffer and Applied Flux send path. */
@GameTestHolder("ae2lt_stress")
@PrefixGameTestTemplate(false)
public final class EnergyApiStressGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void overloadedProviderRejectsNonDroppableIngressOffline(GameTestHelper h) {
        checkResourceIngress(h, com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void extendedProviderRejectsNonDroppableIngressOffline(GameTestHelper h) {
        checkResourceIngress(h, com.moakiee.ae2lt.registry.ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get());
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void pigmeeProviderRejectsNonDroppableIngressOffline(GameTestHelper h) {
        checkResourceIngress(h, com.moakiee.ae2lt.registry.ModBlocks.PIGMEE_PATTERN_PROVIDER.get());
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void interfaceRejectsNonDroppableIngressOffline(GameTestHelper h) {
        checkResourceIngress(h, com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_INTERFACE.get());
    }

    private static void checkResourceIngress(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, block);
        var host = (appeng.blockentity.grid.AENetworkBlockEntity) h.getBlockEntity(pos);
        var inv = resourceInventory(host);
        assertOfflineIngress(h, host, inv);
        // A node can have a grid while still being offline (no power/channel).
        // This must not be reduced to a getGrid() != null admission check.
        h.setBlock(pos.east(), appeng.core.definitions.AEBlocks.ENERGY_CELL.block());
        h.runAfterDelay(20, () -> {
            h.assertTrue(host.getMainNode().getGrid() != null && !host.getMainNode().isActive(),
                    "Fixture must have a connected but inactive grid");
            assertOfflineIngress(h, host, inv);
            h.setBlock(pos.east(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(com.moakiee.ae2lt.logic.ResourceIngressPolicy.isOnline(host, host.getMainNode()),
                    "Fixture must have an active connected server grid: " + block);
            var water = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
            if (host instanceof com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity) {
                // Interfaces proxy into the network, unlike provider return buffers.
                // Give the real network a real AE2 fluid cell before testing admission.
                var cell = appeng.api.storage.StorageCells.getCellInventory(
                        appeng.core.definitions.AEItems.FLUID_CELL_1K.stack(), null);
                h.assertTrue(cell != null, "Fixture must resolve the 1.20.1 fluid cell API");
                host.getMainNode().getGrid().getStorageService()
                        .addGlobalStorageProvider(mounts -> mounts.mount(cell, 0));
            }
            h.assertTrue(inv.isAllowed(water), "Online host must keep fluid admission");
            h.assertTrue(inv.insert(1, water, 1000, Actionable.SIMULATE) == 1000,
                    "Online simulation must admit fluid");
            h.assertTrue(inv.insert(1, water, 1000, Actionable.MODULATE) == 1000,
                    "Online insertion must admit fluid");
            if (host instanceof com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity provider) {
                h.assertTrue(provider.getLogic().getReturnInv().insert(2, water, 1, Actionable.SIMULATE) == 1,
                        "Online GUI return page must admit fluid");
            }
            host.setRemoved();
            assertOfflineIngress(h, host, inv);
            host.getMainNode().destroy(); // keep stale inventory handles; no destructive world drops
            assertOfflineIngress(h, host, inv);
            var saved = host.saveWithoutMetadata();
            var restored = (appeng.blockentity.grid.AENetworkBlockEntity) host.getType().create(
                    host.getBlockPos(), host.getBlockState());
            try {
                restored.load(saved);
                var preserved = new appeng.api.stacks.KeyCounter();
                ((MEStorage) resourceInventory(restored)).getAvailableStacks(preserved);
                if (!(host instanceof com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity)) {
                    h.assertTrue(preserved.get(water) == 1000, "Offline load must preserve already owned fluid");
                }
                assertOfflineIngress(h, restored, resourceInventory(restored));
            } finally {
                restored.setRemoved();
                h.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
                h.setBlock(pos.east(), net.minecraft.world.level.block.Blocks.AIR);
            }
            h.succeed();
        });
    }

    private static appeng.api.behaviors.GenericInternalInventory resourceInventory(
            appeng.blockentity.grid.AENetworkBlockEntity host) {
        if (host instanceof com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity pigmee) {
            return pigmee.getReturnInventory();
        }
        if (host instanceof com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity provider) {
            return ((com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic) provider.getLogic()).getInternalReturnInv();
        }
        return ((com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity) host)
                .getInterfaceLogic().getStorage();
    }

    private static void assertOfflineIngress(GameTestHelper h, appeng.blockentity.grid.AENetworkBlockEntity host,
                                              appeng.api.behaviors.GenericInternalInventory inv) {
        h.assertTrue(!com.moakiee.ae2lt.logic.ResourceIngressPolicy.isOnline(host, host.getMainNode()),
                "Expected offline host");
        var keys = new AEKey[] {AppFluxBridge.FE_KEY, com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE,
                appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER)};
        for (var key : keys) {
            h.assertTrue(!inv.isAllowed(key), "Offline facade advertises unsafe key: " + key);
            for (var mode : Actionable.values()) {
                h.assertTrue(inv.insert(0, key, 100, mode) == 0, "Offline slot insertion accepted " + key);
                if (inv instanceof MEStorage storage) {
                    h.assertTrue(storage.insert(key, 100, mode, SOURCE) == 0,
                            "Offline MEStorage insertion bypassed guard: " + key);
                }
                if (host instanceof com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity provider) {
                    var logic = (com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic) provider.getLogic();
                    h.assertTrue(logic.getReturnInv().insert(0, key, 100, mode) == 0,
                            "Offline GUI page bypassed guard");
                    var facade = new com.moakiee.ae2lt.logic.provider.InsertOnlyReturnInvWrapper(
                            (com.moakiee.ae2lt.logic.provider.UnlimitedReturnInventory) logic.getInternalReturnInv(), logic);
                    h.assertTrue(!facade.isAllowed(key) && facade.insert(0, key, 100, mode) == 0,
                            "Offline capability facade bypassed guard");
                    var before = inv.getStack(0);
                    facade.setStack(0, new appeng.api.stacks.GenericStack(key, 100));
                    h.assertTrue(java.util.Objects.equals(before, inv.getStack(0)),
                            "Offline capability setStack bypassed guard");
                }
            }
        }
        if (host.getLevel() != null && !host.isRemoved()) {
            var fluid = host.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER,
                    Direction.UP).orElse(null);
            h.assertTrue(fluid != null, "Placed host must expose its actual Forge fluid adapter");
            for (var mode : net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.values()) {
                h.assertTrue(fluid.fill(new net.minecraftforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.WATER, 100), mode) == 0,
                        "Offline Forge fluid adapter bypassed guard");
            }
            var generic = host.getCapability(appeng.capabilities.Capabilities.GENERIC_INTERNAL_INV,
                    Direction.UP).orElse(null);
            h.assertTrue(generic != null, "Placed host must expose its actual AE2 inventory capability");
            for (var key : keys) {
                h.assertTrue(!generic.isAllowed(key), "Offline generic capability advertises unsafe key");
                for (var mode : Actionable.values()) {
                    h.assertTrue(generic.insert(0, key, 100, mode) == 0,
                            "Offline generic capability bypassed guard");
                }
            }
        }
        var item = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE);
        h.assertTrue(inv.isAllowed(item), "Offline rule must not blanket-ban ordinary items");
        if (!(host instanceof com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity)) {
            h.assertTrue(inv.insert(0, item, 1, Actionable.MODULATE) == 1,
                    "Offline return inventory should still accept recoverable items");
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void offlineEnergyRejectsNewCreditsButRetainsConfirmedRefund(GameTestHelper h) {
        for (boolean repeated : new boolean[] {false, true}) {
            boolean[] online = {true};
            var cell = new Store(1000);
            var network = new Store(0);
            var buffer = new BufferedMEStorage(network, () -> cell, null, null, () -> online[0]);
            var target = new TargetAccess() {
                public long simulateReceive(long n) { return n; }
                public long receive(long n) { online[0] = false; cell.capacity = 0; network.capacity = 0; return 0; }
            };
            if (repeated) AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 100, 4);
            else AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 100);
            long owned = buffer.getBufferedEnergy();
            for (var mode : Actionable.values()) {
                h.assertTrue(buffer.insert(AppFluxBridge.FE_KEY, 100, mode, SOURCE) == 0,
                        "Offline buffer accepted new FE");
                h.assertTrue(buffer.insert(com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE, 100, mode, SOURCE) == 0,
                        "Offline buffer delegated unsafe key insertion");
            }
            h.assertTrue(owned == 1000 && buffer.getBufferedEnergy() == 1000,
                    "Offline rejection discarded owned refund");
            h.assertTrue(AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 100) == 0,
                    "Offline host started another outgoing transfer");
            online[0] = true;
            cell.capacity = 1000;
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(cell.energy == 1000 && buffer.getPendingEnergy() == 0,
                    "Reconnect failed to recover refund");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void successfulReceiptDisconnectStopsRemainingDirectCalls(GameTestHelper h) {
        for (int multiplier : new int[] {1, 2, 3}) {
            for (long accepted : new long[] {50, 100}) {
                boolean[] online = {true};
                int[] calls = {0};
                var cell = new Store(1000);
                var network = new Store(0);
                var buffer = new BufferedMEStorage(network, () -> cell, null, null, () -> online[0]);
                buffer.setCostMultiplier(multiplier);
                var target = new TargetAccess() {
                    public long simulateReceive(long n) { return n; }
                    public long receive(long n) {
                        calls[0]++;
                        online[0] = false;
                        cell.capacity = 0;
                        network.capacity = 0;
                        return accepted;
                    }
                };
                long sent = AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 100, 4);
                h.assertTrue(calls[0] == 1 && sent == accepted,
                        "Direct batch continued after disconnect: calls=" + calls[0] + " sent=" + sent);
                h.assertTrue(buffer.getBufferedEnergy() + sent * multiplier == 1000,
                        "Disconnect lost or duplicated remaining direct budget");
                online[0] = true;
                cell.capacity = 1000;
                buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
                h.assertTrue(cell.energy + network.energy + sent * multiplier == 1000
                                && buffer.getPendingEnergy() == 0,
                        "Reconnect did not recover the remaining direct budget");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void simulationDisconnectMustNotWithdrawOrDeliver(GameTestHelper h) {
        for (boolean repeated : new boolean[] {false, true}) {
            boolean[] online = {true};
            int[] calls = {0};
            var cell = new Store(0);
            var network = new Store(1000);
            var buffer = new BufferedMEStorage(network, () -> cell, null, null, () -> online[0]);
            var target = new TargetAccess() {
                public long simulateReceive(long n) { online[0] = false; return n; }
                public long receive(long n) { calls[0]++; return n; }
            };
            long sent = repeated ? AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 100, 4)
                    : AppFluxBridge.sendToTarget(target, new BufferedStorageService(null, buffer), SOURCE, 100);
            h.assertTrue(sent == 0 && calls[0] == 0 && network.energy == 1000
                            && cell.energy == 0 && buffer.getPendingEnergy() == 0,
                    "Simulation disconnect still withdrew or delivered FE: repeated=" + repeated);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void withdrawalDisconnectReturnsFundsWithoutCallingTarget(GameTestHelper h) {
        for (boolean repeated : new boolean[] {false, true}) {
            boolean[] online = {true};
            int[] calls = {0};
            var cell = new Store(1000) {
                @Override
                public long extract(AEKey key, long n, Actionable mode, IActionSource source) {
                    long taken = super.extract(key, n, mode, source);
                    if (mode == Actionable.MODULATE) online[0] = false;
                    return taken;
                }
            };
            var network = new Store(0);
            var buffer = new BufferedMEStorage(network, () -> cell, null, null, () -> online[0]);
            var target = new TargetAccess() {
                public long simulateReceive(long n) { return n; }
                public long receive(long n) { calls[0]++; return n; }
            };
            long sent = repeated ? AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 100, 4)
                    : AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 100);
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(sent == 0 && calls[0] == 0 && cell.energy + network.energy == 1000,
                    "Withdrawal disconnect delivered or lost FE: repeated=" + repeated);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void malformedRefundReceiptCannotMakePendingEnergyNegative(GameTestHelper h) {
        var store = new Store(0) {
            @Override
            public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
                super.insert(key, n, mode, source);
                return Long.MAX_VALUE;
            }
        };
        var buffer = new BufferedMEStorage(store);
        buffer.restorePendingEnergy(100);
        buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
        h.assertTrue(store.energy == 100 && buffer.getPendingEnergy() == 0,
                "Overreported refund created negative owned FE: " + buffer.getPendingEnergy());
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void saturatedReturnMustAcknowledgeOnlyWholeStoredUnits(GameTestHelper h) {
        for (boolean direct : new boolean[] {false, true}) {
            var store = new Store(0);
            var buffer = new BufferedMEStorage(store);
            buffer.setCostMultiplier(2);
            long returned = direct ? buffer.returnFromDirectSend(Long.MAX_VALUE, SOURCE)
                    : buffer.insert(AppFluxBridge.FE_KEY, Long.MAX_VALUE, Actionable.MODULATE, SOURCE);
            h.assertTrue(returned == Long.MAX_VALUE / 2 && store.energy == returned * 2,
                    "Saturated return acknowledged unstored units: direct=" + direct + " returned=" + returned);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void recursiveTargetCannotReenterItsOwnBuffer(GameTestHelper h) {
        var store = new Store(1_000_000);
        var buffer = new BufferedMEStorage(store);
        long[] nested = {-1}, received = {0};
        var target = new TargetAccess() {
            int depth;
            public long simulateReceive(long n) { return n; }
            public long receive(long n) {
                if (++depth < 20) nested[0] = AppFluxBridge.sendToTargetKnownDemand(this, buffer, SOURCE, n);
                received[0] += n;
                depth--;
                return n;
            }
        };
        long result = AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 100);
        h.assertTrue(result == 100 && nested[0] == 0 && received[0] == 100 && store.energy == 999_900,
                "Nested callback entered an already active buffer transaction: nested=" + nested[0]);
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void recursiveSimulationCannotReenterItsOwnBuffer(GameTestHelper h) {
        var store = new Store(1000);
        var buffer = new BufferedMEStorage(store);
        var service = new BufferedStorageService(null, buffer);
        long[] nested = {-1};
        var target = new TargetAccess() {
            public long simulateReceive(long n) {
                nested[0] = AppFluxBridge.sendToTargetKnownDemand(this, buffer, SOURCE, n);
                return n;
            }
            public long receive(long n) { return n; }
        };
        h.assertTrue(AppFluxBridge.sendToTarget(target, service, SOURCE, 100) == 100
                        && nested[0] == 0 && store.energy == 900,
                "Simulation callback bypassed the buffered send guard");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void badWithdrawalReceiptCannotAuthorizeExtraDelivery(GameTestHelper h) {
        for (long receipt : new long[] {-1, 101, Long.MAX_VALUE}) {
            var store = new Store(1000) {
                @Override
                public long extract(AEKey key, long n, Actionable mode, IActionSource source) {
                    super.extract(key, n, mode, source);
                    return receipt;
                }
            };
            var buffer = new BufferedMEStorage(store);
            long[] received = {0};
            var target = new TargetAccess() {
                public long simulateReceive(long n) { return n; }
                public long receive(long n) { received[0] += n; return n; }
            };
            h.assertTrue(AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 100) == 0
                            && received[0] == 0 && store.energy == 900 && buffer.getPendingEnergy() == 0,
                    "Invalid withdrawal receipt authorized invented energy: receipt=" + receipt);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void malformedRefundsAreNotReplayedAcrossThousandsOfRetries(GameTestHelper h) {
        for (long receipt : new long[] {-1, 101, Long.MAX_VALUE}) {
            int[] writes = {0};
            var store = new Store(0) {
                @Override
                public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
                    long result = super.insert(key, n, mode, source);
                    if (mode == Actionable.MODULATE) { writes[0]++; return receipt; }
                    return result;
                }
            };
            var buffer = new BufferedMEStorage(store);
            buffer.restorePendingEnergy(100);
            for (int i = 0; i < 10_000; i++) buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(writes[0] == 1 && store.energy == 100 && buffer.getPendingEnergy() == 0,
                    "Malformed refund was replayed or left negative energy: receipt=" + receipt);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void sameBufferRecoversAfterTargetThrowAndRejectedNestedSend(GameTestHelper h) {
        var store = new Store(1_000_000);
        var buffer = new BufferedMEStorage(store);
        var target = new TargetAccess() {
            public long simulateReceive(long n) { return n; }
            public long receive(long n) {
                h.assertTrue(AppFluxBridge.sendToTargetKnownDemand(this, buffer, SOURCE, n) == 0,
                        "Rejected nested transaction changed ownership");
                throw new NoSuchMethodError("post-recursion failure");
            }
        };
        var healthy = new TargetAccess() {
            public long simulateReceive(long n) { return n; }
            public long receive(long n) { return n; }
        };
        for (int i = 0; i < 1000; i++) {
            h.assertTrue(AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 100) == 0,
                    "Unknown receipt must not be acknowledged");
            h.assertTrue(AppFluxBridge.sendToTargetKnownDemand(healthy, buffer, SOURCE, 100) == 100,
                    "Transfer guard remained locked after exception");
        }
        h.assertTrue(store.energy == 800_000, "Recursive error recovery violated accounting");
        System.out.println("ENERGY_RECEIPT_STRESS_PASS malformedRefundRetries=30000 reentrantRecoveryRounds=1000");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void destructionRefusalRecoveryTravelsWithBlockItem(GameTestHelper h) throws Exception {
        var pos = new BlockPos(1, 1, 1);
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "EnergyRecoveryQA"));
        for (var block : new net.minecraft.world.level.block.Block[] {
                com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_POWER_SUPPLY.get(),
                com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_INTERFACE.get(),
                com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_PATTERN_PROVIDER.get()}) {
            h.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE);
            h.setBlock(pos, block);
            var original = h.getBlockEntity(pos);
            var distributor = hostDistributor(original);
            var store = new Store(0);
            store.capacity = 0;
            var buffer = new BufferedMEStorage(store);
            buffer.restorePendingEnergy(100);
            var field = WirelessEnergyDistributor.class.getDeclaredField("bufferedStorage");
            field.setAccessible(true);
            field.set(distributor, buffer);
            var area = new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(2);
            var existing = new java.util.HashSet<>(h.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, area));
            h.assertTrue(h.getLevel().destroyBlock(h.absolutePos(pos), true), "Destroy failed");
            var dropped = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    area, item -> !existing.contains(item));
            long recovery = dropped.stream().map(net.minecraft.world.entity.item.ItemEntity::getItem)
                    .filter(stack -> stack.hasTag()).mapToLong(stack -> stack.getTag().getLong(
                            "ae2lt:pending_wireless_fe")).sum();
            h.assertTrue(recovery == 100 && buffer.getPendingEnergy() == 0,
                    "Refused refund was not transferred to the dismantled block item: recovery="
                            + recovery + ", pending=" + buffer.getPendingEnergy());
            var blockItem = dropped.stream().map(net.minecraft.world.entity.item.ItemEntity::getItem)
                    .filter(stack -> stack.is(block.asItem())).findFirst().orElseThrow().copy();
            for (var item : dropped) item.discard();
            var abs = h.absolutePos(pos);
            var context = new net.minecraft.world.item.context.BlockPlaceContext(h.getLevel(), player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, blockItem,
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(abs.below()).add(0, 0.5, 0),
                            Direction.UP, abs.below(), false));
            h.assertTrue(((net.minecraft.world.item.BlockItem) blockItem.getItem()).place(context).consumesAction(),
                    "Recovered block item could not be placed");
            var restored = hostDistributor(h.getBlockEntity(pos));
            h.assertTrue(restored.getBufferedEnergy() == 100,
                    "Native placement did not restore the owned refund: " + block);
            var recoveredStore = new Store(0);
            restored.getBufferedStorage().rebindDelegate(recoveredStore);
            for (int i = 0; i < 1000; i++) restored.flushBufferToNetwork();
            h.assertTrue(recoveredStore.energy == 100 && restored.getBufferedEnergy() == 0,
                    "Restored refund was lost or replayed: " + block);
            h.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
        }
        System.out.println("ENERGY_DESTRUCTION_RECOVERY_PASS hosts=3 pendingFE=100 nativePlacements=3 refundRetries=3000");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void reentrantCostChangeMustNotRepriceRefund(GameTestHelper h) {
        for (boolean repeated : new boolean[] {false, true}) {
            var cell = new Store(1000);
            var network = new Store(0);
            var buffer = new BufferedMEStorage(network, () -> cell);
            buffer.setCostMultiplier(2);
            long[] received = {0};
            var target = new TargetAccess() {
                public long simulateReceive(long n) { return n; }
                public long receive(long n) {
                    buffer.setCostMultiplier(3);
                    received[0] += n / 2;
                    return n / 2;
                }
            };
            if (repeated) AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 100, 4);
            else AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 100);
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(cell.energy + network.energy + received[0] * 2 == 1000,
                    "Callback repriced already withdrawn FE: repeated=" + repeated + " remaining=" + cell.energy);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void fractionalScratchMustCombineWithBackingEnergy(GameTestHelper h) {
        for (boolean direct : new boolean[] {false, true}) {
            var cell = new Store(1);
            var network = new Store(0);
            var buffer = new BufferedMEStorage(network, () -> cell);
            buffer.setCostMultiplier(2);
            buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 1, SOURCE);
            cell.capacity = 0;
            network.capacity = 0;
            buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE);
            cell.energy = 1; // one new externally supplied raw FE, plus the held raw FE
            long sent = direct ? buffer.extractForDirectSend(1, SOURCE)
                    : buffer.extract(AppFluxBridge.FE_KEY, 1, Actionable.MODULATE, SOURCE);
            h.assertTrue(sent == 1 && buffer.getBufferedEnergy() + network.energy == 0,
                    "Held fraction stranded a whole affordable send: direct=" + direct + " sent=" + sent);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void refusedRefundSurvivesRealHostSaveAndReload(GameTestHelper h) throws Exception {
        var blocks = new net.minecraft.world.level.block.Block[] {
                com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_POWER_SUPPLY.get(),
                com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_INTERFACE.get(),
                com.moakiee.ae2lt.registry.ModBlocks.OVERLOADED_PATTERN_PROVIDER.get()};
        for (var block : blocks) {
            h.setBlock(BlockPos.ZERO, block);
            var original = h.getBlockEntity(BlockPos.ZERO);
            var distributor = hostDistributor(original);
            var store = new Store(100);
            var buffer = new BufferedMEStorage(store);
            buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 100, SOURCE);
            store.capacity = 0;
            buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE);
            var field = WirelessEnergyDistributor.class.getDeclaredField("bufferedStorage");
            field.setAccessible(true);
            field.set(distributor, buffer);
            var saved = original.saveWithoutMetadata();
            var restored = original.getType().create(original.getBlockPos(), original.getBlockState());
            try {
                restored.load(saved);
                h.assertTrue(hostDistributor(restored).getBufferedEnergy() == 100,
                        "Host save/reload discarded refused refund: " + block);
                h.assertTrue(distributor.getBufferedEnergy() == 100, "Saving must not consume the live refund");
            } finally {
                // AE2 queues constructor-created BEs for readiness, even detached snapshots.
                // Do not let a level-less reconstruction interfere with other native tests.
                restored.setRemoved();
                h.setBlock(BlockPos.ZERO, net.minecraft.world.level.block.Blocks.AIR);
            }
        }
        h.succeed();
    }

    private static WirelessEnergyDistributor hostDistributor(net.minecraft.world.level.block.entity.BlockEntity be)
            throws Exception {
        Object owner = be;
        String fieldName = "wirelessDistributor";
        if (be instanceof com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity supply) {
            owner = supply.getSupplyLogic();
            fieldName = "distributor";
        } else if (be instanceof com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity provider) {
            owner = provider.getLogic();
        }
        var field = owner.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (WirelessEnergyDistributor) field.get(owner);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void continuousModeCellAndGridChangesPreserveRawEnergy(GameTestHelper h) {
        var random = new SplittableRandom(420261004L);
        var networks = new Store[] {new Store(1_000_000), new Store(1_000_000)};
        var cell = new Store(1000);
        MEStorage[] installed = {cell};
        var buffer = new BufferedMEStorage(networks[0], () -> installed[0]);
        long[] spent = {0}, uncertain = {0};
        for (int tick = 0; tick < 20_000; tick++) {
            int cost = 1 + random.nextInt(4);
            buffer.setCostMultiplier(cost);
            buffer.advanceHistory();
            installed[0] = random.nextBoolean() ? cell : null;
            buffer.rebindDelegate(networks[random.nextInt(2)]);
            for (var store : networks) store.capacity = random.nextBoolean() ? store.energy : Long.MAX_VALUE;
            cell.capacity = random.nextBoolean() ? cell.energy : 2000;
            boolean batch = random.nextBoolean();
            if (batch) buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, random.nextInt(4096), SOURCE);
            var target = new TargetAccess() {
                public long simulateReceive(long n) { return n; }
                public long receive(long n) {
                    buffer.setCostMultiplier(1 + random.nextInt(4));
                    if (random.nextInt(12) == 0) {
                        uncertain[0] += n * cost;
                        throw new NoSuchMethodError("continuous unknown receipt");
                    }
                    long taken = random.nextBoolean() ? n / 2 : n;
                    spent[0] += taken * cost;
                    return taken;
                }
            };
            if (batch) {
                AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 1 + random.nextInt(1024));
                buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE);
            } else {
                AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 1 + random.nextInt(64), 8);
            }
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(networks[0].energy + networks[1].energy + cell.energy + buffer.getPendingEnergy()
                            + spent[0] + uncertain[0] == 2_001_000,
                    "Continuous lifecycle energy drift at tick=" + tick);
        }
        System.out.println("ENERGY_LIFECYCLE_STRESS_PASS transitions=20000");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void acceptedThenThrowMustNotRefundInFlightEnergy(GameTestHelper h) {
        var cell = new Store(1000);
        var network = new Store(0);
        var buffer = new BufferedMEStorage(network, () -> cell);
        long[] received = {0};
        var target = new TargetAccess() {
            public long simulateReceive(long n) { return n; }
            public long receive(long n) { received[0] += n; throw new IllegalStateException("injected failure AFTER receipt"); }
        };
        try {
            AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 100, 4);
        } catch (RuntimeException e) {
            // Baseline code propagates the exception; accounting must still conserve energy.
        }
        h.assertTrue(cell.energy + network.energy + received[0] == 1000,
                "In-flight energy refunded after receipt: cell=" + cell.energy + " received=" + received[0]);
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void oddCostRemainderMustStayOwnedByBuffer(GameTestHelper h) {
        for (boolean memoryBatch : new boolean[] {false, true}) {
            var cell = new Store(5);
            var network = new Store(0);
            var buffer = new BufferedMEStorage(network, () -> cell);
            buffer.setCostMultiplier(2);
            if (memoryBatch) buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 5, SOURCE);
            long sent = buffer.extract(AppFluxBridge.FE_KEY, 3, Actionable.MODULATE, SOURCE);
            if (memoryBatch) buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE);
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(sent == 2 && cell.energy + network.energy == 1,
                    "Cost division discarded odd FE: batch=" + memoryBatch + " sent=" + sent + " remaining=" + cell.energy);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void randomized512TargetOverloadFaultsPreserveOwnership(GameTestHelper h) {
        var random = new SplittableRandom(20261004);
        int attempts = 0;
        for (int tick = 0; tick < 128; tick++) {
            for (int targetId = 0; targetId < 512; targetId++) {
                int cost = 1 + random.nextInt(4);
                long initial = 1 + random.nextLong(100_000);
                var cell = new Store(initial);
                var network = new Store(0);
                var buffer = new BufferedMEStorage(network, () -> cell);
                buffer.setCostMultiplier(cost);
                long[] received = {0}, uncertain = {0};
                var target = new TargetAccess() {
                    public long simulateReceive(long n) { return 0; }
                    public long receive(long n) {
                        int behavior = random.nextInt(7);
                        if (behavior == 0) { uncertain[0] += n; throw new IllegalStateException("before receipt"); }
                        if (behavior == 1) { received[0] += n; throw new NoSuchMethodError("after receipt"); }
                        long accepted = behavior == 2 ? 0 : behavior == 3 ? n / 2 : n;
                        received[0] += accepted;
                        return behavior == 4 ? Long.MAX_VALUE : behavior == 2 ? -1 : accepted;
                    }
                };
                long result = AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE,
                        1 + random.nextLong(4096), 1 + random.nextInt(64));
                buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
                h.assertTrue(initial == network.energy + buffer.getBufferedEnergy()
                                + (received[0] + uncertain[0]) * cost,
                        "Randomized ownership failed at tick=" + tick + " target=" + targetId);
                h.assertTrue(result >= 0 && result <= received[0], "Invalid acknowledged send accounting");
                attempts++;
            }
        }
        System.out.println("ENERGY_API_STRESS_PASS targets=512 ticks=128 transactions=" + attempts);
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void refusingRefundsStayBufferedUntilStorageRecovers(GameTestHelper h) {
        var cell = new Store(1000);
        var network = new Store(0);
        network.capacity = 0;
        var buffer = new BufferedMEStorage(network, () -> cell);
        var target = new TargetAccess() {
            public long simulateReceive(long n) { return n; }
            public long receive(long n) { cell.capacity = 0; return 0; }
        };
        h.assertTrue(AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, 100, 4) == 0,
                "Rejecting target must acknowledge no energy");
        buffer.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        h.assertTrue(buffer.getBufferedEnergy() + network.energy == 1000, "Refused refund must not be voided");
        network.capacity = Long.MAX_VALUE;
        buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
        h.assertTrue(network.energy + cell.energy == 1000, "Held refund was not recoverable");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void partialFlushCommitSurvivesLaterApiFailure(GameTestHelper h) {
        var cell = new Store(100);
        var network = new Store(0);
        var buffer = new BufferedMEStorage(network, () -> cell);
        buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 100, SOURCE);
        cell.capacity = 40;
        network.failInsert = true;
        try { buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE); } catch (IllegalStateException expected) {}
        network.failInsert = false;
        buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
        h.assertTrue(network.energy + cell.energy == 100, "Known first credit was replayed after a later failure");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void initialPartialPullSurvivesRefillFailure(GameTestHelper h) {
        var cell = new Store(40);
        var network = new Store(60);
        network.failExtract = true;
        var buffer = new BufferedMEStorage(network, () -> cell);
        try { buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 100, SOURCE); } catch (IllegalStateException expected) {}
        network.failExtract = false;
        buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE);
        h.assertTrue(cell.energy + network.energy == 100, "Partial pull was abandoned when refill failed");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void unlimitedPerCallBudgetCannotOverflowBatchTotals(GameTestHelper h) {
        for (int cost : new int[] {1, 2, 3}) {
            var cell = new Store(Long.MAX_VALUE);
            var buffer = new BufferedMEStorage(new Store(0), () -> cell);
            buffer.setCostMultiplier(cost);
            long[] received = {0};
            var target = new TargetAccess() {
                public long simulateReceive(long n) { return n; }
                public long receive(long n) { received[0] += n; return n; }
            };
            long sent = AppFluxBridge.sendToTargetRepeatedOptimistic(target, buffer, SOURCE, Long.MAX_VALUE, 64);
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(sent == Long.MAX_VALUE / cost && cell.energy == Long.MAX_VALUE % cost,
                    "Unlimited transfer overflowed or discarded indivisible fees");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void randomizedNormalBatchesDoNotRefundUnknownReceipts(GameTestHelper h) {
        var random = new SplittableRandom(51220261004L);
        for (int tick = 0; tick < 128; tick++) {
            var network = new Store(1_000_000);
            var buffer = new BufferedMEStorage(network);
            buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 1_000_000, SOURCE);
            long[] delivered = {0}, uncertain = {0};
            for (int i = 0; i < 512; i++) {
                var target = new TargetAccess() {
                    public long simulateReceive(long n) { return n; }
                    public long receive(long n) {
                        int behavior = random.nextInt(6);
                        if (behavior == 0) { delivered[0] += n; throw new IllegalStateException("normal after receipt"); }
                        if (behavior == 1) { uncertain[0] += n; throw new NoSuchMethodError("normal before receipt"); }
                        long taken = behavior == 2 ? 0 : behavior == 3 ? n / 2 : n;
                        delivered[0] += taken;
                        return behavior == 4 ? Long.MAX_VALUE : taken;
                    }
                };
                AppFluxBridge.sendToTargetKnownDemand(target, buffer, SOURCE, 1 + random.nextInt(4096));
            }
            buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE);
            h.assertTrue(network.energy + buffer.getBufferedEnergy() + delivered[0] + uncertain[0] == 1_000_000,
                    "Normal ownership accounting failed at tick=" + tick);
        }
        System.out.println("ENERGY_NORMAL_STRESS_PASS targets=512 ticks=128 transactions=65536");
        h.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void healthyTargetsSurviveThousandsOfFaultyWheelVisits(GameTestHelper h) throws Exception {
        var targets = new ArrayList<WirelessEnergyAPI.Target>();
        for (int i = 0; i < 512; i++) targets.add(new WirelessEnergyAPI.Target(h.getLevel().dimension(), new BlockPos(i, 1, 1), Direction.UP));
        var network = new Store(10_000_000);
        var buffer = new BufferedMEStorage(network);
        int[] calls = new int[512];
        long[] delivered = {0}, uncertain = {0};
        var host = new WirelessEnergyDistributor.Host() {
            public IManagedGridNode getMainNode() { return null; }
            public IActionSource actionSource() { return SOURCE; }
            public boolean isHostRemoved() { return false; }
            public List<WirelessEnergyAPI.Target> getValidTargets() { return targets; }
            public int getValidTargetsVersion() { return 1; }
        };
        var distributor = new WirelessEnergyDistributor(host, (target, level) -> {
            int id = target.pos().getX();
            if (id % 6 == 0) throw new NoSuchMethodError("persistent failed resolution");
            return new TargetAccess() {
                public long simulateReceive(long n) {
                    if (id % 3 == 0) throw new NoSuchMethodError("broken simulation");
                    return Math.min(n, 3);
                }
                public long receive(long n) {
                    calls[id]++;
                    if (id % 3 == 1) { uncertain[0] += n; throw new IllegalStateException("broken receipt"); }
                    delivered[0] += n;
                    return n;
                }
            };
        });
        var field = WirelessEnergyDistributor.class.getDeclaredField("storageProxy"); field.setAccessible(true);
        field.set(distributor, new BufferedStorageService(null, buffer));
        distributor.refreshTargets();
        var run = WirelessEnergyDistributor.class.getDeclaredMethod("runNormalTick", net.minecraft.server.level.ServerLevel.class, BufferedMEStorage.class);
        run.setAccessible(true);
        for (int tick = 0; tick < 2048; tick++) {
            run.invoke(distributor, h.getLevel(), buffer);
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
        }
        for (int i = 0; i < 512; i++) if (i % 3 == 2) h.assertTrue(calls[i] > 1000, "Healthy target stranded: " + i);
        h.assertTrue(network.energy + buffer.getBufferedEnergy() + delivered[0] + uncertain[0] == 10_000_000,
                "Wheel exception recovery violated energy accounting");
        System.out.println("ENERGY_WHEEL_STRESS_PASS targets=512 ticks=2048 healthyMinCalls=1000");
        h.succeed();
    }

    static class Store implements MEStorage {
        long energy;
        long capacity = Long.MAX_VALUE;
        boolean failInsert, failExtract;
        Store(long energy) { this.energy = energy; }
        public long extract(AEKey key, long n, Actionable mode, IActionSource source) {
            if (failExtract && mode == Actionable.MODULATE) throw new IllegalStateException("injected extraction failure");
            long taken = Math.max(0, Math.min(n, energy));
            if (mode == Actionable.MODULATE) energy -= taken;
            return taken;
        }
        public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
            long added = Math.max(0, Math.min(n, capacity - energy));
            if (mode == Actionable.MODULATE) energy += added;
            if (failInsert && mode == Actionable.MODULATE) throw new IllegalStateException("injected insertion failure AFTER credit");
            return added;
        }
        public Component getDescription() { return Component.literal("Energy fault-injection fixture"); }
    }
}
