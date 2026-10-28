package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessControllerCoverageGameTests {
    static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    static ServerPlayer player(ServerLevel level) {
        var player = new MenuPlayer(level);
        player.getInventory().clearContent();
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(1_000_000, 80, 1_000_000);
        return player;
    }

    /** FakePlayer normally suppresses menus. Run the actual registered provider and its opening data. */
    private static final class MenuPlayer extends FakePlayer {
        private int menuId;
        MenuPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "Coverage")); }
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,
                java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer) {
            if (provider == null) return java.util.OptionalInt.empty();
            if (containerMenu != inventoryMenu) closeContainer();
            var menu = provider.createMenu(++menuId, getInventory(), this);
            if (menu == null) return java.util.OptionalInt.empty();
            if (writer != null) {
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registryAccess());
                try { writer.accept(buffer); } finally { buffer.release(); }
            }
            containerMenu = menu;
            return java.util.OptionalInt.of(menuId);
        }
    }

    static WirelessTerminalMenuHost<?> host(ServerPlayer player, ItemStack terminal, GlobalPos anchor) {
        player.getInventory().setItem(0, terminal);
        terminal.set(AEComponents.STORED_ENERGY, 100_000.0);
        if (anchor != null) GridLinkables.get(terminal.getItem()).link(terminal, anchor);
        var host = ((WirelessTerminalItem) terminal.getItem()).getMenuHost(player,
                MenuLocators.forInventorySlot(0), null);
        check(host != null, "real item must create a menu host");
        host.tick();
        return host;
    }

    private static List<ItemStack> terminals() {
        return List.of(AEItems.WIRELESS_TERMINAL.stack(),
                new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get()),
                new ItemStack(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get()));
    }

    private record Network(WirelessOverloadedControllerBlockEntity controller, DriveBlockEntity drive) {
        GlobalPos anchor() { return GlobalPos.of(controller.getLevel().dimension(), controller.getBlockPos()); }
    }

    private static Network network(GameTestHelper helper, boolean advanced) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, (advanced ? ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER
                : ModBlocks.WIRELESS_OVERLOADED_CONTROLLER).get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (DriveBlockEntity) level.getBlockEntity(pos.east());
        var cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        storage.insert(AEItemKey.of(Items.DIAMOND), 64, Actionable.MODULATE, IActionSource.empty());
        storage.persist();
        drive.getInternalInventory().setItemDirect(0, cell);
        return new Network((WirelessOverloadedControllerBlockEntity) level.getBlockEntity(pos), drive);
    }

    static void connected(WirelessTerminalMenuHost<?> host, WirelessOverloadedControllerBlockEntity controller) {
        check(host.getLinkStatus().connected(), "terminal must connect: " + host.getLinkStatus());
        check(host.getActionableNode() != null && host.getActionableNode().getGrid() == controller.getGrid(),
                "actionable node must belong to the bound grid");
        check(host.getItem().getAECurrentPower(host.getItemStack()) < 100_000,
                "extended range must consume terminal battery");
        check(host.getItem().getAECurrentPower(host.getItemStack()) > 99_000,
                "remote/cross-dimensional idle drain must stay finite");
        check(host.insert(host.getPlayer(), AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE) == 1,
                "real terminal insertion must reach the ME cell");
        check(host.getInventory().extract(AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE,
                IActionSource.ofPlayer(host.getPlayer())) == 1, "real ME storage extraction");
    }

    static void menuRoundTrip(ServerPlayer player) {
        var stack = player.getInventory().getItem(0);
        check(stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "item use must open the real terminal menu");
        check(player.containerMenu instanceof appeng.menu.me.common.MEStorageMenu,
                "item must open a storage menu");
        var menu = (appeng.menu.me.common.MEStorageMenu) player.containerMenu;
        try {
            menu.broadcastChanges();
            check(menu.getLinkStatus().connected(), "real menu sync must report connected");
            var field = appeng.menu.me.common.MEStorageMenu.class.getDeclaredField("updateHelper");
            field.setAccessible(true);
            var updates = (appeng.menu.me.common.IncrementalUpdateHelper) field.get(menu);
            var serial = updates.getSerial(AEItemKey.of(Items.DIAMOND));
            check(serial != null, "live inventory update must publish the stored diamonds");
            menu.handleInteraction(serial, appeng.helpers.InventoryAction.PICKUP_OR_SET_DOWN);
            check(menu.getCarried().is(Items.DIAMOND) && menu.getCarried().getCount() == 64,
                    "native menu extraction must move exactly the stored stack to the cursor");
            menu.handleInteraction(-1, appeng.helpers.InventoryAction.PICKUP_OR_SET_DOWN);
            check(menu.getCarried().isEmpty(), "native menu insertion must return the stack to ME storage");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        } finally {
            player.closeContainer();
        }
    }

    @GameTest(template = "workstation_test", timeoutTicks = 160)
    public static void normalCoverageAndBinding(GameTestHelper helper) {
        var net = network(helper, false);
        helper.runAfterDelay(50, () -> {
            check(net.controller().isActive() && net.controller().getFrequencyId() <= 0,
                    "coverage works on a live controller without frequency configuration");
            var player = player(helper.getLevel());
            for (var terminal : terminals()) {
                player.getInventory().setItem(0, terminal);
                terminal.set(AEComponents.STORED_ENERGY, 100_000.0);
                terminal.set(DataComponents.CUSTOM_NAME, Component.literal("Keep components"));
                player.setPos(Vec3.atCenterOf(net.controller().getBlockPos().south()));
                var hit = new BlockHitResult(Vec3.atCenterOf(net.controller().getBlockPos()), Direction.SOUTH,
                        net.controller().getBlockPos(), false);
                var result = player.gameMode.useItemOn(player, helper.getLevel(), terminal, InteractionHand.MAIN_HAND, hit);
                check(result.consumesAction(), "native block interaction must link the held terminal");
                check(net.anchor().equals(terminal.get(AEComponents.WIRELESS_LINK_TARGET)), "controller binding anchor");
                check(terminal.getCount() == 1 && terminal.get(DataComponents.CUSTOM_NAME).getString().equals("Keep components")
                        && terminal.get(AEComponents.STORED_ENERGY) == 100_000.0, "binding preserves count, name and charge");
                player.setPos(1_000_000, 80, 1_000_000);
                connected(host(player, terminal, null), net.controller());
                menuRoundTrip(player);
                var foreignPlayer = player(helper.getLevel().getServer().getLevel(Level.NETHER));
                var cross = host(foreignPlayer, terminal.copy(), null);
                check(!cross.getLinkStatus().connected() && cross.getActionableNode() == null,
                        "normal controller must not cover another dimension");
                check(cross.insert(foreignPlayer, AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE) == 0,
                        "disconnected terminal cannot insert");
                var empty = terminal.copy();
                empty.set(AEComponents.STORED_ENERGY, 0.0);
                player.getInventory().setItem(0, empty);
                var flat = ((WirelessTerminalItem) empty.getItem()).getMenuHost(player, MenuLocators.forInventorySlot(0), null);
                flat.tick();
                check(!flat.getLinkStatus().connected(), "empty terminal must remain offline under coverage");
            }
            var unbound = host(player, AEItems.WIRELESS_TERMINAL.stack(), null);
            check(!unbound.getLinkStatus().connected(), "coverage cannot bind an unlinked terminal implicitly");
            helper.succeed();
        });
    }

    @GameTest(template = "workstation_test", timeoutTicks = 160)
    public static void advancedCrossDimension(GameTestHelper helper) {
        var net = network(helper, true);
        helper.runAfterDelay(50, () -> {
            var player = player(helper.getLevel().getServer().getLevel(Level.NETHER));
            for (var terminal : terminals()) {
                connected(host(player, terminal, net.anchor()), net.controller());
                menuRoundTrip(player);
            }
            if (ModList.get().isLoaded("ae2wtlib")) WirelessCoverageWtChecks.run(player, net.anchor(), net.controller());
            helper.getLevel().setBlockAndUpdate(net.controller().getBlockPos(), Blocks.AIR.defaultBlockState());
            var gone = host(player, AEItems.WIRELESS_TERMINAL.stack(), net.anchor());
            check(!gone.getLinkStatus().connected() && gone.getActionableNode() == null,
                    "removed binding anchor must not resolve through stale history");
            helper.succeed();
        });
    }

    @GameTest(template = "workstation_test", timeoutTicks = 240)
    public static void networkSplitMergeAndFallback(GameTestHelper helper) {
        var net = network(helper, false);
        var wapPos = helper.absolutePos(new BlockPos(5, 2, 1));
        helper.getLevel().setBlockAndUpdate(wapPos, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        var wap = (WirelessAccessPointBlockEntity) helper.getLevel().getBlockEntity(wapPos);
        helper.runAfterDelay(10, () -> {
            var connection = GridHelper.createConnection(wap.getMainNode().getNode(), net.drive().getMainNode().getNode());
            helper.runAfterDelay(50, () -> {
                var player = player(helper.getLevel());
                var terminal = AEItems.WIRELESS_TERMINAL.stack();
                var host = host(player, terminal, GlobalPos.of(helper.getLevel().dimension(), wapPos));
                connected(host, net.controller());
                connection.destroy();
                host.tick();
                check(!host.getLinkStatus().connected() && host.getActionableNode() == null,
                        "split-off access point cannot borrow controller coverage from another grid");
                GridHelper.createConnection(wap.getMainNode().getNode(), net.drive().getMainNode().getNode());
                helper.runAfterDelay(50, () -> {
                    host.tick();
                    connected(host, net.controller());
                    // The same open host follows movement; it need not be reopened to lose coverage.
                    player.setServerLevel(helper.getLevel().getServer().getLevel(Level.NETHER));
                    host.tick();
                    check(!host.getLinkStatus().connected(), "normal coverage follows player's current dimension");
                    player.setServerLevel(helper.getLevel());
                    player.setPos(Vec3.atCenterOf(wapPos.south()));
                    host.tick();
                    check(host.getActionableNode() == wap.getActionableNode(), "nearby native access point retains priority");
                    // An invalidated node must disappear from active coverage even before another world tick.
                    net.controller().onChunkUnloaded();
                    player.setPos(1_000_000, 80, 1_000_000);
                    host.tick();
                    check(!host.getLinkStatus().connected(), "unloaded controller node must revoke coverage");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "workstation_test", timeoutTicks = 300)
    public static void advancedRemovalAndPowerRecovery(GameTestHelper helper) {
        var net = network(helper, false);
        var advancedPos = net.controller().getBlockPos().north();
        helper.getLevel().setBlockAndUpdate(advancedPos,
                ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
        helper.runAfterDelay(60, () -> {
            var player = player(helper.getLevel().getServer().getLevel(Level.NETHER));
            var host = host(player, AEItems.WIRELESS_TERMINAL.stack(), net.anchor());
            connected(host, net.controller());
            helper.getLevel().setBlockAndUpdate(advancedPos, Blocks.AIR.defaultBlockState());
            host.tick();
            check(!host.getLinkStatus().connected(), "removing advanced controller revokes cross-dimension coverage");
            helper.runAfterDelay(40, () -> {
                player.setServerLevel(helper.getLevel());
                host.tick();
                connected(host, net.controller());
                // A demand larger than the controller's entire buffer defeats passive generation as well.
                net.controller().getMainNode().setIdlePowerUsage(1_000_000_000);
                helper.runAfterDelay(30, () -> {
                    check(!net.controller().getGrid().getEnergyService().isNetworkPowered(), "fixture must actually lose network power");
                    host.tick();
                    check(!host.getLinkStatus().connected() && host.getActionableNode() == null,
                            "powered-off grid cannot provide coverage");
                    check(host.insert(player, AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE) == 0,
                            "open terminal cannot insert into the powered-off grid");
                    net.controller().getMainNode().setIdlePowerUsage(3);
                    helper.runAfterDelay(50, () -> {
                        host.tick();
                        connected(host, net.controller());
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "workstation_test", timeoutTicks = 100)
    public static void bindingRespectsPermissions(GameTestHelper helper) {
        var net = network(helper, false);
        var level = helper.getLevel();
        var pos = net.controller().getBlockPos();
        var owner = player(level);
        var stranger = player(level);
        owner.setPos(Vec3.atCenterOf(pos.south()));
        stranger.setPos(Vec3.atCenterOf(pos.south()));
        var manager = com.moakiee.ae2lt.grid.WirelessFrequencyManager.get();
        var frequency = manager.createFrequency(owner, "Coverage binding", 0,
                com.moakiee.ae2lt.grid.FrequencySecurityLevel.PRIVATE, "");
        net.controller().setFrequency(frequency.getId());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getEntity() == owner && event.getPos().equals(pos)) event.setCanceled(true);
        };
        try {
            var stack = AEItems.WIRELESS_TERMINAL.stack();
            stranger.getInventory().setItem(0, stack);
            stranger.gameMode.useItemOn(stranger, level, stack, InteractionHand.MAIN_HAND, hit);
            check(!stack.has(AEComponents.WIRELESS_LINK_TARGET), "private controller denies strangers binding access");
            owner.getInventory().setItem(0, stack);
            NeoForge.EVENT_BUS.addListener(deny);
            owner.gameMode.useItemOn(owner, level, stack, InteractionHand.MAIN_HAND, hit);
            check(!stack.has(AEComponents.WIRELESS_LINK_TARGET), "canceled protection event must deny binding");
            NeoForge.EVENT_BUS.unregister(deny);
            owner.gameMode.useItemOn(owner, level, stack, InteractionHand.MAIN_HAND, hit);
            check(net.anchor().equals(stack.get(AEComponents.WIRELESS_LINK_TARGET)), "authorized owner can bind");
        } finally {
            NeoForge.EVENT_BUS.unregister(deny);
            manager.deleteFrequency(frequency.getId(), level.getServer());
        }
        helper.succeed();
    }
}
