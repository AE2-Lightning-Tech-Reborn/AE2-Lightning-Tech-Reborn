package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.client.ldlib.LdlibAeSubScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2TextField;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import appeng.core.localization.GuiText;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import appeng.menu.me.common.MEStorageMenu;
import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import guideme.PageAnchor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Inventory-maintenance and Tianshu-wide reserved-stock overview. */
public final class TianshuGlobalReserveScreen<M extends MEStorageMenu & TianshuMaintenanceMenu>
        extends LdlibAeSubScreen<M, TianshuMaintenanceTermScreen<M>> {
    private ModularUI foreground;
    private Label valueColumn;
    private Label emptyMessage;
    private Label addHint;
    private Label overflowMessage;
    private TianshuMaintenanceList<OverviewEntry> entryList;

    @Override
    protected PageAnchor getHelpTopic() {
        return TianshuMaintenanceTermScreen.terminalHelpTopic(menu,
                view == View.RESERVES ? "global-reserves" : "inventory-maintenance");
    }

    private static final int LIST_LEFT = 9;
    private static final int LIST_RIGHT = 187;
    private static final int CONTENT_TEXT_LEFT = 37;
    private static final int STOCK_COLUMN_RIGHT = 143;
    private static final int VALUE_COLUMN_RIGHT = 184;
    private static final int FIRST_ROW = 64;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 6;
    private static final int EMPTY_TEXT_Y = 121;

    private final LdlibAe2TextField search;
    private final LdlibRowScroller scrollbar;
    private final LdlibAe2Button rulesButton;
    private final LdlibAe2Button reservesButton;
    private final LdlibAe2TextField reserveAmount;
    private final LdlibAe2Button addReserveButton;
    private final boolean restoreMaintainableView;
    private View view = View.RULES;
    private boolean awaitingRuleEditor;
    private int requestedRuleEditorRevision;

    public TianshuGlobalReserveScreen(TianshuMaintenanceTermScreen<M> parent) {
        super(parent, "/screens/tianshu_inventory_overview.json");
        restoreMaintainableView = menu.isMaintainableView();
        if (restoreMaintainableView) menu.setMaintainableView(false);

        scrollbar = LdlibRowScroller.add(widgets, "scrollbar");
        scrollbar.setCaptureMouseWheel(false);
        search = LdlibAe2TextField.add(widgets, style, "search");
        search.setPlaceholder(GuiText.SearchPlaceholder.text());
        search.setResponder(ignored -> {
            scrollbar.setCurrentScroll(0);
            if (entryList != null) {
                entryList.setEntries(entries());
            }
        });
        rulesButton = LdlibAe2Button.addText(widgets, style, "rules", tabLabel(View.RULES), () -> selectView(View.RULES));
        reservesButton = LdlibAe2Button.addText(widgets, style, "reserves", tabLabel(View.RESERVES), () -> selectView(View.RESERVES));
        reserveAmount = LdlibAe2TextField.add(widgets, style, "reserveAmount");
        reserveAmount.setMaxLength(19);
        reserveAmount.setFilter(TianshuGlobalReserveScreen::validAddAmountDraft);
        reserveAmount.setPlaceholder(Component.translatable(
                "ae2lt.tianshu.reserve.add_amount"));
        reserveAmount.setValue("1");
        addReserveButton = LdlibAe2Button.addText(widgets, style, "addReserve",
                Component.translatable("ae2lt.tianshu.reserve.add"), this::addMarkedReserve);
        widgets.add("back", LdlibAe2Button.back(Component.translatable("gui.back"), this::returnToParent));
    }

    @Override
    public void init() {
        // Slot positions belong to the shared menu. Defer hiding until switchToScreen has saved
        // the terminal's positions so returning can restore them.
        hideSlots();
        super.init();
        var bounds = new Rect2i(LIST_LEFT, FIRST_ROW, LIST_RIGHT - LIST_LEFT, ROW_HEIGHT * VISIBLE_ROWS);
        entryList = new TianshuMaintenanceList<>(imageWidth, imageHeight, bounds, ROW_HEIGHT, scrollbar,
                OverviewRow::new, this::openEntry, this::entryTooltip);
        entryList.setEntries(entries());
        initForeground();
        updateAddControls();
    }

    private void initForeground() {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var root = new UIElement();
        LdlibScreens.addLabel(root, Component.translatable("ae2lt.tianshu.maintenance.overview_title"),
                textColor, 10, 9, 158);
        LdlibScreens.addLabel(root, Component.translatable("ae2lt.tianshu.maintenance.column.item"),
                textColor, CONTENT_TEXT_LEFT, 53, 72);
        var stock = LdlibScreens.addLabel(root, Component.translatable("ae2lt.tianshu.maintenance.column.stock"),
                textColor, 0, 53, STOCK_COLUMN_RIGHT);
        stock.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));
        valueColumn = LdlibScreens.addLabel(root, Component.empty(), textColor, 0, 53, VALUE_COLUMN_RIGHT);
        valueColumn.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));
        var divider = new UIElement().setAllowHitTest(false).style(style ->
                style.background(LdlibScreens.solid(() -> 0x335D646D)));
        root.addChild(LdlibScreens.at(divider, LIST_LEFT + 1, FIRST_ROW - 1, LIST_RIGHT - LIST_LEFT - 2, 1));
        emptyMessage = LdlibScreens.addLabel(root, Component.empty(), textColor,
                LIST_LEFT, EMPTY_TEXT_Y, LIST_RIGHT - LIST_LEFT);
        emptyMessage.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER).textShadow(true));
        addHint = LdlibScreens.addLabel(root, Component.translatable("ae2lt.tianshu.reserve.add_hint"),
                textColor, 10, 190, 188);
        overflowMessage = LdlibScreens.addLabel(root,
                Component.translatable("ae2lt.tianshu.maintenance.summary_too_large"), style.getColor(PaletteColor.ERROR).toARGB(), 10, 232, 187);
        foreground = LdlibScreens.localLayer(root, imageWidth, imageHeight);
    }

    @Override
    protected void onReturnToParent() {
        restoreParentViewMode();
        super.onReturnToParent();
    }

    private void restoreParentViewMode() {
        if (restoreMaintainableView) menu.setMaintainableView(true);
    }

    void hideSlots() {
        for (var slot : menu.slots) {
            var semantic = menu.getSlotSemantic(slot);
            if (semantic != null && semantic != Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK) setSlotsHidden(semantic, true);
        }
    }

    private void selectView(View selected) {
        if (view == selected) return;
        view = selected;
        scrollbar.setCurrentScroll(0);
        entryList.setEntries(entries());
        updateTabLabels();
        updateAddControls();
    }

    private Component tabLabel(View tab) {
        return Component.translatable(tab.translationKey)
                .withStyle(tab == view ? ChatFormatting.AQUA : ChatFormatting.WHITE);
    }

    private void updateTabLabels() {
        rulesButton.setMessage(tabLabel(View.RULES));
        reservesButton.setMessage(tabLabel(View.RESERVES));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (awaitingRuleEditor
                && menu.getMaintenanceEditorRevision() != requestedRuleEditorRevision
                && menu.getMaintenanceEditorData() != null) {
            awaitingRuleEditor = false;
            switchToScreen(new TianshuMaintenanceRuleScreen<>(
                    this, menu.getMaintenanceEditorData()));
            return;
        }
        entryList.setEntries(entries());
        updateTabLabels();
        updateAddControls();
    }

    private void updateAddControls() {
        boolean visible = view == View.RESERVES;
        reserveAmount.setVisible(visible);
        addReserveButton.visible = visible;
        var markSlot = menu.getGlobalReserveMarkSlot();
        markSlot.setActive(visible);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK, !visible);
        addReserveButton.active = visible
                && menu.isMaintenanceAvailable()
                && markedReserveKey() != null
                && parsedAddAmount() != Long.MIN_VALUE;
    }

    private static boolean validAddAmountDraft(String value) {
        if (value.isEmpty() || value.equals("-1")) return true;
        try {
            return Long.parseLong(value) > 0L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private long parsedAddAmount() {
        try {
            long value = Long.parseLong(reserveAmount.getValue());
            return value == -1L || value > 0L ? value : Long.MIN_VALUE;
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private AEKey markedReserveKey() {
        var marked = GenericStack.fromItemStack(menu.getGlobalReserveMarkSlot().getItem());
        return marked != null ? marked.what() : null;
    }

    private void addMarkedReserve() {
        var key = markedReserveKey();
        long value = parsedAddAmount();
        if (key == null || value == Long.MIN_VALUE || !menu.isMaintenanceAvailable()) return;
        menu.sendGlobalReserve(key, value, ReservedStockMatchMode.EXACT);
        menu.getGlobalReserveMarkSlot().setFilterTo(
                net.minecraft.world.item.ItemStack.EMPTY);
        reserveAmount.setValue("1");
    }

    private List<OverviewEntry> entries() {
        String needle = search.getValue().strip().toLowerCase(Locale.ROOT);
        var summaries = menu.getMaintenanceSummary();
        var merged = new LinkedHashMap<AEKey, MaintenanceSummarySyncPacket.Entry>();

        if (view == View.RULES) {
            summaries.values().stream()
                    .filter(MaintenanceSummarySyncPacket.Entry::ruleConfigured)
                    .forEach(entry -> merged.put(entry.key(), entry));
        } else {
            summaries.values().stream()
                    .filter(entry -> entry.globalReserveConfigured() || !needle.isEmpty())
                    .forEach(entry -> merged.put(entry.key(), entry));

            // Searching the reserve view doubles as the explicit "add" flow. Reuse AE2's
            // synchronized terminal repository so no second imitation inventory list is built.
            if (!needle.isEmpty()) {
                for (var networkEntry : getParent().getNetworkEntriesForMaintenance()) {
                    var key = networkEntry.getWhat();
                    if (key == null || merged.containsKey(key)) continue;
                    merged.put(key, new MaintenanceSummarySyncPacket.Entry(
                            key, false, InventoryMaintenanceStatus.IDLE,
                            Math.max(0L, networkEntry.getStoredAmount()),
                            0L, 0L, 0L, 0L, ReservedStockMatchMode.EXACT,
                            false, networkEntry.isCraftable(), false));
                }
            }
        }

        var result = new ArrayList<OverviewEntry>();
        for (var entry : merged.values()) {
            String displayName = entry.key().getDisplayName().getString();
            if (!needle.isEmpty()
                    && !displayName.toLowerCase(Locale.ROOT).contains(needle)
                    && !entry.key().getId().toString().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(new OverviewEntry(entry, displayName, view));
        }
        result.sort(Comparator.comparing(OverviewEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.summary().key().getId().toString()));
        return List.copyOf(result);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        valueColumn.setText(Component.translatable(view == View.RULES
                ? "ae2lt.tianshu.maintenance.column.target" : "ae2lt.tianshu.maintenance.column.reserve"));

        entryList.render(graphics, mouseX - leftPos, mouseY - topPos);

        boolean available = menu.isMaintenanceAvailable();
        boolean empty = entryList.isEmpty();
        emptyMessage.setVisible(!available || empty);
        if (!available) {
            emptyMessage.setText(Component.translatable("ae2lt.tianshu.maintenance.unavailable"));
            emptyMessage.getTextStyle().textColor(style.getColor(PaletteColor.ERROR).toARGB());
        } else if (empty) {
            emptyMessage.setText(Component.translatable(view == View.RULES
                    ? "ae2lt.tianshu.maintenance.empty"
                    : search.getValue().isBlank() ? "ae2lt.tianshu.reserve.empty" : "ae2lt.tianshu.reserve.no_match"));
            emptyMessage.getTextStyle().textColor(style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB());
        }
        addHint.setVisible(view == View.RESERVES && available);
        overflowMessage.setVisible(menu.isMaintenanceSummaryOverflow());
        foreground.getWidget().render(graphics, mouseX - leftPos, mouseY - topPos, 0);
    }

    private final class OverviewRow extends TianshuMaintenanceList.Row {
        private final Label name = label(CONTENT_TEXT_LEFT - LIST_LEFT, 6, 71, Horizontal.LEFT);
        private final Label stock = label(0, 6, STOCK_COLUMN_RIGHT - LIST_LEFT, Horizontal.RIGHT);
        private final Label value = label(0, 6, VALUE_COLUMN_RIGHT - LIST_LEFT, Horizontal.RIGHT);
        private OverviewRow(OverviewEntry entry) {
            super(LIST_RIGHT - LIST_LEFT, ROW_HEIGHT);
            var summary = entry.summary();
            int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
            int color = badgeColor(summary);
            add(new UIElement().style(style -> style.background(LdlibScreens.solid(() -> color))),
                    12 - LIST_LEFT, 8, 4, 4);
            item(summary.key().wrapForDisplayOrFilter(), 18 - LIST_LEFT, 2);
            text(name, Component.literal(font.plainSubstrByWidth(entry.displayName(), 70)),
                    summary.ruleConfigured() && !summary.craftable() ? style.getColor(PaletteColor.ERROR).toARGB() : textColor);
            text(stock, Component.literal(compactAmount(summary.key(), summary.storedAmount())), textColor);
            String amount = entry.view() == View.RULES ? compactAmount(summary.key(), summary.upperThreshold())
                    : formatReserve(summary.globalReserve());
            text(value, Component.literal(amount),
                    entry.view() == View.RESERVES && summary.globalReserve() != 0L ? 0x245E91 : textColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return entryList.mouseClicked(mouseX - leftPos, mouseY - topPos, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    private void openEntry(OverviewEntry entry) {
        var summary = entry.summary();
        if (entry.view() == View.RULES) {
            requestRuleEditor(summary.key());
        } else {
            switchToScreen(new GlobalReserveEditScreen<>(this, summary, variantsFor(summary.key())));
        }
    }

    private void requestRuleEditor(AEKey key) {
        if (key == null || awaitingRuleEditor) return;
        requestedRuleEditorRevision = menu.getMaintenanceEditorRevision();
        awaitingRuleEditor = true;
        menu.requestMaintenanceEditor(key);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return entryList.mouseScrolled(mouseX - leftPos, mouseY - topPos, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        var lines = entryList.tooltipTextsAt(x - leftPos, y - topPos);
        if (!lines.isEmpty()) {
            drawTooltip(graphics, x, y, lines);
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    private List<Component> entryTooltip(OverviewEntry entry) {
        var summary = entry.summary();
        var lines = new ArrayList<Component>();
        lines.add(summary.key().getDisplayName());
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.stock",
                compactAmount(summary.key(), summary.storedAmount())).withStyle(ChatFormatting.GRAY));
        if (summary.ruleConfigured()) {
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.thresholds",
                    summary.lowerThreshold(), summary.upperThreshold())
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.batch",
                    summary.amountPerJob()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.status."
                    + summary.status().name().toLowerCase(Locale.ROOT))
                    .withStyle(statusFormatting(summary.status())));
        }
        if (summary.globalReserve() != 0L) {
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.reserve",
                    formatReserve(summary.globalReserve()),
                    Component.translatable(summary.globalMode() == ReservedStockMatchMode.EXACT
                            ? "ae2lt.tianshu.reserve.exact"
                            : "ae2lt.tianshu.reserve.ignore_nbt"))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        lines.add(Component.translatable(entry.view() == View.RULES
                ? "ae2lt.tianshu.maintenance.click_edit_rule"
                : "ae2lt.tianshu.maintenance.click_edit_reserve")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int badgeColor(MaintenanceSummarySyncPacket.Entry entry) {
        if (!entry.ruleConfigured()) return entry.globalReserve() == 0L ? 0xFF8A9199 : 0xFF3E7FB5;
        return switch (InventoryMaintenanceBadge.from(entry.status())) {
            case GREEN -> 0xFF35B956;
            case YELLOW -> 0xFFE1AD2B;
            case RED -> 0xFFD34A4A;
            case GRAY -> 0xFF858B91;
        };
    }

    private static ChatFormatting statusFormatting(InventoryMaintenanceStatus status) {
        return switch (InventoryMaintenanceBadge.from(status)) {
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.GOLD;
            case RED -> ChatFormatting.RED;
            case GRAY -> ChatFormatting.GRAY;
        };
    }

    private static String compactAmount(AEKey key, long amount) {
        return key.formatAmount(Math.max(0L, amount), AmountFormat.SLOT);
    }

    private static String formatReserve(long amount) {
        return amount < 0L ? "∞" : Long.toString(amount);
    }

    private List<ReserveVariant> variantsFor(AEKey selected) {
        var identity = selected.dropSecondary();
        var variants = new LinkedHashMap<AEKey, ReserveVariant>();
        for (var networkEntry : getParent().getNetworkEntriesForMaintenance()) {
            var candidate = networkEntry.getWhat();
            if (candidate == null || !identity.equals(candidate.dropSecondary())) continue;
            var summary = menu.getMaintenanceSummaryEntry(candidate);
            boolean exactReserve = summary != null && summary.globalReserveConfigured()
                    && summary.globalMode() == ReservedStockMatchMode.EXACT;
            var variant = new ReserveVariant(candidate,
                    Math.max(0L, networkEntry.getStoredAmount()), networkEntry.isCraftable(), exactReserve);
            variants.merge(candidate, variant, (left, right) -> new ReserveVariant(
                    candidate, Math.max(left.storedAmount(), right.storedAmount()),
                    left.craftable() || right.craftable(),
                    left.exactReserveConfigured() || right.exactReserveConfigured()));
        }
        var selectedSummary = menu.getMaintenanceSummaryEntry(selected);
        variants.putIfAbsent(selected, new ReserveVariant(selected,
                selectedSummary != null ? selectedSummary.storedAmount() : 0L,
                selectedSummary != null && selectedSummary.craftable(),
                selectedSummary != null && selectedSummary.globalReserveConfigured()
                        && selectedSummary.globalMode() == ReservedStockMatchMode.EXACT));
        return variants.values().stream()
                .sorted(Comparator.comparing(
                                (ReserveVariant variant) -> variant.key().getDisplayName().getString(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(variant -> variant.key().getId().toString()))
                .toList();
    }

    private enum View {
        RULES("ae2lt.tianshu.maintenance.rules_tab"),
        RESERVES("ae2lt.tianshu.maintenance.reserves_tab");

        private final String translationKey;

        View(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record OverviewEntry(MaintenanceSummarySyncPacket.Entry summary, String displayName, View view) {
    }

    private record ReserveVariant(
            AEKey key, long storedAmount, boolean craftable, boolean exactReserveConfigured) {
    }

    private static final class GlobalReserveEditScreen<M extends MEStorageMenu & TianshuMaintenanceMenu>
            extends LdlibAeSubScreen<M, TianshuGlobalReserveScreen<M>> {
        private TianshuReserveEditorDetails details;

        @Override
        protected PageAnchor getHelpTopic() {
            return TianshuMaintenanceTermScreen.terminalHelpTopic(menu, "global-reserves");
        }

        private final MaintenanceSummarySyncPacket.Entry entry;
        private final List<ReserveVariant> variants;
        private final LdlibAe2TextField amount;
        private final LdlibAe2Button modeButton;
        private final LdlibAe2Button deleteButton;
        private final LdlibAe2Button saveButton;
        private final LdlibRowScroller scrollbar;
        private ReservedStockMatchMode mode;

        GlobalReserveEditScreen(
                TianshuGlobalReserveScreen<M> parent,
                MaintenanceSummarySyncPacket.Entry entry,
                List<ReserveVariant> variants) {
            this(parent, entry, variants,
                    new EditorState(entry.globalReserve(), entry.globalMode()));
        }

        private GlobalReserveEditScreen(
                TianshuGlobalReserveScreen<M> parent,
                MaintenanceSummarySyncPacket.Entry entry,
                List<ReserveVariant> variants,
                EditorState state) {
            super(parent, state.mode == ReservedStockMatchMode.IGNORE_SECONDARY
                    ? "/screens/tianshu_reserve_edit_expanded.json"
                    : "/screens/tianshu_reserve_edit.json");
            this.entry = entry;
            this.variants = List.copyOf(variants);
            this.mode = state.mode;

            scrollbar = LdlibRowScroller.add(widgets, "scrollbar");
            scrollbar.setCaptureMouseWheel(false);
            amount = LdlibAe2TextField.add(widgets, style, "amount");
            amount.setMaxLength(19);
            amount.setFilter(GlobalReserveEditScreen::validDraft);
            amount.setValue(Long.toString(state.amount));
            var scopeButton = LdlibAe2Button.addText(widgets, style, "scope",
                    Component.translatable("ae2lt.tianshu.reserve.global"), () -> { });
            scopeButton.active = false;
            modeButton = LdlibAe2Button.addText(widgets, style, "mode", modeLabel(), this::toggleMode);
            LdlibAe2Button.addText(widgets, style, "zero", Component.literal("0"), () -> amount.setValue("0"));
            LdlibAe2Button.addText(widgets, style, "stack", Component.literal("64"), () -> amount.setValue("64"));
            LdlibAe2Button.addText(widgets, style, "infinite", Component.literal("∞"), () -> amount.setValue("-1"));
            deleteButton = LdlibAe2Button.addText(widgets, style, "delete",
                    Component.translatable("ae2lt.tianshu.reserve.delete"), this::delete);
            saveButton = LdlibAe2Button.addText(widgets, style, "save", Component.translatable("gui.done"), this::save);
            LdlibAe2Button.addText(widgets, style, "cancel", Component.translatable("gui.cancel"), this::returnToParent);
            widgets.add("back", LdlibAe2Button.back(Component.translatable("gui.back"), this::returnToParent));
        }

        @Override
        protected void onReturnToParent() {
            if (details != null) {
                details.onRemoved();
            }
            super.onReturnToParent();
        }

        @Override
        public void removed() {
            try {
                if (details != null) {
                    details.onRemoved();
                }
            } finally {
                super.removed();
            }
        }

        @Override
        public void init() {
            if (details != null) {
                details.onRemoved();
            }
            super.init();
            details = new TianshuReserveEditorDetails(imageWidth, imageHeight, font, style, entry.key(),
                    mode == ReservedStockMatchMode.IGNORE_SECONDARY,
                    variants.stream().map(variant -> new TianshuReserveEditorDetails.Variant(
                            variant.key(), variant.storedAmount(), variant.craftable(),
                            variant.exactReserveConfigured())).toList(), scrollbar);
        }

        private static boolean validDraft(String value) {
            if (value.isEmpty() || value.equals("-1")) return true;
            try {
                return Long.parseLong(value) >= 0L;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private long parsedAmount() {
            try {
                long value = Long.parseLong(amount.getValue());
                return value >= -1L ? value : Long.MIN_VALUE;
            } catch (NumberFormatException ignored) {
                return Long.MIN_VALUE;
            }
        }

        private void toggleMode() {
            long parsed = parsedAmount();
            if (parsed == Long.MIN_VALUE) return;
            mode = mode == ReservedStockMatchMode.EXACT
                    ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
            var next = new GlobalReserveEditScreen<>(
                    getParent(), entry, variants, new EditorState(parsed, mode));
            if (details != null) {
                details.onRemoved();
            }
            releaseBackground();
            switchToScreen(next);
        }

        private Component modeLabel() {
            return Component.translatable(mode == ReservedStockMatchMode.EXACT
                    ? "ae2lt.tianshu.reserve.exact" : "ae2lt.tianshu.reserve.ignore_nbt");
        }

        private void save() {
            long parsed = parsedAmount();
            if (parsed == Long.MIN_VALUE) return;
            menu.sendGlobalReserve(entry.key(), parsed, mode);
            returnToParent();
        }

        private void delete() {
            menu.sendGlobalReserve(entry.key(), 0L, entry.globalMode());
            returnToParent();
        }

        @Override
        protected void updateBeforeRender() {
            super.updateBeforeRender();
            deleteButton.active = entry.globalReserveConfigured();
            saveButton.active = parsedAmount() != Long.MIN_VALUE;
            modeButton.setMessage(modeLabel());
        }

        @Override
        public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
            super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
            var stock = Component.translatable("ae2lt.tianshu.reserve.current_protected",
                    compactAmount(entry.key(), visibleStock()), compactAmount(entry.key(), protectedStock()));
            details.render(graphics, mouseX - leftPos, mouseY - topPos,
                    Component.literal(font.plainSubstrByWidth(stock.getString(), 187)));
        }

        private long visibleStock() {
            if (mode == ReservedStockMatchMode.EXACT) return entry.storedAmount();
            long total = 0L;
            for (var variant : variants) {
                if (variant.exactReserveConfigured()) continue;
                if (Long.MAX_VALUE - total < variant.storedAmount()) return Long.MAX_VALUE;
                total += variant.storedAmount();
            }
            return total;
        }

        private long protectedStock() {
            long stock = visibleStock();
            long configured = parsedAmount();
            if (configured == Long.MIN_VALUE || configured == 0L) return 0L;
            return configured < 0L ? stock : Math.min(stock, configured);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return details.scrollVariantsAtY(mouseY - topPos, scrollY)
                    || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        protected void renderTooltip(GuiGraphics graphics, int x, int y) {
            var lines = details.tooltipTextsAt(x - leftPos, y - topPos);
            if (!lines.isEmpty()) {
                drawTooltip(graphics, x, y, lines);
                return;
            }
            super.renderTooltip(graphics, x, y);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                returnToParent();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private record EditorState(long amount, ReservedStockMatchMode mode) {
        }
    }
}
