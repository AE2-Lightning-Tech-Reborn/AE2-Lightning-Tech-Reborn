package com.moakiee.ae2lt.client.machine;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;

import com.moakiee.ae2lt.client.compat.JecSearchCompat;
import com.moakiee.ae2lt.client.ldlib.LdlibText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import dev.vfyjxf.taffy.style.TaffyDisplay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.core.localization.GuiText;
import appeng.client.gui.style.PaletteColor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2TextField;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibSlotScreen;
import com.moakiee.ae2lt.menu.MatrixPortMenu;
import com.moakiee.ae2lt.client.gui.AE2GuiPalette;

/**
 * Searchable, scrollable view of one matrix warehouse at a time.
 *
 * <p>The search behavior mirrors ExtendedAE's assembler matrix: a row remains visible if any
 * pattern in it has an input or output whose display name matches the ordered search tokens.
 * Matching patterns are highlighted while the other patterns in that retained row are dimmed.</p>
 *
 * <p>The LDLib UI tree reuses one slot view per visible cell. The views follow the
 * filtered window while each menu slot keeps its original server index.</p>
 */
public class MatrixPortScreen extends LdlibSlotScreen<MatrixPortMenu> {
    private static final int MATCH_HIGHLIGHT = 0x8A00FF00;
    private static final int NON_MATCH_HIGHLIGHT = 0x6A000000;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/guis/matrix_pattern_manager.png");
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(TEXTURE, 256, 256);

    private static final int GUI_WIDTH = 190;
    private static final int GUI_HEIGHT = 223;
    private static final int SLOT_SPACING = 18;
    private static final int SLOT_SIZE = 16;
    private static final int OFFSCREEN_SLOT = -10_000;

    private static final int SEARCH_X = 78;
    private static final int SEARCH_Y = 7;
    private static final int SEARCH_WIDTH = 94;
    private static final int SEARCH_HEIGHT = 11;

    private static final int SCROLLBAR_X = 175;
    private static final int SCROLLBAR_Y = 22;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 103;
    private static final int HANDLE_HEIGHT = 15;
    private static final int HANDLE_U_NORMAL = 192;
    private static final int HANDLE_U_HOVERED = 206;
    private static final int HANDLE_V = 4;

    private static final int PATTERN_AREA_X = 12;
    private static final int PATTERN_AREA_Y = 20;
    private static final int PATTERN_AREA_WIDTH = 175;
    private static final int PATTERN_AREA_HEIGHT = 108;

    private final List<Integer> filteredRows = new ArrayList<>();
    private final Set<Integer> matchingPatternSlots = new HashSet<>();
    private final Map<String, Boolean> nameMatchCache = new HashMap<>();
    private final List<MatrixPortMenu.MatrixPatternSlot> visiblePatternSlots =
            new ArrayList<>(MatrixPortMenu.COLUMNS * MatrixPortMenu.VISIBLE_ROWS);
    private final List<Slot> allMenuSlots;
    private final List<Slot> playerMenuSlots;
    private final List<Slot> visibleMenuSlots =
            new ArrayList<>(MatrixPortMenu.COLUMNS * MatrixPortMenu.VISIBLE_ROWS + 36);

    private MatrixSearchField searchField;
    private Scroller scroller;
    private boolean draggingScrollbar;
    private final List<PatternSlotView> patternSlotViews = new ArrayList<>();
    private int scrollRow;
    private long lastPatternContentRevision;
    private MatrixMigrationWidget migration;

    public MatrixPortScreen(MatrixPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
        titleLabelY = 10_000;
        inventoryLabelY = 10_000;
        allMenuSlots = List.copyOf(menu.slots);
        playerMenuSlots = List.copyOf(
                allMenuSlots.subList(menu.getPatternSlotCount(), allMenuSlots.size()));
        visibleMenuSlots.addAll(playerMenuSlots);
        for (var slot : menu.getPatternSlots()) {
            slot.setActive(false);
        }
    }

    @Override
    protected void init() {
        super.init();
        draggingScrollbar = false;
        migration = new MatrixMigrationWidget(menu, leftPos - 18, topPos);
        LdlibScreens.embedWithSlots(this, buildRoot(), playerMenuSlots, this::addRenderableWidget);
        searchField = new MatrixSearchField();
        searchField.setPosition(leftPos + SEARCH_X, topPos + SEARCH_Y);
        searchField.setResponder(ignored -> refreshList(true));
        addRenderableWidget(searchField);
        setInitialFocus(searchField);
        refreshList(true);
        addRenderableWidget(migration.button);
    }

