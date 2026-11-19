package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_wireless_energy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
public final class WirelessEnergyGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final Map<BarrelBlockEntity, Machine> MACHINES = new WeakHashMap<>();
    @SubscribeEvent public static void caps(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BlockEntityType.BARREL,
                (be, face) -> face == Direction.NORTH ? MACHINES.computeIfAbsent(be, ignored -> new Machine()) : null);
    }
    private static void check(boolean value, String message) { if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
    private static final class Store implements MEStorage {
        long fe; boolean reject; Runnable onInsert = () -> {};
        Store(long fe, boolean reject) { this.fe = fe; this.reject = reject; }
        public long extract(AEKey key, long amount, Actionable mode, IActionSource src) {
            long result = Math.min(fe, amount); if (mode == Actionable.MODULATE) fe -= result; return result;
        }
        public long insert(AEKey key, long amount, Actionable mode, IActionSource src) {
            if (reject) return 0; if (mode == Actionable.MODULATE) { onInsert.run(); fe += amount; } return amount;
        }
        public Component getDescription() { return Component.literal("test"); }
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void refundsRestartAndPartialAcceptance(GameTestHelper h) {
        var source = new Store(100, true); var id = UUID.randomUUID();
        var ledger = new WirelessEnergyLedger(); var e = ledger.account(id);
        var buffer = new BufferedMEStorage(source, null, null, e);
        check(buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 100, SOURCE) == 100, "initial extraction");
        long delivered = AppFluxBridge.sendToTargetKnownDemand(new TargetAccess() {
            public long simulateReceive(long max) { return max; }
            public long receive(long amount) { return 30; }
        }, buffer, SOURCE, 100);
        buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE); buffer.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        check(delivered == 30 && source.fe == 0 && e.available() == 70, "refused refund lost/duplicated FE");
        ledger = WirelessEnergyLedger.load(ledger.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        var restored = new BufferedMEStorage(source, null, null, ledger.account(id));
        check(restored.beginMemoryBatch(AppFluxBridge.FE_KEY, 70, SOURCE) == 70, "restart did not reuse retained FE");
        source.reject = false; restored.endBatch(AppFluxBridge.FE_KEY, SOURCE);
        restored.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        check(source.fe == 70 && ledger.account(id).available() == 0, "refund replayed after restart");
        h.succeed();
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void reentrantRefundAndAmbiguousReceipt(GameTestHelper h) {
        var source = new Store(0, false); var escrow = new EnergyEscrow(80, 0, () -> {});
        var buffer = new BufferedMEStorage(source, null, null, escrow);
        source.onInsert = () -> buffer.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        buffer.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        check(source.fe == 80 && escrow.available() == 0, "reentrant refund duplicated FE");
        source.onInsert = () -> {}; source.reject = true;
        buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, 80, SOURCE);
        try {
            AppFluxBridge.sendToTargetKnownDemand(new TargetAccess() {
                public long simulateReceive(long max) { return max; }
                public long receive(long amount) { throw new IllegalStateException("unknown receipt"); }
            }, buffer, SOURCE, 80);
            throw new AssertionError("callback should throw");
        } catch (IllegalStateException expected) { }
        buffer.endBatch(AppFluxBridge.FE_KEY, SOURCE);
        check(escrow.uncertain() == 80 && escrow.available() == 0 && source.fe == 0, "ambiguous FE replayed");
        h.succeed();
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void cellAndNetworkBothRefuseAndMultiplierRemainder(GameTestHelper h) {
        var source = new Store(11, true); var cell = new Store(3, true);
        var e = new EnergyEscrow(0, 0, () -> {});
        var b = new BufferedMEStorage(source, () -> cell, null, e);
        b.setCostMultiplier(2);
        check(b.extractForDirectSend(2, SOURCE) == 1, "multiplier credit");
        b.returnFromDirectSend(1, SOURCE); b.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        check(source.fe + cell.fe + e.available() == 14, "cell/network rejection deleted residue");
        h.succeed();
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 140)
    public static void manualBindingIsProbeFreeIdempotentAndChecksPermissions(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            check(f.controller.getMainNode().isActive(), "controller inactive");
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "bind failed");
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "duplicate not idempotent");
            check(f.machine.simulations == 0 && f.machine.receives == 0, "manual binding performed a probe");
            check(registry.inspect(f.frequency, f.player.getUUID()).size() == 1, "duplicate endpoint");
            var outsider = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "EnergyOutsider"));
            outsider.setPos(f.target.getX() + .5, f.target.getY(), f.target.getZ() - 2);
            check(registry.setBound(outsider, f.frequency, h.getLevel(), f.target, Direction.NORTH, false).equals("no_permission"), "unauthorized removal");
            check(f.controller.getMainNode().getGrid().getStorageService().getInventory()
                    .insert(AppFluxBridge.FE_KEY, 50000, Actionable.MODULATE, SOURCE) == 50000, "no FE source storage");
        });
        h.runAfterDelay(60, () -> {
            check(f.machine.getEnergyStored() > 0, "ordinary FE target not powered");
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            var loaded = WirelessEnergyEndpoints.load(registry.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
            check(loaded.inspect(f.frequency, f.player.getUUID()).size() == 1, "endpoint NBT recovery");
            check(loaded.inspect(f.frequency, f.player.getUUID()).getFirst().face() == Direction.NORTH, "face lost");
            long before = f.machine.getEnergyStored();
            registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, false);
            check(!registry.isBound(h.getLevel(), f.target, f.frequency), "unbind failed");
            h.runAfterDelay(5, () -> { check(f.machine.getEnergyStored() == before, "unbound target still powered"); h.succeed(); });
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void sameTypeReplacementPreservesCoordinateBinding(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "bind failed");
            h.getLevel().removeBlockEntity(f.target);
            h.getLevel().setBlockEntity(new BarrelBlockEntity(f.target, Blocks.BARREL.defaultBlockState()));
            h.runAfterDelay(3, () -> {
                check(registry.isBound(h.getLevel(), f.target, f.frequency), "same-type replacement lost coordinate binding");
                h.succeed();
            });
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void nodeClaimSurvivesGridSplitAndMerge(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var node = f.controller.getMainNode().getNode();
            var service = node.getGrid().getService(WirelessEnergyGridService.class);
            var e = service.escrow(node); e.credit(47);
            var data = new CompoundTag(); service.saveNodeData(node, data);
            service.removeNode(node);
            var split = new WirelessEnergyGridService(); split.addNode(node, data);
            check(split.escrow(node) == e && split.escrow(node).available() == 47, "split copied/lost balance");
            var mergedData = new CompoundTag(); split.saveNodeData(node, mergedData); split.removeNode(node);
            service.addNode(node, mergedData);
            check(service.escrow(node) == e, "merge changed owner");
            e.debit(47); h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 140)
    public static void physicalGridSplitAndMergeKeepsIndependentClaims(GameTestHelper h) {
        var leftPos = new BlockPos(2, 2, 2);
        var bridgePos = leftPos.south();
        var rightPos = leftPos.south(2);
        h.setBlock(leftPos, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(bridgePos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(rightPos, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.runAfterDelay(35, () -> {
            var left = ((AdvancedWirelessOverloadedControllerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(leftPos))).getMainNode().getNode();
            var right = ((AdvancedWirelessOverloadedControllerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rightPos))).getMainNode().getNode();
            check(left != null && right != null && left.getGrid() == right.getGrid(), "fixture grids did not join");
            var leftAccount = left.getGrid().getService(WirelessEnergyGridService.class).escrow(left);
            var rightAccount = right.getGrid().getService(WirelessEnergyGridService.class).escrow(right);
            leftAccount.credit(31); rightAccount.credit(73);
            h.setBlock(bridgePos, Blocks.AIR);
            h.runAfterDelay(10, () -> {
                check(left.getGrid() != right.getGrid(), "physical bridge removal did not split grid");
                check(left.getGrid().getService(WirelessEnergyGridService.class).escrow(left) == leftAccount,
                        "left account lost on physical split");
                check(right.getGrid().getService(WirelessEnergyGridService.class).escrow(right) == rightAccount,
                        "right account lost on physical split");
                h.setBlock(bridgePos, AEBlocks.CREATIVE_ENERGY_CELL.block());
                h.runAfterDelay(10, () -> {
                    check(left.getGrid() == right.getGrid(), "physical bridge restore did not merge grid");
                    var merged = left.getGrid().getService(WirelessEnergyGridService.class);
                    check(merged.escrow(left) == leftAccount && merged.escrow(right) == rightAccount,
                            "physical merge changed account identities");
                    check(leftAccount.available() == 31 && rightAccount.available() == 73,
                            "physical split/merge copied or lost FE");
                    leftAccount.debit(31); rightAccount.debit(73);
                    h.succeed();
                });
            });
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 140)
    public static void permissionRevocationPausesAndRestorationResumes(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "bind failed");
            var frequency = WirelessFrequencyManager.get().getFrequency(f.frequency);
            var members = new CompoundTag(); frequency.writeToTag(members, com.moakiee.ae2lt.grid.WirelessFrequency.NBT_MEMBERS_ONLY);
            frequency.readFromTag(new CompoundTag(), com.moakiee.ae2lt.grid.WirelessFrequency.NBT_MEMBERS_ONLY);
            f.controller.getMainNode().getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, 50000, Actionable.MODULATE, SOURCE);
            h.runAfterDelay(10, () -> {
                check(f.machine.getEnergyStored() == 0, "revoked actor still exported energy");
                check(registry.isBound(h.getLevel(), f.target, f.frequency), "revocation destroyed binding");
                frequency.readFromTag(members, com.moakiee.ae2lt.grid.WirelessFrequency.NBT_MEMBERS_ONLY);
                h.runAfterDelay(10, () -> { check(f.machine.getEnergyStored() > 0, "restored permission did not resume"); h.succeed(); });
            });
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void chunkLifecyclePreservesBindingAndRebuildsCapability(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var level = h.getLevel(); var registry = WirelessEnergyEndpoints.get(level.getServer());
            var remote = f.target.offset(1024, 0, 1024);
            var chunk = level.getChunkAt(remote);
            level.setBlockAndUpdate(remote, Blocks.BARREL.defaultBlockState());
            var original = level.getBlockEntity(remote);
            f.player.setPos(remote.getX() + .5, remote.getY(), remote.getZ() - 2);
            check(registry.setBound(f.player, f.frequency, level, remote, Direction.NORTH, true).equals("connected"), "remote bind");
            var generation = registry.inspect(f.frequency, f.player.getUUID()).getFirst().id();
            var nbt = original.saveWithFullMetadata(level.registryAccess());
            chunk.clearAllBlockEntities(); // actual vanilla unload lifecycle (no BE removal hook)
            check(registry.isBound(level, remote, f.frequency), "unload revoked generation");
            var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(remote, Blocks.BARREL.defaultBlockState(), nbt, level.registryAccess());
            chunk.addAndRegisterBlockEntity(restored);
            level.invalidateCapabilities(remote);
            check(registry.inspect(f.frequency, f.player.getUUID()).getFirst().id().equals(generation), "reload changed generation");
            var machine = (Machine) level.getCapability(Capabilities.EnergyStorage.BLOCK, remote, Direction.NORTH);
            check(machine != null && restored != original, "capability not rebuilt");
            f.controller.getMainNode().getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, 50000, Actionable.MODULATE, SOURCE);
            h.runAfterDelay(10, () -> {
                check(machine.getEnergyStored() > 0, "reloaded endpoint not powered");
                registry.setBound(f.player, f.frequency, level, remote, Direction.NORTH, false);
                level.removeBlock(remote, false); h.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void unloadedDimensionIsRetainedWithoutWorldLookup(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "bind failed");
            var tag = registry.save(new CompoundTag(), h.getLevel().registryAccess());
            var rows = tag.getList("endpoints", net.minecraft.nbt.Tag.TAG_COMPOUND);
            rows.removeIf(row -> ((CompoundTag) row).getInt("frequency") != f.frequency);
            for (var row : rows) ((CompoundTag) row).putString("dimension", "ae2lt:unloaded_test_dimension");
            var isolated = WirelessEnergyEndpoints.load(tag, h.getLevel().registryAccess());
            isolated.tick(h.getLevel().getServer());
            check(isolated.inspect(f.frequency, f.player.getUUID()).getFirst().state() == WirelessEnergyEndpoints.State.UNLOADED, "unloaded endpoint not paused");
            check(f.machine.receives == 0, "unloaded dimension received energy");
            registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, false); h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void refillRejectionRetainsOwnershipAcrossSourceDetach(GameTestHelper h) {
        var backing = new Store(123, false);
        var live = new java.util.concurrent.atomic.AtomicBoolean(true);
        var source = new GuardedEnergyStorage(backing, live::get);
        MEStorage cell = new MEStorage() {
            public long extract(AEKey key, long amount, Actionable mode, IActionSource src) { return 0; }
            public long insert(AEKey key, long amount, Actionable mode, IActionSource src) {
                return mode == Actionable.SIMULATE ? amount : 0;
            }
            public Component getDescription() { return Component.literal("refuses real insertion"); }
        };
        var e = new EnergyEscrow(0, 0, () -> {});
        var b = new BufferedMEStorage(source, () -> cell, null, e);
        check(b.beginMemoryBatch(AppFluxBridge.FE_KEY, 123, SOURCE) == 123, "rejected cell refill not available");
        check(backing.fe == 0 && e.available() == 123, "refill lost FE before dispatch");
        live.set(false); b.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        check(e.available() == 123 && backing.fe == 0, "detached source received stale refund");
        live.set(true); b.flushAll(AppFluxBridge.FE_KEY, SOURCE);
        check(e.available() == 0 && backing.fe == 123, "source recovery lost FE");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void cardEntryRejectsForgedHitAndBindsClickedFace(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            var card = new ItemStack(com.moakiee.ae2lt.registry.ModItems.OVERLOADED_FREQUENCY_CARD.get());
            com.moakiee.ae2lt.item.OverloadedFrequencyCardItem.bindFrequency(card, f.frequency,
                    h.getLevel().dimension(), f.controller.getBlockPos(), f.player.getUUID());
            var hit = new net.minecraft.world.phys.Vec3(f.target.getX() + .5, f.target.getY() + .5, f.target.getZ());
            com.moakiee.ae2lt.network.FrequencyCardUsePacket.tryLinkWithCard(f.player, card, h.getLevel(), f.target,
                    Direction.NORTH, new net.minecraft.world.phys.Vec3(Double.NaN, hit.y, hit.z));
            check(!registry.isBound(h.getLevel(), f.target, f.frequency), "forged hit bound endpoint");
            com.moakiee.ae2lt.network.FrequencyCardUsePacket.tryLinkWithCard(f.player, card, h.getLevel(), f.target, Direction.NORTH, hit);
            check(registry.isBound(h.getLevel(), f.target, f.frequency), "card did not bind actual face");
            check(f.machine.receives == 0 && f.machine.simulations == 0, "card admission probed FE");
            com.moakiee.ae2lt.network.FrequencyCardUsePacket.tryLinkWithCard(f.player, card, h.getLevel(), f.target, Direction.NORTH, hit);
            check(!registry.isBound(h.getLevel(), f.target, f.frequency), "second card use did not disconnect");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void managementDisconnectRejectsStaleGenerationsAndMenus(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "bind failed");
            var old = registry.inspect(f.frequency, f.player.getUUID()).getFirst();
            var outsider = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "EnergyManagerOutsider"));
            check(registry.inspect(f.frequency, outsider.getUUID()).isEmpty(), "endpoint list exposed to outsider");
            check(registry.disconnect(outsider, f.frequency, old.id()).equals("no_permission"), "outsider removed endpoint");
            var menu = new com.moakiee.ae2lt.menu.NetworkManagementMenu(123, f.player.getInventory(), f.controller, true);
            f.player.containerMenu = menu;
            f.player.moveTo(f.controller.getBlockPos().getCenter());
            check(menu.acceptRequest(f.player, false), "valid controller menu rejected");
            check(!menu.acceptRequest(f.player, false), "unbounded same-tick page requests");
            check(menu.acceptRequest(f.player, true), "poll consumed the same-tick edit allowance");
            check(!menu.acceptRequest(f.player, true), "unbounded same-tick edits");
            var oldFrequency = f.controller.getFrequencyId();
            f.controller.setFrequency(-1);
            check(!menu.stillValid(f.player), "changed live frequency accepted");
            f.controller.setFrequency(oldFrequency);
            f.player.containerMenu = f.player.inventoryMenu;
            check(!menu.acceptRequest(f.player, false), "closed menu accepted");
            // Management works from the controller; the remote target need not be in reach.
            f.player.moveTo(f.target.getX() + 100, f.target.getY(), f.target.getZ());
            check(registry.disconnect(f.player, f.frequency, old.id()).equals("disconnected"), "manager could not disconnect distant target");
            f.player.moveTo(f.target.getX() + .5, f.target.getY(), f.target.getZ() - 2);
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "rebind failed");
            var replacement = registry.inspect(f.frequency, f.player.getUUID()).getFirst();
            check(!replacement.id().equals(old.id()), "rebind did not rotate generation");
            registry.disconnect(f.player, f.frequency, old.id());
            check(registry.isBound(h.getLevel(), f.target, f.frequency), "stale disconnect deleted replacement");
            check(f.machine.simulations == 0 && f.machine.receives == 0, "management triggered target probe");
            registry.disconnect(f.player, f.frequency, replacement.id());
            h.succeed();
        });
    }

    private static Fixture fixture(GameTestHelper h) {
        BlockPos c = new BlockPos(2, 2, 2), target = h.absolutePos(c.south(3));
        h.setBlock(c, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(c.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(c.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(c.west()));
        drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell"))));
        h.setBlock(c.south(3), Blocks.BARREL);
        var be = (BarrelBlockEntity) h.getLevel().getBlockEntity(target);
        var machine = MACHINES.computeIfAbsent(be, ignored -> new Machine());
        var controller = (AdvancedWirelessOverloadedControllerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(c));
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "EnergyOwner"));
        player.setPos(target.getX() + .5, target.getY(), target.getZ() - 2);
        var f = WirelessFrequencyManager.get().createFrequency(player, "EnergyTest", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
        controller.setFrequency(f.getId());
        return new Fixture(controller, player, f.getId(), target, machine);
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void indivisibleRefundDoesNotStallPoweredCell(GameTestHelper h) {
        var escrow = new EnergyEscrow(1, 0, () -> {});
        var source = new Store(0, true);
        var cell = new Store(100, true);
        var buffer = new BufferedMEStorage(source, () -> cell, null, escrow);
        buffer.setCostMultiplier(2);
        for (int i = 0; i < 10; i++) {
            check(buffer.extractForDirectSend(1, SOURCE) == 1, "indivisible refund stalled powered cell");
            buffer.quarantineDelivery(1); buffer.settleDelivery(1, 1);
            buffer.endTick(AppFluxBridge.FE_KEY, SOURCE);
        }
        check(cell.fe == 80 && escrow.available() == 1, "multiplier remainder lost or duplicated");
        h.succeed();
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 140)
    public static void saturatedPrefixDoesNotStarveLastEndpoint(GameTestHelper h) { fairness(h, true); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 140)
    public static void busyPrefixDoesNotStarveLastEndpoint(GameTestHelper h) { fairness(h, false); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 160)
    public static void sixtyFourEndpointsAllGetBudget(GameTestHelper h) { largeFairness(h, 64, false); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 160)
    public static void full128EndpointSweepAllGetBudget(GameTestHelper h) { largeFairness(h, 128, false); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 160)
    public static void full128SaturatedPrefixDoesNotStarveTail(GameTestHelper h) { largeFairness(h, 128, true); }
    private static void largeFairness(GameTestHelper h, int count, boolean saturated) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var level = h.getLevel();
            var registry = WirelessEnergyEndpoints.get(level.getServer());
            var node = f.controller.getMainNode().getNode();
            node.getGrid().getService(WirelessEnergyGridService.class).escrow(node)
                    .credit(count * 100000L + WirelessEnergySettings.reserveFe.get());
            var origin = new BlockPos(3200 + count * 32, 64, saturated ? 3232 : 3200);
            level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
            var targets = new java.util.ArrayList<Machine>();
            for (int i = 0; i < count; i++) {
                var pos = origin.offset(i % 16, 0, i / 16);
                level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
                var machine = MACHINES.computeIfAbsent((BarrelBlockEntity) level.getBlockEntity(pos), ignored -> new Machine());
                if (saturated && i < count - 1) machine.receiveEnergy(100000, false);
                targets.add(machine);
                f.player.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
                h.assertTrue(registry.setBound(f.player, f.frequency, level, pos, Direction.NORTH, true).equals("connected"), "large fairness bind failed");
            }
            h.runAfterDelay(45, () -> {
                h.assertTrue(targets.stream().allMatch(t -> t.getEnergyStored() > 0), "large sweep left an endpoint permanently budget-starved");
                for (int i = 0; i < count; i++) level.removeBlock(origin.offset(i % 16, 0, i / 16), false);
                level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, false);
                h.succeed();
            });
        });
    }
    private static void fairness(GameTestHelper h, boolean saturated) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            f.controller.getMainNode().getGrid().getStorageService().getInventory()
                    .insert(AppFluxBridge.FE_KEY, 4000000, Actionable.MODULATE, SOURCE);
            Machine last = null;
            for (int i = 0; i < 17; i++) {
                var pos = h.absolutePos(new BlockPos(1 + i % 5, 2, 6 + i / 5));
                h.getLevel().setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
                var be = (BarrelBlockEntity) h.getLevel().getBlockEntity(pos);
                var machine = MACHINES.computeIfAbsent(be, ignored -> new Machine());
                if (saturated && i < 16) machine.receiveEnergy(100000, false);
                f.player.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
                check(registry.setBound(f.player, f.frequency, h.getLevel(), pos, Direction.NORTH, true).equals("connected"), "fairness bind failed");
                last = machine;
            }
            var tail = last;
            h.runAfterDelay(20, () -> {
                check(tail.getEnergyStored() > 0, "fixed-prefix budget starvation");
                h.succeed();
            });
        });
    }
    private static final class Machine extends EnergyStorage {
        int simulations, receives;
        Machine() { super(100000); }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (simulate) simulations++; else receives++;
            return super.receiveEnergy(amount, simulate);
        }
    }
    private record Fixture(AdvancedWirelessOverloadedControllerBlockEntity controller, ServerPlayer player,
                           int frequency, BlockPos target, Machine machine) {}
}
