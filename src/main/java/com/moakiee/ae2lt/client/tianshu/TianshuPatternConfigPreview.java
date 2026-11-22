package com.moakiee.ae2lt.client.tianshu;

import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.GUI_HEIGHT;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.GUI_WIDTH;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.HEADER_HEIGHT;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.ROW_CONTENT_X_OFFSET;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.ROW_HEIGHT;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.ROW_LEFT;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.ROW_TEXTURE_WIDTH;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.ROW_TEXTURE_X;
import static com.moakiee.ae2lt.client.tianshu.TianshuPatternConfigLayout.VISIBLE_ROWS;

import java.util.List;

import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Shared virtual preview for the processing pattern popups. */
final class TianshuPatternConfigPreview {
    private static final int ICON_LEFT = ROW_LEFT + 2 + ROW_CONTENT_X_OFFSET;
    private static final int HEADER_LEFT = ROW_LEFT + 4 + ROW_CONTENT_X_OFFSET;
    private static final int NAME_LEFT = ROW_LEFT + 22 + ROW_CONTENT_X_OFFSET;
    private static final int ICON_TOP = 4;
    private static final int TEXT_TOP = 8;

    private final VirtualScrollerView<Entry> rows;
    private final ModularUI ui;
    private final int maxScroll;

    TianshuPatternConfigPreview(int textColor, int nameWidth, List<Entry> entries) {
        var root = new UIElement();
        rows = LdlibScreens.virtualRows(ROW_HEIGHT, entry -> previewRow(entry, textColor, nameWidth));
        // The original fixed-row loop does not clip overhanging models or text.
        rows.style(style -> style.overflowVisible(true));
        rows.verticalContainer.style(style -> style.overflowVisible(true));
        rows.viewPort(view -> view.style(style -> style.overflowVisible(true)));
        rows.viewContainer.style(style -> style.overflowVisible(true));
        root.addChild(LdlibScreens.at(rows, 0, HEADER_HEIGHT, GUI_WIDTH, ROW_HEIGHT * VISIBLE_ROWS));
        if (entries.isEmpty()) {
            var empty = Component.translatable("ae2lt.tianshu.pattern_config.empty");
            var label = new UIElement().setAllowHitTest(false).style(style -> style.background(
                    (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                        var font = Minecraft.getInstance().font;
                        LDLibFonts.drawText(graphics, font, empty.getVisualOrderText(),
                                x + (GUI_WIDTH - font.width(empty)) / 2, y, textColor, false);
                    }));
            root.addChild(LdlibScreens.at(label, 0, HEADER_HEIGHT + VISIBLE_ROWS * ROW_HEIGHT / 2 - 4, GUI_WIDTH, 9));
        }
        ui = LdlibScreens.localLayer(root, GUI_WIDTH, GUI_HEIGHT);
        rows.setItems(entries);
        maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
    }

    void render(GuiGraphics graphics, int localMouseX, int localMouseY, int scrollOffset) {
        LdlibScreens.scrollVirtualRows(rows, scrollOffset, maxScroll);
        ui.getWidget().render(graphics, localMouseX, localMouseY, 0);
    }

    void onRemoved() {
        if (!ui.isRemoved()) {
            ui.onRemoved();
        }
    }

    private static UIElement previewRow(Entry entry, int textColor, int nameWidth) {
        var row = new UIElement().setAllowHitTest(false).layout(l -> l.widthPercent(100).height(ROW_HEIGHT));
        switch (entry) {
            case Header header -> LdlibScreens.addLabel(row, header.text(), textColor, HEADER_LEFT, TEXT_TOP,
                    ROW_TEXTURE_X + ROW_TEXTURE_WIDTH - HEADER_LEFT)
                    .textStyle(style -> style.textWrap(TextWrap.NONE));
            case Slot slot -> {
                var icon = LdlibScreens.unadornedItemIcon(slot.key().wrapForDisplayOrFilter());
                row.addChild(LdlibScreens.at(icon, ICON_LEFT, ICON_TOP, 16, 16));
                if (nameWidth > 0) {
                    var label = new UIElement().setAllowHitTest(false).style(style -> style.background(
                            (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                                var font = Minecraft.getInstance().font;
                                var name = Language.getInstance().getVisualOrder(
                                        font.substrByWidth(slot.key().getDisplayName(), nameWidth));
                                LDLibFonts.drawText(graphics, font, name, x, y, textColor, false);
                            }));
                    row.addChild(LdlibScreens.at(label, NAME_LEFT, TEXT_TOP, nameWidth,
                            Minecraft.getInstance().font.lineHeight));
                }
            }
        }
        return row;
    }

    sealed interface Entry permits Header, Slot {
    }

    record Header(Component text) implements Entry {
    }

    record Slot(AEKey key) implements Entry {
    }
}
