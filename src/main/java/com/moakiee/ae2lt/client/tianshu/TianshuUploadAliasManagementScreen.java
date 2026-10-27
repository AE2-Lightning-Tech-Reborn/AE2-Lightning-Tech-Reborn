package com.moakiee.ae2lt.client.tianshu;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.config.UploadAliasRecords;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class TianshuUploadAliasManagementScreen<M extends MEStorageMenu> extends TianshuUploadAliasScreen<M> {
    private final AETextField search;
    private final Scrollbar scroll;
    private final AE2Button add, edit, delete, clear;
    private final String currentSource, currentAlias;
    private List<UploadAliasRecords.Entry> all = List.of(), filtered = List.of();
    private String selected;
    private Component status = Component.empty();
    private int rows, rowHeight;
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
        clear = widgets.addButton("clear", Component.literal("×"), () -> search.setValue(""));
        clear.setTooltip(Tooltip.create(text("clear_search")));
        search.setResponder(value -> { filter(); revealSelection(); });
        refresh();
        if (!source.isBlank() && selection() == null) status = text("unconfigured");
    }

    @Override
    protected void init() {
        super.init();
        rowHeight = imageWidth < 280 ? 28 : 20;
        rows = Math.max(1, (imageHeight - 110) / rowHeight);
        place(search, 8, 24, imageWidth - 42);
        place(clear, imageWidth - 30, 22, 22);
        int buttonWidth = (imageWidth - 24) / 3;
        place(add, 8, imageHeight - 26, buttonWidth);
        place(edit, 12 + buttonWidth, imageHeight - 26, buttonWidth);
        place(delete, 16 + buttonWidth * 2, imageHeight - 26, buttonWidth);
        scroll.setPosition(new Point(imageWidth - 14, 56));
        scroll.setHeight(rows * rowHeight);
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
        int row = (int) ((y - topPos - 56) / rowHeight);
        if (x < leftPos + 8 || x >= leftPos + imageWidth - 20 || y < topPos + 56 || row >= rows) return -1;
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
        int split = (imageWidth - 28) * 55 / 100;
        g.drawString(font, text("source"), 8, 45, 0x252525, false);
        if (imageWidth >= 280) g.drawString(font, text("alias"), 12 + split, 45, 0x252525, false);
        g.fill(8, 56, imageWidth - 20, 56 + rows * rowHeight, 0xffe2e2e2);
        for (int row = 0; row < rows; row++) {
            int index = scroll.getCurrentScroll() + row;
            if (index >= filtered.size()) break;
            var e = filtered.get(index);
            int top = 56 + row * rowHeight;
            if (e.source().equals(selected)) g.fill(8, top, imageWidth - 20, top + rowHeight, 0xffafc4d4);
            else if (index == hovered(mx, my)) g.fill(8, top, imageWidth - 20, top + rowHeight, 0xffd4dce2);
            boolean narrow = imageWidth < 280;
            g.drawString(font, clipped(e.source(), narrow ? imageWidth - 36 : split - 6), 12, top + 4, 0x252525, false);
            g.drawString(font, clipped(e.alias(), narrow ? imageWidth - 36 : imageWidth - split - 36),
                    narrow ? 12 : 12 + split, top + (narrow ? 16 : 4), 0x252525, false);
        }
        if (filtered.isEmpty()) g.drawWordWrap(font, text(all.isEmpty() ? "empty" : "no_results"), 12, 60, imageWidth - 40, 0x555555);
        g.drawString(font, clipped(text("count", filtered.size(), all.size()).getString(), imageWidth - 16),
                8, imageHeight - 49, 0x555555, false);
        g.drawString(font, clipped(status.getString(), imageWidth - 16), 8, imageHeight - 38, 0x404040, false);
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
        } else if (mx >= leftPos + 8 && mx < leftPos + imageWidth - 8
                && my >= topPos + imageHeight - 50 && my < topPos + imageHeight - 27) {
            drawTooltip(g, mx, my, List.of(text("scope"), status));
        }
    }
}
