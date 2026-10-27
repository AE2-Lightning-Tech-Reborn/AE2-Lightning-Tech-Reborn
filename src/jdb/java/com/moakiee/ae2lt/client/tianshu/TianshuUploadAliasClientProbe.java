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
    private static int phase, ticks;
    private static boolean done, wireless;
    private static volatile boolean pending;
    private static volatile Throwable serverFailure;
    private static TianshuUploadAliasEditScreen<?> editor;
    private static List<String> originalAliases;
    private static final StringBuilder report = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.uploadAliasClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || pending || ++ticks % 30 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError(serverFailure);
            switch (phase) {
                case 0 -> {
                    require(mc.gameDirectory.toPath().toAbsolutePath().normalize().endsWith("tianshu-upload-alias-client"), "isolated game directory");
                    Files.writeString(Path.of("upload-alias-probe.txt"), "TIANSHU_ALIAS_QA RUNNING\n");
                    originalAliases = List.copyOf(config().get());
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
                        level.setBlockAndUpdate(CABLE.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                    });
                }
                case 1 -> server(TianshuUploadAliasClientProbe::openTerminal);
                case 2 -> exerciseEditor();
                case 3 -> {
                    require((boolean) field(editor, "received"), "target response received");
                    var menu = (TianshuPatternEncodingTermMenu) editor.getMenu();
                    require(!menu.getUploadTargets().isEmpty(), "powered provider discovered without pattern");
                    require(menu.getUploadTargets().stream().allMatch(t -> t.availableSlots() == 0), "no-pattern slots are zero");
                    var status = (net.minecraft.network.chat.Component) invoke(editor, "matchStatus");
                    require(status.getString().equals(TianshuUploadAliasScreen.text("one_match").getString()), "zero slots still report one matching group");
                    press(editor, "targets");
                    var picker = (TianshuUploadAliasTargetScreen<?>) mc.screen;
                    // Use the actual response already received by the editor for a deterministic click.
                    menu.receiveUploadTargets(menu.getUploadTargets());
                    picker.containerTick();
                    picker.mouseClicked(picker.getGuiLeft() + 15, picker.getGuiTop() + 53, 0);
                    require(mc.screen == editor, "target choice returns to draft");
                    require(AE2LTClientConfig.findUploadAlias(FIRST) == null, "target choice does not save");
                    press(editor, "save");
                    require(mc.screen instanceof TianshuUploadAliasManagementScreen<?>, "save returns to list");
                    require(AE2LTClientConfig.findUploadAlias(FIRST) != null, "save persists alias");
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
                case 7 -> {
                    report.append(wireless ? "wireless" : "wired").append(": PASS native editor, matching, exact deletion, conflict, cancellation, return paths, no upload\n");
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
        require(!(boolean) field(editor, "matchCheckExpanded"), "match check starts collapsed");
        require(!((Button) field(editor, "refresh")).visible, "refresh hidden before check");
        require(!(boolean) field(editor, "waiting"), "ordinary edit does not request snapshot");
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
        press(editor, "matchCheck");
        require((boolean) field(editor, "waiting"), "explicit check requests snapshot");
        // Exercise responsive layout against the actual initialized widgets, then restore native size.
        editor.init(mc, 270, 190);
        require(((Button) field(editor, "save")).getY() + 20 <= 190, "save reachable in narrow layout");
        editor.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
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
        require(mc.screen instanceof TianshuUploadAliasConfirmScreen<?>, "migration collision asks confirmation");
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
        require(AE2LTClientConfig.setUploadAlias(FIRST, "new alias"), "edit current source");
        mc.screen.onClose();
        require(mc.screen == upload, "upload return path");
        require(input(upload, "aliasField").getValue().equals("new alias"), "return refreshes current source alias");
        require((boolean) field(upload, "directUploadAttempted"), "return disarms direct upload");
        upload.containerTick();
        require(!(boolean) field(upload, "awaitingUpload"), "return does not upload");
        upload.onClose();
        require(AE2LTClientConfig.removeUploadAlias(FIRST), "clean current source");
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
