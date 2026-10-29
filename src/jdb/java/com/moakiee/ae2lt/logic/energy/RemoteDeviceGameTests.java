package com.moakiee.ae2lt.logic.energy;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.network.serverbound.ConfigButtonPacket;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.management.*;
import com.moakiee.ae2lt.menu.NetworkManagementMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder("ae2lt_management")
@PrefixGameTestTemplate(false)
public final class RemoteDeviceGameTests {
    private static final BlockPos C = new BlockPos(2, 2, 2), TARGET = C.east(5);
    private static final ManagementQuery VIEW = new ManagementQuery(9, false, true, List.of("ae2:interface"), "interface",
            List.of(new ManagementQuery.Token("interface", List.of())), 2, false, 1, 0);

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void nativeIoPortWithoutSubmenuHostOpensRemotely(GameTestHelper h) {
        standaloneMenu(h, AEBlocks.IO_PORT.block(), appeng.menu.implementations.IOPortMenu.TYPE);
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void nativeOverloadedIoPortWithoutSubmenuHostOpensRemotely(GameTestHelper h) {
        standaloneMenu(h, ModBlocks.OVERLOADED_IO_PORT.get(), com.moakiee.ae2lt.menu.OverloadedIOPortMenu.TYPE);
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void nativeExtendedAeConnectorWithoutSubmenuHostOpensRemotely(GameTestHelper h) {
        standaloneMenu(h, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("extendedae:wireless_connect")),
                com.glodblock.github.extendedae.container.ContainerWirelessConnector.TYPE);
    }

    private static void standaloneMenu(GameTestHelper h, net.minecraft.world.level.block.Block block,
            net.minecraft.world.inventory.MenuType<?> expectedType) {
        var f = base(h);
        h.setBlock(TARGET, block);
        var target = (appeng.blockentity.AEBaseBlockEntity)h.getBlockEntity(TARGET);
        h.assertTrue(!(target instanceof appeng.api.storage.ISubMenuHost), "fixture already uses submenu-return support");
        h.runAfterDelay(20, () -> GridHelper.createConnection(f.host.getActionableNode(),
                ((appeng.api.networking.security.IActionHost)target).getActionableNode()));
        h.runAfterDelay(40, () -> {
            var query = new ManagementQuery(19, false, false, List.of(), "", List.of(), 2, false, 1, 0);
            var row = ManagementSnapshot.build(f.player, f.host, query).rows().stream()
                    .filter(r -> r.target().pos().equals(target.getBlockPos())).findFirst().orElseThrow();
            h.assertTrue(row.canConfigure(), "native device was marked unsupported");
            f.menu.query(f.player, query); f.menu.openConfiguration(row.id());
            h.assertTrue(f.player.containerMenu.getType() == expectedType, "wrong native device menu");
            var opened = (AEBaseMenu)f.player.containerMenu;
            h.assertTrue(opened.getTarget() == target && opened.stillValid(f.player), "wrong host or invalid remote menu");
            h.assertTrue(f.player.distanceToSqr(target.getBlockPos().getCenter()) > 64, "fixture is not remote");
            if (target instanceof appeng.api.util.IConfigurableObject configurable) {
                var settings = configurable.getConfigManager();
                var setting = settings.getSettings().stream().filter(s -> s.getValues().size() > 1).findFirst().orElseThrow();
                Object old = settings.getSetting(setting);
                new ConfigButtonPacket(setting, true).handleOnServer(context(f.player));
                h.assertTrue(!old.equals(settings.getSetting(setting)), "native settings did not reach the real device");
            }
            RemoteDeviceSession.locator(f.player).session().returnToDevices(f.player);
            h.assertTrue(f.player.containerMenu instanceof NetworkManagementMenu returned
                    && row.id().equals(returned.initialSelection()) && returned.initialQuery().equals(query),
                    "native return lost the selected device or query");
            ((NetworkManagementMenu)f.player.containerMenu).openConfiguration(row.id());
            h.assertTrue(f.player.containerMenu.getType() == expectedType, "native menu did not reopen");
            f.frequency.setSecurity(FrequencySecurityLevel.PRIVATE);
            h.assertTrue(!((AEBaseMenu)f.player.containerMenu).stillValid(f.player)
                    && !RemoteDeviceSession.acceptPacket(f.player), "revoked access still permits native actions");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void nativeConfigurationAndReturnPreserveDeviceView(GameTestHelper h) {
        fixture(h, f -> {
            var row = row(f);
            f.menu.query(f.player, VIEW);
            f.menu.openConfiguration(row.id());
            h.assertTrue(f.player.containerMenu instanceof InterfaceMenu, "did not open native interface menu");
            var opened = (AEBaseMenu) f.player.containerMenu;
            h.assertTrue(opened.getTarget() == f.target && opened.stillValid(f.player), "wrong native host or remote menu invalid");
            h.assertTrue(f.player.distanceToSqr(f.target.getBlockPos().getCenter()) > 64, "fixture is not remote");
            var settings = f.target.getConfigManager();
            var setting = settings.getSettings().stream().filter(s -> s.getValues().size() > 1).findFirst().orElseThrow();
            Object old = settings.getSetting(setting);
            new ConfigButtonPacket(setting, true).handleOnServer(context(f.player));
            h.assertTrue(!old.equals(settings.getSetting(setting)), "native configuration did not reach real device");
            RemoteDeviceSession.locator(f.player).session().returnToDevices(f.player);
            h.assertTrue(f.player.containerMenu instanceof NetworkManagementMenu returned
                    && returned.initialQuery().search().equals("interface") && row.id().equals(returned.initialSelection())
                    && returned.initialQuery().expandedTypes().equals(VIEW.expandedTypes())
                    && returned.initialSnapshot().rows().stream().anyMatch(ManagementRow::group), "return lost expanded tree or initial snapshot");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void revokedAccessRejectsNativeConfigPacketImmediately(GameTestHelper h) {
        fixture(h, f -> {
            f.menu.query(f.player, VIEW); f.menu.openConfiguration(row(f).id());
            h.assertTrue(f.player.containerMenu instanceof InterfaceMenu, "native menu missing");
            var settings = f.target.getConfigManager();
            var setting = settings.getSettings().stream().filter(s -> s.getValues().size() > 1).findFirst().orElseThrow();
            Object old = settings.getSetting(setting);
            f.frequency.setSecurity(FrequencySecurityLevel.PRIVATE);
            new ConfigButtonPacket(setting, true).handleOnServer(context(f.player));
            h.assertTrue(old.equals(settings.getSetting(setting)), "revoked access still changed native settings");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void staleRowCannotOpenReplacementAtSamePosition(GameTestHelper h) {
        fixture(h, f -> {
            f.menu.query(f.player, VIEW); var old = row(f);
            h.setBlock(TARGET, Blocks.AIR); h.setBlock(TARGET, AEBlocks.INTERFACE.block());
            f.menu.openConfiguration(old.id());
            h.assertTrue(f.player.containerMenu == f.menu, "stale row opened replacement device");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void serializedLocatorCannotForgeServerSession(GameTestHelper h) {
        fixture(h, f -> {
            var row = row(f);
            var session = new RemoteDeviceSession(f.menu, f.target.getActionableNode(), row.id(), VIEW);
            var locator = new RemoteDeviceLocator(session);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                MenuLocators.writeToPacket(buffer, locator);
                var forged = MenuLocators.readFromPacket(buffer);
                h.assertTrue(forged.locate(f.player, Object.class) == null, "wire data created server authority");
                h.assertTrue(session.valid(f.player), "real session invalid");
                f.player.setPos(f.player.getX() - 20, f.player.getY(), f.player.getZ());
                h.assertTrue(!session.valid(f.player), "leaving controller did not revoke remote access");
            } finally { buffer.release(); }
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void panelDeviceUsesNativeMenuAndExactFace(GameTestHelper h) {
        var f = base(h);
        var pos = h.absolutePos(TARGET);
        appeng.api.parts.PartHelper.setPart(h.getLevel(), pos, null, f.player,
                appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        var part = appeng.api.parts.PartHelper.setPart(h.getLevel(), pos, Direction.SOUTH, f.player, appeng.core.definitions.AEParts.INTERFACE.asItem());
        h.runAfterDelay(20, () -> GridHelper.createConnection(f.host.getActionableNode(), part.getGridNode()));
        h.runAfterDelay(40, () -> {
            var query = new ManagementQuery(VIEW.serial(), false, false, List.of(), VIEW.search(), VIEW.tokens(), 2, false, 1, 0);
            var rows = ManagementSnapshot.build(f.player, f.host, query).rows();
            var row = rows.stream().filter(r -> r.target().face() == Direction.SOUTH.ordinal()).findFirst().orElseThrow();
            f.menu.query(f.player, query); f.menu.openConfiguration(row.id());
            h.assertTrue(f.player.containerMenu instanceof InterfaceMenu menu && menu.getTarget() == part,
                    "panel did not open its native menu");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void frequencySubmenuUsesRemoteDimensionAndCannotChangeLocalDecoy(GameTestHelper h) {
        var f = base(h);
        var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var targetPos = h.absolutePos(TARGET);
        var pos = new BlockPos(targetPos.getX(), 80, targetPos.getZ());
        nether.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        nether.setBlockAndUpdate(pos, ModBlocks.WIRELESS_RECEIVER.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, ModBlocks.WIRELESS_RECEIVER.get().defaultBlockState());
        var remote = (com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity) nether.getBlockEntity(pos);
        var decoy = (com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity) h.getLevel().getBlockEntity(pos);
        // Wait for AE2's queued lifecycle callback; invoking onReady manually can initialize
        // the same node twice when the forced Nether chunk starts ticking during a full suite.
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(f.host.getActionableNode() != null && remote.getActionableNode() != null,
                        "waiting for both dimensions to initialize their native nodes"))
                .thenExecute(() -> GridHelper.createConnection(f.host.getActionableNode(), remote.getActionableNode()))
                .thenIdle(20)
                .thenExecute(() -> {
            var query = new ManagementQuery(11, false, false, List.of(), "wireless_receiver",
                    List.of(new ManagementQuery.Token("wireless_receiver", List.of())), 2, false, 1, 0);
            var rows = ManagementSnapshot.build(f.player, f.host, query).rows();
            h.assertTrue(rows.size() == 1 && rows.getFirst().target().dimension().equals("minecraft:the_nether"), "wrong receiver indexed");
            f.menu.query(f.player, query); f.menu.openConfiguration(rows.getFirst().id());
            h.assertTrue(f.player.containerMenu instanceof com.moakiee.ae2lt.menu.FrequencyMenu, "remote frequency submenu missing");
            h.assertTrue(!com.moakiee.ae2lt.logic.world.PlayerWorldActions.mayInteract(f.player, nether, pos),
                    "physical interaction unexpectedly allowed a different dimension");
            var packet = new com.moakiee.ae2lt.network.SelectFrequencyPacket(
                    f.player.containerMenu.containerId, pos, f.frequency.getId(), "");
            var checks = new AtomicInteger();
            Consumer<PlayerInteractEvent.RightClickBlock> deny = event -> {
                if (event.getEntity() == f.player && event.getPos().equals(pos)) {
                    h.assertTrue(event.getLevel() == nether && f.player.serverLevel() == h.getLevel(),
                            "remote protection used the player's world or moved the player");
                    checks.incrementAndGet();
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(deny);
            try {
                com.moakiee.ae2lt.network.SelectFrequencyPacket.handle(packet, context(f.player));
                h.assertTrue(checks.get() == 1 && remote.getFrequencyId() <= 0 && decoy.getFrequencyId() <= 0,
                        "remote configuration skipped target-world protection");
            } finally { NeoForge.EVENT_BUS.unregister(deny); }
            com.moakiee.ae2lt.network.SelectFrequencyPacket.handle(packet, context(f.player));
            h.assertTrue(remote.getFrequencyId() == f.frequency.getId(), "authorized remote frequency selection was rejected");
            h.assertTrue(decoy.getFrequencyId() <= 0 && f.player.serverLevel() == h.getLevel(),
                    "remote frequency selection changed the local decoy or moved the player");
            nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            nether.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
        }).thenSucceed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void sharedNavigationPreservesHostAndRejectsStaleMenus(GameTestHelper h) {
        fixture(h, f -> {
            f.menu.openFrequencyPage(-1);
            f.menu.openFrequencyPage(com.moakiee.ae2lt.menu.FrequencyPage.TAB_ENERGY.ordinal());
            h.assertTrue(f.player.containerMenu == f.menu, "invalid native page changed menu");
            f.menu.openFrequencyPage(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING.ordinal());
            h.assertTrue(f.player.containerMenu instanceof com.moakiee.ae2lt.menu.FrequencyMenu menu
                    && menu.initialPage() == com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING
                    && menu.getCurrentFrequencyId() == f.frequency.getId() && menu.stillValid(f.player), "wrong native page or controller");
            var opened = f.player.containerMenu;
            f.menu.openFrequencyPage(com.moakiee.ae2lt.menu.FrequencyPage.TAB_HOME.ordinal());
            h.assertTrue(f.player.containerMenu == opened, "stale management menu replaced current screen");
            com.moakiee.ae2lt.network.NetworkManagementRequestPacket.handle(
                    new com.moakiee.ae2lt.network.NetworkManagementRequestPacket(opened.containerId, VIEW), context(f.player));
            h.assertTrue(f.player.containerMenu instanceof NetworkManagementMenu restored
                    && restored.initialQuery().search().equals(VIEW.search())
                    && restored.initialQuery().expandedTypes().equals(VIEW.expandedTypes())
                    && restored.initialSnapshot().rows().stream().anyMatch(r -> !r.group()), "restored view has no fresh server snapshot");
            var links = ManagementQuery.initial(ManagementMode.CONNECTIONS);
            NetworkManagementMenu.open(f.player, f.host, ManagementMode.CONNECTIONS, links, null);
            var linkMenu = (NetworkManagementMenu) f.player.containerMenu;
            var controllerRow = linkMenu.initialSnapshot().rows().stream().filter(ManagementRow::canConfigure).findFirst().orElseThrow();
            linkMenu.openConfiguration(controllerRow.id());
            h.assertTrue(f.player.containerMenu instanceof com.moakiee.ae2lt.menu.FrequencyMenu, "link did not open native configuration");
            RemoteDeviceSession.locator(f.player).session().returnToDevices(f.player);
            var restored = (NetworkManagementMenu) f.player.containerMenu;
            h.assertTrue(restored.initialMode() == ManagementMode.CONNECTIONS, "native return lost frequency-link mode");
            f.player.setPos(f.player.getX() - 20, f.player.getY(), f.player.getZ());
            restored.openFrequencyPage(com.moakiee.ae2lt.menu.FrequencyPage.TAB_HOME.ordinal());
            h.assertTrue(f.player.containerMenu == restored, "out-of-range controller still navigated");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void heldCardOpensFullManagementAndReturnsFromNativeGui(GameTestHelper h) {
        fixture(h, f -> {
            var origin = openCard(f, net.minecraft.world.InteractionHand.OFF_HAND);
            f.player.setPos(f.player.getX() - 40, f.player.getY(), f.player.getZ());
            origin.openManagement(f.player, VIEW);
            h.assertTrue(f.player.containerMenu instanceof NetworkManagementMenu, "card did not open full management");
            var manager = (NetworkManagementMenu) f.player.containerMenu;
            h.assertTrue(manager.fromCard() && manager.stillValid(f.player)
                    && manager.initialSnapshot().devices() == 1, "card view lost its device query or remote access");
            manager.openConfiguration(row(f).id());
            h.assertTrue(f.player.containerMenu instanceof InterfaceMenu nativeMenu
                    && nativeMenu.getTarget() == f.target && nativeMenu.stillValid(f.player), "card did not open native device GUI");
            RemoteDeviceSession.locator(f.player).session().returnToDevices(f.player);
            var returned = (NetworkManagementMenu) f.player.containerMenu;
            h.assertTrue(returned.fromCard() && returned.initialQuery().search().equals(VIEW.search()), "native return lost card entry or query");
            returned.openFrequencyPage(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING.ordinal());
            h.assertTrue(f.player.containerMenu instanceof com.moakiee.ae2lt.menu.FrequencyMenu card
                    && card.isCardMode() && card.stillValid(f.player)
                    && card.initialPage() == com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING, "management returned to controller instead of card");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void removingHeldCardRevokesNativeGuiActionsImmediately(GameTestHelper h) {
        fixture(h, f -> {
            var origin = openCard(f, net.minecraft.world.InteractionHand.MAIN_HAND);
            origin.openManagement(f.player, VIEW);
            var manager = (NetworkManagementMenu) f.player.containerMenu;
            manager.openConfiguration(row(f).id());
            h.assertTrue(f.player.containerMenu instanceof InterfaceMenu, "native menu missing");
            var settings = f.target.getConfigManager();
            var setting = settings.getSettings().stream().filter(s -> s.getValues().size() > 1).findFirst().orElseThrow();
            Object old = settings.getSetting(setting);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            h.assertTrue(!manager.canAccessFromSubMenu(f.player), "removed card retained management access");
            new ConfigButtonPacket(setting, true).handleOnServer(context(f.player));
            h.assertTrue(old.equals(settings.getSetting(setting)), "removed card still changed native GUI settings");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void terminalCardKeepsReturnRouteAndRevokesOnRemoval(GameTestHelper h) {
        fixture(h, f -> {
            var card = boundCard(f);
            var terminal = appeng.core.definitions.AEItems.WIRELESS_TERMINAL.stack();
            var upgrades = ((appeng.api.upgrades.IUpgradeableItem)terminal.getItem()).getUpgrades(terminal);
            upgrades.setItemDirect(0, card);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, terminal);
            var locator = (appeng.menu.locator.ItemMenuHostLocator) MenuLocators.forInventorySlot(f.player.getInventory().selected);
            var origin = new com.moakiee.ae2lt.menu.FrequencyMenu(31, f.player.getInventory(), locator);
            f.player.containerMenu = origin;
            origin.openManagement(f.player, VIEW);
            h.assertTrue(f.player.containerMenu instanceof NetworkManagementMenu, "terminal card did not open management");
            var manager = (NetworkManagementMenu)f.player.containerMenu;
            h.assertTrue(manager.fromCard() && manager.hasParentMenu() && manager.getHost() != null, "terminal return host missing");
            manager.openFrequencyPage(com.moakiee.ae2lt.menu.FrequencyPage.TAB_HOME.ordinal());
            h.assertTrue(f.player.containerMenu instanceof com.moakiee.ae2lt.menu.FrequencyMenu returned
                    && returned.isCardMode() && returned.hasParentMenu(), "card overview lost terminal return");
            upgrades.setItemDirect(0, net.minecraft.world.item.ItemStack.EMPTY);
            h.assertTrue(!manager.canAccessFromSubMenu(f.player), "removed terminal upgrade retained management access");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void reboundCardAndRevokedFrequencyCannotKeepManagementAccess(GameTestHelper h) {
        fixture(h, f -> {
            var origin = openCard(f, net.minecraft.world.InteractionHand.MAIN_HAND);
            origin.openManagement(f.player, VIEW);
            var manager = (NetworkManagementMenu)f.player.containerMenu;
            f.frequency.setSecurity(FrequencySecurityLevel.PRIVATE);
            h.assertTrue(!manager.canAccessFromSubMenu(f.player), "revoked frequency retained card access");
            f.frequency.setSecurity(FrequencySecurityLevel.PUBLIC);
            var card = f.player.getMainHandItem();
            com.moakiee.ae2lt.item.OverloadedFrequencyCardItem.setData(card,
                    com.moakiee.ae2lt.item.OverloadedFrequencyCardItem.getData(card).clearFrequency());
            h.assertTrue(!manager.stillValid(f.player), "unbound card retained previous network");
            h.succeed();
        });
    }

    private static net.minecraft.world.item.ItemStack boundCard(Fixture f) {
        var card = new net.minecraft.world.item.ItemStack(com.moakiee.ae2lt.registry.ModItems.OVERLOADED_FREQUENCY_CARD.get());
        com.moakiee.ae2lt.item.OverloadedFrequencyCardItem.bindFrequency(card, f.frequency.getId(),
                f.player.serverLevel().dimension(), f.host.getBlockPos(), f.player.getUUID());
        return card;
    }
    private static com.moakiee.ae2lt.menu.FrequencyMenu openCard(Fixture f, net.minecraft.world.InteractionHand hand) {
        f.player.setItemInHand(hand, boundCard(f));
        com.moakiee.ae2lt.menu.FrequencyMenu.openHeldCard(f.player, hand);
        return (com.moakiee.ae2lt.menu.FrequencyMenu) f.player.containerMenu;
    }

    private static ManagementRow row(Fixture f) { return ManagementSnapshot.build(f.player, f.host, VIEW).rows().stream().filter(r -> !r.group()).findFirst().orElseThrow(); }
    private static void fixture(GameTestHelper h, Consumer<Fixture> test) {
        var f = base(h); h.setBlock(TARGET, AEBlocks.INTERFACE.block());
        InterfaceBlockEntity target = h.getBlockEntity(TARGET);
        var complete = new Fixture(f.host, f.player, f.frequency, f.menu, target);
        h.runAfterDelay(20, () -> GridHelper.createConnection(f.host.getActionableNode(), target.getActionableNode()));
        h.runAfterDelay(40, () -> test.accept(complete));
    }
    private static Fixture base(GameTestHelper h) {
        h.setBlock(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        AdvancedWirelessOverloadedControllerBlockEntity host = h.getBlockEntity(C);
        var player = new MenuPlayer(h.getLevel()); var pos = h.absolutePos(C);
        player.setPos(pos.getX() - 6, pos.getY(), pos.getZ());
        var owner = new MenuPlayer(h.getLevel());
        var frequency = WirelessFrequencyManager.get().createFrequency(owner, "Remote Menu", 0xffffff, FrequencySecurityLevel.PUBLIC, "");
        host.setFrequency(frequency.getId());
        var menu = new NetworkManagementMenu(20, player.getInventory(), host, false); player.containerMenu = menu;
        return new Fixture(host, player, frequency, menu, null);
    }
    private record Fixture(AdvancedWirelessOverloadedControllerBlockEntity host, MenuPlayer player,
            WirelessFrequency frequency, NetworkManagementMenu menu, InterfaceBlockEntity target) {}
    /** Runs real MenuProvider factories and encoders; graphical construction is covered by the client probe. */
    private static final class MenuPlayer extends FakePlayer {
        private int nextId = 30;
        MenuPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "RemoteMenuQA")); }
        @Override public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
            int id = ++nextId; containerMenu = provider.createMenu(id, getInventory(), this);
            if (writer != null) {
                var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
                try { writer.accept(b); } finally { b.release(); }
            }
            return OptionalInt.of(id);
        }
    }
    private static IPayloadContext context(MenuPlayer player) {
        return (IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "enqueueWork" -> { ((Runnable) args[0]).run(); yield java.util.concurrent.CompletableFuture.completedFuture(null); }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
