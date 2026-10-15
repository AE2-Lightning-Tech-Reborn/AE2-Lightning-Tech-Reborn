package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.client.gui.NetworkManagementScreen;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class WirelessEnergyMenuClientProbe {
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile int frequency;
    private static final BlockPos CONTROLLER = new BlockPos(0, 100, 0);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.wirelessEnergyMenuProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks > 2400) { finish(new AssertionError("timeout phase " + phase)); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (phase >= 2 && ticks % 20 == 0) server(() -> {
            var level = mc.getSingleplayerServer().overworld();
            for (int i = 0; i < 10; i++) {
                BlockPos p = i < 8 ? CONTROLLER.offset(i - 3, 0, 5) : i == 8 ? CONTROLLER.south(2) : CONTROLLER.below().south();
                var cap = level.getCapability(Capabilities.EnergyStorage.BLOCK, p, Direction.NORTH);
                if (cap != null) cap.extractEnergy(2000, false);
            }
        });
        if (ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("ME / Power management QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.gamma().set(1.0); mc.options.renderDistance().set(4);
                    org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1050, 40);
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var level = p.serverLevel();
                        for (int x = -6; x <= 6; x++) for (int z = -5; z <= 8; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                            for (int y = 100; y <= 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.getInventory().clearContent();
                        p.teleportTo(level, .5, 100, -2.5, java.util.Set.of(), 0, 0);
                        level.setBlockAndUpdate(CONTROLLER, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
                        level.setBlockAndUpdate(CONTROLLER.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(CONTROLLER.west(), AEBlocks.DRIVE.block().defaultBlockState());
                        var drive = (DriveBlockEntity) level.getBlockEntity(CONTROLLER.west());
                        drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_64m_cell"))));
                        level.setBlockAndUpdate(CONTROLLER.south(), AEBlocks.INTERFACE.block().defaultBlockState());
                        var nativeInterface = (InterfaceBlockEntity) level.getBlockEntity(CONTROLLER.south());
                        nativeInterface.getInterfaceLogic().getUpgrades().setItemDirect(0, new ItemStack(AppFluxBridge.getInductionCard()));
                        level.setBlockAndUpdate(CONTROLLER.south(2), Blocks.BARREL.defaultBlockState());
                        level.setBlockAndUpdate(CONTROLLER.below(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("appflux:flux_accessor")).defaultBlockState());
                        level.setBlockAndUpdate(CONTROLLER.below().south(), Blocks.BARREL.defaultBlockState());
                        var controller = (AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                        frequency = WirelessFrequencyManager.get(p.server).createFrequency(p, "ME & Power QA", 0xffffff, FrequencySecurityLevel.PRIVATE, "").getId();
                        controller.setFrequency(frequency);
                        for (int i = 0; i < 8; i++) level.setBlockAndUpdate(CONTROLLER.offset(i - 3, 0, 5), Blocks.BARREL.defaultBlockState());
                    });
                }
                case 1 -> server(() -> {
                    var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var level = p.serverLevel();
                    for (int i = 0; i < 8; i++) {
                        var target = CONTROLLER.offset(i - 3, 0, 5); p.moveTo(target.getX() + .5, 100, target.getZ() - 2);
                        require(WirelessEnergyEndpoints.get(p.server).setBound(p, frequency, level, target, Direction.NORTH, true).equals("connected"), "fixture bind");
                    }
                    var controller = (AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                    require(controller.getMainNode().getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY,
                            5_000_000, Actionable.MODULATE, IActionSource.empty()) == 5_000_000, "FE source");
                    p.teleportTo(level, .5, 100, -2.5, java.util.Set.of(), 0, 0);
                });
                case 2 -> openController();
                case 3 -> { require(mc.screen instanceof FrequencyScreen, "native controller opens frequency menu"); click(tr("devices")); }
                case 4 -> {
                    require(snapshot().devices() >= 5 && snapshot().rows().stream().allMatch(r -> r.group()), "ME type aggregation");
                    capture("network-devices.png"); search(Component.translatable("block.ae2.interface").getString());
                }
                case 5 -> {
                    require(snapshot().devices() == 1, "localized device-name search"); row(0);
                }
                case 6 -> {
                    require(snapshot().total() == 1 && !snapshot().rows().getFirst().group(), "type drill-down");
                    capture("network-devices-search.png"); row(0); click(tr("copy"));
                    require(mc.keyboardHandler.getClipboard().contains("minecraft:overworld 0 100 1"), "copy real coordinates");
                    click(tr("locate")); require(mc.screen == null, "locate closes menu");
                    mc.player.setXRot(15);
                }
                case 7 -> { capture("network-device-highlight.png"); openController(); }
                case 8 -> click(tr("energy"));
                case 9 -> {
                    require(snapshot().total() == 10 && snapshot().rows().size() == 5, "all native and wireless power paths, bounded page");
                    click("1s · FE/t");
                }
                case 10 -> { require(snapshot() != null, "5s refresh"); click("5s · FE/t"); }
                case 11 -> { require(snapshot() != null, "30s refresh"); click("30s · FE/t"); }
                case 12 -> {
                    require(snapshot().totals()[4] > 0, "1min average is nonzero FE/t");
                    double last = Double.POSITIVE_INFINITY;
                    for (var r : snapshot().rows()) { require(r.rates()[4] <= last, "power sorting uses selected window"); last = r.rates()[4]; }
                    capture("network-power.png"); search(tr("kind.induction"));
                }
                case 13 -> {
                    require(snapshot().total() == 1 && snapshot().rows().getFirst().kind().equals("induction"), "native induction source filter");
                    capture("network-power-induction.png"); search(tr("kind.accessor"));
                }
                case 14 -> {
                    require(snapshot().total() == 1 && snapshot().rows().getFirst().kind().equals("accessor"), "accessor source filter");
                    search("");
                }
                case 15 -> { require(snapshot().total() == 10, "clear search"); ((Scrollbar)field("scrollbar")).setCurrentScroll(5); }
                case 16 -> {
                    require(snapshot().offset() == 5 && snapshot().rows().size() == 5, "native scrollbar page");
                    int index = 0; while (!snapshot().rows().get(index).canDisconnect()) index++;
                    row(index); click(tr("disconnect"));
                }
                case 17 -> {
                    require(snapshot().total() == 9, "unbind refresh"); capture("network-power-after-disconnect.png");
                    server(() -> { var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(WirelessEnergyEndpoints.get(p.server).inspect(frequency, p.getUUID()).size() == 7, "server removed exactly one target"); });
                }
                case 18 -> clickButton((Button)field("deviceTab"));
                case 19 -> { require(snapshot().devices() >= 5 && snapshot().rows().stream().allMatch(r -> r.group()), "device tab switch"); clickButton((Button)field("powerTab")); }
                case 20 -> { require(snapshot().total() == 9, "power tab switch"); click(tr("back")); }
                case 21 -> { require(mc.screen instanceof FrequencyScreen, "return to frequency menu"); capture("network-management-entry.png"); finish(null); return; }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static void openController() {
        var mc = Minecraft.getInstance(); mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(CONTROLLER), Direction.NORTH, CONTROLLER, false));
    }
    private static Object field(String name) throws Exception {
        var field = NetworkManagementScreen.class.getDeclaredField(name); field.setAccessible(true); return field.get(Minecraft.getInstance().screen);
    }
    private static ManagementSnapshot snapshot() throws Exception {
        require(Minecraft.getInstance().screen instanceof NetworkManagementScreen, "management screen opened");
        var value = (ManagementSnapshot)field("snapshot"); require(value != null, "management snapshot arrived"); return value;
    }
    private static void search(String text) throws Exception { ((AETextField)field("search")).setValue(text); }
    private static void row(int index) throws Exception { clickButton(((Button[])field("rowButtons"))[index]); }
    private static String tr(String key) { return Component.translatable("ae2lt.management." + key).getString(); }
    private static void click(String label) {
        var button = Minecraft.getInstance().screen.children().stream()
                .filter(e -> e instanceof Button b && b.active && b.visible && (b.getMessage().getString().equals(label)
                        || b instanceof appeng.client.gui.widgets.ITooltip tooltip
                        && tooltip.getTooltipMessage().stream().anyMatch(c -> c.getString().equals(label))))
                .map(e -> (Button)e).findFirst().orElseThrow(() -> new AssertionError("missing button " + label));
        clickButton(button);
    }
    private static void clickButton(Button button) {
        var screen = Minecraft.getInstance().screen;
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(screen.mouseClicked(x, y, 0), "native mouse click accepted"); screen.mouseReleased(x, y, 0);
    }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable t) { failure = t; } }); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void finish(Throwable problem) {
        done = true; String result = problem == null ? "NETWORK_MANAGEMENT_CLIENT_PASS: native ME grouping, localized search, drill-down, copy, locate, power windows/sorting, induction/accessor paths, scrollbar and exact disconnect"
                : "NETWORK_MANAGEMENT_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("wireless-energy-menu-result.txt"), result); } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
