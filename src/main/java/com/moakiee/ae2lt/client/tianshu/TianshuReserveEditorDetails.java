package com.moakiee.ae2lt.client.tianshu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.moakiee.ae2lt.client.ldlib.LdlibText;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.ButtonToolTips;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared display for the reserve editors, including the optional item variant list. */
final class TianshuReserveEditorDetails {
    private static final int VARIANT_LEFT = 9;
    private static final int VARIANT_WIDTH = 178;
    private static final int VARIANT_TOP = 134;
    private static final int VARIANT_ROW_HEIGHT = 17;
    private static final int VISIBLE_VARIANTS = 3;
    private static final int VARIANT_HEIGHT = VISIBLE_VARIANTS * VARIANT_ROW_HEIGHT;

    private final ModularUI ui;
    private final Label stock;
    private final LdlibRowScroller scrollbar;
    private final VirtualScrollerView<Variant> variantList;
    private final List<Variant> displayedVariants;
    private final int maxScroll;

    TianshuReserveEditorDetails(int width, int height, Font font, ScreenStyle style, AEKey key, boolean expanded,
            List<Variant> variants, LdlibRowScroller scrollbar) {
        this.scrollbar = scrollbar;
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var root = new UIElement();
        addLabel(root, Component.translatable("ae2lt.tianshu.reserve.title"), textColor, 10, 9, 158);
        var icon = LdlibScreens.unadornedItemIcon(key.wrapForDisplayOrFilter());
        root.addChild(LdlibScreens.at(icon, 16, 32, 16, 16));
        addPlainText(root, font, () -> font.plainSubstrByWidth(key.getDisplayName().getString(), 147),
                textColor, 39, 35, 148);
        stock = addLabel(root, Component.empty(), textColor, 10, 83, 187);
        var amountLabel = Component.translatable("ae2lt.tianshu.reserve.amount");
        addPlainText(root, font, () -> font.plainSubstrByWidth(amountLabel.getString(), 64),
                textColor, 10, 99, 65);
        boolean showVariants = expanded && !variants.isEmpty();
        displayedVariants = showVariants ? List.copyOf(variants) : List.of();
        scrollbar.setVisible(showVariants);
        maxScroll = Math.max(0, variants.size() - VISIBLE_VARIANTS);
        scrollbar.setRange(0, maxScroll, 1);
        variantList = showVariants ? LdlibScreens.virtualRows(VARIANT_ROW_HEIGHT,
                variant -> variantRow(font, variant, textColor)) : null;
        if (expanded) {
            if (showVariants) {
                // The native three-row loops do not clip overhanging models or text.
                variantList.style(uiStyle -> uiStyle.overflowVisible(true));
                variantList.verticalContainer.style(uiStyle -> uiStyle.overflowVisible(true));
                variantList.viewPort(view -> view.style(uiStyle -> uiStyle.overflowVisible(true)));
                variantList.viewContainer.style(uiStyle -> uiStyle.overflowVisible(true));
                addLabel(root, Component.translatable("ae2lt.tianshu.reserve.variant_title"),
                        textColor, 10, 116, 187);
                root.addChild(LdlibScreens.at(variantList, VARIANT_LEFT, VARIANT_TOP, VARIANT_WIDTH, VARIANT_HEIGHT));
            } else {
                var empty = Component.translatable("ae2lt.tianshu.reserve.variant_empty");
                var label = new UIElement().setAllowHitTest(false).style(uiStyle -> uiStyle.background(
                        (graphics, mouseX, mouseY, x, y, drawWidth, drawHeight, ticks) ->
                                LDLibFonts.drawText(graphics, font, empty.getVisualOrderText(),
                                        x + 98 - font.width(empty) / 2, y, textColor, false)));
                root.addChild(LdlibScreens.at(label, 0, 156, width, font.lineHeight));
            }
        }
        ui = LdlibScreens.localLayer(root, width, height);
        if (variantList != null) {
            variantList.setItems(displayedVariants);
        }
    }

