package com.moakiee.ae2lt.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.QuantumCluster;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import de.mari_023.ae2wtlib.AE2wtlib;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class TianshuQuantumBridgeGameTests {
    private TianshuQuantumBridgeGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void staleBridgeDoesNotBreakPatternMenu(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        TianshuQuantumBridgeFixtures.staleBridgeDoesNotBreakPatternMenu(helper);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 170)
    public static void destroyedBridgeReconnectsWithoutReopeningMenu(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        TianshuQuantumBridgeFixtures.destroyedBridgeReconnectsWithoutReopeningMenu(helper);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 140)
    public static void destroyedBridgeFallsBackToLocalAccessPoint(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        TianshuQuantumBridgeFixtures.destroyedBridgeFallsBackToLocalAccessPoint(helper);
    }

}

final class TianshuQuantumBridgeFixtures {
    private TianshuQuantumBridgeFixtures() {
    }

    private static ServerPlayer player(GameTestHelper helper, String name, boolean crafting) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        var stack = new ItemStack(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        stack.getOrCreateTag().putDouble("internalCurrentPower", 1_000_000.0);
        player.getInventory().setItem(0, stack);
        return player;
    }

    private static WTMenuHost host(ServerPlayer player, boolean crafting) {
        return new TianshuWTMenuHost(player, 0, player.getInventory().getItem(0), (owner, menu) -> {});
    }

    private static appeng.menu.AEBaseMenu menu(int id, ServerPlayer player, WTMenuHost host) {
        return new TianshuWirelessPatternEncodingTermMenu(id, player.getInventory(), (TianshuWTMenuHost) host);
    }

