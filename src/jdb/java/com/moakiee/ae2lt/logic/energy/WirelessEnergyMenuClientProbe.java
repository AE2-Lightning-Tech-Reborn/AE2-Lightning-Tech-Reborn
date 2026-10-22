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
    private static boolean done, checkedFrequencyScroll;
    private static volatile Throwable failure;
    private static volatile int frequency;
    private static com.moakiee.ae2lt.menu.FrequencyPage pendingPage;
    private static java.util.UUID savedSelection;
    private static int frameLeft, frameTop;
    private static final BlockPos CONTROLLER = new BlockPos(0, 100, 0);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.wirelessEnergyMenuProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        mc.options.keyAttack.setDown(false);
        if (mc.gameMode != null && mc.screen == null) mc.gameMode.stopDestroyBlock();
        if (++ticks > 3600) { finish(new AssertionError("timeout phase " + phase)); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (mc.screen != null) {
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 10, 10);
            try {
                var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                move.setAccessible(true); move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 10., 10.);
            } catch (ReflectiveOperationException problem) { finish(problem); return; }
        }
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
            if (pendingPage != null) {
                if (!advanceNavigation()) return;
                pendingPage = null;
            }
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
                        for (int i = 0; i < 8; i++) WirelessFrequencyManager.get(p.server).createFrequency(p,
                                "ZZ Scroll " + i, 0xffffff, FrequencySecurityLevel.PRIVATE, "");
                        var friend = new net.neoforged.neoforge.common.util.FakePlayer(level,
                                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "FrequencyFriend"));
                        WirelessFrequencyManager.get(p.server).getFrequency(frequency).enrollAsUser(friend);
                        WirelessFrequencyManager.get(p.server).createFrequency(friend, "Locked QA", 0x00abff,
                                FrequencySecurityLevel.ENCRYPTED, "test-password");
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
                    // GUI tests do not need block breaking; protect the fixture when the world regains mouse focus.
                    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                });
                case 2 -> openController();
                case 3 -> {
                    require(mc.screen instanceof FrequencyScreen, "native controller opens frequency menu");
                    frameLeft = integerField("leftPos"); frameTop = integerField("topPos");
                    assertFrame(); capture("frequency-home.png");
                    if (Boolean.getBoolean("ae2lt.cardManagementOnly")) { phase = 51; return; }
                    click(tr("network"));
                }
                case 4 -> {
                    require(snapshot().devices() >= 5 && snapshot().rows().stream().allMatch(r -> r.group()), "ME type aggregation");
                    capture("network-devices.png"); search(Component.translatable("block.ae2.interface").getString());
                }
                case 5 -> {
                    require(snapshot().devices() == 1, "localized device-name search"); row(0);
                }
                case 6 -> {
                    require(snapshot().total() == 2 && snapshot().rows().getFirst().group() && !snapshot().rows().get(1).group(), "inline type expansion");
                    require(!((Button[])field("locateButtons"))[0].visible, "group header has no location action");
                    require(field("selected") == null, "location does not require selecting a row");
                    locate(1); capture("network-devices-search.png");
                    mc.screen.onClose(); mc.player.setXRot(15);
                }
                case 7 -> { capture("network-device-highlight.png"); mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")); }
                case 8 -> { capture("network-device-coordinates.png"); mc.screen.onClose(); }
                case 9 -> {
                    if (mc.screen == null) { openController(); return; }
                    require(mc.screen instanceof FrequencyScreen, "reopen controller after chat"); nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_ENERGY);
                }
                case 10 -> {
                    require(snapshot().total() == 10 && snapshot().rows().size() == 5, "all native and wireless power paths, bounded page");
                    click("1s · FE/t");
                }
                case 11 -> { require(snapshot() != null, "5s refresh"); click("5s · FE/t"); }
                case 12 -> { require(snapshot() != null, "30s refresh"); click("30s · FE/t"); }
                case 13 -> {
                    require(snapshot().totals()[4] > 0, "1min average is nonzero FE/t");
                    double last = Double.POSITIVE_INFINITY;
                    for (var r : snapshot().rows()) { require(r.rates()[4] <= last, "power sorting uses selected window"); last = r.rates()[4]; }
                    locate(0); capture("network-power.png"); search(tr("kind.induction"));
                }
                case 14 -> {
                    require(snapshot().total() == 1 && snapshot().rows().getFirst().kind().equals("induction"), "native induction source filter");
                    capture("network-power-induction.png"); search(tr("kind.accessor"));
                }
                case 15 -> {
                    require(snapshot().total() == 1 && snapshot().rows().getFirst().kind().equals("accessor"), "accessor source filter");
                    search("");
                }
                case 16 -> { require(snapshot().total() == 10, "clear search"); ((Scrollbar)field("scrollbar")).setCurrentScroll(5); }
                case 17 -> {
                    require(snapshot().offset() == 5 && snapshot().rows().size() == 5, "native scrollbar page");
                    int index = 0; while (!snapshot().rows().get(index).canDisconnect()) index++;
                    row(index); click(tr("disconnect"));
                }
                case 18 -> {
                    require(snapshot().total() == 9, "unbind refresh"); capture("network-power-after-disconnect.png");
                    server(() -> { var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(WirelessEnergyEndpoints.get(p.server).inspect(frequency, p.getUUID()).size() == 7, "server removed exactly one target"); });
                }
                case 19 -> nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_DEVICES);
                case 20 -> {
                    require(snapshot().devices() == 1 && snapshot().total() == 2 && snapshot().rows().getFirst().group(), "device tab restores search and inline expansion");
                    assertFrame();
                    focusSearch();
                }
                case 21 -> {
                    assertSelectedTab(false); capture("network-devices-selected.png");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_ENERGY);
                }
                case 22 -> { require(snapshot().total() == 9, "power tab switch"); focusSearch(); }
                case 23 -> {
                    assertSelectedTab(true); capture("network-power-selected.png");
                    clickButton((Button)field("sortButton"));
                }
                case 24 -> {
                    assertSelectedTab(true); capture("network-power-selected-after-sort.png");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_HOME);
                }
                case 25 -> {
                    require(mc.screen instanceof FrequencyScreen, "return to frequency menu");
                    var foreign = new com.moakiee.ae2lt.logic.management.DeviceLocation("minecraft:the_nether", new BlockPos(8, 80, -12), -1, "minecraft:barrel", "");
                    require(!com.moakiee.ae2lt.client.render.NetworkDeviceHighlight.locate(foreign), "cross-dimension location reports without highlighting wrong world");
                    verifyChatCopy(NetworkManagementScreen.address(foreign));
                    var target = com.moakiee.ae2lt.client.render.NetworkDeviceHighlight.class.getDeclaredField("target"); target.setAccessible(true);
                    require(target.get(null) == null, "cross-dimension locate clears previous world highlight");
                    assertFrame(); capture("network-management-entry.png");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SELECTION);
                }
                case 26 -> {
                    assertFrame();
                    if (!checkedFrequencyScroll) {
                        checkedFrequencyScroll = true;
                        var screen = mc.screen;
                        screen.mouseScrolled(frameLeft + 20, frameTop + 90, 0, -1);
                        require((int)frequencyField("selectionScroll") == 1, "native frequency wheel scroll");
                        screen.mouseScrolled(frameLeft + 20, frameTop + 20, 0, -1);
                        require((int)frequencyField("selectionScroll") == 1, "wheel outside list must not scroll");
                        screen.mouseScrolled(frameLeft + 20, frameTop + 90, 0, 1);
                        int scrollbarX = frameLeft + com.moakiee.ae2lt.client.gui.FrequencyNavigation.WIDTH - 15;
                        require(screen.mouseClicked(scrollbarX, frameTop + 54, 0), "native scrollbar handle click");
                        screen.mouseDragged(scrollbarX, frameTop + 172, 0, 0, 118);
                        screen.mouseReleased(frameLeft - 10, frameTop + 172, 0);
                        require((int)frequencyField("selectionScroll") > 0, "native scrollbar drag updates rows");
                        return;
                    }
                    var input = (AETextField)frequencyField("selectionSearchField");
                    if (input.getValue().isEmpty()) {
                        var bounds = input.getTooltipArea();
                        mc.screen.mouseClicked(bounds.getX() + 10, bounds.getY() + 6, 0);
                        mc.screen.mouseReleased(bounds.getX() + 10, bounds.getY() + 6, 0);
                        for (char c : "Locked".toCharArray()) require(mc.screen.charTyped(c, 0), "frequency search character accepted");
                        return;
                    }
                    require(input.getValue().equals("Locked") && input.isFocused(), "frequency search survives list rebuilds while typing");
                    capture("frequency-selection.png"); clickContaining("Locked QA");
                }
                case 27 -> {
                    require((int)frequencyField("passwordPromptFreqId") > 0, "encrypted row opens password modal");
                    capture("frequency-password-prompt.png"); clickKey("ae2lt.gui.button.cancel");
                }
                case 28 -> { require((int)frequencyField("passwordPromptFreqId") == 0, "password cancel"); nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_CONNECTION); }
                case 29 -> {
                    assertFrame(); require(snapshot().total() == 1 && snapshot().rows().getFirst().kind().equals("connection")
                            && snapshot().rows().getFirst().state().equals("loaded"), "frequency links share the network page");
                    locate(0); capture("frequency-connections.png"); nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_MEMBER);
                }
                case 30 -> { assertFrame(); capture("frequency-members.png"); clickContaining("FrequencyFriend"); }
                case 31 -> { require(frequencyField("popupMemberUUID") != null, "member popup"); capture("frequency-member-popup.png"); clickKey("ae2lt.gui.button.cancel"); }
                case 32 -> nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_CREATE);
                case 33 -> {
                    assertFrame();
                    if (((AETextField)frequencyField("nameField")).getValue().isEmpty()) {
                        ((AETextField)frequencyField("nameField")).setValue("Created QA");
                        for (int i = 0; frequencyField("editSecurity") != FrequencySecurityLevel.ENCRYPTED && i < 3; i++)
                            clickKey("ae2lt.gui.security." + ((FrequencySecurityLevel)frequencyField("editSecurity")).name().toLowerCase(java.util.Locale.ROOT));
                        ((AETextField)frequencyField("passwordField")).setValue("created-password"); return;
                    }
                    capture("frequency-create.png"); clickKey("ae2lt.gui.button.create");
                }
                case 34 -> {
                    require(com.moakiee.ae2lt.client.frequency.ClientFrequencyCache.getAllFrequenciesSorted().stream().anyMatch(f -> f.name().equals("Created QA") && f.security() == FrequencySecurityLevel.ENCRYPTED), "encrypted create round-trip");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING);
                }
                case 35 -> {
                    assertFrame(); capture("frequency-settings.png");
                    ((AETextField)frequencyField("nameField")).setValue("Renamed QA");
                    int blueSwatchX = frameLeft + com.moakiee.ae2lt.client.gui.FrequencyNavigation.WIDTH - 122 + 2 * 16 + 7;
                    require(mc.screen.mouseClicked(blueSwatchX, frameTop + 115, 0), "edit color swatch click");
                    mc.screen.mouseReleased(blueSwatchX, frameTop + 115, 0); clickKey("ae2lt.gui.button.apply");
                }
                case 36 -> {
                    require(com.moakiee.ae2lt.client.frequency.ClientFrequencyCache.getFrequency(frequency).name().equals("Renamed QA")
                            && com.moakiee.ae2lt.client.frequency.ClientFrequencyCache.getFrequency(frequency).color() == 0x0033FF,
                            "settings name/color apply round-trip");
                    ((AETextField)frequencyField("nameField")).setValue("Unapplied draft");
                    clickKey("ae2lt.gui.button.delete");
                }
                case 37 -> { require((boolean)frequencyField("deleteConfirmOpen"), "delete confirmation"); capture("frequency-delete-confirm.png"); clickKey("ae2lt.gui.button.cancel"); }
                case 38 -> { require(!(boolean)frequencyField("deleteConfirmOpen")
                        && ((AETextField)frequencyField("nameField")).getValue().equals("Unapplied draft"), "delete cancel preserves form draft"); nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_ENERGY); }
                case 39 -> {
                    assertFrame(); require((int)field("window") == 4 && (int)field("sort") == 0, "frequency-to-power restores time window and sort");
                    search("barrel");
                }
                case 40 -> { require(snapshot().total() == 9, "power filter after reopen"); ((Scrollbar)field("scrollbar")).setCurrentScroll(2); }
                case 41 -> {
                    require(snapshot().offset() == 2, "power scroll after reopen"); row(0); savedSelection = (java.util.UUID)field("selected");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING);
                }
                case 42 -> {
                    assertFrame(); require(frequencyField("currentTab") == com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING, "management-to-frequency-settings route");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_DEVICES);
                }
                case 43 -> {
                    assertFrame(); require(snapshot().devices() == 1 && snapshot().total() == 2, "frequency-to-devices restores expanded search");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_ENERGY);
                }
                case 44 -> {
                    assertFrame(); require(snapshot().offset() == 2 && savedSelection.equals(field("selected"))
                            && ((AETextField)field("search")).getValue().equals("barrel"), "independent power search, scroll and selection retained");
                    capture("frequency-power-restored.png"); nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_HOME);
                }
                case 45 -> {
                    require(mc.screen.children().stream().noneMatch(e -> e instanceof Button button && button.visible
                            && button.getMessage().getString().equals(Component.translatable("ae2lt.gui.button.disconnect").getString())), "overview has no duplicate disconnect");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SELECTION);
                }
                case 46 -> {
                    ((AETextField)frequencyField("selectionSearchField")).setValue("Renamed");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING);
                }
                case 47 -> {
                    assertFrame(); capture("frequency-merged-settings.png"); clickKey("ae2lt.gui.button.back");
                }
                case 48 -> {
                    require(frequencyField("currentTab") == com.moakiee.ae2lt.menu.FrequencyPage.TAB_SELECTION
                            && ((AETextField)frequencyField("selectionSearchField")).getValue().equals("Renamed"), "settings returns to frequency list with search retained");
                    capture("frequency-merged-return.png"); clickKey("ae2lt.gui.button.disconnect");
                }
                case 49 -> {
                    require(((FrequencyScreen)mc.screen).getMenu().getCurrentFrequencyId() <= 0, "disconnect from frequency page updates live menu");
                    require(!buttonByKey("ae2lt.gui.button.disconnect").active
                            && !buttonByKey("ae2lt.gui.tab.setting").active
                            && buttonByKey("ae2lt.gui.tab.create").active, "unbound device can create but cannot disconnect or edit a nonexistent frequency");
                    server(() -> require(((AdvancedWirelessOverloadedControllerBlockEntity)mc.getSingleplayerServer().overworld()
                            .getBlockEntity(CONTROLLER)).getFrequencyId() <= 0, "server controller disconnected"));
                    capture("frequency-merged-disconnected.png"); clickContaining("Renamed QA");
                }
                case 50 -> {
                    require(((FrequencyScreen)mc.screen).getMenu().getCurrentFrequencyId() == frequency, "frequency list reconnects the same controller");
                    require(buttonByKey("ae2lt.gui.button.disconnect").active && buttonByKey("ae2lt.gui.tab.setting").active,
                            "connection actions restored after reconnect");
                    server(() -> require(((AdvancedWirelessOverloadedControllerBlockEntity)mc.getSingleplayerServer().overworld()
                            .getBlockEntity(CONTROLLER)).getFrequencyId() == frequency, "server controller reconnected"));
                    ((AETextField)frequencyField("selectionSearchField")).setValue("");
                }
                case 51 -> {
                    assertFrame(); capture("frequency-merged-list.png");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var card = new ItemStack(com.moakiee.ae2lt.registry.ModItems.OVERLOADED_FREQUENCY_CARD.get());
                        com.moakiee.ae2lt.item.OverloadedFrequencyCardItem.bindFrequency(card, frequency, player.level().dimension(), CONTROLLER, player.getUUID());
                        player.setItemInHand(InteractionHand.MAIN_HAND, card);
                        com.moakiee.ae2lt.menu.FrequencyMenu.openHeldCard(player, InteractionHand.MAIN_HAND);
                    });
                }
                case 52 -> {
                    assertCardAuto(false, false);
                    capture("frequency-card-auto-off.png");
                    clickButton((Button)frequencyField("energyAutoButton"));
                }
                case 53 -> { assertCardAuto(false, true); clickButton((Button)frequencyField("channelAutoButton")); }
                case 54 -> { assertCardAuto(true, true); clickButton((Button)frequencyField("energyAutoButton")); }
                case 55 -> {
                    assertCardAuto(true, false); capture("frequency-card-independent-auto.png");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_SELECTION);
                }
                case 56 -> nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_HOME);
                case 57 -> {
                    assertCardAuto(true, false);
                    clickButton((Button)frequencyField("channelAutoButton"));
                }
                case 58 -> { assertCardAuto(false, false); assertFrame(); nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_DEVICES); }
                case 59 -> {
                    var manager = ((NetworkManagementScreen)mc.screen).getMenu();
                    require(manager.fromCard() && !manager.hasParentMenu() && snapshot().devices() >= 5,
                            "held card opens all network devices");
                    assertFrame(); capture("frequency-card-management.png");
                    search(Component.translatable("block.ae2.interface").getString());
                }
                case 60 -> { require(snapshot().devices() == 1, "card device search"); row(0); }
                case 61 -> { row(1); capture("frequency-card-open-gui.png"); click(tr("configure")); }
                case 62 -> {
                    require(mc.screen instanceof appeng.client.gui.AEBaseScreen<?> nativeScreen
                            && nativeScreen.getMenu() instanceof appeng.menu.implementations.InterfaceMenu,
                            "card opens native interface GUI");
                    capture("frequency-card-native-interface.png"); click(tr("return_devices"));
                }
                case 63 -> {
                    require(((NetworkManagementScreen)mc.screen).getMenu().fromCard()
                            && snapshot().devices() == 1 && snapshot().total() == 2
                            && ((AETextField)field("search")).getValue().equals(Component.translatable("block.ae2.interface").getString()),
                            "native GUI returns to card management with its query");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_HOME);
                }
                case 64 -> {
                    assertCardAuto(false, false); assertFrame(); capture("frequency-card-compact-home.png");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var terminal = appeng.core.definitions.AEItems.WIRELESS_TERMINAL.stack();
                        var item = (appeng.items.tools.powered.WirelessTerminalItem)terminal.getItem();
                        item.injectAEPower(terminal, item.getAEMaxPower(terminal), Actionable.MODULATE);
                        ((appeng.api.upgrades.IUpgradeableItem)item).getUpgrades(terminal).setItemDirect(0, player.getMainHandItem().copy());
                        player.closeContainer();
                        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
                        // This fixture replaces the held item outside a menu with player slots.
                        // Deliver the terminal stack before a native menu resolves its client-side item host.
                        player.inventoryMenu.broadcastChanges();
                        appeng.menu.MenuOpener.open(com.moakiee.ae2lt.menu.FrequencyMenu.TYPE, player,
                                appeng.menu.locator.MenuLocators.forInventorySlot(player.getInventory().selected));
                    });
                }
                case 65 -> {
                    require(((FrequencyScreen)mc.screen).getMenu().hasParentMenu(), "terminal card retains parent");
                    nav(com.moakiee.ae2lt.menu.FrequencyPage.TAB_DEVICES);
                }
                case 66 -> {
                    var manager = ((NetworkManagementScreen)mc.screen).getMenu();
                    require(manager.fromCard() && manager.hasParentMenu(), "terminal card management retains parent");
                    assertFrame(); capture("frequency-terminal-card-management.png");
                    clickKey("ae2lt.gui.button.return_to_terminal");
                }
                case 67 -> {
                    require(mc.screen instanceof appeng.client.gui.AEBaseScreen<?> terminalScreen
                            && terminalScreen.getMenu().getType() == appeng.menu.me.common.MEStorageMenu.WIRELESS_TYPE,
                            "management returns to native wireless terminal");
                    capture("frequency-card-return-terminal.png"); finish(null); return;
                }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static void assertCardAuto(boolean channels, boolean energy) throws Exception {
        var menu = ((FrequencyScreen)Minecraft.getInstance().screen).getMenu();
        require(menu.isCardMode() && menu.isAutoConnect() == channels && menu.isAutoConnectEnergy() == energy,
                "independent card preferences round-trip to client");
        require(frequencyField("channelAutoButton") instanceof appeng.client.gui.widgets.ToggleButton
                && frequencyField("energyAutoButton") instanceof appeng.client.gui.widgets.ToggleButton,
                "automatic settings use AE2 native toggles");
    }
    private static Object frequencyField(String name) throws Exception {
        var field = FrequencyScreen.class.getDeclaredField(name); field.setAccessible(true); return field.get(Minecraft.getInstance().screen);
    }
    private static int integerField(String name) throws Exception {
        var field = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField(name);
        field.setAccessible(true); return field.getInt(Minecraft.getInstance().screen);
    }
    private static void assertFrame() throws Exception {
        if (Minecraft.getInstance().screen instanceof NetworkManagementScreen) {
            var tabs = (java.util.Map<?, Button>) field("modeButtons");
            require(tabs.size() == 3, "all three network views directly selectable");
            for (var button : tabs.values()) require(Minecraft.getInstance().font.width(button.getMessage()) <= button.getWidth() - 8,
                    "network view label fits without scrolling or clipping");
        }
        int expectedWidth = com.moakiee.ae2lt.client.gui.FrequencyNavigation.WIDTH;
        int expectedHeight = com.moakiee.ae2lt.client.gui.FrequencyNavigation.HEIGHT;
        var screen = Minecraft.getInstance().screen;
        frameLeft = integerField("leftPos"); frameTop = integerField("topPos");
        require(integerField("imageWidth") == expectedWidth && integerField("imageHeight") == expectedHeight
                && frameLeft == (screen.width - expectedWidth) / 2 && frameTop == (screen.height - expectedHeight) / 2,
                "identical centered frame for all frequency and management pages");
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible) {
            require(widget.getX() >= frameLeft - 18 && widget.getX() + widget.getWidth() <= frameLeft + expectedWidth
                    && widget.getY() >= frameTop - 24 && widget.getY() + widget.getHeight() <= frameTop + expectedHeight,
                    "widget fits compact frame: " + widget.getMessage().getString());
        }
        var buttons = Minecraft.getInstance().screen.children().stream().filter(e -> e instanceof com.moakiee.ae2lt.client.gui.FrequencyNavigation.PageButton).toList();
        require(buttons.size() == 4, "four navigation buttons with frequency settings nested");
        require(buttons.stream().filter(e -> ((com.moakiee.ae2lt.client.gui.FrequencyNavigation.PageButton)e).isSelected()).count() == 1, "exactly one selected navigation page");
    }
    private static void nav(com.moakiee.ae2lt.menu.FrequencyPage page) throws Exception {
        pendingPage = page; if (advanceNavigation()) pendingPage = null;
    }
    private static boolean advanceNavigation() throws Exception {
        var target = pendingPage; var screen = Minecraft.getInstance().screen;
        if (target.isManagement()) {
            if (screen instanceof FrequencyScreen) { click(tr("network")); return false; }
            if (field("mode") != target.managementMode()) {
                clickButton(((java.util.Map<com.moakiee.ae2lt.logic.management.ManagementMode, Button>)field("modeButtons")).get(target.managementMode()));
                return false;
            }
            return true;
        }
        if (screen instanceof FrequencyScreen && frequencyField("currentTab") == target) return true;
        if (target == com.moakiee.ae2lt.menu.FrequencyPage.TAB_CREATE || target == com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING) {
            if (screen instanceof FrequencyScreen && frequencyField("currentTab") == target.navigationPage()) clickKey(target == com.moakiee.ae2lt.menu.FrequencyPage.TAB_SETTING
                    ? "ae2lt.gui.tab.setting" : target.getTranslationKey());
            else clickKey(target.navigationPage().navigationTranslationKey());
        } else clickKey(target.navigationTranslationKey());
        return false;
    }
    private static Button buttonByKey(String key) {
        var label = Component.translatable(key).getString();
        return Minecraft.getInstance().screen.children().stream()
                .filter(e -> e instanceof Button button && button.visible && button.getMessage().getString().equals(label))
                .map(e -> (Button)e).findFirst().orElseThrow();
    }
    private static void clickKey(String key) { click(Component.translatable(key).getString()); }
    private static void clickContaining(String text) {
        clickButton(Minecraft.getInstance().screen.children().stream().filter(e -> e instanceof Button b && b.active && b.visible
                && b.getMessage().getString().contains(text)).map(e -> (Button)e).findFirst().orElseThrow());
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
    private static void focusSearch() throws Exception {
        var screen = Minecraft.getInstance().screen;
        var bounds = ((AETextField)field("search")).getTooltipArea();
        double x = bounds.getX() + 10, y = bounds.getY() + 6;
        require(screen.mouseClicked(x, y, 0), "native search focus click"); screen.mouseReleased(x, y, 0);
    }
    private static void assertSelectedTab(boolean power) throws Exception {
        var tab = (appeng.client.gui.widgets.TabButton)field("managementTab");
        require(!tab.isFocused() && tab.isSelected(), "network tab remains selected after focus change");
        require(field("mode") == (power ? com.moakiee.ae2lt.logic.management.ManagementMode.ENERGY
                : com.moakiee.ae2lt.logic.management.ManagementMode.DEVICES), "mode remains selected");
        require(tab.getStyle() == appeng.client.gui.widgets.TabButton.Style.BOX, "shared navigation style");
    }

    private static void locate(int index) throws Exception {
        var mc = Minecraft.getInstance(); var screen = mc.screen;
        var button = ((Button[])field("locateButtons"))[index];
        // A sync packet can refresh snapshot between renders. Assert the location of the
        // row actually displayed on this button, which is the target of its click handler.
        var rowField = button.getClass().getDeclaredField("row"); rowField.setAccessible(true);
        var displayedRow = (com.moakiee.ae2lt.logic.management.ManagementRow)rowField.get(button);
        String address = NetworkManagementScreen.address(displayedRow.target());
        mc.keyboardHandler.setClipboard("not copied yet");
        clickButton(button);
        require(mc.screen == screen, "row location keeps management open like EAE");
        require(mc.keyboardHandler.getClipboard().equals("not copied yet"), "locating does not silently overwrite clipboard");
        verifyChatCopy(address);
    }
    private static void verifyChatCopy(String address) throws Exception {
        var mc = Minecraft.getInstance();
        var messages = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages"); messages.setAccessible(true);
        var latest = ((java.util.List<net.minecraft.client.GuiMessage>) messages.get(mc.gui.getChat())).getFirst().content();
        var clickable = latest.visit((net.minecraft.network.chat.Style style, String text) ->
                style.getClickEvent() != null ? java.util.Optional.of(style) : java.util.Optional.empty(), net.minecraft.network.chat.Style.EMPTY).orElseThrow();
        require(clickable.getClickEvent().getAction() == net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, "chat coordinates use native clipboard action");
        require(mc.screen.handleComponentClicked(clickable), "native chat link handler accepts copy");
        require(mc.keyboardHandler.getClipboard().equals(address), "chat copies complete dimension and coordinates");
    }
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
        done = true; String result = problem == null ? "NETWORK_MANAGEMENT_CLIENT_PASS: native ME grouping, localized search, inline expansion, inline location, clickable coordinate copy, cross-dimension reporting, power windows/sorting, induction/accessor paths, scrollbar, exact disconnect, selected tabs after focus changes, four-page navigation with nested frequency settings, live disconnect/reconnect from the frequency list, direct network view selection, independent native channel/FE switches, three network modes in a compact management frame, all frequency forms and modals, create/edit round-trips, per-page state restoration, full held/terminal card management, native device GUI round-trip and terminal return"
                : "NETWORK_MANAGEMENT_CLIENT_FAIL phase=" + phase + ": " + problem;
        if (problem == null && Boolean.getBoolean("ae2lt.cardManagementOnly"))
            result = "CARD_MANAGEMENT_CLIENT_PASS: compact overview, independent bottom switches, full held/terminal card management, native device GUI round-trip, restored query and native terminal return";
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("wireless-energy-menu-result.txt"), result); } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
