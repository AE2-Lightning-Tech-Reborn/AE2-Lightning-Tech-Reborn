package com.moakiee.ae2lt.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.Point;
import appeng.client.gui.style.StyleManager;
import appeng.core.network.serverbound.SwitchGuisPacket;

import com.moakiee.ae2lt.client.frequency.ClientFrequencyCache;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.FrequencyPage;
import com.moakiee.ae2lt.client.frequency.ManagementViewCache;
import appeng.client.gui.style.BackgroundGenerator;
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
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Compact frequency subpages sharing AE2 navigation with device and power management.
 * Polls {@link ClientFrequencyCache#revision()} each frame and rebuilds
 * widgets when the server-pushed state changes.
 */
public class FrequencyScreen extends AbstractContainerScreen<FrequencyMenu> {

    private static final int GUI_WIDTH = FrequencyNavigation.WIDTH;
    private static final int GUI_HEIGHT = FrequencyNavigation.HEIGHT;
    private static final int ITEMS_PER_PAGE_LIST = 5;
    private static final int ITEMS_PER_PAGE_SEARCH = 5;
    private static final int LIST_ROW_HEIGHT = 25;
    private static final int LIST_ROW_BUTTON_HEIGHT = 24;
    private static final int LIST_ROW_FIRST_Y = 49;
    private static final int LIST_ROW_X = 8;
    private static final int LIST_ROW_WIDTH = GUI_WIDTH - 34;
    private static final int SCROLLBAR_X = GUI_WIDTH - 20;
    private static final int SCROLLBAR_Y = LIST_ROW_FIRST_Y;
    private static final int SCROLLBAR_WIDTH = 12;

    // AE2 standard text tones — matches AE2's own screens such as
    // terminal.png / craftingcpu.png where titles read as dark charcoal
    // on the light-lavender panel.
    // Default text is plain black on the lavender chassis. Hierarchy
    // comes from {@link ChatFormatting} colours applied where they
    // carry information (status / access / channel pressure / etc.) —
    // labels that don't need a tint stay black.
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

    private static final int INPUT_MAX_WIDTH = GUI_WIDTH - 24;
    private static final int FOOTER_Y = GUI_HEIGHT - 20;
    private static final int FOOTER_BUTTON_WIDTH = (GUI_WIDTH - 24) / 3;

    private final ScreenStyle ae2Style = StyleManager.loadStyleDoc("/screens/common/common.json");
    private ToggleButton channelAutoButton, energyAutoButton;

    private FrequencyPage currentTab = FrequencyPage.TAB_HOME;
    /**
     * Top row index currently scrolled to within the Selection / Member tab lists. Replaces the prior {@code *Page} fields — the
     * right-side {@link ScrollbarWidget} now drives incremental row scrolling
     * (one row per wheel notch) instead of discrete << / >> page jumps, and
     * the value is the offset of the first visible row rather than a page
     * index.
     */
    private int selectionScroll = 0;
    private int memberScroll = 0;
    private int connectionScroll = 0;

    /**
     * Currently active list-tab scrollbar widget. {@code null} on Home /
     * Create / Settings tabs (no scrollable content). The screen-level
     * {@link #mouseScrolled} forwards wheel events to this scrollbar so
     * scrolling works over the list, not just over the handle.
     */
    private ScrollbarWidget currentScrollbar;

    /**
     * Row buttons currently rendered for the active list tab. Tracked
     * separately so scrolling can swap the row buttons without rebuilding
     * the rest of the tab — that lets the scrollbar's {@code dragging}
     * state survive across mouse-drag scrolls (a full {@link #clearWidgets}
     * would replace the scrollbar mid-drag and break the gesture).
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
    // When true, the popup target is an online player who isn't a member
    // yet. Only the "Set as User" action is meaningful — the other three
    // buttons are greyed out since there's nothing to demote or remove.
    private boolean popupIsStranger = false;

    /**
     * Last server-sent {@link FrequencyResponsePacket} message rendered
     * as an in-GUI toast. Minecraft's action-bar lives in the hotbar
     * zone — which container screens cover — so a raw
     * {@code displayClientMessage(..., true)} is invisible while this
     * GUI is open. Stashing the component here and painting it inside
     * {@link #renderLabels} makes the feedback actually reach the user.
     */
    private Component inlineError = null;
    private long inlineErrorExpiresAt = 0L;
    private static final long INLINE_ERROR_DURATION_MS = 4000L;
    private final List<FittedTextTooltip> fittedTextTooltips = new ArrayList<>();

    /**
     * Called by {@link FrequencyResponsePacket}'s client handler when
     * this screen is the active one. Replaces whatever prior toast was
     * showing (a newer error always wins — the previous one is already
     * stale information after a fresh server round-trip).
     */
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
        // Card link status arrives after the opening payload and can change without a frequency-list revision.
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

    /**
     * Constructs an {@link AETextField} the same way AE2's own
     * {@code WidgetContainer.addTextField} does: after the constructor
     * it calls {@code setBordered(false)} so the inherited
     * {@link net.minecraft.client.gui.components.EditBox}'s default
     * 1-px light border and black interior fill are suppressed, leaving
     * AE2's {@code guis/text_field.png} 3-slice sprite as the ONLY
     * visible frame. Without this, EditBox's default black rectangle
     * sits inside AE2's sprite and produces the bracket-with-black-fill
     * artifact that was visible in the Settings and Create tabs.
     */
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
                    appeng.client.gui.style.Blitter.texture("guis/text_field.png", 128, 128)
                            .src(1, variant, width, 12).dest(bounds.getX() + x, bounds.getY()).blit(graphics);
                }
            }
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Placeholder string shown in the Settings-tab password field when
     * the frequency already has a stored (and now hashed) password. The
     * user can't see the real value — we only need a visual marker —
     * and any first keystroke/backspace clears it so they can type a
     * fresh password. Submitting without touching the field leaves the
     * sentinel in place, and the Apply handler sends {@code ""} to the
     * server (which preserves the stored hash).
     *
     * <p>Eight U+2022 bullets. Width stays under the 16-char
     * {@code MAX_PASSWORD_LENGTH} cap so it never gets truncated when
     * {@link AETextField#setValue} enforces the length limit.</p>
     */
    private static final String PASSWORD_SENTINEL = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    /**
     * Tracks whether the Settings-tab password field is still showing
     * the {@link #PASSWORD_SENTINEL}. While armed, the Apply button
     * treats the field as "unchanged" and sends an empty string; once
     * the user types/deletes anything the flag flips off and the real
     * field value is forwarded verbatim.
     */
    private boolean settingsPasswordPristine = false;

    /**
     * When non-zero, a modal password prompt is active for this
     * frequency id — all tab body content is suppressed and the user
     * must either submit a password (triggering
     * {@link SelectFrequencyPacket} with the entered plaintext) or
     * cancel. Set by:
     * <ul>
     *   <li>{@link #checkAutoPasswordPrompt} — when the device we're
     *       looking at is bound to an ENCRYPTED frequency we're not a
     *       member of yet (case 1: right-click a locked device).</li>
     *   <li>The Selection-tab row {@code onPress} — when the player
     *       clicks an ENCRYPTED frequency they haven't unlocked yet
     *       (case 2: browsing the frequency list).</li>
     * </ul>
     * Cleared automatically on rebuild once the player's cache shows
     * them as a member (server-side {@code enrollAsUser} fires after
     * a successful {@link SelectFrequencyPacket} round-trip).
     */
    private int passwordPromptFreqId = 0;
    private String passwordPromptFreqName = "";
    /**
     * When {@code true}, cancelling the password prompt closes the
     * whole screen instead of just dismissing the popup. Used for the
     * device-bind path (case 1) where a non-member player who chickens
     * out has nothing else to do with the device anyway.
     */
    private boolean passwordPromptLocksScreen = false;
    private AETextField passwordPromptField;

    /**
     * Query string for the Selection-tab search field (repurposed from
     * the old inline password input). Matches frequency names
     * case-insensitively; empty means "show everything". Stored
     * outside {@link #initSelectionTab} so repeated rebuilds from
     * {@link #scheduleRebuild} don't reset it.
     */
    private String selectionSearchQuery = "";

    /**
     * {@link AETextField} that, while {@link #settingsPasswordPristine}
     * is true, wipes its placeholder on the first key or character
     * event so the user's typed input replaces the sentinel instead of
     * appending to it. Used only by the Settings tab — the Create tab's
     * password field never arms this flag and behaves as a plain field.
     */
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
        // clearWidgets removed our row buttons + scrollbar from the screen,
        // but our cached references still point at the discarded instances.
        // Reset them so the per-tab init below can rebind to fresh widgets.
        currentRowButtons.clear();
        currentScrollbar = null;
        channelAutoButton = energyAutoButton = null;
        closePopup();

        // Password prompt re-evaluation: if the target frequency now
        // grants us membership (server accepted our password and
        // enrolled us as USER), auto-dismiss the popup. Otherwise,
        // seed the popup for the device-bind case — if the device
        // we opened is pinned to an ENCRYPTED frequency and we're
        // still an outsider, pop the prompt immediately so the user
        // doesn't see the locked GUI body.
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

        // Password prompt modal takes precedence over both the
        // delete-confirm modal and the normal tab body — the user
        // can't meaningfully interact with the selected frequency
        // until they authenticate.
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

    /**
     * Builds the Confirm / Cancel buttons for the delete-frequency modal.
     * The panel chrome is drawn in {@link #drawDeleteConfirmPanel}
     * (from {@code renderBg}), the title/hint text in
     * {@link #renderLabels}, and the two buttons live here so they stay
     * on top of the panel fill.
     */
    private void buildDeleteConfirmWidgets(int x0, int y0) {
        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 - 84, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> { deleteConfirmOpen = false; scheduleRebuild(); }));

        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 + 4, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.confirm")
                        .copy().withStyle(ChatFormatting.DARK_RED),
                btn -> {
                    int freqId = freqMenu().getCurrentFrequencyId();
                    if (freqId > 0) {
                        PacketDistributor.sendToServer(new DeleteFrequencyPacket(token(), freqId));
                    }
                    deleteConfirmOpen = false;
                    switchTab(FrequencyPage.TAB_SELECTION);
                }));
    }

    /**
     * Returns true when the local player has no durable access to this
     * ENCRYPTED frequency and must type a password to unlock it.
     * Non-ENCRYPTED frequencies always return false (PRIVATE rejects
     * non-members outright; PUBLIC hands everyone a USER fallback).
     * Reads membership from {@link ClientFrequencyCache} — which is
     * synced by the server-push packets — so this check stays honest
     * even after a successful unlock (the enroll broadcast causes a
     * fresh rebuild, and needsPasswordUnlock flips to false).
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

    /**
     * Device-bind auto-prompt (case 1 from the password-gate UI spec):
     * if the device we just opened is pinned to a frequency that
     * requires password unlock for the current player, arm the popup
     * in lock-screen mode so Cancel closes the whole GUI instead of
     * just dismissing the modal.
     */
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

    /**
     * Builds the widgets for the password prompt modal: a single
     * {@link AETextField} centred below the freq name label plus a
     * Submit / Cancel button pair. Panel chrome is drawn in
     * {@link #drawPasswordPromptPanel} (from {@code renderBg}) and
     * the title text in {@link #renderLabels}.
     */
    private void buildPasswordPromptWidgets(int x0, int y0) {
        passwordPromptField = makeAe2Field(x0 + 24, y0 + 78, GUI_WIDTH - 48, INPUT_HEIGHT);
        passwordPromptField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordPromptField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.password"));
        addRenderableWidget(passwordPromptField);

        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 - 84, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.cancel"),
                btn -> cancelPasswordPrompt()));

        addRenderableWidget(new AE2Button(
                x0 + GUI_WIDTH / 2 + 4, y0 + 108, 80, 18,
                Component.translatable("ae2lt.gui.button.submit"),
                btn -> submitPasswordPrompt()));
    }

    /**
     * Fires the {@link SelectFrequencyPacket} with the typed password.
     * Does NOT clear {@link #passwordPromptFreqId} — the popup stays
     * open until the next rebuild re-evaluates
     * {@link #needsPasswordUnlock} against the updated cache. That
     * way a wrong password keeps the modal visible for retry (the
     * server's {@code NO_PERMISSION} response already renders as a
     * toast via {@link FrequencyResponsePacket}).
     */
    private void submitPasswordPrompt() {
        String pw = passwordPromptField == null ? "" : passwordPromptField.getValue();
        int freqId = passwordPromptFreqId;
        if (freqId <= 0) return;
        PacketDistributor.sendToServer(new SelectFrequencyPacket(
                token(), freqMenu().getBlockPos(), freqId, pw));
    }

    /**
     * Cancel button. For the device-bind path (case 1, flag
     * {@link #passwordPromptLocksScreen}) we have nothing useful to
     * show the player, so we close the whole screen. For the
     * selection-row path (case 2) we just dismiss the modal and
     * return to the Selection tab.
     */
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

    /**
     * Defers {@link #initTabWidgets()} to the next main-thread tick so
     * the current click event (which is what brought us here via a
     * button's {@code onPress}) finishes dispatching BEFORE
     * {@link #clearWidgets} rips the clicked widget out of the screen's
     * children list. Rebuilding inline produced duplicate
     * {@code UI_BUTTON_CLICK} sounds on tab presses — almost certainly
     * a vanilla side-effect of replacing the widget that's still
     * unwinding its own {@code mouseClicked} stack frame.
     */
    private void scheduleRebuild() {
        Minecraft.getInstance().execute(this::initTabWidgets);
    }

    // Tab: Home

    private void initHomeTab(int x0, int y0) {
        boolean card = freqMenu().isCardMode();
        if (card) {
            channelAutoButton = addAutoButton(x0 + GUI_WIDTH / 2 - 24, y0 + imageHeight - 24, false);
            energyAutoButton = addAutoButton(x0 + GUI_WIDTH - 30, y0 + imageHeight - 24, true);
            updateAutoButtons();
        }
        if (freqMenu().usesLocalConnectionList()) {
            var links = addRenderableWidget(new AE2Button(x0 + (GUI_WIDTH - 104) / 2, y0 + imageHeight - 22, 104, 16,
                    Component.translatable("ae2lt.management.connections"), btn -> switchTab(FrequencyPage.TAB_CONNECTION)));
            links.active = freqMenu().getCurrentFrequencyId() > 0;
        }
    }

    private ToggleButton addAutoButton(int x, int y, boolean energy) {
        var button = new ToggleButton(Icon.AUTO_EXPORT_ON, Icon.AUTO_EXPORT_OFF, state -> {
            if (energy) freqMenu().clientToggleAutoConnectEnergy();
            else freqMenu().clientToggleAutoConnect();
        });
        button.setPosition(x, y);
        return addRenderableWidget(button);
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
                ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT, total, ITEMS_PER_PAGE_LIST, connectionScroll,
                offset -> connectionScroll = offset);
        addRenderableWidget(currentScrollbar);
        addRenderableWidget(new AE2Button(x0 + (GUI_WIDTH - 104) / 2, y0 + FOOTER_Y, 104, 16,
                Component.translatable("ae2lt.gui.button.back"), btn -> switchTab(FrequencyPage.TAB_HOME)));
    }
    private void renderLocalConnections(GuiGraphics g) {
        var rows = ClientFrequencyCache.getConnections(freqMenu().getCurrentFrequencyId());
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.total").append(": " + rows.size()), 10, 36, AE2_TEXT_MUTED);
        if (rows.isEmpty()) drawFlatCentered(g, Component.translatable("ae2lt.gui.connection.none"), imageWidth / 2, 55, AE2_TEXT_MUTED);
        for (int i = connectionScroll; i < Math.min(rows.size(), connectionScroll + ITEMS_PER_PAGE_LIST); i++) {
            var row = rows.get(i); int y = LIST_ROW_FIRST_Y + (i - connectionScroll) * LIST_ROW_HEIGHT;
            var state = Component.translatable("ae2lt.management.state." + (row.loaded() ? "loaded" : "unloaded"));
            drawFlatFitted(g, Component.translatable(row.deviceName()), 12, y + 2, LIST_ROW_WIDTH - 16 - font.width(state), AE2_TEXT_BODY);
            drawFlat(g, state, LIST_ROW_X + LIST_ROW_WIDTH - 5 - font.width(state), y + 2, AE2_TEXT_MUTED);
            drawFlatFitted(g, row.dimension() + " · " + row.pos().getX() + " " + row.pos().getY() + " " + row.pos().getZ(),
                    12, y + 11, LIST_ROW_WIDTH - 8, AE2_TEXT_MUTED);
        }
    }

    // Tab: Selection

    private void initSelectionTab(int x0, int y0) {
        // Search field (was the password field pre-popup-refactor).
        // Typed queries filter the frequency list by a case-insensitive
        // substring match on the name; on each keystroke we reset the
        // scroll to the top and rebuild only the row buttons (the
        // scrollbar widget itself is replaced by initTabWidgets via
        // scheduleRebuild because the total count may have changed).
        int searchX = x0 + 8;
        AETextField searchField = selectionSearchField = makeAe2Field(searchX, y0 + 23, GUI_WIDTH - 16, INPUT_HEIGHT);
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
        boolean connected = freqMenu().getCurrentFrequencyId() > 0;
        var disconnect = addRenderableWidget(new AE2Button(x0 + 8, y0 + FOOTER_Y, 96, 16,
                Component.translatable("ae2lt.gui.button.disconnect"),
                btn -> PacketDistributor.sendToServer(new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, ""))));
        disconnect.active = connected;
        addRenderableWidget(new AE2Button(x0 + GUI_WIDTH - 104, y0 + FOOTER_Y, 96, 16,
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
                    display, f.id() == freqMenu().getCurrentFrequencyId(),
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
                ITEMS_PER_PAGE_LIST * LIST_ROW_HEIGHT,
                total, ITEMS_PER_PAGE_LIST,
                memberScroll,
                offset -> { memberScroll = offset; rebuildMemberRows(); });
        addRenderableWidget(currentScrollbar);

        rebuildMemberRows();
    }

    /**
     * Snapshot of every row that should appear in the Members tab —
     * established members first (in server order), then online players
     * who aren't members yet ("strangers"). The flag distinguishes the
     * two so the row-builder picks the right onPress handler without
     * having to re-derive membership for each visible row.
     */
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
                // Build the member-row label as a SINGLE Component instance.
                // Earlier we used ``.copy().append(...)`` to splice a dimmed
                // " [O]" suffix onto the coloured name — but AE2Button's
                // horizontal-scroll text renderer interacts badly with
                // sibling Components (they can measure differently and end
                // up painting at staggered positions). A single literal with
                // a uniform style renders flush on one line inside the row
                // button.
                StringBuilder text = new StringBuilder(entry.name());
                if (isSelf(entry.uuid())) {
                    text.append(' ')
                            .append(Component.translatable("ae2lt.gui.member.you").getString());
                }
                text.append(" [").append(accessLabel(entry.access())).append(']');
                Component display = Component.literal(text.toString())
                        .withStyle(entry.access().getFormatting());
                // Self row is a read-only identity tag — every action in
                // the popup is self-locked anyway (see the multi-owner
                // self-lock rule), so disabling the click here avoids
                // surfacing a popup whose four buttons would all be greyed
                // out.
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

    /**
     * Opens the popup for an online player who isn't a member yet.
     * Sets {@link #popupIsStranger} so {@link #rebuildMemberPopupWidgets}
     * knows to enable only the "Set as User" button — Set Admin / Remove
     * / Set Owner are meaningless for someone who has no access at all.
     * Clicking "Set as User" reuses the existing
     * {@code MEMBERSHIP_SET_USER} packet which creates the member when
     * none exists.
     */
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
        int py = y0 + 50;
        int pw = GUI_WIDTH - 48;

        FrequencyAccessLevel myAccess = selfAccess();
        FrequencyAccessLevel targetLevel = popupIsStranger
                ? FrequencyAccessLevel.BLOCKED
                : popupMemberAccess;
        boolean targetIsOwner = targetLevel == FrequencyAccessLevel.OWNER;
        // Self-lock (multi-owner): even though an OWNER can operate on
        // peer OWNERs, acting on yourself would drop the owner count,
        // breaking the "at least one OWNER remains" invariant. The
        // Members-tab row for self is already click-disabled, but this
        // keeps the popup safe if it's reached via another path.
        boolean isSelfTarget = !popupIsStranger && isSelf(popupMemberUUID);
        boolean ownerActingOnSelf = targetIsOwner && isSelfTarget;

        // Permission gates (shared rule from {@link
        // FrequencyAccessLevel#canActOnLevel}): evaluate max(current,
        // new) so promotions and demotions both route through the same
        // rank test. Strangers enter via {@code targetLevel = BLOCKED},
        // which means every rank-check behaves as if they had no prior
        // access — an OWNER can mint them straight into ADMIN or
        // OWNER without the artificial "USER first" stepping stone.
        // The text-style tint is applied only when the button is
        // actually clickable — otherwise the dark-red / gold colours
        // still read as active even on a greyed button.
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

        // Remove is the one button that stays off for strangers —
        // there's no durable membership entry to cancel.
        boolean canRemove = !ownerActingOnSelf
                && !popupIsStranger
                && myAccess.canActOnLevel(targetLevel);
        Component removeLabel = Component.translatable("ae2lt.gui.member.remove");
        if (canRemove) {
            removeLabel = removeLabel.copy().withStyle(ChatFormatting.DARK_RED);
        }
        AE2Button btnRemove = new AE2Button(px, py + 40, pw, 18,
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
        AE2Button btnSetOwner = new AE2Button(px, py + 60, pw, 18,
                setOwnerLabel,
                btn -> sendMember(WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP));
        btnSetOwner.active = canSetOwner;
        addRenderableWidget(btnSetOwner);

        addRenderableWidget(new AE2Button(x0 + (GUI_WIDTH - 96) / 2, py + 98, 96, 18,
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
        addRenderableWidget(new AE2Button(x0 + 8, y0 + FOOTER_Y, creating ? 96 : FOOTER_BUTTON_WIDTH, 16,
                Component.translatable("ae2lt.gui.button.back"), btn -> switchTab(FrequencyPage.TAB_SELECTION)));
        if (!creating && freq == null) return;
        boolean canEdit = creating || hasManagerAccess();
        boolean canSecure = creating || hasOwnerAccess();

        nameField = makeAe2Field(x0 + 12, y0 + 36, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        nameField.setMaxLength(WirelessFrequency.MAX_NAME_LENGTH);
        nameField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.name"));
        nameField.setValue(savedName); nameField.setEditable(canEdit); addRenderableWidget(nameField);
        passwordField = new PristinePasswordField(x0 + 12, y0 + 90, INPUT_MAX_WIDTH, INPUT_HEIGHT);
        passwordField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordField.setPlaceholder(Component.translatable("ae2lt.gui.frequency.password"));
        passwordField.setValue(savedPassword); passwordField.setEditable(canSecure);
        passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED); addRenderableWidget(passwordField);
        var security = addRenderableWidget(new AE2Button(x0 + GUI_WIDTH - 104, y0 + 54, 92, 18, getSecurityLabel(editSecurity), btn -> {
            editSecurity = FrequencySecurityLevel.VALUES[(editSecurity.ordinal() + 1) % FrequencySecurityLevel.VALUES.length];
            passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
            passwordField.setValue(""); settingsPasswordPristine = false; btn.setMessage(getSecurityLabel(editSecurity));
        }));
        security.active = canSecure;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int color = PRESET_COLORS[i];
            var swatch = addRenderableWidget(Button.builder(Component.literal(" "), btn -> editColor = color)
                    .bounds(x0 + GUI_WIDTH - 122 + (i % 7) * 16, y0 + 108 + (i / 7) * 16, 14, 14).build());
            swatch.active = canEdit;
        }
        var submit = addRenderableWidget(new AE2Button(x0 + GUI_WIDTH - 8 - (creating ? 96 : FOOTER_BUTTON_WIDTH), y0 + FOOTER_Y, creating ? 96 : FOOTER_BUTTON_WIDTH, 16,
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
                    Component.translatable("ae2lt.gui.button.delete").copy().withStyle(ChatFormatting.DARK_RED),
                    btn -> { deleteConfirmOpen = true; scheduleRebuild(); }));
            else addRenderableWidget(new AE2Button(x0 + 12 + FOOTER_BUTTON_WIDTH, y0 + FOOTER_Y, FOOTER_BUTTON_WIDTH, 16,
                    Component.translatable("ae2lt.gui.button.leave"), btn -> {
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                // Disconnect while access still exists, then leave the frequency.
                PacketDistributor.sendToServer(new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, ""));
                PacketDistributor.sendToServer(new ChangeMemberPacket(token(), freq.id(), player.getUUID(), WirelessFrequency.MEMBERSHIP_CANCEL));
                switchTab(FrequencyPage.TAB_SELECTION);
            }));
        }
    }

    // Rendering

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        BackgroundGenerator.draw(GUI_WIDTH, imageHeight, g, leftPos, topPos);
        if (popupMemberUUID == null && !deleteConfirmOpen && passwordPromptFreqId == 0) {
            if (currentTab == FrequencyPage.TAB_HOME) drawInset(g, leftPos + 8, topPos + 24, GUI_WIDTH - 16, freqMenu().isCardMode() ? 54 : 82);
            else if (currentTab == FrequencyPage.TAB_CONNECTION) {
                int visible = Math.min(ITEMS_PER_PAGE_LIST,
                        ClientFrequencyCache.getConnections(freqMenu().getCurrentFrequencyId()).size() - connectionScroll);
                for (int i = 0; i < visible; i++) BackgroundGenerator.draw(LIST_ROW_WIDTH, LIST_ROW_BUTTON_HEIGHT,
                        g, leftPos + LIST_ROW_X, topPos + LIST_ROW_FIRST_Y + i * LIST_ROW_HEIGHT);
            }
        }

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

    private void drawPasswordPromptPanel(GuiGraphics g) { BackgroundGenerator.draw(GUI_WIDTH - 16, 96, g, leftPos + 8, topPos + 42); }
    private void drawDeleteConfirmPanel(GuiGraphics g) { BackgroundGenerator.draw(GUI_WIDTH - 16, 96, g, leftPos + 8, topPos + 42); }
    private void drawMemberPopupPanel(GuiGraphics g) { BackgroundGenerator.draw(GUI_WIDTH - 16, 146, g, leftPos + 8, topPos + 28); }
    private static void drawInset(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF999BAD);
        g.fill(x, y, x + width, y + 1, 0xFF5A5A6B);
        g.fill(x, y, x + 1, y + height, 0xFF5A5A6B);
        g.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF);
        g.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        fittedTextTooltips.clear();

        drawFlat(g, Component.translatable(currentTab.getTranslationKey()), 8, 7, AE2_TEXT_TITLE);

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
                case TAB_MEMBER -> renderMemberLabels(g);
                case TAB_CREATE, TAB_SETTING -> renderFormLabels(g);
                case TAB_CONNECTION -> renderLocalConnections(g);
                case TAB_DEVICES, TAB_ENERGY -> {}
            }
        }

        if (deleteConfirmOpen) {
            drawFlatCentered(g,
                    Component.translatable("ae2lt.gui.delete_confirm.title")
                            .copy().withStyle(ChatFormatting.DARK_RED),
                    imageWidth / 2, 54, AE2_TEXT_TITLE);
            drawFlatCentered(g,
                    Component.translatable("ae2lt.gui.delete_confirm.hint"),
                    imageWidth / 2, 78, AE2_TEXT_MUTED);
        }

        if (passwordPromptFreqId > 0) {
            drawFlatCenteredFitted(g,
                    Component.translatable("ae2lt.gui.password_prompt.title", passwordPromptFreqName),
                    imageWidth / 2, 54, GUI_WIDTH - 40, AE2_TEXT_TITLE);
        }

        if (popupMemberUUID != null) {
            // Header inside the popup panel: target member's name with
            // their access code tacked on as a single line, positioned
            // above the first action button so it never overlaps the
            // {@code 设为用户} row.
            // Stranger popup shows the target dimmed (they have no
            // access yet) with the localised "stranger" code tagged on
            // — everything else is the same one-line header layout so
            // the button stack below stays aligned.
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
            drawFlatCenteredFitted(g, header, imageWidth / 2, 36, GUI_WIDTH - 40, AE2_TEXT_TITLE);
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
                int bgY = imageHeight - 40;
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

    /**
     * AE2 draws panel-surface text flat (no drop-shadow) so the character
     * glyphs read cleanly against the light-lavender chassis. Minecraft's
     * default {@link GuiGraphics#drawString(net.minecraft.client.gui.Font,
     * Component, int, int, int)} draws WITH a shadow, which on a light
     * panel produces an unpleasant embossed look. All label rendering in
     * this screen goes through this helper (and
     * {@link #drawFlatCentered}) to match AE2's native visual weight.
     */
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
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        Component frequency = freq == null ? Component.translatable("ae2lt.gui.frequency.none")
                : Component.literal(freq.name()).withStyle(Style.EMPTY.withColor(freq.color() & 0xFFFFFF));
        homeLine(g, "ae2lt.gui.frequency.current", frequency, 30);
        homeLine(g, "ae2lt.gui.home.device_type", Component.translatable(freqMenu().getDeviceName()), 44);
        homeLine(g, "ae2lt.gui.home.status", Component.translatable(freqMenu().isLinkActive()
                ? "ae2lt.gui.home.connected" : "ae2lt.gui.home.disconnected"), 58);
        if (freqMenu().isCardMode()) {
            renderFrequencyDetails(g, 96);
            drawFlatFitted(g, Component.translatable("ae2lt.gui.home.auto_section"), 12, imageHeight - 46,
                    imageWidth - 24, AE2_TEXT_MUTED);
            drawFlatFitted(g, Component.translatable("ae2lt.gui.home.auto_channel_" + (freqMenu().isAutoConnect() ? "on" : "off")),
                    12, imageHeight - 20, GUI_WIDTH / 2 - 40, AE2_TEXT_BODY);
            drawFlatFitted(g, Component.translatable("ae2lt.gui.home.auto_energy_" + (freqMenu().isAutoConnectEnergy() ? "on" : "off")),
                    GUI_WIDTH / 2 + 6, imageHeight - 20, GUI_WIDTH / 2 - 40, AE2_TEXT_BODY);
            return;
        }
        int used = freqMenu().getUsedChannels(), max = freqMenu().getMaxChannels();
        Component channels = max < 0 ? Component.translatable("ae2lt.gui.home.grid_channels.infinite", used)
                : Component.translatable("ae2lt.gui.home.grid_channels.value", used, max, Math.max(0, max - used));
        homeLine(g, "ae2lt.gui.home.grid_channels", channels, 72);
        if (freqMenu().isController()) homeLine(g, "ae2lt.gui.home.cross_dimension", Component.translatable(
                freqMenu().isAdvanced() ? "ae2lt.gui.home.cross_dimension.yes" : "ae2lt.gui.home.cross_dimension.no"), 86);
        renderFrequencyDetails(g, 124);
    }

    private void renderFrequencyDetails(GuiGraphics g, int top) {
        var frequency = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        drawFlat(g, Component.translatable("ae2lt.gui.home.frequency_details"), 12, top, AE2_TEXT_MUTED);
        homeLine(g, "ae2lt.gui.frequency.security", frequency == null
                ? Component.translatable("ae2lt.gui.frequency.none") : getSecurityLabel(frequency.security()), top + 16);
        var access = selfAccess();
        Component permission = frequency == null ? Component.translatable("ae2lt.gui.frequency.none")
                : !access.canUse() ? Component.translatable("ae2lt.gui.home.no_access")
                : Component.literal(accessLabel(access)).withStyle(access.getFormatting());
        homeLine(g, "ae2lt.gui.home.access", permission, top + 30);
    }

    private void homeLine(GuiGraphics g, String label, Component value, int y) {
        drawFlatFitted(g, Component.translatable(label).append(": ").append(value), 12, y, imageWidth - 24, AE2_TEXT_BODY);
    }

    private void renderSelectionLabels(GuiGraphics g) {
        // Current frequency and count share the strip below search.
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
        drawFlatFitted(g, left, 10, 36, Math.max(20, rightX - 24), AE2_TEXT_BODY);
        drawFlat(g, right, rightX, 36, AE2_TEXT_MUTED);
    }

    private void renderMemberLabels(GuiGraphics g) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.error.no_frequency"),
                    imageWidth / 2, 40, AE2_TEXT_MUTED);
            return;
        }

        // Access on the left, count on the right.
        FrequencyAccessLevel myAccess = selfAccess();
        Component left = Component.translatable("ae2lt.gui.member.your_access",
                Component.translatable("ae2lt.gui.access." + myAccess.name().toLowerCase())
                        .withStyle(myAccess.getFormatting()));
        var members = ClientFrequencyCache.getMembers(currentId);
        Component right = Component.translatable("ae2lt.gui.frequency.total")
                .append(": " + members.size());
        int rightX = imageWidth - 10 - font.width(right);
        drawFlatFitted(g, left, 10, 36, Math.max(20, rightX - 24), AE2_TEXT_BODY);
        drawFlat(g, right, rightX, 36, AE2_TEXT_MUTED);

        if (members.isEmpty()) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.member.none"),
                    imageWidth / 2, 70, AE2_TEXT_MUTED);
        }
    }

    private void renderFormLabels(GuiGraphics g) {
        if (currentTab == FrequencyPage.TAB_SETTING && ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId()) == null) {
            drawFlatCentered(g, Component.translatable("ae2lt.gui.error.no_frequency"), imageWidth / 2, 40, AE2_TEXT_MUTED);
            return;
        }
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.name").append(":"), 12, 24, AE2_TEXT_MUTED);
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.security").append(":"), 12, 58, AE2_TEXT_MUTED);
        if (editSecurity == FrequencySecurityLevel.ENCRYPTED)
            drawFlat(g, Component.translatable("ae2lt.gui.frequency.password").append(":"), 12, 78, AE2_TEXT_MUTED);
        drawFlat(g, Component.translatable("ae2lt.gui.frequency.color").append(":"), 12, 110, AE2_TEXT_MUTED);
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int x = GUI_WIDTH - 122 + (i % 7) * 16, y = 108 + (i / 7) * 16;
            g.fill(x + 1, y + 1, x + 13, y + 13, PRESET_COLORS[i] | 0xFF000000);
            if (PRESET_COLORS[i] == editColor) {
                g.fill(x, y, x + 14, y + 1, 0xFFFFFFFF); g.fill(x, y + 13, x + 14, y + 14, 0xFFFFFFFF);
                g.fill(x, y, x + 1, y + 14, 0xFFFFFFFF); g.fill(x + 13, y, x + 14, y + 14, 0xFFFFFFFF);
            }
        }
        g.fill(12, 144, 24, 154, editColor | 0xFF000000);
        if (nameField != null && !nameField.getValue().isBlank())
            drawFlatFitted(g, nameField.getValue(), 28, 145, imageWidth - 40, editColor | 0xFF000000);
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
        // AbstractContainerScreen already renders the background once.
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

    /** AE2 list row with a persistent binding badge independent of hover/focus, preserving label colours. */
    private final class RowSpriteButton extends Button {
        private final boolean bound;
        private final Component boundLabel = Component.translatable("ae2lt.gui.frequency.bound");
        RowSpriteButton(int x, int y, int width, int height,
                Component message, OnPress onPress) {
            this(x, y, width, height, message, false, onPress);
        }

        RowSpriteButton(int x, int y, int width, int height,
                Component message, boolean bound, OnPress onPress) {
            super(Button.builder(message, onPress).bounds(x, y, width, height));
            this.bound = bound;
            if (bound) {
                setTooltip(Tooltip.create(Component.empty().append(message).append(" — ").append(boundLabel)));
            } else if (!message.getString().equals(FittingText.fit(message.getString(), labelWidth(), font::width))) {
                setTooltip(Tooltip.create(message));
            }
        }

        private int labelWidth() { return getWidth() - 12 - (bound ? font.width(boundLabel) + 12 : 0); }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return bound ? Component.empty().append(super.createNarrationMessage()).append(". ").append(boundLabel)
                    : super.createNarrationMessage();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = active && (isHoveredOrFocused());
            BackgroundGenerator.draw(getWidth(), getHeight(), g, getX(), getY());
            if (hover) g.fill(getX() + 3, getY() + 3, getX() + getWidth() - 3, getY() + getHeight() - 3,
                    ae2Style.getColor(PaletteColor.SELECTION_COLOR).toARGB());
            if (bound) g.fill(getX() + 2, getY() + 3, getX() + 4, getY() + getHeight() - 3,
                    ae2Style.getColor(PaletteColor.SELECTION_COLOR).toARGB());

            // Vanilla MC's drawString honours the Component's per-style
            // colour, so the access tint / frequency RGB baked into
            // {@code getMessage()} flows through. The fallback colour
            // is only used for unstyled glyphs — pick black on the
            // light idle sprite, dark blue-gray on the disabled sprite.
            int fallback = active ? 0x000000 : 0x404040;
            String full = getMessage().getString();
            String fitted = FittingText.fit(full, labelWidth(), font::width);
            Component display = full.equals(fitted)
                    ? getMessage()
                    : Component.literal(fitted).setStyle(getMessage().getStyle());
            int textY = getY() + (getHeight() - 8) / 2;
            int textX = getX() + 6;
            g.drawString(font, display, textX, textY, fallback, false);
            if (bound) g.drawString(font, boundLabel, getX() + getWidth() - 6 - font.width(boundLabel), textY,
                    0x303030, false);
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
