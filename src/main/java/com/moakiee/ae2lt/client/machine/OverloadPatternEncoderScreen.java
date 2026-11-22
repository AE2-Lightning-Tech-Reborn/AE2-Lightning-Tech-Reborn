package com.moakiee.ae2lt.client.machine;

import static com.moakiee.ae2lt.client.ldlib.LdlibScreens.at;

import java.util.ArrayList;
import java.util.List;

import appeng.client.gui.style.PaletteColor;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.client.gui.AE2GuiPalette;

/**
 * Client-side screen for the overload pattern encoder.
 *
 * <p>LDLib2 renders the textured 176x191 viewport, source input/output entry list,
 * match-mode toggles, and scrollbar. Physical slots retain the native container pipeline.</p>
 */
public class OverloadPatternEncoderScreen extends AbstractContainerScreen<OverloadPatternEncoderMenu> {
    private static final Component SCREEN_TITLE = Component.translatable("item.ae2lt.overload_pattern_encoder");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/ae2lt_pattern_encoder.png");
    private static final ResourceLocation CHECKBOX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/checkbox.png");
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(TEXTURE, 256, 256);
    private static final LdlibStyleSprite CHECKBOX_ATLAS = new LdlibStyleSprite(CHECKBOX_TEXTURE, 64, 64);

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 191;

    private static final int PANEL_X = 43;
    private static final int PANEL_Y = 17;
    private static final int PANEL_WIDTH = 125;
    private static final int PANEL_HEIGHT = 75;
    private static final int TRACK_X = 45;
    private static final int TRACK_Y = 21;
    private static final int TRACK_HEIGHT = 69;
    private static final int SLIDER_WIDTH = 7;
    private static final int SLIDER_HEIGHT = 15;
    private static final int SLOT_SIZE = 18;
    private static final int ENTRY_TOP = PANEL_Y + 3 + 3;
    private static final int ENTRY_SLOT_X = 60;
    private static final int ENTRY_TEXT_X = 90;
    private static final int ENTRY_SWITCH_X = 138;
    private static final int ENTRY_ROW_HEIGHT = 22;
    private static final int ENTRY_SWITCH_WIDTH = 22;
    private static final int ENTRY_SWITCH_HEIGHT = 12;
    private static final int ENTRY_SWITCH_Y = 3;
    private static final int ENTRY_WIDTH = PANEL_WIDTH;
    private static final int VISIBLE_ROWS = 3;

    private static final IGuiTexture SLOT_BACKGROUND = nativeSprite(ATLAS, 177, 0, SLOT_SIZE, SLOT_SIZE);
    private static final IGuiTexture SLIDER = nativeSprite(ATLAS, 177, 29, SLIDER_WIDTH, SLIDER_HEIGHT);
    private static final IGuiTexture SWITCH_STRICT = nativeSprite(
            CHECKBOX_ATLAS, 0, 28, ENTRY_SWITCH_WIDTH, ENTRY_SWITCH_HEIGHT);
    private static final IGuiTexture SWITCH_ID_ONLY = nativeSprite(
            CHECKBOX_ATLAS, 0, 40, ENTRY_SWITCH_WIDTH, ENTRY_SWITCH_HEIGHT);

    private ModularUI contentUI;
    private VirtualScrollerView<Entry> entryList;
    private Scroller scroller;
    private boolean draggingScrollbar;
    private int scrollOffset;
    private List<Entry> entries = List.of();

    public OverloadPatternEncoderScreen(OverloadPatternEncoderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 96;
    }

    @Override
    protected void init() {
        super.init();
        releaseContentUI();
        entries = List.of();
        contentUI = LdlibScreens.localLayer(buildRoot(), GUI_WIDTH, GUI_HEIGHT);
        contentUI.setDrawTooltips(false);
        refreshEntries();
        setScroll(scrollOffset);
    }

    @Override
    public void removed() {
        try {
            super.removed();
        } finally {
            releaseContentUI();
        }
    }

