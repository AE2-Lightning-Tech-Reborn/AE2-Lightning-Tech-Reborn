package com.moakiee.ae2lt.client.gui;
import com.moakiee.ae2lt.network.NetworkInit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Color;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.gui.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.TabButton;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.SwitchGuisPacket;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.frequency.ClientFrequencyCache;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Multi-tab frequency management screen, modeled after Flux Networks' GUI.
 * Polls {@link ClientFrequencyCache#revision()} each frame and rebuilds
 * widgets when the server-pushed state changes.
 */
public class FrequencyScreen extends AbstractContainerScreen<FrequencyMenu> {

    private static final int GUI_WIDTH = 195;
    private static final int GUI_HEIGHT = 157;

    /**
     * AE2 {@link TabButton} renders at a fixed 22×22 size (see its
     * constructor bytecode). We expose these as named constants so the
     * rest of the screen can hit-test the same footprint.
     */
    private static final int TAB_WIDTH = 22;
    private static final int TAB_HEIGHT = 22;
    /** Horizontal pitch of the flush AE2 BOX tabs. */
    private static final int TAB_STEP = 22;
    /** The list texture has five rows; the selection texture reserves one for search. */
    private static final int ITEMS_PER_PAGE_LIST = 5;
    private static final int ITEMS_PER_PAGE_SEARCH = 4;
    /** Row pitch including the shared one-pixel shadow. */
    private static final int LIST_ROW_HEIGHT = 21;
    /** Button height inside a list row — matches the sprite height (20 px). */
    private static final int LIST_ROW_BUTTON_HEIGHT = 20;
    /** First row's top y (local) — slot 0 interior starts at y=38. */
    private static final int LIST_ROW_FIRST_Y = 38;
    /** Left x (local) of a row button — well interior left edge is x=9. */
    private static final int LIST_ROW_X = 9;
    /**
     * Width of a row button — exactly 160 px to match the row sprite
     * baked at the bottom of the chassis PNGs (u=0, w=160). Spans the
     * well interior x=9..168 with no gap before the scrollbar gutter.
     */
    private static final int LIST_ROW_WIDTH = 160;

    /** Idle and hover row sprites below the chassis in both list textures. */
    private static final int ROW_SPRITE_WIDTH = 160;
    private static final int ROW_SPRITE_HEIGHT = 20;
    private static final int ROW_SPRITE_IDLE_V = 158;
    private static final int ROW_SPRITE_HOVER_V = 180;

    /** Centers the 12-pixel handle over the texture's six-pixel track at x=178..183. */
    private static final int SCROLLBAR_X = 175;
    /** Scrollbar track Y (local) — first row of the recessed well content. */
    private static final int SCROLLBAR_Y = 38;
    /** Scrollbar handle width — AE2 big_scroller sprite is 12 px wide. */
    private static final int SCROLLBAR_WIDTH = 12;
    /** Scrollbar handle height — AE2 big_scroller sprite is 15 px tall. */
    private static final int SCROLLBAR_HANDLE_HEIGHT = 15;

    /** Per-tab 256x256 atlases with a 195x157 chassis and row sprites below it. */
    private static final ResourceLocation BG_HOME = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/wireless_overloaded_home.png");
    private static final ResourceLocation BG_SELECTION = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/wireless_overloaded_selection.png");
    private static final ResourceLocation BG_LIST = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/wireless_overloaded_list.png");
    private static final ResourceLocation BG_FORM = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/wireless_overloaded_form.png");
    private static final int TEXTURE_SIZE = 256;

    private static final int AE2_TEXT_TITLE = 0x000000;
    private static final int AE2_TEXT_BODY  = 0x000000;
    /**
     * Slightly de-emphasised tone for "label vs value" pairs (field
     * captions like "name:" / "security:") and quiet annotations like
     * row counts. Still a flat dark gray — no tint.
     */
    private static final int AE2_TEXT_MUTED = 0x404040;

    /**
     * AETextField's native {@code guis/text_field.png} frame is a fixed 12
     * px tall 3-slice. Creating the field at this exact height keeps the
     * AE2 frame flush with the recessed shelf we draw behind it.
     */
    private static final int INPUT_HEIGHT = 12;

    /** AE2's text-field middle slice caps at 126 pixels, leaving a gap above 128 total. */
    private static final int INPUT_MAX_WIDTH = 128;

    /** Palette for AETextField, initialized through reflection because this screen has no style JSON. */
    private static final ScreenStyle AE2_STYLE = buildAe2Style();

    @SuppressWarnings("unchecked")
    private static ScreenStyle buildAe2Style() {
        ScreenStyle style = new ScreenStyle();
        try {
            Field paletteField = ScreenStyle.class.getDeclaredField("palette");
            paletteField.setAccessible(true);
            Map<PaletteColor, Color> palette = (Map<PaletteColor, Color>) paletteField.get(style);
            // AE2 hex order is (a, r, g, b) via Color(r, g, b, a)
            palette.put(PaletteColor.DEFAULT_TEXT_COLOR,    new Color(0x40, 0x40, 0x40, 0xFF));
            palette.put(PaletteColor.MUTED_TEXT_COLOR,      new Color(0x7F, 0x7F, 0x7F, 0xFF));
            palette.put(PaletteColor.SELECTION_COLOR,       new Color(0x78, 0xAA, 0xFF, 0x78));
            // Text inside AETextField renders over the dark slate interior of
            // guis/text_field.png — dark-on-dark would be invisible, so we
            // match AE2's own Quartz Knife field and use near-white text.
            palette.put(PaletteColor.TEXTFIELD_TEXT,        new Color(0xFF, 0xFF, 0xFF, 0xFF));
            palette.put(PaletteColor.TEXTFIELD_PLACEHOLDER, new Color(0x60, 0x60, 0x60, 0xFF));
            palette.put(PaletteColor.TEXTFIELD_SELECTION,   new Color(0x78, 0xAA, 0xFF, 0x78));
            palette.put(PaletteColor.TEXTFIELD_ERROR,       new Color(0xC8, 0x46, 0x46, 0xFF));
            palette.put(PaletteColor.ERROR,                 new Color(0xC8, 0x46, 0x46, 0xFF));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to populate AE2 ScreenStyle palette for FrequencyScreen", e);
        }
        return style;
    }

    private FrequencyNavigationTab currentTab = FrequencyNavigationTab.TAB_HOME;
    /** Index of the first visible row in each list tab. */
    private int selectionScroll = 0;
    private int memberScroll = 0;
    private int connectionScroll = 0;

    /** Active list scrollbar; receives wheel events from anywhere over the screen. */
    private ScrollbarWidget currentScrollbar;

    /** Replace rows independently to preserve the scrollbar's drag state. */
    private final java.util.List<AbstractWidget> currentRowButtons = new java.util.ArrayList<>();

    private AETextField nameField;
    private AETextField passwordField;
    private FrequencySecurityLevel editSecurity = FrequencySecurityLevel.PRIVATE;
    private int editColor = 0x1E90FF;

    private int lastCacheRevision = -1;
    private int lastFreqId = Integer.MIN_VALUE;
    private boolean lastAutoConnect;

    // popup state for Members tab
    private UUID popupMemberUUID;
    private String popupMemberName = "";
    private FrequencyAccessLevel popupMemberAccess = FrequencyAccessLevel.USER;
    // When true, the popup target is an online player who isn't a member
    // yet. Only the "Set as User" action is meaningful — the other three
    // buttons are greyed out since there's nothing to demote or remove.
    private boolean popupIsStranger = false;

    /** Latest server response, drawn inside the GUI because container screens cover the action bar. */
    private Component inlineError = null;
    private long inlineErrorExpiresAt = 0L;
    private static final long INLINE_ERROR_DURATION_MS = 4000L;
    private final List<FittedTextTooltip> fittedTextTooltips = new ArrayList<>();

    /** Replaces the inline toast with the latest server response. */
    public void showInlineError(Component message) {
        this.inlineError = message;
        this.inlineErrorExpiresAt = System.currentTimeMillis() + INLINE_ERROR_DURATION_MS;
    }

    // When true, the Settings-tab Delete button opened the confirmation
    // modal; the underlying tab body is suppressed and only
    // Confirm/Cancel are actionable. Cleared on tab switch and after
    // either button fires.
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
    }

    @Override
    protected void init() {
        super.init();
        lastCacheRevision = ClientFrequencyCache.revision();
        lastFreqId = freqMenu().getCurrentFrequencyId();
        lastAutoConnect = freqMenu().isAutoConnect();
        initTabWidgets();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        int rev = ClientFrequencyCache.revision();
        int fid = freqMenu().getCurrentFrequencyId();
        boolean auto = freqMenu().isAutoConnect();
        if (rev != lastCacheRevision || fid != lastFreqId || auto != lastAutoConnect) {
            lastCacheRevision = rev;
            lastFreqId = fid;
            lastAutoConnect = auto;
            initTabWidgets();
        }
    }

    /** Disables the vanilla EditBox frame so only AE2's text-field sprite is drawn. */
    private AETextField makeAe2Field(int x, int y, int width, int height) {
        AETextField field = new AETextField(AE2_STYLE, font, x, y, width, height);
        field.setBordered(false);
        return field;
    }

    /**
     * Eight bullets representing a stored hash. Untouched settings send an empty password
     * to preserve that hash; the first edit clears the sentinel.
     */
    private static final String PASSWORD_SENTINEL = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    /** Whether the password is untouched and should preserve the stored hash. */
    private boolean settingsPasswordPristine = false;

    /**
     * Frequency awaiting password unlock; zero means no modal. Cleared after the server
     * updates membership, so rejected passwords leave the prompt open for retry.
     */
    private int passwordPromptFreqId = 0;
    private String passwordPromptFreqName = "";
    /** For locked-device access, cancelling the prompt closes the screen. */
    private boolean passwordPromptLocksScreen = false;
    private AETextField passwordPromptField;

    /** Case-insensitive selection search, retained across widget rebuilds. */
    private String selectionSearchQuery = "";

    /** Clears the Settings password sentinel on the first key or character event. */
    private final class PristinePasswordField extends AETextField {
        PristinePasswordField(int x, int y, int width, int height) {
            super(AE2_STYLE, font, x, y, width, height);
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
        clearWidgets();
        // clearWidgets removed our row buttons + scrollbar from the screen,
        // but our cached references still point at the discarded instances.
        // Reset them so the per-tab init below can rebind to fresh widgets.
        currentRowButtons.clear();
        currentScrollbar = null;
        closePopup();

        int x0 = leftPos;
        int y0 = topPos;

        buildTopTabs(x0, y0, false);

        // Return to the parent menu through AE2 when this screen is a submenu.
        if (freqMenu().hasParentMenu()) {
            Component backTooltip = Component.translatable(
                    freqMenu().isCardMode()
                            ? "ae2lt.gui.button.return_to_terminal"
                            : "ae2lt.gui.button.back");
            HoverableTabButton backButton = new HoverableTabButton(
                    Icon.ARROW_LEFT, null, backTooltip,
                    btn -> NetworkHandler.instance().sendToServer(
                            SwitchGuisPacket.returnToParentMenu()));
            backButton.setStyle(TabButton.Style.BOX);
            backButton.setX(x0 - TAB_WIDTH + 6);
            backButton.setY(y0 - TAB_HEIGHT);
            backButton.setTooltip(Tooltip.create(backTooltip));
            addRenderableWidget(backButton);
        }

        // Dismiss an accepted password prompt, or open one for an encrypted device binding.
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

        // Authentication takes precedence over other controls.
        if (passwordPromptFreqId > 0) {
            buildPasswordPromptWidgets(x0, y0);
            return;
        }

        // Delete-confirm modal suppresses the current tab body so the
        // only clickable widgets (besides tab ears) are Confirm / Cancel.
        if (deleteConfirmOpen) {
            buildDeleteConfirmWidgets(x0, y0);
            return;
        }

        switch (currentTab) {
            case TAB_HOME -> initHomeTab(x0, y0);
            case TAB_SELECTION -> initSelectionTab(x0, y0);
            case TAB_CONNECTION -> initConnectionsTab(x0, y0);
            case TAB_MEMBER -> initMembersTab(x0, y0);
            case TAB_CREATE -> initCreateTab(x0, y0);
            case TAB_SETTING -> initSettingsTab(x0, y0);
        }
    }

    /** Adds modal buttons above the delete panel rendered by {@link #drawDeleteConfirmPanel}. */
    private void buildDeleteConfirmWidgets(int x0, int y0) {
        addRenderableWidget(new AE2Button(
                x0 + 22, y0 + 92, 58, 16,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> { deleteConfirmOpen = false; scheduleRebuild(); }));

        addRenderableWidget(new AE2Button(
                x0 + 96, y0 + 92, 58, 16,
                Component.translatable("ae2lt.gui.button.confirm")
                        .copy().withStyle(ChatFormatting.DARK_RED),
                btn -> {
                    int freqId = freqMenu().getCurrentFrequencyId();
                    if (freqId > 0) {
                        NetworkInit.sendToServer(new DeleteFrequencyPacket(token(), freqId));
                    }
                    deleteConfirmOpen = false;
                    switchTab(FrequencyNavigationTab.TAB_SELECTION);
                }));
    }

    /**
     * Requires password unlock for encrypted frequencies without cached member access.
     * Public access and private-member rejection are handled separately.
     */
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

    /** Prompts when opening a locked device; Cancel closes the entire screen. */
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

    /** Password field and Submit/Cancel controls above the separately rendered modal panel. */
    private void buildPasswordPromptWidgets(int x0, int y0) {
        int fieldX = x0 + (imageWidth - INPUT_MAX_WIDTH) / 2;
        passwordPromptField = makeAe2Field(fieldX, y0 + 68, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        passwordPromptField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordPromptField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.password"));
        addRenderableWidget(passwordPromptField);

        addRenderableWidget(new AE2Button(
                x0 + 22, y0 + 92, 58, 16,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> cancelPasswordPrompt()));

        addRenderableWidget(new AE2Button(
                x0 + 96, y0 + 92, 58, 16,
                Component.translatable("ae2lt.gui.button.submit"),
                btn -> submitPasswordPrompt()));
    }

    /** Sends the password and keeps the modal open until updated membership confirms access. */
    private void submitPasswordPrompt() {
        String pw = passwordPromptField == null ? "" : passwordPromptField.getValue();
        int freqId = passwordPromptFreqId;
        if (freqId <= 0) return;
        NetworkInit.sendToServer(new SelectFrequencyPacket(
                token(), freqMenu().getBlockPos(), freqId, pw));
    }

    /** Closes locked-device access or returns a browsing user to the Selection tab. */
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

    /** Builds the shared tab strip. Popup tabs dismiss the member editor before switching. */
    private void buildTopTabs(int x0, int y0, boolean popup) {
        for (int i = 0; i < FrequencyNavigationTab.VALUES.length; i++) {
            FrequencyNavigationTab tab = FrequencyNavigationTab.VALUES[i];
            int bx = tabButtonX(x0, tab, i);
            int by = y0 - TAB_HEIGHT;

            // customIcon takes priority: we hand AE2 {@code null} for the
            // base Icon so its renderWidget skips the icon blit entirely
            // (short-circuits on `icon == null`), leaving the surface clean
            // for our PNG overlay. Only TAB_SETTING has customIcon == null
            // and falls back to the AE2 sprite.
            ResourceLocation customIcon = customIconFor(tab);
            Icon baseIcon = customIcon != null ? null : iconFor(tab);
            Component tooltip = Component.translatable(tab.getTranslationKey());
            Button.OnPress onPress = popup
                    ? btn -> { closePopup(); switchTab(tab); }
                    : btn -> switchTab(tab);

            HoverableTabButton button = new HoverableTabButton(baseIcon, customIcon, tooltip, onPress);
            // BOX renders TAB_BUTTON_BACKGROUND — a fully-framed 22×22
            // sprite with a 1-px border on all four sides, matching AE2's
            // ME Interface / Pattern Access tab style. CORNER uses the
            // BORDERLESS variant which has no side frames, so adjacent
            // tabs read as one continuous strip rather than discrete
            // buttons (the effect reported as "tabs look like a long bar").
            button.setStyle(TabButton.Style.BOX);
            button.setX(bx);
            button.setY(by);
            button.setTooltip(Tooltip.create(tooltip));
            addRenderableWidget(button);
        }
    }

    /** Uses AE2's focus sprite on mouse hover and optionally overlays a custom 16x16 icon. */
    private static final class HoverableTabButton extends TabButton {
        private final ResourceLocation customIcon;

        HoverableTabButton(Icon icon, ResourceLocation customIcon,
                Component tooltip, Button.OnPress onPress) {
            super(icon, tooltip, onPress);
            this.customIcon = customIcon;
        }

        @Override
        public boolean isFocused() {
            return super.isFocused() || this.isHovered();
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Super paints the BOX background sprite and — only when
            // the super icon field is non-null — the AE2 glyph. Passing
            // {@code null} for tabs that have a custom PNG means super
            // leaves the icon surface untouched, so we can overlay our
            // own 16×16 without the AE2 glyph bleeding through the
            // transparent pixels of the overlay.
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            if (customIcon != null) {
                // Match AE2's own (+2, +1) icon offset used by
                // TabButton for the BOX style — see its renderWidget
                // bytecode: it draws the Icon at (getX()+offset, getY()+offset-1)
                // where offset=2 for BOX. Using the same position keeps
                // our custom PNG visually aligned with the AE2 COG that
                // TAB_SETTING still renders through the base class.
                guiGraphics.blit(customIcon,
                        getX() + 2, getY() + 1,
                        0, 0, 16, 16, 16, 16);
            }
        }
    }

    /** AE2 atlas fallback for tabs without a custom icon. */
    private static Icon iconFor(FrequencyNavigationTab tab) {
        return switch (tab) {
            // 1.20.1: no COG sprite; fall back to the wrench.
            case TAB_SETTING -> Icon.WRENCH;
            default -> null;
        };
    }

    /**
     * Custom 16×16 PNG overlay for each tab. Returns {@code null} when
     * the AE2 {@link Icon} from {@link #iconFor} should be the visible
     * sprite — currently only TAB_SETTING keeps the AE2 cog.
     */
    private static ResourceLocation customIconFor(FrequencyNavigationTab tab) {
        return switch (tab) {
            case TAB_HOME       -> TAB_ICON_HOME;
            case TAB_SELECTION  -> TAB_ICON_SELECTION;
            case TAB_CONNECTION -> TAB_ICON_CONNECTION;
            case TAB_MEMBER     -> TAB_ICON_MEMBER;
            case TAB_CREATE     -> TAB_ICON_CREATE;
            case TAB_SETTING    -> null;
        };
    }

    private static final ResourceLocation TAB_ICON_HOME = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/buttons/menu.png");
    private static final ResourceLocation TAB_ICON_SELECTION = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/buttons/frequency_select.png");
    private static final ResourceLocation TAB_ICON_CONNECTION = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/buttons/frequency_connect.png");
    private static final ResourceLocation TAB_ICON_MEMBER = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/buttons/frequency_member.png");
    private static final ResourceLocation TAB_ICON_CREATE = new ResourceLocation(AE2LightningTech.MODID, "textures/gui/buttons/frequency_add.png");

    private void switchTab(FrequencyNavigationTab tab) {
        currentTab = tab;
        selectionScroll = 0;
        memberScroll = 0;
        connectionScroll = 0;
        selectionSearchQuery = "";
        deleteConfirmOpen = false;
        scheduleRebuild();
    }

    /** Defers rebuilding until the click dispatch finishes, avoiding widget replacement mid-click. */
    private void scheduleRebuild() {
        Minecraft.getInstance().execute(this::initTabWidgets);
    }

    // Tab: Home

    private void initHomeTab(int x0, int y0) {
        // Disconnect button sits in the panel strip below the info shelf
        // (shelf y=39..111, chassis bottom y=156). Centring vertically
        // around y=132 keeps it clear of both the shelf bevel above and
        // the chassis bottom bevel below.
        if (freqMenu().isCardMode()) {
            // Card mode adds an auto-link toggle alongside disconnect, so the
            // two share the strip side by side instead of one centred button.
            boolean auto = freqMenu().isAutoConnect();
            addRenderableWidget(new AE2Button(
                    x0 + 8, y0 + 124, 88, 18,
                    Component.translatable(auto
                            ? "ae2lt.gui.button.auto_connect_on"
                            : "ae2lt.gui.button.auto_connect_off"),
                    btn -> freqMenu().clientToggleAutoConnect()));
            addRenderableWidget(new AE2Button(
                    x0 + 99, y0 + 124, 88, 18,
                    Component.translatable("ae2lt.gui.button.disconnect"),
                    btn -> NetworkInit.sendToServer(
                            new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, ""))));
            return;
        }
        addRenderableWidget(new AE2Button(
                x0 + (GUI_WIDTH - 96) / 2, y0 + 124, 96, 18,
                Component.translatable("ae2lt.gui.button.disconnect"),
                btn -> NetworkInit.sendToServer(
                        new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, ""))));
    }

    // Tab: Selection

    private void initSelectionTab(int x0, int y0) {
        // Reset pagination when the search changes.
        int searchX = x0 + (imageWidth - INPUT_MAX_WIDTH) / 2;
        AETextField searchField = makeAe2Field(searchX, y0 + 124, INPUT_MAX_WIDTH, INPUT_HEIGHT);
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
        selectionScroll = clampScroll(selectionScroll, total, ITEMS_PER_PAGE_SEARCH);

        currentScrollbar = new ScrollbarWidget(
                x0 + SCROLLBAR_X, y0 + SCROLLBAR_Y,
                ITEMS_PER_PAGE_SEARCH * LIST_ROW_HEIGHT,
                total, ITEMS_PER_PAGE_SEARCH,
                selectionScroll,
                offset -> { selectionScroll = offset; rebuildSelectionRows(); });
        addRenderableWidget(currentScrollbar);

        rebuildSelectionRows();
        // NOTE: no extra "Disconnect" button here — the Home tab already
        // owns the disconnect action. Duplicating it across tabs gives the
        // impression of the same widget being created twice.
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
        int end = Math.min(start + ITEMS_PER_PAGE_SEARCH, freqs.size());

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
            // Paint the frequency's stored RGB colour into the row label —
            // this is the colour the user picked in Create/Setting and is
            // the primary way to distinguish one frequency from another at
            // a glance. Without it every row shows up in AE2Button's
            // default near-white colour.
            Component display = Component.literal(label)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(f.color() & 0xFFFFFF)));
            RowSpriteButton btn = new RowSpriteButton(
                    x0 + LIST_ROW_X, by, LIST_ROW_WIDTH, LIST_ROW_BUTTON_HEIGHT,
                    display,
                    b -> {
                        // ENCRYPTED rows that the player can't unlock pop
                        // the password modal instead of firing a guaranteed-
                        // to-fail select — the modal round-trip handles
                        // authentication and the server's enrollAsUser then
                        // replays the rebuild with the player as a new USER
                        // member.
                        if (needsPasswordUnlock(f)) {
                            passwordPromptFreqId = f.id();
                            passwordPromptFreqName = f.name();
                            passwordPromptLocksScreen = false;
                            scheduleRebuild();
                        } else {
                            NetworkInit.sendToServer(
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

    // Tab: Connections

    private void initConnectionsTab(int x0, int y0) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) return;

        int total = ClientFrequencyCache.getConnections(currentId).size();
        connectionScroll = clampScroll(connectionScroll, total, ITEMS_PER_PAGE_LIST);

        currentScrollbar = new ScrollbarWidget(
                x0 + SCROLLBAR_X, y0 + SCROLLBAR_Y,
                ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT,
                total, ITEMS_PER_PAGE_LIST,
                connectionScroll,
                offset -> connectionScroll = offset);
        addRenderableWidget(currentScrollbar);
        // Connection rows are drawn text-only in renderConnectionLabels,
        // so there are no row buttons to rebuild — the next frame's
        // renderLabels will read connectionScroll directly.
    }

    // Tab: Members

    private void initMembersTab(int x0, int y0) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) return;

        int total = collectMemberRows().size();
        memberScroll = clampScroll(memberScroll, total, ITEMS_PER_PAGE_LIST);

        currentScrollbar = new ScrollbarWidget(
                x0 + SCROLLBAR_X, y0 + SCROLLBAR_Y,
                ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT,
                total, ITEMS_PER_PAGE_LIST,
                memberScroll,
                offset -> { memberScroll = offset; rebuildMemberRows(); });
        addRenderableWidget(currentScrollbar);

        rebuildMemberRows();
    }

    /** Members in server order followed by online non-members; the flag selects the row action. */
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
                // Use one styled literal to keep AE2Button scrolling text aligned.
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

    /** Simple holder for an online-but-not-a-member row in the Members tab. */
    private record StrangerRow(UUID uuid, String name) {}

    // Member edit popup

    private void openMemberPopup(UUID uuid, String name, FrequencyAccessLevel access) {
        popupMemberUUID = uuid;
        popupMemberName = name;
        popupMemberAccess = access;
        popupIsStranger = false;
        // Same deferral rationale as {@link #scheduleRebuild} — the
        // click that opens the popup is still unwinding when this runs.
        Minecraft.getInstance().execute(this::rebuildMemberPopupWidgets);
    }

    /** Offers only Set as User for a non-member, using MEMBERSHIP_SET_USER to enroll them. */
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
        buildTopTabs(x0, y0, true);

        int px = x0 + 20;
        int py = y0 + 40;
        int pw = 136;

        FrequencyAccessLevel myAccess = selfAccess();
        FrequencyAccessLevel targetLevel = popupIsStranger
                ? FrequencyAccessLevel.BLOCKED
                : popupMemberAccess;
        boolean targetIsOwner = targetLevel == FrequencyAccessLevel.OWNER;
        // Prevent owners from removing or demoting themselves.
        boolean isSelfTarget = !popupIsStranger && isSelf(popupMemberUUID);
        boolean ownerActingOnSelf = targetIsOwner && isSelfTarget;

        // Check the higher of the current and requested ranks for both promotions and demotions.
        boolean canUser = !ownerActingOnSelf
                && targetLevel != FrequencyAccessLevel.USER
                && myAccess.canActOnLevel(FrequencyAccessLevel.higher(targetLevel, FrequencyAccessLevel.USER));
        AE2Button btnUser = new AE2Button(px, py, pw, 16,
                Component.translatable("ae2lt.gui.member.set_user"),
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_SET_USER));
        btnUser.active = canUser;
        addRenderableWidget(btnUser);

        boolean canAdmin = !ownerActingOnSelf
                && targetLevel != FrequencyAccessLevel.ADMIN
                && myAccess.canActOnLevel(FrequencyAccessLevel.higher(targetLevel, FrequencyAccessLevel.ADMIN));
        AE2Button btnAdmin = new AE2Button(px, py + 20, pw, 16,
                Component.translatable("ae2lt.gui.member.set_admin"),
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_SET_ADMIN));
        btnAdmin.active = canAdmin;
        addRenderableWidget(btnAdmin);

        // Remove is the one button that stays off for strangers —
        // there's no durable membership entry to cancel.
        boolean canRemove = !ownerActingOnSelf
                && !popupIsStranger
                && myAccess.canActOnLevel(targetLevel);
        Component removeLabel = Component.translatable("ae2lt.gui.member.remove");
        if (canRemove) {
            removeLabel = removeLabel.copy().withStyle(ChatFormatting.DARK_RED);
        }
        AE2Button btnRemove = new AE2Button(px, py + 40, pw, 16,
                removeLabel,
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_CANCEL));
        btnRemove.active = canRemove;
        addRenderableWidget(btnRemove);

        // Renamed from "Transfer Ownership" now that we're multi-owner:
        // this promotes the target to OWNER without demoting existing
        // ones. Requires OWNER-rank reach (same-rank-by-owner rule).
        // The gold tint only lights up when the button is clickable.
        boolean canSetOwner = !targetIsOwner
                && myAccess.canActOnLevel(FrequencyAccessLevel.OWNER);
        Component setOwnerLabel = Component.translatable("ae2lt.gui.member.set_owner");
        if (canSetOwner) {
            setOwnerLabel = setOwnerLabel.copy().withStyle(ChatFormatting.GOLD);
        }
        AE2Button btnSetOwner = new AE2Button(px, py + 60, pw, 16,
                setOwnerLabel,
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP));
        btnSetOwner.active = canSetOwner;
        addRenderableWidget(btnSetOwner);

        addRenderableWidget(new AE2Button(px + 36, py + 84, 64, 16,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> { closePopup(); scheduleRebuild(); }));
    }

    private void sendMember(byte type) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0 || popupMemberUUID == null) return;
        NetworkInit.sendToServer(new ChangeMemberPacket(token(), currentId, popupMemberUUID, type));
        closePopup();
        scheduleRebuild();
    }

    // Tab: Create

    private void initCreateTab(int x0, int y0) {
        int fieldX = x0 + (imageWidth - INPUT_MAX_WIDTH) / 2;
        nameField = makeAe2Field(fieldX, y0 + 30, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        nameField.setMaxLength(WirelessFrequency.MAX_NAME_LENGTH);
        nameField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.name"));
        addRenderableWidget(nameField);

        passwordField = makeAe2Field(fieldX, y0 + 68, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        passwordField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.password"));
        passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
        addRenderableWidget(passwordField);

        // Security selector — right-aligned with the security label so
        // the button sits to the right of the "安全等级:" caption rather
        // than centred on the 195-px chassis.
        addRenderableWidget(new AE2Button(
                x0 + 80, y0 + 50, 96, 14,
                getSecurityLabel(editSecurity),
                btn -> {
                    editSecurity = FrequencySecurityLevel.VALUES[
                            (editSecurity.ordinal() + 1) % FrequencySecurityLevel.VALUES.length];
                    passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
                    btn.setMessage(getSecurityLabel(editSecurity));
                }));

        // Colour swatches — 7×2 grid of 14-px cells centred on the
        // chassis. Total width = 7*16 = 112 px → x_start = (195-112)/2 = 42.
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int c = PRESET_COLORS[i];
            int cx = x0 + 42 + (i % 7) * 16;
            int cy = y0 + 90 + (i / 7) * 16;
            addRenderableWidget(Button.builder(Component.literal(" "),
                            btn -> editColor = c)
                    .bounds(cx, cy, 14, 14).build());
        }

        addRenderableWidget(new AE2Button(
                x0 + (GUI_WIDTH - 60) / 2, y0 + 138, 60, 16,
                Component.translatable("ae2lt.gui.button.create"),
                btn -> {
                    if (nameField.getValue().isBlank()) return;
                    NetworkInit.sendToServer(new CreateFrequencyPacket(
                            token(),
                            nameField.getValue(), editColor, editSecurity,
                            passwordField.getValue()));
                    switchTab(FrequencyNavigationTab.TAB_SELECTION);
                }));
    }

    // Tab: Settings

    private void initSettingsTab(int x0, int y0) {
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        if (freq == null) return;

        // Per-access-level layout: OWNERs see Delete+Apply, ADMINs see
        // Leave+Apply (they can edit but not destroy the frequency),
        // USERs see Leave only and all inputs render view-only. The
        // server-side self-leave branch (MEMBERSHIP_CANCEL) is how
        // non-editors bail out of a frequency they joined via
        // enrollAsUser or an admin-added USER slot.
        boolean isManager = hasManagerAccess();
        boolean isOwner = hasOwnerAccess();

        editSecurity = freq.security();
        editColor = freq.color();

        int fieldX = x0 + (imageWidth - INPUT_MAX_WIDTH) / 2;
        nameField = makeAe2Field(fieldX, y0 + 30, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        nameField.setMaxLength(WirelessFrequency.MAX_NAME_LENGTH);
        nameField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.name"));
        nameField.setValue(freq.name());
        nameField.setEditable(isManager);
        addRenderableWidget(nameField);

        // Settings tab uses the pristine-clearing subclass: when the
        // frequency is already ENCRYPTED we pre-fill the sentinel dots
        // so the user sees "a password is set here" without us leaking
        // the hash, and any keystroke wipes the sentinel so typed input
        // replaces it rather than appending. See
        // {@link #PASSWORD_SENTINEL}.
        passwordField = new PristinePasswordField(fieldX, y0 + 68, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        passwordField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.password"));
        passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
        if (editSecurity == FrequencySecurityLevel.ENCRYPTED) {
            passwordField.setValue(PASSWORD_SENTINEL);
            settingsPasswordPristine = true;
        } else {
            settingsPasswordPristine = false;
        }
        // Password and security are OWNER-gated on the server
        // (EditFrequencyPacket bounces ADMIN with NO_PERMISSION when
        // either changes). Mirror that here so ADMIN doesn't hit a
        // "silent" Apply-failure by cycling the security button —
        // only isOwner gets to flip these.
        passwordField.setEditable(isOwner);
        addRenderableWidget(passwordField);

        AE2Button securityBtn = new AE2Button(
                x0 + 80, y0 + 50, 96, 14,
                getSecurityLabel(editSecurity),
                btn -> {
                    editSecurity = FrequencySecurityLevel.VALUES[
                            (editSecurity.ordinal() + 1) % FrequencySecurityLevel.VALUES.length];
                    passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
                    // Cycling the security mode restarts password entry —
                    // the sentinel only makes sense on the very first
                    // render for an already-ENCRYPTED frequency, never
                    // after the user touches the security toggle.
                    passwordField.setValue("");
                    settingsPasswordPristine = false;
                    btn.setMessage(getSecurityLabel(editSecurity));
                });
        securityBtn.active = isOwner;
        addRenderableWidget(securityBtn);

        // Apply / Delete-or-Leave action row, centred on the chassis as
        // a 60+12+60 button pair (132 px → x_start=(195-132)/2=31).
        AE2Button applyBtn = new AE2Button(
                x0 + 103, y0 + 138, 60, 16,
                Component.translatable("ae2lt.gui.button.apply"),
                btn -> {
                    // If the sentinel is still in the field, the user
                    // didn't touch the password — forward an empty
                    // string so the server preserves the stored hash
                    // (see EditFrequencyPacket's "only overwrite when
                    // the payload is non-empty" branch).
                    String pw = settingsPasswordPristine ? "" : passwordField.getValue();
                    NetworkInit.sendToServer(new EditFrequencyPacket(
                            token(),
                            freq.id(), nameField.getValue(), editColor,
                            editSecurity, pw));
                });
        applyBtn.active = isManager;
        addRenderableWidget(applyBtn);

        if (isOwner) {
            addRenderableWidget(new AE2Button(
                    x0 + 31, y0 + 138, 60, 16,
                    Component.translatable("ae2lt.gui.button.delete")
                            .copy().withStyle(ChatFormatting.DARK_RED),
                    btn -> { deleteConfirmOpen = true; scheduleRebuild(); }));
        } else {
            // Disconnect before leaving, while the player still has access to the frequency.
            addRenderableWidget(new AE2Button(
                    x0 + 31, y0 + 138, 60, 16,
                    Component.translatable("ae2lt.gui.button.leave")
                            .copy().withStyle(ChatFormatting.DARK_GRAY),
                    btn -> {
                        var mc = Minecraft.getInstance();
                        if (mc.player == null) return;
                        NetworkInit.sendToServer(new SelectFrequencyPacket(
                                token(), freqMenu().getBlockPos(), -1, ""));
                        NetworkInit.sendToServer(new ChangeMemberPacket(
                                token(), freq.id(), mc.player.getUUID(),
                                WirelessFrequency.MEMBERSHIP_CANCEL));
                        switchTab(FrequencyNavigationTab.TAB_SELECTION);
                    }));
        }
    }

    // Rendering

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(
                backgroundTextureForTab(currentTab),
                leftPos,
                topPos,
                0, 0,
                GUI_WIDTH,
                GUI_HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE);

        // Member-edit popup panel background. This MUST be drawn from
        // {@link #renderBg} (before widgets render) — if drawn from
        // {@link #renderLabels} it overlays the popup buttons and hides
        // them. The corresponding text (member name / access short code)
        // is rendered later in {@link #renderLabels}, which is fine
        // because that text sits above the buttons rather than overlapping.
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

    /** Chooses the chassis with the row count or form layout required by the tab. */
    private static ResourceLocation backgroundTextureForTab(FrequencyNavigationTab tab) {
        return switch (tab) {
            case TAB_HOME -> BG_HOME;
            case TAB_SELECTION -> BG_SELECTION;
            case TAB_CONNECTION, TAB_MEMBER -> BG_LIST;
            case TAB_CREATE, TAB_SETTING -> BG_FORM;
        };
    }

    /** Draws the password modal background beneath its text and controls. */
    private void drawPasswordPromptPanel(GuiGraphics g) {
        int px0 = leftPos + 16;
        int py0 = topPos + 50;
        int pw = 144;
        int ph = 66;
        g.fill(px0, py0, px0 + pw, py0 + ph, 0xFFCBCCD4);             // panel fill
        g.fill(px0, py0, px0 + pw, py0 + 1, 0xFF5A5A6B);               // top shadow
        g.fill(px0, py0, px0 + 1, py0 + ph, 0xFF5A5A6B);               // left shadow
        g.fill(px0 + pw - 1, py0, px0 + pw, py0 + ph, 0xFFFFFFFF);     // right highlight
        g.fill(px0, py0 + ph - 1, px0 + pw, py0 + ph, 0xFFFFFFFF);     // bottom highlight
    }

    /** Draws the delete-confirmation background beneath its text and controls. */
    private void drawDeleteConfirmPanel(GuiGraphics g) {
        int px0 = leftPos + 16;
        int py0 = topPos + 50;
        int pw = 144;
        int ph = 66;
        g.fill(px0, py0, px0 + pw, py0 + ph, 0xFFCBCCD4);             // panel fill
        g.fill(px0, py0, px0 + pw, py0 + 1, 0xFF5A5A6B);               // top shadow
        g.fill(px0, py0, px0 + 1, py0 + ph, 0xFF5A5A6B);               // left shadow
        g.fill(px0 + pw - 1, py0, px0 + pw, py0 + ph, 0xFFFFFFFF);     // right highlight
        g.fill(px0, py0 + ph - 1, px0 + pw, py0 + ph, 0xFFFFFFFF);     // bottom highlight
    }

    /** Draws the member editor background beneath its text and controls. */
    private void drawMemberPopupPanel(GuiGraphics g) {
        int px0 = leftPos + 10;
        int py0 = topPos + 26;
        int pw = 156;
        // 120 rather than 110 so the Cancel button (local y=124..140)
        // sits inside the panel — at 110 it poked 4 px below the
        // bottom bevel.
        int ph = 120;
        g.fill(px0, py0, px0 + pw, py0 + ph, 0xFFCBCCD4);             // panel fill
        g.fill(px0, py0, px0 + pw, py0 + 1, 0xFF5A5A6B);               // top shadow
        g.fill(px0, py0, px0 + 1, py0 + ph, 0xFF5A5A6B);               // left shadow
        g.fill(px0 + pw - 1, py0, px0 + pw, py0 + ph, 0xFFFFFFFF);     // right highlight
        g.fill(px0, py0 + ph - 1, px0 + pw, py0 + ph, 0xFFFFFFFF);     // bottom highlight
    }

    /** Shared tab X coordinate for widgets and background rendering; Create is right-aligned. */
    private static int tabButtonX(int originX, FrequencyNavigationTab tab, int index) {
        if (tab == FrequencyNavigationTab.TAB_CREATE) {
            return originX + GUI_WIDTH - TAB_WIDTH - 2;
        }
        return originX + 8 + index * TAB_STEP;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        fittedTextTooltips.clear();

        drawFlatCentered(g,
                Component.translatable(currentTab.getTranslationKey()),
                imageWidth / 2, 6, AE2_TEXT_TITLE);

        // When the member-edit popup or delete-confirm modal is open it
        // covers the tab body — rendering the tab's labels underneath
        // leaks them through around the panel edges and creates a
        // cluttered look. Suppress the body labels while a modal is
        // active; the modal owns the visible surface until the user
        // cancels or commits.
        if (popupMemberUUID == null && !deleteConfirmOpen && passwordPromptFreqId == 0) {
            switch (currentTab) {
                case TAB_HOME -> renderHomeLabels(g);
                case TAB_SELECTION -> renderSelectionLabels(g);
                case TAB_CONNECTION -> renderConnectionLabels(g);
                case TAB_MEMBER -> renderMemberLabels(g);
                case TAB_CREATE -> renderCreateLabels(g);
                case TAB_SETTING -> renderSettingLabels(g);
            }
        }

        if (deleteConfirmOpen) {
            // Delete-confirm modal contents. Panel sits at local y=50..116
            // (from {@link #drawDeleteConfirmPanel}); title + hint go in
            // the 42 px head area above the Confirm/Cancel row (y=92..108).
            drawFlatCentered(g,
                    Component.translatable("ae2lt.gui.delete_confirm.title")
                            .copy().withStyle(ChatFormatting.DARK_RED),
                    imageWidth / 2, 60, AE2_TEXT_TITLE);
            drawFlatCentered(g,
                    Component.translatable("ae2lt.gui.delete_confirm.hint"),
                    imageWidth / 2, 74, AE2_TEXT_MUTED);
        }

        if (passwordPromptFreqId > 0) {
            // Password prompt modal contents. Panel sits at local y=50..116
            // (same footprint as the delete modal); header above the
            // text field at y=68 and Cancel/Submit at y=92.
            drawFlatCenteredFitted(g,
                    Component.translatable("ae2lt.gui.password_prompt.title", passwordPromptFreqName),
                    imageWidth / 2, 58, 136, AE2_TEXT_TITLE);
        }

        if (popupMemberUUID != null) {
            Component header;
            if (popupIsStranger) {
                String code = Component.translatable("ae2lt.gui.member.stranger_short").getString();
                header = Component.literal(popupMemberName + " [" + code + "]")
                        .withStyle(ChatFormatting.DARK_GRAY);
            } else {
                header = Component.literal(popupMemberName)
                        .withStyle(popupMemberAccess.getFormatting())
                        .copy()
                        .append(Component.literal(" [" + accessLabel(popupMemberAccess) + "]")
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            drawFlatCenteredFitted(g, header, imageWidth / 2, 30, 148, AE2_TEXT_TITLE);
        }

        // Inline error toast (server-pushed). Painted last so it sits
        // on top of every modal + tab body, near the bottom edge of
        // the panel where it doesn't collide with the primary
        // content. A dark translucent bar behind the text keeps it
        // legible on top of the recessed shelf + list rows.
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
                int bgY = imageHeight - 14;
                g.fill(bgX, bgY, bgX + bgW, bgY + 12, 0xD0202028);
                g.fill(bgX, bgY, bgX + bgW, bgY + 1, 0xFFD04848);   // top accent
                drawFlatCentered(g, display, imageWidth / 2, bgY + 2, 0xFFFFB4B4);
                if (!full.equals(fitted)) {
                    addFittedTooltip(bgX, bgY, bgW, 12, inlineError);
                }
            } else {
                inlineError = null;
            }
        }
    }

    /** Draws panel text without a drop shadow to match AE2 labels. */
    private void drawFlat(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    private void drawFlat(GuiGraphics g, String text, int x, int y, int color) {
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
        // Five info lines sit inside the wide info shelf baked into
        // wireless_overloaded_home.png (shelf y=39..111). 14-px row pitch
        // gives clean spacing between glyphs and avoids touching the
        // shelf bevel at top/bottom.
        int y = 43;
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        if (freq != null) {
            // Frequency name in its stored RGB colour — the colour the
            // user picked in Create / Settings. Earlier versions also
            // appended " (#<id>)" — the numeric id is internal data and
            // adds noise to a player-facing line, so we drop it here.
            Component name = Component.literal(freq.name())
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(freq.color() & 0xFFFFFF)));
            Component line = Component.translatable("ae2lt.gui.frequency.current")
                    .append(": ").append(name);
            drawFlatFitted(g, line, 10, y, imageWidth - 20, AE2_TEXT_BODY);
        } else {
            drawFlatFitted(g, Component.translatable("ae2lt.gui.frequency.none"),
                    10, y, imageWidth - 20, AE2_TEXT_MUTED);
        }
        y += 14;

        drawFlatFitted(g, Component.translatable("ae2lt.gui.home.device_type")
                .append(": ").append(Component.translatable(freqMenu().getDeviceName())),
                10, y, imageWidth - 20, AE2_TEXT_BODY);
        y += 14;

        boolean connected = freqMenu().isLinkActive();
        drawFlatFitted(g, Component.translatable("ae2lt.gui.home.status")
                        .append(": ")
                        .append(connected
                                ? Component.translatable("ae2lt.gui.home.connected")
                                : Component.translatable("ae2lt.gui.home.disconnected")),
                10, y, imageWidth - 20, AE2_TEXT_BODY);
        y += 14;

        int used = freqMenu().getUsedChannels();
        int max = freqMenu().getMaxChannels();
        Component channelsValue;
        if (max < 0) {
            channelsValue = Component.translatable("ae2lt.gui.home.grid_channels.infinite", used);
        } else if (max == 0) {
            channelsValue = Component.translatable("ae2lt.gui.home.grid_channels.value", used, 0, 0);
        } else {
            int remain = Math.max(0, max - used);
            channelsValue = Component.translatable("ae2lt.gui.home.grid_channels.value", used, max, remain);
        }
        drawFlatFitted(g, Component.translatable("ae2lt.gui.home.grid_channels")
                        .append(": ")
                        .append(channelsValue),
                10, y, imageWidth - 20, AE2_TEXT_BODY);
        y += 14;

        if (freqMenu().isController()) {
            drawFlatFitted(g, Component.translatable("ae2lt.gui.home.cross_dimension")
                    .append(": ").append(freqMenu().isAdvanced()
                            ? Component.translatable("ae2lt.gui.home.cross_dimension.yes")
                            : Component.translatable("ae2lt.gui.home.cross_dimension.no")),
                    10, y, imageWidth - 20, AE2_TEXT_BODY);
        }
    }

    private void renderSelectionLabels(GuiGraphics g) {
        // Single subtitle row between title (y=6) and shelf (y=28).
        // Text at y=18 renders glyphs on y=18..26, keeping a 2-px gap
        // above the shelf and well clear of the title descenders — the
        // two-row layout used previously overlapped the title at y=12.
        var freqs = ClientFrequencyCache.getAllFrequenciesSorted();
        var current = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());

        Component left = Component.translatable("ae2lt.gui.frequency.current")
                .append(": ")
                .append(current != null
                        ? Component.literal(current.name()).setStyle(
                                Style.EMPTY.withColor(TextColor.fromRgb(current.color() & 0xFFFFFF)))
                        : Component.translatable("ae2lt.gui.frequency.none").withStyle(ChatFormatting.DARK_GRAY));
        Component right = Component.translatable("ae2lt.gui.frequency.total").append(": " + freqs.size());
        int rightX = imageWidth - 10 - font.width(right);
        drawFlatFitted(g, left, 10, 18, Math.max(20, rightX - 24), AE2_TEXT_BODY);
        drawFlat(g, right, rightX, 18, AE2_TEXT_MUTED);
        // No "password:" label here — the AETextField placeholder
        // communicates that already, and keeping the label out of the
        // shelf's bottom bevel avoids the prior clipping/overlap.
    }

    private void renderConnectionLabels(GuiGraphics g) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.error.no_frequency"),
                    imageWidth / 2, 40, AE2_TEXT_MUTED);
            return;
        }

        var conns = ClientFrequencyCache.getConnections(currentId);
        Component right = Component.translatable("ae2lt.gui.frequency.total")
                .append(": " + conns.size());
        drawFlat(g, right, imageWidth - 10 - font.width(right), 18, AE2_TEXT_MUTED);

        if (conns.isEmpty()) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.connection.none"),
                    imageWidth / 2, 70, AE2_TEXT_MUTED);
            return;
        }

        int start = connectionScroll;
        int end = Math.min(start + ITEMS_PER_PAGE_LIST, conns.size());
        for (int i = start; i < end; i++) {
            var c = conns.get(i);
            int row = i - start;
            // Two-line layout per 21-px well row: type on top, coords below.
            // Row 0 well content area is y=38..57; centring two 9-px text
            // lines puts the type at y=39 and the coords at y=49.
            int yTop = LIST_ROW_FIRST_Y + 1 + row * LIST_ROW_HEIGHT;
            int yBot = yTop + 10;

            String typeKey = c.deviceName();
            ChatFormatting typeColor = c.controller() ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_GREEN;

            drawFlatFitted(g, Component.translatable(typeKey).withStyle(typeColor),
                    LIST_ROW_X, yTop, 145, AE2_TEXT_BODY);

            String posStr = "(" + c.pos().getX() + "," + c.pos().getY() + "," + c.pos().getZ() + ")";

            String dim = c.dimension();
            int slash = dim.indexOf(':');
            String shortDim = slash >= 0 ? dim.substring(slash + 1) : dim;
            if (shortDim.length() > 8) shortDim = shortDim.substring(0, 8);
            int dimX = 168 - font.width(shortDim);
            drawFlatFitted(g, posStr, LIST_ROW_X, yBot,
                    Math.max(20, dimX - LIST_ROW_X - 6), AE2_TEXT_MUTED);

            int dotColor = c.loaded() ? 0xFF3AA55A : 0xFF7F7F7F;
            // Centre the 6-px loaded/unloaded dot vertically between the
            // two text lines so it reads as a row-level status badge
            // rather than a glyph clinging to either line.
            int dotY = yTop + 5;
            g.fill(160, dotY, 166, dotY + 6, dotColor);

            // Right-align inside the well (right edge at x=168) rather
            // than against the chassis edge so it doesn't poke past
            // the recessed shelf.
            drawFlat(g, shortDim, dimX, yBot, AE2_TEXT_MUTED);
        }
    }

    private void renderMemberLabels(GuiGraphics g) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.error.no_frequency"),
                    imageWidth / 2, 40, AE2_TEXT_MUTED);
            return;
        }

        // Subtitle row — access on the left, count on the right, both at y=18.
        FrequencyAccessLevel myAccess = selfAccess();
        Component left = Component.translatable("ae2lt.gui.member.your_access",
                Component.translatable("ae2lt.gui.access." + myAccess.name().toLowerCase())
                        .withStyle(myAccess.getFormatting()));
        var members = ClientFrequencyCache.getMembers(currentId);
        Component right = Component.translatable("ae2lt.gui.frequency.total")
                .append(": " + members.size());
        int rightX = imageWidth - 10 - font.width(right);
        drawFlatFitted(g, left, 10, 18, Math.max(20, rightX - 24), AE2_TEXT_BODY);
        drawFlat(g, right, rightX, 18, AE2_TEXT_MUTED);

        if (members.isEmpty()) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.member.none"),
                    imageWidth / 2, 70, AE2_TEXT_MUTED);
        }
    }

    private void renderCreateLabels(GuiGraphics g) {
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.name").append(":"),
                16, 22, AE2_TEXT_MUTED);
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.security").append(":"),
                16, 52, AE2_TEXT_MUTED);
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.color").append(":"),
                16, 82, AE2_TEXT_MUTED);

        // renderLabels already uses coordinates relative to the GUI.
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int cx = 42 + (i % 7) * 16;
            int cy = 90 + (i / 7) * 16;
            int fill = PRESET_COLORS[i] | 0xFF000000;
            g.fill(cx + 1, cy + 1, cx + 13, cy + 13, fill);
            if (PRESET_COLORS[i] == editColor) {
                // Highlight the selected swatch with a 1-px white frame
                // so the user can see which colour they've picked.
                int accent = 0xFFFFFFFF;
                g.fill(cx,       cy,       cx + 14, cy + 1,  accent); // top
                g.fill(cx,       cy + 13,  cx + 14, cy + 14, accent); // bottom
                g.fill(cx,       cy,       cx + 1,  cy + 14, accent); // left
                g.fill(cx + 13,  cy,       cx + 14, cy + 14, accent); // right
            }
        }

        g.fill(16, 124, 28, 134, editColor | 0xFF000000);
        if (nameField != null && !nameField.getValue().isBlank()) {
            drawFlatFitted(g, nameField.getValue(), 32, 125,
                    imageWidth - 42, editColor | 0xFF000000);
        }
    }

    private void renderSettingLabels(GuiGraphics g) {
        if (ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId()) == null) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.error.no_frequency"),
                    imageWidth / 2, 40, AE2_TEXT_MUTED);
            return;
        }
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.name").append(":"),
                16, 22, AE2_TEXT_MUTED);
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.security").append(":"),
                16, 52, AE2_TEXT_MUTED);
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
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderFittedTextTooltip(g, mouseX, mouseY);
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
        // fall back to frequency security: PUBLIC → USER, else BLOCKED
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

    /**
     * Uses idle/hover row sprites and preserves Component color styles. Disabled rows
     * keep the idle sprite and their access-tinted labels.
     */
    private final class RowSpriteButton extends Button {
        RowSpriteButton(int x, int y, int width, int height,
                Component message, OnPress onPress) {
            super(Button.builder(message, onPress).bounds(x, y, width, height));
            if (!message.getString().equals(FittingText.fit(message.getString(), width - 8, font::width))) {
                setTooltip(Tooltip.create(message));
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = active && (isHoveredOrFocused());
            int srcV = hover ? ROW_SPRITE_HOVER_V : ROW_SPRITE_IDLE_V;
            // BG_LIST and BG_SELECTION carry an identical sprite library
            // below the chassis, so sourcing from BG_LIST works for every
            // tab that uses this widget regardless of which chassis is
            // currently bound for the panel itself.
            g.blit(BG_LIST,
                    getX(), getY(),
                    0, srcV,
                    ROW_SPRITE_WIDTH, ROW_SPRITE_HEIGHT,
                    TEXTURE_SIZE, TEXTURE_SIZE);

            // Vanilla MC's drawString honours the Component's per-style
            // colour, so the access tint / frequency RGB baked into
            // {@code getMessage()} flows through. The fallback colour
            // is only used for unstyled glyphs — pick black on the
            // light idle sprite, dark blue-gray on the disabled sprite.
            int fallback = active ? 0x000000 : 0x404040;
            String full = getMessage().getString();
            String fitted = FittingText.fit(full, getWidth() - 8, font::width);
            Component display = full.equals(fitted)
                    ? getMessage()
                    : Component.literal(fitted).setStyle(getMessage().getStyle());
            int textY = getY() + (getHeight() - 8) / 2;
            int textX = getX() + 4 + (getWidth() - 8 - font.width(display)) / 2;
            g.drawString(font, display, textX, textY, fallback, false);
        }
    }

    /**
     * Supports track paging, dragging and wheel scrolling. Offset changes rebuild only
     * rows, preserving this widget's drag state.
     */
    private final class ScrollbarWidget extends AbstractWidget {
        private final int totalItems;
        private final int visibleItems;
        private final java.util.function.IntConsumer onScroll;
        private int scrollOffset;
        private boolean dragging;
        private int dragYOffset;

        ScrollbarWidget(int x, int y, int trackHeight,
                int totalItems, int visibleItems,
                int initialScroll,
                java.util.function.IntConsumer onScroll) {
            super(x, y, SCROLLBAR_WIDTH, trackHeight, Component.empty());
            this.totalItems = totalItems;
            this.visibleItems = visibleItems;
            this.onScroll = onScroll;
            this.scrollOffset = clamp(initialScroll);
        }

        int maxOffset() {
            return Math.max(0, totalItems - visibleItems);
        }

        private int clamp(int v) {
            return Math.max(0, Math.min(maxOffset(), v));
        }

        private void setOffset(int v) {
            int c = clamp(v);
            if (c != scrollOffset) {
                scrollOffset = c;
                onScroll.accept(c);
            }
        }

        /**
         * Wheel hook called from the screen-level {@link FrequencyScreen#mouseScrolled}.
         * One row per notch — vertical sign matches vanilla (positive
         * scrollY = wheel up = scroll towards top).
         */
        boolean handleScroll(double scrollY) {
            if (maxOffset() == 0) return false;
            int delta = -(int) Math.signum(scrollY);
            if (delta == 0) return false;
            int before = scrollOffset;
            setOffset(scrollOffset + delta);
            return scrollOffset != before;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean enabled = maxOffset() > 0;
            // 1.20.1 has no GuiGraphics.blitSprite: AE2 1.20.1 draws its scrollbar
            // handles from the vanilla creative-tabs texture (enabled at 232,0,
            // disabled at 244,0, each 12x15).
            ResourceLocation sprite =
                    new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");
            int srcX = enabled ? 232 : 244;
            int availH = Math.max(0, getHeight() - SCROLLBAR_HANDLE_HEIGHT);
            int handleY = enabled
                    ? getY() + scrollOffset * availH / maxOffset()
                    : getY();
            g.blit(sprite, getX(), handleY, srcX, 0, SCROLLBAR_WIDTH, SCROLLBAR_HANDLE_HEIGHT, 256, 256);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !visible || !active) return false;
            if (!isMouseOver(mouseX, mouseY)) return false;
            if (maxOffset() == 0) return true;
            int availH = Math.max(0, getHeight() - SCROLLBAR_HANDLE_HEIGHT);
            int handleY = getY() + scrollOffset * availH / maxOffset();
            if (mouseY < handleY) {
                setOffset(scrollOffset - visibleItems);
            } else if (mouseY < handleY + SCROLLBAR_HANDLE_HEIGHT) {
                dragging = true;
                dragYOffset = (int) (mouseY - handleY);
            } else {
                setOffset(scrollOffset + visibleItems);
            }
            setFocused(true);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) dragging = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dx, double dy) {
            if (!dragging || maxOffset() == 0) return;
            int availH = getHeight() - SCROLLBAR_HANDLE_HEIGHT;
            if (availH <= 0) return;
            double rel = (mouseY - getY() - dragYOffset) / availH;
            rel = Math.max(0, Math.min(1, rel));
            setOffset((int) Math.round(rel * maxOffset()));
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return handleScroll(delta);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Forward wheel events anywhere over the GUI to the active list-tab
        // scrollbar so the user doesn't have to aim at the 12-px gutter —
        // spinning the wheel while hovering the row buttons or panel chrome
        // still scrolls the list.
        if (currentScrollbar != null && currentScrollbar.handleScroll(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

}
