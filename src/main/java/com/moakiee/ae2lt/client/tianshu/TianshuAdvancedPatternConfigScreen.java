package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.math.Size;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import appeng.menu.SlotSemantics;
import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.pedroksl.advanced_ae.AdvancedAE;
import net.pedroksl.advanced_ae.common.definitions.AAEText;

/**
 * Popup arming the next processing encode as an AdvancedAE pattern.
 * Uses AdvancedAE's direction textures and translations.
 */
final class TianshuAdvancedPatternConfigScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> implements LdlibToolbarHost {
    private static final int BUTTONS_LEFT = 34;
    private static final int BUTTONS_Y_OFFSET = 5;
    private static final int DIR_BUTTON_WIDTH = 12;
    private static final int DIR_BUTTON_HEIGHT = 14;
    private static final Direction[] DIRECTIONS = {
            null, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN
    };

    private final BackgroundLayer background = TianshuPatternConfigLayout.createBackground();
    private final LdlibRowScroller scrollbar;
    private final List<Row> rows = new ArrayList<>();
    private final DirectionButton[][] rowButtons =
            new DirectionButton[TianshuPatternConfigLayout.VISIBLE_ROWS][DIRECTIONS.length];
    private TianshuPatternConfigPreview preview;

    TianshuAdvancedPatternConfigScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_advanced_pattern_config.json");
        imageWidth = TianshuPatternConfigLayout.GUI_WIDTH;
        imageHeight = TianshuPatternConfigLayout.GUI_HEIGHT;

        widgets.add("button_back", LdlibAe2Button.back(Component.translatable("gui.back"), this::returnToParent));
        LdlibAe2Button.addText(widgets, style, "save",
                Component.translatable("ae2lt.tianshu.pattern_config.done"), this::confirm);
        scrollbar = LdlibRowScroller.add(widgets, "scrollbar");

        collectRows();
    }

    /** One row per distinct input key; AdvancedAE stores directions per key, not per slot. */
    private void collectRows() {
        var config = menu.getAdvancedEncodingConfig();
        var pending = new LinkedHashMap<AEKey, Integer>();
        var slots = menu.getProcessingInputSlots();
        for (int i = 0; i < slots.length; i++) {
            var stack = GenericStack.fromItemStack(slots[i].getItem());
            if (stack == null || stack.what() == null) continue;
            pending.putIfAbsent(stack.what(), config != null ? config.direction(i) : 0);
        }
        pending.forEach((key, direction) -> rows.add(new Row(key, direction)));
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
        for (var row : rowButtons) {
            for (var button : row) {
                if (button != null) button.dispose();
            }
        }
        if (preview != null) {
            preview.onRemoved();
        }
        background.onRemoved();
        super.init();
        var entries = rows.stream().<TianshuPatternConfigPreview.Entry>map(row ->
                new TianshuPatternConfigPreview.Slot(row.key)).toList();
        preview = new TianshuPatternConfigPreview(style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), 0, entries);
        for (int visible = 0; visible < Math.min(rowButtons.length, rows.size()); visible++) {
            int visibleIndex = visible;
            for (int column = 0; column < DIRECTIONS.length; column++) {
                int directionIndex = column;
                var button = new DirectionButton(column, () -> selectDirection(visibleIndex, directionIndex));
                button.setPosition(leftPos + BUTTONS_LEFT + TianshuPatternConfigLayout.ROW_CONTENT_X_OFFSET
                                + column * (DIR_BUTTON_WIDTH + 1),
                        topPos + TianshuPatternConfigLayout.rowY(visible) + BUTTONS_Y_OFFSET);
                rowButtons[visible][column] = addRenderableWidget(button);
            }
        }
        scrollbar.setHeight(TianshuPatternConfigLayout.SCROLLBAR_HEIGHT);
        scrollbar.setRange(0, Math.max(0, rows.size() - TianshuPatternConfigLayout.VISIBLE_ROWS), 2);
        scrollbar.setOnScrollChanged(this::updateDirections);
        updateDirections();
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void selectDirection(int visible, int column) {
        int index = scrollbar.getCurrentScroll() + visible;
        if (index < rows.size()) {
            var direction = DIRECTIONS[column];
            rows.get(index).direction = direction == null ? 0 : direction.ordinal() + 1;
            updateDirections();
        }
    }

    private void confirm() {
        if (rows.isEmpty()) {
            returnToParent();
            return;
        }
        var slots = menu.getProcessingInputSlots();
        var directions = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            var stack = GenericStack.fromItemStack(slots[i].getItem());
            if (stack == null || stack.what() == null) continue;
            for (var row : rows) {
                if (row.key.equals(stack.what())) {
                    directions[i] = row.direction;
                    break;
                }
            }
        }
        menu.armAdvancedEncoding(new ProcessingPatternEncodingType.AdvancedConfig(directions));
        returnToParent();
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        preview.render(graphics, mouseX - leftPos, mouseY - topPos, scrollbar.getCurrentScroll());
    }

    private void updateDirections() {
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < Math.min(rowButtons.length, rows.size()); visible++) {
            var buttons = rowButtons[visible];
            int highlighted = columnForDirection(rows.get(scroll + visible).direction);
            for (int col = 0; col < buttons.length; col++) {
                buttons[col].highlighted = col == highlighted;
            }
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

    private static int columnForDirection(int direction) {
        for (int column = 1; column < DIRECTIONS.length; column++) {
            if (DIRECTIONS[column].ordinal() + 1 == direction) return column;
        }
        return 0;
    }

    private static final class DirectionButton extends LdlibAe2Button {
        private boolean highlighted;

        private DirectionButton(int column, Runnable action) {
            super(DIR_BUTTON_WIDTH, DIR_BUTTON_HEIGHT, directionLabel(column), action);
            var direction = DIRECTIONS[column];
            String name = direction == null ? "any" : direction.getName();
            var normal = texture(name + "_button.png");
            var selected = texture(name + "_button_selected.png");
            var face = IGuiTexture.dynamic(() -> highlighted ? selected : normal);
            control.buttonStyle(style -> style.baseTexture(face).hoverTexture(face).pressedTexture(face));
            setTooltip(Tooltip.create(getMessage()));
            setMessage(Component.empty());
        }

        private static IGuiTexture texture(String file) {
            return new LdlibStyleSprite(AdvancedAE.makeId("textures/guis/" + file), Size.of(16, 16),
                    0, 0, DIR_BUTTON_WIDTH, DIR_BUTTON_HEIGHT).full();
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return false;
        }
    }

    private static Component directionLabel(int index) {
        return Component.translatable(switch (index) {
            case 1 -> AAEText.NorthButton.getTranslationKey();
            case 2 -> AAEText.EastButton.getTranslationKey();
            case 3 -> AAEText.SouthButton.getTranslationKey();
            case 4 -> AAEText.WestButton.getTranslationKey();
            case 5 -> AAEText.UpButton.getTranslationKey();
            case 6 -> AAEText.DownButton.getTranslationKey();
            default -> AAEText.AnyButton.getTranslationKey();
        });
    }

    /** direction: 0 = any side, otherwise Direction.ordinal() + 1. */
    private static final class Row {
        final AEKey key;
        int direction;

        Row(AEKey key, int direction) {
            this.key = key;
            this.direction = direction;
        }
    }
}
