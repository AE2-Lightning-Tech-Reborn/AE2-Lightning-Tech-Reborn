package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Popup arming the next processing encode as an overload pattern with per-slot match modes. */
final class TianshuOverloadPatternConfigScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> implements LdlibToolbarHost {
    private static final int TOGGLE_LEFT = 120;
    private static final int TOGGLE_Y_OFFSET = 4;
    private static final int TOGGLE_WIDTH = 46;
    private static final int TOGGLE_HEIGHT = 16;

    private final BackgroundLayer background = TianshuPatternConfigLayout.createBackground();
    private final LdlibRowScroller scrollbar;
    private final List<Row> rows = new ArrayList<>();
    private final LdlibAe2Button[] toggleButtons = new LdlibAe2Button[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private TianshuPatternConfigPreview preview;

    TianshuOverloadPatternConfigScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_overload_pattern_config.json");
        imageWidth = TianshuPatternConfigLayout.GUI_WIDTH;
        imageHeight = TianshuPatternConfigLayout.GUI_HEIGHT;

        widgets.add("button_back", LdlibAe2Button.back(Component.translatable("gui.back"), this::returnToParent));
        LdlibAe2Button.addText(widgets, style, "save",
                Component.translatable("ae2lt.tianshu.pattern_config.done"), this::confirm);
        scrollbar = LdlibRowScroller.add(widgets, "scrollbar");

        collectRows();
    }

    private void collectRows() {
        var config = menu.getOverloadEncodingConfig();
        var inputRows = new ArrayList<Row>();
        var inputs = menu.getProcessingInputSlots();
        for (int i = 0; i < inputs.length; i++) {
            var stack = GenericStack.fromItemStack(inputs[i].getItem());
            if (stack == null || stack.what() == null) continue;
            inputRows.add(Row.slot(false, i, stack,
                    config != null && config.isInputIdOnly(i)));
        }
        var outputRows = new ArrayList<Row>();
        var outputs = menu.getProcessingOutputSlots();
        for (int i = 0; i < outputs.length; i++) {
            var stack = GenericStack.fromItemStack(outputs[i].getItem());
            if (stack == null || stack.what() == null) continue;
            outputRows.add(Row.slot(true, i, stack,
                    config != null && config.isOutputIdOnly(i)));
        }
        if (!inputRows.isEmpty()) {
            rows.add(Row.header(Component.translatable("ae2lt.tianshu.overload_config.inputs")));
            rows.addAll(inputRows);
        }
        if (!outputRows.isEmpty()) {
            rows.add(Row.header(Component.translatable("ae2lt.tianshu.overload_config.outputs")));
            rows.addAll(outputRows);
        }
    }

    @Override
    protected void onReturnToParent() {
        if (preview != null) {
            preview.onRemoved();
        }
        background.onRemoved();
        super.onReturnToParent();
    }

    @Override
    public void removed() {
        try {
            if (preview != null) {
                preview.onRemoved();
            }
        } finally {
            try {
                background.onRemoved();
            } finally {
                super.removed();
            }
        }
    }

    @Override
    protected void init() {
        for (var button : toggleButtons) {
            if (button != null) button.dispose();
        }
        if (preview != null) {
            preview.onRemoved();
        }
        background.onRemoved();
        super.init();
        var entries = rows.stream().<TianshuPatternConfigPreview.Entry>map(row -> row.header == null
                ? new TianshuPatternConfigPreview.Slot(row.stack.what())
                : new TianshuPatternConfigPreview.Header(row.header)).toList();
        preview = new TianshuPatternConfigPreview(style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(),
                TOGGLE_LEFT - TianshuPatternConfigLayout.ROW_LEFT - 24
                        - TianshuPatternConfigLayout.ROW_CONTENT_X_OFFSET,
                entries);
        var modeTooltip = Tooltip.create(
                Component.translatable("ae2lt.tianshu.overload_config.mode.tooltip"));
        for (int i = 0; i < TianshuPatternConfigLayout.VISIBLE_ROWS; i++) {
            int visibleIndex = i;
            var button = LdlibAe2Button.text(TOGGLE_WIDTH, TOGGLE_HEIGHT,
                    Component.empty(), () -> toggleRow(visibleIndex));
            button.setTooltip(modeTooltip);
            button.setPosition(leftPos + TOGGLE_LEFT,
                    topPos + TianshuPatternConfigLayout.rowY(i) + TOGGLE_Y_OFFSET);
            toggleButtons[i] = addRenderableWidget(button);
        }
        scrollbar.setHeight(TianshuPatternConfigLayout.SCROLLBAR_HEIGHT);
        scrollbar.setRange(0, Math.max(0, rows.size() - TianshuPatternConfigLayout.VISIBLE_ROWS), 2);
        scrollbar.setOnScrollChanged(this::updateToggles);
        updateToggles();
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void toggleRow(int visibleIndex) {
        int index = scrollbar.getCurrentScroll() + visibleIndex;
        if (index < 0 || index >= rows.size()) return;
        var row = rows.get(index);
        if (row.header != null) return;
        row.idOnly = !row.idOnly;
        updateToggles();
    }

    private void confirm() {
        var inputIdOnly = new ArrayList<Integer>();
        var outputIdOnly = new ArrayList<Integer>();
        boolean hasSlots = false;
        for (var row : rows) {
            if (row.header != null) continue;
            hasSlots = true;
            if (!row.idOnly) continue;
            (row.output ? outputIdOnly : inputIdOnly).add(row.slotIndex);
        }
        if (hasSlots) {
            menu.armOverloadEncoding(new ProcessingPatternEncodingType.OverloadConfig(
                    toArray(inputIdOnly), toArray(outputIdOnly)));
        }
        returnToParent();
    }

    private static int[] toArray(List<Integer> values) {
        var result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        preview.render(graphics, mouseX - leftPos, mouseY - topPos, scrollbar.getCurrentScroll());
    }

    private void updateToggles() {
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            var button = toggleButtons[visible];
            if (index >= rows.size()) {
                button.visible = false;
                continue;
            }
            var row = rows.get(index);
            if (row.header != null) {
                button.visible = false;
                continue;
            }
            button.setMessage(Component.translatable(row.idOnly
                    ? "ae2lt.tianshu.overload_config.id_only"
                    : "ae2lt.tianshu.overload_config.strict"));
            button.visible = true;
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        background.render(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && rows.size() > TianshuPatternConfigLayout.VISIBLE_ROWS) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() + (deltaY > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private static final class Row {
        @Nullable final Component header;
        final boolean output;
        final int slotIndex;
        final GenericStack stack;
        boolean idOnly;

        private Row(@Nullable Component header, boolean output, int slotIndex,
                    GenericStack stack, boolean idOnly) {
            this.header = header;
            this.output = output;
            this.slotIndex = slotIndex;
            this.stack = stack;
            this.idOnly = idOnly;
        }

        static Row header(Component text) {
            return new Row(text, false, -1, null, false);
        }

        static Row slot(boolean output, int slotIndex, GenericStack stack, boolean idOnly) {
            return new Row(null, output, slotIndex, stack, idOnly);
        }
    }
}