    private static Object cachedBridge(WTMenuHost host) {
        try {
            Field field = WTMenuHost.class.getDeclaredField("quantumBridge");
            field.setAccessible(true);
            return field.get(host);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Object targetGrid(WTMenuHost host) {
        try {
            Field field = WTMenuHost.class.getDeclaredField("targetGrid");
            field.setAccessible(true);
            return field.get(host);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void cacheBridge(WTMenuHost host, QuantumCluster cluster) {
        try {
            Field field = WTMenuHost.class.getDeclaredField("quantumBridge");
            field.setAccessible(true);
            field.set(host, cluster);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    static void staleBridgeDoesNotBreakPatternMenu(GameTestHelper helper) {
        staleBridgeDoesNotBreakPatternMenu(helper, false);
    }

    static void staleBridgeDoesNotBreakPatternMenu(GameTestHelper helper, boolean crafting) {
        var player = player(helper, "StaleBridge", crafting);
        var host = host(player, crafting);
        var menu = menu(30, player, host);
        player.containerMenu = menu;
        try {
            var stale = new QuantumCluster(BlockPos.ZERO, new BlockPos(2, 2, 0));
            helper.assertTrue(stale.getCenter() == null, "Test bridge must have no center");
            cacheBridge(host, stale);
            menu.broadcastChanges();
            helper.assertTrue(host.getActionableNode() == null && !host.rangeCheck(),
                    "Dead bridge must not expose a grid node");
            helper.assertTrue(cachedBridge(host) == null, "Dead bridge cache must be discarded");
        } finally {
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    private record Bridge(BlockPos center, DriveBlockEntity drive) {
    }

    private static Bridge bridge(GameTestHelper helper, ServerPlayer player) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(3, 3, 3));
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                var block = x == 0 && y == 0 ? AEBlocks.QUANTUM_LINK.block() : AEBlocks.QUANTUM_RING.block();
                level.setBlockAndUpdate(center.offset(x, y, 0), block.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(center.west(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(center.west(3), AEBlocks.DRIVE.block().defaultBlockState());
        var cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        storage.insert(AEItemKey.of(Items.OAK_PLANKS), 256, Actionable.MODULATE, IActionSource.ofPlayer(player));
        storage.persist();
        var drive = (DriveBlockEntity) level.getBlockEntity(center.west(3));
        drive.getInternalInventory().setItemDirect(0, cell);
        var singularity = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        singularity.getOrCreateTag().putLong("freq", UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        ((QuantumBridgeBlockEntity) level.getBlockEntity(center)).getInternalInventory().setItemDirect(0, singularity.copy());
        var upgrades = UpgradeInventories.forItem(player.getInventory().getItem(0), WUTHandler.getUpgradeCardCount());
        helper.assertTrue(upgrades.addItems(new ItemStack(AE2wtlib.QUANTUM_BRIDGE_CARD)).isEmpty(),
                "Terminal must accept a quantum bridge card");
        var singularityInventory = new appeng.util.inv.AppEngInternalInventory(null, 1);
        singularityInventory.setItemDirect(0, singularity);
        singularityInventory.writeToNBT(player.getInventory().getItem(0).getOrCreateTag(), "singularity");
        return new Bridge(center, drive);
    }

    static void destroyedBridgeReconnectsWithoutReopeningMenu(GameTestHelper helper) {
        destroyedBridgeReconnectsWithoutReopeningMenu(helper, false);
    }

    static void destroyedBridgeReconnectsWithoutReopeningMenu(GameTestHelper helper, boolean crafting) {
        var player = player(helper, "BridgeRebuild", crafting);
        var bridge = bridge(helper, player);
        player.setPos(bridge.center().getX() + 100, bridge.center().getY(), bridge.center().getZ() + 100);
        helper.runAfterDelay(50, () -> {
            var host = host(player, crafting);
            var menu = menu(31, player, host);
            player.containerMenu = menu;
            helper.assertTrue(host.rangeCheck() && host.getActionableNode() != null,
                    "Powered quantum bridge must connect the wireless terminal");
            var oldCluster = ((QuantumBridgeBlockEntity) helper.getLevel()
                    .getBlockEntity(bridge.center())).getCluster();
            helper.getLevel().setBlockAndUpdate(bridge.center().east(), Blocks.AIR.defaultBlockState());
            menu.broadcastChanges();
            helper.assertTrue(oldCluster.isDestroyed() && oldCluster.getCenter() == null,
                    "Breaking the ring must destroy its cluster");
            helper.assertTrue(host.getActionableNode() == null && !host.rangeCheck(),
                    "Broken bridge must not expose its old grid");
            helper.assertTrue(cachedBridge(host) == null, "Broken bridge must release its cache");
            helper.getLevel().setBlockAndUpdate(bridge.center().east(),
                    AEBlocks.QUANTUM_RING.block().defaultBlockState());
            helper.runAfterDelay(50, () -> {
                try {
                    menu.broadcastChanges();
                    helper.assertTrue(host.rangeCheck() && host.getActionableNode() != null,
                            "Rebuilt bridge must reconnect the same menu");
                    helper.assertTrue(cachedBridge(host) != oldCluster,
                            "Reconnection must discover a new cluster");
                } finally {
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
                helper.succeed();
            });
        });
    }

    static void destroyedBridgeFallsBackToLocalAccessPoint(GameTestHelper helper) {
        destroyedBridgeFallsBackToLocalAccessPoint(helper, false);
    }

    static void destroyedBridgeFallsBackToLocalAccessPoint(GameTestHelper helper, boolean crafting) {
        var player = player(helper, "BridgeFallback", crafting);
        var bridge = bridge(helper, player);
        var accessPoint = bridge.center().west(2).north();
        helper.getLevel().setBlockAndUpdate(accessPoint,
                AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        player.setPos(accessPoint.getX() + 0.5, accessPoint.getY() + 1, accessPoint.getZ() + 0.5);
        player.getInventory().getItem(0).getOrCreateTag().put("accessPoint", GlobalPos.CODEC.encodeStart(
                NbtOps.INSTANCE, GlobalPos.of(helper.getLevel().dimension(), accessPoint)).result().orElseThrow());
        helper.runAfterDelay(50, () -> {
            var host = host(player, crafting);
            var menu = menu(32, player, host);
            player.containerMenu = menu;
            helper.assertTrue(host.rangeCheck() && host.getActionableNode() != null,
                    "Bridge must be available before breaking the ring");
            helper.getLevel().setBlockAndUpdate(bridge.center().east(), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(30, () -> {
                try {
                    menu.broadcastChanges();
                    helper.assertTrue(host.rangeCheck() && host.getActionableNode() != null,
                            "Local access point must take over after bridge loss: range=" + host.rangeCheck()
                                    + " node=" + host.getActionableNode()
                                    + " wap=" + ((WirelessAccessPointBlockEntity) helper.getLevel()
                                            .getBlockEntity(accessPoint)).getMainNode().isActive()
                                    + " bridge=" + cachedBridge(host)
                                    + " target=" + targetGrid(host)
                                    + " wapGrid=" + ((WirelessAccessPointBlockEntity) helper.getLevel()
                                            .getBlockEntity(accessPoint)).getMainNode().getGrid());
                    helper.assertTrue(cachedBridge(host) == null, "Fallback must release the broken bridge");
                    helper.assertTrue(host.getInventory().extract(AEItemKey.of(Items.OAK_PLANKS), 1,
                            Actionable.SIMULATE, IActionSource.ofPlayer(player)) == 1,
                            "Fallback must retain access to real ME storage");
                } finally {
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
                helper.succeed();
            });
        });
    }
}
