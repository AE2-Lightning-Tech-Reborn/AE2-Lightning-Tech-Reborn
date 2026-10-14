package com.moakiee.ae2lt.client.crafting;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import appeng.api.config.Actionable;
import appeng.api.config.TerminalStyle;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.MEStorage;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.me.crafting.CraftErrorScreen;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.AEConfig;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import com.moakiee.thunderbolt.compat.gto.inv.NetworkCraftingSimulationState;
import appeng.menu.ISubMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.thunderbolt.ae2.crafting.ThunderboltCraftingPlanSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

/** Opt-in native UI/Mixin acceptance in a disposable world. Excluded from release jars. */
@Mod.EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class CraftingReportClientProbe {
    private static int phase, ticks;
    private static boolean done;
    private static volatile boolean serverPassed;
    private static volatile Throwable serverFailure;
    private static AE2LtCraftConfirmScreen report;
    private static UiMenu menu;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.craftReportProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 30 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError("server checks", serverFailure);
            switch (phase) {
                case 0 -> {
                    mc.options.guiScale().set(2);
                    mc.getWindow().setWindowed(960, 720);
                    mc.resizeDisplay();
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            serverChecks(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));
                            serverPassed = true;
                        } catch (Throwable failure) { serverFailure = failure; }
                    });
                }
                case 1 -> {
                    if (!serverPassed) return;
                    AEConfig.instance().setTerminalStyle(TerminalStyle.MEDIUM);
                    menu = new UiMenu(mc.player.getInventory());
                    var used = new KeyCounter();
                    BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(36)
                            .forEach(item -> used.set(AEItemKey.of(item), 35));
                    used.set(AEItemKey.of(Items.IRON_ORE), 35);
                    menu.setPlan(ThunderboltCraftingPlanSummary.fromPlan(new CraftingPlan(
                            new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1), 435, false, false,
                            used, new KeyCounter(), new KeyCounter(), Map.of(treePattern(), 1L))));
                    mc.player.containerMenu = menu;
                    set(menu, "ae2lt$showReport", true);
                    mc.setScreen(new appeng.client.gui.me.crafting.CraftConfirmScreen(menu,
                            mc.player.getInventory(), Component.empty(),
                            StyleManager.loadStyleDoc("/screens/craft_confirm.json")));
                }
                case 2 -> {
                    require(mc.screen instanceof AE2LtCraftConfirmScreen, "native confirmation did not route to the report");
                    report = (AE2LtCraftConfirmScreen) mc.screen;
                    require(intField(report, "imageHeight") == 210, "medium must fit the supplied texture");
                    require(intField(report, "visibleRows") == 5, "medium must display five rows");
                    var start = ((com.moakiee.ae2lt.mixin.client.CraftConfirmScreenAccessor) (Object) report).ae2lt$getStart();
                    menu.noCPU = false;
                    start.onPress();
                    require(menu.retries == 1, "native start button did not reach the report submission path");
                    menu.noCPU = true;
                    start.onPress();
                    require(menu.retries == 1, "native start button bypassed the report CPU guard");
                    menu.retries = 0;
                    capture("medium.png");
                    styleButton().onPress();
                }
                case 3 -> {
                    require(intField(report, "visibleRows") == 7, "tall must display seven rows");
                    capture("tall.png");
                    styleButton().onPress();
                }
                case 4 -> {
                    require(intField(report, "imageHeight") <= report.height - 28, "full clipped at bottom");
                    capture("full.png");
                    styleButton().onPress();
                }
                case 5 -> {
                    require(intField(report, "visibleRows") == 3, "small must display three rows");
                    var scroll = ((com.moakiee.ae2lt.mixin.client.CraftConfirmScreenAccessor) (Object) report).ae2lt$getScrollbar();
                    int lastRow = (menu.getPlan().getEntries().size() + 2) / 3 - 3;
                    scroll.setCurrentScroll(lastRow);
                    report.updateBeforeRender();
                    require(scroll.getCurrentScroll() == lastRow, "native five-row clamp hid the small report's last rows");
                    capture("small.png");
                    styleButton().onPress();
                    var tree = buttons().stream().filter(b -> b.getClass().getName().equals("com.neuvillette.ae2ct.gui.ChangeButton")).findFirst();
                    if (tree.isPresent()) tree.get().onPress();
                    else phase = 6;
                }
                case 6 -> {
                    require(mc.screen.getClass().getName().equals("com.neuvillette.ae2ct.gui.CraftingTreeScreen"),
                            "native AE2 Crafting Tree screen did not open");
                    capture("tree.png");
                    mc.screen.children().stream().filter(appeng.client.gui.widgets.TabButton.class::isInstance)
                            .map(Button.class::cast).findFirst().orElseThrow().onPress();
                }
                case 7 -> {
                    require(mc.screen == report && report.getMenu() == menu, "tree did not return to same report/menu");
                    checkBookmarks();
                    menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
                            CraftingSubmitResult.missingIngredient(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 20)));
                }
                case 8 -> {
                    require(mc.screen instanceof CraftErrorScreen, "missing did not open native popup");
                    require(mc.screen.children().stream().filter(Button.class::isInstance)
                            .map(Button.class::cast).filter(b -> b.visible).count() == 3,
                            "error popup must have exactly three actions");
                    require(mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .anyMatch(b -> b.getMessage().getString().equals(Component.translatable(
                                    "gui.ae2lt.crafting_report.replan").getString())), "explicit replan label");
                    capture("missing.png");
                    var retry = mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(b -> b.getMessage().getString().equals(
                                    appeng.core.localization.GuiText.CraftErrorRetry.text().getString()))
                            .findFirst().orElseThrow();
                    retry.onPress();
                    require(mc.screen == report && menu.retries == 1, "retry did not return to report and submit");
                }
                case 9 -> {
                    Files.writeString(mc.gameDirectory.toPath().resolve("report-result.txt"),
                            "PASS: native server menu initial capture, missing x2, frozen replan 100->80->75, "
                                    + "fresh request reset; medium=210px/5 rows, 4 style buttons, "
                                    + "optional Crafting Tree routing, JEI item/fluid bookmarks, native missing popup and retry.");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("phase " + phase);
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("report-result.txt"), "FAIL phase=" + phase + ": " + failure); }
            catch (Exception ignored) { }
            done = true;
            mc.stop();
        }
    }

    private static void serverChecks(ServerPlayer player) throws Exception {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        long[] live = {100}, observed = {0}, missing = {20}, extractable = {100};
        int[] reads = {0};
        MEStorage storage = new MEStorage() {
            public Component getDescription() { return Component.literal("probe"); }
            public void getAvailableStacks(KeyCounter out) { reads[0]++; out.set(iron, live[0]); }
            public long extract(AEKey key, long amount, Actionable mode, appeng.api.networking.security.IActionSource source) {
                require(mode == Actionable.SIMULATE, "snapshot must not extract materials");
                return Math.min(amount, extractable[0]);
            }
        };
        var storageService = proxy(IStorageService.class, (name, args) -> switch (name) {
            case "getInventory" -> storage;
            case "getCachedInventory" -> storage.getAvailableStacks();
            default -> null;
        });
        var plan = new CraftingPlan(new GenericStack(iron, 1), 435, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        ICraftingPlan[] submitted = {null};
        var service = proxy(ICraftingService.class, (name, args) -> switch (name) {
            case "beginCraftingCalculation" -> {
                var requester = (ICraftingSimulationRequester) args[1];
                var snapshot = new NetworkCraftingSimulationState(storageService, requester.getActionSource());
                observed[0] = snapshot.extract(iron, Long.MAX_VALUE, Actionable.SIMULATE);
                yield CompletableFuture.completedFuture(plan);
            }
            case "submitJob" -> {
                submitted[0] = (ICraftingPlan) args[0];
                yield CraftingSubmitResult.missingIngredient(new GenericStack(iron, missing[0]));
            }
            default -> null;
        });
        var grid = proxy(IGrid.class, (name, args) -> name.equals("getCraftingService") ? service : name.equals("getStorageService") ? storageService : null);
        var node = proxy(IGridNode.class, (name, args) -> name.equals("getGrid") ? grid : null);
        var host = new Host(node);
        host.setLevel(player.serverLevel());
        var serverMenu = new CraftConfirmMenu(17, player.getInventory(), host);
        serverMenu.planJob(iron, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
        require(reads[0] == 1 && observed[0] == 100, "initial stock capture");
        set(serverMenu, "ae2lt$showReport", true);
        set(serverMenu, "result", plan);
        extractable[0] = 80;
        serverMenu.startJob();
        serverMenu.startJob();
        live[0] = 500;
        serverMenu.replan();
        require(reads[0] == 1 && observed[0] == 80, "frozen replan read live stock or deducted retry twice");
        set(serverMenu, "ae2lt$showReport", true);
        set(serverMenu, "result", plan);
        missing[0] = 5;
        extractable[0] = 75;
        serverMenu.startJob();
        serverMenu.replan();
        require(reads[0] == 1 && observed[0] == 75, "second replan did not keep reduced snapshot");
        extractable[0] = 500;
        serverMenu.planJob(iron, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
        require(reads[0] == 2 && observed[0] == 500, "new request did not get fresh stock");
        if (net.minecraftforge.fml.ModList.get().isLoaded("extendedae_plus")) {
            var deficit = new KeyCounter();
            deficit.set(iron, 3);
            var simulation = new CraftingPlan(new GenericStack(iron, 1), 8, true, false,
                    new KeyCounter(), new KeyCounter(), deficit, Map.of());
            set(serverMenu, "result", simulation);
            serverMenu.getClass().getMethod("eap$clientSetForceCraftStart", boolean.class).invoke(serverMenu, true);
            serverMenu.startJob();
            var forced = com.moakiee.ae2lt.integration.eaep.EaepForcedCraftingPlanAccess.read(submitted[0]);
            require(forced != null && forced.original() == simulation && forced.manualMissing().get(iron) == 3,
                    "EAEP force-start did not retain the original plan and missing inputs");
            require(field(serverMenu, "result").get(serverMenu) == simulation,
                    "EAEP force-start did not restore the menu's original simulation");
        }
    }

    private static IPatternDetails treePattern() {
        return new IPatternDetails() {
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            public GenericStack[] getOutputs() { return new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)}; }
            public IInput[] getInputs() { return new IInput[] {new IInput() {
                public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_ORE), 1)}; }
                public long getMultiplier() { return 1; }
                public boolean isValid(AEKey key, Level level) { return AEItemKey.of(Items.IRON_ORE).equals(key); }
                public AEKey getRemainingKey(AEKey key) { return null; }
            }}; }
        };
    }

    private static void checkBookmarks() throws Exception {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("jei")) return;
        var original = menu.getPlan();
        var missing = new KeyCounter();
        missing.set(AEItemKey.of(Items.IRON_INGOT), 2);
        missing.set(AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER), 1000);
        menu.setPlan(ThunderboltCraftingPlanSummary.fromPlan(new CraftingPlan(
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1), 8, true, false,
                new KeyCounter(), new KeyCounter(), missing, Map.of())));
        report.updateBeforeRender();
        if (net.minecraftforge.fml.ModList.get().isLoaded("extendedae_plus")) {
            require(com.moakiee.ae2lt.integration.eaep.EaepForceCraftingAccess.isAvailable(menu),
                    "EAEP Forge force-start channel is unavailable");
        }
        int retries = menu.retries;
        report.startJob();
        require(menu.retries == retries, "missing materials submitted without the force-start gesture");
        var button = (Button) field(report, "bookmarkMissing").get(report);
        require(button.active, "missing-material bookmark adapter is unavailable");
        button.onPress();
        require(Minecraft.getInstance().screen == report, "bookmark button closed the report");
        var implementation = Class.forName("com.moakiee.ae2lt.integration.jei.JeiBookmarkAccessImpl");
        var runtimeField = implementation.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        Object runtime = runtimeField.get(null);
        Object overlay = runtime.getClass().getMethod("getBookmarkOverlay").invoke(runtime);
        Object list = field(overlay, "bookmarkList").get(overlay);
        var entries = (List<?>) list.getClass().getMethod("getElements").invoke(list);
        require(entries.size() == 2, "item and fluid were not both bookmarked");
        button.onPress();
        entries = (List<?>) list.getClass().getMethod("getElements").invoke(list);
        require(entries.size() == 2, "bookmark action toggled or duplicated existing entries");
        menu.setPlan(original);
    }

    private static List<Button> buttons() {
        var bar = ((AEBaseScreenAccessor) (Object) report).ae2lt$getVerticalToolbar();
        return ((VerticalButtonBarAccessor) bar).ae2lt$getButtons();
    }

    private static Button styleButton() {
        return buttons().stream().filter(SettingToggleButton.class::isInstance).findFirst().orElseThrow();
    }

    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(mc.gameDirectory.toPath().resolve(name));
        }
    }

    private static int intField(Object target, String name) throws Exception { return (int) field(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static java.lang.reflect.Field field(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try { var f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == args[0];
            if (m.getName().equals("toString")) return "ReportProbe:" + type.getSimpleName();
            return handler.apply(m.getName(), args);
        });
    }

    private static final class Host extends BlockEntity implements ISubMenuHost, IActionHost {
        private final IGridNode node;
        Host(IGridNode node) { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); this.node = node; }
        public IGridNode getActionableNode() { return node; }
        public void returnToMainMenu(Player player, ISubMenu menu) { }
        public ItemStack getMainMenuIcon() { return new ItemStack(Items.CRAFTING_TABLE); }
    }

    private static final class UiMenu extends CraftConfirmMenu {
        int retries;
        UiMenu(Inventory inventory) { super(18, inventory, new Host(null)); }
        @Override public void startJob() { clearError(); retries++; }
        @Override public void removed(Player player) { }
    }
}
