package com.moakiee.ae2lt.logic.energy;

import appeng.api.networking.GridHelper;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PriorityScreen;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import appeng.menu.implementations.InterfaceMenu;
import appeng.parts.AEBasePart;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.client.gui.NetworkManagementScreen;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Disposable real-client test: the far chunk is not sent to the client and the second interface is in the Nether. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class RemoteDeviceClientProbe {
    private static final BlockPos C = new BlockPos(0, 100, 0), FAR = new BlockPos(160, 100, 0);
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable failure;
    private static String panelSearch, treeType;
    private static int treeTotal, treeCount;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.remoteDeviceClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks > 3600) { finish(new AssertionError("timeout at " + phase)); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (mc.screen != null) {
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 10, 10);
            try {
                var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                move.setAccessible(true); move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 10., 10.);
            } catch (ReflectiveOperationException problem) { finish(problem); return; }
        }
        if (ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Remote device submenu QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.options.guiScale().set(2); mc.options.renderDistance().set(4); mc.resizeDisplay();
                    panelSearch = BuiltInRegistries.ITEM.getKey(AEParts.INTERFACE.asItem()) + " 162";
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var nether = mc.getSingleplayerServer().getLevel(Level.NETHER);
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); player.getInventory().clearContent();
                        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) level.setBlockAndUpdate(C.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                        player.teleportTo(level, .5, 100, -2.5, java.util.Set.of(), 0, 10);
                        level.setBlockAndUpdate(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
                        level.setBlockAndUpdate(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setChunkForced(FAR.getX() >> 4, FAR.getZ() >> 4, true);
                        nether.setChunkForced(FAR.getX() >> 4, FAR.getZ() >> 4, true);
                        level.setBlockAndUpdate(FAR, AEBlocks.INTERFACE.block().defaultBlockState());
                        ((InterfaceBlockEntity)level.getBlockEntity(FAR)).setName("远端接口");
                        nether.setBlockAndUpdate(FAR, AEBlocks.INTERFACE.block().defaultBlockState());
                        ((InterfaceBlockEntity)nether.getBlockEntity(FAR)).setName("异维接口");
                        PartHelper.setPart(level, FAR.east(2), null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
                        PartHelper.setPart(level, FAR.east(2), Direction.SOUTH, player, AEParts.INTERFACE.asItem());
                        level.setBlockAndUpdate(FAR.east(4), ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                        var frequency = WirelessFrequencyManager.get().createFrequency(player, "Remote Config QA", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
                        ((AdvancedWirelessOverloadedControllerBlockEntity)level.getBlockEntity(C)).setFrequency(frequency.getId());
                    });
                }
                case 1 -> server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    var nether = mc.getSingleplayerServer().getLevel(Level.NETHER);
                    var controller = (AdvancedWirelessOverloadedControllerBlockEntity)level.getBlockEntity(C);
                    GridHelper.createConnection(controller.getActionableNode(), ((InterfaceBlockEntity)level.getBlockEntity(FAR)).getActionableNode());
                    GridHelper.createConnection(controller.getActionableNode(), ((InterfaceBlockEntity)nether.getBlockEntity(FAR)).getActionableNode());
                    GridHelper.createConnection(controller.getActionableNode(), PartHelper.getPart(level, FAR.east(2), Direction.SOUTH).getGridNode());
                    GridHelper.createConnection(controller.getActionableNode(), ((appeng.api.networking.security.IActionHost)level.getBlockEntity(FAR.east(4))).getActionableNode());
                });
                case 2 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(C), Direction.NORTH, C, false));
                case 3 -> { require(mc.screen instanceof FrequencyScreen, "controller menu"); click(tr("devices")); }
                case 4 -> { require(snapshot().devices() >= 6, "remote devices indexed"); search("远端接口"); }
                case 5 -> { require(snapshot().devices() == 1, "named remote search"); row(0); }
                case 6 -> { row(1); capture("remote-config-entry.png"); click(tr("configure")); }
                case 7 -> {
                    require(nativeMenu() instanceof InterfaceMenu, "native interface configuration");
                    require(mc.level.getBlockEntity(FAR) == null, "far client chunk must remain unloaded");
                    capture("remote-interface.png"); click(GuiText.Priority.text().getString());
                }
                case 8 -> {
                    require(mc.screen instanceof PriorityScreen, "native nested priority menu");
                    ((NumberEntryWidget)field(PriorityScreen.class, "priority")).setLongValue(321);
                }
                case 9 -> {
                    server(() -> require(((InterfaceBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(FAR)).getPriority() == 321,
                            "remote priority edit reached real interface"));
                    capture("remote-priority.png"); net.neoforged.neoforge.network.PacketDistributor.sendToServer(appeng.core.network.serverbound.SwitchGuisPacket.returnToParentMenu());
                }
                case 10 -> { require(nativeMenu() instanceof InterfaceMenu, "native submenu return"); click(tr("return_devices")); }
                case 11 -> {
                    require(searchValue().equals("远端接口") && snapshot().total() == 2, "restored search and expanded tree");
                    capture("remote-config-return.png"); search("异维接口");
                }
                case 12 -> { require(snapshot().devices() == 1 && snapshot().total() == 2, "cross-dimension search retains expanded type"); }
                case 13 -> { row(1); click(tr("configure")); }
                case 14 -> { require(nativeMenu() instanceof InterfaceMenu, "cross-dimension native menu"); capture("remote-nether-interface.png"); click(GuiText.Priority.text().getString()); }
                case 15 -> { require(mc.screen instanceof PriorityScreen, "cross-dimension priority submenu"); ((NumberEntryWidget)field(PriorityScreen.class, "priority")).setLongValue(654); }
                case 16 -> {
                    server(() -> {
                        require(((InterfaceBlockEntity)mc.getSingleplayerServer().getLevel(Level.NETHER).getBlockEntity(FAR)).getPriority() == 654, "Nether target not configured");
                        require(((InterfaceBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(FAR)).getPriority() == 321, "wrong-dimension target changed");
                    });
                    click(tr("return_devices"));
                }
                case 17 -> { require(searchValue().equals("异维接口"), "cross-dimension return view"); search(panelSearch); }
                case 18 -> { require(snapshot().devices() == 1, "part face search"); row(0); }
                case 19 -> { row(1); click(tr("configure")); }
                case 20 -> {
                    require(nativeMenu() instanceof InterfaceMenu && nativeMenu().getTarget() instanceof AEBasePart part && part.getSide() == Direction.SOUTH,
                            "remote part snapshot and side"); capture("remote-panel.png"); click(tr("return_devices"));
                }
                case 21 -> { require(searchValue().equals(panelSearch), "part return view"); search("ae2lt:overloaded_pattern_provider"); }
                case 22 -> { require(snapshot().devices() == 1, "LT provider search"); row(0); }
                case 23 -> { row(1); click(tr("configure")); }
                case 24 -> { require(nativeMenu() instanceof OverloadedPatternProviderMenu, "native LT provider menu"); capture("remote-lt-provider.png"); click(tr("return_devices")); }
                case 25 -> { require(searchValue().contains("overloaded_pattern_provider"), "LT provider return view"); search("ae2lt:advanced_wireless_overloaded_controller"); }
                case 26 -> { require(snapshot().devices() == 1, "controller search"); row(0); }
                case 27 -> { row(1); click(tr("configure")); }
                case 28 -> {
                    require(mc.screen instanceof FrequencyScreen frequency && frequency.getMenu().hasParentMenu(), "frequency controller as submenu");
                    capture("remote-controller.png"); net.neoforged.neoforge.network.PacketDistributor.sendToServer(appeng.core.network.serverbound.SwitchGuisPacket.returnToParentMenu());
                }
                case 29 -> {
                    require(snapshot().devices() == 1 && searchValue().contains("advanced_wireless_overloaded_controller"), "controller returned to device view");
                    search("");
                }
                case 30 -> {
                    require(snapshot().rows().stream().anyMatch(r -> r.group()) && snapshot().rows().stream().anyMatch(r -> !r.group()), "tree contains both headers and children");
                    capture("network-tree-expanded.png");
                    int index = java.util.stream.IntStream.range(0, snapshot().rows().size())
                            .filter(i -> snapshotUnchecked().rows().get(i).group() && snapshotUnchecked().rows().get(i).state().equals("expanded"))
                            .findFirst().orElseThrow();
                    var header = snapshot().rows().get(index); treeType = header.target().type(); treeCount = header.count(); treeTotal = snapshot().total();
                    row(index); require(snapshot() != null, "collapsing cleared the visible page");
                }
                case 31 -> {
                    require(snapshot().total() == treeTotal - treeCount, "collapse removed wrong rows");
                    require(snapshot().rows().stream().filter(r -> r.group()).count() >= 2, "other groups disappeared");
                    capture("network-tree-collapsed.png");
                    int index = java.util.stream.IntStream.range(0, snapshot().rows().size())
                            .filter(i -> snapshotUnchecked().rows().get(i).group() && snapshotUnchecked().rows().get(i).target().type().equals(treeType))
                            .findFirst().orElseThrow();
                    row(index); require(snapshot() != null, "expanding cleared the visible page");
                }
                case 32 -> {
                    require(snapshot().total() == treeTotal, "expand did not restore child rows");
                    capture("network-tree-expanded.png");
                    ((appeng.client.gui.widgets.Scrollbar)field(NetworkManagementScreen.class, "scrollbar")).setCurrentScroll(2);
                }
                case 33 -> { require(snapshot().offset() == 2, "tree scroll did not settle"); capture("network-tree-scrolled.png"); finish(null); return; }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static appeng.menu.AEBaseMenu nativeMenu() {
        require(Minecraft.getInstance().screen instanceof AEBaseScreen<?>, "native AE2 screen open");
        return ((AEBaseScreen<?>)Minecraft.getInstance().screen).getMenu();
    }
    private static Object field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(Minecraft.getInstance().screen);
    }
    private static ManagementSnapshot snapshot() throws Exception {
        require(Minecraft.getInstance().screen instanceof NetworkManagementScreen, "management screen open");
        var snapshot = (ManagementSnapshot) field(NetworkManagementScreen.class, "snapshot"); require(snapshot != null, "snapshot ready"); return snapshot;
    }
    private static ManagementSnapshot snapshotUnchecked() {
        try { return snapshot(); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void search(String value) throws Exception {
        ((AETextField)field(NetworkManagementScreen.class, "search")).setValue(value);
        var retained = snapshot();
        int serial = (int)field(NetworkManagementScreen.class, "serial");
        var screen = (NetworkManagementScreen)Minecraft.getInstance().screen;
        screen.receive(new com.moakiee.ae2lt.network.NetworkManagementSyncPacket(screen.getMenu().containerId, serial - 1,
                "", new ManagementSnapshot(999, 999, 0, new double[5], false, java.util.List.of())));
        require(snapshot() == retained, "stale response replaced the current visible page");
    }
    private static String searchValue() throws Exception { return ((AETextField)field(NetworkManagementScreen.class, "search")).getValue(); }
    private static void row(int index) throws Exception { clickButton(((Button[])field(NetworkManagementScreen.class, "rowButtons"))[index]); }
    private static String tr(String key) { return Component.translatable("ae2lt.management." + key).getString(); }
    private static void click(String label) {
        var button = Minecraft.getInstance().screen.children().stream()
                .filter(e -> e instanceof Button b && b.active && b.visible && (b.getMessage().getString().equals(label)
                        || b instanceof appeng.client.gui.widgets.ITooltip t && t.getTooltipMessage().stream().anyMatch(c -> c.getString().equals(label))))
                .map(e -> (Button)e).findFirst().orElseThrow(() -> new AssertionError("button missing " + label));
        clickButton(button);
    }
    private static void clickButton(Button button) {
        var screen = Minecraft.getInstance().screen;
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(screen.mouseClicked(x, y, 0), "native click accepted"); screen.mouseReleased(x, y, 0);
    }
    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance(); try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name)); }
    }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable t) { failure = t; } }); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void finish(Throwable problem) {
        done = true; String result = problem == null ? "REMOTE_DEVICE_CLIENT_PASS: native remote and cross-dimension configuration, priority edits, panel face, LT provider, controller submenu, inline tree expansion/collapse, retained pages and stale response rejection"
                : "REMOTE_DEVICE_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("remote-device-result.txt"), result); } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
