package com.moakiee.ae2lt.debug;

import appeng.api.config.OperationMode;
import com.google.gson.Gson;
import com.moakiee.ae2lt.client.machine.OverloadedIOPortScreen;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.OverloadedIOPortMenu;
import com.moakiee.ae2lt.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Real independent clients, controlled only in an isolated loopback test profile. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class DedicatedMenuClientProbe {
    private static final Gson JSON = new Gson();
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static String command = "";
    private static int ticks, waiting;
    private static boolean sent, terminal;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || terminal || !Boolean.getBoolean("ae2lt.networkClientProbe")
                || ++ticks % 10 != 0) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        var role = System.getProperty("ae2lt.networkRole");
        var root = Path.of(System.getProperty("ae2lt.networkCoord"));
        try {
            var request = root.resolve(role + "-command.txt");
            if (!Files.isRegularFile(request)) return;
            var value = Files.readString(request).trim();
            if (!value.equals(command)) { command = value; waiting = 0; sent = false; }
            if (Files.isRegularFile(root.resolve(role + "-result.json"))) {
                var previous = JSON.fromJson(Files.readString(root.resolve(role + "-result.json")), Map.class);
                if (command.equals(previous.get("command"))) return;
            }
            if (++waiting > 60) throw new AssertionError("Command exceeded 600 client ticks: " + command);
            var action = command.substring(command.indexOf(' ') + 1);
            if (action.equals("EXIT")) {
                result(root, role, "PASS", mc); terminal = true; mc.stop(); return;
            }
            if (action.equals("DISCONNECT")) {
                if (!sent) {
                    if (mc.level != null) mc.level.disconnect();
                    mc.clearLevel(); mc.setScreen(new TitleScreen()); sent = true;
                }
                if (mc.player == null && mc.level == null) result(root, role, "PASS", mc);
                return;
            }
            if (action.equals("CONNECT") && !sent) {
                var address = System.getProperty("ae2lt.networkAddress");
                if (!address.startsWith("127.0.0.1:")) throw new AssertionError("Not a loopback test server");
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                        new ServerData("AE2LT disposable QA", address, false), false);
                sent = true;
            }
            if (mc.player == null || mc.level == null) return;
            if (mc.getSingleplayerServer() != null) throw new AssertionError("Expected an independent dedicated server");
            if (action.equals("CONNECTED") || action.equals("CONNECT")) {
                result(root, role, "PASS", mc); return;
            }
            if (action.equals("OPEN")) {
                if (!sent) {
                    if (mc.level.getBlockEntity(POS) == null) return;
                    mc.player.getInventory().selected = 8;
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(POS), Direction.SOUTH, POS, false));
                    sent = true;
                }
                if (mc.screen instanceof OverloadedIOPortScreen) result(root, role, "PASS", mc);
                return;
            }
            if (!(mc.screen instanceof OverloadedIOPortScreen screen)) return;
            var menu = screen.getMenu();
            var filter = menu.getSlots(Ae2ltSlotSemantics.OVERLOADED_IO_FILTER).get(0);
            var matrix = menu.getSlots(Ae2ltSlotSemantics.OVERLOADED_IO_MATRIX).get(0);
            switch (action) {
                case "BASE_EMPTY" -> {
                    if (base(menu) && filter.getItem().isEmpty() && matrix.getItem().isEmpty()
                            && menu.operation == OperationMode.EMPTY) result(root, role, "PASS", mc);
                }
                case "BASE_FILLED" -> {
                    if (base(menu) && filter.getItem().isEmpty() && matrix.getItem().isEmpty()
                            && menu.operation == OperationMode.FILL) result(root, role, "PASS", mc);
                }
                case "INSERT" -> {
                    if (!sent) {
                        var playerFilter = menu.slots.stream().filter(s -> s.getItem().is(ModItems.OVERLOADED_FILTER_COMPONENT.get())).findFirst().orElseThrow();
                        var playerMatrices = menu.slots.stream().filter(s -> s.getItem().is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get())).findFirst().orElseThrow();
                        click(mc, menu, playerFilter.index); click(mc, menu, playerMatrices.index); sent = true;
                    }
                    if (expanded(menu) && filter.getItem().getCount() == 1 && matrix.getItem().getCount() == 8
                            && mc.player.getInventory().countItem(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()) == 56)
                        result(root, role, "PASS", mc);
                }
                case "EXPANDED" -> {
                    if (expanded(menu) && filter.getItem().getCount() == 1 && matrix.getItem().getCount() == 8)
                        result(root, role, "PASS", mc);
                }
                case "TOGGLE" -> {
                    if (!sent) { screen.mouseClicked(screen.getGuiLeft() + 88, screen.getGuiTop() + 25, 0); sent = true; }
                    if (menu.operation == OperationMode.FILL) result(root, role, "PASS", mc);
                }
                case "FILLED" -> { if (menu.operation == OperationMode.FILL) result(root, role, "PASS", mc); }
                case "REMOVE" -> {
                    if (!sent) { click(mc, menu, filter.index); click(mc, menu, matrix.index); sent = true; }
                    if (base(menu) && filter.getItem().isEmpty() && matrix.getItem().isEmpty() && ledger(mc))
                        result(root, role, "PASS", mc);
                }
                case "LEDGER" -> { if (ledger(mc)) result(root, role, "PASS", mc); }
                default -> throw new AssertionError("Unknown client command: " + command);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { result(root, role, "FAIL: " + failure, mc); } catch (Exception ignored) { }
            terminal = true; mc.stop();
        }
    }

    private static boolean base(OverloadedIOPortMenu menu) { return menu.batchLimit == 4 && menu.transferCap == 8_388_608L; }
    private static boolean expanded(OverloadedIOPortMenu menu) { return menu.batchLimit == 16 && menu.transferCap == Long.MAX_VALUE; }
    private static boolean ledger(Minecraft mc) {
        return mc.player.getInventory().countItem(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()) == 64
                && mc.player.getInventory().countItem(ModItems.OVERLOADED_FILTER_COMPONENT.get()) == 1;
    }
    private static void click(Minecraft mc, OverloadedIOPortMenu menu, int slot) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
    }
    private static void result(Path root, String role, String status, Minecraft mc) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("command", command); data.put("status", status); data.put("role", role);
        data.put("singleplayer", mc.getSingleplayerServer() != null);
        data.put("screen", mc.screen == null ? "null" : mc.screen.getClass().getName());
        if (mc.player != null) {
            data.put("uuid", mc.player.getUUID().toString());
            data.put("matrices", mc.player.getInventory().countItem(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
            data.put("filters", mc.player.getInventory().countItem(ModItems.OVERLOADED_FILTER_COMPONENT.get()));
            if (mc.player.containerMenu instanceof OverloadedIOPortMenu menu) {
                data.put("containerId", menu.containerId); data.put("batchLimit", menu.batchLimit);
                data.put("transferCap", menu.transferCap); data.put("operation", menu.operation.name());
                Screenshot.grab(mc.gameDirectory, "network-" + role + "-" + command.replace(' ', '-') + ".png",
                        mc.getMainRenderTarget(), ignored -> {});
            }
        }
        Files.writeString(root.resolve(role + "-result.json"), JSON.toJson(data));
        System.out.println("DEDICATED_MENU_CLIENT " + JSON.toJson(data));
    }
}
