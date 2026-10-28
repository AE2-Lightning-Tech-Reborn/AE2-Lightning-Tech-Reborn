package com.moakiee.ae2lt.client.tianshu;

import appeng.api.ids.AEComponents;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.widgets.AETextField;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native UI probe in an isolated run directory; never packaged in the release jar. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class TianshuUploadAliasClientProbe {
    private static final BlockPos CABLE = new BlockPos(0, 100, 0);
    private static final String FIRST = "alias_probe:first", SECOND = "alias_probe:second";
    private static int phase, ticks, startupTicks;
    private static boolean done, wireless;
    private static volatile boolean pending;
    private static volatile Throwable serverFailure;
    private static TianshuUploadAliasEditScreen<?> editor;
    private static List<String> originalAliases;
    private static com.moakiee.ae2lt.config.TianshuUploadMatchMode originalMode;
    private static String originalClipboard;
    private static final StringBuilder report = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.uploadAliasClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (++startupTicks % 100 == 0) System.out.println("ALIAS_QA_WAITING screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getName()));
            if (startupTicks > 1800) finish(false, "Timed out before entering isolated QA world");
            return;
        }
        if (pending || ++ticks % 30 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError(serverFailure);
            switch (phase) {
                case 0 -> {
                    require(mc.gameDirectory.toPath().toAbsolutePath().normalize().endsWith("tianshu-upload-alias-client"), "isolated game directory");
                    Files.writeString(Path.of("upload-alias-probe.txt"), "TIANSHU_ALIAS_QA RUNNING\n");
                    originalAliases = List.copyOf(config().get());
                    originalMode = AE2LTClientConfig.uploadMatchMode();
                    originalClipboard = mc.keyboardHandler.getClipboard();
                    AE2LTClientConfig.setUploadMatchMode(com.moakiee.ae2lt.config.TianshuUploadMatchMode.OFF);
                    mc.getWindow().setWindowed(1100, 800);
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var level = player.serverLevel();
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(.5, 101, 2.5);
                        PartHelper.setPart(level, CABLE, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
                        PartHelper.setPart(level, CABLE, Direction.SOUTH, player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
                        level.setBlockAndUpdate(CABLE.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(CABLE.east(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
                        level.setBlockAndUpdate(CABLE.east(2), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
                        level.setBlockAndUpdate(CABLE.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                    });
                }
                case 1 -> server(TianshuUploadAliasClientProbe::openTerminal);
                case 2 -> exerciseEditor();
                case 3 -> {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory, (wireless ? "wireless" : "wired") + "-alias-editor.png", mc.getMainRenderTarget(), ignored -> {});
                    var menu = (TianshuPatternEncodingTermMenu) editor.getMenu();
                    press(editor, "save");
                    require(mc.screen instanceof TianshuUploadAliasManagementScreen<?>, "save returns to list");
                    require(AE2LTClientConfig.findUploadAlias(FIRST) != null, "save persists alias");
                    exerciseClipboardAndMode();
                    exerciseRecords();
                    mc.screen.onClose();
                    require(mc.screen instanceof TianshuTerminalSettingsScreen<?>, "settings return path");
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    mc.screen.children().stream().filter(c -> c instanceof appeng.client.gui.widgets.TabButton)
                            .map(c -> (Button) c).findFirst().orElseThrow().onPress();
                    require(mc.screen instanceof TianshuPatternEncodingTermScreen<?>, "terminal restored");
                    require(menu.slots.stream().anyMatch(s -> s.x >= 0), "terminal slots restored");
                    exerciseUploadReturn((TianshuPatternEncodingTermScreen<?>) mc.screen);
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var serverMenu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                        require(serverMenu.uploadState == 0, "management never changes server upload state");
                        require(serverMenu.getSlots(SlotSemantics.ENCODED_PATTERN).stream().allMatch(s -> s.getItem().isEmpty()), "empty encoded slot unchanged");
                        var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) player.serverLevel().getBlockEntity(CABLE.east());
                        require(provider.getLogic().getPatternInv().isEmpty(), "management leaves provider empty");
                    });
                }
                case 4 -> server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var menu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                    var pattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 1)),
                            List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 1)));
                    menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().set(pattern);
                    menu.broadcastChanges();
                });
                case 5 -> {
                    var terminal = (TianshuPatternEncodingTermScreen<?>) mc.screen;
                    require(!terminal.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem().isEmpty(), "encoded pattern synchronized");
                    exerciseUploadReturn(terminal);
                }
                case 6 -> server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var menu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                    require(menu.uploadState == 0, "encoded pattern management never uploads");
                    require(!menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem().isEmpty(), "encoded pattern retained after return");
                    var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) player.serverLevel().getBlockEntity(CABLE.east());
                    require(provider.getLogic().getPatternInv().isEmpty(), "provider untouched with encoded pattern");
                    menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().set(ItemStack.EMPTY);
                    menu.broadcastChanges();
                });
                case 7 -> server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var menu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                    var pattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 1)),
                            List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 1)));
                    menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().set(pattern);
                    menu.sendUploadTargets(player);
                    menu.uploadTianshuPatternToTarget(player, menu.getUploadTargets().getFirst().group(), menu.getUploadTargetRequestRevision());
                    require(menu.uploadState == 1 && menu.canUndoUpload, "server upload creates player receipt");
                });
                case 8 -> {
                    var terminal = (TianshuPatternEncodingTermScreen<?>) mc.screen;
                    var button = terminal.children().stream()
                            .filter(c -> c instanceof appeng.client.gui.widgets.ActionButton a && a.getTooltipMessage().stream()
                                    .anyMatch(t -> t.getString().equals(net.minecraft.network.chat.Component.translatable("ae2lt.tianshu.upload.encode_management").getString())))
                            .map(c -> (appeng.client.gui.widgets.ActionButton)c).findFirst().orElseThrow();
                    require(button.mouseClicked(button.getX() + 2, button.getY() + 2, 1), "native encode right click handled");
                }
                case 9 -> server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var menu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                    var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) player.serverLevel().getBlockEntity(CABLE.east());
                    require(!menu.canUndoUpload && provider.getLogic().getPatternInv().isEmpty(), "right click undoes uploaded pattern");
                    require(!menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem().isEmpty(), "paid pattern returned to terminal");
                    menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().set(ItemStack.EMPTY);
                    menu.broadcastChanges();
                });
                case 10 -> {
                    var terminal = (TianshuPatternEncodingTermScreen<?>) mc.screen;
                    switchScreen(terminal, new TianshuUploadAliasManagementScreen<>(terminal, "", ""));
                }
                case 11 -> {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory, (wireless ? "wireless" : "wired") + "-alias-management.png", mc.getMainRenderTarget(), ignored -> {});
                    var list = (TianshuUploadAliasManagementScreen<?>) mc.screen;
                    list.init(mc, 270, 190);
                    require((int) field(list, "listTop") + (int) field(list, "rows") * (int) field(list, "rowHeight") <= 170 - 73,
                            "narrow management list does not overlap footer");
                    require(((Button) field(list, "exportAliases")).getY() + 20 <= 190, "narrow clipboard controls remain reachable");
                    list.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                    list.onClose();
                    exerciseNormalViewerTransfer();
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var menu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                        var pattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                                List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 1)),
                                List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 1)));
                        menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().set(pattern);
                        menu.setMode(appeng.parts.encoding.EncodingMode.PROCESSING);
                        menu.getSlots(SlotSemantics.PROCESSING_INPUTS).forEach(slot -> slot.set(ItemStack.EMPTY));
                        menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).forEach(slot -> slot.set(ItemStack.EMPTY));
                        menu.getSlots(SlotSemantics.PROCESSING_INPUTS).getFirst().set(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
                        menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).getFirst().set(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT));
                        menu.encode();
                        require(menu.triggeredUploadAck > 0, "real server encoding establishes automatic-upload ownership");
                        menu.broadcastChanges();
                    });
                }
                case 12 -> {
                    AE2LTClientConfig.setUploadMatchMode(com.moakiee.ae2lt.config.TianshuUploadMatchMode.SAVED_ALIAS);
                    openAutomaticPicker();
                }
                case 13 -> {
                    require(mc.screen instanceof TianshuUploadTargetScreen<?>, "saved-only mode retains picker without explicit alias");
                    var picker = (TianshuUploadTargetScreen<?>) mc.screen;
                    require(!(boolean) field(picker, "awaitingUpload"), "machine guess alone does not auto upload in saved mode");
                    require(!picker.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem().isEmpty(), "saved-only mode retains physical pattern");
                    picker.onClose();
                    AE2LTClientConfig.setUploadAlias(FIRST, "minecraft:furnace");
                    openAutomaticPicker();
                }
                case 14 -> {
                    require(mc.screen instanceof TianshuPatternEncodingTermScreen<?>, "saved unique alias auto uploads and returns to terminal");
                    var terminal = (TianshuPatternEncodingTermScreen<?>) mc.screen;
                    require(terminal.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem().isEmpty(), "auto upload emptied encoded slot");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        ((TianshuPatternEncodingTermMenu) player.containerMenu).undoLastUpload();
                    });
                }
                case 15 -> {
                    AE2LTClientConfig.removeUploadAlias(FIRST);
                    AE2LTClientConfig.setUploadMatchMode(com.moakiee.ae2lt.config.TianshuUploadMatchMode.ANY_MATCH);
                    openAutomaticPicker();
                }
                case 16 -> {
                    require(mc.screen instanceof TianshuPatternEncodingTermScreen<?>, "unique machine ID auto uploads in any-match mode");
                    var terminal = (TianshuPatternEncodingTermScreen<?>) mc.screen;
                    require(terminal.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().getItem().isEmpty(), "machine upload transferred the physical pattern");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var menu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                        menu.undoLastUpload();
                        menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst().set(ItemStack.EMPTY);
                        menu.broadcastChanges();
                    });
                    AE2LTClientConfig.setUploadMatchMode(com.moakiee.ae2lt.config.TianshuUploadMatchMode.OFF);
                }
                case 17 -> {
                    report.append(wireless ? "wireless" : "wired").append(": PASS native editor, clipboard merge, three modes + real automatic uploads, machine IDs, exact deletion, conflict, cancellation, return paths, right-click undo\n");
                    if (!wireless) {
                        wireless = true;
                        phase = 0;
                    } else {
                        restoreConfig();
                        finish(true, "Both terminal variants passed");
                    }
                }
                default -> throw new AssertionError("Unexpected phase " + phase);
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { restoreConfig(); } catch (Throwable restore) { failure.addSuppressed(restore); }
            finish(false, "phase=" + phase + ": " + failure);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void exerciseNormalViewerTransfer() throws Exception {
        var mc = Minecraft.getInstance();
        var terminal = (TianshuPatternEncodingTermScreen<?>) mc.screen;
        TianshuRecipeTransferContext.clear(terminal.getMenu());
        if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
            EmiSources.normalTransfer(terminal);
        } else {
            var runtime = mezz.jei.common.Internal.getJeiRuntime();
            var recipe = mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING).getFirst();
            var category = runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.SMELTING);
            var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            var recipesGui = (mezz.jei.gui.recipes.RecipesGui) runtime.getRecipesGui();
            recipesGui.showTypes(List.of(mezz.jei.api.constants.RecipeTypes.SMELTING));
            var controller = new mezz.jei.gui.recipes.RecipeTransferButtonController(layout, recipesGui);
            var input = (mezz.jei.api.gui.inputs.IJeiUserInput) Proxy.newProxyInstance(
                    mezz.jei.api.gui.inputs.IJeiUserInput.class.getClassLoader(),
                    new Class<?>[]{mezz.jei.api.gui.inputs.IJeiUserInput.class}, (proxy, method, args) ->
                            method.getReturnType() == boolean.class ? false : null);
            require(controller.onPress(input), "actual JEI transfer button handled");
            require(mc.screen == terminal, "JEI transfer succeeded and restored terminal");
        }
        var captured = TianshuRecipeTransferContext.snapshotFor(terminal.getMenu());
        require(captured.sourceKey().equals("minecraft:smelting"), "actual viewer transfer preserves recipe type");
        require(captured.machineIds().contains("minecraft:furnace"), "actual viewer transfer captures furnace registry ID");
        require(captured.defaultAliases().getFirst().equals("minecraft:furnace"), "machine ID precedes fuzzy candidates");
        report.append("Actual recipe transfer -> furnace ID: PASS\n");
    }

    private static void openAutomaticPicker() throws Exception {
        var terminal = (TianshuPatternEncodingTermScreen<?>) Minecraft.getInstance().screen;
        TianshuRecipeTransferContext.publish(terminal.getMenu(), FIRST, "", List.of("minecraft:*"), List.of("minecraft:furnace"));
        switchScreen(terminal, new TianshuUploadTargetScreen<>(terminal, false));
    }

    private static void openTerminal() {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.closeContainer();
        player.teleportTo(.5, 101, 2.5);
        if (wireless) {
            var item = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
            var stack = new ItemStack(item);
            stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(player.level().dimension(), CABLE.north()));
            stack.set(AEComponents.STORED_ENERGY, 1000000.0);
            player.getInventory().setItem(0, stack);
            player.inventoryMenu.broadcastChanges();
            item.open(player, MenuLocators.forInventorySlot(0), false);
        } else {
            var terminal = PartHelper.getPart(player.serverLevel(), CABLE, Direction.SOUTH);
            MenuOpener.open(TianshuPatternEncodingTermMenu.TYPE, player, MenuLocators.forPart((appeng.parts.AEBasePart) terminal));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void exerciseEditor() throws Exception {
        var mc = Minecraft.getInstance();
        require(mc.screen instanceof TianshuPatternEncodingTermScreen<?>, "native terminal opened");
        var terminal = (TianshuPatternEncodingTermScreen) mc.screen;
        var menu = (TianshuPatternEncodingTermMenu) terminal.getMenu();
        TianshuRecipeTransferContext.clear(menu);
        require(TianshuUploadSourceSelection.collect(menu).sourceKey().isEmpty(), "no invented recipe source");
        var nativeSettings = new TerminalSettingsScreen(terminal);
        switchScreen(terminal, nativeSettings);
        var settings = new TianshuTerminalSettingsScreen(nativeSettings);
        switchScreen(nativeSettings, settings);
        pressLabel(settings, TianshuUploadAliasScreen.text("title").getString());
        var management = (TianshuUploadAliasManagementScreen<?>) mc.screen;
        require(!((Button) field(management, "edit")).active, "settings entry does not select first record");
        TianshuRecipeTransferContext.publish(menu, FIRST, "", List.of());
        press(management, "add");
        editor = (TianshuUploadAliasEditScreen<?>) mc.screen;
        require(input(editor, "source").getValue().equals(FIRST), "new entry prefills current source");
        exerciseRecipeSources(editor);
        editor.onClose();
        require(mc.screen == management, "untouched prefill cancels without confirmation");
        press(management, "add");
        editor = (TianshuUploadAliasEditScreen<?>) mc.screen;
        input(editor, "source").setValue("bad=key");
        input(editor, "alias").setValue("*");
        require(!((Button) field(editor, "save")).active, "invalid source disables save");
        input(editor, "source").setValue(FIRST);
        require(((Button) field(editor, "save")).active, "valid draft enables save");
        editor.onClose();
        require(mc.screen instanceof TianshuUploadAliasConfirmScreen<?>, "changed draft asks before discard");
        mc.screen.onClose();
        require(mc.screen == editor && input(editor, "alias").getValue().equals("*"), "cancel discard retains draft");
        // Exercise responsive layout against the actual initialized widgets, then restore native size.
        editor.init(mc, 270, 190);
        require(((Button) field(editor, "save")).getY() + 20 <= 190, "save reachable in narrow layout");
        editor.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private static void exerciseClipboardAndMode() throws Exception {
        var mc = Minecraft.getInstance();
        var list = (TianshuUploadAliasManagementScreen<?>) mc.screen;
        var off = com.moakiee.ae2lt.config.TianshuUploadMatchMode.OFF;
        list.onClose();
        require(mc.screen instanceof TianshuTerminalSettingsScreen<?>, "auto upload belongs to terminal settings");
        var settings = (TianshuTerminalSettingsScreen<?>) mc.screen;
        require(AE2LTClientConfig.uploadMatchMode() == off, "automatic upload starts off");
        press(settings, "autoUploadButton");
        require(AE2LTClientConfig.uploadMatchMode() == off.next(), "cycle saved aliases");
        press(settings, "autoUploadButton");
        require(AE2LTClientConfig.uploadMatchMode() == off.next().next(), "cycle any match");
        press(settings, "autoUploadButton");
        require(AE2LTClientConfig.uploadMatchMode() == off, "cycle off");
        pressLabel(settings, TianshuUploadAliasScreen.text("title").getString());
        list = (TianshuUploadAliasManagementScreen<?>) mc.screen;
        press(list, "exportAliases");
        String exported = mc.keyboardHandler.getClipboard();
        require(com.moakiee.ae2lt.config.UploadAliasClipboard.parse(exported).stream().anyMatch(e -> e.source().equals(FIRST)), "clipboard export includes saved mapping");
        var before = List.copyOf(config().get());
        mc.keyboardHandler.setClipboard("{broken");
        press(list, "importAliases");
        require(before.equals(config().get()), "invalid clipboard leaves all records intact");
        mc.keyboardHandler.setClipboard(com.moakiee.ae2lt.config.UploadAliasClipboard.exportAliases(List.of(
                new com.moakiee.ae2lt.config.UploadAliasRecords.Entry(SECOND, "中文*", 1))));
        press(list, "importAliases");
        require("中文*".equals(AE2LTClientConfig.findUploadAlias(SECOND)), "valid clipboard merged");
        require(AE2LTClientConfig.findUploadAlias(FIRST) != null && AE2LTClientConfig.uploadMatchMode() == off,
                "import preserves unrelated mappings and mode");
        mc.keyboardHandler.setClipboard(com.moakiee.ae2lt.config.UploadAliasClipboard.exportAliases(List.of(
                new com.moakiee.ae2lt.config.UploadAliasRecords.Entry(SECOND, "changed", 1))));
        press(list, "importAliases");
        require(mc.screen instanceof TianshuUploadAliasConfirmScreen<?>, "conflicting import asks before replacing");
        mc.screen.onClose();
        require("中文*".equals(AE2LTClientConfig.findUploadAlias(SECOND)), "cancel import preserves existing mapping");
        press(list, "importAliases");
        press(mc.screen, "confirm");
        require("changed".equals(AE2LTClientConfig.findUploadAlias(SECOND)), "confirmed import replaces conflict");
        AE2LTClientConfig.removeUploadAlias(SECOND);
        invoke(list, "refresh");
        exerciseMachineMatching();
    }

    private static void exerciseMachineMatching() {
        var group1 = new appeng.api.implementations.blockentities.PatternContainerGroup(
                appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.FURNACE), net.minecraft.network.chat.Component.literal("Furnace"), List.of());
        var group2 = new appeng.api.implementations.blockentities.PatternContainerGroup(
                appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.BLAST_FURNACE), net.minecraft.network.chat.Component.literal("Blast Furnace"), List.of());
        var one = new com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData(group1, 1, 3);
        var full = new com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData(group2, 1, 0);
        var any = com.moakiee.ae2lt.config.TianshuUploadMatchMode.ANY_MATCH;
        var ids = List.of("minecraft:furnace", "minecraft:blast_furnace");
        require(TianshuUploadTargetMatcher.automaticCandidate(List.of(one), any, "", "minecraft:*", ids) == one,
                "exact catalyst ID selects unique machine");
        require(TianshuUploadTargetMatcher.automaticCandidate(List.of(one, full), any, "", "minecraft:furnace", ids) == null,
                "multiple catalyst matches remain ambiguous even if one is full");
        require(TianshuUploadTargetMatcher.automaticCandidate(List.of(one), any, "", "minecraft:*", List.of("minecraft:smoker")) == null,
                "machine metadata does not fall back to a broad namespace guess");
        require(TianshuUploadTargetMatcher.automaticCandidate(List.of(one), any, "no such alias", "minecraft:furnace", ids) == null,
                "saved alias overrides machine guess");
        require(TianshuUploadTargetMatcher.automaticCandidate(List.of(one), any.next().next(), "", "minecraft:furnace", ids) == null,
                "saved-only mode never auto uploads a machine guess");
        if (!net.neoforged.fml.ModList.get().isLoaded("emi")) {
            require(com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata.machineIds(mezz.jei.api.constants.RecipeTypes.SMELTING)
                    .contains("minecraft:furnace"), "actual JEI catalyst API returns furnace registry ID");
        }
    }

    private static void exerciseRecords() throws Exception {
        var mc = Minecraft.getInstance();
        var list = (TianshuUploadAliasManagementScreen<?>) mc.screen;
        String shared = AE2LTClientConfig.findUploadAlias(FIRST);
        require(AE2LTClientConfig.setUploadAlias(SECOND, shared), "seed same alias on second source");
        invoke(list, "refresh");
        input(list, "search").setValue(FIRST);
        require(((List<?>) field(list, "filtered")).size() == 1, "search filters by source");
        input(list, "search").setValue("");
        press(list, "edit");
        var edit = (TianshuUploadAliasEditScreen<?>) mc.screen;
        input(edit, "source").setValue(SECOND);
        input(edit, "alias").setValue("replacement");
        press(edit, "save");
        require(mc.screen instanceof TianshuUploadAliasConfirmScreen<?>,
                "migration collision asks confirmation: screen=" + mc.screen.getClass().getSimpleName()
                        + ", original=" + field(edit, "original") + ", records=" + AE2LTClientConfig.uploadAliases());
        mc.screen.onClose();
        require(AE2LTClientConfig.findUploadAlias(SECOND).equals(shared), "cancel conflict preserves destination");
        edit.onClose();
        press(mc.screen, "confirm");
        require(mc.screen == list, "discard returns to list");
        press(list, "delete");
        press(mc.screen, "confirm");
        require(AE2LTClientConfig.findUploadAlias(FIRST) == null, "delete removes selected source");
        require(AE2LTClientConfig.findUploadAlias(SECOND).equals(shared), "same alias on another source survives");
        require(AE2LTClientConfig.removeUploadAlias(SECOND), "clean probe source");
    }

    private static <M extends TianshuPatternEncodingTermMenu> void exerciseUploadReturn(TianshuPatternEncodingTermScreen<M> terminal) throws Exception {
        var mc = Minecraft.getInstance();
        var menu = terminal.getMenu();
        TianshuRecipeTransferContext.publish(menu, FIRST, "", List.of("default"));
        var upload = new TianshuUploadTargetScreen<>(terminal, true);
        switchScreen(terminal, upload);
        invoke(upload, "openAliasManagement");
        var management = mc.screen;
        require(AE2LTClientConfig.findUploadAlias(FIRST) == null, "upload source has no saved alias");
        press(management, "add");
        var draft = (TianshuUploadAliasEditScreen<?>) mc.screen;
        require(input(draft, "source").getValue().equals(FIRST), "upload management add prefills current source");
        require(input(draft, "alias").getValue().equals(input(upload, "aliasField").getValue()), "upload filter prefills alias draft");
        input(draft, "alias").setValue("new alias");
        press(draft, "save");
        require(mc.screen == management, "new source saves back to management");
        mc.screen.onClose();
        require(mc.screen == upload, "upload return path");
        require(input(upload, "aliasField").getValue().equals("new alias"), "return refreshes current source alias");
        require((boolean) field(upload, "directUploadAttempted"), "return disarms direct upload");
        upload.containerTick();
        require(!(boolean) field(upload, "awaitingUpload"), "return does not upload");
        upload.onClose();
        require(AE2LTClientConfig.removeUploadAlias(FIRST), "clean current source");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void exerciseRecipeSources(TianshuUploadAliasEditScreen<?> draft) throws Exception {
        var mc = Minecraft.getInstance();
        var menu = (TianshuPatternEncodingTermMenu) draft.getMenu();
        var previousContext = TianshuRecipeTransferContext.snapshotFor(menu);
        var previousRecords = List.copyOf(config().get());
        String previousSource = input(draft, "source").getValue();
        String previousAlias = input(draft, "alias").getValue();
        input(draft, "alias").setValue("keep this alias");
        var vanilla = mc.player.level().getRecipeManager()
                .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING).getFirst();
        if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
            EmiSources.exercise(draft, vanilla, previousSource);
            report.append("EMI source picker: PASS\n");
        } else {
            var recipesGui = (mezz.jei.gui.recipes.RecipesGui) mezz.jei.common.Internal.getJeiRuntime().getRecipesGui();
            for (boolean backed : List.of(false, true)) {
                recipesGui.showTypes(List.of(mezz.jei.api.constants.RecipeTypes.SMELTING));
                require(mc.screen == recipesGui, "JEI recipe page opens from editor");
                var type = mezz.jei.api.recipe.RecipeType.create("alias_probe", "viewer_category", Object.class);
                var category = (mezz.jei.api.recipe.category.IRecipeCategory) Proxy.newProxyInstance(
                        mezz.jei.api.recipe.category.IRecipeCategory.class.getClassLoader(),
                        new Class<?>[]{mezz.jei.api.recipe.category.IRecipeCategory.class}, (proxy, method, args) ->
                                method.getName().equals("getRecipeType") ? type : null);
                var layout = (mezz.jei.api.gui.IRecipeLayoutDrawable) Proxy.newProxyInstance(
                        mezz.jei.api.gui.IRecipeLayoutDrawable.class.getClassLoader(),
                        new Class<?>[]{mezz.jei.api.gui.IRecipeLayoutDrawable.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "getRecipeCategory" -> category;
                            case "getRecipe" -> backed ? vanilla : new Object();
                            default -> null;
                        });
                var controller = new mezz.jei.gui.recipes.RecipeTransferButtonController(layout, recipesGui);
                boolean[] state = new boolean[2];
                controller.updateState((mezz.jei.api.gui.buttons.IButtonState) Proxy.newProxyInstance(
                        mezz.jei.api.gui.buttons.IButtonState.class.getClassLoader(),
                        new Class<?>[]{mezz.jei.api.gui.buttons.IButtonState.class}, (proxy, method, args) -> {
                            if (method.getName().equals("setActive")) state[0] = (boolean) args[0];
                            if (method.getName().equals("setVisible")) state[1] = (boolean) args[0];
                            return null;
                        }));
                require(state[0] && state[1], "JEI source fill button enabled");
                for (boolean simulate : List.of(true, false)) {
                    var input = (mezz.jei.api.gui.inputs.IJeiUserInput) Proxy.newProxyInstance(
                            mezz.jei.api.gui.inputs.IJeiUserInput.class.getClassLoader(),
                            new Class<?>[]{mezz.jei.api.gui.inputs.IJeiUserInput.class}, (proxy, method, args) ->
                                    method.getName().equals("isSimulate") ? simulate : null);
                    require(controller.onPress(input), "JEI source click handled");
                    if (simulate) require(input(draft, "source").getValue().equals(previousSource), "JEI simulation does not change draft");
                }
                require(input(draft, "source").getValue().equals(backed ? "minecraft:smelting" : "alias_probe:viewer_category"),
                        "JEI prefers vanilla type and falls back to category");
                require(mc.screen == draft, "JEI source click returns to editor");
                input(draft, "source").setValue(previousSource);
            }
            report.append("JEI source picker: PASS\n");
        }
        require(input(draft, "alias").getValue().equals("keep this alias"), "recipe source selection retains alias draft");
        require(previousContext.equals(TianshuRecipeTransferContext.snapshotFor(menu)), "source selection preserves terminal recipe context");
        require(previousRecords.equals(config().get()), "source selection does not save configuration");
        require(menu.uploadState == 0, "source selection does not upload");
        input(draft, "source").setValue(previousSource);
        input(draft, "alias").setValue(previousAlias);
    }

    // Keep optional EMI bytecode out of the subscriber class when testing a JEI-only installation.
    private static final class EmiSources {
        @SuppressWarnings({"rawtypes", "unchecked"})
        static void normalTransfer(TianshuPatternEncodingTermScreen<?> terminal) {
            var recipe = dev.emi.emi.api.EmiApi.getRecipeManager()
                    .getRecipes(dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.SMELTING).getFirst();
            var handler = new com.moakiee.ae2lt.integration.emi.TianshuEmiEncodePatternHandler(TianshuPatternEncodingTermMenu.class);
            var context = new dev.emi.emi.api.recipe.handler.EmiCraftContext(terminal,
                    new dev.emi.emi.api.recipe.EmiPlayerInventory(List.of()),
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON);
            require(handler.craft(recipe, context), "actual EMI transfer succeeds");
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        static void exercise(TianshuUploadAliasEditScreen<?> draft,
                net.minecraft.world.item.crafting.RecipeHolder<?> vanilla, String previousSource) throws Exception {
            var mc = Minecraft.getInstance();
            var handler = new com.moakiee.ae2lt.integration.emi.TianshuEmiEncodePatternHandler(TianshuPatternEncodingTermMenu.class);
            var context = new dev.emi.emi.api.recipe.handler.EmiCraftContext(draft,
                    new dev.emi.emi.api.recipe.EmiPlayerInventory(List.of()),
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON);
            for (boolean backed : List.of(false, true)) {
                dev.emi.emi.api.EmiApi.displayRecipes(dev.emi.emi.api.stack.EmiStack.of(net.minecraft.world.item.Items.IRON_INGOT));
                require(mc.screen != draft && dev.emi.emi.api.EmiApi.getHandledScreen() == draft,
                        "EMI recipe page retains editor as its parent");
                var recipe = (dev.emi.emi.api.recipe.EmiRecipe) Proxy.newProxyInstance(
                        dev.emi.emi.api.recipe.EmiRecipe.class.getClassLoader(),
                        new Class<?>[]{dev.emi.emi.api.recipe.EmiRecipe.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "getCategory" -> dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.CRAFTING;
                            case "getBackingRecipe" -> backed ? vanilla : null;
                            default -> null;
                        });
                require(handler.canCraft(recipe, context), "EMI source fill button enabled");
                require(input(draft, "source").getValue().equals(previousSource), "EMI button check does not change draft");
                require(handler.craft(recipe, context), "EMI source selection succeeds");
                require(mc.screen == draft, "EMI source click returns to editor");
                require(input(draft, "source").getValue().equals(backed ? "minecraft:smelting" : "minecraft:crafting"),
                        "EMI prefers vanilla type and falls back to category");
                input(draft, "source").setValue(previousSource);
            }
        }
    }

    private static void switchScreen(AEBaseScreen<?> from, AEBaseScreen<?> to) throws Exception {
        var method = AEBaseScreen.class.getDeclaredMethod("switchToScreen", AEBaseScreen.class);
        method.setAccessible(true);
        method.invoke(from, to);
    }

    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static AETextField input(Object screen, String name) throws Exception { return (AETextField) field(screen, name); }
    private static void press(Object screen, String name) throws Exception {
        var button = (Button) field(screen, name);
        require(button.active && button.visible, "button accessible: " + name);
        button.onPress();
    }
    private static void pressLabel(net.minecraft.client.gui.screens.Screen screen, String label) {
        screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(label))
                .map(c -> (Button) c).findFirst().orElseThrow().onPress();
    }
    @SuppressWarnings("unchecked")
    private static net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>> config() throws Exception {
        var field = AE2LTClientConfig.class.getDeclaredField("TIANSHU_UPLOAD_ALIASES");
        field.setAccessible(true);
        return (net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>>) field.get(null);
    }
    private static void restoreConfig() throws Exception {
        if (originalAliases != null) { config().set(originalAliases); AE2LTClientConfig.SPEC.save(); }
        if (originalMode != null) AE2LTClientConfig.setUploadMatchMode(originalMode);
        if (originalClipboard != null) Minecraft.getInstance().keyboardHandler.setClipboard(originalClipboard);
    }
    private static void server(Runnable action) {
        pending = true;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable failure) { serverFailure = failure; }
            finally { pending = false; }
        });
    }
    private static void finish(boolean passed, String message) {
        done = true;
        String result = "TIANSHU_ALIAS_QA " + (passed ? "PASS" : "FAIL") + ": " + message + "\n" + report;
        System.out.println(result);
        try { Files.writeString(Path.of("upload-alias-probe.txt"), result); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
