package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.crafting.big.BigTerminalMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.core.storage.big.BigStorageOps;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Real client stock packets and tooltip rendering in a disposable world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class BigTerminalClientProbe {
    private static final BigInteger HUGE = BigInteger.TEN.pow(100).add(BigInteger.valueOf(12345));
    private static final AEItemKey KEY = AEItemKey.of(Items.IRON_INGOT);
    private static appeng.blockentity.storage.DriveBlockEntity drive;
    private static final appeng.parts.AEBasePart[] terminals = new appeng.parts.AEBasePart[2];
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static int serverTicks, ticks, phase, terminalIndex;
    private static boolean done, readingTooltip, exactTooltip;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.bigTerminalProbe") || ready) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty() || serverFailure != null) return;
        try {
            var player = server.getPlayerList().getPlayers().get(0);
            var level = player.serverLevel();
            if (++serverTicks == 1) {
                var pos = new BlockPos(10, 110, 10);
                level.setBlockAndUpdate(pos, AEBlocks.DRIVE.block().defaultBlockState());
                level.setBlockAndUpdate(pos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(pos);
                drive.getInternalInventory().setItemDirect(0, new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
                terminals[0] = PartHelper.setPart(level, pos.east(2), Direction.NORTH, player, AEParts.TERMINAL.asItem());
                terminals[1] = PartHelper.setPart(level, pos.east(4), Direction.NORTH, player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.teleportTo(10, 112, 8);
            } else if (serverTicks == 30) {
                for (var terminal : terminals)
                    GridHelper.createConnection(drive.getMainNode().getNode(), terminal.getMainNode().getNode());
            } else if (serverTicks == 60) {
                require(BigStorageOps.insert(drive.getMainNode().getGrid().getStorageService().getInventory(), KEY,
                        HUGE, Actionable.MODULATE, IActionSource.empty()).equals(HUGE), "fixture stock");
                ready = true;
            }
        } catch (Throwable failure) { serverFailure = failure; }
    }

    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.bigTerminalProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null) return;
        try {
            if (serverFailure != null) throw new AssertionError("server fixture", serverFailure);
            require(++ticks < 1600, "client probe timeout, phase=" + phase);
            if (!ready || ticks % 40 != 0) return;
            if (phase == 0) {
                server(p -> appeng.menu.MenuOpener.open(terminalIndex == 0 ? MEStorageMenu.TYPE
                        : com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu.TYPE, p,
                        appeng.menu.locator.MenuLocators.forPart(terminals[terminalIndex])));
            } else {
                require(mc.screen instanceof MEStorageScreen<?>, "native terminal screen");
                var menu = ((MEStorageScreen<?>) mc.screen).getMenu();
                require(menu instanceof BigTerminalMenu, "native menu mixin");
                var entry = menu.getClientRepo().getAllEntries().stream()
                        .filter(e -> KEY.equals(e.getWhat())).findFirst().orElseThrow();
                var exact = ((BigTerminalMenu) menu).ae2lt$getBigStock(KEY);
                if (phase == 1 || phase == 3) {
                    require(HUGE.equals(exact) && entry.getStoredAmount() == Long.MAX_VALUE, "exact stock packet");
                    if (phase == 1) {
                        require(exactTooltip, "native tooltip must contain every exact digit");
                        Screenshot.grab(mc.gameDirectory, "exact-terminal-" + terminalIndex + ".png", mc.getMainRenderTarget(), ignored -> {});
                        server(p -> require(BigStorageOps.extract(drive.getMainNode().getGrid().getStorageService().getInventory(),
                                KEY, HUGE.subtract(BigInteger.valueOf(37)), Actionable.MODULATE, IActionSource.empty())
                                .equals(HUGE.subtract(BigInteger.valueOf(37))), "lower stock"));
                    } else {
                        terminalIndex++;
                        exactTooltip = false;
                        if (terminalIndex == terminals.length) {
                            Files.writeString(Path.of("big-terminal-result.txt"), "PASS: native and Tianshu terminals; exact stock packets, full rendered tooltip, long cache removal and exact restoration.\n");
                            finish();
                            return;
                        }
                        phase = -1;
                    }
                } else if (phase == 2) {
                    require(exact == null && entry.getStoredAmount() == 37, "stale exact cache after long decrease");
                    server(p -> require(BigStorageOps.insert(drive.getMainNode().getGrid().getStorageService().getInventory(), KEY,
                            HUGE.subtract(BigInteger.valueOf(37)), Actionable.MODULATE, IActionSource.empty())
                            .equals(HUGE.subtract(BigInteger.valueOf(37))), "restore exact stock"));
                }
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("big-terminal-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            finish();
        }
    }

    @SubscribeEvent
    public static void render(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("ae2lt.bigTerminalProbe") || phase != 1 || done
                || !(event.getScreen() instanceof MEStorageScreen<?> screen)) return;
        var entry = screen.getMenu().getClientRepo().getAllEntries().stream()
                .filter(e -> KEY.equals(e.getWhat())).findFirst().orElse(null);
        if (entry == null) return;
        try {
            readingTooltip = true;
            for (var slot : screen.getMenu().slots) {
                if (slot instanceof gto_ae.hooks.gui.menu.IRepoSlot repoSlot
                        && repoSlot.getEntry() != null && KEY.equals(repoSlot.getEntry().getWhat())) {
                    // Render the actual GTO screen at the entry's mouse position. This
                    // reaches MEStorageScreen.renderTooltip and LT's production wrapper.
                    screen.render(event.getGuiGraphics(), screen.getGuiLeft() + slot.x + 8,
                            screen.getGuiTop() + slot.y + 8, 0);
                    break;
                }
            }
        } catch (Throwable failure) { serverFailure = failure; }
        finally { readingTooltip = false; }
    }

    @SubscribeEvent
    public static void tooltip(net.minecraftforge.client.event.RenderTooltipEvent.GatherComponents event) {
        if (!readingTooltip) return;
        var text = event.getTooltipElements().stream().flatMap(e -> e.left().stream())
                .map(net.minecraft.network.chat.FormattedText::getString).collect(java.util.stream.Collectors.joining()).replace(",", "");
        exactTooltip |= text.contains(HUGE.toString());
    }

    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void finish() {
        done = true;
        Minecraft.getInstance().setScreen(null);
        Minecraft.getInstance().stop();
    }
}