    private UIElement buildRoot() {
        var root = new UIElement();
        root.layout(l -> l.width(GUI_WIDTH).height(GUI_HEIGHT));
        root.style(s -> s.background(tex(0, 0, GUI_WIDTH, GUI_HEIGHT)));
        var highlights = new UIElement().setAllowHitTest(false).style(s -> s.background(
                (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                        renderSearchHighlights(graphics, (int) x, (int) y)));
        root.addChild(LdlibScreens.at(highlights, 0, 0, GUI_WIDTH, GUI_HEIGHT));

        var normalThumb = tex(HANDLE_U_NORMAL, HANDLE_V, SCROLLBAR_WIDTH, HANDLE_HEIGHT);
        var activeThumb = tex(HANDLE_U_HOVERED, HANDLE_V, SCROLLBAR_WIDTH, HANDLE_HEIGHT);
        IGuiTexture thumb = (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            boolean hovered = draggingScrollbar || mouseX >= x && mouseX < x + SCROLLBAR_WIDTH
                    && mouseY >= y && mouseY < y + HANDLE_HEIGHT;
            var texture = hovered ? activeThumb : normalThumb;
            texture.draw(graphics, mouseX, mouseY, x, y, width, height, ticks);
        };
        scroller = LdlibScreens.displayThumbScroller(thumb, thumb, HANDLE_HEIGHT, SCROLLBAR_HEIGHT);
        root.addChild(LdlibScreens.at(scroller, SCROLLBAR_X, SCROLLBAR_Y, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT));

        // Complete all native search fills and the thumb before drawing any slot contents.
        var patternArea = new UIElement();
        root.addChild(LdlibScreens.at(patternArea,
                PATTERN_AREA_X, PATTERN_AREA_Y, PATTERN_AREA_WIDTH, PATTERN_AREA_HEIGHT));
        var patternGrid = new UIElement().setAllowHitTest(false);
        patternGrid.layout(l -> l.display(TaffyDisplay.GRID).gapAll(SLOT_SPACING - SLOT_SIZE)
                .gridTemplateColumns("repeat(" + MatrixPortMenu.COLUMNS + ", " + SLOT_SIZE + "px)")
                .gridTemplateRows("repeat(" + MatrixPortMenu.VISIBLE_ROWS + ", " + SLOT_SIZE + "px)"));
        patternArea.addChild(LdlibScreens.at(patternGrid,
                MatrixPortMenu.PATTERN_X - PATTERN_AREA_X, MatrixPortMenu.PATTERN_Y - PATTERN_AREA_Y,
                MatrixPortMenu.COLUMNS * SLOT_SPACING, MatrixPortMenu.VISIBLE_ROWS * SLOT_SPACING));
        patternSlotViews.clear();
        for (int cell = 0; cell < MatrixPortMenu.COLUMNS * MatrixPortMenu.VISIBLE_ROWS; cell++) {
            var view = new PatternSlotView();
            patternSlotViews.add(view);
            patternGrid.addChild(view);
        }
        return root;
    }

    private void renderSearchHighlights(GuiGraphics graphics, int x, int y) {
        if (searchField == null || searchField.getValue().isBlank()) {
            return;
        }
        for (var slot : visiblePatternSlots) {
            int color = matchingPatternSlots.contains(slot.getPatternIndex()) ? MATCH_HIGHLIGHT : NON_MATCH_HIGHLIGHT;
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + SLOT_SIZE, y + slot.y + SLOT_SIZE, color);
        }
    }

