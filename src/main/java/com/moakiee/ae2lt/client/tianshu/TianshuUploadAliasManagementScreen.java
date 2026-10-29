package com.moakiee.ae2lt.client.tianshu;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.client.widgets.SmallScrollbarTrack;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.config.UploadAliasRecords;
import com.moakiee.ae2lt.config.UploadAliasClipboard;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TianshuUploadAliasManagementScreen<M extends MEStorageMenu> extends TianshuUploadAliasScreen<M> {
    private static final ResourceLocation LIST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/wireless_overloaded_selection.png");
    private static final int LIST_LEFT = 10;
    private static final int CELL_PADDING = 6;
    private final AETextField search;
    private final Scrollbar scroll;
    private final AE2Button add, edit, delete, clear, importAliases, exportAliases;
    private final String currentSource, currentAlias;
    private List<UploadAliasRecords.Entry> all = List.of(), filtered = List.of();
    private String selected;
    private Component status = Component.empty();
    private int rows, rowHeight = 24, listTop = 60;
    private long lastClick;

    public TianshuUploadAliasManagementScreen(AEBaseScreen<M> parent, String source, String alias) {
        super(parent, "/screens/tianshu_upload_aliases.json", "title");
        ((com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu) menu).cancelPendingClientUpload();
        currentSource = source;
        currentAlias = alias;
        selected = UploadAliasRecords.normalize(source);
        search = widgets.addTextField("search");
        search.setMaxLength(256);
        search.setPlaceholder(text("search"));
        scroll = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
        add = widgets.addButton("add", text("add"), () -> openEditor(null));
        edit = widgets.addButton("edit", text("edit"), () -> openEditor(selection()));
        delete = widgets.addButton("delete", text("delete"), this::deleteSelected);
        clear = new AE2Button(Component.literal("×"), button -> search.setValue("")) {
            @Override
            protected void renderButtonText(GuiGraphics g, net.minecraft.client.gui.Font font,
                                            int padding, int color, int yOffset) {
                g.pose().pushPose();
                g.pose().translate(0, 1, 0);
                super.renderButtonText(g, font, padding, color, yOffset);
                g.pose().popPose();
            }
        };
        widgets.add("clear", clear);
        importAliases = widgets.addButton("importAliases", text("import"), this::importFromClipboard);
        exportAliases = widgets.addButton("exportAliases", text("export"), this::exportToClipboard);
        clear.setTooltip(Tooltip.create(text("clear_search")));
        search.setResponder(value -> { filter(); revealSelection(); });
        refresh();
        if (!source.isBlank() && selection() == null) status = text("unconfigured");
    }

    @Override
    protected int preferredHeight() { return 270; }

    @Override
    protected int preferredWidth() { return 254; }

    private void exportToClipboard() {
        try {
            minecraft.keyboardHandler.setClipboard(UploadAliasClipboard.exportAliases(all));
            status = text("exported", all.size());
        } catch (RuntimeException invalid) { status = text("export_failed"); }
    }

    private void importFromClipboard() {
        final List<UploadAliasRecords.Entry> incoming;
        try { incoming = UploadAliasClipboard.parse(minecraft.keyboardHandler.getClipboard()); }
        catch (IllegalArgumentException invalid) { status = text("import_invalid"); return; }
        if (incoming.isEmpty()) { status = text("import_empty"); return; }
        long replacements = incoming.stream().filter(entry -> {
            String previous = AE2LTClientConfig.findUploadAlias(entry.source());
            return previous != null && !previous.equals(entry.alias());
        }).count();
        Runnable apply = () -> {
            if (!AE2LTClientConfig.importUploadAliases(incoming)) { status = text("save_failed"); return; }
            refresh();
            status = text("imported", incoming.size());
            revealSelection();
        };
        if (replacements > 0) switchToScreen(new TianshuUploadAliasConfirmScreen<>(this,
                text("import_replace_confirm", incoming.size(), replacements), "import", apply));
        else apply.run();
    }

    @Override
    protected void init() {
        super.init();
        boolean compact = imageHeight < 206;
        listTop = compact ? 54 : 60;
        rowHeight = compact ? 20 : 24;
        rows = Math.max(0, (imageHeight - listTop - 82) / rowHeight);
        int searchY = compact ? 24 : 28;
        place(search, 10, searchY, imageWidth - 38);
        place(clear, imageWidth - 23, searchY, 12);
        clear.setHeight(12);
        int buttonWidth = (imageWidth - 32) / 3;
        place(add, 10, imageHeight - 54, buttonWidth);
        place(edit, 16 + buttonWidth, imageHeight - 54, buttonWidth);
        place(delete, 22 + buttonWidth * 2, imageHeight - 54, imageWidth - 32 - buttonWidth * 2);
        int half = (imageWidth - 26) / 2;
        place(importAliases, 10, imageHeight - 28, half);
        place(exportAliases, 16 + half, imageHeight - 28, imageWidth - 26 - half);
        scroll.setPosition(new Point(imageWidth - 18, listTop));
        scroll.setHeight(Math.max(15, rows * rowHeight));
        filter();
        revealSelection();
    }

    private UploadAliasRecords.Entry selection() {
        return all.stream().filter(e -> e.source().equals(selected)).findFirst().orElse(null);
    }

    private void refresh() { all = AE2LTClientConfig.uploadAliases(); filter(); }

    private void filter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = all.stream().filter(e -> e.source().contains(query)
                || e.alias().toLowerCase(Locale.ROOT).contains(query)).toList();
        scroll.setRange(0, Math.max(0, filtered.size() - Math.max(1, rows)), 1);
        boolean available = filtered.stream().anyMatch(e -> e.source().equals(selected));
        edit.active = delete.active = available;
        edit.setTooltip(available ? null : Tooltip.create(text("select_first")));
        delete.setTooltip(available ? null : Tooltip.create(text("select_first")));
    }

    private void revealSelection() {
        for (int i = 0; i < filtered.size(); i++) {
            if (!filtered.get(i).source().equals(selected)) continue;
            int top = scroll.getCurrentScroll();
            if (i < top) scroll.setCurrentScroll(i);
            else if (i >= top + rows) scroll.setCurrentScroll(i - rows + 1);
            return;
        }
    }

    private void openEditor(UploadAliasRecords.Entry entry) {
        switchToScreen(new TianshuUploadAliasEditScreen<>(this, entry,
                entry == null ? currentSource : entry.source(), entry == null ? currentAlias : entry.alias(), this::saved));
    }

    void saved(String source) {
        selected = source;
        refresh();
        boolean hidden = filtered.stream().noneMatch(e -> e.source().equals(source));
        if (hidden) search.setValue("");
        status = text(hidden ? "saved_search_cleared" : "saved");
        revealSelection();
    }

    private void deleteSelected() {
        var entry = selection();
        if (entry == null) return;
        switchToScreen(new TianshuUploadAliasConfirmScreen<>(this,
                text("delete_confirm", entry.source(), entry.alias()), "delete", () -> {
                    int index = filtered.indexOf(entry);
                    if (!AE2LTClientConfig.removeUploadAlias(entry.source())) { status = text("save_failed"); return; }
                    selected = "";
                    refresh();
                    if (!filtered.isEmpty()) selected = filtered.get(Math.max(0, Math.min(index, filtered.size() - 1))).source();
                    filter();
                    status = text("deleted");
                }));
    }

    private int hovered(double x, double y) {
        int row = (int) ((y - topPos - listTop) / rowHeight);
        if (x < leftPos + LIST_LEFT || x >= leftPos + listRight() || y < topPos + listTop || row >= rows) return -1;
        int index = scroll.getCurrentScroll() + row;
        return index < filtered.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int index = hovered(x, y);
        if (button == 0 && index >= 0) {
            String source = filtered.get(index).source();
            long now = net.minecraft.Util.getMillis();
            boolean twice = source.equals(selected) && now - lastClick < 250;
            selected = source;
            setFocused(null);
            lastClick = now;
            filter();
            if (twice) openEditor(selection());
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!search.isFocused() && !filtered.isEmpty()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                int index = filtered.indexOf(selection());
                int direction = key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1;
                selected = filtered.get(Math.max(0, Math.min(filtered.size() - 1, index + direction))).source();
                filter();
                revealSelection();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && edit.active) { openEditor(selection()); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        super.drawFG(g, x, y, mx, my);
        int split = LIST_LEFT + (listRight() - LIST_LEFT) * 55 / 100;
        g.drawString(font, clipped(text("source").getString(), split - LIST_LEFT - CELL_PADDING * 2),
                LIST_LEFT + CELL_PADDING, listTop - 14, 0x2A2E3F, false);
        g.drawString(font, clipped(text("alias").getString(), listRight() - split - CELL_PADDING * 2),
                split + CELL_PADDING, listTop - 14, 0x2A2E3F, false);
        if (filtered.isEmpty() && rows > 0) {
            g.drawWordWrap(font, text(all.isEmpty() ? "empty" : "no_results"),
                    LIST_LEFT + CELL_PADDING, listTop + 7, listRight() - LIST_LEFT - CELL_PADDING * 2, 0x363B4E);
        }
        for (int row = 0; row < rows; row++) {
            int index = scroll.getCurrentScroll() + row;
            if (index >= filtered.size()) break;
            int top = listTop + row * rowHeight;
            var entry = filtered.get(index);
            boolean selectedRow = entry.source().equals(selected);
            boolean highlighted = selectedRow || index == hovered(mx, my);
            if (highlighted) {
                g.blit(LIST_TEXTURE, LIST_LEFT + 1, top, listRight() - LIST_LEFT - 2, rowHeight,
                        1, 233, 204, 22, 256, 256);
            }
            if (selectedRow) g.fill(LIST_LEFT + 1, top + 2, LIST_LEFT + 3, top + rowHeight - 2, 0xff1864ab);
            int textY = top + (rowHeight - font.lineHeight) / 2;
            g.drawString(font, clipped(entry.source(), split - LIST_LEFT - CELL_PADDING * 2),
                    LIST_LEFT + CELL_PADDING, textY, 0x14161F, false);
            g.drawString(font, clipped(entry.alias(), listRight() - split - CELL_PADDING * 2),
                    split + CELL_PADDING, textY, 0x14161F, false);
        }
        g.drawString(font, clipped(text("count", filtered.size(), all.size()).getString(), imageWidth - 16),
                10, imageHeight - 79, 0x4E5370, false);
        g.drawString(font, clipped(status.getString(), imageWidth - 20), 10, imageHeight - 67, 0x363B4E, false);
    }

    private int listRight() { return imageWidth - 24; }

    @Override
    public void drawBG(GuiGraphics g, int x, int y, int mx, int my, float partialTicks) {
        super.drawBG(g, x, y, mx, my, partialTicks);
        int listHeight = scroll.getBounds().getHeight();
        drawRecess(g, x + LIST_LEFT, y + listTop - 1, listRight() - LIST_LEFT, listHeight + 2);
        SmallScrollbarTrack.draw(g, scroll, x, y);
    }

    /** Use the controller's recessed panel, preserving its one-pixel rim at every size. */
    private void drawRecess(GuiGraphics g, int x, int y, int width, int height) {
        int[] sourceX = {8, 9, 187, 188};
        int[] sourceY = {38, 39, 111, 112};
        int[] targetX = {x, x + 1, x + width - 1, x + width};
        int[] targetY = {y, y + 1, y + height - 1, y + height};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                g.blit(PANEL_TEXTURE, targetX[column], targetY[row],
                        targetX[column + 1] - targetX[column], targetY[row + 1] - targetY[row],
                        sourceX[column], sourceY[row], sourceX[column + 1] - sourceX[column],
                        sourceY[row + 1] - sourceY[row], 256, 256);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        super.renderTooltip(g, mx, my);
        int index = hovered(mx, my);
        if (index >= 0) {
            var e = filtered.get(index);
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.literal(e.source()));
            lines.add(Component.literal(e.alias()));
            if (e.occurrences() > 1) lines.add(text("duplicates", e.occurrences()));
            drawTooltip(g, mx, my, lines);
        }
    }
}
