package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibAeSubScreen;
import guideme.PageAnchor;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Checkbox;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2TextField;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import appeng.menu.me.common.MEStorageMenu;
import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/** Shift-middle-click inventory-maintenance rule editor, backed by the menu-bound Tianshu. */
public final class TianshuMaintenanceRuleScreen<M extends MEStorageMenu & TianshuMaintenanceMenu>
        extends LdlibAeSubScreen<M, AEBaseScreen<M>> {
    private ModularUI foreground;
    private Label topologyCaption;
    private List<Label> topologyColumns;
    private TianshuMaintenanceList<ReserveDraft> entryList;

    @Override
    protected PageAnchor getHelpTopic() {
        return TianshuMaintenanceTermScreen.terminalHelpTopic(menu, "inventory-maintenance");
    }

    private static final int LIST_LEFT = 9;
    private static final int LIST_RIGHT = 187;
    private static final int FIRST_ROW_Y = 134;
    private static final int ROW_HEIGHT = 17;
    private static final int VISIBLE_ROWS = 3;

    private final Draft draft;
    private final LdlibAe2TextField lower;
    private final LdlibAe2TextField upper;
    private final LdlibAe2TextField perJob;
    private final LdlibAe2Checkbox enabled;
    private final LdlibRowScroller scrollbar;
    private final LdlibAe2Button saveButton;
    private final LdlibAe2Button deleteButton;
    private final LdlibAe2Button checkButton;
    private final LdlibAe2Button cancelJobButton;
    private boolean deleteArmed;

    public TianshuMaintenanceRuleScreen(
            AEBaseScreen<M> parent, MaintenanceEditorData data) {
        super(parent, "/screens/tianshu_maintenance_rule.json");
        draft = new Draft(data);

        lower = numberField("lower", data.lowerThreshold());
        upper = numberField("upper", data.upperThreshold());
        perJob = numberField("perJob", data.amountPerJob());
        enabled = LdlibAe2Checkbox.add(widgets, style, "enabled",
                Component.translatable("ae2lt.tianshu.maintenance.enabled"), null);
        enabled.setSelected(data.enabled());

        scrollbar = LdlibRowScroller.add(widgets, "scrollbar");
        scrollbar.setCaptureMouseWheel(false);
        checkButton = LdlibAe2Button.addText(widgets, style, "check",
                Component.translatable("ae2lt.tianshu.maintenance.check_now"), this::checkNow);
        cancelJobButton = LdlibAe2Button.addText(widgets, style, "cancelJob",
                Component.translatable("ae2lt.tianshu.maintenance.cancel_job"), this::cancelJob);
        deleteButton = LdlibAe2Button.addText(widgets, style, "delete",
                Component.translatable("ae2lt.tianshu.maintenance.delete"), this::delete);
        LdlibAe2Button.addText(widgets, style, "cancel", Component.translatable("gui.cancel"), this::returnToParent);
        saveButton = LdlibAe2Button.addText(widgets, style, "save", Component.translatable("gui.done"), this::save);
        widgets.add("back", LdlibAe2Button.back(Component.translatable("gui.back"), this::returnToParent));
    }

    @Override
    public void init() {
        // Preserve the parent terminal's slot positions before this sub-screen hides them.
        hideTerminalSlots();
        super.init();
        var bounds = new Rect2i(LIST_LEFT, FIRST_ROW_Y, LIST_RIGHT - LIST_LEFT, ROW_HEIGHT * VISIBLE_ROWS);
        entryList = new TianshuMaintenanceList<>(imageWidth, imageHeight, bounds, ROW_HEIGHT, scrollbar,
                TopologyRow::new, this::openEntry, this::entryTooltip);
        entryList.setEntries(draft.reserves);
        initForeground();
    }

    private void initForeground() {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var root = new UIElement();
        LdlibScreens.addLabel(root, Component.translatable(draft.data.ruleId() == null
                        ? "ae2lt.tianshu.maintenance.create_title" : "ae2lt.tianshu.maintenance.edit_title"),
                textColor, 10, 9, 158);
        var target = draft.data.target();
        var icon = LdlibScreens.unadornedItemIcon(target.wrapForDisplayOrFilter());
        root.addChild(LdlibScreens.at(icon, 16, 32, 16, 16));
        LdlibScreens.addLabel(root, Component.literal(font.plainSubstrByWidth(target.getDisplayName().getString(), 147)),
                textColor, 39, 29, 148);
        var stockAndStatus = Component.translatable("ae2lt.tianshu.maintenance.stock_and_status",
                compactAmount(target, draft.data.currentStock()), Component.translatable(statusKey(draft.data.status())));
        LdlibScreens.addLabel(root, Component.literal(font.plainSubstrByWidth(stockAndStatus.getString(), 147)),
                0xFF000000 | statusColor(draft.data.status()), 39, 41, 148);
        LdlibScreens.addLabel(root, Component.translatable("ae2lt.tianshu.maintenance.lower"),
                textColor, 10, 57, 61);
        LdlibScreens.addLabel(root, Component.translatable("ae2lt.tianshu.maintenance.upper"),
                textColor, 73, 57, 62);
        LdlibScreens.addLabel(root, Component.translatable("ae2lt.tianshu.maintenance.batch"),
                textColor, 137, 57, 60);
        topologyCaption = LdlibScreens.addLabel(root, Component.empty(), textColor, 10, 116, 181);
        topologyCaption.textStyle(s -> s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.NONE));
        topologyColumns = List.of(topologyColumn(root, "stock", 117, textColor),
                topologyColumn(root, "global_short", 151, textColor),
                topologyColumn(root, "rule_short", 179, textColor));
        foreground = LdlibScreens.localLayer(root, imageWidth, imageHeight);
    }

    private Label topologyColumn(UIElement root, String key, int center, int textColor) {
        var caption = Component.translatable("ae2lt.tianshu.maintenance.column." + key);
        int width = font.width(caption);
        return LdlibScreens.addLabel(root, caption, textColor, center - width / 2, 116, width + 1);
    }

    private LdlibAe2TextField numberField(String id, long initialValue) {
        var field = LdlibAe2TextField.add(widgets, style, id);
        field.setMaxLength(19);
        field.setFilter(TianshuMaintenanceRuleScreen::isNonNegativeIntegerDraft);
        field.setValue(Long.toString(initialValue));
        return field;
    }

    private static boolean isNonNegativeIntegerDraft(String value) {
        if (value.isEmpty()) return true;
        try {
            return Long.parseLong(value) >= 0L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void hideTerminalSlots() {
        for (var slot : menu.slots) {
            var semantic = menu.getSlotSemantic(slot);
            if (semantic != null) setSlotsHidden(semantic, true);
        }
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        // This screen renders its target and topology itself. The shared menu's
        // processing-pattern draft slots must never bleed through this sub-screen,
        // even if another screen or compatibility mod repositions them.
    }

    @Override
    protected boolean isHovering(Slot slot, double mouseX, double mouseY) {
        return false;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        saveButton.active = validationError() == null;
        deleteButton.visible = draft.data.ruleId() != null;
        deleteButton.active = draft.data.ruleId() != null;
        deleteButton.setMessage(Component.translatable(deleteArmed
                ? "ae2lt.tianshu.maintenance.confirm_delete"
                : "ae2lt.tianshu.maintenance.delete"));

        boolean existing = draft.data.ruleId() != null;
        checkButton.visible = existing;
        checkButton.active = existing && draft.data.status() != InventoryMaintenanceStatus.CRAFTING
                && draft.data.status() != InventoryMaintenanceStatus.CANCELLING;
        cancelJobButton.visible = existing;
        cancelJobButton.active = existing && (draft.data.status() == InventoryMaintenanceStatus.CRAFTING
                || draft.data.status() == InventoryMaintenanceStatus.CANCELLING);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        Component error = validationError();
        boolean topology = error == null && !draft.data.recoveryPage();
        topologyCaption.setText(error != null
                ? Component.literal(font.plainSubstrByWidth(error.getString(), 181))
                : Component.translatable(topology
                        ? "ae2lt.tianshu.maintenance.topology" : "ae2lt.tianshu.maintenance.recovery_page"));
        topologyCaption.getTextStyle().textColor(style.getColor(
                topology ? PaletteColor.DEFAULT_TEXT_COLOR : PaletteColor.ERROR).toARGB());
        for (var column : topologyColumns) column.setVisible(topology);

        entryList.render(graphics, mouseX - leftPos, mouseY - topPos);
        foreground.getWidget().render(graphics, mouseX - leftPos, mouseY - topPos, 0);
    }

    private static Component reserveText(long amount, ReservedStockMatchMode mode) {
        String value = amount < 0L ? "∞" : Long.toString(amount);
        return Component.literal(value + (mode == ReservedStockMatchMode.IGNORE_SECONDARY ? "*" : ""));
    }

    private final class TopologyRow extends TianshuMaintenanceList.Row {
        private final Label name = label(31 - LIST_LEFT, 4, 70, Horizontal.LEFT);
        private final Label stock = label(0, 4, 125 - LIST_LEFT, Horizontal.RIGHT);
        private final Label global = label(0, 4, 158 - LIST_LEFT, Horizontal.RIGHT);
        private final Label rule = label(0, 4, 185 - LIST_LEFT, Horizontal.RIGHT);

        private TopologyRow(ReserveDraft entry) {
            super(LIST_RIGHT - LIST_LEFT, ROW_HEIGHT);
            int indent = Math.min(4, entry.depth) * 4;
            int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
            item(entry.key.wrapForDisplayOrFilter(), 11 - LIST_LEFT + indent, 1);
            name.layout(layout -> layout.left(31 - LIST_LEFT + indent));
            text(name, Component.literal(font.plainSubstrByWidth(entry.key.getDisplayName().getString(),
                    69 - indent)), entry.craftable ? textColor : style.getColor(PaletteColor.ERROR).toARGB());
            text(stock, Component.literal(compactAmount(entry.key, entry.storedAmount)), textColor);
            text(global, reserveText(entry.globalAmount, entry.globalMode), entry.globalAmount == 0L ? textColor : 0x245E91);
            text(rule, reserveText(entry.ruleAmount, entry.ruleMode), entry.ruleAmount == 0L ? textColor : 0x794D91);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return entryList.mouseClicked(mouseX - leftPos, mouseY - topPos, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    private void openEntry(ReserveDraft reserve) {
        switchToScreen(new ReserveEditorScreen<>(this, reserve,
                draft.data.target().equals(reserve.key) ? draft.data.variants() : List.of()));
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

    private List<Component> entryTooltip(ReserveDraft reserve) {
        var lines = new ArrayList<Component>();
        lines.add(reserve.key.getDisplayName());
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.stock",
                compactAmount(reserve.key, reserve.storedAmount)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.reserve.global_value",
                formatReserve(reserve.globalAmount), modeLabel(reserve.globalMode))
                .withStyle(ChatFormatting.BLUE));
        lines.add(Component.translatable("ae2lt.tianshu.reserve.rule_value",
                formatReserve(reserve.ruleAmount), modeLabel(reserve.ruleMode))
                .withStyle(ChatFormatting.DARK_PURPLE));
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.click_edit_reserve")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private Component validationError() {
        Long parsedLower = parse(lower);
        Long parsedUpper = parse(upper);
        Long parsedPerJob = parse(perJob);
        if (parsedLower == null || parsedUpper == null || parsedPerJob == null) {
            return Component.translatable("ae2lt.tianshu.maintenance.error.number");
        }
        if (parsedUpper < parsedLower) {
            return Component.translatable("ae2lt.tianshu.maintenance.error.thresholds");
        }
        if (parsedPerJob <= 0L) {
            return Component.translatable("ae2lt.tianshu.maintenance.error.batch");
        }
        return null;
    }

    private void save() {
        if (validationError() != null) return;
        long parsedLower = parse(lower);
        long parsedUpper = parse(upper);
        long parsedPerJob = parse(perJob);
        var edits = draft.reserves.stream().map(entry -> new SaveMaintenanceRulePacket.ReserveEdit(
                entry.key, entry.globalAmount, entry.globalMode,
                entry.ruleAmount, entry.ruleMode)).toList();
        menu.sendMaintenanceSave(new SaveMaintenanceRulePacket(
                menu.containerId, menu.getTianshuSelectionRevision(), draft.data.target(),
                draft.data.ruleId(), false, parsedLower, parsedUpper, parsedPerJob,
                enabled.isSelected(), edits));
        returnToParent();
    }

    private void delete() {
        if (draft.data.ruleId() == null) return;
        if (!deleteArmed) {
            deleteArmed = true;
            return;
        }
        menu.sendMaintenanceSave(new SaveMaintenanceRulePacket(
                menu.containerId, menu.getTianshuSelectionRevision(), draft.data.target(),
                draft.data.ruleId(), true, 0L, 1L, 1L, false, List.of()));
        returnToParent();
    }

    private void checkNow() {
        if (draft.data.ruleId() == null) return;
        menu.runMaintenanceAction(draft.data.ruleId(), false);
        returnToParent();
    }

    private void cancelJob() {
        if (draft.data.ruleId() == null) return;
        menu.runMaintenanceAction(draft.data.ruleId(), true);
        returnToParent();
    }

    private static Long parse(LdlibAe2TextField field) {
        try {
            return Long.parseLong(field.getValue());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String compactAmount(AEKey key, long amount) {
        return key.formatAmount(Math.max(0L, amount), AmountFormat.SLOT);
    }

    private static String formatReserve(long amount) {
        return amount < 0L ? "∞" : Long.toString(amount);
    }

    private static Component modeLabel(ReservedStockMatchMode mode) {
        return Component.translatable(mode == ReservedStockMatchMode.EXACT
                ? "ae2lt.tianshu.reserve.exact" : "ae2lt.tianshu.reserve.ignore_nbt");
    }

    private static String statusKey(InventoryMaintenanceStatus status) {
        return "ae2lt.tianshu.maintenance.status." + status.name().toLowerCase(Locale.ROOT);
    }

    private static int statusColor(InventoryMaintenanceStatus status) {
        return switch (InventoryMaintenanceBadge.from(status)) {
            case GREEN -> 0x2B8E43;
            case YELLOW -> 0x9B6A16;
            case RED -> 0xB2353B;
            case GRAY -> 0x6E7379;
        };
    }

    private static final class Draft {
        final MaintenanceEditorData data;
        final List<ReserveDraft> reserves = new ArrayList<>();

        Draft(MaintenanceEditorData data) {
            this.data = data;
            for (var entry : data.topology()) reserves.add(new ReserveDraft(entry));
        }
    }

    private static final class ReserveDraft {
        final AEKey key;
        final int depth;
        final boolean craftable;
        final long storedAmount;
        long globalAmount;
        ReservedStockMatchMode globalMode;
        long ruleAmount;
        ReservedStockMatchMode ruleMode;

        ReserveDraft(MaintenanceEditorData.TopologyEntry entry) {
            key = entry.key();
            depth = entry.depth();
            craftable = entry.craftable();
            storedAmount = entry.storedAmount();
            globalAmount = entry.globalReserve();
            globalMode = entry.globalMode();
            ruleAmount = entry.ruleReserve();
            ruleMode = entry.ruleMode();
        }
    }

    /** Second-level editor. Values are committed to the parent draft only when Done is pressed. */
    private static final class ReserveEditorScreen<M extends MEStorageMenu & TianshuMaintenanceMenu>
            extends LdlibAeSubScreen<M, TianshuMaintenanceRuleScreen<M>> {
        private TianshuReserveEditorDetails details;

        @Override
        protected PageAnchor getHelpTopic() {
            return TianshuMaintenanceTermScreen.terminalHelpTopic(menu,
                    global ? "global-reserves" : "inventory-maintenance");
        }

        private final ReserveDraft reserve;
        private final List<MaintenanceEditorData.VariantEntry> variants;
        private final LdlibAe2TextField amount;
        private final LdlibAe2Button scopeButton;
        private final LdlibAe2Button modeButton;
        private final LdlibAe2Button deleteButton;
        private final LdlibAe2Button saveButton;
        private final LdlibRowScroller scrollbar;
        private boolean global = true;
        private long globalAmount;
        private ReservedStockMatchMode globalMode;
        private long ruleAmount;
        private ReservedStockMatchMode ruleMode;

        ReserveEditorScreen(
                TianshuMaintenanceRuleScreen<M> parent,
                ReserveDraft reserve,
                List<MaintenanceEditorData.VariantEntry> variants) {
            this(parent, reserve, variants, new EditorState(
                    true, reserve.globalAmount, reserve.globalMode,
                    reserve.ruleAmount, reserve.ruleMode));
        }

        private ReserveEditorScreen(
                TianshuMaintenanceRuleScreen<M> parent,
                ReserveDraft reserve,
                List<MaintenanceEditorData.VariantEntry> variants,
                EditorState state) {
            super(parent, state.selectedMode() == ReservedStockMatchMode.IGNORE_SECONDARY
                    ? "/screens/tianshu_reserve_edit_expanded.json"
                    : "/screens/tianshu_reserve_edit.json");
            this.reserve = reserve;
            this.variants = List.copyOf(variants);
            global = state.global;
            globalAmount = state.globalAmount;
            globalMode = state.globalMode;
            ruleAmount = state.ruleAmount;
            ruleMode = state.ruleMode;

            scrollbar = LdlibRowScroller.add(widgets, "scrollbar");
            scrollbar.setCaptureMouseWheel(false);
            amount = LdlibAe2TextField.add(widgets, style, "amount");
            amount.setMaxLength(19);
            amount.setFilter(ReserveEditorScreen::validDraft);
            amount.setValue(formatReserve(globalAmount).replace("∞", "-1"));
            scopeButton = LdlibAe2Button.addText(widgets, style, "scope", scopeLabel(), this::toggleScope);
            modeButton = LdlibAe2Button.addText(widgets, style, "mode", modeLabel(), this::toggleMode);
            LdlibAe2Button.addText(widgets, style, "zero", Component.literal("0"), () -> amount.setValue("0"));
            LdlibAe2Button.addText(widgets, style, "stack", Component.literal("64"), () -> amount.setValue("64"));
            LdlibAe2Button.addText(widgets, style, "infinite", Component.literal("∞"), () -> amount.setValue("-1"));
            deleteButton = LdlibAe2Button.addText(widgets, style, "delete",
                    Component.translatable("ae2lt.tianshu.reserve.delete"), this::delete);
            LdlibAe2Button.addText(widgets, style, "cancel", Component.translatable("gui.cancel"), this::returnToParent);
            saveButton = LdlibAe2Button.addText(widgets, style, "save", Component.translatable("gui.done"), this::save);
            widgets.add("back", LdlibAe2Button.back(Component.translatable("gui.back"), this::returnToParent));
        }

        @Override
        public void renderSlot(GuiGraphics graphics, Slot slot) {
            // The reserve editor also uses only manually rendered item previews.
        }

        @Override
        protected boolean isHovering(Slot slot, double mouseX, double mouseY) {
            return false;
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
            details = new TianshuReserveEditorDetails(imageWidth, imageHeight, font, style, reserve.key,
                    (global ? globalMode : ruleMode) == ReservedStockMatchMode.IGNORE_SECONDARY,
                    variants.stream().map(variant -> new TianshuReserveEditorDetails.Variant(
                            variant.key(), variant.storedAmount(), variant.craftable(), false)).toList(), scrollbar);
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

        private void storeVisibleDraft() {
            long parsed = parsedAmount();
            if (parsed == Long.MIN_VALUE) return;
            if (global) globalAmount = parsed;
            else ruleAmount = parsed;
        }

        private void toggleScope() {
            if (parsedAmount() == Long.MIN_VALUE) return;
            storeVisibleDraft();
            global = !global;
            reopen();
        }

        private void toggleMode() {
            if (parsedAmount() == Long.MIN_VALUE) return;
            storeVisibleDraft();
            if (global) {
                globalMode = globalMode == ReservedStockMatchMode.EXACT
                        ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
            } else {
                ruleMode = ruleMode == ReservedStockMatchMode.EXACT
                        ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
            }
            reopen();
        }

        private void reopen() {
            var next = new ReserveEditorScreen<>(getParent(), reserve, variants,
                    new EditorState(global, globalAmount, globalMode, ruleAmount, ruleMode));
            if (details != null) {
                details.onRemoved();
            }
            releaseBackground();
            switchToScreen(next);
        }

        private Component scopeLabel() {
            return Component.translatable(global
                    ? "ae2lt.tianshu.reserve.global" : "ae2lt.tianshu.reserve.rule");
        }

        private Component modeLabel() {
            var mode = global ? globalMode : ruleMode;
            return TianshuMaintenanceRuleScreen.modeLabel(mode);
        }

        private void save() {
            if (parsedAmount() == Long.MIN_VALUE) return;
            storeVisibleDraft();
            reserve.globalAmount = globalAmount;
            reserve.globalMode = globalMode;
            reserve.ruleAmount = ruleAmount;
            reserve.ruleMode = ruleMode;
            returnToParent();
        }

        private void delete() {
            if (global) globalAmount = 0L;
            else ruleAmount = 0L;
            reserve.globalAmount = globalAmount;
            reserve.globalMode = globalMode;
            reserve.ruleAmount = ruleAmount;
            reserve.ruleMode = ruleMode;
            returnToParent();
        }

        @Override
        protected void updateBeforeRender() {
            super.updateBeforeRender();
            deleteButton.active = (global ? globalAmount : ruleAmount) != 0L;
            saveButton.active = parsedAmount() != Long.MIN_VALUE;
            scopeButton.setMessage(scopeLabel());
            modeButton.setMessage(modeLabel());
        }

        @Override
        public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
            super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
            details.render(graphics, mouseX - leftPos, mouseY - topPos,
                    Component.translatable("ae2lt.tianshu.reserve.current_stock",
                            compactAmount(reserve.key, reserve.storedAmount)));
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

        private record EditorState(
                boolean global,
                long globalAmount,
                ReservedStockMatchMode globalMode,
                long ruleAmount,
                ReservedStockMatchMode ruleMode) {
            ReservedStockMatchMode selectedMode() {
                return global ? globalMode : ruleMode;
            }
        }
    }
}