    void render(GuiGraphics graphics, int x, int y, Component stockText) {
        stock.setText(stockText);
        if (variantList != null) {
            LdlibScreens.scrollVirtualRows(variantList, scrollbar.getCurrentScroll(), maxScroll);
        }
        ui.getWidget().render(graphics, x, y, 0);
    }

    void onRemoved() {
        if (!ui.isRemoved()) {
            ui.onRemoved();
        }
    }

    boolean scrollVariantsAtY(double y, double scrollY) {
        if (variantList == null || y < VARIANT_TOP || y >= VARIANT_TOP + VARIANT_HEIGHT) {
            return false;
        }
        scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() - (int) Math.signum(scrollY));
        return true;
    }

    List<Component> tooltipTextsAt(double x, double y) {
        if (x < VARIANT_LEFT || x >= VARIANT_LEFT + VARIANT_WIDTH || y < VARIANT_TOP
                || y >= VARIANT_TOP + VARIANT_HEIGHT) {
            return List.of();
        }
        int index = scrollbar.getCurrentScroll() + (int) ((y - VARIANT_TOP) / VARIANT_ROW_HEIGHT);
        if (index >= displayedVariants.size()) {
            return List.of();
        }
        var variant = displayedVariants.get(index);
        return variantTooltip(variant, stockAmount(variant));
    }

    private static UIElement variantRow(Font font, Variant variant, int textColor) {
        var row = new UIElement().layout(l -> l.widthPercent(100).height(VARIANT_ROW_HEIGHT));
        var key = variant.key();
        var icon = LdlibScreens.unadornedItemIcon(key.wrapForDisplayOrFilter());
        row.addChild(LdlibScreens.at(icon, 3, 0, 16, 16));
        var text = new UIElement().setAllowHitTest(false).style(uiStyle -> uiStyle.background(
                (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                    var name = font.plainSubstrByWidth(key.getDisplayName().getString(), 112);
                    LdlibText.drawString(
                            graphics, font, name, x + 24, y + 4, textColor, false);
                    String amount = stockAmount(variant);
                    int amountColor = variant.craftable() ? 0xFF2F6D3C : textColor;
                    LdlibText.drawString(
                            graphics, font, amount, x + 176 - font.width(amount), y + 4, amountColor, false);
                }));
        row.addChild(LdlibScreens.at(text, 0, 0, VARIANT_WIDTH, VARIANT_ROW_HEIGHT));
        return row;
    }

    private static void addPlainText(UIElement root, Font font, Supplier<String> text, int color,
            int x, int y, int width) {
        var label = new UIElement().setAllowHitTest(false).style(uiStyle -> uiStyle.background(
                (graphics, mouseX, mouseY, left, top, drawWidth, drawHeight, ticks) ->
                        LdlibText.drawString(graphics, font, text.get(), left, top, color, false)));
        root.addChild(LdlibScreens.at(label, x, y, width, font.lineHeight));
    }

    private static Label addLabel(UIElement root, Component text, int color, int x, int y, int width) {
        var label = LdlibScreens.addLabel(root, text, color, x, y, width);
        label.textStyle(s -> s.textWrap(TextWrap.NONE));
        return label;
    }

    private static String stockAmount(Variant variant) {
        return variant.key().formatAmount(Math.max(0L, variant.storedAmount()), AmountFormat.SLOT);
    }

    private static List<Component> variantTooltip(Variant variant, String amount) {
        var lines = new ArrayList<>(AEKeyRendering.getTooltip(variant.key()));
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.stock", amount)
                .withStyle(ChatFormatting.GRAY));
        if (variant.craftable()) {
            lines.add(ButtonToolTips.Craftable.text().withStyle(ChatFormatting.GREEN));
        }
        if (variant.exactReserveConfigured()) {
            lines.add(Component.translatable("ae2lt.tianshu.reserve.exact_override")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        return lines;
    }

    record Variant(AEKey key, long storedAmount, boolean craftable, boolean exactReserveConfigured) {
    }
}
