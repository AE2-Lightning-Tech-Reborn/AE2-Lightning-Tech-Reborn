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
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder("ae2lt_management")
@PrefixGameTestTemplate(false)
public final class RemoteDeviceGameTests {
    private static final BlockPos C = new BlockPos(2, 2, 2), TARGET = C.east(5);
    private static final ManagementQuery VIEW = new ManagementQuery(9, false, false, "", "interface",
            List.of(new ManagementQuery.Token("interface", List.of())), 2, false, 1, 0);

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
                    && returned.initialQuery().search().equals("interface") && row.id().equals(returned.initialSelection()), "return lost view state");
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
            var rows = ManagementSnapshot.build(f.player, f.host, VIEW).rows();
            var row = rows.stream().filter(r -> r.target().face() == Direction.SOUTH.ordinal()).findFirst().orElseThrow();
            f.menu.query(f.player, VIEW); f.menu.openConfiguration(row.id());
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
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(f.host.getActionableNode() != null && remote.getActionableNode() != null,
                        "waiting for both dimensions to initialize their native nodes"))
                .thenExecute(() -> GridHelper.createConnection(f.host.getActionableNode(), remote.getActionableNode()))
                .thenIdle(20)
                .thenExecute(() -> {
            var query = new ManagementQuery(11, false, false, "", "wireless_receiver",
                    List.of(new ManagementQuery.Token("wireless_receiver", List.of())), 2, false, 1, 0);
            var rows = ManagementSnapshot.build(f.player, f.host, query).rows();
            h.assertTrue(rows.size() == 1 && rows.getFirst().target().dimension().equals("minecraft:the_nether"), "wrong receiver indexed");
            f.menu.query(f.player, query); f.menu.openConfiguration(rows.getFirst().id());
            h.assertTrue(f.player.containerMenu instanceof com.moakiee.ae2lt.menu.FrequencyMenu, "remote frequency submenu missing");
            com.moakiee.ae2lt.network.SelectFrequencyPacket.handle(new com.moakiee.ae2lt.network.SelectFrequencyPacket(
                    f.player.containerMenu.containerId, pos, f.frequency.getId(), ""), context(f.player));
            h.assertTrue(remote.getFrequencyId() == f.frequency.getId() && decoy.getFrequencyId() <= 0,
                    "frequency selection changed the wrong dimension");
            nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            nether.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
        }).thenSucceed();
    }

    private static ManagementRow row(Fixture f) { return ManagementSnapshot.build(f.player, f.host, VIEW).rows().getFirst(); }
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