    private static IGuiTexture tex(int u, int v, int width, int height) {
        return ATLAS.slice(u, v, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= topPos + 5 && mouseY < topPos + 18 && menu.getWarehouseCount() > 0) {
            if (mouseX >= leftPos + 5 && mouseX < leftPos + 18) {
                selectWarehouse(menu.getSelectedWarehouse() - 1);
                return true;
            }
            if (mouseX >= leftPos + 63 && mouseX < leftPos + 77) {
                selectWarehouse(menu.getSelectedWarehouse() + 1);
                return true;
            }
        }
        if (button == 1 && searchField != null && searchField.isMouseOver(mouseX, mouseY)) {
            searchField.setValue("");
        }
        if (button == 0 && mouseX >= leftPos + SCROLLBAR_X
                && mouseX < leftPos + SCROLLBAR_X + SCROLLBAR_WIDTH
                && mouseY >= topPos + SCROLLBAR_Y && mouseY < topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT
                && maxScrollRow() > 0) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
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
        if (mouseX >= leftPos + PATTERN_AREA_X && mouseX < leftPos + PATTERN_AREA_X + PATTERN_AREA_WIDTH
                && mouseY >= topPos + PATTERN_AREA_Y && mouseY < topPos + PATTERN_AREA_Y + PATTERN_AREA_HEIGHT
                && maxScrollRow() > 0 && scrollY != 0.0) {
            setScrollRow(scrollRow - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateScrollFromMouse(double mouseY) {
        setScrollRow(LdlibScreens.centeredThumbRow(
                mouseY - topPos - SCROLLBAR_Y, SCROLLBAR_HEIGHT, HANDLE_HEIGHT, maxScrollRow()));
    }

    private Component warehouseText() {
        return Component.translatable("ae2lt.gui.matrix_port.warehouse",
                menu.getSelectedWarehouse() + 1, menu.getWarehouseCount());
    }

    private void selectWarehouse(int index) {
        if (index < 0 || index >= menu.getWarehouseCount()
                || index == menu.getSelectedWarehouse() || minecraft == null || minecraft.gameMode == null) {
            return;
        }
        menu.selectWarehouseClient(index);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
        if (searchField != null) {
            searchField.setValue("");
        }
        refreshList(true);
    }

    private static final class MatrixSearchField extends LdlibAe2TextField {
        private final Component hint = GuiText.SearchPlaceholder.text().copy().setStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(AE2GuiPalette.color(PaletteColor.TEXTFIELD_PLACEHOLDER))));

        private MatrixSearchField() {
            super(SEARCH_WIDTH, SEARCH_HEIGHT, true);
            useUnborderedEditBoxStyle();
            setTextColor(AE2GuiPalette.color(PaletteColor.TEXTFIELD_TEXT));
            setMaxLength(64);
            setPlaceholder(hint);
            setMessage(Component.translatable("ae2lt.gui.matrix_port.search"));
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            setPlaceholder(focused ? Component.empty() : hint);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (migration != null) {
            migration.update();
        }
        if (searchField != null && !searchField.getValue().isBlank()) {
            long revision = menu.getPatternContentRevision();
            if (revision != lastPatternContentRevision) {
                refreshList(false);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Drawn by the LDLib tree.
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR);
        if (migration != null) {
            var line = migration.summary();
            if (line != null) {
                LdlibText.drawString(graphics, font, font.plainSubstrByWidth(line.getString(), 162),
                        13, 129, color, false);
            }
        }
        LdlibText.drawString(graphics, font, "<", 7, 7, color, false);
        LDLibFonts.drawText(graphics, font, warehouseText().getVisualOrderText(), 19, 7, color, false);
        LdlibText.drawString(graphics, font, ">", 67, 7, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderVisibleBatch(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Vanilla walks every menu slot twice per frame even when most slots are inactive. Keep the
     * authoritative warehouse slot list outside rendering, but expose only this six-row window
     * while the vanilla renderer and render events are running.
     */
    private void renderVisibleBatch(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        menu.slots.clear();
        menu.slots.addAll(visibleMenuSlots);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            menu.slots.clear();
            menu.slots.addAll(allMenuSlots);
        }
    }

    private void refreshList(boolean resetScroll) {
        filteredRows.clear();
        matchingPatternSlots.clear();
        nameMatchCache.clear();

        List<String> searchTokens = tokenize(searchField == null ? "" : searchField.getValue());
        int rowCount = (menu.getSelectedWarehouseCapacity() + MatrixPortMenu.COLUMNS - 1)
                / MatrixPortMenu.COLUMNS;
        for (int row = 0; row < rowCount; row++) {
            if (searchTokens.isEmpty() || rowMatches(row, searchTokens)) {
                filteredRows.add(row);
            }
        }

        if (resetScroll) {
            scrollRow = 0;
        } else {
            scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
        }
        updateVisibleSlots();
        syncScroller();
        lastPatternContentRevision = menu.getPatternContentRevision();
    }

    private boolean rowMatches(int row, List<String> searchTokens) {
        boolean rowMatches = false;
        int firstSlot = row * MatrixPortMenu.COLUMNS;
        int lastSlot = Math.min(firstSlot + MatrixPortMenu.COLUMNS,
                menu.getSelectedWarehouseCapacity());
        for (int slotIndex = firstSlot; slotIndex < lastSlot; slotIndex++) {
            ItemStack stack = menu.getPatternSlots().get(slotIndex).getItem();
            if (patternMatches(stack, searchTokens)) {
                matchingPatternSlots.add(slotIndex);
                rowMatches = true;
            }
        }
        return rowMatches;
    }

    private boolean patternMatches(ItemStack patternStack, List<String> searchTokens) {
        if (patternStack.isEmpty() || minecraft == null || minecraft.level == null) {
            return false;
        }

        final IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(patternStack, minecraft.level);
        } catch (RuntimeException ignored) {
            return false;
        }
        if (details == null) {
            return false;
        }

        for (var output : details.getOutputs()) {
            if (output != null && nameMatches(output.what().getDisplayName().getString(), searchTokens)) {
                return true;
            }
        }
        for (var input : details.getInputs()) {
            if (input == null) {
                continue;
            }
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length > 0
                    && possibleInputs[0] != null
                    && nameMatches(possibleInputs[0].what().getDisplayName().getString(), searchTokens)) {
                return true;
            }
        }
        return false;
    }

    private boolean nameMatches(String displayName, List<String> searchTokens) {
        return nameMatchCache.computeIfAbsent(displayName, name -> {
            if (compareTokens(searchTokens, tokenize(name))) {
                return true;
            }
            String compactQuery = String.join("", searchTokens);
            return JecSearchCompat.contains(name.toLowerCase(Locale.ROOT), compactQuery);
        });
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var tokens = new ArrayList<String>();
        for (String token : text.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** Ordered partial-token comparison used by ExtendedAE's assembler-matrix search. */
    private static boolean compareTokens(List<String> filter, List<String> target) {
        int start = 0;
        while (start <= target.size() - filter.size()) {
            int targetIndex = start;
            int filterIndex = 0;
            while (targetIndex < target.size() && filterIndex < filter.size()) {
                if (target.get(targetIndex).contains(filter.get(filterIndex))) {
                    filterIndex++;
                }
                targetIndex++;
            }
            if (filterIndex >= filter.size()) {
                return true;
            }
            start++;
        }
        return false;
    }

    private void updateVisibleSlots() {
        var slots = menu.getPatternSlots();
        for (var slot : visiblePatternSlots) {
            slot.setActive(false);
            slot.x = OFFSCREEN_SLOT;
            slot.y = OFFSCREEN_SLOT;
        }
        visiblePatternSlots.clear();

        int visibleRows = Math.min(
                MatrixPortMenu.VISIBLE_ROWS, Math.max(0, filteredRows.size() - scrollRow));
        for (int visibleRow = 0; visibleRow < visibleRows; visibleRow++) {
            int patternRow = filteredRows.get(scrollRow + visibleRow);
            int firstSlot = patternRow * MatrixPortMenu.COLUMNS;
            int lastSlot = Math.min(firstSlot + MatrixPortMenu.COLUMNS,
                    menu.getSelectedWarehouseCapacity());
            for (int slotIndex = firstSlot; slotIndex < lastSlot; slotIndex++) {
                int column = slotIndex - firstSlot;
                var slot = slots.get(slotIndex);
                slot.x = MatrixPortMenu.PATTERN_X + column * SLOT_SPACING;
                slot.y = MatrixPortMenu.PATTERN_Y + visibleRow * SLOT_SPACING;
                slot.setActive(true);
                visiblePatternSlots.add(slot);
            }
        }

        visibleMenuSlots.clear();
        visibleMenuSlots.addAll(visiblePatternSlots);
        visibleMenuSlots.addAll(playerMenuSlots);
        for (int cell = 0; cell < patternSlotViews.size(); cell++) {
            var view = patternSlotViews.get(cell);
            view.assigned = cell < visiblePatternSlots.size();
            if (view.assigned) {
                LdlibScreens.bindExistingSlot(menu, view, visiblePatternSlots.get(cell));
            } else {
                LdlibScreens.unbindExistingSlot(menu, view);
            }
        }
    }

    private final class PatternSlotView extends LdlibScreens.ExistingMenuSlot {
        private boolean assigned;

        private PatternSlotView() {
            layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE).paddingAll(0));
            style(s -> s.background(IGuiTexture.EMPTY));
            slotStyle(s -> s.showItemTooltips(false));
        }

        @Override
        public boolean isVisible() {
            // Keep the grid cell laid out even while it has no physical slot.
            return assigned && super.isVisible();
        }

        @Override
        public ItemStack getValue() {
            return getSlot() instanceof MatrixPortMenu.MatrixPatternSlot pattern
                    ? pattern.getDisplayStack()
                    : super.getValue();
        }
    }

    private void syncScroller() {
        if (scroller == null) {
            return;
        }
        int maxScroll = maxScrollRow();
        LdlibScreens.syncRowThumb(scroller, scrollRow, maxScroll);
        int thumbY = maxScroll <= 0 ? 0
                : Math.round((float) scrollRow / maxScroll * (SCROLLBAR_HEIGHT - HANDLE_HEIGHT));
        // ModularUI disables Taffy rounding. Override the scroller's important percent layout in pixels.
        com.lowdragmc.lowdraglib2.gui.ui.Style.importantPipeline(scroller.scrollBar.getLayout(),
                layout -> layout.height(HANDLE_HEIGHT).top(thumbY));
    }

    private void setScrollRow(int value) {
        int clamped = Mth.clamp(value, 0, maxScrollRow());
        if (clamped != scrollRow) {
            scrollRow = clamped;
            updateVisibleSlots();
        }
        syncScroller();
    }

    private int maxScrollRow() {
        return Math.max(0, filteredRows.size() - MatrixPortMenu.VISIBLE_ROWS);
    }
}
