package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.*;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.crafting.*;
import appeng.core.definitions.AEParts;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.crafting.*;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.client.widgets.BigNumberEntry;
import com.moakiee.ae2lt.crafting.big.*;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.storage.big.BigStorageOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Opt-in client acceptance: native widgets and packets in a disposable world; never shipped. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class BigIntegerClientAcceptanceProbe {
    public static final BigInteger RAW = BigInteger.TEN.pow(100).add(BigInteger.valueOf(12345));
    public static final BigInteger ORDER = RAW.multiply(BigInteger.valueOf(4));
    private static final Set<String> emitted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final appeng.parts.AEBasePart[] terminals = new appeng.parts.AEBasePart[3];
    private static TianshuSupercomputerControllerBlockEntity cpu;
    private static appeng.blockentity.storage.DriveBlockEntity drive;
    private static volatile int setup;
    private static int ticks, failures;
    private static long setupAt;
    private static UUID seenJob;
    private static volatile String currentCase = "unknown";
    private static volatile boolean waitingRefund;
    private static volatile boolean completedImmediately;
    private static boolean readingTerminalTooltip;
    private static int actionPhase, caseIndex;
    private static boolean checkedBack, finished;
    private static volatile Throwable actionFailure;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void server(java.util.function.Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)); }
            catch (Throwable e) { actionFailure = e; }
        });
    }

    private static appeng.menu.locator.MenuHostLocator locator() {
        return appeng.menu.locator.MenuLocators.forPart(terminals[caseIndex]);
    }

    private static void openTerminal(ServerPlayer p) {
        switch (caseIndex) {
            case 0 -> appeng.menu.MenuOpener.open(MEStorageMenu.TYPE, p, locator());
            case 1 -> appeng.menu.MenuOpener.open(com.moakiee.ae2lt.menu.TianshuCraftingTermMenu.TYPE, p, locator());
            case 2 -> appeng.menu.MenuOpener.open(com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu.TYPE, p, locator());
        }
    }

    private static void actions() throws Exception {
        if (!Boolean.getBoolean("ae2lt.bigClientActions") || finished || ticks % 30 != 0) return;
        var mc = Minecraft.getInstance();
        if (actionFailure != null) throw new AssertionError("server action", actionFailure);
        System.out.println("BIG_CLIENT_ACTION case=" + caseIndex + " phase=" + actionPhase + " screen=" + mc.screen);
        switch (actionPhase) {
            case 0 -> server(BigIntegerClientAcceptanceProbe::openTerminal);
            case 1 -> {
                require(mc.screen instanceof MEStorageScreen<?>, "terminal missing");
                var screen = (MEStorageScreen<?>) mc.screen;
                var entry = screen.getMenu().getClientRepo().getAllEntries().stream()
                        .filter(e -> e.getWhat().equals(outKey()) && e.isCraftable()).findFirst().orElseThrow();
                var click = MEStorageScreen.class.getDeclaredMethod("handleGridInventoryEntryMouseClick",
                        appeng.menu.me.common.GridInventoryEntry.class, int.class, net.minecraft.world.inventory.ClickType.class);
                click.setAccessible(true);
                click.invoke(screen, entry, 0, net.minecraft.world.inventory.ClickType.PICKUP);
            }
            case 2 -> {
                require(mc.screen instanceof CraftAmountScreen, "amount screen missing");
                var f = CraftAmountScreen.class.getDeclaredField("amountToCraft"); f.setAccessible(true);
                var widget = (BigNumberEntry) f.get(mc.screen);
                if (checkedBack) record("back", widget.ae2lt$getBig().filter(ORDER::equals).isPresent(), "exact input restored");
                widget.ae2lt$setBig(ORDER);
                var add = appeng.client.gui.widgets.NumberEntryWidget.class.getDeclaredMethod("addQty", long.class);
                add.setAccessible(true); add.invoke(widget, 1L);
                record("increment", widget.ae2lt$getBig().filter(ORDER.add(BigInteger.ONE)::equals).isPresent(), "+1 exact");
                widget.ae2lt$setBig(ORDER);
            }
            case 3 -> {
                var confirm = CraftAmountScreen.class.getDeclaredMethod("confirm");
                confirm.setAccessible(true); confirm.invoke(mc.screen);
            }
            case 4 -> {
                require(mc.screen instanceof CraftConfirmScreen, "confirm screen missing");
                var m = ((CraftConfirmScreen) mc.screen).getMenu();
                require(m.getPlan() != null && !m.getPlan().isSimulation(), "plan not executable");
                if (!checkedBack) {
                    checkedBack = true; m.goBack(); actionPhase = 1;
                } else { m.cycleSelectedCPU(true); m.startJob(); }
            }
            case 5 -> server(p -> {
                if (cpu.bigCrafting().busy()) {
                    appeng.menu.MenuOpener.open(CraftingStatusMenu.TYPE, p, locator());
                } else {
                    // The 0t matrix may settle before the post-tick observer can suspend it.
                    // Prove completion from exact inventory deltas before restoring the fixture.
                    var storage = cpu.getGrid().getStorageService().getInventory();
                    var stock = BigStorageOps.snapshot(storage, IActionSource.empty());
                    var raw = stock.getOrDefault(rawKey(), BigInteger.ZERO);
                    var output = stock.getOrDefault(outKey(), BigInteger.ZERO);
                    require(raw.signum() == 0 && output.equals(ORDER),
                            "job missing without exact completion: raw=" + raw + " output=" + output);
                    record("submit-completed", true, "0t completion: raw=" + raw + " output=" + output);
                    require(BigStorageOps.extract(storage, outKey(), ORDER, Actionable.MODULATE, IActionSource.empty()).equals(ORDER),
                            "completed fixture output removal");
                    require(BigStorageOps.insert(storage, rawKey(), RAW, Actionable.MODULATE, IActionSource.empty()).equals(RAW),
                            "completed fixture input restoration");
                    completedImmediately = true;
                }
            });
            case 6 -> {
                if (completedImmediately) {
                    server(BigIntegerClientAcceptanceProbe::openTerminal);
                    actionPhase = 8;
                } else {
                    require(mc.screen instanceof CraftingStatusScreen, "status screen missing");
                    var m = ((CraftingStatusScreen) mc.screen).getMenu();
                    var entry = m.cpuList.cpus().stream().filter(c -> c.currentJob() != null).findFirst().orElseThrow();
                    m.selectCpu(entry.serial());
                }
            }
            case 7 -> {
                require(emitted.contains(currentCase + ":status"), "status exact packet missing");
                ((CraftingStatusScreen) mc.screen).getMenu().cancelCrafting();
            }
            case 8 -> server(p -> {
                require(!cpu.bigCrafting().busy(), "cancel did not clear job");
                openTerminal(p);
            });
            case 9 -> {
                require(completedImmediately || emitted.contains(currentCase + ":refund"), "refund not observed");
                screenshot("refunded-terminal");
                server(p -> {
                    var removed = BigStorageOps.extract(cpu.getGrid().getStorageService().getInventory(), rawKey(),
                            RAW.subtract(BigInteger.valueOf(37)), Actionable.MODULATE, IActionSource.empty());
                    require(removed.equals(RAW.subtract(BigInteger.valueOf(37))), "lower-to-long extraction failed");
                });
            }
            case 10 -> {
                var m = ((MEStorageScreen<?>) mc.screen).getMenu();
                var entry = m.getClientRepo().getAllEntries().stream().filter(e -> e.getWhat().equals(rawKey())).findFirst().orElseThrow();
                var exact = ((BigTerminalMenu) m).ae2lt$getBigStock(rawKey());
                record("lower-to-long", exact == null && entry.getStoredAmount() == 37, "same open menu: exact=" + exact + " native=" + entry.getStoredAmount());
                server(p -> {
                    var restored = BigStorageOps.insert(cpu.getGrid().getStorageService().getInventory(), rawKey(),
                            RAW.subtract(BigInteger.valueOf(37)), Actionable.MODULATE, IActionSource.empty());
                    require(restored.equals(RAW.subtract(BigInteger.valueOf(37))), "restore-big insertion failed");
                });
            }
            case 11 -> {
                var m = ((MEStorageScreen<?>) mc.screen).getMenu();
                var exact = ((BigTerminalMenu) m).ae2lt$getBigStock(rawKey());
                record("restore-big", RAW.equals(exact), "same open menu restored=" + exact);
            }
            case 12 -> {
                for (String stage : List.of("stock", "terminal-tooltip", "increment", "amount", "confirm", "confirm-renderer",
                        "confirm-bytes", "confirm-bytes-label", "back", "refunded-stock", "lower-to-long", "restore-big"))
                    require(emitted.contains(currentCase + ":" + stage), "missing observation: " + stage);
                for (String stage : completedImmediately ? List.of("submit-completed")
                        : List.of("submit", "status", "status-renderer", "refund"))
                    require(emitted.contains(currentCase + ":" + stage), "missing observation: " + stage);
                record("case-complete", true, "native terminal -> amount -> confirm/back -> start -> "
                        + (completedImmediately ? "exact 0t completion" : "status -> cancel")
                        + " -> terminal, including live cache removal");
                caseIndex++;
                if (caseIndex == 3) {
                    record("all-complete", failures == 0, "three terminal flows complete; failed checks=" + failures);
                    finished = true;
                    mc.stop();
                } else { checkedBack = false; completedImmediately = false; actionPhase = -1; }
            }
        }
        actionPhase++;
    }

    private static boolean enabled() { return Boolean.getBoolean("ae2lt.bigClientAcceptance"); }
    private static AEKey rawKey() { return AEItemKey.of(Items.OAK_LOG); }
    private static AEKey outKey() { return AEItemKey.of(Items.OAK_PLANKS); }

    private static synchronized void record(String stage, boolean ok, String detail) {
        String identity = currentCase + ":" + stage;
        if (!emitted.add(identity)) return;
        if (!ok) failures++;
        String line = "BIG_CLIENT " + (ok ? "PASS" : "FAIL") + " " + identity + " " + detail + "\n";
        System.out.print(line);
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("acceptance-results.txt"),
                    line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Object fixtureField(String name) throws Exception {
        var field = BigIntegerNativeUiProbe.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (!enabled()) return;
        try {
            var server = event.getServer();
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.serverLevel();
            if (setup == 0) {
                // The existing fixture builder writes only this isolated test world's marked area.
                var build = BigIntegerNativeUiProbe.class.getDeclaredMethod("build", net.minecraft.server.level.ServerLevel.class);
                build.setAccessible(true);
                build.invoke(null, level);
                cpu = (TianshuSupercomputerControllerBlockEntity) fixtureField("cpu");
                drive = (appeng.blockentity.storage.DriveBlockEntity) fixtureField("drive");
                for (int x = 10; x <= 18; x++) for (int z = 8; z <= 13; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 108, z), Blocks.SMOOTH_STONE.defaultBlockState());
                player.setGameMode(GameType.CREATIVE);
                player.getInventory().clearContent();
                player.teleportTo(level, 12.5, 109, 9.5, Set.of(), 0, 0);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                setupAt = level.getGameTime();
                setup = 1;
            } else if (setup == 1 && level.getGameTime() - setupAt >= 30) {
                cpu.scanNow();
                var matrix = (MatrixControllerBlockEntity) fixtureField("matrix");
                matrix.scanAndForm(player);
                if (!cpu.isFormed() || !matrix.isBigCraftingAvailable()) throw new AssertionError("fixture formation failed");
                var cp = (TianshuSupercomputerPortBlockEntity) level.getBlockEntity(cpu.getPortPos());
                var mp = (MatrixPortBlockEntity) level.getBlockEntity(
                        com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner.worldPos(matrix.getBlockPos(),
                                new BlockPos(6, 5, 3), Direction.EAST));
                GridHelper.createConnection(drive.getMainNode().getNode(), cp.getMainNode().getNode());
                GridHelper.createConnection(drive.getMainNode().getNode(), mp.getMainNode().getNode());
                terminals[0] = PartHelper.setPart(level, new BlockPos(12, 110, 12), Direction.NORTH, player, AEParts.TERMINAL.asItem());
                terminals[1] = PartHelper.setPart(level, new BlockPos(14, 110, 12), Direction.NORTH, player, ModItems.TIANSHU_CRAFTING_TERMINAL.get());
                terminals[2] = PartHelper.setPart(level, new BlockPos(16, 110, 12), Direction.NORTH, player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
                setupAt = level.getGameTime();
                setup = 2;
            } else if (setup == 2 && level.getGameTime() - setupAt >= 30) {
                for (var terminal : terminals)
                    GridHelper.createConnection(drive.getMainNode().getNode(), terminal.getMainNode().getNode());
                setupAt = level.getGameTime();
                setup = 3;
            } else if (setup == 3 && level.getGameTime() - setupAt >= 30) {
                var stored = BigStorageOps.insert(cpu.getGrid().getStorageService().getInventory(), rawKey(), RAW,
                        Actionable.MODULATE, IActionSource.empty());
                record("fixture", stored.equals(RAW), "ready: terminals x=12/14/16 y=110 z=12 raw=" + stored + " order=" + ORDER);
                setup = 4;
            }
            if (setup < 4) return;
            var job = cpu.bigCrafting().job();
            if (job != null && !job.returning) {
                // Hold the pending job so all three screens can be inspected before cancellation.
                job.suspended = true;
                if (!job.id.equals(seenJob)) {
                    seenJob = job.id;
                    waitingRefund = true;
                    var escrow = job.escrow.getOrDefault(rawKey(), BigInteger.ZERO);
                    record("submit", job.amount.equals(ORDER) && escrow.equals(RAW),
                            "job=" + job.id + " amount=" + job.amount + " escrow=" + escrow);
                }
            } else if (job == null && waitingRefund) {
                waitingRefund = false;
                var stock = BigStorageOps.snapshot(cpu.getGrid().getStorageService().getInventory(), IActionSource.empty());
                var raw = stock.getOrDefault(rawKey(), BigInteger.ZERO);
                var output = stock.getOrDefault(outKey(), BigInteger.ZERO);
                record("refund", raw.equals(RAW) && output.signum() == 0, "raw=" + raw + " output=" + output);
            }
        } catch (Throwable e) {
            record("server-error", false, e.toString());
            e.printStackTrace();
            setup = -1000000;
        }
    }

    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (!enabled() || finished || ++ticks % 10 != 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || setup < 4) return;
        try {
            if (mc.screen instanceof MEStorageScreen<?> screen) {
                var menu = screen.getMenu();
                currentCase = menu.getHost().getClass().getSimpleName();
                var nativeEntry = menu.getClientRepo().getAllEntries().stream().filter(e -> e.getWhat().equals(rawKey())).findFirst().orElse(null);
                if (nativeEntry == null) return;
                BigInteger precise = null;
                if (menu instanceof TianshuMaintenanceMenu m) precise = m.getBigStock(rawKey());
                else {
                    try { precise = (BigInteger) menu.getClass().getMethod("ae2lt$getBigStock", AEKey.class).invoke(menu, rawKey()); }
                    catch (NoSuchMethodException unavailable) { }
                }
                if (precise == null && menu instanceof TianshuMaintenanceMenu) return;
                record(actionPhase >= 9 ? "refunded-stock" : "stock", RAW.equals(precise),
                        "clientExact=" + precise + " nativeLong=" + nativeEntry.getStoredAmount());
                screenshot(actionPhase >= 9 ? "refunded-terminal" : "terminal");
            } else if (mc.screen instanceof CraftAmountScreen screen) {
                var f = CraftAmountScreen.class.getDeclaredField("amountToCraft"); f.setAccessible(true);
                var widget = (BigNumberEntry) f.get(screen);
                if (widget.ae2lt$getBig().filter(ORDER::equals).isPresent()) {
                    record("amount", ((BigAmountMenu) screen.getMenu()).ae2lt$bigAvailable(), "clientInput=" + widget.ae2lt$getBig().orElseThrow());
                    screenshot("amount");
                }
            } else if (mc.screen instanceof CraftConfirmScreen screen) {
                var menu = screen.getMenu();
                var report = ExactPlanReports.get(menu.getPlan());
                if (report == null) return;
                var input = report.entries().get(rawKey());
                var output = report.entries().get(outKey());
                record("confirm", menu instanceof com.moakiee.thunderbolt.api.crafting.CraftingRequestMenu m && m.thunderbolt$isExactRequest()
                                && !menu.getPlan().isSimulation() && input != null && RAW.equals(input.stored())
                                && output != null && ORDER.equals(output.crafting()) && !report.previewOnly(),
                        "screen=" + screen.getClass().getSimpleName() + " raw=" + input + " output=" + output);
                var expectedBytes = RAW.multiply(BigInteger.valueOf(6)).add(BigInteger.valueOf(16));
                record("confirm-bytes", expectedBytes.equals(report.bytes())
                                && menu.getPlan().getUsedBytes() == Long.MAX_VALUE,
                        "expected=" + expectedBytes + " actual=" + report.bytes());
                var update = screen.getClass().getDeclaredMethod("updateBeforeRender");
                update.setAccessible(true);
                update.invoke(screen);
                var texts = appeng.client.gui.AEBaseScreen.class.getDeclaredField("textOverrides");
                texts.setAccessible(true);
                var bytesText = ((Map<String, appeng.client.gui.TextOverride>) texts.get(screen)).get("bytes_used");
                var expectedText = net.minecraft.network.chat.Component.translatable("gui.ae2lt.crafting_report.exact_bytes",
                        com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter.compact(expectedBytes, 1));
                record("confirm-bytes-label", bytesText != null && expectedText.equals(bytesText.getContent()),
                        "expected=" + expectedText.getString() + " actual=" + (bytesText == null ? null : bytesText.getContent()));
                var tableField = screen.getClass().getDeclaredField("table"); tableField.setAccessible(true);
                verifyRenderer("confirm-renderer", tableField.get(screen), menu.getPlan().getEntries(), CraftingPlanSummaryEntry.class);
                screenshot("confirm");
            } else if (mc.screen instanceof CraftingStatusScreen screen) {
                var f = CraftingCPUScreen.class.getDeclaredField("status"); f.setAccessible(true);
                var status = (CraftingStatus) f.get(screen);
                if (status == null || status.getEntries().isEmpty()) return;
                var raw = status.getEntries().stream().filter(e -> e.getWhat().equals(rawKey())).findFirst().orElse(null);
                var output = status.getEntries().stream().filter(e -> e.getWhat().equals(outKey())).findFirst().orElse(null);
                if (raw == null || output == null) return;
                var r = ((BigStatusEntry) raw).ae2lt$amounts();
                var o = ((BigStatusEntry) output).ae2lt$amounts();
                record("status", r != null && o != null && RAW.equals(r.stored()) && ORDER.equals(o.active().add(o.pending())),
                        "raw=" + r + " output=" + o);
                var tableField = CraftingCPUScreen.class.getDeclaredField("table"); tableField.setAccessible(true);
                verifyRenderer("status-renderer", tableField.get(screen), status.getEntries(), CraftingStatusEntry.class);
                screenshot("status");
            }
                } catch (Throwable e) { record("client-error", false, "phase=" + actionPhase + " " + e); e.printStackTrace(); finished = true; mc.stop(); }
        finally {
            try { actions(); }
            catch (Throwable e) { record("action-error", false, "phase=" + actionPhase + " " + e); e.printStackTrace(); finished = true; mc.stop(); }
        }
    }

    private static void verifyRenderer(String stage, Object table, Collection<?> entries, Class<?> entryClass) throws Exception {
        if (emitted.contains(currentCase + ":" + stage)) return;
        var description = table.getClass().getDeclaredMethod("getEntryDescription", entryClass); description.setAccessible(true);
        var tooltip = table.getClass().getDeclaredMethod("getEntryTooltip", entryClass); tooltip.setAccessible(true);
        boolean ok = true;
        var detail = new StringBuilder();
        for (var entry : entries) {
            var key = (AEKey) entryClass.getMethod("getWhat").invoke(entry);
            if (!key.equals(rawKey()) && !key.equals(outKey())) continue;
            BigInteger expected = key.equals(rawKey()) ? RAW : ORDER;
            var shortLines = (List<net.minecraft.network.chat.Component>) description.invoke(table, entry);
            var fullLines = (List<net.minecraft.network.chat.Component>) tooltip.invoke(table, entry);
            String compact = shortLines.stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining());
            String full = fullLines.stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining()).replace(",", "");
            ok &= full.contains(expected.toString());
            detail.append(" compact=").append(compact).append(" fullExact=").append(full.contains(expected.toString()));
        }
        record(stage, ok, detail.toString());
    }

    @SubscribeEvent public static void renderTooltip(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if (!enabled() || !Boolean.getBoolean("ae2lt.bigClientActions") || finished || setup < 4) return;
        if (!(event.getScreen() instanceof MEStorageScreen<?> screen)) return;
        var m = screen.getMenu();
        if (!RAW.equals(((BigTerminalMenu) m).ae2lt$getBigStock(rawKey()))) return;
        var entry = m.getClientRepo().getAllEntries().stream().filter(e -> e.getWhat().equals(rawKey())).findFirst().orElse(null);
        if (entry == null) return;
        try {
            readingTerminalTooltip = true;
            var render = MEStorageScreen.class.getDeclaredMethod("renderGridInventoryEntryTooltip", net.minecraft.client.gui.GuiGraphics.class,
                    appeng.menu.me.common.GridInventoryEntry.class, int.class, int.class);
            render.setAccessible(true); render.invoke(screen, event.getGuiGraphics(), entry, 160, 40);
        } catch (Throwable e) { record("tooltip-error", false, e.toString()); }
        finally { readingTerminalTooltip = false; }
    }

    @SubscribeEvent public static void tooltipComponents(net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event) {
        if (!readingTerminalTooltip) return;
        String text = event.getTooltipElements().stream().flatMap(e -> e.left().stream())
                .map(net.minecraft.network.chat.FormattedText::getString).collect(java.util.stream.Collectors.joining()).replace(",", "");
        record("terminal-tooltip", text.contains(RAW.toString()), "native rendered tooltip includes all " + RAW.toString().length() + " digits=" + text.contains(RAW.toString()));
    }

    private static void screenshot(String stage) {
        String key = currentCase + ":screenshot:" + stage;
        synchronized (BigIntegerClientAcceptanceProbe.class) { if (!emitted.add(key)) return; }
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "acceptance-" + currentCase + "-" + stage + ".png", mc.getMainRenderTarget(), t -> {});
    }
}
