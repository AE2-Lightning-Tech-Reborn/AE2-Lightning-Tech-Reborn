package com.moakiee.ae2lt.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.network.serverbound.SwitchGuisPacket;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.frequency.ClientFrequencyCache;
import com.moakiee.ae2lt.client.frequency.ManagementViewCache;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.FrequencyPage;
import com.moakiee.ae2lt.network.ChangeMemberPacket;
import com.moakiee.ae2lt.network.CreateFrequencyPacket;
import com.moakiee.ae2lt.network.DeleteFrequencyPacket;
import com.moakiee.ae2lt.network.EditFrequencyPacket;
import com.moakiee.ae2lt.network.SelectFrequencyPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Compact frequency subpages sharing AE2 navigation with device and power management.
 * Polls {@link ClientFrequencyCache#revision()} each frame and rebuilds
 * widgets when the server-pushed state changes.
 */
public class FrequencyScreen extends AbstractContainerScreen<FrequencyMenu> {

    private static final ResourceLocation TEXTURE_HOME = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/wireless_overloaded_home.png");
    private static final ResourceLocation TEXTURE_SELECTION = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/wireless_overloaded_selection.png");
    private static final ResourceLocation TEXTURE_LIST = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/wireless_overloaded_list.png");
    private static final ResourceLocation TEXTURE_FORM = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/wireless_overloaded_form.png");

    private static final int GUI_WIDTH = FrequencyNavigation.WIDTH;
    private static final int GUI_HEIGHT = FrequencyNavigation.HEIGHT;
    private static final int ITEMS_PER_PAGE_LIST = 5;
    private static final int LIST_ROW_HEIGHT = 25;
    private static final int LIST_ROW_BUTTON_HEIGHT = 24;
    private static final int LIST_ROW_FIRST_Y = 49;
    private static final int LIST_ROW_X = 8;
    private static final int LIST_ROW_WIDTH = GUI_WIDTH - 34;
    private static final int SCROLLBAR_X = GUI_WIDTH - 20;
    private static final int SCROLLBAR_Y = LIST_ROW_FIRST_Y;
    private static final int SCROLLBAR_WIDTH = 12;

    // AE2 standard text tones matching MultiblockControllerScreen & Tianshu screens.
    private static final int AE2_TEXT_TITLE = 0x2A2E3F;
    private static final int AE2_TEXT_BODY = 0x14161F;
    private static final int AE2_TEXT_LABEL = 0x363B4E;
    private static final int AE2_TEXT_MUTED = 0x4E5370;
    private static final int COL_GREEN = 0x1C6B34;
    private static final int COL_RED = 0x8F2B23;

    /**
     * AETextField's native {@code guis/text_field.png} frame is a fixed 12
     * px tall 3-slice. Creating the field at this exact height keeps the
     * AE2 frame flush with the recessed shelf we draw behind it.
     */
    private static final int INPUT_HEIGHT = 12;

    private static final int INPUT_MAX_WIDTH = GUI_WIDTH - 24;
    private static final int FOOTER_Y = GUI_HEIGHT - 24;
    private static final int FOOTER_BUTTON_WIDTH = (GUI_WIDTH - 24) / 3;

    private final ScreenStyle ae2Style = StyleManager.loadStyleDoc("/screens/common/common.json");
    private ToggleButton channelAutoButton, energyAutoButton;

    private FrequencyPage currentTab = FrequencyPage.TAB_HOME;
    /**
     * Top row index currently scrolled to within the Selection / Member tab lists.
     */
    private int selectionScroll = 0;
    private int memberScroll = 0;
    private int connectionScroll = 0;

    /**
     * Currently active list-tab scrollbar widget. {@code null} on Home /
     * Create / Settings tabs (no scrollable content).
     */
    private ScrollbarWidget currentScrollbar;

    /**
     * Row buttons currently rendered for the active list tab.
     */
    private final java.util.List<AbstractWidget> currentRowButtons = new java.util.ArrayList<>();

    private AETextField nameField;
    private AETextField selectionSearchField;
    private FrequencyPage formPage;
    private int formFrequency;
    private AETextField passwordField;
    private FrequencySecurityLevel editSecurity = FrequencySecurityLevel.PRIVATE;
    private int editColor = 0x1E90FF;

    private int lastCacheRevision = -1;
    private int lastFreqId = Integer.MIN_VALUE;

    // popup state for Members tab
    private UUID popupMemberUUID;
    private String popupMemberName = "";
    private FrequencyAccessLevel popupMemberAccess = FrequencyAccessLevel.USER;
    private boolean popupIsStranger = false;

    private Component inlineError = null;
    private long inlineErrorExpiresAt = 0L;
    private static final long INLINE_ERROR_DURATION_MS = 4000L;
    private final List<FittedTextTooltip> fittedTextTooltips = new ArrayList<>();

    public void showInlineError(Component message) {
        this.inlineError = message;
        this.inlineErrorExpiresAt = System.currentTimeMillis() + INLINE_ERROR_DURATION_MS;
    }

    private boolean deleteConfirmOpen = false;

    private static final int[] PRESET_COLORS = {
            0x6B17E8, 0x4400EC, 0x0033FF, 0x00ABFF, 0x00FFD9, 0x00FF00, 0x77FF00,
            0xFFFF00, 0xFF8800, 0xFF0000, 0xFF006A, 0xEC00EC, 0x7F7F7F, 0xFFFFFF
    };

    private FrequencyMenu freqMenu() { return getMenu(); }

    private int token() { return getMenu().containerId; }

    public FrequencyScreen(FrequencyMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.currentTab = menu.initialPage();
    }

    @Override
    protected void init() {
        super.init();
        lastCacheRevision = ClientFrequencyCache.revision();
        lastFreqId = freqMenu().getCurrentFrequencyId();
        initTabWidgets();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        int rev = ClientFrequencyCache.revision();
        int fid = freqMenu().getCurrentFrequencyId();
        if (currentScrollbar != null) currentScrollbar.tick();
        if (channelAutoButton != null) updateAutoButtons();
        for (var child : children()) {
            if (child instanceof FrequencyNavigation.PageButton button && button.page().isManagement())
                button.active = managementAvailable();
        }
        if (rev != lastCacheRevision || fid != lastFreqId) {
            lastCacheRevision = rev;
            lastFreqId = fid;
            initTabWidgets();
        }
    }

    private AETextField makeAe2Field(int x, int y, int width, int height) {
        AETextField field = new WideTextField(x, y, width, height);
        field.setBordered(false);
        return field;
    }

    /** Tiles AE2's native text-field middle beyond its 126-pixel default limit. */
    private class WideTextField extends AETextField {
        private boolean editable = true;
        @Override public void setEditable(boolean editable) { this.editable = editable; super.setEditable(editable); }
        WideTextField(int x, int y, int width, int height) { super(ae2Style, FrequencyScreen.this.font, x, y, width, height); }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (isVisible()) {
                var bounds = getTooltipArea();
                int variant = !editable ? 12 : isFocused() ? 24 : 0;
                for (int x = 127; x < bounds.getWidth() - 1; x += 126) {
                    int width = Math.min(126, bounds.getWidth() - 1 - x);
                    Blitter.texture("guis/text_field.png", 128, 128)
                            .src(1, variant, width, 12).dest(bounds.getX() + x, bounds.getY()).blit(graphics);
                }
            }
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final String PASSWORD_SENTINEL = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private boolean settingsPasswordPristine = false;

    private int passwordPromptFreqId = 0;
    private String passwordPromptFreqName = "";
    private boolean passwordPromptLocksScreen = false;
    private AETextField passwordPromptField;

    private String selectionSearchQuery = "";

    private final class PristinePasswordField extends WideTextField {
        PristinePasswordField(int x, int y, int width, int height) {
            super(x, y, width, height);
            setBordered(false);
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (settingsPasswordPristine) {
                settingsPasswordPristine = false;
                setValue("");
            }
            return super.keyPressed(key, scan, mods);
        }

        @Override
        public boolean charTyped(char c, int mods) {
            if (settingsPasswordPristine) {
                settingsPasswordPristine = false;
                setValue("");
            }
            return super.charTyped(c, mods);
        }
    }

    private void initTabWidgets() {
        boolean restoreSearchFocus = currentTab == FrequencyPage.TAB_SELECTION
                && selectionSearchField != null && selectionSearchField.isFocused();
        int searchCursor = restoreSearchFocus ? selectionSearchField.getCursorPosition() : 0;
        clearWidgets();
        currentRowButtons.clear();
        currentScrollbar = null;
        channelAutoButton = energyAutoButton = null;
        closePopup();

        if (passwordPromptFreqId > 0) {
            var freq = ClientFrequencyCache.getFrequency(passwordPromptFreqId);
            if (freq == null || !needsPasswordUnlock(freq)) {
                passwordPromptFreqId = 0;
                passwordPromptLocksScreen = false;
            }
        }
        if (passwordPromptFreqId == 0) {
            checkAutoPasswordPrompt();
        }

        // Keep the frame and navigation stationary when switching between main pages.
        imageHeight = GUI_HEIGHT;
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        int x0 = leftPos, y0 = topPos;
        buildTopTabs(x0, y0);

        if (passwordPromptFreqId > 0) {
            buildPasswordPromptWidgets(x0, y0);
            return;
        }

        if (deleteConfirmOpen) {
            buildDeleteConfirmWidgets(x0, y0);
            return;
        }

        switch (currentTab) {
            case TAB_HOME -> initHomeTab(x0, y0);
            case TAB_SELECTION -> initSelectionTab(x0, y0);
            case TAB_MEMBER -> initMembersTab(x0, y0);
            case TAB_CREATE -> initFrequencyForm(x0, y0, true);
            case TAB_SETTING -> initFrequencyForm(x0, y0, false);
            case TAB_CONNECTION -> initLocalConnections(x0, y0);
            case TAB_DEVICES, TAB_ENERGY -> throw new IllegalStateException("Management pages use their native menu");
        }
        if (restoreSearchFocus && selectionSearchField != null) {
            setFocused(selectionSearchField); selectionSearchField.setFocused(true);
            selectionSearchField.moveCursorTo(searchCursor, false);
        }
    }

    private void buildDeleteConfirmWidgets(int x0, int y0) {
        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 - 84, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> { deleteConfirmOpen = false; scheduleRebuild(); }));

        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 + 4, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.confirm"),
                btn -> {
                    int freqId = freqMenu().getCurrentFrequencyId();
                    if (freqId > 0) {
                        PacketDistributor.sendToServer(new DeleteFrequencyPacket(token(), freqId));
                    }
                    deleteConfirmOpen = false;
                    switchTab(FrequencyPage.TAB_SELECTION);
                }));
    }

    private boolean needsPasswordUnlock(ClientFrequencyCache.CachedFrequency freq) {
        if (freq.security() != FrequencySecurityLevel.ENCRYPTED) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        UUID me = mc.player.getUUID();
        for (var m : ClientFrequencyCache.getMembers(freq.id())) {
            if (m.uuid().equals(me)) return false;
        }
        return true;
    }

    private void checkAutoPasswordPrompt() {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) return;
        var freq = ClientFrequencyCache.getFrequency(currentId);
        if (freq == null) return;
        if (needsPasswordUnlock(freq)) {
            passwordPromptFreqId = currentId;
            passwordPromptFreqName = freq.name();
            passwordPromptLocksScreen = true;
        }
    }

    private void buildPasswordPromptWidgets(int x0, int y0) {
        passwordPromptField = makeAe2Field(x0 + 24, y0 + 80, GUI_WIDTH - 48, INPUT_HEIGHT);
        passwordPromptField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordPromptField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.password"));
        addRenderableWidget(passwordPromptField);
        setFocused(passwordPromptField);
        passwordPromptField.setFocused(true);

        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 - 84, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> cancelPasswordPrompt()));

        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 + 4, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.submit"),
                btn -> submitPasswordPrompt()));
    }

    private void submitPasswordPrompt() {
        String pw = passwordPromptField == null ? "" : passwordPromptField.getValue();
        int freqId = passwordPromptFreqId;
        if (freqId <= 0) return;
        PacketDistributor.sendToServer(new SelectFrequencyPacket(
                token(), freqMenu().getBlockPos(), freqId, pw));
    }

    private void cancelPasswordPrompt() {
        boolean closeAll = passwordPromptLocksScreen;
        passwordPromptFreqId = 0;
        passwordPromptLocksScreen = false;
        if (closeAll) {
            Minecraft.getInstance().execute(this::onClose);
        } else {
            scheduleRebuild();
        }
    }

    private void buildTopTabs(int x0, int y0) {
        boolean showManagement = freqMenu().supportsManagement();
        for (var button : FrequencyNavigation.create(x0, y0, currentTab, showManagement,
                managementAvailable(), tab -> { closePopup(); switchTab(tab); })) {
            if (button.page() == FrequencyPage.TAB_SETTING)
                button.active = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId()) != null;
            addRenderableWidget(button);
        }
        if (freqMenu().hasParentMenu()) {
            var back = new TabButton(Icon.BACK, Component.translatable(freqMenu().isCardMode()
                    ? "ae2lt.gui.button.return_to_terminal" : "ae2lt.gui.button.back"),
                    button -> PacketDistributor.sendToServer(SwitchGuisPacket.returnToParentMenu()));
            back.setStyle(TabButton.Style.BOX);
            back.setPosition(x0 - 18, y0 + FrequencyNavigation.TAB_TOP);
            addRenderableWidget(back);
        }
    }

    private boolean managementAvailable() {
        return freqMenu().getCurrentFrequencyId() > 0 && (!freqMenu().isCardMode() || freqMenu().isLinkActive());
    }

    private void switchTab(FrequencyPage tab) {
        if (tab.isManagement() && !(tab == FrequencyPage.TAB_CONNECTION && freqMenu().usesLocalConnectionList())) {
            if (freqMenu().supportsManagement())
                freqMenu().clientOpenManagement((tab == FrequencyPage.TAB_DEVICES
                        ? ManagementViewCache.latest(freqMenu().getBlockPos(), freqMenu().getCurrentFrequencyId())
                        : ManagementViewCache.get(freqMenu().getBlockPos(), freqMenu().getCurrentFrequencyId(), tab.managementMode())).query());
            return;
        }
        boolean withinFrequency = (currentTab.navigationPage() == FrequencyPage.TAB_SELECTION || currentTab == FrequencyPage.TAB_SETTING)
                && (tab.navigationPage() == FrequencyPage.TAB_SELECTION || tab == FrequencyPage.TAB_SETTING);
        if (tab != currentTab) formPage = null;
        currentTab = tab;
        if (!withinFrequency) {
            selectionScroll = 0;
            selectionSearchQuery = "";
        }
        memberScroll = 0;
        connectionScroll = 0;
        deleteConfirmOpen = false;
        scheduleRebuild();
    }

    private void scheduleRebuild() {
        Minecraft.getInstance().execute(this::initTabWidgets);
    }

    // Tab: Home

    private void initHomeTab(int x0, int y0) {
        boolean card = freqMenu().isCardMode();
        if (card) {
            channelAutoButton = addAutoButton(x0 + 91, y0 + 148, false);
            energyAutoButton = addAutoButton(x0 + 203, y0 + 148, true);
            updateAutoButtons();
        }
        if (freqMenu().usesLocalConnectionList()) {
            var links = addRenderableWidget(new AE2Button(x0 + (GUI_WIDTH - 104) / 2, y0 + FOOTER_Y, 104, 16,
                    Component.translatable("ae2lt.management.connections"), btn -> switchTab(FrequencyPage.TAB_CONNECTION)));
            links.active = freqMenu().getCurrentFrequencyId() > 0;
        }
    }

    private ToggleButton addAutoButton(int x, int y, boolean energy) {
        var button = new Ae2SwitchButton(state -> {
            if (energy) freqMenu().clientToggleAutoConnectEnergy();
            else freqMenu().clientToggleAutoConnect();
        });
        button.setPosition(x, y);
        return addRenderableWidget(button);
    }

    private static final class Ae2SwitchButton extends ToggleButton {
        private static final Blitter SWITCH_BLITTER = Blitter.texture("guis/checkbox.png", 64, 64);
        private boolean selected;

        Ae2SwitchButton(Listener listener) {
            super(Icon.AUTO_EXPORT_ON, Icon.AUTO_EXPORT_OFF, listener);
            this.width = 22;
            this.height = 12;
        }

        @Override
        public void setState(boolean isOn) {
            this.selected = isOn;
            super.setState(isOn);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            int srcX = isHoveredOrFocused() ? 22 : 0;
            int srcY = this.selected ? 40 : 28;
            SWITCH_BLITTER.copy().src(srcX, srcY, 22, 12).dest(getX(), getY()).blit(g);
        }

        @Override
        public Rect2i getTooltipArea() {
            return new Rect2i(getX(), getY(), this.width, this.height);
        }
    }

    private void updateAutoButtons() {
        updateAutoButton(channelAutoButton, false, freqMenu().isAutoConnect());
        updateAutoButton(energyAutoButton, true, freqMenu().isAutoConnectEnergy());
    }

    private void updateAutoButton(ToggleButton button, boolean energy, boolean enabled) {
        button.setState(enabled);
        var label = Component.translatable("ae2lt.gui.button.auto_connect_"
                + (energy ? "energy_" : "") + (enabled ? "on" : "off"));
        button.setMessage(label);
        button.setTooltip(Tooltip.create(label.copy().append("\n").append(Component.translatable(
                energy ? "ae2lt.gui.home.auto_energy_hint" : "ae2lt.gui.home.auto_channel_hint"))));
    }

    /** Receivers and remote device submenus retain their read-only connection view. */
    private void initLocalConnections(int x0, int y0) {
        int total = ClientFrequencyCache.getConnections(freqMenu().getCurrentFrequencyId()).size();
        connectionScroll = clampScroll(connectionScroll, total, ITEMS_PER_PAGE_LIST);
        currentScrollbar = new ScrollbarWidget(x0 + SCROLLBAR_X, y0 + SCROLLBAR_Y,
                ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT - 1, total, ITEMS_PER_PAGE_LIST, connectionScroll,
                offset -> connectionScroll = offset);
        addRenderableWidget(currentScrollbar);
        addRenderableWidget(new AE2Button(x0 + (GUI_WIDTH - 104) / 2, y0 + FOOTER_Y, 104, 16,
                Component.translatable("ae2lt.gui.button.back"), btn -> switchTab(FrequencyPage.TAB_HOME)));
    }

    private void renderLocalConnections(GuiGraphics g) {
        var rows = ClientFrequencyCache.getConnections(freqMenu().getCurrentFrequencyId());
        var current = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        Component left = Component.translatable("ae2lt.gui.frequency.current")
                .append(": ")
                .append(current != null
                        ? Component.literal(current.name())
                        : Component.translatable("ae2lt.gui.frequency.none"));
        Component right = Component.translatable("ae2lt.gui.frequency.total").append(": " + rows.size());
        int rightX = imageWidth - 12 - font.width(right);
        drawFlatFitted(g, left, 12, 28, Math.max(20, rightX - 24), AE2_TEXT_BODY);
        drawFlat(g, right, rightX, 28, AE2_TEXT_LABEL);

        if (rows.isEmpty()) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.connection.none"),
                    LIST_ROW_X + LIST_ROW_WIDTH / 2, LIST_ROW_FIRST_Y + 56, AE2_TEXT_LABEL);
            return;
        }
        for (int i = connectionScroll; i < Math.min(rows.size(), connectionScroll + ITEMS_PER_PAGE_LIST); i++) {
            var row = rows.get(i);
            int y = LIST_ROW_FIRST_Y + (i - connectionScroll) * LIST_ROW_HEIGHT;
            var state = Component.translatable("ae2lt.management.state." + (row.loaded() ? "loaded" : "unloaded"));
            int stateColor = row.loaded() ? COL_GREEN : COL_RED;
            drawFlatFitted(g, Component.translatable(row.deviceName()), 13, y + 3,
                    LIST_ROW_WIDTH - 18 - font.width(state), AE2_TEXT_BODY);
            drawFlat(g, state, LIST_ROW_X + LIST_ROW_WIDTH - 5 - font.width(state), y + 3, stateColor);
            drawFlatFitted(g, row.dimension() + " · " + row.pos().getX() + " " + row.pos().getY() + " " + row.pos().getZ(),
                    13, y + 13, LIST_ROW_WIDTH - 10, AE2_TEXT_LABEL);
        }
    }

    // Tab: Selection

    private void initSelectionTab(int x0, int y0) {
        int searchX = x0 + 8;
        AETextField searchField = selectionSearchField = makeAe2Field(searchX, y0 + 22, GUI_WIDTH - 16, INPUT_HEIGHT);
        searchField.setMaxLength(32);
        searchField.setValue(selectionSearchQuery);
        searchField.setResponder(value -> {
            if (!value.equals(selectionSearchQuery)) {
                selectionSearchQuery = value;
                selectionScroll = 0;
                scheduleRebuild();
            }
        });
        searchField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.search"));
        addRenderableWidget(searchField);

        int total = filteredFrequencies().size();
        selectionScroll = clampScroll(selectionScroll, total, ITEMS_PER_PAGE_LIST);

        currentScrollbar = new ScrollbarWidget(
                x0 + SCROLLBAR_X, y0 + SCROLLBAR_Y,
                ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT - 1,
                total, ITEMS_PER_PAGE_LIST,
                selectionScroll,
                offset -> { selectionScroll = offset; rebuildSelectionRows(); });
        addRenderableWidget(currentScrollbar);

        rebuildSelectionRows();
        boolean connected = freqMenu().getCurrentFrequencyId() > 0;
        var disconnect = addRenderableWidget(new AE2Button(x0 + 8, y0 + FOOTER_Y, 106, 16,
                Component.translatable("ae2lt.gui.button.disconnect"),
                btn -> PacketDistributor.sendToServer(new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, ""))));
        disconnect.active = connected;
        addRenderableWidget(new AE2Button(x0 + GUI_WIDTH - 114, y0 + FOOTER_Y, 106, 16,
                Component.translatable("ae2lt.gui.tab.create"), btn -> switchTab(FrequencyPage.TAB_CREATE)));
    }

    private java.util.List<com.moakiee.ae2lt.client.frequency.ClientFrequencyCache.CachedFrequency> filteredFrequencies() {
        var all = ClientFrequencyCache.getAllFrequenciesSorted();
        String q = selectionSearchQuery.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) return all;
        return all.stream()
                .filter(f -> f.name().toLowerCase(java.util.Locale.ROOT).contains(q))
                .toList();
    }

    private static int clampScroll(int v, int total, int visible) {
        return Math.max(0, Math.min(v, Math.max(0, total - visible)));
    }

    private void rebuildSelectionRows() {
        clearRowButtons();
        var freqs = filteredFrequencies();
        int x0 = leftPos;
        int y0 = topPos;
        int start = selectionScroll;
        int end = Math.min(start + ITEMS_PER_PAGE_LIST, freqs.size());

        for (int i = start; i < end; i++) {
            var f = freqs.get(i);
            int row = i - start;
            int by = y0 + LIST_ROW_FIRST_Y + row * LIST_ROW_HEIGHT;

            String label = switch (f.security()) {
                case ENCRYPTED -> f.name() + " ["
                        + Component.translatable("ae2lt.gui.security.encrypted").getString() + "]";
                case PRIVATE -> f.name() + " ["
                        + Component.translatable("ae2lt.gui.security.private").getString() + "]";
                default -> f.name();
            };
            Component display = Component.literal(label)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(f.color() & 0xFFFFFF)));
            RowSpriteButton btn = new RowSpriteButton(
                    x0 + LIST_ROW_X, by, LIST_ROW_WIDTH, LIST_ROW_BUTTON_HEIGHT,
                    display, f.id() == freqMenu().getCurrentFrequencyId(),
                    b -> {
                        if (needsPasswordUnlock(f)) {
                            passwordPromptFreqId = f.id();
                            passwordPromptFreqName = f.name();
                            passwordPromptLocksScreen = false;
                            scheduleRebuild();
                        } else {
                            PacketDistributor.sendToServer(
                                    new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), f.id(), ""));
                        }
                    });
            currentRowButtons.add(btn);
            addRenderableWidget(btn);
        }
    }

    private void clearRowButtons() {
        for (var b : currentRowButtons) {
            removeWidget(b);
        }
        currentRowButtons.clear();
    }

    // Tab: Members

    private void initMembersTab(int x0, int y0) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) return;

        int total = collectMemberRows().size();
        memberScroll = clampScroll(memberScroll, total, ITEMS_PER_PAGE_LIST);

        currentScrollbar = new ScrollbarWidget(
                x0 + SCROLLBAR_X, y0 + SCROLLBAR_Y,
                ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT - 1,
                total, ITEMS_PER_PAGE_LIST,
                memberScroll,
                offset -> { memberScroll = offset; rebuildMemberRows(); });
        addRenderableWidget(currentScrollbar);

        rebuildMemberRows();
    }

    private record MemberRow(boolean isMember, UUID uuid, String name, FrequencyAccessLevel access) {}

    private java.util.List<MemberRow> collectMemberRows() {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) return java.util.List.of();
        var members = ClientFrequencyCache.getMembers(currentId);
        java.util.List<MemberRow> rows = new java.util.ArrayList<>();
        java.util.Set<UUID> memberIds = new java.util.HashSet<>();
        for (var m : members) {
            rows.add(new MemberRow(true, m.uuid(), m.name(), m.access()));
            memberIds.add(m.uuid());
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            java.util.List<StrangerRow> strangers = new java.util.ArrayList<>();
            for (var info : connection.getOnlinePlayers()) {
                UUID id = info.getProfile().getId();
                if (id != null && !memberIds.contains(id)) {
                    strangers.add(new StrangerRow(id, info.getProfile().getName()));
                }
            }
            strangers.sort(java.util.Comparator.comparing(s -> s.name.toLowerCase()));
            for (var s : strangers) {
                rows.add(new MemberRow(false, s.uuid(), s.name(), FrequencyAccessLevel.BLOCKED));
            }
        }
        return rows;
    }

    private void rebuildMemberRows() {
        clearRowButtons();
        var rows = collectMemberRows();
        int x0 = leftPos;
        int y0 = topPos;
        int start = memberScroll;
        int end = Math.min(start + ITEMS_PER_PAGE_LIST, rows.size());

        for (int i = start; i < end; i++) {
            var entry = rows.get(i);
            int row = i - start;
            int by = y0 + LIST_ROW_FIRST_Y + row * LIST_ROW_HEIGHT;

            if (entry.isMember()) {
                StringBuilder text = new StringBuilder(entry.name());
                if (isSelf(entry.uuid())) {
                    text.append(' ')
                            .append(Component.translatable("ae2lt.gui.member.you").getString());
                }
                text.append(" [").append(accessLabel(entry.access())).append(']');
                Component display = Component.literal(text.toString())
                        .withStyle(entry.access().getFormatting());
                RowSpriteButton rowBtn = new RowSpriteButton(
                        x0 + LIST_ROW_X, by, LIST_ROW_WIDTH, LIST_ROW_BUTTON_HEIGHT,
                        display,
                        b -> openMemberPopup(entry.uuid(), entry.name(), entry.access()));
                if (isSelf(entry.uuid())) {
                    rowBtn.active = false;
                }
                currentRowButtons.add(rowBtn);
                addRenderableWidget(rowBtn);
            } else {
                String shortCode = Component.translatable("ae2lt.gui.member.stranger_short").getString();
                Component display = Component.literal(entry.name() + " [" + shortCode + "]")
                        .withStyle(ChatFormatting.DARK_GRAY);
                RowSpriteButton btn = new RowSpriteButton(
                        x0 + LIST_ROW_X, by, LIST_ROW_WIDTH, LIST_ROW_BUTTON_HEIGHT,
                        display,
                        b -> openStrangerPopup(entry.uuid(), entry.name()));
                currentRowButtons.add(btn);
                addRenderableWidget(btn);
            }
        }
    }

    private record StrangerRow(UUID uuid, String name) {}

    // Member edit popup

    private void openMemberPopup(UUID uuid, String name, FrequencyAccessLevel access) {
        popupMemberUUID = uuid;
        popupMemberName = name;
        popupMemberAccess = access;
        popupIsStranger = false;
        Minecraft.getInstance().execute(this::rebuildMemberPopupWidgets);
    }

    private void openStrangerPopup(UUID uuid, String name) {
        popupMemberUUID = uuid;
        popupMemberName = name;
        popupMemberAccess = FrequencyAccessLevel.USER;
        popupIsStranger = true;
        Minecraft.getInstance().execute(this::rebuildMemberPopupWidgets);
    }

    private void closePopup() {
        popupMemberUUID = null;
        popupIsStranger = false;
    }

    private void rebuildMemberPopupWidgets() {
        clearWidgets();
        int x0 = leftPos;
        int y0 = topPos;
        buildTopTabs(x0, y0);

        int px = x0 + 24;
        int py = y0 + 52;
        int pw = GUI_WIDTH - 48;

        FrequencyAccessLevel myAccess = selfAccess();
        FrequencyAccessLevel targetLevel = popupIsStranger
                ? FrequencyAccessLevel.BLOCKED
                : popupMemberAccess;
        boolean targetIsOwner = targetLevel == FrequencyAccessLevel.OWNER;
        boolean isSelfTarget = !popupIsStranger && isSelf(popupMemberUUID);
        boolean ownerActingOnSelf = targetIsOwner && isSelfTarget;

        boolean canUser = !ownerActingOnSelf
                && targetLevel != FrequencyAccessLevel.USER
                && myAccess.canActOnLevel(FrequencyAccessLevel.higher(targetLevel, FrequencyAccessLevel.USER));
        AE2Button btnUser = new AE2Button(px, py, pw, 18,
                Component.translatable("ae2lt.gui.member.set_user"),
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_SET_USER));
        btnUser.active = canUser;
        addRenderableWidget(btnUser);

        boolean canAdmin = !ownerActingOnSelf
                && targetLevel != FrequencyAccessLevel.ADMIN
                && myAccess.canActOnLevel(FrequencyAccessLevel.higher(targetLevel, FrequencyAccessLevel.ADMIN));
        AE2Button btnAdmin = new AE2Button(px, py + 20, pw, 18,
                Component.translatable("ae2lt.gui.member.set_admin"),
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_SET_ADMIN));
        btnAdmin.active = canAdmin;
        addRenderableWidget(btnAdmin);

        boolean canRemove = !ownerActingOnSelf
                && !popupIsStranger
                && myAccess.canActOnLevel(targetLevel);
        AE2Button btnRemove = new AE2Button(px, py + 40, pw, 18,
                Component.translatable("ae2lt.gui.member.remove"),
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_CANCEL));
        btnRemove.active = canRemove;
        addRenderableWidget(btnRemove);

        boolean canSetOwner = !targetIsOwner
                && myAccess.canActOnLevel(FrequencyAccessLevel.OWNER);
        AE2Button btnSetOwner = new AE2Button(px, py + 60, pw, 18,
                Component.translatable("ae2lt.gui.member.set_owner"),
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP));
        btnSetOwner.active = canSetOwner;
        addRenderableWidget(btnSetOwner);

        addRenderableWidget(new AE2Button(x0 + (GUI_WIDTH - 96) / 2, py + 94, 96, 18,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> { closePopup(); scheduleRebuild(); }));
    }

    private void sendMember(byte type) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0 || popupMemberUUID == null) return;
        PacketDistributor.sendToServer(new ChangeMemberPacket(token(), currentId, popupMemberUUID, type));
        closePopup();
        scheduleRebuild();
    }

    /** Creation and editing use one layout; server-side access checks remain authoritative. */
    private void initFrequencyForm(int x0, int y0, boolean creating) {
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        boolean resume = formPage == currentTab && (creating || freq != null && formFrequency == freq.id());
        String savedName = resume && nameField != null ? nameField.getValue() : creating || freq == null ? "" : freq.name();
        String savedPassword = resume && passwordField != null ? passwordField.getValue() : "";
        if (!resume) {
            editSecurity = creating || freq == null ? FrequencySecurityLevel.PRIVATE : freq.security();
            editColor = creating || freq == null ? 0x1E90FF : freq.color();
            settingsPasswordPristine = !creating && editSecurity == FrequencySecurityLevel.ENCRYPTED;
            if (settingsPasswordPristine) savedPassword = PASSWORD_SENTINEL;
        }
        formPage = currentTab; formFrequency = freq == null ? -1 : freq.id();
        int footerBtnW = creating ? 106 : FOOTER_BUTTON_WIDTH;
        addRenderableWidget(new AE2Button(x0 + 8, y0 + FOOTER_Y, footerBtnW, 16,
                Component.translatable("ae2lt.gui.button.back"), btn -> switchTab(FrequencyPage.TAB_SELECTION)));
        if (!creating && freq == null) return;
        boolean canEdit = creating || hasManagerAccess();
        boolean canSecure = creating || hasOwnerAccess();

        nameField = makeAe2Field(x0 + 12, y0 + 34, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        nameField.setMaxLength(WirelessFrequency.MAX_NAME_LENGTH);
        nameField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.name"));
        nameField.setValue(savedName); nameField.setEditable(canEdit); addRenderableWidget(nameField);
        passwordField = new PristinePasswordField(x0 + 12, y0 + 88, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        passwordField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.password"));
        passwordField.setValue(savedPassword); passwordField.setEditable(canSecure);
        passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED); addRenderableWidget(passwordField);
        var security = addRenderableWidget(new AE2Button(x0 + GUI_WIDTH - 104, y0 + 52, 92, 18, getSecurityLabel(editSecurity), btn -> {
            editSecurity = FrequencySecurityLevel.VALUES[(editSecurity.ordinal() + 1) % FrequencySecurityLevel.VALUES.length];
            passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
            passwordField.setValue(""); settingsPasswordPristine = false; btn.setMessage(getSecurityLabel(editSecurity));
        }));
        security.active = canSecure;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int color = PRESET_COLORS[i];
            var swatch = addRenderableWidget(new ColorSwatchButton(
                    x0 + GUI_WIDTH - 122 + (i % 7) * 16,
                    y0 + 108 + (i / 7) * 16,
                    14, 14, color));
            swatch.active = canEdit;
        }
        var submit = addRenderableWidget(new AE2Button(x0 + GUI_WIDTH - 8 - footerBtnW, y0 + FOOTER_Y, footerBtnW, 16,
                Component.translatable(creating ? "ae2lt.gui.button.create" : "ae2lt.gui.button.apply"), btn -> {
            if (nameField.getValue().isBlank()) return;
            String password = settingsPasswordPristine ? "" : passwordField.getValue();
            if (creating) {
                PacketDistributor.sendToServer(new CreateFrequencyPacket(token(), nameField.getValue(), editColor, editSecurity, password));
                switchTab(FrequencyPage.TAB_SELECTION);
            } else PacketDistributor.sendToServer(new EditFrequencyPacket(token(), freq.id(), nameField.getValue(), editColor, editSecurity, password));
        }));
        submit.active = canEdit && !savedName.isBlank();
        nameField.setResponder(value -> submit.active = canEdit && !value.isBlank());
        if (!creating) {
            if (hasOwnerAccess()) addRenderableWidget(new AE2Button(x0 + 12 + FOOTER_BUTTON_WIDTH, y0 + FOOTER_Y, FOOTER_BUTTON_WIDTH, 16,
                    Component.translatable("ae2lt.gui.button.delete"),
                    btn -> { deleteConfirmOpen = true; scheduleRebuild(); }));
            else addRenderableWidget(new AE2Button(x0 + 12 + FOOTER_BUTTON_WIDTH, y0 + FOOTER_Y, FOOTER_BUTTON_WIDTH, 16,
                    Component.translatable("ae2lt.gui.button.leave"), btn -> {
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                PacketDistributor.sendToServer(new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, ""));
                PacketDistributor.sendToServer(new ChangeMemberPacket(token(), freq.id(), player.getUUID(), WirelessFrequency.MEMBERSHIP_CANCEL));
                switchTab(FrequencyPage.TAB_SELECTION);
            }));
        }
    }

    // Rendering

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ResourceLocation bg = switch (currentTab) {
            case TAB_SELECTION -> TEXTURE_SELECTION;
            case TAB_MEMBER, TAB_CONNECTION -> TEXTURE_LIST;
            case TAB_CREATE, TAB_SETTING -> TEXTURE_FORM;
            default -> TEXTURE_HOME;
        };
        g.blit(bg, leftPos, topPos, 0, 0, GUI_WIDTH, imageHeight, 256, 256);

        if (popupMemberUUID == null && !deleteConfirmOpen && passwordPromptFreqId == 0) {
            if (currentTab == FrequencyPage.TAB_HOME) {
                if (freqMenu().isCardMode()) {
                    drawWell(g, leftPos + 6, topPos + 23, GUI_WIDTH - 12, 46);
                    drawWell(g, leftPos + 6, topPos + 85, GUI_WIDTH - 12, 32);
                    drawWell(g, leftPos + 6, topPos + 139, GUI_WIDTH - 12, 30);
                    g.fill(leftPos + GUI_WIDTH / 2, topPos + 142, leftPos + GUI_WIDTH / 2 + 1, topPos + 166, 0xFF696D88);
                } else {
                    int upperH = freqMenu().isController() ? 86 : 70;
                    drawWell(g, leftPos + 6, topPos + 23, GUI_WIDTH - 12, upperH);
                    drawWell(g, leftPos + 6, topPos + 131, GUI_WIDTH - 12, 36);
                }
            } else if (currentTab == FrequencyPage.TAB_CONNECTION) {
                int visible = Math.min(ITEMS_PER_PAGE_LIST,
                        Math.max(0, ClientFrequencyCache.getConnections(freqMenu().getCurrentFrequencyId()).size() - connectionScroll));
                for (int i = 0; i < visible; i++) {
                    g.blit(TEXTURE_LIST, leftPos + LIST_ROW_X, topPos + LIST_ROW_FIRST_Y + i * LIST_ROW_HEIGHT,
                            0, 208, LIST_ROW_WIDTH, LIST_ROW_BUTTON_HEIGHT, 256, 256);
                }
            }
        }

        if (popupMemberUUID != null) {
            drawMemberPopupPanel(g);
        }
        if (deleteConfirmOpen) {
            drawDeleteConfirmPanel(g);
        }
        if (passwordPromptFreqId > 0) {
            drawPasswordPromptPanel(g);
        }
    }

    private void drawModalBackdrop(GuiGraphics g, int panelX, int panelY, int panelW, int panelH) {
        g.fill(leftPos + 2, topPos + 18, leftPos + GUI_WIDTH - 2, topPos + imageHeight - 3, 0x5810121A);
        BackgroundGenerator.draw(panelW, panelH, g, panelX, panelY);
        drawWell(g, panelX + 8, panelY + 8, panelW - 16, 18);
    }

    private void drawPasswordPromptPanel(GuiGraphics g) {
        drawModalBackdrop(g, leftPos + 8, topPos + 42, GUI_WIDTH - 16, 96);
    }

    private void drawDeleteConfirmPanel(GuiGraphics g) {
        drawModalBackdrop(g, leftPos + 8, topPos + 42, GUI_WIDTH - 16, 96);
    }

    private void drawMemberPopupPanel(GuiGraphics g) {
        drawModalBackdrop(g, leftPos + 8, topPos + 26, GUI_WIDTH - 16, 148);
    }

    /** Draws an AE2-style recessed well: 1px #413F54 top/left shadow, 1px #F2F2F2 bottom/right highlight, #696D88 inner rim, #9A9FB4 fill. */
    private static void drawWell(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFFF2F2F2);
        g.fill(x, y, x + width - 1, y + 1, 0xFF413F54);
        g.fill(x, y, x + 1, y + height - 1, 0xFF413F54);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF696D88);
        g.fill(x + 2, y + 2, x + width - 1, y + height - 1, 0xFF9A9FB4);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        fittedTextTooltips.clear();

        drawFlat(g, Component.translatable(currentTab.getTranslationKey()), 8, 6, AE2_TEXT_TITLE);

        if (popupMemberUUID == null && !deleteConfirmOpen && passwordPromptFreqId == 0) {
            switch (currentTab) {
                case TAB_HOME -> renderHomeLabels(g);
                case TAB_SELECTION -> renderSelectionLabels(g);
                case TAB_MEMBER -> renderMemberLabels(g);
                case TAB_CREATE, TAB_SETTING -> renderFormLabels(g);
                case TAB_CONNECTION -> renderLocalConnections(g);
                case TAB_DEVICES, TAB_ENERGY -> {}
            }
        }

        if (deleteConfirmOpen) {
            drawFlatCentered(g,
                    Component.translatable("ae2lt.gui.delete_confirm.title"),
                    imageWidth / 2, 55, COL_RED);
            drawFlatCentered(g,
                    Component.translatable("ae2lt.gui.delete_confirm.hint"),
                    imageWidth / 2, 80, AE2_TEXT_LABEL);
        }

        if (passwordPromptFreqId > 0) {
            drawFlatCenteredFitted(g,
                    Component.translatable("ae2lt.gui.password_prompt.title", passwordPromptFreqName),
                    imageWidth / 2, 55, GUI_WIDTH - 40, AE2_TEXT_TITLE);
        }

        if (popupMemberUUID != null) {
            String suffix = popupIsStranger
                    ? " [" + Component.translatable("ae2lt.gui.member.stranger_short").getString() + "]"
                    : " [" + accessLabel(popupMemberAccess) + "]";
            String full = popupMemberName + suffix;
            String fitted = FittingText.fit(full, GUI_WIDTH - 52, font::width);
            int textW = font.width(fitted);
            int totalW = 10 + textW;
            int startX = (imageWidth - totalW) / 2;
            int badgeRgb = popupIsStranger || popupMemberAccess.getFormatting().getColor() == null
                    ? 0xFF555555
                    : (0xFF000000 | popupMemberAccess.getFormatting().getColor());
            g.fill(startX, 40, startX + 6, 46, 0xFF202430);
            g.fill(startX + 1, 41, startX + 5, 45, badgeRgb);
            g.drawString(font, fitted, startX + 10, 39, AE2_TEXT_BODY, false);
        }

        if (inlineError != null) {
            if (System.currentTimeMillis() < inlineErrorExpiresAt) {
                int maxTextW = imageWidth - 16;
                String full = inlineError.getString();
                String fitted = FittingText.fit(full, maxTextW, font::width);
                Component display = full.equals(fitted)
                        ? inlineError
                        : Component.literal(fitted).setStyle(inlineError.getStyle());
                int textW = font.width(display);
                int bgW = Math.min(imageWidth - 8, textW + 8);
                int bgX = (imageWidth - bgW) / 2;
                int bgY = imageHeight - 40;
                g.fill(bgX, bgY, bgX + bgW, bgY + 12, 0xD0202028);
                g.fill(bgX, bgY, bgX + bgW, bgY + 1, 0xFFD04848);
                drawFlatCentered(g, display, imageWidth / 2, bgY + 2, 0xFFFFB4B4);
                if (!full.equals(fitted)) {
                    addFittedTooltip(bgX, bgY, bgW, 12, inlineError);
                }
            } else {
                inlineError = null;
            }
        }
    }

    private void drawFlat(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    private void drawFlatCentered(GuiGraphics g, Component text, int centerX, int y, int color) {
        int w = font.width(text);
        g.drawString(font, text, centerX - w / 2, y, color, false);
    }

    private void drawFlatFitted(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        String full = text.getString();
        String fitted = FittingText.fit(full, maxWidth, font::width);
        Component display = full.equals(fitted)
                ? text
                : Component.literal(fitted).setStyle(text.getStyle());
        g.drawString(font, display, x, y, color, false);
        if (!full.equals(fitted)) {
            addFittedTooltip(x, y, Math.min(maxWidth, font.width(display)), 9, text);
        }
    }

    private void drawFlatFitted(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        String fitted = FittingText.fit(text, maxWidth, font::width);
        g.drawString(font, fitted, x, y, color, false);
        if (!text.equals(fitted)) {
            addFittedTooltip(x, y, Math.min(maxWidth, font.width(fitted)), 9, Component.literal(text));
        }
    }

    private void drawFlatCenteredFitted(GuiGraphics g, Component text, int centerX, int y, int maxWidth, int color) {
        String full = text.getString();
        String fitted = FittingText.fit(full, maxWidth, font::width);
        Component display = full.equals(fitted)
                ? text
                : Component.literal(fitted).setStyle(text.getStyle());
        int w = font.width(display);
        int x = centerX - w / 2;
        g.drawString(font, display, x, y, color, false);
        if (!full.equals(fitted)) {
            addFittedTooltip(x, y, Math.min(maxWidth, w), 9, text);
        }
    }

    private void addFittedTooltip(int x, int y, int width, int height, Component text) {
        fittedTextTooltips.add(new FittedTextTooltip(new Rect2i(leftPos + x, topPos + y, width, height), text));
    }

    private void renderHomeLabels(GuiGraphics g) {
        boolean card = freqMenu().isCardMode();
        int step = card ? 14 : 16;
        int y0 = card ? 28 : 29;
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        Component frequency = freq == null ? Component.translatable("ae2lt.gui.frequency.none")
                : Component.literal(freq.name());
        Integer freqSwatch = freq == null ? null : (0xFF000000 | (freq.color() & 0xFFFFFF));
        homeWellRow(g, "ae2lt.gui.frequency.current", frequency, y0, AE2_TEXT_BODY, freqSwatch, true, step);
        homeWellRow(g, "ae2lt.gui.home.device_type", Component.translatable(freqMenu().getDeviceName()), y0 + step, AE2_TEXT_BODY, null, true, step);
        boolean active = freqMenu().isLinkActive();
        int statusColor = active ? COL_GREEN : COL_RED;
        homeWellRow(g, "ae2lt.gui.home.status",
                Component.translatable(active ? "ae2lt.gui.home.connected" : "ae2lt.gui.home.disconnected"),
                y0 + step * 2, statusColor, 0xFF000000 | statusColor, !card, step);

        if (card) {
            renderFrequencyDetails(g, 74, 85, 14);
            drawFlatFitted(g, Component.translatable("ae2lt.gui.home.auto_section"), 9, 126,
                    imageWidth - 18, AE2_TEXT_MUTED);
            boolean chAuto = freqMenu().isAutoConnect();
            boolean feAuto = freqMenu().isAutoConnectEnergy();
            g.fill(13, 152, 17, 156, chAuto ? (0xFF000000 | COL_GREEN) : 0xFF5A6075);
            drawFlatFitted(g, Component.translatable("ae2lt.gui.home.auto_channel_" + (chAuto ? "on" : "off")),
                    20, 150, 68, chAuto ? COL_GREEN : AE2_TEXT_LABEL);
            g.fill(125, 152, 129, 156, feAuto ? (0xFF000000 | COL_GREEN) : 0xFF5A6075);
            drawFlatFitted(g, Component.translatable("ae2lt.gui.home.auto_energy_" + (feAuto ? "on" : "off")),
                    132, 150, 68, feAuto ? COL_GREEN : AE2_TEXT_LABEL);
            return;
        }
        int used = freqMenu().getUsedChannels(), max = freqMenu().getMaxChannels();
        Component channels = max < 0 ? Component.translatable("ae2lt.gui.home.grid_channels.infinite", used)
                : Component.translatable("ae2lt.gui.home.grid_channels.value", used, max, Math.max(0, max - used));
        boolean controller = freqMenu().isController();
        homeWellRow(g, "ae2lt.gui.home.grid_channels", channels, y0 + step * 3, AE2_TEXT_BODY, null, controller, step);
        if (controller) {
            boolean crossDim = freqMenu().isAdvanced();
            homeWellRow(g, "ae2lt.gui.home.cross_dimension",
                    Component.translatable(crossDim ? "ae2lt.gui.home.cross_dimension.yes" : "ae2lt.gui.home.cross_dimension.no"),
                    y0 + step * 4, crossDim ? COL_GREEN : AE2_TEXT_LABEL, null, false, step);
        }
        renderFrequencyDetails(g, 118, 131, 16);
    }

    private void renderFrequencyDetails(GuiGraphics g, int headerY, int wellTop, int step) {
        var frequency = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        drawFlat(g, Component.translatable("ae2lt.gui.home.frequency_details"), 9, headerY, AE2_TEXT_MUTED);
        int row0Y = wellTop + (step == 16 ? 6 : 5);
        homeWellRow(g, "ae2lt.gui.frequency.security", frequency == null
                ? Component.translatable("ae2lt.gui.frequency.none") : getSecurityLabel(frequency.security()),
                row0Y, AE2_TEXT_BODY, null, true, step);
        var access = selfAccess();
        Component permission = frequency == null ? Component.translatable("ae2lt.gui.frequency.none")
                : !access.canUse() ? Component.translatable("ae2lt.gui.home.no_access")
                : Component.literal(accessLabel(access));
        Integer accessColor = frequency != null && access.canUse() && access.getFormatting().getColor() != null
                ? (0xFF000000 | access.getFormatting().getColor())
                : null;
        homeWellRow(g, "ae2lt.gui.home.access", permission, row0Y + step,
                frequency != null && !access.canUse() ? COL_RED : AE2_TEXT_BODY, accessColor, false, step);
    }

    /** Left-label / right-value row inside an AE2 recessed well, with optional indicator swatch and bottom divider. */
    private void homeWellRow(GuiGraphics g, String labelKey, Component value, int y, int valueColor,
            Integer badgeColor, boolean divider, int step) {
        Component label = Component.translatable(labelKey);
        drawFlat(g, label, 13, y, AE2_TEXT_LABEL);
        int maxValW = Math.max(30, GUI_WIDTH - 28 - font.width(label) - (badgeColor != null ? 10 : 0));
        String full = value.getString();
        String fitted = FittingText.fit(full, maxValW, font::width);
        int valW = font.width(fitted);
        int rightX = GUI_WIDTH - 13 - valW;
        if (badgeColor != null) {
            int bx = rightX - 9;
            int by = y + 1;
            g.fill(bx, by, bx + 6, by + 6, 0xFF202430);
            g.fill(bx + 1, by + 1, bx + 5, by + 5, badgeColor);
        }
        g.drawString(font, fitted, rightX, y, valueColor, false);
        if (!full.equals(fitted)) {
            addFittedTooltip(rightX, y, valW, 9, value);
        }
        if (divider) {
            int divY = y + (step == 16 ? 12 : 11);
            g.fill(12, divY, GUI_WIDTH - 12, divY + 1, 0xFF868BA4);
        }
    }

    private void renderSelectionLabels(GuiGraphics g) {
        var freqs = ClientFrequencyCache.getAllFrequenciesSorted();
        var current = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());

        Component left = Component.translatable("ae2lt.gui.frequency.current")
                .append(": ")
                .append(current != null
                        ? Component.literal(current.name())
                        : Component.translatable("ae2lt.gui.frequency.none").withStyle(ChatFormatting.DARK_GRAY));
        Component right = Component.translatable("ae2lt.gui.frequency.total").append(": " + freqs.size());
        int rightX = imageWidth - 10 - font.width(right);
        int leftX = 10;
        if (current != null) {
            int labelPrefixW = font.width(Component.translatable("ae2lt.gui.frequency.current").append(": "));
            drawFlat(g, Component.translatable("ae2lt.gui.frequency.current").append(": "), leftX, 37, AE2_TEXT_MUTED);
            int swatchX = leftX + labelPrefixW;
            g.fill(swatchX, 38, swatchX + 6, 44, 0xFF202430);
            g.fill(swatchX + 1, 39, swatchX + 5, 43, 0xFF000000 | (current.color() & 0xFFFFFF));
            drawFlatFitted(g, current.name(), swatchX + 9, 37, Math.max(20, rightX - swatchX - 16), AE2_TEXT_BODY);
        } else {
            drawFlatFitted(g, left, leftX, 37, Math.max(20, rightX - 24), AE2_TEXT_MUTED);
        }
        drawFlat(g, right, rightX, 37, AE2_TEXT_MUTED);

        if (filteredFrequencies().isEmpty()) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.frequency.none"),
                    LIST_ROW_X + LIST_ROW_WIDTH / 2, LIST_ROW_FIRST_Y + 56, AE2_TEXT_LABEL);
        }
    }

    private void renderMemberLabels(GuiGraphics g) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.error.no_frequency"),
                    LIST_ROW_X + LIST_ROW_WIDTH / 2, LIST_ROW_FIRST_Y + 56, AE2_TEXT_LABEL);
            return;
        }

        FrequencyAccessLevel myAccess = selfAccess();
        Component left = Component.translatable("ae2lt.gui.member.your_access",
                Component.translatable("ae2lt.gui.access." + myAccess.name().toLowerCase()));
        var members = ClientFrequencyCache.getMembers(currentId);
        Component right = Component.translatable("ae2lt.gui.frequency.total")
                .append(": " + members.size());
        int rightX = imageWidth - 12 - font.width(right);
        drawFlatFitted(g, left, 12, 28, Math.max(20, rightX - 24), AE2_TEXT_BODY);
        drawFlat(g, right, rightX, 28, AE2_TEXT_LABEL);

        if (members.isEmpty()) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.member.none"),
                    LIST_ROW_X + LIST_ROW_WIDTH / 2, LIST_ROW_FIRST_Y + 56, AE2_TEXT_LABEL);
        }
    }

    private void renderFormLabels(GuiGraphics g) {
        if (currentTab == FrequencyPage.TAB_SETTING && ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId()) == null) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.error.no_frequency"), imageWidth / 2, 60, AE2_TEXT_MUTED);
            return;
        }
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.name").append(":"), 12, 22, AE2_TEXT_MUTED);
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.security").append(":"), 12, 57, AE2_TEXT_MUTED);
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.password").append(":"), 12, 75, AE2_TEXT_MUTED);
        if (editSecurity != FrequencySecurityLevel.ENCRYPTED && passwordField != null) {
            var bounds = passwordField.getTooltipArea();
            int bx = bounds.getX() - leftPos;
            int by = bounds.getY() - topPos;
            int bw = bounds.getWidth();
            Blitter.texture("guis/text_field.png", 128, 128).src(0, 12, 1, 12).dest(bx, by).blit(g);
            for (int x = 1; x < bw - 1; x += 126) {
                int w = Math.min(126, bw - 1 - x);
                Blitter.texture("guis/text_field.png", 128, 128).src(1, 12, w, 12).dest(bx + x, by).blit(g);
            }
            Blitter.texture("guis/text_field.png", 128, 128).src(127, 12, 1, 12).dest(bx + bw - 1, by).blit(g);
            drawFlatFitted(g, getSecurityLabel(editSecurity), bx + 2, by + 2, INPUT_MAX_WIDTH - 8, 0xFFA0A4B8);
        }
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.color").append(":"), 13, 111, AE2_TEXT_LABEL);

        // Live preview card inside the lower recessed well
        int px = 12, py = 143, pw = INPUT_MAX_WIDTH, ph = 22;
        g.fill(px, py, px + pw, py + ph, 0xFF878FA5);
        g.fill(px, py, px + pw, py + 1, 0xFF565B70);
        g.fill(px, py, px + 1, py + ph, 0xFF565B70);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFFDADEEB);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0xFFDADEEB);

        g.fill(px + 6, py + 7, px + 14, py + 15, 0xFF202430);
        g.fill(px + 7, py + 8, px + 13, py + 14, editColor | 0xFF000000);
        String previewName = (nameField != null && !nameField.getValue().isBlank())
                ? nameField.getValue()
                : Component.translatable("ae2lt.gui.frequency.name").getString();
        String secSuffix = " [" + getSecurityLabel(editSecurity).getString() + "]";
        String fittedPreview = FittingText.fit(previewName + secSuffix, pw - 26, font::width);
        g.drawString(font, fittedPreview, px + 18, py + 7, AE2_TEXT_BODY, false);
    }

    private static Component getSecurityLabel(FrequencySecurityLevel level) {
        return switch (level) {
            case PUBLIC -> Component.translatable("ae2lt.gui.security.public");
            case ENCRYPTED -> Component.translatable("ae2lt.gui.security.encrypted");
            case PRIVATE -> Component.translatable("ae2lt.gui.security.private");
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderFittedTextTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (currentTab == FrequencyPage.TAB_SELECTION && selectionSearchField != null
                    && selectionSearchField.isVisible() && selectionSearchField.isMouseOver(mouseX, mouseY)) {
                selectionSearchField.setValue("");
                setFocused(selectionSearchField);
                selectionSearchField.setFocused(true);
                return true;
            }
            if ((currentTab == FrequencyPage.TAB_CREATE || currentTab == FrequencyPage.TAB_SETTING)
                    && nameField != null && nameField.isVisible() && nameField.isMouseOver(mouseX, mouseY)) {
                nameField.setValue("");
                setFocused(nameField);
                nameField.setFocused(true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (passwordPromptFreqId > 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitPasswordPrompt();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelPasswordPrompt();
                return true;
            }
        } else if (deleteConfirmOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            deleteConfirmOpen = false;
            scheduleRebuild();
            return true;
        } else if (popupMemberUUID != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePopup();
            scheduleRebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Helpers

    private void renderFittedTextTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = fittedTextTooltips.size() - 1; i >= 0; i--) {
            var tooltip = fittedTextTooltips.get(i);
            var area = tooltip.area();
            if (isWithinArea(mouseX, mouseY, area)) {
                g.renderComponentTooltip(font, List.of(tooltip.text()), mouseX, mouseY);
                return;
            }
        }
    }

    private static boolean isWithinArea(int mouseX, int mouseY, Rect2i area) {
        return mouseX >= area.getX()
                && mouseX < area.getX() + area.getWidth()
                && mouseY >= area.getY()
                && mouseY < area.getY() + area.getHeight();
    }

    private boolean isSelf(UUID uuid) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(uuid);
    }

    private FrequencyAccessLevel selfAccess() {
        var members = ClientFrequencyCache.getMembers(freqMenu().getCurrentFrequencyId());
        var mc = Minecraft.getInstance();
        if (mc.player == null) return FrequencyAccessLevel.BLOCKED;
        UUID me = mc.player.getUUID();
        for (var m : members) {
            if (m.uuid().equals(me)) return m.access();
        }
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        if (freq != null && freq.security() == FrequencySecurityLevel.PUBLIC) {
            return FrequencyAccessLevel.USER;
        }
        return FrequencyAccessLevel.BLOCKED;
    }

    private boolean hasManagerAccess() {
        return selfAccess().isManager();
    }

    private boolean hasOwnerAccess() {
        return selfAccess().isOwner();
    }

    private static String accessLabel(FrequencyAccessLevel a) {
        return Component.translatable("ae2lt.gui.access." + a.name().toLowerCase()).getString();
    }

    private record FittedTextTooltip(Rect2i area, Component text) {}

    /** AE2 color swatch button in the Create / Settings form. */
    private final class ColorSwatchButton extends AE2Button {
        private final int swatchColor;

        ColorSwatchButton(int x, int y, int width, int height, int swatchColor) {
            super(x, y, width, height, Component.literal(" "), btn -> editColor = swatchColor);
            this.swatchColor = swatchColor;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean selected = swatchColor == editColor;
            boolean hovered = active && isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, selected ? 0xFFFFFFFF : hovered ? 0xFFDAFFFF : 0xFF363B4E);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF202430);
            g.fill(x + 2, y + 2, x + w - 2, y + h - 2, swatchColor | 0xFF000000);
        }
    }

    /** AE2 list row with a persistent binding badge independent of hover/focus, preserving label colours. */
    private final class RowSpriteButton extends AE2Button {
        private final boolean bound;
        private final Component boundLabel = Component.translatable("ae2lt.gui.frequency.bound");
        RowSpriteButton(int x, int y, int width, int height,
                Component message, OnPress onPress) {
            this(x, y, width, height, message, false, onPress);
        }

        RowSpriteButton(int x, int y, int width, int height,
                Component message, boolean bound, OnPress onPress) {
            super(x, y, width, height, message, onPress);
            this.bound = bound;
            if (bound) {
                setTooltip(Tooltip.create(Component.empty().append(message).append(" — ").append(boundLabel)));
            } else if (!message.getString().equals(FittingText.fit(message.getString(), labelWidth(), font::width))) {
                setTooltip(Tooltip.create(message));
            }
        }

        private int labelWidth() { return getWidth() - 22 - (bound ? font.width(boundLabel) + 14 : 0); }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return bound ? Component.empty().append(super.createNarrationMessage()).append(". ").append(boundLabel)
                    : super.createNarrationMessage();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = active && isHoveredOrFocused();
            if (bound) {
                g.fill(x, y, x + w, y + h, hover ? 0xFF88C0F4 : 0xFF78B0E4);
                g.fill(x, y, x + w, y + 1, 0xFFDAFFFF);
                g.fill(x, y + h - 1, x + w, y + h, 0xFF5282B8);
                g.fill(x, y, x + 2, y + h, 0xFF1864AB);
            } else {
                g.blit(TEXTURE_SELECTION, x, y, 0, hover ? 232 : 208, w, h, 256, 256);
            }

            TextColor styleColor = getMessage().getStyle().getColor();
            int textY = y + (h - 8) / 2;
            int textX = x + 6;
            if (styleColor != null) {
                int rgb = 0xFF000000 | styleColor.getValue();
                g.fill(x + 6, y + (h - 6) / 2, x + 12, y + (h - 6) / 2 + 6, 0xFF202430);
                g.fill(x + 7, y + (h - 6) / 2 + 1, x + 11, y + (h - 6) / 2 + 5, rgb);
                textX = x + 16;
            }

            int textColor = bound ? 0xFF102840 : active ? AE2_TEXT_BODY : AE2_TEXT_LABEL;
            String full = getMessage().getString();
            String fitted = FittingText.fit(full, labelWidth(), font::width);
            g.drawString(font, fitted, textX, textY, textColor, false);

            if (bound) {
                int bw = font.width(boundLabel) + 6;
                int bx = x + w - bw - 4;
                int by = y + (h - 12) / 2;
                g.fill(bx, by, bx + bw, by + 12, 0x44183858);
                g.drawString(font, boundLabel, bx + 3, textY, 0xFF102840, false);
            }
        }
    }

    /** Bridges the native composite widget into this container screen's event routing. */
    private final class ScrollbarWidget extends AbstractWidget {
        private final Scrollbar delegate = new Scrollbar(Scrollbar.BIG);
        private final java.util.function.IntConsumer onScroll;
        private int lastScroll;

        ScrollbarWidget(int x, int y, int trackHeight, int totalItems, int visibleItems,
                int initialScroll, java.util.function.IntConsumer onScroll) {
            super(x, y, SCROLLBAR_WIDTH, trackHeight, Component.empty());
            this.onScroll = onScroll;
            delegate.setPosition(new Point(x, y));
            delegate.setHeight(trackHeight);
            delegate.setRange(0, Math.max(0, totalItems - visibleItems), 1);
            delegate.setCurrentScroll(initialScroll);
            lastScroll = delegate.getCurrentScroll();
        }
        private void sync() {
            int current = delegate.getCurrentScroll();
            if (current != lastScroll) { lastScroll = current; onScroll.accept(current); }
        }
        void tick() { delegate.tick(); sync(); }
        boolean handleScroll(double delta) {
            boolean handled = delegate.onMouseWheel(new Point(getX(), getY()), delta);
            sync(); return handled;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            delegate.drawForegroundLayer(g, delegate.getBounds(), new Point(mx, my));
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (!visible || !active || !isMouseOver(x, y)) return false;
            boolean handled = delegate.onMouseDown(new Point((int)x, (int)y), button);
            sync(); return handled;
        }
        @Override public boolean mouseReleased(double x, double y, int button) {
            return delegate.onMouseUp(new Point((int)x, (int)y), button);
        }
        @Override protected void onDrag(double x, double y, double dx, double dy) {
            drag(x, y, 0);
        }
        boolean drag(double x, double y, int button) {
            boolean handled = delegate.onMouseDrag(new Point((int)x, (int)y), button);
            sync(); return handled;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (currentScrollbar != null && currentScrollbar.drag(x, y, button)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (currentScrollbar != null) currentScrollbar.mouseReleased(x, y, button);
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (currentScrollbar != null && x >= leftPos + LIST_ROW_X && x < leftPos + GUI_WIDTH
                && y >= topPos + LIST_ROW_FIRST_Y && y < topPos + LIST_ROW_FIRST_Y + ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT
                && currentScrollbar.handleScroll(dy)) return true;
        return super.mouseScrolled(x, y, dx, dy);
    }
}
