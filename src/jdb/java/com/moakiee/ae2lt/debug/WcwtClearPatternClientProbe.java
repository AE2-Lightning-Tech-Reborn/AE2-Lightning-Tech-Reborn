package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.MEStorageMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import com.moakiee.ae2lt.crafting.big.BigTerminalMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.core.storage.big.BigStorageOps;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real WCWT button, menu and packet regression in a disposable integrated-server world.
 * Reflection keeps the optional WCWT dependency out of normal builds and release jars.
 */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class WcwtClearPatternClientProbe {
    private static final BlockPos BASE = new BlockPos(0, 100, 0);
    private static final BigInteger STOCK = BigInteger.TEN.pow(30).add(BigInteger.valueOf(123));
    private static DriveBlockEntity drive;
    private static WirelessAccessPointBlockEntity access;
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.wcwtClearProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks % 40 != 0) return;
        try {
            require(ticks < 1800, "client probe timed out");
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            if (serverFailure != null) throw new AssertionError("server fixture/action failed", serverFailure);
            switch (phase++) {
                case 0 -> {
                    record("Versions: " + ModList.get().getMods().stream()
                            .filter(m -> Set.of("ae2", "ae2lt", "wcwt", "ae2wtlib", "neoforge", "thunderbolt").contains(m.getModId()))
                            .map(m -> m.getModId() + "=" + m.getVersion()).toList());
                    mc.setScreen(null);
                    server(p -> {
                        var level = p.serverLevel();
                        level.getChunkAt(BASE);
                        level.setBlockAndUpdate(BASE, AEBlocks.DRIVE.block().defaultBlockState());
                        level.setBlockAndUpdate(BASE.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(BASE.offset(2, 0, 0), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                        drive = (DriveBlockEntity) level.getBlockEntity(BASE);
                        access = (WirelessAccessPointBlockEntity) level.getBlockEntity(BASE.offset(2, 0, 0));
                        drive.getInternalInventory().setItemDirect(0, new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
                        p.setGameMode(GameType.CREATIVE);
                        p.teleportTo(level, 0.5, 103, 2.5, Set.of(), 0, 0);
                        p.getAbilities().flying = true;
                        p.onUpdateAbilities();
                    });
                }
                case 1 -> server(p -> {
                    GridHelper.createConnection(drive.getMainNode().getNode(), access.getMainNode().getNode());
                    require(BigStorageOps.insert(drive.getMainNode().getGrid().getStorageService().getInventory(),
                            key(), STOCK, Actionable.MODULATE, IActionSource.empty()).equals(STOCK), "big stock insertion");
                });
                case 2 -> server(p -> {
                    try {
                        var supplier = (Supplier<?>) Class.forName("com.lhy.wcwt.init.ModItems")
                                .getField("WIRELESS_COMPREHENSIVE_WORK_TERMINAL").get(null);
                        var item = (WirelessTerminalItem) supplier.get();
                        var stack = new ItemStack(item);
                        WirelessTerminalItem.LINKABLE_HANDLER.link(stack, GlobalPos.of(p.level().dimension(), access.getBlockPos()));
                        item.injectAEPower(stack, item.getAEMaxPower(stack), Actionable.MODULATE);
                        p.getInventory().setItem(0, stack);
                        p.inventoryMenu.broadcastChanges();
                        var type = (MenuType<?>) Class.forName("com.lhy.wcwt.init.ModMenus").getField("WCWT_MENU_TYPE").get(null);
                        require(MenuOpener.open(type, p, MenuLocators.forInventorySlot(0)), "WCWT menu did not open");
                        p.containerMenu.getClass().getMethod("setPatternEncodingMode", EncodingMode.class)
                                .invoke(p.containerMenu, EncodingMode.PROCESSING);
                        seed(logic(p.containerMenu));
                        p.containerMenu.broadcastChanges();
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                case 3 -> {
                    var menu = menu();
                    require(menu.isClientSide(), "expected a real client menu");
                    require(STOCK.equals(((BigTerminalMenu) menu).ae2lt$getBigStock(key())), "initial exact stock packet missing");
                    var getter = MEStorageMenu.class.getDeclaredMethod("getPreviousAvailableStacks");
                    getter.setAccessible(true);
                    try { getter.invoke(menu); throw new AssertionError("AE2 getter did not reject client access"); }
                    catch (InvocationTargetException expected) {
                        require(expected.getCause() instanceof IllegalStateException, "unexpected getter failure");
                    }
                    var draft = logic(menu);
                    seed(draft);
                    var field = mc.screen.getClass().getDeclaredField("clearPatternEncodingButton");
                    field.setAccessible(true);
                    var button = (AbstractButton) field.get(mc.screen);
                    for (int i = 0; i < 3; i++) {
                        seed(draft);
                        button.onPress();
                        require(draft.getEncodedInputInv().getStack(0) == null
                                && draft.getEncodedOutputInv().getStack(0) == null, "client draft did not clear");
                    }
                    record("PASS: real WCWT clear button x3; client getter rejects direct access; no crash");
                }
                case 4 -> server(p -> {
                    try {
                        var draft = logic(p.containerMenu);
                        require(draft.getEncodedInputInv().getStack(0) == null
                                && draft.getEncodedOutputInv().getStack(0) == null, "clear packet did not reach server");
                        require(STOCK.equals(((BigTerminalMenu) p.containerMenu).ae2lt$getBigStock(key())), "server stock missing");
                        require(BigStorageOps.extract(drive.getMainNode().getGrid().getStorageService().getInventory(),
                                key(), STOCK.subtract(BigInteger.valueOf(37)), Actionable.MODULATE,
                                IActionSource.empty()).equals(STOCK.subtract(BigInteger.valueOf(37))), "stock reduction");
                        record("PASS: clear reached server; server exact stock retained");
                    } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                });
                case 5 -> {
                    var menu = menu();
                    require(((BigTerminalMenu) menu).ae2lt$getBigStock(key()) == null, "stale exact stock not removed");
                    require(menu.getClientRepo().getAllEntries().stream()
                            .anyMatch(e -> key().equals(e.getWhat()) && e.getStoredAmount() == 37), "native stock delta missing");
                    server(p -> require(BigStorageOps.insert(drive.getMainNode().getGrid().getStorageService().getInventory(),
                            key(), STOCK.subtract(BigInteger.valueOf(37)), Actionable.MODULATE,
                            IActionSource.empty()).equals(STOCK.subtract(BigInteger.valueOf(37))), "stock restoration"));
                }
                case 6 -> {
                    require(STOCK.equals(((BigTerminalMenu) menu()).ae2lt$getBigStock(key())), "restored exact stock packet missing");
                    record("PASS: exact stock delta removed at 37 and restored above Long.MAX_VALUE in same open WCWT menu");
                    record("PASS: WCWT regression complete");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("unexpected phase");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            record("FAIL: phase=" + (phase - 1) + " " + failure);
            done = true;
            mc.stop();
        }
    }

    private static MEStorageMenu menu() {
        var menu = Minecraft.getInstance().player.containerMenu;
        require(menu.getClass().getName().equals("com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu"), "WCWT menu missing");
        return (MEStorageMenu) menu;
    }
    private static PatternEncodingLogic logic(Object menu) throws ReflectiveOperationException {
        var host = menu.getClass().getMethod("getMenuHost").invoke(menu);
        return (PatternEncodingLogic) host.getClass().getMethod("getLogic").invoke(host);
    }
    private static void seed(PatternEncodingLogic logic) {
        logic.getEncodedInputInv().setStack(0, new GenericStack(key(), 1));
        logic.getEncodedOutputInv().setStack(0, new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1));
    }
    private static AEItemKey key() { return AEItemKey.of(Items.IRON_INGOT); }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void record(String message) {
        System.out.println("WCWT_CLEAR_PROBE " + message);
        try {
            var path = Path.of(System.getProperty("ae2lt.wcwtClearReport"));
            Files.createDirectories(path.getParent());
            Files.writeString(path, message + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
    }
}