    private void releaseContentUI() {
        if (contentUI != null) {
            if (!contentUI.isRemoved()) {
                contentUI.onRemoved();
            }
            contentUI = null;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshEntries();
        setScroll(scrollOffset);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        refreshEntries();
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(leftPos, topPos, 0);
            contentUI.getWidget().render(graphics, mouseX - leftPos, mouseY - topPos, partialTick);
        } finally {
            pose.popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, SCREEN_TITLE, 8, 6,
                AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR), false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR), false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderEntryTooltip(graphics, mouseX, mouseY);
    }

    private void renderEntryTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= PANEL_X && x < PANEL_X + PANEL_WIDTH && y >= PANEL_Y && y < PANEL_Y + PANEL_HEIGHT) {
            // Keep the original row lookup origin and truncation, distinct from the icon's top.
            int row = (int) ((y - (PANEL_Y + 3)) / ENTRY_ROW_HEIGHT);
            var currentEntries = buildEntries();
            int index = scrollOffset + row;
            if (row >= 0 && row < VISIBLE_ROWS && index >= 0 && index < currentEntries.size()) {
                var entry = currentEntries.get(index);
                int slotY = ENTRY_TOP + row * ENTRY_ROW_HEIGHT;
                if (x >= ENTRY_SLOT_X && x < ENTRY_SLOT_X + SLOT_SIZE
                        && y >= slotY && y < slotY + SLOT_SIZE) {
                    graphics.renderTooltip(font, entry.stack(), mouseX, mouseY);
                    return;
                }
                if (switchRowAt(x, y) == row) {
                    graphics.renderComponentTooltip(font, List.of(Component.translatable(entry.mode() == MatchMode.ID_ONLY
                            ? "ae2lt.gui.overload_pattern_encoder.mode.id_only"
                            : "ae2lt.gui.overload_pattern_encoder.mode.strict")), mouseX, mouseY);
                    return;
                }
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double x = mouseX - leftPos;
            double y = mouseY - topPos;
            if (x >= TRACK_X - 1 && x < TRACK_X - 1 + SLIDER_WIDTH && y >= TRACK_Y && y < TRACK_Y + TRACK_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
            int row = switchRowAt(x, y);
            if (row >= 0) {
                refreshEntries();
                int index = scrollOffset + row;
                if (index < entries.size()) {
                    toggleEntry(entries.get(index));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= PANEL_X && x < PANEL_X + PANEL_WIDTH && y >= PANEL_Y && y < PANEL_Y + PANEL_HEIGHT) {
            refreshEntries();
            if (maxScrollOffset() > 0) {
                setScroll(scrollOffset - (int) Math.signum(scrollY));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateScrollFromMouse(double mouseY) {
        refreshEntries();
        int max = maxScrollOffset();
        if (max <= 0) {
            setScroll(0);
            return;
        }
        float progress = (float) ((mouseY - (topPos + TRACK_Y) - SLIDER_HEIGHT / 2.0)
                / (TRACK_HEIGHT - SLIDER_HEIGHT));
        setScroll(Math.round(Mth.clamp(progress, 0.0F, 1.0F) * max));
    }

    private static int switchRowAt(double x, double y) {
        double rowY = y - ENTRY_TOP - ENTRY_SWITCH_Y;
        if (x < ENTRY_SWITCH_X || x >= ENTRY_SWITCH_X + ENTRY_SWITCH_WIDTH
                || rowY < 0 || rowY >= VISIBLE_ROWS * ENTRY_ROW_HEIGHT) {
            return -1;
        }
        int row = (int) (rowY / ENTRY_ROW_HEIGHT);
        return rowY < row * ENTRY_ROW_HEIGHT + ENTRY_SWITCH_HEIGHT ? row : -1;
    }

    // ---- LDLib tree ---------------------------------------------------------------------

    private UIElement buildRoot() {
        var root = new UIElement();
        root.layout(l -> l.width(GUI_WIDTH).height(GUI_HEIGHT));
        root.style(s -> s.background(nativeSprite(ATLAS, 0, 0, GUI_WIDTH, GUI_HEIGHT)));

        var panel = new UIElement();
        panel.style(s -> s.overflowVisible(false));
        root.addChild(at(panel, PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT));

        entryList = LdlibScreens.virtualRows(ENTRY_ROW_HEIGHT, EntryRow::new);
        // Draw three rows while preserving the original full-panel clipping bounds.
        entryList.style(s -> s.overflowVisible(true));
        entryList.verticalContainer.style(s -> s.overflowVisible(true));
        entryList.viewPort(view -> view.style(s -> s.overflowVisible(true)));
        entryList.viewContainer.style(s -> s.overflowVisible(true));
        panel.addChild(at(entryList, 0, ENTRY_TOP - PANEL_Y,
                ENTRY_WIDTH, VISIBLE_ROWS * ENTRY_ROW_HEIGHT));

        scroller = LdlibScreens.displayThumbScroller(SLIDER, SLIDER, SLIDER_HEIGHT, TRACK_HEIGHT);
        root.addChild(at(scroller, TRACK_X - 1, TRACK_Y, SLIDER_WIDTH, TRACK_HEIGHT));
        return root;
    }

    private void refreshEntries() {
        if (scroller == null) {
            return;
        }
        var updated = buildEntries();
        if (!updated.equals(entries)) {
            entries = List.copyOf(updated);
            entryList.setItems(entries);
        }
        syncScroll();
    }

    private void setScroll(int offset) {
        scrollOffset = Mth.clamp(offset, 0, maxScrollOffset());
        syncScroll();
    }

    private void syncScroll() {
        int max = maxScrollOffset();
        // Upstream clamps the drawn window without mutating the saved offset until tick/input.
        int visibleOffset = Mth.clamp(scrollOffset, 0, max);
        LdlibScreens.syncRowThumb(scroller, scrollOffset, max);
        LdlibScreens.positionThumbPixels(scroller,
                max <= 0 ? 0 : Math.round(scrollOffset / (float) max * (TRACK_HEIGHT - SLIDER_HEIGHT)),
                SLIDER_HEIGHT);
        LdlibScreens.scrollVirtualRows(entryList, visibleOffset, max);
    }

    private int maxScrollOffset() {
        return Math.max(0, entries.size() - VISIBLE_ROWS);
    }

    private List<Entry> buildEntries() {
        var result = new ArrayList<Entry>();
        var state = menu.syncedState;

        for (int i = 0; i < state.inputSlots().size(); i++) {
            var configured = state.inputSlots().get(i);
            result.add(new Entry(true, configured.slotIndex(), configured.matchMode(), menu.getInputPreviewStack(i).copy()));
        }

        for (int i = 0; i < state.outputSlots().size(); i++) {
            var configured = state.outputSlots().get(i);
            result.add(new Entry(false, configured.slotIndex(), configured.matchMode(), menu.getOutputPreviewStack(i).copy()));
        }

        return result;
    }

    private void toggleEntry(Entry entry) {
        if (entry.input()) {
            menu.clientToggleInputMode(entry.slotIndex());
        } else {
            menu.clientToggleOutputMode(entry.slotIndex());
        }
    }

    private static IGuiTexture nativeSprite(LdlibStyleSprite atlas, int u, int v, int width, int height) {
        return (graphics, mouseX, mouseY, x, y, boxWidth, boxHeight, ticks) ->
                atlas.draw(graphics, (int) x, (int) y, u, v, width, height);
    }

    /** One visible list row: item preview, input/output label and match-mode switch. */
    private final class EntryRow extends UIElement {
        private EntryRow(Entry entry) {
            layout(l -> l.widthPercent(100).height(ENTRY_ROW_HEIGHT));

            var preview = new UIElement().style(s -> s.background(SLOT_BACKGROUND));
            var icon = new UIElement().setAllowHitTest(false)
                    .style(s -> s.background((graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                        graphics.renderItem(entry.stack(), (int) x, (int) y);
                        graphics.renderItemDecorations(font, entry.stack(), (int) x, (int) y);
                    }));
            preview.addChild(at(icon, 1, 1, 16, 16));

            var text = new UIElement().setAllowHitTest(false).style(s -> s.background(
                    (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                            LDLibFonts.drawText(graphics, font, Component.translatable(entry.input()
                                    ? "ae2lt.gui.overload_pattern_encoder.entry.input"
                                    : "ae2lt.gui.overload_pattern_encoder.entry.output").getVisualOrderText(),
                                    x, y, AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR), false)));

            var modeSwitch = new UIElement().style(s -> s.background(
                    entry.mode().ignoresComponents() ? SWITCH_ID_ONLY : SWITCH_STRICT));

            addChildren(at(preview, ENTRY_SLOT_X - PANEL_X, 0, SLOT_SIZE, SLOT_SIZE),
                    at(text, ENTRY_TEXT_X - PANEL_X, 5, ENTRY_SWITCH_X - ENTRY_TEXT_X, 9),
                    at(modeSwitch, ENTRY_SWITCH_X - PANEL_X, ENTRY_SWITCH_Y,
                            ENTRY_SWITCH_WIDTH, ENTRY_SWITCH_HEIGHT));
        }
    }

    private record Entry(boolean input, int slotIndex, MatchMode mode, ItemStack stack) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Entry that
                    && input == that.input
                    && slotIndex == that.slotIndex
                    && mode == that.mode
                    && ItemStack.matches(stack, that.stack);
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(input) * 31 * 31 + slotIndex * 31 + mode.hashCode();
        }
    }
}
