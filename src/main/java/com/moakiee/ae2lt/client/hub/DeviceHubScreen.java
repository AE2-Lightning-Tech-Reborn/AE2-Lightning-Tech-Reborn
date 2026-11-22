package com.moakiee.ae2lt.client.hub;

import appeng.client.gui.style.PaletteColor;
import static com.moakiee.ae2lt.client.ldlib.LdlibScreens.at;
import static com.moakiee.ae2lt.client.ldlib.LdlibScreens.either;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.client.gui.FittingText;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.menu.hub.DeviceHubDisplayRules;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.hub.DeviceHubActionPacket;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.client.gui.AE2GuiPalette;

/**
 * Device hub (Celestweave armor pieces and railgun). LDLib lays out the foreground,
 * refreshed from the synced menu every frame and drawn after the native container.
 * Atlas backgrounds stay in the background pass; actions go through
 * {@link DeviceHubActionPacket} as before.
 */
public class DeviceHubScreen extends AbstractContainerScreen<DeviceHubMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/armor_settings_gui.png");
    private static final ResourceLocation CHECKBOX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/checkbox.png");
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(TEXTURE, 256, 256);
    private static final LdlibStyleSprite CHECKBOX_ATLAS = new LdlibStyleSprite(CHECKBOX_TEXTURE, 64, 64);

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 223;

    private static final int TEXT_ON_DARK_BG = 0xFFFFFFFF;
    private static final int ROW_HOVER = 0x304D4D67;
    private static final int ROW_SELECTED = 0x404D4D67;
    private static final int ROW_DIVIDER = 0xFF878DA4;
    private static final int BUTTON_BORDER = 0xFF4D5268;
    private static final int BUTTON_BORDER_HOVER = 0xFF8C96B3;
    private static final int BUTTON_DISABLED = 0xFF6B7086;
    private static final int BUTTON_FILL = 0xFF69708A;
    private static final int BUTTON_FILL_HOVER = 0xFF7B849F;
    private static final int BUTTON_FILL_DISABLED = 0xFF7D839B;
    private static final int BUTTON_TEXT = TEXT_ON_DARK_BG;

    private static final int TAB_COUNT = 5;
    private static final int TAB_WIDTH = 31;
    private static final int TAB_HEIGHT = 25;
    private static final int TAB_ICON_SIZE = 16;
    private static final int TAB_ACTIVE_SRC_Y = 225;
    private static final int TAB_ACTIVE_H = 26;
    private static final int[] TAB_X = {0, 31, 62, 93, 145};
    private static final int[] TAB_ACTIVE_SRC_X = {0, 31, 62, 93, 145};

    private static final int STATUS_X = 12;
    private static final int STATUS_Y = 36;
    private static final int STATUS_ICON_X = 13;
    private static final int STATUS_ICON_Y = 42;
    private static final int STATUS_TEXT_X = 34;
    private static final int STATUS_NAME_Y = 40;
    private static final int STATUS_LINE_Y = 53;
    private static final int STATUS_RIGHT = 166;

    private static final int MODULE_HEADER_X = 12;
    private static final int MODULE_HEADER_Y = 69;
    private static final int MODULE_LIST_X = 19;
    private static final int MODULE_LIST_Y = 83;
    private static final int MODULE_LIST_RIGHT = 166;
    private static final int MODULE_ROW_H = 14;
    private static final int MODULE_VISIBLE_ROWS = 4;
    private static final int MODULE_CHECKBOX_X = 142;

    private static final int SCROLL_X = 10;
    private static final int SCROLL_Y = 83;
    private static final int SCROLL_H = 56;
    private static final int SCROLL_SRC_X = 180;
    private static final int SCROLL_SRC_Y = 0;
    private static final int SCROLL_HOVER_SRC_Y = 17;
    private static final int SCROLL_SRC_W = 7;
    private static final int SCROLL_SRC_H = 15;
    private static final int SCROLL_HOVER_SRC_H = 14;

    private static final int CONFIG_X = 12;
    private static final int CONFIG_ROW_X = MODULE_LIST_X;
    private static final int CONFIG_HEADER_Y = 144;
    private static final int CONFIG_Y = 160;
    private static final int CONFIG_BUTTON_X = 124;
    private static final int CONFIG_BUTTON_W = 40;
    private static final int CONFIG_BUTTON_H = 12;
    private static final int CONFIG_ROW_H = 16;
    private static final int CONFIG_VISIBLE_ROWS = 3;
    private static final int CONFIG_SCROLL_Y = CONFIG_Y;
    private static final int CONFIG_SCROLL_H = 46;
    private static final int RAILGUN_SETTING_COUNT = 7;
    private static final int RAILGUN_SETTING_TERRAIN = 0;
    private static final int RAILGUN_SETTING_PVP = 1;
    private static final int RAILGUN_SETTING_SOUND = 2;
    private static final int RAILGUN_SETTING_CHAIN_DAMAGE = 3;
    private static final int RAILGUN_SETTING_CHARGED_SPLASH = 4;
    private static final int RAILGUN_SETTING_EXECUTION_MODE = 5;
    private static final int RAILGUN_SETTING_EHV_BEAM = 6;

    private static final int CHECKBOX_WIDTH = 22;
    private static final int CHECKBOX_HEIGHT = 12;
    private static final int CHECKBOX_OFF_SRC_Y = 28;
    private static final int CHECKBOX_ON_SRC_Y = 40;

    private static final IGuiTexture CHECKBOX_OFF = CHECKBOX_ATLAS.slice(0, CHECKBOX_OFF_SRC_Y,
            CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
    private static final IGuiTexture CHECKBOX_ON = CHECKBOX_ATLAS.slice(0, CHECKBOX_ON_SRC_Y,
            CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
    private static final IGuiTexture THUMB = tex(SCROLL_SRC_X, SCROLL_SRC_Y, SCROLL_SRC_W, SCROLL_SRC_H);
    private static final IGuiTexture THUMB_HOVER = ATLAS.fixedSlice(
            SCROLL_SRC_X, SCROLL_HOVER_SRC_Y, SCROLL_SRC_W, SCROLL_HOVER_SRC_H);

    private static final String[] TAB_LABEL_KEYS = {
            "ae2lt.device_hub.tab.helmet",
            "ae2lt.device_hub.tab.chestplate",
            "ae2lt.device_hub.tab.leggings",
            "ae2lt.device_hub.tab.boots",
            "ae2lt.device_hub.tab.railgun"
    };
    private static final String[] TAB_REQUIRED_KEYS = {
            "ae2lt.device_hub.tab.required.helmet",
            "ae2lt.device_hub.tab.required.chestplate",
            "ae2lt.device_hub.tab.required.leggings",
            "ae2lt.device_hub.tab.required.boots",
            "ae2lt.device_hub.tab.required.railgun"
    };

    private final List<Button> tabButtons = new ArrayList<>(TAB_COUNT);
    private ModularUI embeddedUI;
    private VirtualScrollerView<ModuleEntry> moduleRows;
    private VirtualScrollerView<ConfigEntry> configRows;
    private List<ModuleEntry> moduleEntries = List.of();
    private List<ConfigEntry> configEntries = List.of();
    private Label noDevice;
    private UIElement devicePanel;
    private Label deviceName;
    private Label statusLine;
    private Label moduleHeader;
    private Label configHeader;
    private Scroller moduleScroller;
    private Scroller configScroller;

    private int scrollOffset = 0;
    private int configScrollOffset = 0;
    private int lastConfigTab = -1;
    private int lastConfigModule = -1;
    private int frameMouseX;
    private int frameMouseY;

    public DeviceHubScreen(DeviceHubMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight + 100;
    }

    @Override
    protected void init() {
        if (embeddedUI != null && !embeddedUI.isRemoved()) {
            embeddedUI.onRemoved();
        }
        super.init();
        this.titleLabelX = 0;
        this.titleLabelY = -100;
        tabButtons.clear();
        moduleEntries = List.of();
        configEntries = List.of();
        // Keep the tree in the input/narration lists; its foreground is drawn after the container.
        embeddedUI = LdlibScreens.embed(this, buildRoot(), this::addWidget);
        embeddedUI.setDrawTooltips(false);
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        ATLAS.draw(gfx, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        renderSelectedTabTexture(gfx, menu.getSelectedTab());
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        frameMouseX = mouseX;
        frameMouseY = mouseY;
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        refresh();
        embeddedUI.getWidget().render(gfx, mouseX, mouseY, partialTick);
        if (menu.isEntityEquipment()) {
            gfx.drawString(font, FittingText.withEllipsis(font, title.getString(), GUI_WIDTH),
                    leftPos, topPos - 12, TEXT_ON_DARK_BG, true);
        }
        renderTabTooltips(gfx, mouseX, mouseY);
        renderTooltip(gfx, mouseX, mouseY);
    }

    private void renderTabTooltips(GuiGraphics gfx, int mouseX, int mouseY) {
        int availability = menu.getTabAvailability();
        for (int tab = 0; tab < TAB_COUNT; tab++) {
            if (!within(mouseX - leftPos, mouseY - topPos, TAB_X[tab], 0, TAB_WIDTH, TAB_HEIGHT)) {
                continue;
            }
            boolean available = (availability & (1 << tab)) != 0;
            ItemStack stack = available ? tabStack(tab) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                gfx.renderTooltip(font, stack, mouseX, mouseY);
            } else {
                gfx.renderTooltip(font, Component.translatable(
                        available ? TAB_LABEL_KEYS[tab] : TAB_REQUIRED_KEYS[tab]), mouseX, mouseY);
            }
            return;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            cycleTab((modifiers & 1) != 0 ? -1 : 1);
            return true;
        }
        if (keyCode == 263) {
            cycleTab(-1);
            return true;
        }
        if (keyCode == 262) {
            cycleTab(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- LDLib tree ---------------------------------------------------------------------

    private UIElement buildRoot() {
        var root = new UIElement();
        root.layout(l -> l.width(GUI_WIDTH).height(GUI_HEIGHT));

        for (int i = 0; i < TAB_COUNT; i++) {
            root.addChild(buildTab(i));
        }

        noDevice = at(label(Component.translatable("ae2lt.device_hub.no_device"), TEXT_ON_DARK_BG),
                STATUS_X, STATUS_Y + 9, STATUS_RIGHT - STATUS_X, 9);
        root.addChild(noDevice);

        // This container overlaps the tabs; only its children should participate in hit testing.
        devicePanel = at(new UIElement().setAllowHitTest(false), 0, 0, GUI_WIDTH, GUI_HEIGHT);
        root.addChild(devicePanel);

        devicePanel.addChild(at(LdlibScreens.unadornedItemIcon(this::selectedDeviceStack),
                STATUS_ICON_X, STATUS_ICON_Y, 16, 16));
        deviceName = at(label(Component.empty(), TEXT_ON_DARK_BG),
                STATUS_TEXT_X, STATUS_NAME_Y, STATUS_RIGHT - STATUS_TEXT_X, 9);
        statusLine = at(label(Component.empty(), TEXT_ON_DARK_BG),
                STATUS_TEXT_X, STATUS_LINE_Y, STATUS_RIGHT - STATUS_TEXT_X, 9);
        devicePanel.addChildren(deviceName, statusLine);

        moduleHeader = at(label(Component.translatable("ae2lt.device_hub.modules"),
                AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR)),
                MODULE_HEADER_X, MODULE_HEADER_Y, STATUS_RIGHT - MODULE_HEADER_X, 9);
        devicePanel.addChild(moduleHeader);
        moduleRows = LdlibScreens.virtualRows(MODULE_ROW_H, this::moduleRow);
        allowRowOverflow(moduleRows);
        devicePanel.addChild(at(moduleRows, MODULE_LIST_X - 1, MODULE_LIST_Y - 1,
                MODULE_LIST_RIGHT - MODULE_LIST_X + 1, MODULE_ROW_H * MODULE_VISIBLE_ROWS));
        moduleScroller = LdlibScreens.displayThumbScroller(THUMB, THUMB_HOVER, SCROLL_SRC_H, SCROLL_H);
        renderScrollBar(devicePanel, moduleScroller, SCROLL_Y, SCROLL_H);

        configHeader = at(label(Component.empty(),
                AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR)),
                CONFIG_X, CONFIG_HEADER_Y, STATUS_RIGHT - CONFIG_X, 9);
        devicePanel.addChild(configHeader);
        configRows = LdlibScreens.virtualRows(CONFIG_ROW_H, this::configRow);
        allowRowOverflow(configRows);
        devicePanel.addChild(at(configRows, CONFIG_ROW_X, CONFIG_Y - 2,
                CONFIG_BUTTON_X + CONFIG_BUTTON_W + 1 - CONFIG_ROW_X, CONFIG_ROW_H * CONFIG_VISIBLE_ROWS));
        renderConfigScrollBar(devicePanel);
        return root;
    }

    private static void allowRowOverflow(VirtualScrollerView<?> rows) {
        rows.style(style -> style.overflowVisible(true));
        rows.verticalContainer.style(style -> style.overflowVisible(true));
        rows.viewPort(view -> view.style(style -> style.overflowVisible(true)));
        rows.viewContainer.style(style -> style.overflowVisible(true));
    }

    private UIElement buildTab(int tab) {
        var element = new UIElement();
        var button = new Button().noText();
        tabButtons.add(button);
        button.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(IGuiTexture.EMPTY).pressedTexture(IGuiTexture.EMPTY));
        button.addChild(at(LdlibScreens.unadornedItemIcon(() -> tabDisplayStack(tab)),
                (TAB_WIDTH - TAB_ICON_SIZE) / 2, 5, TAB_ICON_SIZE, TAB_ICON_SIZE));
        button.setOnClick(event -> sendAction(DeviceHubActionPacket.ACTION_SELECT_TAB, tab));
        element.addChild(at(button, 0, 0, TAB_WIDTH, TAB_HEIGHT));
        return at(element, TAB_X[tab], 0, TAB_WIDTH, TAB_HEIGHT);
    }

    /** Raised-tab sprite shown behind the selected tab. */
    private void renderSelectedTabTexture(GuiGraphics gfx, int selectedTab) {
        if (selectedTab < 0 || selectedTab >= TAB_COUNT) {
            return;
        }
        ATLAS.draw(gfx, leftPos + TAB_X[selectedTab], topPos,
                TAB_ACTIVE_SRC_X[selectedTab], TAB_ACTIVE_SRC_Y, TAB_WIDTH, TAB_ACTIVE_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int selectedTab = menu.getSelectedTab();
        if (button == 0 && selectedTab == lastConfigTab
                && (menu.getTabAvailability() & (1 << selectedTab)) != 0
                && (clickConfig(mouseX - leftPos, mouseY - topPos)
                        || clickModule(mouseX - leftPos, mouseY - topPos))) {
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickConfig(double x, double y) {
        boolean railgun = menu.getSelectedTab() == DeviceHubMenu.TAB_RAILGUN;
        if (!railgun && lastConfigModule != menu.getSelectedModuleIndex()) {
            return false;
        }
        int count = configCount();
        int first = DeviceHubDisplayRules.clampScrollOffset(configScrollOffset, count, CONFIG_VISIBLE_ROWS);
        for (int row = 0; row < CONFIG_VISIBLE_ROWS && first + row < count; row++) {
            int index = first + row;
            int top = CONFIG_Y + row * CONFIG_ROW_H;
            boolean checkbox = railgun && index != RAILGUN_SETTING_EXECUTION_MODE;
            if (checkbox ? within(x, y, MODULE_CHECKBOX_X, top, CHECKBOX_WIDTH, CHECKBOX_HEIGHT)
                    : within(x, y, CONFIG_BUTTON_X, top - 1, CONFIG_BUTTON_W, CONFIG_BUTTON_H)) {
                if (railgun) {
                    sendAction(railgunSettingAction(index), 0);
                } else if (menu.getModuleConfigEditable().get(index)) {
                    sendAction(DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, index);
                } else {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickModule(double x, double y) {
        if (menu.getSelectedTab() == DeviceHubMenu.TAB_RAILGUN) {
            return false;
        }
        int count = menu.getModuleNameKeys().size();
        int first = DeviceHubDisplayRules.clampScrollOffset(scrollOffset, count, MODULE_VISIBLE_ROWS);
        for (int row = 0; row < MODULE_VISIBLE_ROWS && first + row < count; row++) {
            int top = MODULE_LIST_Y + row * MODULE_ROW_H;
            if (within(x, y, MODULE_CHECKBOX_X, top + 1, CHECKBOX_WIDTH, CHECKBOX_HEIGHT)) {
                sendAction(DeviceHubActionPacket.ACTION_TOGGLE_MODULE, first + row);
                return true;
            }
            if (within(x, y, MODULE_LIST_X, top, MODULE_LIST_RIGHT - MODULE_LIST_X, MODULE_ROW_H)) {
                sendAction(DeviceHubActionPacket.ACTION_SELECT_MODULE, first + row);
                return true;
            }
        }
        return false;
    }

    private static boolean within(double x, double y, int left, int top, int width, int height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (moduleScroller != null && x >= 8 && x <= 168 && y >= 78 && y <= 142) {
            scrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                    scrollOffset - (int) Math.signum(scrollY), menu.getModuleNameKeys().size(), MODULE_VISIBLE_ROWS);
            refresh();
            return true;
        }
        int configCount = configCount();
        if (configScroller != null && configCount > 0
                && x >= 8 && x <= 175 && y >= CONFIG_HEADER_Y && y <= GUI_HEIGHT) {
            configScrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                    configScrollOffset - (int) Math.signum(scrollY), configCount, CONFIG_VISIBLE_ROWS);
            refresh();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- state --------------------------------------------------------------------------

    private void refresh() {
        if (devicePanel == null) {
            return;
        }
        refreshTabButtons();

        int selectedTab = menu.getSelectedTab();
        boolean railgunTab = selectedTab == DeviceHubMenu.TAB_RAILGUN;
        resetConfigScrollWhenSelectionChanges(selectedTab, railgunTab ? -1 : menu.getSelectedModuleIndex());

        boolean hasDevice = (menu.getTabAvailability() & (1 << selectedTab)) != 0;
        noDevice.setDisplay(!hasDevice);
        devicePanel.setDisplay(hasDevice);
        if (!hasDevice) {
            return;
        }

        int headerColor = AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR);
        moduleHeader.textStyle(style -> style.textColor(headerColor));
        configHeader.textStyle(style -> style.textColor(headerColor));
        String name = menu.getDeviceName();
        deviceName.setText(Component.literal(FittingText.withEllipsis(
                font, name, STATUS_RIGHT - STATUS_TEXT_X)));
        deviceName.setDisplay(!name.isEmpty());
        statusLine.setText(Component.translatable("ae2lt.device_hub.status.line", statusText(railgunTab)));

        updateModuleRows(railgunTab);

        configHeader.setText(Component.translatable(railgunTab
                ? "ae2lt.device_hub.settings"
                : "ae2lt.celestweave.screen.module_options"));
        updateConfigRows(railgunTab);
    }

    private void updateModuleRows(boolean railgunTab) {
        var keys = menu.getModuleNameKeys();
        var counts = menu.getModuleCounts();
        var flags = menu.getModuleEnabled();
        var items = new ArrayList<ModuleEntry>(keys.size());
        for (int index = 0; index < keys.size(); index++) {
            int count = index < counts.size() ? counts.get(index) : 1;
            boolean enabled = index < flags.size() && flags.get(index);
            items.add(new ModuleEntry(index, keys.get(index), count, enabled, !railgunTab));
        }
        if (!items.equals(moduleEntries)) {
            moduleEntries = List.copyOf(items);
            moduleRows.setItems(moduleEntries);
        }
        scrollOffset = DeviceHubDisplayRules.clampScrollOffset(scrollOffset, items.size(), MODULE_VISIBLE_ROWS);
        moduleScroller.setDisplay(items.size() > MODULE_VISIBLE_ROWS);
        syncScroller(moduleScroller, moduleRows, scrollOffset, MODULE_VISIBLE_ROWS);
    }

    private void updateConfigRows(boolean railgunTab) {
        int count = configCount();
        var items = new ArrayList<ConfigEntry>(count);
        for (int index = 0; index < count; index++) {
            if (railgunTab) {
                boolean checkbox = index != RAILGUN_SETTING_EXECUTION_MODE;
                var value = checkbox ? Component.empty() : Component.translatable(menu.getExecutionMode().translationKey());
                items.add(new ConfigEntry(index, railgunSettingLabel(index), value, checkbox, railgunSettingEnabled(index),
                        true));
            } else {
                items.add(new ConfigEntry(index, moduleConfigLabel(index), Component.literal(menu.getModuleConfigValues().get(index)),
                        false, false, menu.getModuleConfigEditable().get(index)));
            }
        }
        if (!items.equals(configEntries)) {
            configEntries = List.copyOf(items);
            configRows.setItems(configEntries);
        }
        if (count > 0) {
            configScrollOffset = DeviceHubDisplayRules.clampScrollOffset(configScrollOffset, count, CONFIG_VISIBLE_ROWS);
        }
        configScroller.setDisplay(railgunTab || count > CONFIG_VISIBLE_ROWS);
        syncScroller(configScroller, configRows, configScrollOffset, CONFIG_VISIBLE_ROWS);
    }

    /** Updates whether each tab can be selected; icon stacks are read during drawing. */
    private void refreshTabButtons() {
        int availability = menu.getTabAvailability();
        int selectedTab = menu.getSelectedTab();
        for (int i = 0; i < TAB_COUNT; i++) {
            tabButtons.get(i).setActive((availability & (1 << i)) != 0 && i != selectedTab);
        }
    }

    private void syncScroller(Scroller scroller, VirtualScrollerView<?> rows, int offset, int visibleRows) {
        int max = Math.max(0, rows.getItemCount() - visibleRows);
        int height = scroller == moduleScroller ? SCROLL_H : CONFIG_SCROLL_H;
        LdlibScreens.syncRowThumb(scroller, offset, max);
        LdlibScreens.positionThumbPixels(scroller,
                (height - SCROLL_SRC_H) * offset / Math.max(1, max), SCROLL_SRC_H);
        LdlibScreens.scrollVirtualRows(rows, offset, max);
    }

    private int configCount() {
        return menu.getSelectedTab() == DeviceHubMenu.TAB_RAILGUN ? railgunSettingCount() : moduleConfigCount();
    }

    private Component statusText(boolean railgunTab) {
        if (!railgunTab) {
            return Component.translatable(DeviceHubDisplayRules.armorStatusKey(
                    menu.hasCore(),
                    menu.isPowered()));
        }
        return Component.translatable(menu.isPowered()
                ? "ae2lt.device_hub.status.normal"
                : "ae2lt.device_hub.status.unpowered");
    }

    private int railgunSettingCount() {
        return menu.hasEhvBeamModule() ? RAILGUN_SETTING_COUNT : RAILGUN_SETTING_COUNT - 1;
    }

    private Component railgunSettingLabel(int settingIndex) {
        return switch (settingIndex) {
            case RAILGUN_SETTING_EHV_BEAM -> Component.translatable("ae2lt.railgun.config.ehv_beam");
            case RAILGUN_SETTING_TERRAIN -> Component.translatable("ae2lt.device_hub.setting.terrain");
            case RAILGUN_SETTING_PVP -> Component.translatable("ae2lt.device_hub.setting.pvp");
            case RAILGUN_SETTING_SOUND -> Component.translatable("ae2lt.device_hub.setting.sound");
            case RAILGUN_SETTING_CHAIN_DAMAGE -> Component.translatable("ae2lt.device_hub.setting.chain_damage");
            case RAILGUN_SETTING_CHARGED_SPLASH ->
                Component.translatable("ae2lt.railgun.config.charged_splash");
            case RAILGUN_SETTING_EXECUTION_MODE ->
                Component.translatable("ae2lt.railgun.config.overload_execution_mode");
            default -> Component.empty();
        };
    }

    private boolean railgunSettingEnabled(int settingIndex) {
        return switch (settingIndex) {
            case RAILGUN_SETTING_EHV_BEAM -> menu.isEhvBeamEnabled();
            case RAILGUN_SETTING_TERRAIN -> menu.isTerrainDestruction();
            case RAILGUN_SETTING_PVP -> menu.isPvp();
            case RAILGUN_SETTING_SOUND -> menu.isSoundEnabled();
            case RAILGUN_SETTING_CHAIN_DAMAGE -> menu.isChainDamage();
            case RAILGUN_SETTING_CHARGED_SPLASH -> menu.isChargedSplash();
            default -> false;
        };
    }

    private static int railgunSettingAction(int settingIndex) {
        return switch (settingIndex) {
            case RAILGUN_SETTING_EHV_BEAM -> DeviceHubActionPacket.ACTION_TOGGLE_EHV_BEAM;
            case RAILGUN_SETTING_TERRAIN -> DeviceHubActionPacket.ACTION_TOGGLE_TERRAIN;
            case RAILGUN_SETTING_PVP -> DeviceHubActionPacket.ACTION_TOGGLE_PVP;
            case RAILGUN_SETTING_SOUND -> DeviceHubActionPacket.ACTION_TOGGLE_SOUND;
            case RAILGUN_SETTING_CHAIN_DAMAGE -> DeviceHubActionPacket.ACTION_TOGGLE_CHAIN_DAMAGE;
            case RAILGUN_SETTING_CHARGED_SPLASH -> DeviceHubActionPacket.ACTION_TOGGLE_CHARGED_SPLASH;
            case RAILGUN_SETTING_EXECUTION_MODE -> DeviceHubActionPacket.ACTION_CYCLE_EXECUTION_MODE;
            default -> -1;
        };
    }

    private int moduleConfigCount() {
        return Math.min(
                Math.min(Math.min(menu.getModuleConfigKeys().size(), menu.getModuleConfigLabels().size()),
                        menu.getModuleConfigValues().size()),
                menu.getModuleConfigEditable().size());
    }

    private Component moduleConfigLabel(int index) {
        String key = menu.getModuleConfigKeys().get(index);
        if (key != null && !key.isBlank()) {
            return Component.translatable("ae2lt.celestweave.config." + key);
        }
        return Component.literal(menu.getModuleConfigLabels().get(index));
    }

    private static Component moduleName(String nameKey, int count) {
        Component name = Component.translatable(nameKey);
        if (count > 1) {
            return Component.translatable("ae2lt.device_hub.module.counted", name, count);
        }
        return name;
    }

    /** The settings list reuses the module list's left-hand scrollbar track and sprites. */
    private void renderConfigScrollBar(UIElement parent) {
        configScroller = LdlibScreens.displayThumbScroller(THUMB, THUMB_HOVER, SCROLL_SRC_H, CONFIG_SCROLL_H);
        renderScrollBar(parent, configScroller, CONFIG_SCROLL_Y, CONFIG_SCROLL_H);
    }

    private void renderScrollBar(UIElement parent, Scroller scroller, int relativeY, int height) {
        scroller.scrollerStyle(s -> s.scrollBarSize(SCROLL_SRC_H * 100.0F / height));
        parent.addChild(at(scroller, SCROLL_X, relativeY, SCROLL_SRC_W, height));
    }

    private void resetConfigScrollWhenSelectionChanges(int selectedTab, int selectedModule) {
        if (selectedTab == lastConfigTab && selectedModule == lastConfigModule) {
            return;
        }
        lastConfigTab = selectedTab;
        lastConfigModule = selectedModule;
        configScrollOffset = 0;
    }

    private void cycleTab(int dir) {
        int current = menu.getSelectedTab();
        int tabMask = menu.getTabAvailability();
        for (int attempt = 0; attempt < TAB_COUNT; attempt++) {
            current = (current + dir + TAB_COUNT) % TAB_COUNT;
            if ((tabMask & (1 << current)) != 0) {
                sendAction(DeviceHubActionPacket.ACTION_SELECT_TAB, current);
                playClick();
                return;
            }
        }
    }

    private void playClick() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            UISoundUtils.playButtonClickSound();
        }
    }

    private static void sendAction(int action, int value) {
        PacketDistributor.sendToServer(new DeviceHubActionPacket(action, value));
    }

    private static IGuiTexture tex(int u, int v, int width, int height) {
        return ATLAS.slice(u, v, width, height);
    }

    private static UIElement checkbox(boolean on) {
        return new UIElement().setAllowHitTest(false)
                .style(style -> style.background(on ? CHECKBOX_ON : CHECKBOX_OFF));
    }

    private static IGuiTexture valueButton(int border, int fill) {
        return (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            int buttonX = (int) x;
            int buttonY = (int) y;
            graphics.fill(buttonX - 1, buttonY - 1,
                    buttonX + CONFIG_BUTTON_W + 1, buttonY + CONFIG_BUTTON_H + 1, border);
            graphics.fill(buttonX, buttonY, buttonX + CONFIG_BUTTON_W, buttonY + CONFIG_BUTTON_H, fill);
        };
    }

    private ItemStack selectedDeviceStack() {
        return tabStack(menu.getSelectedTab());
    }

    private ItemStack tabDisplayStack(int tab) {
        ItemStack equipped = tabStack(tab);
        return equipped.isEmpty() ? defaultTabStack(tab) : equipped;
    }

    private static ItemStack defaultTabStack(int tab) {
        return switch (tab) {
            case DeviceHubMenu.TAB_HELMET -> new ItemStack(ModItems.CELESTWEAVE_OCULUS.get());
            case DeviceHubMenu.TAB_CHESTPLATE -> new ItemStack(ModItems.CELESTWEAVE_CORE.get());
            case DeviceHubMenu.TAB_LEGGINGS -> new ItemStack(ModItems.CELESTWEAVE_CONDUIT.get());
            case DeviceHubMenu.TAB_BOOTS -> new ItemStack(ModItems.CELESTWEAVE_STRIDE.get());
            case DeviceHubMenu.TAB_RAILGUN -> new ItemStack(ModItems.ELECTROMAGNETIC_RAILGUN.get());
            default -> ItemStack.EMPTY;
        };
    }

    private ItemStack tabStack(int tab) {
        if (minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        LivingEntity player = menu.getEquipmentWearer(minecraft.player);
        if (player == null) return ItemStack.EMPTY;
        return switch (tab) {
            case DeviceHubMenu.TAB_HELMET -> armorStack(player, EquipmentSlot.HEAD);
            case DeviceHubMenu.TAB_CHESTPLATE -> armorStack(player, EquipmentSlot.CHEST);
            case DeviceHubMenu.TAB_LEGGINGS -> armorStack(player, EquipmentSlot.LEGS);
            case DeviceHubMenu.TAB_BOOTS -> armorStack(player, EquipmentSlot.FEET);
            case DeviceHubMenu.TAB_RAILGUN -> railgunStack(player);
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack armorStack(LivingEntity player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        return stack.getItem() instanceof BaseCelestweaveArmorItem ? stack : ItemStack.EMPTY;
    }

    private static ItemStack railgunStack(LivingEntity player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ElectromagneticRailgunItem) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof ElectromagneticRailgunItem ? offhand : ItemStack.EMPTY;
    }

    // ---- rows ---------------------------------------------------------------------------

    private static boolean inVisibleWindow(int index, int first, int visibleRows) {
        return index >= first && index - first < visibleRows;
    }

    private boolean moduleHovered(int index) {
        if (!inVisibleWindow(index, scrollOffset, MODULE_VISIBLE_ROWS)) {
            return false;
        }
        int rowY = topPos + MODULE_LIST_Y + (index - scrollOffset) * MODULE_ROW_H;
        return frameMouseX >= leftPos + MODULE_LIST_X && frameMouseX < leftPos + MODULE_LIST_RIGHT
                && frameMouseY >= rowY && frameMouseY < rowY + MODULE_ROW_H;
    }

    private static Label label(Component text, int color) {
        var label = LdlibScreens.label(text, color);
        label.textStyle(style -> style.textWrap(TextWrap.NONE));
        return label;
    }

    private UIElement unboundedLabel(Component text) {
        return unboundedLabel(() -> text);
    }

    private UIElement unboundedLabel(Supplier<Component> text) {
        return new UIElement().setAllowHitTest(false).style(style -> style.overflowVisible(true).background(
                (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                        LDLibFonts.drawText(graphics, font, text.get().getVisualOrderText(),
                                x, y, TEXT_ON_DARK_BG, false)));
    }

    private UIElement moduleRow(ModuleEntry entry) {
        var element = new UIElement() {
            @Override
            public boolean isVisible() {
                return super.isVisible() && inVisibleWindow(entry.index(), scrollOffset, MODULE_VISIBLE_ROWS);
            }
        };
        element.layout(l -> l.widthPercent(100).height(MODULE_ROW_H + 1));
        element.style(style -> style.overflowVisible(true));
        var band = new UIElement().setAllowHitTest(false);
        band.style(style -> style.background(either(() -> entry.index() == menu.getSelectedModuleIndex(),
                LdlibScreens.solid(() -> ROW_SELECTED),
                either(() -> moduleHovered(entry.index()), LdlibScreens.solid(() -> ROW_HOVER), IGuiTexture.EMPTY))));
        int rowWidth = MODULE_LIST_RIGHT - MODULE_LIST_X + 1;
        element.addChild(at(band, 0, 0, rowWidth, MODULE_ROW_H));
        int nameWidth = entry.showToggle() ? MODULE_CHECKBOX_X - MODULE_LIST_X - 6 : MODULE_LIST_RIGHT - MODULE_LIST_X;
        var name = unboundedLabel(() -> Component.literal(FittingText.withEllipsis(
                font, moduleName(entry.nameKey(), entry.count()).getString(), nameWidth)));
        element.addChild(at(name, 1, 3, nameWidth, 9));
        if (entry.showToggle()) {
            var toggle = checkbox(entry.enabled());
            element.addChild(at(toggle, MODULE_CHECKBOX_X - MODULE_LIST_X + 1, 2, CHECKBOX_WIDTH, CHECKBOX_HEIGHT));
        }
        var divider = new UIElement().setAllowHitTest(false);
        divider.style(style -> style.background(either(
                () -> entry.index() + 1 < Math.min(moduleRows.getItemCount(), scrollOffset + MODULE_VISIBLE_ROWS),
                LdlibScreens.solid(() -> ROW_DIVIDER), IGuiTexture.EMPTY)));
        element.addChild(at(divider, 1, MODULE_ROW_H, rowWidth - 1, 1));
        return element;
    }

    private UIElement configRow(ConfigEntry entry) {
        var row = new UIElement() {
            @Override
            public boolean isVisible() {
                return super.isVisible() && inVisibleWindow(entry.index(), configScrollOffset, CONFIG_VISIBLE_ROWS);
            }
        };
        row.layout(l -> l.widthPercent(100).height(CONFIG_ROW_H));
        row.style(style -> style.overflowVisible(true));
        row.addChild(at(unboundedLabel(entry.label()), 0, 3, CONFIG_BUTTON_X - CONFIG_ROW_X - 2, 9));

        if (entry.checkbox()) {
            var toggle = checkbox(entry.enabled());
            row.addChild(at(toggle, MODULE_CHECKBOX_X - CONFIG_ROW_X, 2, CHECKBOX_WIDTH, CHECKBOX_HEIGHT));
        } else {
            var button = new UIElement();
            var idle = entry.editable() ? valueButton(BUTTON_BORDER, BUTTON_FILL)
                    : valueButton(BUTTON_DISABLED, BUTTON_FILL_DISABLED);
            var hover = entry.editable() ? valueButton(BUTTON_BORDER_HOVER, BUTTON_FILL_HOVER) : idle;
            button.style(style -> style.background(either(button::isHover, hover, idle)));
            var value = new UIElement().setAllowHitTest(false).style(s -> s.background(
                    (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                        String fitted = FittingText.withEllipsis(font, entry.value().getString(), CONFIG_BUTTON_W - 4);
                        LDLibFonts.drawText(graphics, font, Component.literal(fitted).getVisualOrderText(),
                                x + (CONFIG_BUTTON_W - font.width(fitted)) / 2, y + 2,
                                entry.editable() ? BUTTON_TEXT : TEXT_ON_DARK_BG, false);
                    }));
            button.addChild(at(value, 0, 0, CONFIG_BUTTON_W, CONFIG_BUTTON_H));
            row.addChild(at(button, CONFIG_BUTTON_X - CONFIG_ROW_X, 1,
                    CONFIG_BUTTON_W + 1, CONFIG_BUTTON_H + 1));
        }
        return row;
    }

    private record ModuleEntry(int index, String nameKey, int count, boolean enabled, boolean showToggle) {
    }

    private record ConfigEntry(int index, Component label, Component value, boolean checkbox, boolean enabled,
            boolean editable) {
    }
}
