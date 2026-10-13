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
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
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
    public static void sameTypeReplacementRevokesGeneration(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var registry = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            check(registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, true).equals("connected"), "bind failed");
            h.getLevel().removeBlockEntity(f.target);
            h.getLevel().setBlockEntity(new BarrelBlockEntity(f.target, Blocks.BARREL.defaultBlockState()));
            check(!registry.isBound(h.getLevel(), f.target, f.frequency), "same-type replacement inherited energy binding");
            h.succeed();
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
            for (int i = 0; i < rows.size(); i++) if (rows.getCompound(i).getInt("frequency") == f.frequency) {
                rows.getCompound(i).putString("dimension", "ae2lt:unloaded_test_dimension");
            }
            var isolated = WirelessEnergyEndpoints.load(tag, h.getLevel().registryAccess());
            isolated.tick(h.getLevel().getServer());
            check(isolated.inspect(f.frequency, f.player.getUUID()).getFirst().state() == WirelessEnergyEndpoints.State.UNLOADED, "unloaded endpoint not paused");
            check(f.machine.receives == 0, "unloaded dimension received energy");
            registry.setBound(f.player, f.frequency, h.getLevel(), f.target, Direction.NORTH, false); h.succeed();
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
